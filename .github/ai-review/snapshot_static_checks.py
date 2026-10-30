import time
from typing import Any

import requests

from check_rules import RULE_DEAD_CODE_REPOSITORY, make_finding
from engine import evaluate, validate_finding
from github_content import fetch_file_content_fallback
from repository_call_search import SnapshotRepositorySearch
from review_snapshot import ReviewSnapshot
from reporter import evaluate_shadow_review
from source_budget import SourceReadBudget
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


def run_snapshot_static_checks(
    files: list[dict[str, Any]], repo_name: str, head_sha: str,
    getter: Any, *, deadline: float | None = None, base_sha: str = "",
    verdicts: dict | None = None, audit: list[dict[str, Any]] | None = None,
    source_budget: SourceReadBudget | None = None,
) -> list[dict[str, Any]]:
    """影子靜態檢查不混入本機內容；缺必要資料時保留明確阻擋。"""
    deadline = time.monotonic() + 900 if deadline is None else deadline
    pending: list[dict[str, Any]] = []
    full_files: list[dict[str, Any]] = []
    snapshot = ReviewSnapshot(
        [dict(item, full_content=None, content=None) for item in files],
        head_sha=head_sha, base_sha=base_sha, repo_name=repo_name,
        source_budget=source_budget or SourceReadBudget(deadline=deadline),
        repository_getter=getter,
        content_fetcher=lambda repo, path, sha: fetch_file_content_fallback(
            repo, path, sha, gh_get_fn=getter,
        ),
    )
    for item in files:
        path = item.get("filename") or item.get("path") or ""
        if not path or item.get("status") in ("removed", "deleted"):
            continue
        if not head_sha or not repo_name or time.monotonic() >= deadline:
            pending.append(incomplete_finding(path, "缺少固定 SHA 或全場期限已到"))
            continue
        # 不信任未附來源版本的入參 content；一律依 Head SHA 重新讀取。
        content = snapshot.get_file_content(path)
        if not content:
            pending.append(incomplete_finding(path, "無法取得固定 Head SHA 完整檔案"))
            continue
        full_files.append(dict(item, filename=path, full_content=content, content=content))

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
    verified = verify_static_claims(
        findings + pending, snapshot, verdicts or {}, deadline,
        audit if audit is not None else [],
    )
    if audit is not None:
        audit.extend(dict(result, rule_key=RULE_DEAD_CODE_REPOSITORY,
                          location=path, method=method)
                     for (path, method), result in searcher.outcomes.items())
    return verified


def evaluate_snapshot_shadow_review(
    results: list[dict[str, Any]], files: list[dict[str, Any]], expected: list[str],
    reviewed: list[str], policy: dict[str, Any], repo: str, sha: str,
    getter: Any, deadline: float, base_sha: str = "", pr_number: int = 0,
    source_budget: SourceReadBudget | None = None,
) -> dict[str, Any] | None:
    if not any("shadow_verified_findings" in result for result in results):
        return None
    audit: list[dict[str, Any]] = []
    findings = run_snapshot_static_checks(
        files, repo, sha, getter, deadline=deadline, base_sha=base_sha,
        verdicts=configured_verdicts(repo, pr_number, sha, base_sha), audit=audit,
        source_budget=source_budget,
    )
    result = evaluate_shadow_review(results, findings, expected, reviewed, policy)
    if result is not None:
        result["static_claim_audit"] = audit
    return result


def evaluate_verified_review(
    results: list[dict[str, Any]], files: list[dict[str, Any]], expected: list[str],
    reviewed: list[str], policy: dict[str, Any], repo: str, sha: str,
    getter: Any, deadline: float, base_sha: str = "", pr_number: int = 0,
    source_budget: SourceReadBudget | None = None,
) -> dict[str, Any]:
    """正式閘門僅使用工具查證結果；不回退使用未查證的模型或本機掃描。"""
    if not results or not sha or not base_sha:
        raise RuntimeError("正式查證缺少批次結果或固定 Base／Head SHA")
    for batch in results:
        verified = batch.get("shadow_verified_findings")
        if not isinstance(verified, list) or any(
            not validate_finding(item, allowed_files=expected) for item in verified
        ):
            raise RuntimeError("正式查證批次結果缺失或格式不符；禁止退回原始模型 Finding")
    result = evaluate_snapshot_shadow_review(
        results, files, expected, reviewed, policy, repo, sha, getter, deadline,
        base_sha, pr_number, source_budget=source_budget,
    )
    if not isinstance(result, dict) or not isinstance(result.get("findings"), list):
        raise RuntimeError("正式查證未完成；禁止套用舊引擎結果")
    if any(not validate_finding(item, allowed_files=expected) for item in result["findings"]):
        raise RuntimeError("固定版本靜態查證回傳不合法 Finding")
    # 由確定性政策重新計算，不讓候選回傳的 decision 或旗標自行放行。
    selected = evaluate(result["findings"], expected, reviewed, policy)
    selected["static_claim_audit"] = result.get("static_claim_audit", [])
    selected["gate_role"] = "active"
    return selected
