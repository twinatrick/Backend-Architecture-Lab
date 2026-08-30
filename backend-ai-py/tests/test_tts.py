import os
import sys
import tempfile
from unittest.mock import MagicMock
from unittest.mock import patch

import pytest
import requests


# 先行於替換外部相依套件，避免 CI 環境未安裝或載入成本過高
class _FakeMinioError(Exception):
    """測試用 MinIO 錯誤型別替身（取代真實 minio.error.MinioError）。"""


sys.modules["uvicorn"] = MagicMock()
sys.modules["sherpa_onnx"] = MagicMock()
sys.modules["soundfile"] = MagicMock()
sys.modules["av"] = MagicMock()
sys.modules["faster_whisper"] = MagicMock()
sys.modules["minio"] = MagicMock()
sys.modules["minio.error"] = MagicMock(MinioError=_FakeMinioError)

# 確保 backend-ai-py 目錄在 Python 搜尋路徑中
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from services.gpt_sovits_client import GptSovitsClient
from services.stt_service import SttService
from services.tts_exceptions import GptSovitsError
from services.tts_exceptions import TtsTimeoutError
from services.tts_service import TtsService
from services.voice_sample_provider import VoiceSampleProvider


def test_gpt_sovits_success_returns_bytes():
    with patch("requests.post") as mock_post:
        mock_post.return_value.raise_for_status = MagicMock()
        mock_post.return_value.content = b"WAVDATA"

        client = GptSovitsClient()
        result = client.synthesize("你好", "zh", {"ref_audio_path": "/tmp/ref.wav"})

    assert result == b"WAVDATA"
    payload = mock_post.call_args.kwargs["json"]
    assert payload["text"] == "你好"
    assert payload["text_lang"] == "zh"
    assert payload["text_split_method"] == "cut5"
    assert payload["ref_audio_path"] == "/tmp/ref.wav"


def test_gpt_sovits_timeout_raises_tts_timeout_error():
    with patch("requests.post", side_effect=requests.Timeout("timeout")):
        client = GptSovitsClient()
        with pytest.raises(TtsTimeoutError):
            client.synthesize("你好", "zh", {})


def test_gpt_sovits_http_error_raises_gpt_sovits_error():
    resp = MagicMock()
    resp.raise_for_status.side_effect = requests.HTTPError("500 Internal Server Error")
    with patch("requests.post", return_value=resp):
        client = GptSovitsClient()
        with pytest.raises(GptSovitsError):
            client.synthesize("你好", "zh", {})


def test_voice_sample_download_failure_returns_empty_payload():
    with patch(
        "services.voice_sample_provider.download_from_minio",
        side_effect=OSError("minio down"),
    ):
        provider = VoiceSampleProvider()
        assert provider.resolve("ref/x.wav", None, "zh") == {}


def test_voice_sample_resolve_propagates_programming_error():
    with patch(
        "services.voice_sample_provider.download_from_minio",
        side_effect=ValueError("bad ref key"),
    ):
        provider = VoiceSampleProvider()
        with pytest.raises(ValueError):
            provider.resolve("ref/x.wav", None, "zh")


def test_tts_service_falls_back_on_timeout():
    service = TtsService()
    with (
        patch(
            "services.voice_sample_provider.download_from_minio",
            return_value="/tmp/ref.wav",
        ),
        patch.object(service.gpt_sovits, "synthesize", side_effect=TtsTimeoutError("timeout")),
        patch.object(
            service.fallback_engine, "synthesize", return_value=b"FALLBACK"
        ) as mock_fallback,
    ):
        result = service.text_to_sound("你好", "zh")

    assert result == b"FALLBACK"
    mock_fallback.assert_called_once()


def test_tts_service_falls_back_on_external_service_error():
    service = TtsService()
    with (
        patch(
            "services.voice_sample_provider.download_from_minio",
            return_value="/tmp/ref.wav",
        ),
        patch.object(service.gpt_sovits, "synthesize", side_effect=GptSovitsError("boom")),
        patch.object(
            service.fallback_engine, "synthesize", return_value=b"FALLBACK"
        ) as mock_fallback,
    ):
        result = service.text_to_sound("你好", "zh")

    assert result == b"FALLBACK"
    mock_fallback.assert_called_once()


def test_tts_service_propagates_unknown_error_without_fallback():
    service = TtsService()
    with (
        patch(
            "services.voice_sample_provider.download_from_minio",
            return_value="/tmp/ref.wav",
        ),
        patch.object(service.gpt_sovits, "synthesize", side_effect=ValueError("bad config")),
        patch.object(
            service.fallback_engine, "synthesize", return_value=b"FALLBACK"
        ) as mock_fallback,
        pytest.raises(ValueError),
    ):
        service.text_to_sound("你好", "zh")

    mock_fallback.assert_not_called()


def test_tts_service_fallback_cleans_temp_file():
    service = TtsService()
    written_paths = []

    def _fake_synthesize(text, save_path):
        with open(save_path, "wb") as f:
            f.write(b"wav")
        written_paths.append(save_path)
        return b"wav"

    with (
        patch(
            "services.voice_sample_provider.download_from_minio",
            side_effect=OSError("minio down"),
        ),
        patch.object(service.gpt_sovits, "synthesize", side_effect=GptSovitsError("boom")),
        patch.object(service.fallback_engine, "synthesize", side_effect=_fake_synthesize),
    ):
        service.text_to_sound("你好", "zh")

    assert len(written_paths) == 1
    assert not os.path.exists(written_paths[0])


def test_cleanup_oserror_does_not_raise():
    tmp_file = tempfile.NamedTemporaryFile(delete=False)
    tmp_file.close()

    with patch("os.remove", side_effect=OSError("permission denied")):
        SttService._cleanup([tmp_file.name])

    os.remove(tmp_file.name)
