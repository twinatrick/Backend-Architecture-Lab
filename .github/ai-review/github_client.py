import json
import logging
import os
from pathlib import Path
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
        normalize_path(item) for item in (path_list or [])
        if isinstance(item, str) and normalize_path(item)
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


def load_pr_metadata(metadata_path: Path | str | None = None) -> dict[str, Any] | None:
    candidates = [Path(metadata_path)] if metadata_path else [
        Path(".ai-review-meta/pr-metadata.json"), Path("pr-metadata.json"),
    ]
    for path_obj in candidates:
        try:
            if path_obj.exists() and path_obj.is_file():
                raw = json.loads(path_obj.read_text(encoding="utf-8"))
                if isinstance(raw, dict) and raw.get("pr_number"):
                    return raw
        except (json.JSONDecodeError, OSError) as exc:
            logging.warning("讀取 PR 中繼資料檔案 %s 失敗: %s", path_obj, exc)
    return None


def validate_target_pr(
    pr_data: dict[str, Any],
    allowed_base_refs: list[str] | None = None,
    expected_head_sha: str | None = None,
) -> tuple[bool, str]:
    if not isinstance(pr_data, dict):
        return False, "PR 資料無效或非字典型態"
    if (state := pr_data.get("state", "").lower()) != "open":
        return False, f"PR 狀態為 '{state}'（非開啟中 open 狀態）"
    base_info = pr_data.get("base") or {}
    base_ref = base_info.get("ref", "")
    default_branches = os.environ.get("ALLOWED_BASE_BRANCHES", "master,main")
    allowed = allowed_base_refs or default_branches.split(",")
    allowed_clean = [branch.strip() for branch in allowed if branch.strip()]
    if base_ref not in allowed_clean:
        return False, f"PR 目標分支 '{base_ref}' 非受信任基準分支（允許：{allowed_clean}）"
    cur_repo, pr_repo = get_repo(), base_info.get("repo", {}).get("full_name", "")
    if cur_repo and pr_repo and cur_repo.lower() != pr_repo.lower():
        return False, f"PR 所屬 Repo '{pr_repo}' 與當前環境 '{cur_repo}' 不一致"
    head_sha = pr_data.get("head", {}).get("sha", "")
    if expected_head_sha and (not head_sha or head_sha != expected_head_sha):
        return False, f"PR head_sha '{head_sha}' 與期望 SHA '{expected_head_sha}' 不一致或缺失"
    return True, ""


def resolve_pr_number(event: dict, metadata_path: Path | str | None = None) -> int:
    if (metadata := load_pr_metadata(metadata_path)) and metadata.get("pr_number"):
        meta_repo, cur_repo = metadata.get("repo", ""), get_repo()
        if not meta_repo or not cur_repo or meta_repo.lower() == cur_repo.lower():
            try:
                return int(metadata["pr_number"])
            except (ValueError, TypeError) as exc:
                logging.warning("無法將 metadata.pr_number 解析為整數: %s", exc)
    if (event.get("pull_request") or {}).get("number"):
        return int(event["pull_request"]["number"])
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
            req for req in pull_requests if head_sha and req.get("head", {}).get("sha") == head_sha
        ]
        if len(matching) == 1 and matching[0].get("number"):
            return int(matching[0]["number"])
        raise SystemExit(f"workflow_run 包含歧義 PR 清單（共 {len(pull_requests)} 個）。")
    if (inputs := event.get("inputs") or {}).get("pr_number"):
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
                open_pulls = [assoc for assoc in associated if assoc.get("state") == "open"]
                if len(open_pulls) == 1 and open_pulls[0].get("number"):
                    return int(open_pulls[0]["number"])
                raise SystemExit(f"Commit SHA {head_sha} 關聯多個候選 PR，無法唯一確定。")
        except requests.RequestException as exc:
            logging.warning("無法透過 Commit SHA 查詢關聯 PR：%s", exc)
    raise SystemExit("無法從 GitHub 事件、Artifact 或環境中解析對應的 Pull Request 編號。")


def resolve_review_target(event: dict, metadata_path: Path | str | None = None) -> dict[str, Any]:
    metadata = load_pr_metadata(metadata_path)
    pr_number = resolve_pr_number(event, metadata_path=metadata_path)
    workflow_run, inputs = event.get("workflow_run") or {}, event.get("inputs") or {}
    raw_actor = (
        (metadata.get("actor") if metadata else None)
        or event.get("sender", {}).get("login")
        or workflow_run.get("actor", {}).get("login")
        or workflow_run.get("triggering_actor", {}).get("login")
        or event.get("actor") or "system"
    )
    raw_sha = (
        (metadata.get("head_sha") if metadata else None)
        or inputs.get("head_sha") or workflow_run.get("head_sha") or event.get("after") or ""
    )
    expected_sha = raw_sha.strip() if isinstance(raw_sha, str) else ""
    trigger_type = (
        "metadata_artifact" if metadata and metadata.get("pr_number")
        else "workflow_dispatch" if inputs.get("pr_number")
        else "workflow_run" if workflow_run
        else "pull_request" if event.get("pull_request")
        else "event"
    )
    return {
        "pr_number": pr_number, "expected_head_sha": expected_sha or None,
        "head_sha": expected_sha or None, "actor": str(raw_actor), "trigger_type": trigger_type,
    }


def post_issue_comment(pr_number: int, body: str) -> dict | None:
    repo, marked_body = get_repo(), body.rstrip() + "\n\n" + REVIEW_MARKER
    url = f"https://api.github.com/repos/{repo}/issues/{pr_number}/comments"
    try:
        for comment_item in gh_get(url, params={"per_page": 100}):
            if REVIEW_MARKER in comment_item.get("body", ""):
                c_url = f"https://api.github.com/repos/{repo}/issues/comments/{comment_item['id']}"
                res = requests.patch(
                    c_url, headers=get_github_headers(), json={"body": marked_body}, timeout=30
                )
                res.raise_for_status()
                return res.json()
        res = requests.post(
            url, headers=get_github_headers(), json={"body": marked_body}, timeout=30
        )
        res.raise_for_status()
        return res.json()
    except requests.RequestException as exc:
        print(f"發布/更新 PR Issue 留言時發生錯誤：{redact_secrets(str(exc))}")
        return None


def post_pr_review(
    pr_number: int, body: str, event_type: str = "COMMENT", commit_id: str | None = None,
) -> dict | None:
    url = f"https://api.github.com/repos/{get_repo()}/pulls/{pr_number}/reviews"
    payload: dict[str, Any] = {"body": body, "event": event_type}
    if commit_id:
        payload["commit_id"] = commit_id
    try:
        res = requests.post(url, headers=get_github_headers(), json=payload, timeout=30)
        if res.status_code == 422:
            logging.warning("PR Review 422: %s", redact_secrets(res.text))
            return {"status": "skipped_422", "body": res.text}
        res.raise_for_status()
        return res.json()
    except requests.RequestException as exc:
        print(f"提交 PR Review 失敗：{redact_secrets(str(exc))}")
        return None


def post_commit_status(
    head_sha: str, state: str, description: str = "", context: str = "ai-review/architecture-gate",
) -> dict | None:
    if not head_sha:
        return None
    url = f"https://api.github.com/repos/{get_repo()}/statuses/{head_sha}"
    payload = {"state": state, "description": description[:140], "context": context}
    try:
        res = requests.post(url, headers=get_github_headers(), json=payload, timeout=30)
        res.raise_for_status()
        return res.json()
    except requests.RequestException as exc:
        logging.warning("發布 Commit Status 失敗: %s", redact_secrets(str(exc)))
        return None


def publish_review(
    pr_number: int, body: str, decision: str = "COMMENT",
    commit_id: str | None = None, requires_human_review: bool = False,
) -> bool:
    comment_res = post_issue_comment(pr_number, body)
    review_event = decision if decision in ("APPROVE", "REQUEST_CHANGES") else "COMMENT"
    review_res = post_pr_review(pr_number, body, review_event, commit_id=commit_id)

    is_posted = review_res is not None and (
        not isinstance(review_res, dict) or review_res.get("status") != "skipped_422"
    )
    is_422 = isinstance(review_res, dict) and review_res.get("status") == "skipped_422"
    if not is_posted and not is_422:
        if commit_id:
            post_commit_status(commit_id, "failure", f"Review 發布失敗 ({decision})")
        raise RuntimeError(f"無法在 PR #{pr_number} 提交正式 PR Review，觸發 Fail-Closed。")
    if is_422 and comment_res is None:
        if commit_id:
            post_commit_status(commit_id, "failure", f"Review 發布失敗 ({decision})")
        raise RuntimeError("PR Review 因 422 跳過且 Issue 留言失敗，觸發 Fail-Closed。")

    if commit_id:
        status_state = "success" if decision == "APPROVE" else "failure"
        appr_msg = (
            "AI Review: APPROVE (Human Review Required)" if requires_human_review
            else "AI Review: APPROVE"
        )
        desc_map = {
            "HUMAN_REVIEW_REQUIRED": "AI Review: Human Review Required (Architect Approval Needed)",
            "APPROVE": appr_msg, "REQUEST_CHANGES": "AI Review: REQUEST_CHANGES",
        }
        desc = desc_map.get(decision, f"AI Review: {decision}")
        if post_commit_status(commit_id, status_state, desc) is None:
            err = f"無法為 Commit {commit_id} 發布 Status ({status_state})，觸發 Fail-Closed。"
            raise RuntimeError(err)
    return True


def publish_failure_report(
    pr_number: int | None, title: str, reason: str, details: Any = None,
    commit_id: str | None = None, status_type: str = "REVIEW_FAILED_INFRA",
) -> str:
    summary_lines = []
    if details:
        logging.error("AI Review 失敗資訊：%s", redact_secrets(str(details))[:2000])
        if isinstance(details, list):
            for item in details:
                is_tuple = isinstance(item, (tuple, list))
                msg = str(item[1] if is_tuple else item).split("\n")[0][:120]
                prefix = f"- `{redact_secrets(str(item[0]))}`：" if is_tuple else "- "
                summary_lines.append(f"{prefix}{redact_secrets(msg)}")
        else:
            summary_lines.append(
                f"```\n{redact_secrets(str(details).splitlines()[0][:200])}\n```"
            )
    detail_sec = ("**摘要資訊**：\n" + "\n".join(summary_lines) + "\n\n") if summary_lines else ""
    body = (
        f"# AI Architecture & Security Review\n\n## 審查結果\n{status_type}\n\n"
        f"## 🔴 {redact_secrets(str(title))}\n**原因**：{redact_secrets(str(reason))}\n\n"
        f"{detail_sec}這是 fail-closed 行為：AI Review 遭遇錯誤或未完成時不得產生 APPROVE。\n"
        "請檢查 CI 日誌或修復相關設定後重新觸發。"
    )
    if pr_number:
        try:
            publish_review(pr_number, body, "REQUEST_CHANGES", commit_id=commit_id)
        except (RuntimeError, requests.RequestException) as exc:
            logging.error("發布失敗報告至 PR #%s 失敗: %s", pr_number, redact_secrets(str(exc)))
            if commit_id:
                post_commit_status(commit_id, "failure", f"AI Review: {status_type}")
    elif commit_id:
        post_commit_status(commit_id, "failure", f"AI Review: {status_type}")
    return body
