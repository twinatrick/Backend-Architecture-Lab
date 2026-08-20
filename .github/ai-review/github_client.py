import logging
import os
import sys
import requests
from redaction import get_gh_token, redact_secrets

REPO = os.environ.get("REPO", "")
EVENT_PATH = os.environ.get("EVENT_PATH", "")
GH_TOKEN = os.environ.get("GH_TOKEN", "")
REVIEW_MARKER = "<!-- ai-review-gate -->"


def _get_review_attr(attr_name: str, fallback_callable):
    review_mod = sys.modules.get("review")
    if review_mod and hasattr(review_mod, attr_name):
        return getattr(review_mod, attr_name)
    return fallback_callable


def get_repo() -> str:
    return os.environ.get("REPO") or REPO


def get_event_path() -> str:
    return os.environ.get("EVENT_PATH") or EVENT_PATH


def normalize_path(path_str: str) -> str:
    if not isinstance(path_str, str):
        return ""
    normalized = path_str.strip().replace("\\", "/")
    return normalized.removeprefix("./")


def normalize_paths(path_list: list) -> list:
    if not isinstance(path_list, list):
        return []
    return [normalize_path(p) for p in path_list if normalize_path(p)]


def get_github_headers() -> dict:
    return {
        "Authorization": f"Bearer {get_gh_token()}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def gh_get(url: str, params: dict | None = None):
    response = requests.get(
        url,
        headers=get_github_headers(),
        params=params,
        timeout=30,
    )
    response.raise_for_status()
    return response.json()


def resolve_pr_number(event: dict) -> int:
    pull_request = event.get("pull_request") or {}
    if pull_request.get("number"):
        return int(pull_request["number"])

    workflow_run = event.get("workflow_run") or {}
    pull_requests = workflow_run.get("pull_requests") or event.get("pull_requests") or []
    if pull_requests and pull_requests[0].get("number"):
        return int(pull_requests[0]["number"])

    inputs = event.get("inputs") or {}
    if inputs.get("pr_number"):
        try:
            return int(inputs["pr_number"])
        except (ValueError, TypeError) as exc:
            logging.warning("無法將 inputs.pr_number '%s' 解析為整數: %s", inputs.get("pr_number"), exc)

    head_sha = workflow_run.get("head_sha") or event.get("after")
    repo_name = get_repo()
    token_value = get_gh_token()
    if head_sha and repo_name and token_value:
        try:
            commits_url = f"https://api.github.com/repos/{repo_name}/commits/{head_sha}/pulls"
            getter = _get_review_attr("gh_get", gh_get)
            associated_pulls = getter(commits_url)
            if associated_pulls and associated_pulls[0].get("number"):
                return int(associated_pulls[0]["number"])
        except requests.RequestException as exc:
            print(f"無法透過 Commit SHA 查詢關聯 PR：{exc}")

    raise SystemExit("無法從 GitHub 事件或環境中解析對應的 Pull Request 編號。")


def post_issue_comment(pr_number: int, body: str):
    repo_name = get_repo()
    marked_body = body.rstrip() + "\n\n" + REVIEW_MARKER
    comments_url = f"https://api.github.com/repos/{repo_name}/issues/{pr_number}/comments"
    try:
        getter = _get_review_attr("gh_get", gh_get)
        existing_comments = getter(comments_url, params={"per_page": 100})
        existing = next(
            (
                comment
                for comment in existing_comments
                if REVIEW_MARKER in (comment.get("body") or "")
            ),
            None,
        )
        if existing:
            comment_id = existing["id"]
            patch_url = f"https://api.github.com/repos/{repo_name}/issues/comments/{comment_id}"
            response = requests.patch(
                patch_url,
                headers=get_github_headers(),
                json={"body": marked_body},
                timeout=30,
            )
            response.raise_for_status()
            print(f"成功更新現有 PR Issue 留言（ID: {comment_id}）")
            return response.json()
        else:
            response = requests.post(
                comments_url,
                headers=get_github_headers(),
                json={"body": marked_body},
                timeout=30,
            )
            response.raise_for_status()
            print(f"成功在 PR #{pr_number} 建立新 Issue 留言")
            return response.json()
    except requests.RequestException as exc:
        print(f"發布/更新 PR Issue 留言時發生錯誤：{exc}")
        return None


def post_pr_review(pr_number: int, body: str, event_type: str = "COMMENT"):
    repo_name = get_repo()
    review_url = f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/reviews"
    payload = {"body": body, "event": event_type}
    try:
        response = requests.post(
            review_url,
            headers=get_github_headers(),
            json=payload,
            timeout=30,
        )
        if response.status_code == 422:
            print("PR Review API 回傳 422（例如無法審查自身 PR），已由 Issue 留言保障可見性。")
            return None
        response.raise_for_status()
        print(f"成功提交 PR Review（狀態：{event_type}）")
        return response.json()
    except requests.RequestException as exc:
        print(f"提交 PR Review 時發生錯誤：{exc}")
        return None


def publish_review(pr_number: int, body: str, decision: str = "COMMENT"):
    post_issue_comment(pr_number, body)
    if decision == "APPROVE":
        review_event = "APPROVE"
    elif decision == "REQUEST_CHANGES":
        review_event = "REQUEST_CHANGES"
    else:
        review_event = "COMMENT"
    post_pr_review(pr_number, body, review_event)


def publish_failure_report(pr_number: int, title: str, reason: str, details=None):
    clean_title = redact_secrets(str(title))
    clean_reason = redact_secrets(str(reason))
    report = [
        "# AI Architecture & Security Review",
        "",
        "## 審查結果",
        "REQUEST_CHANGES",
        "",
        f"## 🔴 {clean_title}",
        f"**原因**：{clean_reason}",
        "",
    ]
    if details:
        report.append("**詳細資訊**：")
        if isinstance(details, list):
            for detail_item in details:
                if isinstance(detail_item, (tuple, list)) and len(detail_item) == 2:
                    key_text = redact_secrets(str(detail_item[0]))
                    val_text = redact_secrets(str(detail_item[1]))
                    report.append(f"- `{key_text}`：{val_text}")
                else:
                    report.append(f"- {redact_secrets(str(detail_item))}")
        else:
            report.append(f"```\n{redact_secrets(str(details))}\n```")
        report.append("")
    report.extend([
        "這是 fail-closed 行為：AI Review 遭遇錯誤或未完成時不得產生 APPROVE。",
        "請檢查 CI 日誌或修復相關設定後重新觸發。",
        "",
        "## 執行原則",
        "- 所有自然語言內容使用繁體中文。",
        "- AI Provider 或執行失敗不會被當成 Review PASS。",
        "- 保持 PR 流程透明，隨時留存診斷 Message。",
    ])
    body = "\n".join(report)
    if pr_number:
        publisher = _get_review_attr("publish_review", publish_review)
        publisher(pr_number, body, "REQUEST_CHANGES")
    return body
