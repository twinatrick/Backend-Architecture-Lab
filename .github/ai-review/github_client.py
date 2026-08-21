import logging
import os
from typing import Any

import requests

from redaction import get_gh_token, redact_secrets

REPO = os.environ.get("REPO", "")
EVENT_PATH = os.environ.get("EVENT_PATH", "")
GH_TOKEN = os.environ.get("GH_TOKEN", "")
REVIEW_MARKER = "<!-- ai-review-gate -->"


def get_repo() -> str:
    return os.environ.get("REPO") or os.environ.get("GITHUB_REPOSITORY") or REPO


def get_event_path() -> str:
    return os.environ.get("EVENT_PATH") or os.environ.get("GITHUB_EVENT_PATH") or EVENT_PATH


def normalize_path(path_str: str) -> str:
    if not isinstance(path_str, str):
        return ""
    return path_str.strip().replace("\\", "/").removeprefix("./")


def normalize_paths(path_list: list[str]) -> list[str]:
    return [
        normalize_path(item_path) for item_path in (path_list or [])
        if isinstance(item_path, str) and normalize_path(item_path)
    ]


def get_github_headers() -> dict[str, str]:
    token = get_gh_token()
    headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def gh_get(url: str, params: dict[str, Any] | None = None) -> Any:
    res = requests.get(url, headers=get_github_headers(), params=params, timeout=30)
    res.raise_for_status()
    return res.json()


def validate_target_pr(
    pr_data: dict[str, Any],
    allowed_base_refs: list[str] | None = None,
    expected_head_sha: str | None = None,
) -> tuple[bool, str]:
    """校驗目標 PR 狀態、目標分支、所屬 Repository 與 head_sha 一致性。"""
    if not isinstance(pr_data, dict):
        return False, "PR 資料無效或非字典型態"
    state = pr_data.get("state", "").lower()
    if state != "open":
        return False, f"PR 狀態為 '{state}'（非開啟中 open 狀態）"

    base_info = pr_data.get("base") or {}
    base_ref = base_info.get("ref", "")
    allowed = allowed_base_refs or os.environ.get(
        "ALLOWED_BASE_BRANCHES", "master,main"
    ).split(",")
    allowed_clean = [branch_name.strip() for branch_name in allowed if branch_name.strip()]
    if base_ref not in allowed_clean:
        return False, f"PR 目標分支 '{base_ref}' 非受信任基準分支（允許：{allowed_clean}）"

    cur_repo, pr_repo = get_repo(), base_info.get("repo", {}).get("full_name", "")
    if cur_repo and pr_repo and cur_repo.lower() != pr_repo.lower():
        return False, f"PR 所屬 Repo '{pr_repo}' 與當前環境 '{cur_repo}' 不一致"

    if expected_head_sha:
        head_sha = pr_data.get("head", {}).get("sha", "")
        if not head_sha or head_sha != expected_head_sha:
            return False, f"PR head_sha '{head_sha}' 與期望 SHA '{expected_head_sha}' 不一致或缺失"
    return True, ""


def resolve_pr_number(event: dict) -> int:
    """自 GitHub Webhook 事件中解析唯一 Pull Request 編號。"""
    pull_request = event.get("pull_request") or {}
    if pull_request.get("number"):
        return int(pull_request["number"])

    workflow_run = event.get("workflow_run") or {}
    run_repo, cur_repo = workflow_run.get("repository", {}).get("full_name"), get_repo()
    if run_repo and cur_repo and run_repo.lower() != cur_repo.lower():
        raise SystemExit(f"workflow_run Repo '{run_repo}' 與配置 '{cur_repo}' 不一致。")

    pull_requests = workflow_run.get("pull_requests") or event.get("pull_requests") or []
    head_sha = workflow_run.get("head_sha") or event.get("after")

    if len(pull_requests) == 1 and pull_requests[0].get("number"):
        pr_sha = pull_requests[0].get("head", {}).get("sha")
        if head_sha and pr_sha and head_sha != pr_sha:
            raise SystemExit(f"workflow_run SHA '{head_sha}' 與 PR SHA '{pr_sha}' 不一致。")
        return int(pull_requests[0]["number"])

    if len(pull_requests) > 1:
        matching = [
            item for item in pull_requests
            if head_sha and item.get("head", {}).get("sha") == head_sha
        ]
        if len(matching) == 1 and matching[0].get("number"):
            return int(matching[0]["number"])
        raise SystemExit(f"workflow_run 包含歧義 PR 清單（共 {len(pull_requests)} 個）。")

    inputs = event.get("inputs") or {}
    if inputs.get("pr_number"):
        actor = event.get("sender", {}).get("login") or event.get("actor") or "unknown"
        logging.info("手動 dispatch 觸發審查，Actor: %s，PR: %s", actor, inputs.get("pr_number"))
        try:
            return int(inputs["pr_number"])
        except (ValueError, TypeError) as exc:
            raise SystemExit(f"無法將 inputs.pr_number 解析為整數: {exc}")

    repo_name, token = get_repo(), get_gh_token()
    if head_sha and repo_name and token:
        try:
            url = f"https://api.github.com/repos/{repo_name}/commits/{head_sha}/pulls"
            associated = gh_get(url)
            if len(associated) == 1 and associated[0].get("number"):
                return int(associated[0]["number"])
            if len(associated) > 1:
                open_pulls = [item for item in associated if item.get("state") == "open"]
                if len(open_pulls) == 1 and open_pulls[0].get("number"):
                    return int(open_pulls[0]["number"])
                raise SystemExit(f"Commit SHA {head_sha} 關聯多個候選 PR，無法唯一確定。")
        except requests.RequestException as exc:
            logging.warning("無法透過 Commit SHA 查詢關聯 PR：%s", exc)

    raise SystemExit("無法從 GitHub 事件或環境中解析對應的 Pull Request 編號。")


def resolve_review_target(event: dict) -> dict[str, Any]:
    pr_number = resolve_pr_number(event)
    workflow_run = event.get("workflow_run") or {}
    inputs = event.get("inputs") or {}
    raw_actor = (
        event.get("sender", {}).get("login")
        or workflow_run.get("actor", {}).get("login")
        or workflow_run.get("triggering_actor", {}).get("login")
        or event.get("actor") or "system"
    )
    raw_sha = inputs.get("head_sha") or workflow_run.get("head_sha") or event.get("after") or ""
    expected_sha = raw_sha.strip() if isinstance(raw_sha, str) else ""
    trigger_type = (
        "workflow_dispatch" if inputs.get("pr_number")
        else "workflow_run" if workflow_run
        else "pull_request" if event.get("pull_request")
        else "event"
    )
    return {
        "pr_number": pr_number,
        "expected_head_sha": expected_sha or None,
        "head_sha": expected_sha or None,
        "actor": str(raw_actor),
        "trigger_type": trigger_type,
    }


def post_issue_comment(pr_number: int, body: str) -> dict | None:
    repo = get_repo()
    marked_body = body.rstrip() + "\n\n" + REVIEW_MARKER
    url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    try:
        existing = gh_get(url, params={"per_page": 100})
        for comment_item in existing:
            if REVIEW_MARKER in comment_item.get("body", ""):
                c_url = f"https://api.github.com/repos/{repo}/issues/comments/{comment_item['id']}"
                res = requests.patch(
                    c_url, headers=get_github_headers(), json={"body": marked_body}, timeout=30,
                )
                res.raise_for_status()
                return res.json()
        res = requests.post(
            url, headers=get_github_headers(), json={"body": marked_body}, timeout=30,
        )
        res.raise_for_status()
        return res.json()
    except requests.RequestException as exc:
        print(f"發布/更新 PR Issue 留言時發生錯誤：{redact_secrets(str(exc))}")
        return None


def post_pr_review(
    pr_number: int,
    body: str,
    event_type: str = "COMMENT",
    commit_id: str | None = None,
) -> dict | None:
    repo = get_repo()
    url = f"https://api.github.com/repos/{repo}/pulls/{pr_number}/reviews"
    payload: dict[str, Any] = {"body": body, "event": event_type}
    if commit_id:
        payload["commit_id"] = commit_id
    try:
        res = requests.post(url, headers=get_github_headers(), json=payload, timeout=30)
        if res.status_code == 422:
            print(f"PR Review API 回傳 422：{redact_secrets(res.text)}")
            return None
        res.raise_for_status()
        print(f"成功提交 PR Review（狀態：{event_type}）")
        return res.json()
    except requests.RequestException as exc:
        print(f"提交 PR Review 時發生錯誤：{redact_secrets(str(exc))}")
        return None


def post_commit_status(
    head_sha: str,
    state: str,
    description: str = "",
    context: str = "ai-review/architecture-gate",
) -> dict | None:
    if not head_sha:
        return None
    repo = get_repo()
    url = f"https://api.github.com/repos/{repo}/statuses/{head_sha}"
    payload = {"state": state, "description": description[:140], "context": context}
    try:
        res = requests.post(url, headers=get_github_headers(), json=payload, timeout=30)
        res.raise_for_status()
        return res.json()
    except requests.RequestException as exc:
        logging.warning("發布 Commit Status 失敗: %s", redact_secrets(str(exc)))
        return None


def publish_review(
    pr_number: int,
    body: str,
    decision: str = "COMMENT",
    commit_id: str | None = None,
    requires_human_review: bool = False,
) -> bool:
    post_issue_comment(pr_number, body)
    review_event = decision if decision in ("APPROVE", "REQUEST_CHANGES") else "COMMENT"
    review_res = post_pr_review(pr_number, body, review_event, commit_id=commit_id)

    if review_res is None:
        if commit_id:
            post_commit_status(commit_id, "failure", f"Review 發布失敗 ({decision})")
        raise RuntimeError(f"無法在 PR #{pr_number} 提交正式 PR Review，觸發 Fail-Closed。")
    if commit_id:
        status_state = "success" if decision == "APPROVE" else "failure"
        if decision == "HUMAN_REVIEW_REQUIRED":
            description_text = "AI Review: Human Review Required (Architect Approval Needed)"
        elif decision == "APPROVE" and requires_human_review:
            description_text = "AI Review: APPROVE (Human Review Required)"
        else:
            description_text = f"AI Review: {decision}"
        status_res = post_commit_status(commit_id, status_state, description_text)
        if status_res is None:
            err = f"無法為 Commit {commit_id} 發布 Commit Status ({status_state})，觸發 Fail-Closed。"
            raise RuntimeError(err)
    return True


def publish_failure_report(
    pr_number: int,
    title: str,
    reason: str,
    details: Any = None,
    commit_id: str | None = None,
    status_type: str = "REVIEW_FAILED_INFRA",
) -> str:
    report = [
        "# AI Architecture & Security Review\n",
        f"## 審查結果\n{status_type}\n",
        f"## 🔴 {redact_secrets(str(title))}",
        f"**原因**：{redact_secrets(str(reason))}\n",
    ]
    if details:
        logging.error("AI Review 失敗詳細診斷資訊：%s", redact_secrets(str(details))[:2000])
        report.append("**摘要資訊**：")
        if isinstance(details, list):
            for item in details:
                if isinstance(item, (tuple, list)) and len(item) == 2:
                    msg = str(item[1]).split("\n")[0][:120]
                    report.append(f"- `{redact_secrets(str(item[0]))}`：{redact_secrets(msg)}")
                else:
                    report.append(f"- {redact_secrets(str(item)[:120])}")
        else:
            report.append(f"```\n{redact_secrets(str(details).splitlines()[0][:200])}\n```")
        report.append("")
    report.extend([
        "這是 fail-closed 行為：AI Review 遭遇錯誤或未完成時不得產生 APPROVE。",
        "請檢查 CI 日誌或修復相關設定後重新觸發。",
    ])
    body = "\n".join(report)
    if pr_number:
        try:
            publish_review(pr_number, body, "REQUEST_CHANGES", commit_id=commit_id)
        except (RuntimeError, requests.RequestException) as exc:
            logging.error("發布失敗報告至 PR #%s 失敗: %s", pr_number, redact_secrets(str(exc)))
    return body
