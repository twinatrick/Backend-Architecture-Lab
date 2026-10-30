import base64
import sys
import time
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from claim_verifier import VerificationBudget, inspect_findings_as_claims
from method_fact_inspector import MethodFactInspector
from review_snapshot import InspectionStatus, ReviewSnapshot
from rule_registry import RuleRegistry
from snapshot_static_checks import run_snapshot_static_checks
from source_budget import SourceReadBudget


def finding(problem: str) -> dict:
    return {"location": "Service.java:3", "rule": "BUSINESS_LOGIC", "problem": problem,
            "evidence": "private void doRebind() {}", "severity": "HIGH", "confidence": "HIGH",
            "category": "ARCHITECTURE", "risk": "操作入口影響資源", "recommendation": "檢查"}


@pytest.mark.parametrize("problem", [
    "`doRebind` 為公開方法", "`doWrongName` 為公開方法",
])
def test_private_or_nonexistent_method_fact_can_be_refuted_without_clearing_business(problem):
    snapshot = ReviewSnapshot([{"filename": "Service.java", "full_content":
                               "class Service {\n public void entry() {}\n"
                               " private void doRebind() {}\n}"}])
    original = finding(problem)
    result = MethodFactInspector().inspect(original, snapshot)
    assert result.status == InspectionStatus.COMPLIANT
    claims = RuleRegistry().split_finding_into_claims(original)
    assert {claim.rule_key for claim in claims} == {"RULE_JAVA_METHOD_FACT", "BUSINESS_LOGIC"}


def test_actual_public_method_does_not_itself_prove_business_violation():
    snapshot = ReviewSnapshot([{"filename": "Service.java", "full_content":
                               "class Service {\n public void doRebind() {}\n}"}])
    assert MethodFactInspector().inspect(
        finding("`doRebind` 為公開方法"), snapshot,
    ).status == InspectionStatus.INCONCLUSIVE


def test_41kb_file_is_read_even_after_model_context_has_been_spent():
    code = "class Service {\n" + " // padding\n" * 4000 + " java.util.Date date;\n}"
    line = len(code.splitlines()) - 1
    snapshot = ReviewSnapshot([{"filename": "Service.java", "full_content": code}])
    budget = VerificationBudget(extra_context_tokens_used=32000)
    original = dict(finding("FQN"), rule="RULE_PROHIBIT_FQN", location=f"Service.java:{line}",
                    evidence="java.util.Date date;")
    results = inspect_findings_as_claims([original], snapshot, RuleRegistry(), budget)
    assert results[0][1].status == InspectionStatus.VIOLATION
    assert budget.extra_context_tokens_used == 32000
    assert snapshot.source_budget.bytes_used > 32000


def test_static_stage_uses_same_head_cache_and_budget_as_other_batch_snapshots():
    code = "VALUE = 1\n"
    budget = SourceReadBudget()
    getter = MagicMock(return_value={"encoding": "base64", "content":
                                    base64.b64encode(code.encode()).decode()})
    for _ in range(2):
        assert run_snapshot_static_checks(
            [{"filename": "value.py", "status": "added"}], "owner/repo", "head", getter,
            source_budget=budget,
        ) == []
    getter.assert_called_once()
    assert budget.bytes_used == len(code.encode())


def test_parse_cache_is_partitioned_by_repo_sha_path_and_tool():
    budget = SourceReadBudget()
    parser = MagicMock(return_value=object())
    first = budget.parse_once(("java", "repo", "head", "Path"), parser)
    assert budget.parse_once(("java", "repo", "head", "Path"), parser) is first
    parser.assert_called_once()
    budget.parse_once(("java", "repo", "base", "Path"), parser)
    assert parser.call_count == 2


def test_expired_source_budget_prevents_read_and_parse_of_new_source():
    budget = SourceReadBudget(deadline=time.monotonic() - 1)
    loader = MagicMock()
    assert budget.read("repo", "head", "Path", loader) == ""
    assert budget.parse_once(("tool", "repo", "head", "Path"), loader) is None
    loader.assert_not_called()
