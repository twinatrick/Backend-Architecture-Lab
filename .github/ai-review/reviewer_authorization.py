"""依受信任設定中的固定 GitHub 使用者 ID 驗證人工裁定資格。"""

import json
from pathlib import Path
from typing import Any
from urllib.parse import quote

import requests

from github_client import gh_get

REVIEWER_CONFIG = Path(__file__).resolve().with_name("approved_reviewers.json")


def approved_reviewer_ids(repo: str) -> set[int]:
    """僅讀受信任檢出的設定；不讀 PR 內容、環境覆寫或使用者指定路徑。"""
    data = json.loads(REVIEWER_CONFIG.read_text(encoding="utf-8"))
    if (not isinstance(data, dict) or set(data) != {"version", "repository", "reviewers"}
            or type(data["version"]) is not int or data["version"] != 1
            or not isinstance(data["repository"], str)
            or data["repository"].casefold() != repo.casefold()
            or not isinstance(data["reviewers"], list)):
        raise ValueError("受信任審核者名單格式或儲存庫不符。")
    identities: set[int] = set()
    for item in data["reviewers"]:
        if (not isinstance(item, dict) or set(item) != {"github_user_id", "display_login"}
                or type(item["github_user_id"]) is not int or item["github_user_id"] <= 0
                or not isinstance(item["display_login"], str) or not item["display_login"].strip()
                or item["github_user_id"] in identities):
            raise ValueError("審核者必須有唯一的固定使用者 ID；帳號名稱僅供識別。")
        identities.add(item["github_user_id"])
    return identities


def verify_approved_reviewer(
    repo: str, username: str, gh_get_fn: Any = None, *, expected_user_id: int | None = None,
) -> tuple[bool, str, int]:
    """用 GitHub API 身分比對核准名單；不推導合併權限，也不使用 LLM。"""
    if not isinstance(repo, str) or not repo or not isinstance(username, str) or not username:
        return False, "缺少可驗證的儲存庫或審核者身分。", 0
    try:
        identities = approved_reviewer_ids(repo)
        getter = gh_get_fn or gh_get
        user = getter(f"https://api.github.com/users/{quote(username, safe='')}")
        if (not isinstance(user, dict) or user.get("type") != "User"
                or not isinstance(user.get("login"), str)
                or user["login"].casefold() != username.casefold()
                or type(user.get("id")) is not int or user["id"] not in identities):
            return False, "此 GitHub 使用者 ID 未列於受信任審核者名單。", 0
        if expected_user_id is not None and (
            type(expected_user_id) is not int or expected_user_id != user["id"]
        ):
            return False, "留言者固定 ID 與 GitHub API 身分不符。", 0
        return True, "已確認為專案負責人核准的審核者。", user["id"]
    except (OSError, ValueError, TypeError, requests.RequestException):
        return False, "審核者名單或 GitHub 身分驗證未完成，拒絕授權。", 0
