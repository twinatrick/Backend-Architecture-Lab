import base64
import sys
import time
from pathlib import Path
from unittest.mock import MagicMock

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from batch_runner import fetch_file_content_fallback
from review_snapshot import HumanClaimVerdict, ReviewSnapshot, compute_claim_identity
from snapshot_static_checks import SnapshotRepositorySearch, run_snapshot_static_checks
from static_claim_gate import verify_static_claims


def test_snapshot_static_never_reads_local_file_or_unversioned_content(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    (tmp_path / "code.py").write_text("except: pass\n", encoding="utf-8")
    code = "VALUE = 1\n"
    getter = MagicMock(return_value={
        "encoding": "base64", "content": base64.b64encode(code.encode()).decode(),
    })
    findings = run_snapshot_static_checks([
        {"filename": "code.py", "status": "added", "full_content": "except: pass"},
    ], "owner/repo", "head-sha", getter)
    assert findings == []
    getter.assert_called_once_with(
        "https://api.github.com/repos/owner/repo/contents/code.py", params={"ref": "head-sha"},
    )


def test_snapshot_missing_source_or_expired_deadline_blocks():
    files = [{"filename": "code.py", "status": "added"}]
    assert run_snapshot_static_checks(files, "owner/repo", "head", MagicMock(
        return_value={},
    ))[0]["rule"] == "REVIEW_INCOMPLETE"
    getter = MagicMock()
    assert run_snapshot_static_checks(
        files, "owner/repo", "head", getter, deadline=time.monotonic() - 1,
    )[0]["severity"] == "HIGH"
    getter.assert_not_called()


def test_snapshot_expired_search_does_not_fetch_tree_or_use_cached_tree():
    snapshot = ReviewSnapshot([], repo_name="owner/repo", head_sha="fixed")
    getter = MagicMock()
    searcher = SnapshotRepositorySearch(snapshot, getter, time.monotonic() - 1)
    assert searcher("deleteById", "Repository.java") == []
    assert searcher.incomplete == {"Repository.java"}
    searcher.paths = []
    assert searcher._load_tree() is None
    getter.assert_not_called()


def test_snapshot_truncated_tree_cannot_prove_absence():
    snapshot = ReviewSnapshot([], repo_name="owner/repo", head_sha="fixed")
    searcher = SnapshotRepositorySearch(
        snapshot, MagicMock(return_value={"truncated": True, "tree": []}),
        time.monotonic() + 10,
    )
    assert searcher("deleteById", "Repository.java") == []
    assert searcher.incomplete == {"Repository.java"}


def test_snapshot_missing_dependency_cannot_prove_absence():
    snapshot = ReviewSnapshot([], repo_name="owner/repo", head_sha="fixed")
    searcher = SnapshotRepositorySearch(snapshot, MagicMock(return_value={
        "truncated": False, "tree": [{"type": "blob", "path": "Caller.java"}],
    }), time.monotonic() + 10)
    assert searcher("deleteById", "Repository.java") == []
    assert searcher.incomplete == {"Repository.java"}


def test_static_confirmed_claim_is_retained_and_accepts_bound_human_verdict():
    finding = {
        "location": "Demo.java:1", "rule": "RULE_PROHIBIT_FQN",
        "evidence": "java.util.Date value;", "problem": "FQN", "category": "ARCHITECTURE",
        "severity": "HIGH", "confidence": "HIGH", "risk": "risk", "recommendation": "import",
    }
    snapshot = ReviewSnapshot([{
        "filename": "Demo.java", "status": "added", "full_content": "java.util.Date value;",
    }], head_sha="head", base_sha="base")
    audit = []
    assert len(verify_static_claims([finding], snapshot, {}, time.monotonic() + 900, audit)) == 1
    identity = compute_claim_identity(finding["location"], finding["rule"], finding["evidence"])
    assert audit[0]["claim_id"] == identity
    verdict = HumanClaimVerdict(
        identity, "head", "FALSE_POSITIVE", "reviewer", "已核對同名型別合法例外",
        base_sha="base", auth_verified=True, can_merge_to_main=True,
    )
    assert verify_static_claims(
        [finding], snapshot, {identity: verdict}, time.monotonic() + 900, [],
    ) == []


def test_snapshot_comment_is_not_repository_caller():
    snapshot = ReviewSnapshot([{
        "filename": "Caller.java", "full_content": "class Caller { /* deleteById */ }",
    }], repo_name="owner/repo", head_sha="fixed")
    searcher = SnapshotRepositorySearch(snapshot, MagicMock(return_value={
        "truncated": False, "tree": [{"type": "blob", "path": "Caller.java"}],
    }), time.monotonic() + 10)
    assert searcher("deleteById", "Repository.java") == []
    assert not searcher.incomplete


def test_literal_reference_without_symbol_resolution_stays_inconclusive():
    snapshot = ReviewSnapshot([{
        "filename": "Other.java", "full_content": "class Other { void deleteById() {} }",
    }], repo_name="owner/repo", head_sha="fixed")
    searcher = SnapshotRepositorySearch(snapshot, MagicMock(return_value={
        "truncated": False, "tree": [{"type": "blob", "path": "Other.java"}],
    }), time.monotonic() + 10)
    assert searcher("deleteById", "Repository.java") == ["Other.java"]
    assert searcher.incomplete == {"Repository.java"}


def test_source_path_cannot_inject_a_different_ref_query():
    getter = MagicMock(return_value={"encoding": "base64", "content": "Y29kZQ=="})
    assert fetch_file_content_fallback(
        "owner/repo", "src/name?ref=master#part.py", "fixed", gh_get_fn=getter,
    ) == "code"
    getter.assert_called_once_with(
        "https://api.github.com/repos/owner/repo/contents/src/name%3Fref%3Dmaster%23part.py",
        params={"ref": "fixed"},
    )


def test_invalid_utf8_is_not_silently_repaired_into_evidence():
    getter = MagicMock(return_value={"encoding": "base64", "content": "/w=="})
    assert fetch_file_content_fallback("owner/repo", "bad.py", "fixed", gh_get_fn=getter) is None
