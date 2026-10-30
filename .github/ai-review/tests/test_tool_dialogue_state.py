import sys
import time
from pathlib import Path
from unittest.mock import MagicMock

import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from change_attribution import AttributionStatus, attribute_location
from claim_attribution import attribute_confirmed_claims
from claim_verifier import VerificationBudget
from native_tool_dialogue import ToolDialogueState, run_native_tool_dialogue
from review_snapshot import ReviewSnapshot
from rule_registry import RuleRegistry


def test_model_fallback_preserves_tool_results_and_budget():
    first = MagicMock(provider="groq", model="first")
    second = MagicMock(provider="gemini", model="second")
    first.exchange.side_effect = requests.Timeout()
    second.exchange.side_effect = [
        {"role": "assistant", "calls": [{"id": "one", "name": "inspect_rule_claim",
          "arguments": {"claim_id": "claim", "rule_key": "RULE"}}]},
        {"role": "assistant", "calls": [], "text": "done"},
    ]
    state = ToolDialogueState(
        [{"role": "user", "text": "查證"}], {"claim": "RULE"},
        verified_models={("groq", "first"), ("gemini", "second")},
    )
    budget = VerificationBudget()
    executor = MagicMock(return_value={"status": "VIOLATION"})
    assert run_native_tool_dialogue(state, [first, second], executor, budget) == "done"
    assert budget.tool_calls_used == 1
    assert state.history[-1]["result"]["status"] == "VIOLATION"
    executor.assert_called_once_with("claim", "RULE")


def test_expired_session_never_calls_provider():
    transport = MagicMock()
    state = ToolDialogueState([], {})
    budget = VerificationBudget(session_start_monotonic=time.monotonic() - 901)
    assert run_native_tool_dialogue(state, [transport], MagicMock(), budget) is None
    transport.exchange.assert_not_called()


def test_base_head_mapping_handles_line_shift_without_claiming_legacy_clearance():
    snapshot = ReviewSnapshot([{
        "filename": "A.java", "status": "modified", "full_content": "new\nold\nend",
        "base_full_content": "old\nend",
    }], head_sha="head", base_sha="base")
    old = attribute_location(snapshot, "A.java:2")
    assert old.status == AttributionStatus.PREEXISTING_LOCATION
    assert old.base_line == 1
    assert attribute_location(snapshot, "A.java:1").status == AttributionStatus.DIRECT_CHANGE


def test_missing_base_never_means_preexisting():
    snapshot = ReviewSnapshot([{"filename": "A.java", "full_content": "old"}],
                              head_sha="head", base_sha="base")
    assert attribute_location(snapshot, "A.java:1").status == AttributionStatus.INCONCLUSIVE


def test_unchanged_fqn_is_legacy_only_after_base_rule_confirmation():
    source = "class Demo {\njava.util.Date value;\n}"
    snapshot = ReviewSnapshot([{
        "filename": "Demo.java", "full_content": "// new\n" + source,
        "base_full_content": source, "status": "modified",
    }], head_sha="head", base_sha="base")
    finding = {"rule": "RULE_PROHIBIT_FQN", "location": "Demo.java:3",
               "evidence": "java.util.Date value;", "problem": "FQN"}
    registry = RuleRegistry()
    claim = registry.split_finding_into_claims(finding)[0]
    result = registry.get_inspector(claim.rule_key).inspect(finding, snapshot)
    attributed = attribute_confirmed_claims(
        [(claim, result)], snapshot, registry, VerificationBudget(),
    )
    assert attributed[0][1].status.value == "NOT_APPLICABLE"
    assert "LEGACY_UNTOUCHED" in attributed[0][1].verdict_reason


def test_new_fqn_keeps_confirmed_violation():
    snapshot = ReviewSnapshot([{
        "filename": "Demo.java", "full_content": "class Demo {\njava.util.Date value;\n}",
        "base_full_content": "class Demo {\nDate value;\n}", "status": "modified",
    }], head_sha="head", base_sha="base")
    finding = {"rule": "RULE_PROHIBIT_FQN", "location": "Demo.java:2",
               "evidence": "java.util.Date value;", "problem": "FQN"}
    registry = RuleRegistry()
    claim = registry.split_finding_into_claims(finding)[0]
    result = registry.get_inspector(claim.rule_key).inspect(finding, snapshot)
    attributed = attribute_confirmed_claims(
        [(claim, result)], snapshot, registry, VerificationBudget(),
    )
    assert attributed[0][1].status.value == "VIOLATION"
