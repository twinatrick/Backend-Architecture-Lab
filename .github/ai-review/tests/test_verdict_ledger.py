import base64
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from verdict_ledger import append_comment_verdicts, configured_verdicts, require_ledger_protection


def protected():
    return {"allow_force_pushes": {"enabled": False}, "allow_deletions": {"enabled": False},
            "enforce_admins": {"enabled": True},
            "restrictions": {"users": [{"login": "writer"}], "teams": [], "apps": []}}


@pytest.mark.parametrize("setting", [{}, {"allow_force_pushes": {"enabled": True}},
                                     {"restrictions": {"users": []}}])
def test_unprotected_or_incomplete_ledger_never_authorizes(setting):
    with pytest.raises(RuntimeError):
        require_ledger_protection("owner/repo", lambda url: setting, "writer")


@pytest.mark.parametrize("admins", [None, {}, {"enabled": False}, {"enabled": "true"}])
def test_ledger_rejects_admin_bypass(admins):
    setting = protected()
    setting["enforce_admins"] = admins
    with pytest.raises(RuntimeError, match="管理員"):
        require_ledger_protection("owner/repo", lambda url: setting, "writer")


@pytest.mark.parametrize("key", ["allow_force_pushes", "allow_deletions"])
def test_ledger_rejects_malformed_protection_flags(key):
    setting = protected()
    setting[key] = None
    with pytest.raises(RuntimeError):
        require_ledger_protection("owner/repo", lambda url: setting, "writer")


def test_append_preserves_old_bytes_and_uses_compare_and_swap():
    def getter(url, params=None):
        if "/branches/ai-review-verdicts/" in url:
            return protected()
        if url.endswith("/pulls/1"):
            return {"state": "open", "head": {"sha": "head"}, "base": {"sha": "base"}}
        if "/contents/" in url:
            return {"encoding": "base64", "content": "", "sha": "blob-sha"}
        if url.endswith("/permission"):
            return {"permission": "write"}
        if url.endswith("/protection"):
            return {"required_status_checks": None, "restrictions": None}
        if "/rules/branches/" in url:
            return []
        return {"default_branch": "master"}

    putter = MagicMock()
    comment = {"id": 123, "user": {"login": "reviewer"}, "body":
               '/ai-verdict claim_id=claim decision=FALSE_POSITIVE head_sha=head '
               'base_sha=base reason="有具體反證"'}
    assert append_comment_verdicts("owner/repo", 1, comment, getter, putter, "writer") == 1
    payload = putter.call_args.args[1]
    assert payload["sha"] == "blob-sha"
    assert payload["branch"] == "ai-review-verdicts"
    record = json.loads(base64.b64decode(payload["content"]).decode("utf-8"))
    assert record["auth_verified"] is True
    assert record["comment_id"] == 123


def test_configured_reader_requires_separate_read_credential(monkeypatch):
    monkeypatch.setenv("AI_REVIEW_VERDICT_LEDGER_WRITER", "writer")
    monkeypatch.delenv("AI_REVIEW_LEDGER_READ_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="唯讀憑證"):
        configured_verdicts("owner/repo", 1, "head", "base")
