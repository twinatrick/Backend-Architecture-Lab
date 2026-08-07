import sys
import os
from contextlib import ExitStack
from unittest.mock import patch, MagicMock

# Mock out external libraries that might not be installed or are heavy in CI
sys.modules["uvicorn"] = MagicMock()
sys.modules["sherpa_onnx"] = MagicMock()
sys.modules["soundfile"] = MagicMock()
sys.modules["av"] = MagicMock()
sys.modules["faster_whisper"] = MagicMock()
sys.modules["minio"] = MagicMock()

from fastapi.testclient import TestClient  # noqa: E402

# Ensure backend-ai-py folder is in the python search path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app  # noqa: E402


def _make_client():
    with (
        patch("main._nacos_register"),
        patch("main._warmup_ollama"),
        patch("main._nacos_deregister"),
    ):
        return TestClient(app)


def _patch_stt_flow():
    """統一 mock STT 管線的外部依賴（MinIO 下載、轉檔、音訊長度）。"""
    stack = ExitStack()
    stack.enter_context(patch("routers.stt.download_from_minio", return_value="tmp/input.wav"))
    stack.enter_context(patch("routers.stt.convert_to_wav", return_value="tmp/input-converted.wav"))
    stack.enter_context(patch("routers.stt.get_audio_duration", return_value=2.5))
    return stack


def test_stt_whisper_returns_fields_and_uses_whisper_provider():
    with (
        _patch_stt_flow(),
        patch("routers.stt.sound_to_text", return_value="你好世界") as mock_stt,
    ):
        with _make_client() as client:
            response = client.post("/stt/whisper?object_key=audio/a.wav&language=zh")

            assert response.status_code == 200
            body = response.json()
            assert body["text"] == "你好世界"
            assert body["language"] == "zh"
            assert body["duration_sec"] == 2.5
            assert body["audio_url"].startswith("http://localhost:9000")

            mock_stt.assert_called_once()
            assert mock_stt.call_args.args[2] == "whisper"


def test_stt_sensevoice_uses_sensevoice_provider():
    with (
        _patch_stt_flow(),
        patch("routers.stt.sound_to_text", return_value="你好世界") as mock_stt,
    ):
        with _make_client() as client:
            response = client.post("/stt/sensevoice?object_key=audio/a.wav&language=zh")

            assert response.status_code == 200
            assert response.json()["text"] == "你好世界"

            mock_stt.assert_called_once()
            assert mock_stt.call_args.args[2] == "sensevoice"


def test_stt_endpoint_forwards_provider_param():
    with (
        _patch_stt_flow(),
        patch("routers.stt.sound_to_text", return_value="你好世界") as mock_stt,
    ):
        with _make_client() as client:
            response = client.post(
                "/stt?object_key=audio/a.wav&language=zh&provider=sensevoice"
            )

            assert response.status_code == 200
            mock_stt.assert_called_once()
            assert mock_stt.call_args.args[2] == "sensevoice"


def test_stt_endpoint_default_provider_is_empty():
    with (
        _patch_stt_flow(),
        patch("routers.stt.sound_to_text", return_value="你好世界") as mock_stt,
    ):
        with _make_client() as client:
            response = client.post("/stt?object_key=audio/a.wav&language=zh")

            assert response.status_code == 200
            assert response.json()["text"] == "你好世界"
            mock_stt.assert_called_once()
            assert mock_stt.call_args.args[2] == ""
