import json
import os
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import github_client


def test_resolve_pr_number_from_pull_request_event():
    event = {"pull_request": {"number": 42}}
    assert github_client.resolve_pr_number(event) == 42


def test_resolve_pr_number_from_workflow_run_event():
    event = {"workflow_run": {"pull_requests": [{"number": 88}]}}
    assert github_client.resolve_pr_number(event) == 88


def test_resolve_pr_number_from_inputs():
    event = {"inputs": {"pr_number": "123"}}
    assert github_client.resolve_pr_number(event) == 123


def test_resolve_pr_number_from_commit_sha_query():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=[{"number": 99}]):
        assert github_client.resolve_pr_number(event) == 99


def test_resolve_pr_number_from_commit_sha_multiple_associated_pulls_filters_open():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    mock_pulls = [
        {"number": 10, "state": "closed"},
        {"number": 99, "state": "open"},
    ]
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=mock_pulls):
        assert github_client.resolve_pr_number(event) == 99


def test_resolve_pr_number_from_commit_sha_multiple_open_pulls_raises():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    mock_pulls = [
        {"number": 10, "state": "open"},
        {"number": 99, "state": "open"},
    ]
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=mock_pulls):
        with pytest.raises(SystemExit):
            github_client.resolve_pr_number(event)


def test_resolve_pr_number_from_workflow_run_multiple_prs_matching_head_sha():
    event = {
        "workflow_run": {
            "head_sha": "sha_target",
            "pull_requests": [
                {"number": 11, "head": {"sha": "sha_other"}},
                {"number": 22, "head": {"sha": "sha_target"}},
            ],
        }
    }
    assert github_client.resolve_pr_number(event) == 22


def test_resolve_pr_number_from_workflow_run_multiple_prs_unresolved_raises():
    event = {
        "workflow_run": {
            "head_sha": "sha_none",
            "pull_requests": [
                {"number": 11, "head": {"sha": "sha_a"}},
                {"number": 22, "head": {"sha": "sha_b"}},
            ],
        }
    }
    with pytest.raises(SystemExit):
        github_client.resolve_pr_number(event)


def test_validate_target_pr_scenarios():
    with patch.dict(os.environ, {"REPO": "org/repo"}):
        pr_valid = {
            "state": "open",
            "base": {"ref": "master", "repo": {"full_name": "org/repo"}},
            "head": {"sha": "sha123"},
        }
        ok, msg = github_client.validate_target_pr(pr_valid, expected_head_sha="sha123")
        assert ok is True
        assert msg == ""

        pr_closed = {
            "state": "closed",
            "base": {"ref": "master", "repo": {"full_name": "org/repo"}},
        }
        ok, msg = github_client.validate_target_pr(pr_closed)
        assert ok is False
        assert "非開啟" in msg

        pr_wrong_branch = {
            "state": "open",
            "base": {"ref": "feature-x", "repo": {"full_name": "org/repo"}},
        }
        ok, msg = github_client.validate_target_pr(pr_wrong_branch)
        assert ok is False
        assert "非受信任" in msg

        pr_wrong_repo = {
            "state": "open",
            "base": {"ref": "master", "repo": {"full_name": "other/repo"}},
        }
        ok, msg = github_client.validate_target_pr(pr_wrong_repo)
        assert ok is False
        assert "不一致" in msg


def test_publish_review_raises_fail_closed_when_both_fail():
    with patch("github_client.post_issue_comment", return_value=None), \
         patch("github_client.post_pr_review", return_value=None):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(42, "review body", "COMMENT")
        assert "無法在 PR #42 提交正式 PR Review" in str(exc_info.value)


def test_publish_review_raises_fail_closed_when_request_changes_review_fails():
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value=None):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(42, "review body", "REQUEST_CHANGES")
        assert "無法在 PR #42 提交正式 PR Review" in str(exc_info.value)


def test_publish_review_raises_fail_closed_when_comment_review_fails():
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value=None):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(42, "review body", "COMMENT")
        assert "無法在 PR #42 提交正式 PR Review" in str(exc_info.value)


def test_publish_review_succeeds_when_request_changes_review_succeeds():
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value={"id": 456}), \
         patch("github_client.post_commit_status", return_value={"id": 789}):
        assert github_client.publish_review(
            42, "review body", "REQUEST_CHANGES", commit_id="commit123"
        ) is True


def test_publish_review_raises_fail_closed_when_commit_status_fails():
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value={"id": 456}), \
         patch("github_client.post_commit_status", return_value=None):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(
                42, "review body", "APPROVE", commit_id="commit123"
            )
        assert "無法為 Commit commit123 發布 Commit Status" in str(exc_info.value)


def test_resolve_pr_number_fails_when_unresolved():
    event = {"action": "completed"}
    with pytest.raises(SystemExit):
        github_client.resolve_pr_number(event)


def test_post_issue_comment_creates_new_when_no_existing():
    with patch("github_client.gh_get", return_value=[]):
        mock_post = MagicMock()
        mock_post.return_value.json.return_value = {"id": 1001}
        mock_post.return_value.raise_for_status = MagicMock()
        with patch("requests.post", mock_post):
            result = github_client.post_issue_comment(42, "審查結果內容")
            assert result == {"id": 1001}
            assert mock_post.called
            post_json = mock_post.call_args[1]["json"]
            assert "審查結果內容" in post_json["body"]
            assert github_client.REVIEW_MARKER in post_json["body"]


def test_post_issue_comment_updates_existing_when_found():
    existing_comment = {
        "id": 555,
        "body": f"舊的報告\n\n{github_client.REVIEW_MARKER}",
    }
    with patch("github_client.gh_get", return_value=[existing_comment]):
        mock_patch = MagicMock()
        mock_patch.return_value.json.return_value = {"id": 555}
        mock_patch.return_value.raise_for_status = MagicMock()
        with patch("requests.patch", mock_patch):
            result = github_client.post_issue_comment(42, "新的報告內容")
            assert result == {"id": 555}
            assert mock_patch.called
            patch_json = mock_patch.call_args[1]["json"]
            assert "新的報告內容" in patch_json["body"]
            assert github_client.REVIEW_MARKER in patch_json["body"]


def test_post_pr_review_handles_422_gracefully():
    mock_post = MagicMock()
    mock_post.return_value.status_code = 422
    with patch("requests.post", mock_post):
        result = github_client.post_pr_review(42, "Review Body", "APPROVE")
        assert result is None


def test_post_pr_review_includes_commit_id():
    mock_post = MagicMock()
    mock_post.return_value.status_code = 200
    mock_post.return_value.json.return_value = {"id": 999}
    with patch("requests.post", mock_post):
        result = github_client.post_pr_review(
            42, "Review Body", "APPROVE", commit_id="commit123"
        )
        assert result == {"id": 999}
        assert mock_post.call_args[1]["json"]["commit_id"] == "commit123"


def test_publish_failure_report_generates_markdown_and_publishes():
    with patch("github_client.publish_review") as mock_publish:
        body = github_client.publish_failure_report(
            pr_number=42,
            title="Groq API 呼叫異常",
            reason="所有模型均回傳 503",
            details=[("model-a", "503 Service Unavailable"), ("model-b", "Rate limited")],
            commit_id="commit789",
        )
        assert "# AI Architecture & Security Review" in body
        assert "REVIEW_FAILED_INFRA" in body
        assert "Groq API 呼叫異常" in body
        assert "model-a" in body
        assert "model-b" in body
        mock_publish.assert_called_once_with(
            42, body, "REQUEST_CHANGES", commit_id="commit789"
        )


def test_resolve_review_target_dispatch_and_workflow_run():
    # 1. 測試 workflow_dispatch
    event_dispatch = {
        "inputs": {"pr_number": "60", "head_sha": "abc1234"},
        "sender": {"login": "dev-alice"},
    }
    target_dispatch = github_client.resolve_review_target(event_dispatch)
    assert target_dispatch["pr_number"] == 60
    assert target_dispatch["expected_head_sha"] == "abc1234"
    assert target_dispatch["actor"] == "dev-alice"
    assert target_dispatch["trigger_type"] == "workflow_dispatch"

    # 2. 測試 workflow_run
    event_run = {
        "workflow_run": {
            "head_sha": "def5678",
            "actor": {"login": "ci-bot"},
            "pull_requests": [{"number": 60, "head": {"sha": "def5678"}}],
            "repository": {"full_name": "twinatrick/Backend-Architecture-Lab"},
        },
        "repository": {"full_name": "twinatrick/Backend-Architecture-Lab"},
    }
    target_run = github_client.resolve_review_target(event_run)
    assert target_run["pr_number"] == 60
    assert target_run["expected_head_sha"] == "def5678"
    assert target_run["actor"] == "ci-bot"
    assert target_run["trigger_type"] == "workflow_run"


def test_post_commit_status_success():
    mock_post = MagicMock()
    mock_post.return_value.status_code = 201
    mock_post.return_value.json.return_value = {"id": 1001, "state": "success"}
    with patch("requests.post", mock_post):
        result = github_client.post_commit_status(
            head_sha="commit_test_sha",
            state="success",
            description="Passed deterministic and AI checks",
        )
        assert result == {"id": 1001, "state": "success"}
        called_url = mock_post.call_args[0][0]
        assert "statuses/commit_test_sha" in called_url
        assert mock_post.call_args[1]["json"]["context"] == "ai-review/architecture-gate"


def test_publish_review_with_human_review_required():
    with patch("github_client.post_issue_comment", return_value={"id": 1}), \
         patch("github_client.post_pr_review", return_value={"id": 2}), \
         patch("github_client.post_commit_status", return_value={"id": 3}) as mock_status:
        github_client.publish_review(
            42, "body", "HUMAN_REVIEW_REQUIRED", commit_id="sha123", requires_human_review=True,
        )
        mock_status.assert_called_once_with(
            "sha123", "failure", "AI Review: Human Review Required (Architect Approval Needed)",
        )

