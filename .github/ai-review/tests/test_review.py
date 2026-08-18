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


def test_normalize_path_and_paths():
    assert review.normalize_path("  ./foo/bar.py  ") == "foo/bar.py"
    assert review.normalize_path("foo\\bar.py") == "foo/bar.py"
    assert review.normalize_path("") == ""
    assert review.normalize_path(None) == ""
    assert review.normalize_paths([" ./a.py ", "b\\c.py", ""]) == ["a.py", "b/c.py"]
    assert review.normalize_paths(None) == []


def test_validate_coverage_with_order_and_normalization():
    expected = [".github/ai-review/review.py", ".github/ai-review/tests/test_review.py"]
    reviewed_reversed = ["./.github/ai-review/tests/test_review.py", " .github/ai-review/review.py "]
    norm_expected = review.normalize_paths(expected)
    norm_reviewed = review.normalize_paths(reviewed_reversed)
    assert review.validate_coverage(norm_expected, norm_reviewed)


def test_validate_coverage_fails_when_missing_or_extra():
    expected = ["a.py", "b.py"]
    norm_expected = review.normalize_paths(expected)
    assert not review.validate_coverage(norm_expected, review.normalize_paths(["a.py"]))
    assert not review.validate_coverage(norm_expected, review.normalize_paths(["a.py", "b.py", "c.py"]))
    assert not review.validate_coverage(norm_expected, review.normalize_paths(["a.py", "a.py"]))


def test_parse_retry_after_from_header():
    mock_resp = MagicMock()
    mock_resp.headers = {"retry-after": "8.5"}
    assert review.parse_retry_after(mock_resp) == 8.5


def test_parse_retry_after_from_text_seconds():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = '{"error":{"message":"Rate limit reached. Please try again in 10.4175s."}}'
    assert review.parse_retry_after(mock_resp) == 10.4175


def test_parse_retry_after_from_text_milliseconds():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = '{"error":{"message":"Rate limit reached. Please try again in 500ms."}}'
    assert review.parse_retry_after(mock_resp) == 0.5


def test_parse_retry_after_fallback():
    mock_resp = MagicMock()
    mock_resp.headers = {}
    mock_resp.text = "Internal error without retry hints"
    assert review.parse_retry_after(mock_resp) == 5.0


def test_chat_completion_success_on_first_try():
    mock_resp = MagicMock()
    mock_resp.ok = True
    mock_resp.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "test", "findings": []}'}}]
    }
    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", return_value=mock_resp) as mock_post:
        result = review.chat_completion("test prompt")
        assert result == '{"batch": "test", "findings": []}'
        assert mock_post.call_count == 1
        payload = mock_post.call_args[1]["json"]
        assert payload["max_tokens"] == 4096
        assert payload["response_format"] == {"type": "json_object"}


def test_chat_completion_retries_on_429_then_succeeds():
    mock_resp_429 = MagicMock()
    mock_resp_429.ok = False
    mock_resp_429.status_code = 429
    mock_resp_429.headers = {"retry-after": "2"}
    mock_resp_429.text = "Rate limit reached"

    mock_resp_200 = MagicMock()
    mock_resp_200.ok = True
    mock_resp_200.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "test-retry", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", side_effect=[mock_resp_429, mock_resp_200]) as mock_post, \
         patch("time.sleep") as mock_sleep:
        result = review.chat_completion("test prompt", max_retries_per_model=2)
        assert result == '{"batch": "test-retry", "findings": []}'
        assert mock_post.call_count == 2
        mock_sleep.assert_called_once()


def test_chat_completion_downgrades_on_400_json_validate_failed():
    mock_resp_400 = MagicMock()
    mock_resp_400.ok = False
    mock_resp_400.status_code = 400
    mock_resp_400.headers = {}
    mock_resp_400.text = '{"error":{"message":"json_validate_failed: failed to validate json schema"}}'

    mock_resp_200 = MagicMock()
    mock_resp_200.ok = True
    mock_resp_200.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "fallback-text", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["qwen/qwen3.6-27b"]), \
         patch("requests.post", side_effect=[mock_resp_400, mock_resp_200]) as mock_post, \
         patch("time.sleep") as mock_sleep:
        result = review.chat_completion("test prompt", max_retries_per_model=2)
        assert result == '{"batch": "fallback-text", "findings": []}'
        assert mock_post.call_count == 2
        # Verify 2nd attempt did not have response_format
        second_payload = mock_post.call_args_list[1][1]["json"]
        assert "response_format" not in second_payload
        mock_sleep.assert_called_once()
        called_sleep_time = mock_sleep.call_args[0][0]
        assert 3.0 <= called_sleep_time <= 5.0


def test_calculate_backoff_delay_exponential_growth():
    delay_1 = review.calculate_backoff_delay(attempt=1, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0))
    delay_2 = review.calculate_backoff_delay(attempt=2, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0))
    delay_3 = review.calculate_backoff_delay(attempt=3, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0))
    assert delay_1 == 2.5
    assert delay_2 == 5.0
    assert delay_3 == 10.0


def test_calculate_backoff_delay_respects_retry_after():
    delay = review.calculate_backoff_delay(attempt=1, retry_after=12.5, base_delay=2.5, jitter_range=(0.0, 0.0))
    assert delay == 12.5


def test_calculate_backoff_delay_capped_at_max_delay():
    delay = review.calculate_backoff_delay(attempt=10, retry_after=100.0, max_delay=90.0, jitter_range=(0.0, 0.0))
    assert delay == 90.0


def test_chat_completion_demotes_model_on_persistent_400():
    review.ACTIVE_MODEL_CANDIDATES = ["model-400-fail", "model-ok"]

    mock_resp_400 = MagicMock()
    mock_resp_400.ok = False
    mock_resp_400.status_code = 400
    mock_resp_400.headers = {}
    mock_resp_400.text = "invalid_request_error"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "demote-400", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["model-400-fail", "model-ok"]), \
         patch("requests.post", side_effect=[mock_resp_400, mock_resp_ok]), \
         patch("time.sleep"):
        result = review.chat_completion("test demote on 400", max_retries_per_model=1)
        assert result == '{"batch": "demote-400", "findings": []}'
        assert review.ACTIVE_MODEL_CANDIDATES[0] == "model-ok"
        assert review.ACTIVE_MODEL_CANDIDATES[-1] == "model-400-fail"


def test_chat_completion_adaptive_model_promotion_and_demotion():
    review.ACTIVE_MODEL_CANDIDATES = ["model-fail", "model-ok"]

    mock_resp_fail = MagicMock()
    mock_resp_fail.ok = False
    mock_resp_fail.status_code = 429
    mock_resp_fail.headers = {}
    mock_resp_fail.text = "Rate limit reached"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "adaptive", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["model-fail", "model-ok"]), \
         patch("requests.post", side_effect=[mock_resp_fail, mock_resp_ok]), \
         patch("time.sleep"):
        result = review.chat_completion("test adaptive", max_retries_per_model=1)
        assert result == '{"batch": "adaptive", "findings": []}'
        # model-ok should now be promoted to the front, and model-fail demoted to back
        assert review.ACTIVE_MODEL_CANDIDATES[0] == "model-ok"
        assert review.ACTIVE_MODEL_CANDIDATES[-1] == "model-fail"



