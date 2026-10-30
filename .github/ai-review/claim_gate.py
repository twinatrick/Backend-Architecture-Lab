from typing import Any

from review_snapshot import (
    HumanClaimVerdict,
    InspectionStatus,
    ReviewSnapshot,
    RuleClaim,
    RuleInspectionResult,
)
from rule_registry import RuleRegistry, SuspicionEligibilityAuditor


def enforce_claim_gate(
    claims_with_results: list[tuple[RuleClaim, RuleInspectionResult | None]],
    snapshot: ReviewSnapshot,
    registry: RuleRegistry,
    human_verdicts: dict[str, HumanClaimVerdict] | None = None,
) -> list[dict[str, Any]]:
    """分離工具違規、人工追查資格、讀取失敗與純推測；模型不得自己宣告工具違規。"""
    verified: list[dict[str, Any]] = []
    for claim, result in claims_with_results:
        finding = dict(claim.finding)
        verdict = (human_verdicts or {}).get(claim.claim_id)
        if (verdict and verdict.claim_id == claim.claim_id
                and verdict.is_valid_for(snapshot.head_sha, snapshot.base_sha)):
            if verdict.decision == "CONFLICTING":
                finding.update(
                    category="COMPLIANCE", severity="HIGH", confidence="HIGH",
                    problem=f"[HUMAN_VERDICT_CONFLICT:{claim.claim_id}] {verdict.reason}",
                )
                verified.append(finding)
                continue
            if verdict.decision == "FALSE_POSITIVE":
                continue
            if verdict.decision == "CONFIRMED_VIOLATION":
                finding["confidence"] = "HIGH"
                verified.append(finding)
                continue
        if result is not None:
            if result.status in (InspectionStatus.COMPLIANT, InspectionStatus.NOT_APPLICABLE):
                continue
            if result.status == InspectionStatus.VIOLATION:
                finding.update(
                    location=result.location, rule=result.rule_name,
                    problem=result.verdict_reason, evidence=result.verified_snippet,
                    confidence="HIGH",
                )
                verified.append(finding)
                continue
            if result.missing_context:
                finding.update(
                    category="COMPLIANCE", severity="HIGH", confidence="HIGH",
                    problem=f"[REVIEW_INCOMPLETE:{claim.claim_id}] {result.verdict_reason}",
                )
                verified.append(finding)
                continue
        eligible, reason = SuspicionEligibilityAuditor.audit(finding, snapshot)
        if eligible:
            finding.update(category="COMPLIANCE", severity="HIGH", confidence="HIGH")
            finding["problem"] = (
                f"[ELIGIBLE_FOR_HUMAN_VERDICT:{claim.claim_id}] {finding.get('problem', '')}"
            )
        else:
            path, line = snapshot.parse_location(str(finding.get("location", "")))
            content = snapshot.get_file_content(path)
            if not content:
                finding.update(
                    category="COMPLIANCE", severity="HIGH", confidence="HIGH",
                    problem=f"[REVIEW_INCOMPLETE:{claim.claim_id}] 無法讀取疑點必要完整內容",
                )
            elif not snapshot.verify_evidence_exists(path, line, str(finding.get("evidence", ""))):
                continue
            else:
                finding.update(severity="LOW", confidence="LOW")
                finding["problem"] = f"[SPECULATION_ONLY:{reason}] {finding.get('problem', '')}"
        verified.append(finding)
    return verified
