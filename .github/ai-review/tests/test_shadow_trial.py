import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import shadow_trial


def pr_data(head: str = "a" * 40, count: int = 1) -> dict:
    return {"head": {"sha": head}, "base": {"sha": "b" * 40}, "changed_files": count}


@pytest.mark.parametrize("method,url,models", [
    ("POST", "https://api.github.com/repos/owner/repo/statuses/sha", True),
    ("PUT", "https://api.github.com/repos/owner/repo/contents/ledger", True),
    ("DELETE", "https://api.github.com/repos/owner/repo/issues/1", True),
    ("GET", "http://api.github.com/repos/owner/repo", False),
    ("GET", "https://api.github.com.evil.test/path", True),
    ("GET", "https://user:password@api.github.com/path", False),
    ("GET", "https://api.github.com:444/path", False),
    ("POST", "https://api.groq.com/openai/v1/chat/completions", False),
    ("GET", "https://api.groq.com/openai/v1/models", False),
    ("GET", "https://api.groq.com/openai/v1/files", True),
])
def test_trial_denies_writes_and_unapproved_network(method, url, models):
    with pytest.raises(RuntimeError):
        shadow_trial.assert_trial_request(method, url, models)


@pytest.mark.parametrize("method,url,models", [
    ("GET", "https://api.github.com/repos/owner/repo", False),
    ("GET", "https://pypi.org/pypi/requests/2.34.2/json", False),
    ("POST", "https://api.groq.com/openai/v1/chat/completions", True),
    ("GET", "https://api.groq.com/openai/v1/models", True),
    ("POST", "https://generativelanguage.googleapis.com/v1beta/models/test", True),
])
def test_trial_allows_only_required_reads_and_explicit_model_calls(method, url, models):
    shadow_trial.assert_trial_request(method, url, models)


def test_transport_guard_prevents_send_before_any_github_write(monkeypatch):
    sender = MagicMock()
    monkeypatch.setattr(requests.Session, "send", sender)
    with shadow_trial.trial_network(allow_models=True):
        with pytest.raises(RuntimeError):
            requests.post("https://api.github.com/repos/owner/repo/issues/1/comments")
    sender.assert_not_called()
    assert requests.Session.send is sender


def test_prepare_fixed_snapshot_and_complete_plan():
    getter = MagicMock(side_effect=[pr_data(), [
        {"filename": "sample.py", "patch": "@@ -0,0 +1 @@\n+VALUE = 1"},
    ], pr_data()])
    manifest, files, batches = shadow_trial.prepare_trial("owner/repo", 1, getter)
    assert manifest["status"] == "PREPARED_NOT_EXECUTED"
    assert manifest["head_sha"] == "a" * 40
    assert manifest["base_sha"] == "b" * 40
    assert manifest["github_writes"] is False
    assert manifest["max_workers"] == 1
    assert files[0]["filename"] == "sample.py"
    assert [path for _, paths in batches for path in paths] == ["sample.py"]


@pytest.mark.parametrize("latest,count", [(pr_data("c" * 40), 1), (pr_data(), 2)])
def test_prepare_rejects_snapshot_race_or_truncated_file_list(latest, count):
    getter = MagicMock(side_effect=[pr_data(count=count), [
        {"filename": "sample.py", "patch": "+VALUE = 1"},
    ], latest])
    with pytest.raises(ValueError):
        shadow_trial.prepare_trial("owner/repo", 1, getter)


def configure_main(monkeypatch, tmp_path, run: bool = False) -> Path:
    output = tmp_path / "new-trial"
    argv = ["shadow_trial.py", "--repo", "owner/repo", "--pr", "1", "--output", str(output)]
    if run:
        argv.append("--run")
    monkeypatch.setattr(sys, "argv", argv)
    monkeypatch.setenv("GH_TOKEN", "fake_test_read_only_token")
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "shadow")
    return output


def test_default_preparation_never_executes_models(monkeypatch, tmp_path):
    output = configure_main(monkeypatch, tmp_path)
    monkeypatch.setattr(shadow_trial, "prepare_trial", lambda *args: ({"mode": "shadow"}, [], []))
    executor = MagicMock()
    monkeypatch.setattr(shadow_trial, "execute_trial", executor)
    assert shadow_trial.main() == 0
    executor.assert_not_called()
    assert json.loads((output / "outcome.json").read_text())["status"] == "PREPARED_NOT_EXECUTED"
    assert not (output / "comparison.json").exists()


def test_trial_failure_has_explicit_failed_outcome_not_partial_success(monkeypatch, tmp_path):
    output = configure_main(monkeypatch, tmp_path, run=True)
    monkeypatch.setattr(shadow_trial, "get_groq_api_keys", lambda: ["fake_test_model_key"])
    monkeypatch.setattr(shadow_trial, "prepare_trial", lambda *args: ({"mode": "shadow"}, [], []))
    monkeypatch.setattr(shadow_trial, "execute_trial", MagicMock(side_effect=SystemExit(1)))
    assert shadow_trial.main() == 1
    assert json.loads((output / "outcome.json").read_text())["status"] == "FAILED"
    assert not (output / "comparison.json").exists()


def test_trial_never_overwrites_existing_directory(monkeypatch, tmp_path):
    output = configure_main(monkeypatch, tmp_path)
    output.mkdir()
    sentinel = output / "manifest.json"
    sentinel.write_text("keep", encoding="utf-8")
    with pytest.raises(FileExistsError):
        shadow_trial.main()
    assert sentinel.read_text() == "keep"


@pytest.mark.parametrize("changed", [False, True])
def test_execution_compares_but_never_publishes_and_checks_final_sha(monkeypatch, changed):
    manifest = {
        "repository": "owner/repo", "pr_number": 1, "base_sha": "b" * 40,
        "head_sha": "a" * 40, "changed_files": [".github/ai-review/sample.py"],
        "tool_version": "test",
    }
    getter = MagicMock(side_effect=[pr_data(), pr_data("c" * 40) if changed else pr_data()])
    monkeypatch.setattr(shadow_trial, "enrich_files_with_full_content", lambda *args, **kw: None)
    processor = MagicMock(return_value=[{
        "files_reviewed": [".github/ai-review/sample.py"], "findings": [],
        "shadow_verified_findings": [],
    }])
    monkeypatch.setattr(shadow_trial, "process_batches_parallel", processor)
    monkeypatch.setattr(shadow_trial, "run_static_checks", lambda *args: [])
    shadow = {"decision": "APPROVE", "findings": [], "blocking_findings": []}
    monkeypatch.setattr(shadow_trial, "evaluate_snapshot_shadow_review", lambda *args: shadow)
    if changed:
        with pytest.raises(ValueError, match="Base／Head"):
            shadow_trial.execute_trial(manifest, [], [], getter)
    else:
        report = shadow_trial.execute_trial(manifest, [], [], getter)
        assert report["shadow_review"]["decision"] == "APPROVE"
        assert report["decision"] == "HUMAN_REVIEW_REQUIRED"
        assert report["requires_human_review"] is True
        assert report["high_risk_files"] == [".github/ai-review/sample.py"]
    assert processor.call_args.kwargs["max_workers"] == 1
    assert processor.call_args.kwargs["base_sha"] == "b" * 40
