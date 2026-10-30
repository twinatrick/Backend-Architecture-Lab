import base64
import copy
import json
import sys
from pathlib import Path

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import reviewer_authorization
import verdict_ledger
from claim_verifier import apply_batch_claim_verification
from reporter import evaluate_shadow_review, format_json_report
from rule_registry import RuleRegistry
from verdict_manager import resolve_effective_verdicts


class LocalLedgerApi:
    """只在記憶體模擬 GitHub Contents CAS；任何未知 URL 都使測試失敗。"""

    def __init__(self):
        self.content = None
        self.sha = ""
        self.writes = 0
        self.pr_reads = 0
        self.concurrent_write = False
        self.updated_pr = None
        self.pr = {"state": "open", "head": {"sha": "head"}, "base": {"sha": "base"}}

    @staticmethod
    def http_error(status):
        response = requests.Response()
        response.status_code = status
        return requests.HTTPError(response=response)

    def get(self, url, params=None):
        if url.endswith("/branches/ai-review-verdicts/protection"):
            return {
                "enforce_admins": {"enabled": True},
                "allow_force_pushes": {"enabled": False},
                "allow_deletions": {"enabled": False},
                "restrictions": {"users": [{"login": "writer"}], "teams": [], "apps": []},
            }
        if url == "https://api.github.com/users/reviewer":
            return {"id": 42, "login": "reviewer", "type": "User"}
        if url.endswith("/pulls/1"):
            self.pr_reads += 1
            if self.updated_pr is not None and self.pr_reads % 2 == 0:
                return copy.deepcopy(self.updated_pr)
            return copy.deepcopy(self.pr)
        if url.endswith("/contents/verdicts/pr-1.jsonl"):
            assert params == {"ref": "ai-review-verdicts"}
            if self.content is None:
                raise self.http_error(404)
            return {"encoding": "base64", "sha": self.sha,
                    "content": base64.b64encode(self.content).decode()}
        raise AssertionError(f"非預期 API：{url}")

    def put(self, url, payload):
        assert url.endswith("/contents/verdicts/pr-1.jsonl")
        assert payload["branch"] == "ai-review-verdicts"
        if self.concurrent_write or payload.get("sha", "") != self.sha:
            raise self.http_error(409)
        updated = base64.b64decode(payload["content"], validate=True)
        if self.content is not None:
            assert updated.startswith(self.content)
        self.content = updated
        self.writes += 1
        self.sha = f"blob-{self.writes}"

    def append(self, decision, comment_id, **fields):
        arguments = dict(claim_id="claim", decision=decision, head_sha="head", base_sha="base")
        arguments.update(fields)
        body = "/ai-verdict " + " ".join(f"{key}={value}" for key, value in arguments.items())
        comment = {"id": comment_id, "user": {"login": "reviewer", "id": 42},
                   "body": body + ' reason="可核對的裁定理由"'}
        return verdict_ledger.append_comment_verdicts(
            "owner/repo", 1, comment, self.get, self.put, "writer",
        )

    def resolve(self, head="head", base="base"):
        records = verdict_ledger.read_protected_verdicts("owner/repo", 1, self.get, "writer")
        return resolve_effective_verdicts(records, head, base)


@pytest.fixture
def ledger(monkeypatch):
    monkeypatch.setattr(reviewer_authorization, "approved_reviewer_ids", lambda repo: {42})
    monkeypatch.setattr(verdict_ledger, "approved_reviewer_ids", lambda repo: {42})
    return LocalLedgerApi()


def test_append_read_conflict_retract_supersede_lifecycle(ledger):
    assert ledger.resolve() == {}
    assert ledger.append("FALSE_POSITIVE", 1) == 1
    original_bytes = ledger.content
    assert ledger.resolve()["claim"].decision == "FALSE_POSITIVE"
    assert ledger.append("FALSE_POSITIVE", 1) == 0
    assert ledger.content == original_bytes and ledger.writes == 1
    ledger.append("CONFIRMED_VIOLATION", 2)
    assert ledger.resolve()["claim"].decision == "CONFLICTING"
    ledger.append("RETRACT", 3, supersedes="2")
    assert ledger.resolve()["claim"].decision == "FALSE_POSITIVE"
    ledger.append("SUPERSEDE", 4, supersedes="1", replacement_decision="CONFIRMED_VIOLATION")
    assert ledger.resolve()["claim"].decision == "CONFIRMED_VIOLATION"
    ledger.append("RETRACT", 5, supersedes="4")
    assert ledger.resolve() == {}
    assert len(ledger.content.splitlines()) == 5
    assert ledger.content.startswith(original_bytes)


def test_rejected_compare_and_swap_keeps_existing_history(ledger):
    ledger.append("FALSE_POSITIVE", 1)
    original_bytes = ledger.content
    ledger.concurrent_write = True
    with pytest.raises(requests.HTTPError) as caught:
        ledger.append("CONFIRMED_VIOLATION", 2)
    assert caught.value.response.status_code == 409
    assert ledger.content == original_bytes and ledger.writes == 1


@pytest.mark.parametrize("change", [
    {"state": "closed"}, {"head": {"sha": "new-head"}}, {"base": {"sha": "new-base"}},
])
def test_pr_changed_between_validation_and_write_is_rejected(ledger, change):
    ledger.updated_pr = dict(ledger.pr, **change)
    with pytest.raises(ValueError):
        ledger.append("FALSE_POSITIVE", 1)
    assert ledger.content is None and ledger.writes == 0


def test_snapshot_and_revoked_reviewer_cannot_release_new_review(ledger, monkeypatch):
    ledger.append("FALSE_POSITIVE", 1)
    assert ledger.resolve("new-head", "base") == {}
    assert ledger.resolve("head", "new-base") == {}
    monkeypatch.setattr(verdict_ledger, "approved_reviewer_ids", lambda repo: set())
    assert ledger.resolve() == {}


@pytest.mark.parametrize("data", [
    {"encoding": "base64", "sha": "existing"},
    {"encoding": "base64", "sha": "existing", "content": "!!!!"},
    {"encoding": "base64", "sha": True, "content": ""},
])
def test_incomplete_or_invalid_encoding_is_not_an_empty_ledger(data):
    with pytest.raises(ValueError):
        verdict_ledger.decode_ledger(data)


def test_corrupted_existing_record_is_not_overwritten(ledger):
    ledger.content = b'{"claim_id":"incomplete"}\n'
    ledger.sha = "existing"
    with pytest.raises((ValueError, TypeError)):
        ledger.append("FALSE_POSITIVE", 1)
    assert ledger.writes == 0


def test_reader_uses_only_read_credential_and_does_not_write(ledger, monkeypatch):
    ledger.append("FALSE_POSITIVE", 1)
    monkeypatch.setenv("AI_REVIEW_VERDICT_LEDGER_WRITER", "writer")
    monkeypatch.setenv("AI_REVIEW_LEDGER_READ_TOKEN", "local-read-token")
    monkeypatch.setenv("AI_REVIEW_LEDGER_WRITE_TOKEN", "local-write-token")

    def get(url, **kwargs):
        assert kwargs["headers"]["Authorization"] == "Bearer local-read-token"
        response = requests.Response()
        response.status_code = 200
        response._content = json.dumps(ledger.get(url, kwargs.get("params"))).encode()
        return response

    monkeypatch.setattr(verdict_ledger.requests, "get", get)
    assert verdict_ledger.configured_verdicts("owner/repo", 1, "head", "base")[
        "claim"
    ].decision == "FALSE_POSITIVE"
    assert ledger.writes == 1


def test_first_write_collision_can_retry_without_losing_history(ledger):
    ledger.concurrent_write = True
    with pytest.raises(requests.HTTPError):
        ledger.append("FALSE_POSITIVE", 1)
    assert ledger.content is None
    ledger.concurrent_write = False
    assert ledger.append("FALSE_POSITIVE", 1) == 1
    assert ledger.append("FALSE_POSITIVE", 1) == 0
    assert ledger.writes == 1


def test_append_preserves_valid_unterminated_old_record(ledger):
    ledger.append("FALSE_POSITIVE", 1)
    ledger.content = ledger.content.rstrip(b"\n")
    original_bytes = ledger.content
    ledger.append("CONFIRMED_VIOLATION", 2)
    assert ledger.content.startswith(original_bytes + b"\n")
    assert ledger.resolve()["claim"].decision == "CONFLICTING"


def test_base64_api_line_wrapping_remains_supported(ledger):
    ledger.append("FALSE_POSITIVE", 1)
    encoded = base64.encodebytes(ledger.content).decode().replace("\n", "\r\n")
    content, sha = verdict_ledger.decode_ledger({
        "encoding": "base64", "sha": ledger.sha, "content": encoded,
    })
    assert content.encode() == ledger.content and sha == ledger.sha


@pytest.mark.parametrize("status", [401, 403, 429, 500])
def test_non_404_read_error_never_creates_or_clears_a_ledger(ledger, status):
    def getter(url, params=None):
        if "/contents/" in url:
            raise ledger.http_error(status)
        return ledger.get(url, params)

    with pytest.raises(requests.HTTPError):
        verdict_ledger.read_protected_verdicts("owner/repo", 1, getter, "writer")
    assert ledger.content is None and ledger.writes == 0


def test_unknown_reviewer_cannot_append_or_modify_history(ledger, monkeypatch):
    ledger.append("FALSE_POSITIVE", 1)
    original_bytes = ledger.content
    monkeypatch.setattr(reviewer_authorization, "approved_reviewer_ids", lambda repo: set())
    with pytest.raises(PermissionError):
        ledger.append("CONFIRMED_VIOLATION", 2)
    assert ledger.content == original_bytes and ledger.writes == 1


def test_stored_verdict_reaches_shadow_gate_audit_and_report(ledger, monkeypatch):
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "shadow")
    source = "def run():\n    return 1\ndef run():\n    return 2"
    original = {
        "location": "ci.py:3", "rule": "RULE_PYTHON_DUPLICATE_DEFINITION",
        "problem": "函數重複定義", "evidence": "def run():", "category": "COMPLIANCE",
        "severity": "HIGH", "confidence": "HIGH", "risk": "執行行為被覆寫",
        "recommendation": "確認定義是否重複",
    }
    claim = RuleRegistry().split_finding_into_claims(original)[0]
    ledger.append("FALSE_POSITIVE", 1, claim_id=claim.claim_id)

    def review():
        data = {"findings": [original]}
        apply_batch_claim_verification(
            data, "ci", 1, ["ci.py"], [{"filename": "ci.py", "full_content": source,
                                       "status": "added"}], "contract", lambda prompt: "",
            10, False, head_sha="head", base_sha="base", human_verdicts=ledger.resolve(),
        )
        assert data["findings"] == [original]
        assert data["claim_audit"][0]["claim_id"] == claim.claim_id
        assert data["claim_audit"][0]["status"] == "VIOLATION"
        shadow = evaluate_shadow_review([data], [], ["ci.py"], ["ci.py"], {})
        payload = json.loads(format_json_report(
            "REQUEST_CHANGES", [original], [original], 1, ["ci.py"],
            shadow_review=shadow, claim_audit=data["claim_audit"],
        ))
        assert payload["decision"] == "REQUEST_CHANGES"
        return data["shadow_verified_findings"]

    assert review() == []
    ledger.append("CONFIRMED_VIOLATION", 2, claim_id=claim.claim_id)
    assert "HUMAN_VERDICT_CONFLICT" in review()[0]["problem"]
    ledger.append("RETRACT", 3, supersedes="1", claim_id=claim.claim_id)
    assert review()[0]["confidence"] == "HIGH"
