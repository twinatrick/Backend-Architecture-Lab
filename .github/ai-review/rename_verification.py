import time
from typing import Any

from review_snapshot import ReviewSnapshot


def verified_content_rename(item: dict[str, Any], snapshot: ReviewSnapshot, content: str) -> bool:
    """僅認可固定版本完整內容相同的純搬移，不信任候選輸入的通過旗標。"""
    previous = item.get("previous_filename")
    path = item.get("filename") or item.get("path")
    if (
        item.get("status") != "renamed"
        or not isinstance(previous, str)
        or not previous
        or previous == path
        or not snapshot.base_sha
        or not snapshot.head_sha
        or snapshot.base_sha == snapshot.head_sha
        or not content
        or snapshot.content_fetcher is None
        or time.monotonic() >= snapshot.source_budget.deadline
        or any(type(item.get(key)) is not int or item[key] != 0
               for key in ("additions", "deletions", "changes"))
    ):
        return False
    # 直接使用原始路徑，避免快照的檔名尾綴搜尋誤配至另一個檔案。
    base_content = snapshot.source_budget.read(
        snapshot.repo_name, snapshot.base_sha, previous,
        lambda: snapshot.content_fetcher(snapshot.repo_name, previous, snapshot.base_sha),
    )
    return (time.monotonic() < snapshot.source_budget.deadline
            and bool(base_content) and base_content == content)
