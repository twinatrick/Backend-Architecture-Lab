import json
import re
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import batch_runner
from claim_verifier import apply_batch_claim_verification

CODE = "def clash():\n    return 1\ndef clash():\n    return 2\n"
POLICY = {"blocking_severities": ["HIGH"], "blocking_confidence": ["HIGH"]}


def payload(path):
    return json.dumps({
        "files_reviewed": [path], "coverage": "COMPLETE", "passed_checks": [],
        "findings": [{
            "location": f"{path}:3", "category": "COMPLIANCE", "severity": "HIGH",
            "confidence": "HIGH", "rule": "RULE_PYTHON_DUPLICATE_DEFINITION",
            "problem": "重複定義 clash", "evidence": "def clash():",
            "risk": "先前定義被覆寫", "recommendation": "保留唯一實作",
        }],
    })


@pytest.fixture
def local_run(monkeypatch):
    monkeypatch.setattr(batch_runner.time, "sleep", MagicMock())
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "shadow")
    monkeypatch.delenv("AI_REVIEW_VERDICT_LEDGER_WRITER", raising=False)
    publish = MagicMock()
    monkeypatch.setattr(batch_runner, "publish_failure_report", publish)
    files = [{"filename": path, "status": "added", "full_content": CODE,
              "patch": "+def clash():\n+    return 1\n+def clash():\n+    return 2"}
             for path in ("A.py", "B.py", "C.py")]
    return files, publish


@pytest.mark.parametrize("failure", ["provider", "invalid_json", "coverage", "schema"])
def test_split_and_next_batch_keep_audit_sha_and_one_shared_deadline(
    local_run, monkeypatch, failure,
):
    files, publish = local_run
    calls, verifications = [], []

    def chat(prompt, **kwargs):
        paths = re.findall(r"diff -- ([^\n]+)", prompt)
        calls.append(paths)
        if len(paths) > 1:
            if failure == "provider":
                raise RuntimeError("batch capacity")
            if failure == "invalid_json":
                return "not JSON"
            if failure == "schema":
                return json.dumps({"files_reviewed": paths, "coverage": "COMPLETE",
                                   "findings": [None]})
            return '{"files_reviewed":[],"coverage":"COMPLETE","findings":[]}'
        return payload(paths[0])

    def verify(*args, **kwargs):
        verifications.append((args[3], kwargs["budget"], kwargs["session_start_monotonic"],
                              kwargs["head_sha"], kwargs["base_sha"]))
        return apply_batch_claim_verification(*args, **kwargs)

    monkeypatch.setattr(batch_runner, "apply_batch_claim_verification", verify)
    results = batch_runner.process_batches_parallel(
        [("business", ["A.py", "B.py"]), ("business", ["C.py"])], files,
        "rules", "contract", POLICY, 42, repo_name="owner/repo", head_sha="head-fixed",
        base_sha="base-fixed", max_workers=1, chat_fn=chat,
        gh_get_fn=MagicMock(side_effect=AssertionError("unexpected network")),
    )
    assert len(calls) == (4 if failure == "provider" else 6)
    assert [result["files_reviewed"] for result in results] == [["A.py", "B.py"], ["C.py"]]
    assert verifications[0][1] is verifications[1][1]
    assert verifications[0][1] is not verifications[2][1]
    assert len({item[2] for item in verifications}) == 1
    assert all(item[3:] == ("head-fixed", "base-fixed") for item in verifications)
    for result in results:
        assert len(result["findings"]) == len(result["shadow_verified_findings"])
        assert all(record["status"] == "VIOLATION" for record in result["claim_audit"])
        assert all(record["tool_version"] for record in result["claim_audit"])
    publish.assert_not_called()


def test_split_cannot_reset_tool_budget_for_second_child(local_run, monkeypatch):
    files, _ = local_run

    def chat(prompt, **kwargs):
        paths = re.findall(r"diff -- ([^\n]+)", prompt)
        if len(paths) > 1:
            raise RuntimeError("split needed")
        return payload(paths[0])

    def verify(*args, **kwargs):
        kwargs["budget"].max_tool_calls_per_batch = 1
        return apply_batch_claim_verification(*args, **kwargs)

    monkeypatch.setattr(batch_runner, "apply_batch_claim_verification", verify)
    result = batch_runner.process_batch(
        "business", 1, ["A.py", "B.py"], files, "rules", "contract", POLICY, 42,
        repo_name="owner/repo", head_sha="head", base_sha="base", chat_fn=chat,
    )
    assert [record["status"] for record in result["claim_audit"]] == [
        "VIOLATION", "INCONCLUSIVE",
    ]
    assert "REVIEW_INCOMPLETE" in result["shadow_verified_findings"][1]["problem"]
    assert len(result["findings"]) == 2


@pytest.mark.parametrize("failure", ["provider", "invalid_json"])
def test_later_batch_failure_never_returns_partial_approved_result(local_run, failure):
    files, publish = local_run

    def chat(prompt, **kwargs):
        if "diff -- C.py" in prompt:
            if failure == "provider":
                raise RuntimeError("provider unavailable")
            return "not JSON"
        return payload("A.py")

    with pytest.raises(SystemExit):
        batch_runner.process_batches_parallel(
            [("business", ["A.py"]), ("business", ["C.py"])], files,
            "rules", "contract", POLICY, 42, head_sha="head", base_sha="base",
            max_workers=1, chat_fn=chat,
        )
    publish.assert_called_once()
    assert publish.call_args.kwargs["commit_id"] == "head"
