import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

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

    cfg_25 = providers.GeminiClient.build_generation_config("gemini-2.5-flash")
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
