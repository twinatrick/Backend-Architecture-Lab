import base64
import logging
from typing import Any
from urllib.parse import quote

import requests

from github_client import gh_get


def fetch_all_pr_files(
    repo_name: str, pr_number: int, gh_get_fn: Any = None,
) -> list[dict[str, Any]]:
    """分頁讀取 PR 變更檔案；呼叫端須再次驗證 PR SHA。"""
    getter = gh_get_fn or gh_get
    files: list[dict[str, Any]] = []
    page = 1
    while True:
        chunk = getter(
            f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/files",
            params={"per_page": 100, "page": page},
        )
        files.extend(chunk)
        if len(chunk) < 100:
            break
        page += 1
    return files


def fetch_file_content_fallback(
    repo_name: str, path: str, ref: str | None = None, gh_get_fn: Any = None,
) -> str | None:
    """讀取指定版本完整 UTF-8 原始碼，不以替代字元修補證據。"""
    getter = gh_get_fn or gh_get
    try:
        url = f"https://api.github.com/repos/{repo_name}/contents/{quote(path, safe='/')}"
        data = getter(url, params={"ref": ref} if ref else None)
        if isinstance(data, dict) and data.get("encoding") == "base64" and data.get("content"):
            return base64.b64decode(data["content"]).decode("utf-8")
    except (requests.RequestException, UnicodeDecodeError, ValueError, KeyError) as exc:
        # 僅保留型別與 HTTP 狀態，不輸出可能含憑證或私有內容的例外訊息。
        response = getattr(exc, "response", None)
        status = getattr(response, "status_code", None)
        safe_status = status if type(status) is int and 100 <= status <= 599 else None
        logging.warning("無法自 Contents API 讀取檔案 %s（%s，HTTP=%s）",
                        path, type(exc).__name__, safe_status)
    return None


def enrich_files_with_full_content(
    repo_name: str, files: list[dict[str, Any]], head_sha: str, gh_get_fn: Any = None,
) -> None:
    """針對缺少 patch 且未刪除的檔案，透過固定版本補充完整內容。"""
    for file_item in files:
        if (
            file_item.get("status") not in ("removed", "deleted")
            and not file_item.get("patch")
            and not file_item.get("full_content")
        ):
            blob = fetch_file_content_fallback(
                repo_name, file_item.get("filename", ""), head_sha, gh_get_fn=gh_get_fn,
            )
            if blob is not None:
                file_item["full_content"] = blob
