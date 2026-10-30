import base64
from dataclasses import replace
import json
from pathlib import Path
import sys

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from claim_gate import enforce_claim_gate
from java_code_analyzer import analyze_java_source
from review_snapshot import HumanClaimVerdict, InspectionStatus, ReviewSnapshot, RuleClaim
from rule_inspectors import AspectSelfInvocationRuleInspector
from rule_registry import RuleRegistry
from verdict_manager import (
    fetch_ledger_verdicts, parse_verdict_commands_from_comment, resolve_effective_verdicts,
    verify_main_branch_merge_authorization,
)


def _verdict(**changes):
    original = HumanClaimVerdict(
        claim_id="claim", head_sha="head", base_sha="base", decision="FALSE_POSITIVE",
        reviewer="reviewer", reason="已核對證據", auth_verified=True, can_merge_to_main=True,
        comment_id=1,
    )
    return replace(original, **changes)


@pytest.mark.parametrize("suffix", [
    " decision=CONFIRMED_VIOLATION", " unknown=value", " garbage", ' reason="未閉合',
])
def test_verdict_command_rejects_ambiguous_or_partially_parsed_input(suffix):
    command = (
        '/ai-verdict claim_id=claim decision=FALSE_POSITIVE head_sha=head base_sha=base '
        'reason="已核對證據"'
    )
    assert parse_verdict_commands_from_comment(command + suffix, "reviewer", 1) == []


def test_supersede_requires_and_applies_an_explicit_replacement():
    first = _verdict(decision="CONFIRMED_VIOLATION")
    invalid = _verdict(decision="SUPERSEDE", comment_id=2, supersedes_verdict_id="1")
    assert not invalid.is_valid_for("head", "base")
    replacement = replace(invalid, replacement_decision="FALSE_POSITIVE")
    effective = resolve_effective_verdicts([first, replacement], "head", "base")
    assert effective["claim"].decision == "FALSE_POSITIVE"
    assert effective["claim"].comment_id == 2
    nonexistent = replace(replacement, supersedes_verdict_id="999")
    assert resolve_effective_verdicts([first, nonexistent], "head", "base")[
        "claim"
    ].decision == "CONFIRMED_VIOLATION"


@pytest.mark.parametrize("changes", [
    {"auth_verified": "false"}, {"can_merge_to_main": "true"}, {"reviewer": ""},
    {"reason": 123}, {"head_sha": 123}, {"claim_id": ""},
])
def test_verdict_rejects_truthy_strings_and_missing_identity(changes):
    assert not _verdict(**changes).is_valid_for("head", "base")


@pytest.mark.parametrize("record", [[], None, {"claim_id": "missing-fields"}])
def test_malformed_ledger_record_is_not_silently_ignored(record):
    encoded = base64.b64encode(json.dumps(record).encode()).decode()
    with pytest.raises(RuntimeError, match="裁定紀錄"):
        fetch_ledger_verdicts(
            "owner/repo", 1,
            gh_get_fn=lambda *args, **kwargs: {"encoding": "base64", "content": encoded},
        )


def test_unbalanced_brace_order_does_not_produce_a_complete_java_parse():
    assert not analyze_java_source("} class A { public void run() {} ").parse_complete


def test_aspect_inspector_does_not_confirm_a_different_line_in_same_method():
    code = (
        "class A {\n public void entry() {\n  int safe = 1;\n  this.write();\n }\n"
        " @Transactional\n public void write() {}\n}"
    )
    snapshot = ReviewSnapshot(files=[{"filename": "A.java", "full_content": code}])
    inspector = AspectSelfInvocationRuleInspector()
    claim = {"location": "A.java:3", "rule": "RULE_SELF_INVOCATION_BYPASS"}
    assert inspector.inspect(claim, snapshot).status == InspectionStatus.INCONCLUSIVE
    verified = inspector.inspect(dict(claim, location="A.java:4"), snapshot)
    assert verified.status == InspectionStatus.VIOLATION
    assert verified.location == "A.java:4"


def test_lazy_word_does_not_prove_a_combined_aspect_claim_compliant():
    snapshot = ReviewSnapshot(files=[{"filename": "A.java", "full_content": "class A {}"}])
    result = AspectSelfInvocationRuleInspector().inspect({
        "location": "A.java:1", "rule": "RULE_ASPECT_SELF_INVOCATION_AND_LAZY",
        "problem": "沒有 @Lazy 但可能自調用切面",
    }, snapshot)
    assert result.status == InspectionStatus.INCONCLUSIVE


def test_eligible_human_claim_cannot_be_unblocked_by_model_low_confidence():
    code = "class A {\n void entry() { deleteResource(); }\n}"
    finding = {
        "location": "A.java:2", "rule": "BUSINESS_RULE", "severity": "LOW",
        "confidence": "LOW", "category": "QUALITY", "evidence": "deleteResource();",
        "problem": "入口方法刪除資源但缺少保護條件與呼叫上下文", "risk": "資源損失",
        "recommendation": "人工追查",
    }
    snapshot = ReviewSnapshot(files=[{"filename": "A.java", "full_content": code}])
    claim = RuleClaim("claim", "BUSINESS_RULE", finding)
    output = enforce_claim_gate([(claim, None)], snapshot, RuleRegistry())
    assert output[0]["problem"].startswith("[ELIGIBLE_FOR_HUMAN_VERDICT:")
    assert (output[0]["severity"], output[0]["confidence"]) == ("HIGH", "HIGH")


@pytest.mark.parametrize("user", [None, [], "malformed", {"permissions": "push"},
                                  {"permissions": {"push": "true"}}])
def test_malformed_permission_payload_does_not_grant_authorization(user):
    allowed, _ = verify_main_branch_merge_authorization(
        "owner/repo", "reviewer", gh_get_fn=lambda url: {"permission": "read", "user": user},
    )
    assert not allowed
