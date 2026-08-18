import json
import os
from pathlib import Path
import sys
from unittest.mock import MagicMock, patch
import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import review


def test_resolve_pr_number_from_pull_request_event():
    event = {"pull_request": {"number": 42}}
    assert review.resolve_pr_number(event) == 42


def test_resolve_pr_number_from_workflow_run_event():
    event = {"workflow_run": {"pull_requests": [{"number": 88}]}}
    assert review.resolve_pr_number(event) == 88


def test_resolve_pr_number_from_inputs():
    event = {"inputs": {"pr_number": "123"}}
    assert review.resolve_pr_number(event) == 123


def test_resolve_pr_number_from_commit_sha_query():
    event = {"workflow_run": {"head_sha": "abc1234", "pull_requests": []}}
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}):
        with patch("review.gh_get", return_value=[{"number": 99}]):
            assert review.resolve_pr_number(event) == 99


def test_resolve_pr_number_fails_when_unresolved():
    event = {"action": "completed"}
    with pytest.raises(SystemExit):
        review.resolve_pr_number(event)


def test_post_issue_comment_creates_new_when_no_existing():
    with patch("review.gh_get", return_value=[]):
        mock_post = MagicMock()
        mock_post.return_value.json.return_value = {"id": 1001}
        mock_post.return_value.raise_for_status = MagicMock()
        with patch("requests.post", mock_post):
            result = review.post_issue_comment(42, "審查結果內容")
            assert result == {"id": 1001}
            assert mock_post.called
            post_json = mock_post.call_args[1]["json"]
            assert "審查結果內容" in post_json["body"]
            assert review.REVIEW_MARKER in post_json["body"]


def test_post_issue_comment_updates_existing_when_found():
    existing_comment = {"id": 555, "body": f"舊的報告\n\n{review.REVIEW_MARKER}"}
    with patch("review.gh_get", return_value=[existing_comment]):
        mock_patch = MagicMock()
        mock_patch.return_value.json.return_value = {"id": 555}
        mock_patch.return_value.raise_for_status = MagicMock()
        with patch("requests.patch", mock_patch):
            result = review.post_issue_comment(42, "新的報告內容")
            assert result == {"id": 555}
            assert mock_patch.called
            patch_json = mock_patch.call_args[1]["json"]
            assert "新的報告內容" in patch_json["body"]
            assert review.REVIEW_MARKER in patch_json["body"]


def test_post_pr_review_handles_422_gracefully():
    mock_post = MagicMock()
    mock_post.return_value.status_code = 422
    with patch("requests.post", mock_post):
        result = review.post_pr_review(42, "Review Body", "APPROVE")
        assert result is None


def test_publish_failure_report_generates_markdown_and_publishes():
    with patch("review.publish_review") as mock_publish:
        body = review.publish_failure_report(
            pr_number=42,
            title="Groq API 呼叫異常",
            reason="所有模型均回傳 503",
            details=[("model-a", "503 Service Unavailable"), ("model-b", "Rate limited")],
        )
        assert "# AI Architecture & Security Review" in body
        assert "REQUEST_CHANGES" in body
        assert "Groq API 呼叫異常" in body
        assert "model-a" in body
        assert "model-b" in body
        mock_publish.assert_called_once_with(42, body, "REQUEST_CHANGES")


def test_extract_json_payload_clean_json():
    raw = '{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}'
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_think_tag():
    raw = """<think>
Here's a thinking process:
1. Review the diff.
2. Formulate JSON.
</think>
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}"""
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_markdown_codeblock():
    raw = """```json
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}
```"""
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_think_and_surrounding_prose():
    raw = """<think>
Analysis steps...
</think>
Here is the review result:
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}
Hope this helps!"""
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_invalid_json_raises():
    raw = "<think>thinking</think>Not a json at all"
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload(raw)

