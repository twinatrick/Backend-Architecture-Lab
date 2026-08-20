import json
import os
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import review


@pytest.fixture(autouse=True)
def reset_cooldowns_fixture():
    review.reset_key_cooldowns()
    yield
    review.reset_key_cooldowns()


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
    with patch.dict(os.environ, {"REPO": "owner/repo", "GH_TOKEN": "token"}), \
         patch("review.gh_get", return_value=[{"number": 99}]):
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
    raw = (
        '{"batch": "ci-1", "coverage": "COMPLETE", '
        '"files_reviewed": ["test.java"], "findings": []}'
    )
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
    reviewed_reversed = [
        "./.github/ai-review/tests/test_review.py",
        " .github/ai-review/review.py ",
    ]
    norm_expected = review.normalize_paths(expected)
    norm_reviewed = review.normalize_paths(reviewed_reversed)
    assert review.validate_coverage(norm_expected, norm_reviewed)


def test_validate_coverage_fails_when_missing_or_extra():
    expected = ["a.py", "b.py"]
    norm_expected = review.normalize_paths(expected)
    assert not review.validate_coverage(norm_expected, review.normalize_paths(["a.py"]))
    assert not review.validate_coverage(
        norm_expected, review.normalize_paths(["a.py", "b.py", "c.py"])
    )
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
    mock_resp_400.text = (
        '{"error":{"message":"json_validate_failed: failed to validate json schema"}}'
    )

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
    delay_1 = review.calculate_backoff_delay(
        attempt=1, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    delay_2 = review.calculate_backoff_delay(
        attempt=2, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    delay_3 = review.calculate_backoff_delay(
        attempt=3, retry_after=0.0, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    assert delay_1 == 2.5
    assert delay_2 == 5.0
    assert delay_3 == 10.0


def test_calculate_backoff_delay_respects_retry_after():
    delay = review.calculate_backoff_delay(
        attempt=1, retry_after=12.5, base_delay=2.5, jitter_range=(0.0, 0.0)
    )
    assert delay == 12.5


def test_calculate_backoff_delay_capped_at_max_delay():
    delay = review.calculate_backoff_delay(
        attempt=10, retry_after=100.0, max_delay=90.0, jitter_range=(0.0, 0.0)
    )
    assert delay == 90.0


def test_extract_json_payload_preserves_string_literals_with_commas_and_brackets():
    raw = (
        '{"batch": "ci-1", "files_reviewed": ["a.py"], '
        '"findings": [{"evidence": "items = [1,];", "problem": "issue with mapping,}"}]}'
    )
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["findings"][0]["evidence"] == "items = [1,];"
    assert parsed["findings"][0]["problem"] == "issue with mapping,}"


def test_extract_json_payload_preserves_newlines_tabs_and_code_snippets():
    raw = (
        '{\n'
        '  "batch": "business-1",\n'
        '  "files_reviewed": ["Service.java"],\n'
        '  "findings": [{\n'
        '    "severity": "HIGH",\n'
        '    "confidence": "HIGH",\n'
        '    "location": "Service.java:42",\n'
        '    "rule": "SOLID 原則",\n'
        '    "problem": "多行問題說明\\n第二行說明\\t含 Tab",\n'
        '    "evidence": "if (a) {\\n\\treturn 1;\\n}",\n'
        '    "risk": "架構風險",\n'
        '    "recommendation": "重構為介面注入"\n'
        '  }],\n'
        '  "passed_checks": ["SOLID"],\n'
        '  "coverage": "COMPLETE"\n'
        '}'
    )
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "business-1"
    finding = parsed["findings"][0]
    assert finding["problem"] == "多行問題說明\n第二行說明\t含 Tab"
    assert finding["evidence"] == "if (a) {\n\treturn 1;\n}"


def test_extract_json_payload_invalid_trailing_comma_raises_decode_error():
    raw = '{"batch": "ci-1", "files_reviewed": ["a.py", "b.py",], "findings": [],}'
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload(raw)


def test_repair_json_string_with_unclosed_think_tag():
    raw = """<think>
Some thinking that got truncated before closing tag
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["a.py"], "findings": []}"""
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"


def test_repair_json_string_with_embedded_markdown_codeblock():
    raw = """Below is the review result in JSON format:
```json
{
  "batch": "ci-1",
  "coverage": "COMPLETE",
  "files_reviewed": ["a.py"],
  "findings": []
}
```
End of review."""
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"


def test_repair_json_string_with_prose_containing_brackets_before_payload():
    raw = (
        'Here is a note with an example: {"example_key": "val"}.\n'
        'Below is the actual review output:\n'
        '{"batch": "ci-actual", "coverage": "COMPLETE", '
        '"files_reviewed": ["b.py"], "findings": []}'
    )
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "ci-actual"


def test_repair_json_string_with_nested_brackets_and_strings():
    raw = (
        'Explanation: {\n'
        '  "batch": "nested-1",\n'
        '  "files_reviewed": ["c.java"],\n'
        '  "findings": [{\n'
        '    "severity": "HIGH",\n'
        '    "evidence": "void fn() { map.put(\\"key\\", new Object() {}); }",\n'
        '    "problem": "nested braces in {string}"\n'
        '  }]\n'
        '}\n'
        'End of output'
    )
    parsed = review.extract_json_payload(raw)
    assert parsed["batch"] == "nested-1"
    assert parsed["findings"][0]["evidence"] == 'void fn() { map.put("key", new Object() {}); }'


def test_extract_json_payload_rejects_non_dict():
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload('["item1", "item2"]')
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload('"just a string"')
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload('12345')


def test_extract_json_payload_rejects_conflicting_multiple_review_payloads():
    raw = (
        '{"batch": "ci-1", "files_reviewed": ["a.py"], "findings": []}\n'
        '{"batch": "ci-2", "files_reviewed": ["b.py"], "findings": []}'
    )
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload(raw)



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


def test_chat_completion_retries_on_invalid_json_then_succeeds():
    mock_resp_invalid = MagicMock()
    mock_resp_invalid.ok = True
    mock_resp_invalid.json.return_value = {
        "choices": [{"message": {"content": "This is not valid json {"}}]
    }

    mock_resp_valid = MagicMock()
    mock_resp_valid.ok = True
    mock_resp_valid.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "test-valid", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", side_effect=[mock_resp_invalid, mock_resp_valid]) as mock_post, \
         patch("time.sleep") as mock_sleep:
        result = review.chat_completion("test prompt", max_retries_per_model=5)
        assert result == '{"batch": "test-valid", "findings": []}'
        assert mock_post.call_count == 2
        mock_sleep.assert_called_once()


def test_chat_completion_fails_over_to_next_model_after_5_invalid_json_attempts():
    review.ACTIVE_MODEL_CANDIDATES = ["model-bad-json", "model-good-json"]

    mock_resp_invalid = MagicMock()
    mock_resp_invalid.ok = True
    mock_resp_invalid.json.return_value = {
        "choices": [{"message": {"content": "Invalid truncated JSON { unterminated..."}}]
    }

    mock_resp_valid = MagicMock()
    mock_resp_valid.ok = True
    mock_resp_valid.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "good-model", "findings": []}'}}]
    }

    responses = [mock_resp_invalid] * 5 + [mock_resp_valid]

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["model-bad-json", "model-good-json"]), \
         patch("requests.post", side_effect=responses) as mock_post, \
         patch("time.sleep"):
        result = review.chat_completion("test prompt", max_retries_per_model=5)
        assert result == '{"batch": "good-model", "findings": []}'
        assert mock_post.call_count == 6
        assert review.ACTIVE_MODEL_CANDIDATES[0] == "model-good-json"
        assert review.ACTIVE_MODEL_CANDIDATES[-1] == "model-bad-json"


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


def test_parse_retry_limit_valid():
    assert review.parse_retry_limit("5") == 5
    assert review.parse_retry_limit("1") == 1
    assert review.parse_retry_limit("12") == 12
    assert review.parse_retry_limit("50") == 12
    assert review.parse_retry_limit("100") == 12


def test_parse_retry_limit_invalid_or_empty():
    assert review.parse_retry_limit(None) == 3
    assert review.parse_retry_limit("") == 3
    assert review.parse_retry_limit("   ") == 3
    assert review.parse_retry_limit("not_a_number") == 3
    assert review.parse_retry_limit("0") == 3
    assert review.parse_retry_limit("-5") == 3


def test_chat_completion_demotes_model_on_413_payload_too_large():
    review.ACTIVE_MODEL_CANDIDATES = ["model-413-fail", "model-fallback-ok"]

    mock_resp_413 = MagicMock()
    mock_resp_413.ok = False
    mock_resp_413.status_code = 413
    mock_resp_413.headers = {}
    mock_resp_413.text = "Request Entity Too Large"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "413-ok", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["model-413-fail", "model-fallback-ok"]), \
         patch("requests.post", side_effect=[mock_resp_413, mock_resp_ok]), \
         patch("time.sleep"):
        result = review.chat_completion("test payload too large", max_retries_per_model=5)
        assert result == '{"batch": "413-ok", "findings": []}'
        assert review.ACTIVE_MODEL_CANDIDATES[0] == "model-fallback-ok"
        assert review.ACTIVE_MODEL_CANDIDATES[-1] == "model-413-fail"


def test_chat_completion_demotes_model_on_429_tpd_or_long_wait():
    review.ACTIVE_MODEL_CANDIDATES = ["model-tpd-fail", "model-fallback-ok"]

    mock_resp_429_tpd = MagicMock()
    mock_resp_429_tpd.ok = False
    mock_resp_429_tpd.status_code = 429
    mock_resp_429_tpd.headers = {"retry-after": "120"}
    mock_resp_429_tpd.text = "TPD limit reached: daily quota exhausted"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "429-tpd-ok", "findings": []}'}}]
    }

    with patch.dict(os.environ, {"GROQ_API_KEY": "fake_key"}), \
         patch("review.get_available_models", return_value=["model-tpd-fail", "model-fallback-ok"]), \
         patch("requests.post", side_effect=[mock_resp_429_tpd, mock_resp_ok]), \
         patch("time.sleep"):
        result = review.chat_completion("test 429 tpd", max_retries_per_model=5)
        assert result == '{"batch": "429-tpd-ok", "findings": []}'
        assert review.ACTIVE_MODEL_CANDIDATES[0] == "model-fallback-ok"
        assert review.ACTIVE_MODEL_CANDIDATES[-1] == "model-tpd-fail"


def test_extract_json_payload_rejects_unparseable_balanced_candidates():
    # 含有平衡括號但內部損毀非合法 JSON 的情況
    raw = "Prefix { this is broken key: value without quotes } suffix"
    with pytest.raises(json.JSONDecodeError):
        review.extract_json_payload(raw)


def test_mask_api_key():
    assert review.mask_api_key("") == ""
    assert review.mask_api_key(None) == ""
    assert review.mask_api_key("   ") == ""
    masked_short = review.mask_api_key("12345678")
    assert masked_short.startswith("sha256:")
    assert len(masked_short) == 15
    assert "1234" not in masked_short
    assert "5678" not in masked_short

    key = "AIzaSyBMMcmOmGpClLzf14bpHR3uKY6bfIK6kc8"
    masked = review.mask_api_key(key)
    assert masked.startswith("sha256:")
    assert "AIza" not in masked
    assert "6kc8" not in masked


def test_get_gemini_api_keys_discovery_and_sorting():
    env_vars = {
        "GEMINI_API_KEY_2": "key_two",
        "GEMINI_API_KEY_10": "key_ten",
        "GEMINI_API_KEY": "key_default",
        "GEMINI_API_KEY_1": "key_one",
        "GEMINI_API_KEY_B": "key_beta",  # 非純數字後綴應被嚴格過濾
        "GEMINI_API_KEY_A": "key_alpha",
        "GEMINI_API_KEY_EMPTY": "",
        "GEMINI_API_KEY_BLANK": "   ",
        "GEMINI_API_KEY_DUP": "key_one",  # 重複金鑰值應被去重
        "OTHER_VAR": "something_else",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        keys = review.get_gemini_api_keys()
        var_names = [k[0] for k in keys]
        key_vals = [k[1] for k in keys]
        assert var_names == [
            "GEMINI_API_KEY",
            "GEMINI_API_KEY_1",
            "GEMINI_API_KEY_2",
            "GEMINI_API_KEY_10",
        ]
        assert key_vals == ["key_default", "key_one", "key_two", "key_ten"]


def test_get_groq_api_keys_discovery_and_sorting():
    env_vars = {
        "GROQ_API_KEY_3": "groq_three",
        "GROQ_API_KEY": "groq_default",
        "GROQ_API_KEY_1": "groq_one",
        "GROQ_API_KEY_XYZ": "invalid_suffix",
        "GROQ_API_KEY_DUP": "groq_default",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        keys = review.get_groq_api_keys()
        var_names = [k[0] for k in keys]
        key_vals = [k[1] for k in keys]
        assert var_names == [
            "GROQ_API_KEY",
            "GROQ_API_KEY_1",
            "GROQ_API_KEY_3",
        ]
        assert key_vals == ["groq_default", "groq_one", "groq_three"]


def test_redact_secrets():
    env_vars = {
        "GROQ_API_KEY": "gsk_1234567890abcdef1234567890abcdef",
        "GEMINI_API_KEY": "AIzaSyTestKey1234567890abcdef1234567890",
        "GH_TOKEN": "ghp_1234567890abcdef1234567890abcdef",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        raw_msg = (
            "Error with key gsk_1234567890abcdef1234567890abcdef "
            "and Gemini AIzaSyTestKey1234567890abcdef1234567890 "
            "and GitHub ghp_1234567890abcdef1234567890abcdef "
            "and pat github_pat_11ABCD1234567890123456_abcdef"
        )
        redacted = review.redact_secrets(raw_msg)
        assert "gsk_" not in redacted
        assert "AIza" not in redacted
        assert "ghp_" not in redacted
        assert "github_pat_" not in redacted
        assert "[REDACTED]" in redacted


def test_call_gemini_api_constructs_proper_request():
    mock_resp = MagicMock()
    mock_resp.ok = True
    with patch("requests.post", return_value=mock_resp) as mock_post:
        resp = review.call_gemini_api("test review prompt", "gemini-2.0-flash", "test-key-123")
        assert resp == mock_resp
        assert mock_post.called
        call_url = mock_post.call_args[0][0]
        call_headers = mock_post.call_args[1]["headers"]
        call_json = mock_post.call_args[1]["json"]
        assert "models/gemini-2.0-flash:generateContent" in call_url
        assert "key=" not in call_url
        assert call_headers.get("x-goog-api-key") == "test-key-123"
        assert call_json["generationConfig"]["responseMimeType"] == "application/json"
        assert "test review prompt" in call_json["contents"][0]["parts"][0]["text"]


def test_extract_gemini_text_valid_and_invalid():
    valid_resp = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-1", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }
    assert review.extract_gemini_text(valid_resp) == '{"batch": "gemini-1", "findings": []}'

    with pytest.raises(ValueError):
        review.extract_gemini_text({"candidates": []})

    with pytest.raises(ValueError):
        review.extract_gemini_text({"candidates": [{}]})


def test_chat_completion_gemini_single_key_success():
    mock_resp = MagicMock()
    mock_resp.ok = True
    mock_resp.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-pass", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }

    with patch.dict(os.environ, {"GEMINI_API_KEY": "test_gemini_key"}, clear=True), \
         patch("requests.post", return_value=mock_resp) as mock_post:
        result = review.chat_completion("review prompt")
        assert result == '{"batch": "gemini-pass", "findings": []}'
        assert mock_post.called
        assert mock_post.call_args[1]["headers"].get("x-goog-api-key") == "test_gemini_key"


def test_chat_completion_gemini_multi_key_rotation_on_429():
    mock_resp_429 = MagicMock()
    mock_resp_429.ok = False
    mock_resp_429.status_code = 429
    mock_resp_429.text = "Quota exceeded (429 RESOURCE_EXHAUSTED)"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-key2-ok", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }

    env_vars = {
        "GEMINI_API_KEY_1": "bad_key_rate_limited",
        "GEMINI_API_KEY_2": "good_key_working",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("requests.post", side_effect=[mock_resp_429, mock_resp_ok]) as mock_post, \
         patch("time.sleep"):
        result = review.chat_completion("review prompt", max_retries_per_model=3)
        assert result == '{"batch": "gemini-key2-ok", "findings": []}'
        assert mock_post.call_count == 2
        # Verify first call used key 1, second call rotated to key 2
        assert mock_post.call_args_list[0][1]["headers"].get("x-goog-api-key") == "bad_key_rate_limited"
        assert mock_post.call_args_list[1][1]["headers"].get("x-goog-api-key") == "good_key_working"
        assert review.is_key_in_cooldown("bad_key_rate_limited") is True
        assert review.is_key_in_cooldown("good_key_working") is False


def test_chat_completion_gemini_multi_key_rotation_on_403():
    mock_resp_403 = MagicMock()
    mock_resp_403.ok = False
    mock_resp_403.status_code = 403
    mock_resp_403.text = "The caller does not have permission (403)"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-key2-403-ok", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }

    env_vars = {
        "GEMINI_API_KEY_1": "invalid_key",
        "GEMINI_API_KEY_2": "valid_key",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("requests.post", side_effect=[mock_resp_403, mock_resp_ok]) as mock_post, \
         patch("time.sleep"):
        result = review.chat_completion("review prompt", max_retries_per_model=3)
        assert result == '{"batch": "gemini-key2-403-ok", "findings": []}'
        assert mock_post.call_count == 2
        assert mock_post.call_args_list[0][1]["headers"].get("x-goog-api-key") == "invalid_key"
        assert mock_post.call_args_list[1][1]["headers"].get("x-goog-api-key") == "valid_key"
        assert review.is_key_in_cooldown("invalid_key") is True


def test_chat_completion_groq_multi_key_rotation_on_429():
    mock_resp_429 = MagicMock()
    mock_resp_429.ok = False
    mock_resp_429.status_code = 429
    mock_resp_429.headers = {"retry-after": "120"}
    mock_resp_429.text = "Rate limit reached (429)"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "groq-key2-ok", "findings": []}'}}]
    }

    env_vars = {
        "GROQ_API_KEY_1": "bad_groq_key_rate_limited",
        "GROQ_API_KEY_2": "good_groq_key_working",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", side_effect=[mock_resp_429, mock_resp_ok]) as mock_post, \
         patch("time.sleep"):
        result = review.chat_completion("review prompt", max_retries_per_model=3)
        assert result == '{"batch": "groq-key2-ok", "findings": []}'
        assert mock_post.call_count == 2
        first_auth = mock_post.call_args_list[0][1]["headers"]["Authorization"]
        second_auth = mock_post.call_args_list[1][1]["headers"]["Authorization"]
        assert first_auth == "Bearer bad_groq_key_rate_limited"
        assert second_auth == "Bearer good_groq_key_working"
        assert review.is_key_in_cooldown("bad_groq_key_rate_limited") is True
        assert review.is_key_in_cooldown("good_groq_key_working") is False


def test_chat_completion_groq_multi_key_rotation_on_403():
    mock_resp_403 = MagicMock()
    mock_resp_403.ok = False
    mock_resp_403.status_code = 403
    mock_resp_403.text = "Invalid API Key (403)"

    mock_resp_ok = MagicMock()
    mock_resp_ok.ok = True
    mock_resp_ok.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "groq-key2-403-ok", "findings": []}'}}]
    }

    env_vars = {
        "GROQ_API_KEY_1": "invalid_groq_key",
        "GROQ_API_KEY_2": "valid_groq_key",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", side_effect=[mock_resp_403, mock_resp_ok]) as mock_post, \
         patch("time.sleep"):
        result = review.chat_completion("review prompt", max_retries_per_model=3)
        assert result == '{"batch": "groq-key2-403-ok", "findings": []}'
        assert mock_post.call_count == 2
        first_auth = mock_post.call_args_list[0][1]["headers"]["Authorization"]
        second_auth = mock_post.call_args_list[1][1]["headers"]["Authorization"]
        assert first_auth == "Bearer invalid_groq_key"
        assert second_auth == "Bearer valid_groq_key"
        assert review.is_key_in_cooldown("invalid_groq_key") is True


def test_chat_completion_groq_falls_back_to_gemini_when_groq_exhausted():
    review.ACTIVE_MODEL_CANDIDATES = ["groq-test-fail"]
    review.ACTIVE_GEMINI_MODELS = ["gemini-test-ok"]

    mock_resp_groq_fail = MagicMock()
    mock_resp_groq_fail.ok = False
    mock_resp_groq_fail.status_code = 429
    mock_resp_groq_fail.headers = {"retry-after": "120"}
    mock_resp_groq_fail.text = "Groq all keys exhausted"

    mock_resp_gemini_ok = MagicMock()
    mock_resp_gemini_ok.ok = True
    mock_resp_gemini_ok.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-fallback-ok", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }

    env_vars = {
        "GROQ_API_KEY": "failing_groq_key",
        "GEMINI_API_KEY": "working_gemini_key",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("review.get_available_models", return_value=["groq-test-fail"]), \
         patch("requests.post", side_effect=[mock_resp_groq_fail, mock_resp_gemini_ok]), \
         patch("time.sleep"):
        result = review.chat_completion("fallback test", max_retries_per_model=1)
        assert result == '{"batch": "gemini-fallback-ok", "findings": []}'


def test_chat_completion_raises_when_no_api_keys_configured():
    with patch.dict(os.environ, {}, clear=True):
        with pytest.raises(RuntimeError) as exc_info:
            review.chat_completion("test without keys")
        assert "未配置任何 AI Provider 密鑰" in str(exc_info.value)


def test_key_cooldown_marking_and_expiration():
    review.reset_key_cooldowns()
    test_key = "test_cooldown_key_123"

    assert review.is_key_in_cooldown(test_key) is False
    assert review.get_key_cooldown_remaining(test_key) == 0.0

    # 標記冷卻 2 秒
    review.mark_key_cooldown(test_key, 2.0)
    assert review.is_key_in_cooldown(test_key) is True
    assert review.get_key_cooldown_remaining(test_key) > 0.0

    # 模擬時間過期
    with patch("time.time", return_value=review.time.time() + 3.0):
        assert review.is_key_in_cooldown(test_key) is False
        assert review.get_key_cooldown_remaining(test_key) == 0.0


def test_get_active_keys_filters_cooldown_keys():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    review.mark_key_cooldown("val_2", 60.0)

    active = review.get_active_keys(keys)
    assert len(active) == 2
    assert ("KEY_1", "val_1") in active
    assert ("KEY_3", "val_3") in active
    assert ("KEY_2", "val_2") not in active


def test_pick_random_active_key_behavior():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    # 當排除 KEY_1 且 KEY_2 在冷卻中時，只能抽到 KEY_3
    review.mark_key_cooldown("val_2", 60.0)
    picked = review.pick_random_active_key(keys, excluded_keys={"val_1"})
    assert picked == ("KEY_3", "val_3")

    # 若所有金鑰均被排除或冷卻，回傳 None
    picked_none = review.pick_random_active_key(keys, excluded_keys={"val_1", "val_3"})
    assert picked_none is None


def test_pick_random_active_key_distribution():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    picked_counts = {"val_1": 0, "val_2": 0, "val_3": 0}
    for _ in range(300):
        res = review.pick_random_active_key(keys)
        assert res is not None
        picked_counts[res[1]] += 1

    # 300 次隨機抽取中，每個 Key 至少被抽到 30 次以上
    assert picked_counts["val_1"] > 30
    assert picked_counts["val_2"] > 30
    assert picked_counts["val_3"] > 30


def test_chat_completion_groq_429_persists_cooldown_for_subsequent_batch():
    mock_resp_429 = MagicMock()
    mock_resp_429.ok = False
    mock_resp_429.status_code = 429
    mock_resp_429.headers = {"retry-after": "60"}
    mock_resp_429.text = "Rate limit reached (429)"

    mock_resp_ok1 = MagicMock()
    mock_resp_ok1.ok = True
    mock_resp_ok1.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "batch1-ok", "findings": []}'}}]
    }

    mock_resp_ok2 = MagicMock()
    mock_resp_ok2.ok = True
    mock_resp_ok2.json.return_value = {
        "choices": [{"message": {"content": '{"batch": "batch2-ok", "findings": []}'}}]
    }

    env_vars = {
        "GROQ_API_KEY_1": "groq_key_1",
        "GROQ_API_KEY_2": "groq_key_2",
    }
    with patch.dict(os.environ, env_vars, clear=True), \
         patch("random.choice", side_effect=lambda pool: pool[0]), \
         patch("review.get_available_models", return_value=["llama-3.3-70b-versatile"]), \
         patch("requests.post", side_effect=[mock_resp_429, mock_resp_ok1, mock_resp_ok2]) as mock_post, \
         patch("time.sleep"):

        # 批次 1：第 1 把 Key 遇到 429 進入冷卻，第 2 把 Key 成功
        res1 = review.chat_completion("batch 1 prompt")
        assert res1 == '{"batch": "batch1-ok", "findings": []}'
        assert review.is_key_in_cooldown("groq_key_1") is True

        # 批次 2：因為 groq_key_1 仍在冷卻清單中，直接挑選未冷卻的 groq_key_2，無須再次踩雷 429！
        res2 = review.chat_completion("batch 2 prompt")
        assert res2 == '{"batch": "batch2-ok", "findings": []}'

        assert mock_post.call_count == 3
        # 第一次嘗試 key 1 (429)，第二次嘗試 key 2 (成功)，第三次直接使用 key 2 (成功)
        assert mock_post.call_args_list[0][1]["headers"]["Authorization"] == "Bearer groq_key_1"
        assert mock_post.call_args_list[1][1]["headers"]["Authorization"] == "Bearer groq_key_2"
        assert mock_post.call_args_list[2][1]["headers"]["Authorization"] == "Bearer groq_key_2"


def test_chat_completion_all_groq_cooling_immediately_falls_back_to_gemini():
    review.ACTIVE_MODEL_CANDIDATES = ["llama-3.3-70b-versatile"]
    review.ACTIVE_GEMINI_MODELS = ["gemini-2.5-flash"]

    mock_resp_gemini_ok = MagicMock()
    mock_resp_gemini_ok.ok = True
    mock_resp_gemini_ok.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "gemini-fast-ok", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }

    env_vars = {
        "GROQ_API_KEY_1": "cooling_groq_key",
        "GEMINI_API_KEY_1": "active_gemini_key",
    }
    # 先將 Groq Key 設為冷卻中
    review.mark_key_cooldown("cooling_groq_key", 120.0)

    with patch.dict(os.environ, env_vars, clear=True), \
         patch("requests.post", return_value=mock_resp_gemini_ok) as mock_post, \
         patch("time.sleep"):
        res = review.chat_completion("prompt when groq is cooling")
        assert res == '{"batch": "gemini-fast-ok", "findings": []}'
        # 直接呼叫 Gemini，未對冷卻的 Groq 進行無效請求
        assert mock_post.call_count == 1
        assert mock_post.call_args[1]["headers"].get("x-goog-api-key") == "active_gemini_key"


def test_key_pool_isolation():
    pool1 = review.KeyPool("PREFIX1")
    pool2 = review.KeyPool("PREFIX2")

    pool1.mark_cooldown("key_a", 100.0)
    assert pool1.is_in_cooldown("key_a") is True
    assert pool2.is_in_cooldown("key_a") is False

    pool1.reset_cooldowns()
    assert pool1.is_in_cooldown("key_a") is False


def test_model_pool_isolation_and_order():
    pool1 = review.ModelPool(["model-a", "model-b", "model-c"])
    pool2 = review.ModelPool(["model-x", "model-y"])

    pool1.demote("model-a")
    assert pool1.get_candidates() == ["model-b", "model-c", "model-a"]

    pool1.promote("model-c")
    assert pool1.get_candidates() == ["model-c", "model-b", "model-a"]

    # pool2 is unchanged
    assert pool2.get_candidates() == ["model-x", "model-y"]


def test_review_orchestrator_custom_instances():
    pool_groq = review.KeyPool()
    pool_gemini = review.KeyPool()
    pool_gemini.get_all_keys = MagicMock(return_value=[("CUSTOM_GEMINI", "custom_key")])

    mock_gemini_client = MagicMock()
    mock_gemini_resp = MagicMock()
    mock_gemini_resp.ok = True
    mock_gemini_resp.json.return_value = {
        "candidates": [
            {
                "content": {
                    "parts": [{"text": '{"batch": "custom-ok", "findings": []}'}],
                    "role": "model",
                }
            }
        ]
    }
    mock_gemini_client.call.return_value = mock_gemini_resp
    mock_gemini_client.extract_text = review.GeminiClient.extract_text

    orchestrator = review.ReviewOrchestrator(
        groq_key_pool=pool_groq,
        gemini_key_pool=pool_gemini,
        gemini_client=mock_gemini_client,
    )

    result = orchestrator.chat_completion("test custom orchestrator")
    assert result == '{"batch": "custom-ok", "findings": []}'
    mock_gemini_client.call.assert_called_once()


def test_build_batches_categorization_and_chunking():
    files = [
        {"filename": ".github/workflows/ci.yml", "patch": "diff -- ci"},
        {"filename": "backend-auth/src/main/java/AuthController.java", "patch": "diff -- auth"},
        {"filename": "backend-service/src/main/java/OrderService.java", "patch": "diff -- service"},
        {"filename": "backend-data/src/main/java/UserEntity.java", "patch": "diff -- entity"},
        {"filename": "backend-feign/src/main/java/UserClient.java", "patch": "diff -- client"},
        {"filename": "backend-ai-py/main.py", "patch": "diff -- python"},
        {"filename": "README.md", "patch": "diff -- doc"},
    ]
    batches = review.build_batches(files, max_chars=10000)
    scopes = [scope for scope, _ in batches]
    assert "ci" in scopes
    assert "security-api" in scopes
    assert "business" in scopes
    assert "data" in scopes
    assert "integration" in scopes
    assert "python" in scopes
    assert "other" in scopes

    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted([f["filename"] for f in files])


def test_build_batches_splits_large_batch():
    files = [
        {"filename": f"backend-service/Service{i}.java", "patch": "+" * 8000}
        for i in range(4)
    ]
    # Total chars per file ~ 8000 + 1000 = 9000. With max_chars=15000, 4 files should split into 2-4 batches.
    batches = review.build_batches(files, max_chars=15000)
    assert len(batches) >= 2
    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted([f["filename"] for f in files])
    assert len(flattened) == 4


def test_build_batches_handles_missing_patch():
    files = [
        {"filename": "deleted.txt", "patch": None, "status": "removed"},
        {"filename": "binary.png", "patch": None, "status": "added"},
    ]
    batches = review.build_batches(files, max_chars=5000)
    flattened = [filename for _, paths in batches for filename in paths]
    assert sorted(flattened) == sorted(["deleted.txt", "binary.png"])








