import os
import sys
from contextlib import ExitStack
from unittest.mock import MagicMock
from unittest.mock import patch


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

from fastapi.testclient import TestClient

# 確保 backend-ai-py 目錄在 Python 搜尋路徑中
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app


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
    stack.enter_context(
        patch("services.stt_service.AudioStorage.download", return_value="tmp/input.wav")
    )
    stack.enter_context(
        patch(
            "services.stt_service.AudioConverter.convert_to_wav",
            return_value="tmp/input-converted.wav",
        )
    )
    stack.enter_context(patch("services.stt_service.AudioConverter.get_duration", return_value=2.5))
    return stack


def test_stt_whisper_returns_fields_and_uses_whisper_provider():
    with (
        _patch_stt_flow(),
        patch("services.stt_service.SttEngine.transcribe", return_value="你好世界") as mock_stt,
        _make_client() as client,
    ):
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
        patch("services.stt_service.SttEngine.transcribe", return_value="你好世界") as mock_stt,
        _make_client() as client,
    ):
        response = client.post("/stt/sensevoice?object_key=audio/a.wav&language=zh")

        assert response.status_code == 200
        assert response.json()["text"] == "你好世界"

        mock_stt.assert_called_once()
        assert mock_stt.call_args.args[2] == "sensevoice"


def test_stt_endpoint_forwards_provider_param():
    with (
        _patch_stt_flow(),
        patch("services.stt_service.SttEngine.transcribe", return_value="你好世界") as mock_stt,
        _make_client() as client,
    ):
        response = client.post("/stt?object_key=audio/a.wav&language=zh&provider=sensevoice")

        assert response.status_code == 200
        mock_stt.assert_called_once()
        assert mock_stt.call_args.args[2] == "sensevoice"


def test_stt_endpoint_default_provider_is_empty():
    with (
        _patch_stt_flow(),
        patch("services.stt_service.SttEngine.transcribe", return_value="你好世界") as mock_stt,
        _make_client() as client,
    ):
        response = client.post("/stt?object_key=audio/a.wav&language=zh")

        assert response.status_code == 200
        assert response.json()["text"] == "你好世界"
        mock_stt.assert_called_once()
        assert mock_stt.call_args.args[2] == ""
