from dataclasses import dataclass, field
from enum import Enum
import hashlib
import json
import re
from typing import Any, Callable

from diff_parser import extract_changed_lines
from rule_version import trusted_rule_version


class InspectionStatus(str, Enum):
    """規則查證工具之四態判定結果。"""

    VIOLATION = "VIOLATION"
    COMPLIANT = "COMPLIANT"
    NOT_APPLICABLE = "NOT_APPLICABLE"
    INCONCLUSIVE = "INCONCLUSIVE"


@dataclass(frozen=True)
class RuleInspectionResult:
    """單一規則主張（Rule Claim）經確定性規則查證工具執行後的結構化報告。"""

    status: InspectionStatus
    rule_name: str
    location: str
    verdict_reason: str
    verified_snippet: str = ""
    missing_context: tuple[str, ...] = ()
    confirmed_findings: tuple[dict[str, Any], ...] = ()


def compute_claim_identity(location: str, rule_key: str, evidence: str = "") -> str:
    """綁定路徑、行號、規則、證據及受信任查證版本；更新工具不得沿用舊裁定。"""
    norm_loc = str(location).strip().replace("\\", "/")
    norm_rule = str(rule_key).strip().upper()
    norm_ev = " ".join(str(evidence).strip().split())
    raw_key = json.dumps([trusted_rule_version(), norm_loc, norm_rule, norm_ev], ensure_ascii=False)
    return hashlib.sha256(raw_key.encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class RuleClaim:
    """由單一審查疑點拆分出之獨立規則主張（可獨立查證與獨立人工裁定）。"""

    claim_id: str
    rule_key: str
    finding: dict[str, Any]


@dataclass(frozen=True)
class HumanClaimVerdict:
    """具備主要分支合併權限之審查者針對特定規則主張與審查快照做出之可稽核人工裁定。"""

    claim_id: str
    head_sha: str
    decision: str
    reviewer: str
    reason: str
    base_sha: str = ""
    rule_version: str = "v1"
    can_merge_to_main: bool = False
    auth_verified: bool = False
    comment_id: int = 0
    supersedes_verdict_id: str = ""
    replacement_decision: str = ""

    def is_valid_for(self, target_head_sha: str, target_base_sha: str = "") -> bool:
        text_fields = (
            self.claim_id, self.head_sha, self.base_sha, self.decision, self.reviewer,
            self.reason, self.rule_version, self.supersedes_verdict_id, self.replacement_decision,
            target_head_sha, target_base_sha,
        )
        if not all(isinstance(value, str) for value in text_fields) or not self.claim_id:
            return False
        if self.auth_verified is not True or self.can_merge_to_main is not True:
            return False
        if not isinstance(self.reviewer, str) or not self.reviewer.strip():
            return False
        if not self.head_sha or self.head_sha != target_head_sha:
            return False
        if not self.base_sha or self.base_sha != target_base_sha:
            return False
        if not self.reason or not self.reason.strip():
            return False
        if self.rule_version != "v1":
            return False
        if self.decision in ("RETRACT", "SUPERSEDE") and not self.supersedes_verdict_id:
            return False
        if self.decision == "SUPERSEDE" and self.replacement_decision not in (
            "CONFIRMED_VIOLATION", "FALSE_POSITIVE",
        ):
            return False
        return self.decision in (
            "CONFIRMED_VIOLATION", "FALSE_POSITIVE", "RETRACT", "SUPERSEDE", "CONFLICTING",
        )


@dataclass
class ReviewSnapshot:
    """綁定 PR 特定 Base/Head Commit SHA 的唯讀審查快照，嚴格分離完整檔案內容與局部 Patch。"""

    files: list[dict[str, Any]]
    head_sha: str = ""
    base_sha: str = ""
    repo_name: str = ""
    content_fetcher: Callable[[str, str, str], str | None] | None = None
    max_extra_tokens: int = 32000
    extra_tokens_used: int = 0
    _file_map: dict[str, dict[str, Any]] = field(init=False, default_factory=dict)
    _changed_lines_map: dict[str, set[int]] = field(init=False, default_factory=dict)
    _repo_searcher: Callable[[str], list[str]] | None = field(init=False, default=None)
    _content_cache: dict[tuple[str, str], str] = field(init=False, default_factory=dict)

    def __post_init__(self) -> None:
        for file_item in self.files:
            fname = str(file_item.get("filename", "")).replace("\\", "/")
            if fname:
                self._file_map[fname] = file_item
                patch_text = str(file_item.get("patch") or "")
                self._changed_lines_map[fname] = extract_changed_lines(patch_text)
        self._repo_searcher = self._search_snapshot

    def _search_snapshot(self, pattern: str) -> list[str]:
        """只搜尋此快照已取得的 Head 內容；未命中不代表全儲存庫不存在。"""
        compiled = re.compile(pattern)
        return [
            path for path in self._file_map
            if compiled.search(self.get_file_content(path))
        ]

    @property
    def repo_searcher(self) -> Callable[[str], list[str]]:
        assert self._repo_searcher is not None
        return self._repo_searcher

    def find_file_entry(self, rel_path: str) -> dict[str, Any] | None:
        norm = rel_path.replace("\\", "/").lstrip("/")
        if norm in self._file_map:
            return self._file_map[norm]
        matches = [val for key, val in self._file_map.items() if key.endswith("/" + norm)]
        if len(matches) == 1:
            return matches[0]
        return None

    def get_file_content(self, rel_path: str, ref_sha: str = "") -> str:
        """僅回傳指定版本之真實完整檔案內容；若僅有局部 Patch 且無 content_fetcher，回傳空字串。"""
        entry = self.find_file_entry(rel_path)
        canonical_path = str(entry.get("filename", rel_path)) if entry else rel_path
        target_sha = ref_sha or self.head_sha
        if ref_sha and ref_sha not in (self.head_sha, self.base_sha):
            return ""
        is_base = bool(ref_sha and self.base_sha and ref_sha == self.base_sha)
        cache_key = "base_full_content" if is_base else "full_content"
        identity = (canonical_path, target_sha)
        if identity in self._content_cache:
            return self._content_cache[identity]

        if entry is not None:
            cached = entry.get(cache_key) or (None if is_base else entry.get("content"))
            if isinstance(cached, str) and cached.strip():
                return self._remember_content(identity, cached)

        if self.content_fetcher is not None and target_sha:
            fetched = self.content_fetcher(self.repo_name, canonical_path, target_sha)
            if isinstance(fetched, str) and fetched.strip():
                return self._remember_content(identity, fetched)
        return ""

    def _remember_content(self, identity: tuple[str, str], content: str) -> str:
        estimated = max(1, len(content.encode("utf-8")))
        if self.extra_tokens_used + estimated > self.max_extra_tokens:
            return ""
        self.extra_tokens_used += estimated
        self._content_cache[identity] = content
        return content

    def get_patch_text(self, rel_path: str) -> str:
        entry = self.find_file_entry(rel_path)
        return str(entry.get("patch") or "") if entry is not None else ""

    def get_lines_window(self, rel_path: str, center_line: int, radius: int = 8) -> str:
        content = self.get_file_content(rel_path)
        if not content:
            return ""
        lines = content.splitlines()
        if not lines:
            return ""
        start_idx = max(1, center_line - radius)
        end_idx = min(len(lines), max(1, center_line) + radius)
        return "\n".join(
            f"{line_idx}: {lines[line_idx - 1]}" for line_idx in range(start_idx, end_idx + 1)
        )

    def get_changed_lines(self, rel_path: str) -> set[int]:
        entry = self.find_file_entry(rel_path)
        if entry is None:
            return set()
        fname = str(entry.get("filename", "")).replace("\\", "/")
        return self._changed_lines_map.get(fname, set())

    def is_line_changed_or_nearby(self, rel_path: str, line_num: int, margin: int = 3) -> bool:
        changed = self.get_changed_lines(rel_path)
        if not changed:
            entry = self.find_file_entry(rel_path)
            return bool(entry and not entry.get("patch"))
        return any(abs(line_num - ch_line) <= margin for ch_line in changed)

    def verify_evidence_exists(self, rel_path: str, line_num: int, evidence: str) -> bool:
        ev_clean = str(evidence or "").strip()
        if not ev_clean:
            return False
        content = self.get_file_content(rel_path)
        if not content or line_num < 1 or line_num > len(content.splitlines()):
            return False
        lines = content.splitlines()
        window = "\n".join(lines[max(0, line_num - 3):line_num + 2])
        return " ".join(ev_clean.split()) in " ".join(window.split())

    @staticmethod
    def parse_location(location: str) -> tuple[str, int]:
        loc_str = str(location or "").strip()
        if ":" not in loc_str:
            return loc_str, 1
        path_part, line_part = loc_str.rsplit(":", 1)
        if not re.fullmatch(r"[1-9][0-9]*", line_part.strip()):
            return path_part.strip(), 0
        return path_part.strip(), int(line_part)


class RuleInspector:
    """確定性規則查證工具基底介面。"""

    rule_name: str = "GENERIC_RULE"
    supports_compliance_proof: bool = True

    def inspect(
        self, finding: dict[str, Any], snapshot: ReviewSnapshot
    ) -> RuleInspectionResult:
        raise NotImplementedError
