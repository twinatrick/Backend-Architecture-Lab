import sys
import os
from unittest.mock import patch
from fastapi.testclient import TestClient

# Ensure backend-ai-py folder is in the python search path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from main import app


def test_health():
    # Mock the lifespan events to avoid any real network requests during testing
    with (
        patch("main._nacos_register"),
        patch("main._warmup_ollama"),
        patch("main._nacos_deregister"),
    ):
        with TestClient(app) as client:
            response = client.get("/health")
            assert response.status_code == 200
            assert response.json() == {"status": "ok"}


def test_settings():
    from config import settings

    assert settings.service_name == "ai-py-service"
