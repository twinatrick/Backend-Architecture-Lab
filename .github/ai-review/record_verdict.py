"""僅供受信任 issue_comment 工作流程執行的裁定紀錄入口。"""

import json
import os
from pathlib import Path

import requests

from github_client import gh_get
from verdict_ledger import append_comment_verdicts


def main() -> None:
    """不 checkout PR、不執行留言內容、不接受修改既有裁定。"""
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if event.get("action") != "created" or not event.get("issue", {}).get("pull_request"):
        raise ValueError("僅支援新增 PR 留言事件。")
    repo = os.environ["GITHUB_REPOSITORY"]
    pr_number = event["issue"]["number"]
    comment_id = event["comment"]["id"]
    if not isinstance(pr_number, int) or not isinstance(comment_id, int):
        raise ValueError("事件識別碼不正確。")
    comment = gh_get(f"https://api.github.com/repos/{repo}/issues/comments/{comment_id}")
    if comment.get("created_at") != comment.get("updated_at"):
        raise ValueError("已編輯留言不作為新增裁定；請另外新增指令。")
    token = os.environ["AI_REVIEW_LEDGER_WRITE_TOKEN"]

    def putter(url: str, payload: dict) -> None:
        response = requests.put(
            url, headers={"Authorization": f"Bearer {token}",
                          "Accept": "application/vnd.github+json"},
            json=payload, timeout=30,
        )
        response.raise_for_status()

    count = append_comment_verdicts(
        repo, pr_number, comment, gh_get, putter,
        os.environ["AI_REVIEW_VERDICT_LEDGER_WRITER"],
    )
    print(f"已追加 {count} 筆裁定；未修改任何既有紀錄。")


if __name__ == "__main__":
    main()
