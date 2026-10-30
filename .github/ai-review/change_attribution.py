"""固定 Base／Head 的位置歸責；位置未修改不等同沒有新影響鏈。"""

from dataclasses import dataclass
from difflib import SequenceMatcher
from enum import Enum

from review_snapshot import ReviewSnapshot


class AttributionStatus(str, Enum):
    DIRECT_CHANGE = "DIRECT_CHANGE"
    PREEXISTING_LOCATION = "PREEXISTING_LOCATION"
    INCONCLUSIVE = "INCONCLUSIVE"


@dataclass(frozen=True)
class ChangeAttribution:
    status: AttributionStatus
    reason: str
    base_line: int = 0


def attribute_location(snapshot: ReviewSnapshot, location: str) -> ChangeAttribution:
    """以完整版本比對排除行號平移；無版本、缺內容或無效位置不得宣告既有債。"""
    path, head_line = snapshot.parse_location(location)
    if not snapshot.head_sha or not snapshot.base_sha:
        return ChangeAttribution(AttributionStatus.INCONCLUSIVE, "缺少固定 Base／Head SHA。")
    entry = snapshot.find_file_entry(path)
    if entry is None:
        return ChangeAttribution(AttributionStatus.INCONCLUSIVE, "位置未列於變更清單。")
    head = snapshot.get_file_content(path)
    if head_line < 1 or head_line > len(head.splitlines()):
        return ChangeAttribution(AttributionStatus.INCONCLUSIVE, "無法取得完整 Head 位置。")
    if entry.get("status") == "added":
        return ChangeAttribution(AttributionStatus.DIRECT_CHANGE, "本次新增檔案。")
    base_path = str(entry.get("previous_filename") or path)
    base = snapshot.get_file_content(base_path, snapshot.base_sha)
    if not base:
        return ChangeAttribution(AttributionStatus.INCONCLUSIVE, "無法取得完整 Base 內容。")
    matcher = SequenceMatcher(None, base.splitlines(), head.splitlines(), autojunk=False)
    for tag, base_start, _, head_start, head_end in matcher.get_opcodes():
        if head_start <= head_line - 1 < head_end:
            if tag != "equal":
                return ChangeAttribution(AttributionStatus.DIRECT_CHANGE, "指定位置於本次被修改。")
            return ChangeAttribution(
                AttributionStatus.PREEXISTING_LOCATION,
                "位置內容於 Base 已存在；仍須查證本次變更是否引入新的影響鏈。",
                base_start + head_line - head_start,
            )
    return ChangeAttribution(AttributionStatus.INCONCLUSIVE, "無法映射 Base 位置。")
