from pathlib import Path
import sys
from unittest.mock import MagicMock
import json

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import batch_runner
from claim_gate import enforce_claim_gate
from claim_verifier import (
    VerificationBudget, apply_batch_claim_verification, validate_claim_gate_mode,
    verify_and_refine_batch_findings,
)
from java_code_analyzer import analyze_java_source
from reporter import evaluate_shadow_review, format_markdown_report
from review_snapshot import (
    HumanClaimVerdict, InspectionStatus, ReviewSnapshot, RuleInspectionResult,
    compute_claim_identity,
)
from rule_registry import RuleRegistry
from verdict_manager import resolve_effective_verdicts, verify_main_branch_merge_authorization


def _finding(rule: str = "RULE_PROHIBIT_FQN", line: int = 3) -> dict[str, str]:
    return {
        "location": f"src/A.java:{line}", "rule": rule, "problem": "程式碼疑點",
        "evidence": "java.util.Date value;", "severity": "HIGH", "confidence": "HIGH",
        "category": "ARCHITECTURE", "risk": "風險", "recommendation": "修正",
    }


def _snapshot(code: str) -> ReviewSnapshot:
    return ReviewSnapshot(
        files=[{"filename": "src/A.java", "full_content": code}], head_sha="head", base_sha="base"
    )


def test_evidence_requires_whole_quote_near_claim_line():
    snapshot = _snapshot("class A {\n" + "int value;\n" * 25 + "}")
    assert not snapshot.verify_evidence_exists("src/A.java", 3, "int value; fabricated();")
    assert not snapshot.verify_evidence_exists("src/A.java", 26, "class A")
    assert not snapshot.verify_evidence_exists("src/A.java", 200, "int value;")


def test_snapshot_does_not_confuse_versions_or_ambiguous_suffixes():
    snapshot = ReviewSnapshot(
        files=[{"filename": "one/A.java", "full_content": "head"},
               {"filename": "two/A.java", "full_content": "other"}],
        head_sha="head", base_sha="base", content_fetcher=lambda repo, path, sha: sha,
    )
    assert snapshot.find_file_entry("A.java") is None
    assert snapshot.get_file_content("one/A.java", "base") == "base"
    assert snapshot.get_file_content("one/A.java", "arbitrary") == ""
    assert snapshot.get_file_content("one/A.java") == "head"


def test_dependency_fetch_is_cached_and_charged_once():
    fetcher = MagicMock(return_value="class Dep {}")
    snapshot = ReviewSnapshot(files=[], head_sha="head", content_fetcher=fetcher)
    assert snapshot.get_file_content("src/Dep.java") == "class Dep {}"
    used = snapshot.extra_tokens_used
    assert snapshot.get_file_content("src/Dep.java") == "class Dep {}"
    assert snapshot.extra_tokens_used == used
    fetcher.assert_called_once()


def test_cached_full_content_is_also_subject_to_context_budget():
    snapshot = _snapshot("class A {}")
    snapshot.max_extra_tokens = 2
    assert snapshot.get_file_content("src/A.java") == ""


def test_claim_identity_does_not_truncate_evidence_or_casefold_linux_paths():
    prefix = "evidence" * 30
    assert compute_claim_identity("A.java:1", "RULE", prefix + "x") != compute_claim_identity(
        "A.java:1", "RULE", prefix + "y"
    )
    assert compute_claim_identity("A.java:1", "RULE") != compute_claim_identity("a.java:1", "RULE")


@pytest.mark.parametrize("line,status", [(2, InspectionStatus.COMPLIANT),
                                         (3, InspectionStatus.VIOLATION)])
def test_fqn_inspection_is_scoped_to_claim_line(line, status):
    snapshot = _snapshot("class A {\n String text;\n vendor.library.Widget value;\n}")
    finding = _finding(line=line)
    inspector = RuleRegistry().get_inspector(finding["rule"])
    assert inspector.inspect(finding, snapshot).status == status


def test_unused_conflicting_import_does_not_justify_fqn_exception():
    snapshot = _snapshot("import java.sql.Date;\nclass A {\n java.util.Date value;\n}")
    result = RuleRegistry().get_inspector("RULE_PROHIBIT_FQN").inspect(_finding(), snapshot)
    assert result.status == InspectionStatus.VIOLATION


def test_text_blocks_are_explicitly_unsupported_not_compliant():
    snapshot = _snapshot('class A {\n String value = """\n @Lazy\n """;\n}')
    result = RuleRegistry().get_inspector("RULE_PROHIBIT_FQN").inspect(_finding(), snapshot)
    assert result.status == InspectionStatus.INCONCLUSIVE


def test_java_annotations_do_not_leak_from_previous_method():
    structure = analyze_java_source(
        'class A {\n @Transactional\n public void write() {}\n public void read() {}\n}'
    )
    assert structure.parse_complete
    assert len(structure.methods) == 2
    assert "Transactional" in structure.methods[0].annotations
    assert "Transactional" not in structure.methods[1].annotations


def test_commented_mapping_does_not_become_real_uri():
    structure = analyze_java_source(
        'class A {\n // @GetMapping("/deleteUsers")\n'
        ' @GetMapping(path="/addresses", produces="application/json")\n'
        ' public void read() {}\n}'
    )
    assert structure.methods[0].mapping_paths == ["/addresses"]


def test_model_cannot_remove_confirmed_tool_violation():
    snapshot = _snapshot("class A {\n int number;\n java.util.Date value;\n}")
    caller = MagicMock(return_value='{"coverage":"COMPLETE","files_reviewed":["src/A.java"],'
                                   '"findings":[]}')
    result = verify_and_refine_batch_findings(
        "business", 1, ["src/A.java"], [_finding()], snapshot, RuleRegistry(), caller,
        "prompt", gate_mode="enforce",
    )
    assert len(result) == 1
    assert "java.util.Date" in result[0]["evidence"]
    caller.assert_not_called()


def test_shadow_verification_preserves_original_findings(monkeypatch):
    monkeypatch.delenv("AI_REVIEW_CLAIM_GATE_MODE", raising=False)
    finding = _finding()
    data = {"findings": [finding]}
    snapshot = _snapshot("class A {\n int number;\n String value;\n}")
    caller = MagicMock()
    apply_batch_claim_verification(
        data, "business", 1, ["src/A.java"], snapshot.files, "prompt", caller, 100, False,
        head_sha="head", base_sha="base",
    )
    assert data["findings"] == [finding]
    assert data["shadow_verified_findings"] == []
    assert data["claim_audit"][0]["status"] == "COMPLIANT"
    caller.assert_not_called()


def test_missing_content_stays_fail_closed_in_shadow_candidate():
    finding = _finding()
    data = {"findings": [finding]}
    apply_batch_claim_verification(
        data, "business", 1, ["src/A.java"], [{"filename": "src/A.java"}], "prompt",
        None, 100, False, head_sha="head",
    )
    assert data["shadow_verified_findings"][0]["severity"] == "HIGH"
    assert "REVIEW_INCOMPLETE" in data["shadow_verified_findings"][0]["problem"]


def _verdict(decision: str, comment_id: int, supersedes: str = "") -> HumanClaimVerdict:
    return HumanClaimVerdict(
        "claim", "head", decision, "reviewer", "理由", base_sha="base",
        auth_verified=True, can_merge_to_main=True, comment_id=comment_id,
        supersedes_verdict_id=supersedes,
    )


def test_verdict_rejects_base_or_rule_version_mismatch():
    verdict = _verdict("FALSE_POSITIVE", 1)
    assert not verdict.is_valid_for("head", "new-base")
    assert not verdict.is_valid_for("head")
    assert verdict.is_valid_for("head", "base")


def test_verdict_conflicts_require_explicit_retraction():
    first, second = _verdict("FALSE_POSITIVE", 1), _verdict("CONFIRMED_VIOLATION", 2)
    assert resolve_effective_verdicts([first, second], "head", "base")["claim"].decision == (
        "CONFLICTING"
    )
    records = [first, second, _verdict("RETRACT", 3, supersedes="1")]
    assert resolve_effective_verdicts(records, "head", "base")["claim"].decision == (
        "CONFIRMED_VIOLATION"
    )


def test_admin_is_not_automatic_proof_of_main_branch_merge_authorization():
    getter = MagicMock(return_value={"permission": "admin", "default_branch": "master"})
    authorized, _ = verify_main_branch_merge_authorization("owner/repo", "admin", getter)
    assert not authorized


@pytest.mark.parametrize("mode", ["enforce", "invalid", ""])
def test_unfinished_production_gate_cannot_be_enabled(monkeypatch, mode):
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", mode)
    with pytest.raises(RuntimeError, match="shadow"):
        validate_claim_gate_mode()


@pytest.mark.parametrize("malformed", [None, {}, [None]])
def test_malformed_findings_retry_before_shadow_verification(monkeypatch, malformed):
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "shadow")
    payload = {"files_reviewed": ["src/A.java"], "coverage": "COMPLETE"}
    caller = MagicMock(side_effect=[
        json.dumps({**payload, "findings": malformed}),
        json.dumps({**payload, "findings": []}),
    ])
    result = batch_runner.process_batch(
        "business", 1, ["src/A.java"],
        [{"filename": "src/A.java", "patch": "+class A {}"}], "rules", "contract", {}, 1,
        chat_fn=caller,
    )
    assert caller.call_count == 2
    assert result["findings"] == []


def test_split_batches_share_same_verification_budget(monkeypatch):
    monkeypatch.setattr(batch_runner.time, "sleep", lambda seconds: None)
    seen = []

    def capture_budget(*args, **kwargs):
        seen.append(kwargs["budget"])

    def caller(prompt, **kwargs):
        paths = [path for path in ("src/A.java", "src/B.java") if path in prompt]
        if len(paths) == 2:
            raise RuntimeError("拆批")
        return json.dumps({"files_reviewed": paths, "coverage": "COMPLETE", "findings": []})

    monkeypatch.setattr(batch_runner, "apply_batch_claim_verification", capture_budget)
    budget = VerificationBudget()
    batch_runner.process_batch(
        "business", 1, ["src/A.java", "src/B.java"],
        [{"filename": path, "patch": "+class A {}"}
         for path in ("src/A.java", "src/B.java")],
        "rules", "contract", {}, 1, chat_fn=caller, verification_budget=budget,
    )
    assert len(seen) == 2
    assert all(item is budget for item in seen)


def test_conflicting_verdict_blocks_even_if_tool_found_compliance():
    snapshot = _snapshot("class A {}")
    claim = RuleRegistry().split_finding_into_claims(_finding())[0]
    verdict = HumanClaimVerdict(
        claim.claim_id, "head", "CONFLICTING", "reviewer", "矛盾尚未解決", base_sha="base",
        auth_verified=True, can_merge_to_main=True,
    )
    compliant = RuleInspectionResult(
        InspectionStatus.COMPLIANT, claim.rule_key, "src/A.java:3", "此主張合規",
    )
    result = enforce_claim_gate([(claim, compliant)], snapshot, RuleRegistry(),
                                {claim.claim_id: verdict})
    assert result[0]["severity"] == "HIGH"
    assert "HUMAN_VERDICT_CONFLICT" in result[0]["problem"]


def test_shadow_report_is_separate_and_preserves_static_findings():
    original = _finding()
    batches = [{"findings": [original], "shadow_verified_findings": []}]
    shadow = evaluate_shadow_review(batches, [original], ["src/A.java"], ["src/A.java"], {})
    assert shadow["findings"] == [original]
    body = format_markdown_report(
        "REQUEST_CHANGES", ["src/A.java"], batches, [original], [original], [],
        shadow_review=shadow,
    )
    assert "REQUEST_CHANGES" in body
    assert batches[0]["findings"] == [original]


def test_previously_spent_context_budget_is_not_reset_for_unknown_claim():
    finding = _finding(rule="BUSINESS_LOGIC")
    snapshot = _snapshot("class A {\n int number;\n java.util.Date value;\n}")
    budget = VerificationBudget(max_extra_context_tokens=10, extra_context_tokens_used=9)
    result = verify_and_refine_batch_findings(
        "business", 1, ["src/A.java"], [finding], snapshot, RuleRegistry(), None,
        "prompt", gate_mode="enforce", budget=budget,
    )
    assert budget.extra_context_tokens_used <= 10
    assert "REVIEW_INCOMPLETE" in result[0]["problem"]


def test_exhausted_tool_budget_preserves_pending_claim():
    snapshot = _snapshot("class A {\n int number;\n java.util.Date value;\n}")
    budget = VerificationBudget(max_tool_calls_per_batch=0)
    result = verify_and_refine_batch_findings(
        "business", 1, ["src/A.java"], [_finding()], snapshot, RuleRegistry(), None,
        "prompt", gate_mode="enforce", budget=budget,
    )
    assert budget.tool_calls_used == 0
    assert result[0]["severity"] == "HIGH"
    assert "REVIEW_INCOMPLETE" in result[0]["problem"]
