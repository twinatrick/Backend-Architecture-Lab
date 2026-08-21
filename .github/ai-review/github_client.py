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
    if not isinstance(path_list, list):
        return []
    return [normalize_path(p) for p in path_list if isinstance(p, str) and normalize_path(p)]


def get_github_headers() -> dict[str, str]:
    token = get_gh_token()
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def gh_get(url: str, params: dict[str, Any] | None = None) -> Any:
    """封裝 GitHub API GET 請求，回傳 JSON 資料。"""
    res = requests.get(url, headers=get_github_headers(), params=params, timeout=30)
    res.raise_for_status()
    return res.json()


def validate_target_pr(
    pr_data: dict[str, Any],
    allowed_base_refs: list[str] | None = None,
    expected_head_sha: str | None = None,
) -> tuple[bool, str]:
    """嚴格校驗目標 PR 狀態、目標基準分支、所屬 Repository 與 head_sha 一致性。"""
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
    allowed_clean = [b.strip() for b in allowed if b.strip()]
    if base_ref not in allowed_clean:
        return False, f"PR 目標分支 '{base_ref}' 非受信任基準分支（允許：{allowed_clean}）"

    current_repo = get_repo()
    pr_repo = base_info.get("repo", {}).get("full_name", "")
    if current_repo and pr_repo and current_repo.lower() != pr_repo.lower():
        return False, f"PR 所屬 Repo '{pr_repo}' 與當前環境 '{current_repo}' 不一致"

    if expected_head_sha:
        pr_head_sha = pr_data.get("head", {}).get("sha", "")
        if pr_head_sha and pr_head_sha != expected_head_sha:
            return False, f"PR head_sha '{pr_head_sha}' 與期望 SHA '{expected_head_sha}' 不一致"
    return True, ""


def resolve_pr_number(event: dict) -> int:
    """自 GitHub Webhook 事件中精確解析唯一 Pull Request 編號。"""
    pull_request = event.get("pull_request") or {}
    if pull_request.get("number"):
        return int(pull_request["number"])

    workflow_run = event.get("workflow_run") or {}
    run_repo = workflow_run.get("repository", {}).get("full_name")
    current_repo = get_repo()
    if run_repo and current_repo and run_repo.lower() != current_repo.lower():
        raise SystemExit(f"workflow_run Repo '{run_repo}' 與配置 '{current_repo}' 不一致。")

    pull_requests = workflow_run.get("pull_requests") or event.get("pull_requests") or []
    head_sha = workflow_run.get("head_sha") or event.get("after")

    if len(pull_requests) == 1 and pull_requests[0].get("number"):
        pr_item = pull_requests[0]
        pr_head_sha = pr_item.get("head", {}).get("sha")
        if head_sha and pr_head_sha and head_sha != pr_head_sha:
            raise SystemExit(
                f"workflow_run head_sha '{head_sha}' 與關聯 PR head_sha '{pr_head_sha}' 不一致。"
            )
        return int(pr_item["number"])

    if len(pull_requests) > 1:
        matching = [
            p for p in pull_requests
            if head_sha and p.get("head", {}).get("sha") == head_sha
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

    repo_name = get_repo()
    token_value = get_gh_token()
    if head_sha and repo_name and token_value:
        try:
            commits_url = f"https://api.github.com/repos/{repo_name}/commits/{head_sha}/pulls"
            associated = gh_get(commits_url)
            if len(associated) == 1 and associated[0].get("number"):
                return int(associated[0]["number"])
            if len(associated) > 1:
                open_pulls = [p for p in associated if p.get("state") == "open"]
                if len(open_pulls) == 1 and open_pulls[0].get("number"):
                    return int(open_pulls[0]["number"])
                raise SystemExit(f"Commit SHA {head_sha} 關聯多個候選 PR，無法唯一確定。")
        except requests.RequestException as exc:
            logging.warning("無法透過 Commit SHA 查詢關聯 PR：%s", exc)

    raise SystemExit("無法從 GitHub 事件或環境中解析對應的 Pull Request 編號。")


def resolve_review_target(event: dict) -> dict[str, Any]:
    """解析觸發審查的目標 PR 編號、期望 SHA、觸發者與觸發型態。"""
    pr_number = resolve_pr_number(event)
    workflow_run = event.get("workflow_run") or {}
    inputs = event.get("inputs") or {}
    raw_actor = (
        event.get("sender", {}).get("login")
        or workflow_run.get("actor", {}).get("login")
        or workflow_run.get("triggering_actor", {}).get("login")
        or event.get("actor")
        or "system"
    )
    raw_sha = (
        inputs.get("head_sha")
        or workflow_run.get("head_sha")
        or event.get("after")
        or ""
    )
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
    repo_name = get_repo()
    marked_body = body.rstrip() + "\n\n" + REVIEW_MARKER
    comments_url = f"https://api.github.com/repos/{repo_name}/issues/{pr_number}/comments"
    try:
        existing_comments = gh_get(comments_url, params={"per_page": 100})
        existing = next(
            (c for c in existing_comments if REVIEW_MARKER in (c.get("body") or "")),
            None,
        )
        if existing:
            patch_url = f"https://api.github.com/repos/{repo_name}/issues/comments/{existing['id']}"
            res = requests.patch(
                patch_url, headers=get_github_headers(), json={"body": marked_body}, timeout=30
            )
            res.raise_for_status()
            print(f"成功更新現有 PR Issue 留言（ID: {existing['id']}）")
            return res.json()
        res = requests.post(
            comments_url, headers=get_github_headers(), json={"body": marked_body}, timeout=30
        )
        res.raise_for_status()
        print(f"成功在 PR #{pr_number} 建立新 Issue 留言")
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
    repo_name = get_repo()
    review_url = f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/reviews"
    payload = {"body": body, "event": event_type}
    if commit_id:
        payload["commit_id"] = commit_id
    try:
        response = requests.post(
            review_url,
            headers=get_github_headers(),
            json=payload,
            timeout=30,
        )
        if response.status_code == 422:
            print(
                f"PR Review API 回傳 422（無法提交狀態：{event_type}）："
                f"{redact_secrets(response.text)}"
            )
            return None
        response.raise_for_status()
        print(f"成功提交 PR Review（狀態：{event_type}）")
        return response.json()
    except requests.RequestException as exc:
        print(f"提交 PR Review 時發生錯誤：{redact_secrets(str(exc))}")
        return None


def publish_review(
    pr_number: int,
    body: str,
    decision: str = "COMMENT",
    commit_id: str | None = None,
) -> bool:
    """發布審查意見至 PR Issue 留言與 PR Review。若發布失敗則拋出例外落實 Fail-Closed。"""
    comment_res = post_issue_comment(pr_number, body)
    review_event = decision if decision in ("APPROVE", "REQUEST_CHANGES") else "COMMENT"
    review_res = post_pr_review(pr_number, body, review_event, commit_id=commit_id)

    if review_res is None:
        raise RuntimeError(
            f"無法在 PR #{pr_number} 提交正式 PR Review（決策：{decision}），"
            f"觸發 Fail-Closed 保護。"
        )
    return True


def publish_failure_report(
    pr_number: int,
    title: str,
    reason: str,
    details: Any = None,
    commit_id: str | None = None,
) -> str:
    report = [
        "# AI Architecture & Security Review\n",
        "## 審查結果\nREQUEST_CHANGES\n",
        f"## 🔴 {redact_secrets(str(title))}",
        f"**原因**：{redact_secrets(str(reason))}\n",
    ]
    if details:
        logging.error(
            "AI Review 失敗詳細診斷資訊：%s",
            redact_secrets(str(details))[:2000],
        )
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
