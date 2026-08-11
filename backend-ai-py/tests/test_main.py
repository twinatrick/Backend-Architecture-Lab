import os
import sys
from unittest.mock import MagicMock
from unittest.mock import patch

# 先行替換外部相依套件，避免 CI 環境未安裝或載入成本過高
sys.modules["uvicorn"] = MagicMock()
sys.modules["sherpa_onnx"] = MagicMock()
sys.modules["soundfile"] = MagicMock()
sys.modules["av"] = MagicMock()
sys.modules["faster_whisper"] = MagicMock()
sys.modules["minio"] = MagicMock()

from fastapi.testclient import TestClient  # noqa: E402

# 確保 backend-ai-py 目錄在 Python 搜尋路徑中
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app  # noqa: E402


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
    from config import settings  # noqa: E402

    assert settings.service_name == "ai-py-service"
