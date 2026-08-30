import os
import sys
from unittest.mock import MagicMock
from unittest.mock import patch


# 先行替換外部相依套件，避免 CI 環境未安裝或載入成本過高
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

from config import settings
from main import app


def test_health():
    # 封鎖 lifespan 事件，避免測試期間發出真實網路請求
    with (
        patch("main._nacos_register"),
        patch("main._warmup_ollama"),
        patch("main._nacos_deregister"),
        TestClient(app) as client,
    ):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}


def test_settings():
    assert settings.service_name == "ai-py-service"
