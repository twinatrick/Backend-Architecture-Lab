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


@pytest.mark.parametrize("event,expected", [
    ({"pull_request": {"number": 42}}, 42),
    ({"workflow_run": {"pull_requests": [{"number": 88}]}}, 88),
    ({"inputs": {"pr_number": "123"}}, 123),
])
def test_resolve_pr_number_basic_events(event, expected):
    assert github_client.resolve_pr_number(event) == expected


def test_resolve_pr_number_from_commit_sha_query():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=[{"number": 99}]):
        assert github_client.resolve_pr_number(event) == 99


def test_resolve_pr_number_from_commit_sha_multiple_associated_pulls_filters_open():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    mock_pulls = [{"number": 10, "state": "closed"}, {"number": 99, "state": "open"}]
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=mock_pulls):
        assert github_client.resolve_pr_number(event) == 99


def test_resolve_pr_number_from_commit_sha_multiple_open_pulls_raises():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    mock_pulls = [{"number": 10, "state": "open"}, {"number": 99, "state": "open"}]
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("github_client.gh_get", return_value=mock_pulls):
        with pytest.raises(SystemExit):
            github_client.resolve_pr_number(event)


def test_resolve_pr_number_from_workflow_run_multiple_prs():
    event_ok = {
        "workflow_run": {
            "head_sha": "sha_target",
            "pull_requests": [
                {"number": 11, "head": {"sha": "sha_other"}},
                {"number": 22, "head": {"sha": "sha_target"}},
            ],
        }
    }
    assert github_client.resolve_pr_number(event_ok) == 22

    event_ambiguous = {
        "workflow_run": {
            "head_sha": "sha_none",
            "pull_requests": [
                {"number": 11, "head": {"sha": "sha_a"}},
                {"number": 22, "head": {"sha": "sha_b"}},
            ],
        }
    }
    with pytest.raises(SystemExit):
        github_client.resolve_pr_number(event_ambiguous)


def test_validate_target_pr_scenarios():
    with patch.dict(os.environ, {"REPO": "org/repo"}):
        pr_valid = {
            "state": "open", "head": {"sha": "sha123"},
            "base": {"ref": "master", "repo": {"full_name": "org/repo"}},
        }
        ok, msg = github_client.validate_target_pr(pr_valid, expected_head_sha="sha123")
        assert ok is True and msg == ""

        pr_closed = {
            "state": "closed", "base": {"ref": "master", "repo": {"full_name": "org/repo"}}
        }
        ok, msg = github_client.validate_target_pr(pr_closed)
        assert ok is False and "非開啟" in msg

        pr_wrong_branch = {
            "state": "open", "base": {"ref": "feature-x", "repo": {"full_name": "org/repo"}}
        }
        ok, msg = github_client.validate_target_pr(pr_wrong_branch)
        assert ok is False and "非受信任" in msg

        pr_wrong_repo = {
            "state": "open", "base": {"ref": "master", "repo": {"full_name": "other/repo"}}
        }
        ok, msg = github_client.validate_target_pr(pr_wrong_repo)
        assert ok is False and "不一致" in msg


@pytest.mark.parametrize("decision", ["COMMENT", "REQUEST_CHANGES"])
def test_publish_review_raises_fail_closed_when_pr_review_fails(decision):
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value=None):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(42, "review body", decision)
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
            github_client.publish_review(42, "review body", "APPROVE", commit_id="commit123")
        assert "無法為 Commit commit123 發布" in str(exc_info.value)


def test_resolve_pr_number_fails_when_unresolved():
    with pytest.raises(SystemExit):
        github_client.resolve_pr_number({"action": "completed"})


def test_post_issue_comment_creates_new_when_no_existing():
    with patch("github_client.gh_get", return_value=[]):
        mock_post = MagicMock()
        mock_post.return_value.json.return_value = {"id": 1001}
        mock_post.return_value.raise_for_status = MagicMock()
        with patch("requests.post", mock_post):
            result = github_client.post_issue_comment(42, "審查結果內容")
            assert result == {"id": 1001}
            post_json = mock_post.call_args[1]["json"]
            assert "審查結果內容" in post_json["body"]
            assert github_client.REVIEW_MARKER in post_json["body"]


def test_post_issue_comment_updates_existing_when_found():
    existing_comment = {"id": 555, "body": f"舊的報告\n\n{github_client.REVIEW_MARKER}"}
    with patch("github_client.gh_get", return_value=[existing_comment]):
        mock_patch = MagicMock()
        mock_patch.return_value.json.return_value = {"id": 555}
        mock_patch.return_value.raise_for_status = MagicMock()
        with patch("requests.patch", mock_patch):
            result = github_client.post_issue_comment(42, "新的報告內容")
            assert result == {"id": 555}
            patch_json = mock_patch.call_args[1]["json"]
            assert "新的報告內容" in patch_json["body"]
            assert github_client.REVIEW_MARKER in patch_json["body"]


def test_post_pr_review_handles_422_gracefully():
    mock_post = MagicMock()
    mock_post.status_code = 422
    mock_post.text = "Validation Failed: Self review not permitted"
    with patch("requests.post", return_value=mock_post):
        result = github_client.post_pr_review(42, "Review Body", "APPROVE")
        assert isinstance(result, dict) and result.get("status") == "skipped_422"


def test_publish_review_succeeds_when_pr_review_returns_422_and_comment_succeeds():
    mock_review_skipped = {"status": "skipped_422", "body": "422"}
    with patch("github_client.post_issue_comment", return_value={"id": 123}), \
         patch("github_client.post_pr_review", return_value=mock_review_skipped), \
         patch("github_client.post_commit_status", return_value={"id": 789}):
        assert github_client.publish_review(
            42, "review body", "APPROVE", commit_id="commit123"
        ) is True


def test_publish_review_fails_when_pr_review_returns_422_and_comment_fails():
    mock_review_skipped = {"status": "skipped_422", "body": "422"}
    with patch("github_client.post_issue_comment", return_value=None), \
         patch("github_client.post_pr_review", return_value=mock_review_skipped), \
         patch("github_client.post_commit_status", return_value={"id": 789}):
        with pytest.raises(RuntimeError) as exc_info:
            github_client.publish_review(42, "review body", "APPROVE", commit_id="commit123")
        assert "PR Review 因 422 跳過且 Issue 留言失敗" in str(exc_info.value)


def test_load_pr_metadata_from_file(tmp_path):
    meta_file = tmp_path / "pr-metadata.json"
    meta_content = {
        "pr_number": 62, "head_sha": "sha_pr62", "base_ref": "master",
        "repo": "owner/repo", "actor": "twinatrick",
    }
    meta_file.write_text(json.dumps(meta_content), encoding="utf-8")
    assert github_client.load_pr_metadata(metadata_path=meta_file) == meta_content


def test_resolve_pr_number_from_pr_metadata_artifact(tmp_path):
    meta_file = tmp_path / "pr-metadata.json"
    meta_file.write_text(json.dumps({"pr_number": 62, "repo": "owner/repo"}), encoding="utf-8")
    with patch.dict(os.environ, {"REPO": "owner/repo"}):
        assert github_client.resolve_pr_number({}, metadata_path=meta_file) == 62


def test_resolve_review_target_with_pr_metadata_artifact(tmp_path):
    meta_file = tmp_path / "pr-metadata.json"
    meta_file.write_text(
        json.dumps({
            "pr_number": 62, "head_sha": "sha_pr62",
            "repo": "owner/repo", "actor": "twinatrick",
        }),
        encoding="utf-8",
    )
    with patch.dict(os.environ, {"REPO": "owner/repo"}):
        target = github_client.resolve_review_target({}, metadata_path=meta_file)
        assert target["pr_number"] == 62
        assert target["expected_head_sha"] == "sha_pr62"
        assert target["actor"] == "twinatrick"
        assert target["trigger_type"] == "metadata_artifact"


def test_post_pr_review_includes_commit_id():
    mock_post = MagicMock()
    mock_post.return_value.status_code = 200
    mock_post.return_value.json.return_value = {"id": 999}
    with patch("requests.post", mock_post):
        result = github_client.post_pr_review(42, "Review Body", "APPROVE", commit_id="commit123")
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
        mock_publish.assert_called_once_with(42, body, "REQUEST_CHANGES", commit_id="commit789")


def test_resolve_review_target_dispatch_and_workflow_run():
    event_dispatch = {
        "inputs": {"pr_number": "60", "head_sha": "abc1234"},
        "sender": {"login": "dev-alice"},
    }
    target_dispatch = github_client.resolve_review_target(event_dispatch)
    assert target_dispatch["pr_number"] == 60
    assert target_dispatch["expected_head_sha"] == "abc1234"
    assert target_dispatch["actor"] == "dev-alice"
    assert target_dispatch["trigger_type"] == "workflow_dispatch"

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
        assert "statuses/commit_test_sha" in mock_post.call_args[0][0]
        assert mock_post.call_args[1]["json"]["context"] == "ai-review/architecture-gate"


def test_publish_review_with_human_review_required():
    with patch("github_client.post_issue_comment", return_value={"id": 1}), \
         patch("github_client.post_pr_review", return_value={"id": 2}), \
         patch("github_client.post_commit_status", return_value={"id": 3}) as mock_status:
        github_client.publish_review(
            42, "body", "HUMAN_REVIEW_REQUIRED", commit_id="sha123", requires_human_review=True,
        )
        msg = "AI Review: Human Review Required (Architect Approval Needed)"
        mock_status.assert_called_once_with("sha123", "failure", msg)
