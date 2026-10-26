import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import gemini_runner
import key_pool
import model_pool
import parser as parser_mod
import providers


def test_call_gemini_api_constructs_proper_request():
    mock_resp = MagicMock()
    mock_resp.ok = True
    with patch("requests.post", return_value=mock_resp) as mock_post:
        resp = providers.call_gemini_api(
            "test review prompt", "gemini-3.7-flash", "test-key-123"
        )
        assert resp == mock_resp
        assert mock_post.called
        call_url = mock_post.call_args[0][0]
        call_headers = mock_post.call_args[1]["headers"]
        call_json = mock_post.call_args[1]["json"]
        assert "models/gemini-3.7-flash:generateContent" in call_url
        assert "key=" not in call_url
        assert call_headers.get("x-goog-api-key") == "test-key-123"
        assert call_json["generationConfig"]["responseMimeType"] == "application/json"
        assert "temperature" not in call_json["generationConfig"]
        assert "test review prompt" in call_json["contents"][0]["parts"][0]["text"]


def test_gemini_build_generation_config_sampling_parameters_by_model():
    cfg_38 = providers.GeminiClient.build_generation_config("gemini-3.8-flash")
    assert "temperature" not in cfg_38
    assert "top_p" not in cfg_38
    assert "top_k" not in cfg_38
    assert cfg_38["responseMimeType"] == "application/json"
    assert cfg_38["maxOutputTokens"] == 4096

    cfg_37 = providers.GeminiClient.build_generation_config("gemini-3.7-flash")
    assert "temperature" not in cfg_37
    assert "top_p" not in cfg_37
    assert "top_k" not in cfg_37
    assert cfg_37["responseMimeType"] == "application/json"
    assert cfg_37["maxOutputTokens"] == 4096

    cfg_36 = providers.GeminiClient.build_generation_config("gemini-3.6-flash")
    assert "temperature" not in cfg_36

    cfg_35_lite = providers.GeminiClient.build_generation_config("gemini-3.5-flash-lite")
    assert "temperature" not in cfg_35_lite

    cfg_flash_latest = providers.GeminiClient.build_generation_config("gemini-flash-latest")
    assert "temperature" not in cfg_flash_latest

    cfg_25 = providers.GeminiClient.build_generation_config("gemini-2.5-flash-lite")
    assert "temperature" in cfg_25
    assert cfg_25["temperature"] == 0.1


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
    assert providers.extract_gemini_text(valid_resp) == '{"batch": "gemini-1", "findings": []}'

    with pytest.raises(ValueError):
        providers.extract_gemini_text({"candidates": []})

    with pytest.raises(ValueError):
        providers.extract_gemini_text({"candidates": [{}]})


def test_gemini_runner_quarantines_http_400_api_key_invalid_and_uses_next_key(monkeypatch):
    monkeypatch.setattr(gemini_runner.time, "sleep", lambda _sec: None)
    monkeypatch.setattr(key_pool.random, "choice", lambda seq: seq[0])
    pool = key_pool.KeyPool("GEMINI_TEST_INVALID")
    monkeypatch.setattr(
        pool,
        "get_all_keys",
        lambda: [("GEMINI_API_KEY", "bad_key"), ("GEMINI_API_KEY_1", "good_key")],
    )
    mpool = model_pool.ModelPool(["gemini-3.8-flash"])
    client = MagicMock()

    bad_resp = MagicMock()
    bad_resp.ok = False
    bad_resp.status_code = 400
    bad_resp.headers = {}
    bad_resp.text = '{"error":{"message":"API key not valid","reason":"API_KEY_INVALID"}}'

    good_resp = MagicMock()
    good_resp.ok = True
    good_resp.json.return_value = {
        "candidates": [{"content": {"parts": [{"text": '{"findings":[]}'}]}}],
    }

    def _call(prompt: str, model_name: str, api_key: str):
        return bad_resp if api_key == "bad_key" else good_resp

    client.call.side_effect = _call
    client.extract_text.return_value = '{"findings":[]}'
    errors: list[tuple[str, str]] = []

    res = gemini_runner.execute_gemini_loop(
        "prompt",
        3,
        ["gemini-3.8-flash"],
        pool,
        mpool,
        client,
        parser_mod.ReviewResponseParser(),
        errors,
    )
    assert res == '{"findings":[]}'
    assert pool.is_in_cooldown("bad_key") is True
    assert pool.get_cooldown_remaining("bad_key") > 3600.0
    assert pool.is_in_cooldown("good_key") is False
