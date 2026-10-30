"""對局部語法規則以 Base 重驗歸責；未支援的影響鏈不可當作既有債放行。"""

from dataclasses import replace
from typing import Any

from change_attribution import AttributionStatus, attribute_location
from review_snapshot import InspectionStatus, ReviewSnapshot
from rule_inspectors import (
    AspectSelfInvocationRuleInspector,
    FqnRuleInspector,
    RestfulEndpointRuleInspector,
)
from rule_version import trusted_rule_version


def attribute_confirmed_claims(
    claims: list[Any], snapshot: ReviewSnapshot, registry: Any, budget: Any,
) -> list[Any]:
    """既有局部語法違規僅記入稽核；未知／業務依賴影響必須人工查證。"""
    output = []
    for claim, result in claims:
        if result is None or result.status != InspectionStatus.VIOLATION:
            output.append((claim, result))
            continue
        attribution = attribute_location(snapshot, result.location)
        if attribution.status == AttributionStatus.DIRECT_CHANGE:
            output.append((claim, result))
            continue
        inspector = registry.get_inspector(claim.rule_key)
        local_proof = isinstance(inspector, (
            FqnRuleInspector, RestfulEndpointRuleInspector, AspectSelfInvocationRuleInspector,
        ))
        if (attribution.status != AttributionStatus.PREEXISTING_LOCATION
                or not local_proof or not budget.can_call_tool()):
            output.append((claim, replace(
                result, status=InspectionStatus.INCONCLUSIVE,
                verdict_reason="已確認 Head 疑點，但尚未證明由本次變更引入或新造成影響。",
                missing_context=("base_head_impact_attribution",),
            )))
            continue
        path, _ = snapshot.parse_location(result.location)
        entry = snapshot.find_file_entry(path) or {}
        base_path = str(entry.get("previous_filename") or path)
        content = snapshot.get_file_content(base_path, snapshot.base_sha)
        base_snapshot = ReviewSnapshot(
            [{"filename": base_path, "full_content": content}],
            head_sha=snapshot.base_sha, repo_name=snapshot.repo_name,
            source_budget=snapshot.source_budget,
        )
        base_finding = dict(claim.finding, location=f"{base_path}:{attribution.base_line}")
        budget.tool_calls_used += 1
        prior = inspector.inspect(base_finding, base_snapshot)
        if prior.status == InspectionStatus.VIOLATION:
            result = replace(
                result, status=InspectionStatus.NOT_APPLICABLE,
                verdict_reason="[LEGACY_UNTOUCHED] Base 同位置已確認相同局部語法違規，另列既有債。",
            )
        elif prior.status not in (InspectionStatus.COMPLIANT, InspectionStatus.NOT_APPLICABLE):
            result = replace(
                result, status=InspectionStatus.INCONCLUSIVE,
                verdict_reason="Base 規則查證未完成，無法確認違規歸責。",
                missing_context=("base_rule_inspection",),
            )
        output.append((claim, result))
    return output


def append_claim_audit(records: list, results: list[Any], snapshot: Any, budget: Any) -> None:
    """每主張保留最新裁定與歸責；既有債亦保留於獨立稽核資料。"""
    latest = {claim.claim_id: (claim, result) for claim, result in results}
    for claim, result in latest.values():
        location = str(claim.finding.get("location", ""))
        attribution = attribute_location(snapshot, location)
        records.append({
            "claim_id": claim.claim_id, "rule_key": claim.rule_key, "location": location,
            "tool_version": trusted_rule_version(),
            "status": result.status.value if result else "UNTOOLED",
            "reason": result.verdict_reason if result else "無專屬查證工具",
            "missing_context": list(result.missing_context) if result else [],
            "attribution": attribution.status.value, "attribution_reason": attribution.reason,
            "base_line": attribution.base_line,
        })
