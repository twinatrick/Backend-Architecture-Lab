from typing import Any

from claim_attribution import append_claim_audit, attribute_confirmed_claims
from claim_gate import enforce_claim_gate
from claim_verifier import VerificationBudget
from review_snapshot import (
    InspectionStatus,
    ReviewSnapshot,
    RuleClaim,
    RuleInspectionResult,
    compute_claim_identity,
)
from rule_registry import RuleRegistry


def verify_static_claims(
    findings: list[dict[str, Any]], snapshot: ReviewSnapshot, verdicts: dict,
    deadline: float, audit: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """確定性結果獨立保留，亦須經快照歸責與同一人工裁定流程。"""
    registry = RuleRegistry()
    budget = VerificationBudget(session_start_monotonic=deadline - 900)
    records = []
    for finding in findings:
        location = finding["location"]
        rule_key = finding["rule"]
        claim = RuleClaim(compute_claim_identity(
            location, rule_key, finding["evidence"],
        ), rule_key, finding)
        incomplete = rule_key == "REVIEW_INCOMPLETE"
        # 此處來源是受信任掃描器，不接受模型自報的「工具已確認」。
        result = RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE if incomplete else InspectionStatus.VIOLATION,
            rule_key, location, finding["problem"], verified_snippet=finding["evidence"],
            missing_context=("snapshot_static_context",) if incomplete else (),
            confirmed_findings=() if incomplete else (finding,),
        )
        records.append((claim, result))
    records = attribute_confirmed_claims(records, snapshot, registry, budget)
    gated = enforce_claim_gate(
        records, snapshot=snapshot, registry=registry, human_verdicts=verdicts,
    )
    append_claim_audit(audit, records, snapshot, budget)
    return gated
