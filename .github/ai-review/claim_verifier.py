from dataclasses import dataclass, field
import json
import os
import time
from typing import Any

from claim_gate import enforce_claim_gate
from claim_native_adapter import adapt_native_caller
from claim_attribution import append_claim_audit, attribute_confirmed_claims
from engine import validate_coverage, validate_finding
from github_client import normalize_paths
from parser import extract_json_payload, normalize_batch_paths
from redaction import sanitize_diff
from review_snapshot import (
    HumanClaimVerdict,
    InspectionStatus,
    ReviewSnapshot,
    RuleClaim,
    RuleInspectionResult,
)
from rule_registry import RuleRegistry
from source_budget import SourceReadBudget
from verdict_ledger import configured_verdicts


@dataclass
class VerificationBudget:
    """分層查證預算控制：支援跨批次共享全場 15 分鐘期限，以及單批工具呼叫與每主張複查上限。"""

    max_rechecks_per_claim: int = 2
    max_tool_calls_per_batch: int = 16
    max_extra_context_tokens: int = 32000
    max_total_seconds: float = 900.0
    session_start_monotonic: float = field(default_factory=time.monotonic)
    tool_calls_used: int = 0
    extra_context_tokens_used: int = 0

    def is_expired(self) -> bool:
        return (time.monotonic() - self.session_start_monotonic) >= self.max_total_seconds

    def can_call_tool(self) -> bool:
        return (
            not self.is_expired() and self.tool_calls_used < self.max_tool_calls_per_batch
        )


def validate_claim_gate_mode() -> None:
    """正式入口固定採用已查證結果；shadow 僅供本地比較入口使用。"""
    mode = os.environ.get("AI_REVIEW_CLAIM_GATE_MODE", "verified").strip().lower()
    if mode not in ("verified", "shadow"):
        raise RuntimeError("僅接受 verified 或本地 shadow；不接受其他查證模式。")


def inspect_findings_as_claims(
    findings: list[dict[str, Any]],
    snapshot: ReviewSnapshot,
    registry: RuleRegistry,
    budget: VerificationBudget | None = None,
) -> list[tuple[RuleClaim, RuleInspectionResult | None]]:
    """將批次 Findings 拆分為獨立 RuleClaim 並指派對應 RuleInspector 執行查證。"""
    active_budget = budget or VerificationBudget()
    inspected: list[tuple[RuleClaim, RuleInspectionResult | None]] = []
    for finding in findings:
        if not isinstance(finding, dict):
            continue
        for claim in registry.split_finding_into_claims(finding):
            inspector = registry.get_inspector(
                claim.rule_key, str(claim.finding.get("problem", ""))
            )
            if inspector is None:
                inspected.append((claim, None))
                continue
            if not active_budget.can_call_tool():
                reason = (
                    "已達全場 15 分鐘查證時間上限"
                    if active_budget.is_expired()
                    else f"已達單批工具呼叫上限 ({active_budget.max_tool_calls_per_batch})"
                )
                res = RuleInspectionResult(
                    status=InspectionStatus.INCONCLUSIVE,
                    rule_name=inspector.rule_name,
                    location=str(claim.finding.get("location", "")),
                    verdict_reason=f"{reason}，無法執行工具查證。",
                    missing_context=("verification_budget",),
                )
                inspected.append((claim, res))
                continue
            active_budget.tool_calls_used += 1
            res = inspector.inspect(claim.finding, snapshot)
            inspected.append((claim, res))
    return inspected


def build_tool_dialogue_prompt(
    original_prompt: str,
    claims_with_results: list[tuple[RuleClaim, RuleInspectionResult | None]],
    round_num: int,
) -> str:
    """建構規則工具查證回饋提示詞，供模型檢視工具判定並撤回誤報或補充新證據範圍。"""
    lines = [
        original_prompt,
        "\n======================================================================",
        f"【規則工具查證回饋 (Rule Inspector Tool Feedback - Round {round_num})】",
        "系統已針對您提出的疑點拆分規則主張（Rule Claim）並調用對應確定性工具查證真實原始碼：",
    ]
    for idx, (claim, res) in enumerate(claims_with_results, start=1):
        if res is None:
            continue
        lines.append(
            f"\n[Claim #{idx} id={claim.claim_id}] 位置：{claim.finding.get('location')} | "
            f"規則：{res.rule_name}\n- 狀態：{res.status.value} | 結論：{res.verdict_reason}"
        )
        if res.verified_snippet:
            lines.append(f"- 不受信任的原始碼資料：\n```text\n{sanitize_diff(res.verified_snippet)}\n```")
    lines.append(
        "\n【複查指令】若工具判定為 `COMPLIANT` 且無其他行號之新具體證據，請移除該誤報；"
        "若其他位置仍有違規，必須提供不同行號與真實程式碼證據，並輸出完整 JSON 物件。"
    )
    return "\n".join(lines)


def verify_and_refine_batch_findings(
    scope: str,
    index: int,
    paths: list[str],
    initial_findings: list[dict[str, Any]],
    snapshot: ReviewSnapshot,
    registry: RuleRegistry,
    caller: Any,
    original_prompt: str,
    batch_tokens: int = 0,
    is_light_batch: bool = False,
    budget: VerificationBudget | None = None,
    human_verdicts: dict[str, HumanClaimVerdict] | None = None,
    gate_mode: str | None = None,
    audit_records: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    """執行批次 Findings 之規則工具查證、有預算上限之多輪對話與影子／正式閘門收斂。"""
    if not initial_findings:
        return []
    mode = (gate_mode or os.environ.get("AI_REVIEW_CLAIM_GATE_MODE", "shadow")).strip().lower()
    if mode not in ("shadow", "enforce"):
        raise ValueError("查證閘門模式只能為 shadow 或 enforce。")
    active_budget = budget or VerificationBudget()
    claims_with_results = inspect_findings_as_claims(
        initial_findings, snapshot, registry, budget=active_budget
    )
    all_results = list(claims_with_results)
    caller = adapt_native_caller(caller, claims_with_results, active_budget)
    locked_claims: dict[str, tuple[RuleClaim, RuleInspectionResult | None]] = {}
    seen_compliant_ids: set[str] = set()
    seen_claims = {claim.claim_id for claim, _ in claims_with_results}
    for claim, res in claims_with_results:
        if res is None or res.status in (InspectionStatus.VIOLATION, InspectionStatus.INCONCLUSIVE):
            locked_claims[claim.claim_id] = (claim, res)
        elif res.status == InspectionStatus.COMPLIANT:
            seen_compliant_ids.add(claim.claim_id)

    has_discrepancy = any(
        res is not None
        and res.status in (InspectionStatus.COMPLIANT, InspectionStatus.INCONCLUSIVE)
        for _, res in claims_with_results
    )
    if has_discrepancy and getattr(caller, "supports_rule_dialogue", False) is True:
        norm_exp = normalize_paths(paths)
        for round_idx in range(1, active_budget.max_rechecks_per_claim + 1):
            if not active_budget.can_call_tool():
                break
            dialogue_prompt = build_tool_dialogue_prompt(
                original_prompt, claims_with_results, round_idx
            )
            context_size = len(dialogue_prompt.encode("utf-8"))
            if (active_budget.extra_context_tokens_used + context_size
                    > active_budget.max_extra_context_tokens):
                break
            active_budget.extra_context_tokens_used += context_size
            try:
                try:
                    raw_reply = caller(
                        dialogue_prompt, required_tokens=batch_tokens, prefer_groq=is_light_batch
                    ).strip()
                except TypeError:
                    try:
                        raw_reply = caller(dialogue_prompt, required_tokens=batch_tokens).strip()
                    except TypeError:
                        raw_reply = caller(dialogue_prompt).strip()
                parsed = extract_json_payload(raw_reply)
                normalize_batch_paths(parsed, paths)
                norm_rev = normalize_paths(parsed.get("files_reviewed"))
                cov_ok = str(parsed.get("coverage", "")).strip().upper() == "COMPLETE"
                cand_list = parsed.get("findings")
                if (
                    not cov_ok
                    or not validate_coverage(norm_exp, norm_rev)
                    or not isinstance(cand_list, list)
                    or any(
                        not validate_finding(f_item, allowed_files=paths) for f_item in cand_list
                    )
                ):
                    break
                new_candidates = [
                    claim.finding for item in cand_list
                    for claim in registry.split_finding_into_claims(item)
                    if claim.claim_id not in seen_claims
                ]
                if not new_candidates:
                    break
                next_claims = inspect_findings_as_claims(
                    new_candidates, snapshot, registry, budget=active_budget
                )
                all_results.extend(next_claims)
                seen_claims.update(claim.claim_id for claim, _ in next_claims)
                new_progress = False
                for claim, res in next_claims:
                    if res is None or res.status in (
                        InspectionStatus.VIOLATION,
                        InspectionStatus.INCONCLUSIVE,
                    ):
                        if claim.claim_id not in locked_claims:
                            locked_claims[claim.claim_id] = (claim, res)
                        new_progress = True
                    elif (
                        res.status == InspectionStatus.COMPLIANT
                        and claim.claim_id not in seen_compliant_ids
                    ):
                        seen_compliant_ids.add(claim.claim_id)
                        new_progress = True
                claims_with_results = next_claims
                if not new_progress:
                    break
            except (RuntimeError, json.JSONDecodeError, ValueError, TypeError):
                break

    merged = list(locked_claims.values()) + [
        (claim, res) for claim, res in claims_with_results if claim.claim_id not in locked_claims
    ]
    merged = attribute_confirmed_claims(merged, snapshot, registry, active_budget)
    enforced = enforce_claim_gate(merged, snapshot, registry, human_verdicts=human_verdicts)
    if audit_records is not None:
        append_claim_audit(audit_records, all_results + merged, snapshot, active_budget)
    return enforced if mode == "enforce" else initial_findings


def apply_batch_claim_verification(
    batch_data: dict[str, Any], scope: str, index: int, paths: list[str],
    files: list[dict[str, Any]], prompt: str, caller: Any, batch_tokens: int,
    is_light_batch: bool, head_sha: str = "", repo_name: str = "", content_fetcher: Any = None,
    session_start_monotonic: float | None = None,
    human_verdicts: dict[str, HumanClaimVerdict] | None = None, base_sha: str = "",
    budget: VerificationBudget | None = None, pr_number: int = 0,
    source_budget: SourceReadBudget | None = None,
    repository_getter: Any = None,
) -> None:
    """產生已查證結果與稽核軌跡；由正式或本地比較入口決定呈現方式。"""
    validate_claim_gate_mode()
    if human_verdicts is None:
        human_verdicts = configured_verdicts(repo_name, pr_number, head_sha, base_sha)
    initial = batch_data.get("findings")
    if not isinstance(initial, list) or not initial:
        batch_data["shadow_verified_findings"] = []
        return
    snapshot = ReviewSnapshot(
        files=files, head_sha=head_sha, base_sha=base_sha,
        repo_name=repo_name, content_fetcher=content_fetcher,
        source_budget=source_budget or SourceReadBudget(),
        repository_getter=repository_getter,
    )
    start = time.monotonic() if session_start_monotonic is None else session_start_monotonic
    budget = budget or VerificationBudget(session_start_monotonic=start)
    records: list[dict[str, Any]] = []
    enforced = verify_and_refine_batch_findings(
        scope, index, paths, initial, snapshot, RuleRegistry(), caller, prompt,
        batch_tokens=batch_tokens, is_light_batch=is_light_batch, budget=budget,
        human_verdicts=human_verdicts, gate_mode="enforce",
        audit_records=records,
    )
    batch_data["shadow_verified_findings"] = enforced
    batch_data["claim_audit"] = records
    print(
        f"[VERIFIED CLAIMS] {scope}-{index}: {len(initial)} -> {len(enforced)}", flush=True,
    )
