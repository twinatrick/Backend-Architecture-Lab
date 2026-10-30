import json
import sys
import time
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import review
import snapshot_static_checks
from claim_verifier import validate_claim_gate_mode
from engine import evaluate, load_policy
from reporter import format_markdown_report


def finding() -> dict:
    return {
        "location": "sample.py:1", "category": "COMPLIANCE", "rule": "RULE_TEST",
        "problem": "已確認違規", "evidence": "bad()", "risk": "風險",
        "recommendation": "修正", "severity": "HIGH", "confidence": "HIGH",
    }


def execute(results: list, monkeypatch, verified: list | None = None) -> dict:
    candidate = evaluate(verified or [], ["sample.py"], ["sample.py"], load_policy())
    monkeypatch.setattr(
        snapshot_static_checks, "evaluate_snapshot_shadow_review",
        MagicMock(return_value=candidate),
    )
    return snapshot_static_checks.evaluate_verified_review(
        results, [], ["sample.py"], ["sample.py"], load_policy(),
        "owner/repo", "head", MagicMock(), time.monotonic() + 10, "base", 1,
    )


def test_tool_disproved_raw_model_finding_no_longer_blocks(monkeypatch):
    raw = finding()
    result = execute([
        {"findings": [raw], "shadow_verified_findings": []},
    ], monkeypatch)
    assert evaluate([raw], ["sample.py"], ["sample.py"], load_policy())["decision"] == (
        "REQUEST_CHANGES"
    )
    assert result["decision"] == "APPROVE"
    assert result["gate_role"] == "active"


def test_verified_violation_blocks_even_if_model_omits_it(monkeypatch):
    result = execute([
        {"findings": [], "shadow_verified_findings": [finding()]},
    ], monkeypatch, [finding()])
    assert result["decision"] == "REQUEST_CHANGES"
    assert len(result["blocking_findings"]) == 1


@pytest.mark.parametrize("results", [
    [], [{"findings": []}], [{"shadow_verified_findings": None}],
    [{"shadow_verified_findings": []}, {"findings": []}],
    [{"shadow_verified_findings": [None]}],
])
def test_missing_or_partial_verification_never_falls_back(monkeypatch, results):
    with pytest.raises(RuntimeError):
        execute(results, monkeypatch)
    snapshot_static_checks.evaluate_snapshot_shadow_review.assert_not_called()


@pytest.mark.parametrize("candidate", [None, {}, {"findings": [None]}])
def test_invalid_static_candidate_cannot_be_published(monkeypatch, candidate):
    monkeypatch.setattr(
        snapshot_static_checks, "evaluate_snapshot_shadow_review", lambda *args, **kw: candidate,
    )
    with pytest.raises(RuntimeError):
        snapshot_static_checks.evaluate_verified_review(
            [{"shadow_verified_findings": []}], [], ["sample.py"], ["sample.py"],
            load_policy(), "owner/repo", "head", MagicMock(), time.monotonic() + 10, "base",
        )


def test_policy_recomputes_decision_and_retains_mandatory_human_review(monkeypatch):
    monkeypatch.setattr(
        snapshot_static_checks, "evaluate_snapshot_shadow_review",
        lambda *args, **kw: {"decision": "APPROVE", "findings": [], "requires_human_review": False},
    )
    path = ".github/ai-review/sample.py"
    result = snapshot_static_checks.evaluate_verified_review(
        [{"shadow_verified_findings": []}], [], [path], [path], load_policy(),
        "owner/repo", "head", MagicMock(), time.monotonic() + 10, "base",
    )
    assert result["decision"] == "HUMAN_REVIEW_REQUIRED"
    assert result["requires_human_review"] is True
    report = format_markdown_report(
        result["decision"], [path], [], [], [], [], shadow_review=result,
    )
    assert "工具查證正式閘門" in report
    assert "不變更正式閘門" not in report


def test_default_and_explicit_verified_mode_are_supported(monkeypatch):
    monkeypatch.delenv("AI_REVIEW_CLAIM_GATE_MODE", raising=False)
    validate_claim_gate_mode()
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "verified")
    validate_claim_gate_mode()


@pytest.mark.parametrize("complete", [True, False])
def test_real_entry_publishes_verified_result_or_failure_never_raw_model(
    monkeypatch, tmp_path, complete,
):
    monkeypatch.chdir(tmp_path)
    event = tmp_path / "event.json"
    event.write_text("{}", encoding="utf-8")
    monkeypatch.setattr(review, "get_event_path", lambda: str(event))
    monkeypatch.setattr(review, "get_gh_token", lambda: "fake_test_token")
    monkeypatch.setattr(review, "get_groq_api_keys", lambda: ["fake_test_key"])
    monkeypatch.setattr(review, "get_repo", lambda: "owner/repo")
    monkeypatch.setattr(review, "resolve_review_target", lambda event: {"pr_number": 1})
    monkeypatch.setattr(review, "validate_target_pr", lambda *args, **kwargs: (True, ""))
    monkeypatch.setattr(review, "gh_get", lambda *args, **kwargs: {
        "head": {"sha": "head"}, "base": {"sha": "base"},
    })
    monkeypatch.setattr(review, "_fetch_all_pr_files", lambda *args: [
        {"filename": "sample.py", "status": "added", "full_content": "bad()"},
    ])
    monkeypatch.setattr(review, "enrich_files_with_full_content", MagicMock())
    monkeypatch.setattr(review, "build_batches", lambda *args, **kwargs: [
        ("business", ["sample.py"]),
    ])
    batch = {"files_reviewed": ["sample.py"], "findings": [finding()]}
    if complete:
        batch["shadow_verified_findings"] = []
    monkeypatch.setattr(review, "_process_batches_parallel", lambda *args, **kwargs: [batch])
    candidate = evaluate([], ["sample.py"], ["sample.py"], load_policy())
    monkeypatch.setattr(
        snapshot_static_checks, "evaluate_snapshot_shadow_review", lambda *args, **kw: candidate,
    )
    publisher, failure = MagicMock(), MagicMock()
    monkeypatch.setattr(review, "publish_review", publisher)
    monkeypatch.setattr(review, "publish_failure_report", failure)
    if complete:
        review.main()
        assert publisher.call_args.args[2] == "APPROVE"
        report = json.loads((tmp_path / "ai-review.json").read_text(encoding="utf-8"))
        assert report["decision"] == "APPROVE"
        assert report["findings"] == []
        failure.assert_not_called()
    else:
        with pytest.raises(SystemExit, match="工具查證未完成"):
            review.main()
        publisher.assert_not_called()
        assert failure.call_args.kwargs["status_type"] == "REVIEW_EXECUTION_FAILED"
        assert not (tmp_path / "ai-review.json").exists()
