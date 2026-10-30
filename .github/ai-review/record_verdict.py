"""僅供受信任 issue_comment 工作流程執行的裁定紀錄入口。"""

import json
import os
import re
from datetime import datetime
from pathlib import Path

import requests

from github_client import gh_get
from verdict_ledger import append_comment_verdicts


def main() -> None:
    """不 checkout PR、不執行留言內容、不接受修改既有裁定。"""
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if not isinstance(event, dict):
        raise ValueError("事件必須為物件。")
    issue = event.get("issue")
    event_comment = event.get("comment")
    if (event.get("action") != "created" or not isinstance(issue, dict)
            or not issue.get("pull_request") or not isinstance(event_comment, dict)):
        raise ValueError("僅支援新增 PR 留言事件。")
    repo = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise ValueError("儲存庫識別不正確。")
    pr_number = issue.get("number")
    comment_id = event_comment.get("id")
    if any(type(value) is not int or value <= 0 for value in (pr_number, comment_id)):
        raise ValueError("事件識別碼不正確。")
    token = os.environ.get("AI_REVIEW_LEDGER_WRITE_TOKEN", "").strip()
    writer = os.environ.get("AI_REVIEW_VERDICT_LEDGER_WRITER", "").strip()
    if not token or not writer:
        raise ValueError("缺少裁定寫入憑證或寫入者設定。")
    comment = gh_get(f"https://api.github.com/repos/{repo}/issues/comments/{comment_id}")
    if (not isinstance(comment, dict) or type(comment.get("id")) is not int
            or comment["id"] != comment_id
            or comment.get("issue_url") != (
                f"https://api.github.com/repos/{repo}/issues/{pr_number}")):
        raise ValueError("回讀留言與事件的 PR 或識別碼不一致。")
    created = comment.get("created_at")
    if not isinstance(created, str) or not created or created != comment.get("updated_at"):
        raise ValueError("已編輯留言不作為新增裁定；請另外新增指令。")
    if datetime.fromisoformat(created.replace("Z", "+00:00")).tzinfo is None:
        raise ValueError("留言時間缺少時區。")

    def putter(url: str, payload: dict) -> None:
        response = requests.put(
            url, headers={"Authorization": f"Bearer {token}",
                          "Accept": "application/vnd.github+json"},
            json=payload, timeout=30,
        )
        response.raise_for_status()

    count = append_comment_verdicts(
        repo, pr_number, comment, gh_get, putter,
        writer,
    )
    print(f"已追加 {count} 筆裁定；未修改任何既有紀錄。")


if __name__ == "__main__":
    main()
