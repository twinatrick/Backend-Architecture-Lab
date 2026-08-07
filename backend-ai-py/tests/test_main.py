import os
import sys
from unittest.mock import MagicMock
from unittest.mock import patch

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


def test_health():
    # Mock the lifespan events to avoid any real network requests during testing
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
    from config import settings

    assert settings.service_name == "ai-py-service"
