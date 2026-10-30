import re
import time
from typing import Any

import requests

from check_rules import RULE_DEAD_CODE_REPOSITORY, make_finding
from github_content import fetch_file_content_fallback
from java_code_analyzer import strip_comments_and_strings
from review_snapshot import ReviewSnapshot
from reporter import evaluate_shadow_review
from static_checks import run_static_checks
from static_claim_gate import verify_static_claims
from verdict_ledger import configured_verdicts


def incomplete_finding(path: str, reason: str) -> dict[str, Any]:
    """必要上下文未完成不是已確認違規，亦不得當作合規放行。"""
    return make_finding(
        path, 1, "HIGH", "COMPLIANCE", "REVIEW_INCOMPLETE",
        "固定版本靜態查證未完成", reason,
        "缺少必要原始碼或完整呼叫端範圍，無法確認本次變更符合規範",
        "取得同一 Head SHA 的完整上下文後重新查證或提交人工裁定",
    )


class SnapshotRepositorySearch:
    """只讀固定 SHA；不以本機 master 或不完整負向搜尋證明死代碼。"""

    def __init__(self, snapshot: ReviewSnapshot, getter: Any, deadline: float) -> None:
        self.snapshot = snapshot
        self.getter = getter
        self.deadline = deadline
        self.paths: list[str] | None = None
        self.incomplete: set[str] = set()

    def _load_tree(self) -> list[str] | None:
        if time.monotonic() >= self.deadline:
            return None
        if self.paths is not None:
            return self.paths
        tree = self.getter(
            f"https://api.github.com/repos/{self.snapshot.repo_name}/git/trees/"
            f"{self.snapshot.head_sha}", params={"recursive": "1"},
        )
        if not isinstance(tree, dict) or tree.get("truncated") is not False:
            return None
        entries = tree.get("tree")
        if not isinstance(entries, list):
            return None
        if any(not isinstance(item, dict) for item in entries):
            return None
        self.paths = [
            item["path"] for item in entries
            if item.get("type") == "blob" and isinstance(item.get("path"), str)
            and item["path"].endswith(".java")
        ]
        return self.paths

    def __call__(self, method_name: str, repository_path: str) -> list[str]:
        try:
            paths = self._load_tree()
            if paths is None:
                self.incomplete.add(repository_path)
                return []
            pattern = re.compile(rf"\b{re.escape(method_name)}\b")
            for path in paths:
                if path == repository_path:
                    continue
                if time.monotonic() >= self.deadline:
                    self.incomplete.add(repository_path)
                    return []
                content = self.snapshot.get_file_content(path)
                if not content:
                    self.incomplete.add(repository_path)
                    continue
                stripped, complete = strip_comments_and_strings(content)
                if not complete:
                    self.incomplete.add(repository_path)
                    continue
                if pattern.search(stripped):
                    # 字面引用不等於已解析到同一 Repository 的呼叫；保留待人工查證。
                    self.incomplete.add(repository_path)
                    return [path]
            return []
        except (requests.RequestException, ValueError, KeyError):
            self.incomplete.add(repository_path)
            return []


def run_snapshot_static_checks(
    files: list[dict[str, Any]], repo_name: str, head_sha: str,
    getter: Any, *, deadline: float | None = None, base_sha: str = "",
    verdicts: dict | None = None, audit: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    """影子靜態檢查不混入本機內容；缺必要資料時保留明確阻擋。"""
    deadline = time.monotonic() + 900 if deadline is None else deadline
    pending: list[dict[str, Any]] = []
    full_files: list[dict[str, Any]] = []
    for item in files:
        path = item.get("filename") or item.get("path") or ""
        if not path or item.get("status") in ("removed", "deleted"):
            continue
        if not head_sha or not repo_name or time.monotonic() >= deadline:
            pending.append(incomplete_finding(path, "缺少固定 SHA 或全場期限已到"))
            continue
        # 不信任未附來源版本的入參 content；一律依 Head SHA 重新讀取。
        content = fetch_file_content_fallback(repo_name, path, head_sha, gh_get_fn=getter)
        if content is None:
            pending.append(incomplete_finding(path, "無法取得固定 Head SHA 完整檔案"))
            continue
        full_files.append(dict(item, filename=path, full_content=content, content=content))

    snapshot = ReviewSnapshot(
        full_files, head_sha=head_sha, base_sha=base_sha, repo_name=repo_name,
        content_fetcher=lambda repo, path, sha: fetch_file_content_fallback(
            repo, path, sha, gh_get_fn=getter,
        ),
    )
    searcher = SnapshotRepositorySearch(snapshot, getter, deadline)
    findings = run_static_checks(
        full_files, repo_searcher=searcher, allow_local_fallback=False,
    )
    findings = [
        item for item in findings
        if not (
            item.get("rule") == RULE_DEAD_CODE_REPOSITORY
            and str(item.get("location", "")).rsplit(":", 1)[0] in searcher.incomplete
        )
    ]
    pending.extend(
        incomplete_finding(path, "固定 Head SHA 全儲存庫呼叫端搜尋未完成")
        for path in sorted(searcher.incomplete)
    )
    return verify_static_claims(
        findings + pending, snapshot, verdicts or {}, deadline,
        audit if audit is not None else [],
    )


def evaluate_snapshot_shadow_review(
    results: list[dict[str, Any]], files: list[dict[str, Any]], expected: list[str],
    reviewed: list[str], policy: dict[str, Any], repo: str, sha: str,
    getter: Any, deadline: float, base_sha: str = "", pr_number: int = 0,
) -> dict[str, Any] | None:
    if not any("shadow_verified_findings" in result for result in results):
        return None
    audit: list[dict[str, Any]] = []
    findings = run_snapshot_static_checks(
        files, repo, sha, getter, deadline=deadline, base_sha=base_sha,
        verdicts=configured_verdicts(repo, pr_number, sha, base_sha), audit=audit,
    )
    result = evaluate_shadow_review(results, findings, expected, reviewed, policy)
    if result is not None:
        result["static_claim_audit"] = audit
    return result
