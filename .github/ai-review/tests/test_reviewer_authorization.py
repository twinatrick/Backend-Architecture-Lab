import base64
from dataclasses import asdict
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import reviewer_authorization
from review_snapshot import HumanClaimVerdict
from reviewer_authorization import approved_reviewer_ids, verify_approved_reviewer
from verdict_ledger import read_protected_verdicts


@pytest.fixture
def config(tmp_path, monkeypatch):
    path = tmp_path / "approved_reviewers.json"
    path.write_text(json.dumps({"version": 1, "repository": "owner/repo", "reviewers": [
        {"github_user_id": 42, "display_login": "reviewer"},
    ]}), encoding="utf-8")
    monkeypatch.setattr(reviewer_authorization, "REVIEWER_CONFIG", path)
    return path


def test_approved_identity_does_not_require_merge_permission(config):
    getter = MagicMock(return_value={"id": 42, "login": "reviewer", "type": "User"})
    allowed, _, identity = verify_approved_reviewer(
        "owner/repo", "reviewer", getter, expected_user_id=42,
    )
    assert allowed and identity == 42
    getter.assert_called_once_with("https://api.github.com/users/reviewer")


def test_display_name_is_not_authority_and_rename_preserves_identity(config):
    getter = MagicMock(return_value={"id": 42, "login": "renamed", "type": "User"})
    assert verify_approved_reviewer("owner/repo", "renamed", getter)[0]
    getter.return_value = {"id": 99, "login": "reviewer", "type": "User"}
    assert not verify_approved_reviewer("owner/repo", "reviewer", getter)[0]


@pytest.mark.parametrize("user", [
    {"permission": "admin"}, {"id": True, "login": "reviewer", "type": "User"},
    {"id": "42", "login": "reviewer", "type": "User"},
    {"id": 42, "login": "other", "type": "User"},
    {"id": 42, "login": "reviewer", "type": "Bot"}, None,
])
def test_malformed_or_unapproved_identity_never_authorizes(config, user):
    assert not verify_approved_reviewer("owner/repo", "reviewer", lambda url: user)[0]


@pytest.mark.parametrize("identity", [True, 99, "42", 0])
def test_comment_id_must_match_api_identity(config, identity):
    getter = MagicMock(return_value={"id": 42, "login": "reviewer", "type": "User"})
    assert not verify_approved_reviewer(
        "owner/repo", "reviewer", getter, expected_user_id=identity,
    )[0]


@pytest.mark.parametrize("changes", [
    {"version": True}, {"repository": "attacker/repo"}, {"reviewers": None},
    {"reviewers": [{"github_user_id": True, "display_login": "reviewer"}]},
    {"reviewers": [{"github_user_id": 42, "display_login": "reviewer"}] * 2},
    {"unknown": "ignored"},
])
def test_malformed_trusted_config_fails_closed(config, changes):
    data = json.loads(config.read_text(encoding="utf-8"))
    data.update(changes)
    config.write_text(json.dumps(data), encoding="utf-8")
    with pytest.raises(ValueError):
        approved_reviewer_ids("owner/repo")
    assert not verify_approved_reviewer("owner/repo", "reviewer", MagicMock())[0]


def test_missing_config_and_network_failure_fail_closed(config):
    assert not verify_approved_reviewer("owner/repo", "reviewer", MagicMock(
        side_effect=requests.HTTPError("unavailable"),
    ))[0]
    config.unlink()
    assert not verify_approved_reviewer("owner/repo", "reviewer", MagicMock())[0]


def test_revoked_reviewer_record_is_not_consumed(config):
    record = HumanClaimVerdict(
        "claim", "head", "FALSE_POSITIVE", "former", "具體理由", base_sha="base",
        reviewer_authorized=True, reviewer_id=99, auth_verified=True,
    )
    def getter(url, params=None):
        if url.endswith("/protection"):
            return {"enforce_admins": {"enabled": True},
                    "allow_force_pushes": {"enabled": False},
                    "allow_deletions": {"enabled": False},
                    "restrictions": {"users": [{"login": "writer"}], "teams": [], "apps": []}}
        content = base64.b64encode(json.dumps(asdict(record)).encode()).decode()
        return {"encoding": "base64", "content": content, "sha": "blob"}
    assert read_protected_verdicts("owner/repo", 1, getter, "writer") == []
