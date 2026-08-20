import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import orchestrator


def test_orchestrator_initializes_with_defaults():
    orch = orchestrator.ReviewOrchestrator()
    assert orch.groq_key_pool is not None
    assert orch.gemini_key_pool is not None
    assert orch.groq_model_pool is not None
    assert orch.gemini_model_pool is not None


def test_orchestrator_chat_completion_no_keys():
    groq_pool = MagicMock()
    groq_pool.get_all_keys.return_value = []
    gemini_pool = MagicMock()
    gemini_pool.get_all_keys.return_value = []

    orch = orchestrator.ReviewOrchestrator(
        groq_key_pool=groq_pool,
        gemini_key_pool=gemini_pool,
    )
    with pytest.raises(RuntimeError) as exc_info:
        orch.chat_completion("hello")
    assert "未配置任何 AI Provider 密鑰" in str(exc_info.value)


def test_orchestrator_chat_completion_groq_success(monkeypatch):
    groq_pool = MagicMock()
    groq_pool.get_all_keys.return_value = [("GROQ_API_KEY", "key1")]
    gemini_pool = MagicMock()
    gemini_pool.get_all_keys.return_value = []

    monkeypatch.setattr(
        orchestrator,
        "execute_groq_loop",
        MagicMock(return_value="[{\"location\": \"a.py:1\"}]"),
    )

    orch = orchestrator.ReviewOrchestrator(
        groq_key_pool=groq_pool,
        gemini_key_pool=gemini_pool,
    )
    result = orch.chat_completion("test prompt")
    assert result == "[{\"location\": \"a.py:1\"}]"


def test_orchestrator_chat_completion_fallback_to_gemini(monkeypatch):
    groq_pool = MagicMock()
    groq_pool.get_all_keys.return_value = [("GROQ_API_KEY", "key1")]
    gemini_pool = MagicMock()
    gemini_pool.get_all_keys.return_value = [("GEMINI_API_KEY", "gkey1")]

    monkeypatch.setattr(orchestrator, "execute_groq_loop", MagicMock(return_value=None))
    monkeypatch.setattr(
        orchestrator,
        "execute_gemini_loop",
        MagicMock(return_value="[{\"location\": \"b.py:2\"}]"),
    )

    orch = orchestrator.ReviewOrchestrator(
        groq_key_pool=groq_pool,
        gemini_key_pool=gemini_pool,
    )
    result = orch.chat_completion("test prompt")
    assert result == "[{\"location\": \"b.py:2\"}]"


def test_orchestrator_chat_completion_all_fail(monkeypatch):
    groq_pool = MagicMock()
    groq_pool.get_all_keys.return_value = [("GROQ_API_KEY", "key1")]
    gemini_pool = MagicMock()
    gemini_pool.get_all_keys.return_value = [("GEMINI_API_KEY", "gkey1")]

    monkeypatch.setattr(orchestrator, "execute_groq_loop", MagicMock(return_value=None))
    monkeypatch.setattr(orchestrator, "execute_gemini_loop", MagicMock(return_value=None))

    orch = orchestrator.ReviewOrchestrator(
        groq_key_pool=groq_pool,
        gemini_key_pool=gemini_pool,
    )
    with pytest.raises(RuntimeError):
        orch.chat_completion("test prompt")


def test_orchestrator_groq_tpd_exhaustion_switches_to_gemini(monkeypatch):
    groq_pool = MagicMock()
    groq_pool.get_all_keys.return_value = [("GROQ_API_KEY", "key1")]
    gemini_pool = MagicMock()
    gemini_pool.get_all_keys.return_value = [("GEMINI_API_KEY", "gkey1")]

    def fake_groq_loop(*args, **kwargs):
        error_details = kwargs.get("error_details", args[7] if len(args) > 7 else [])
        error_details.append(("Groq/llama-3.3-70b-versatile", "HTTP 429: tokens per day (TPD)"))
        return None

    monkeypatch.setattr(orchestrator, "execute_groq_loop", fake_groq_loop)
    monkeypatch.setattr(
        orchestrator,
        "execute_gemini_loop",
        MagicMock(return_value="[{\"location\": \"gemini.py:1\"}]"),
    )

    orch = orchestrator.ReviewOrchestrator(
        groq_key_pool=groq_pool,
        gemini_key_pool=gemini_pool,
    )
    result = orch.chat_completion("test prompt")
    assert result == "[{\"location\": \"gemini.py:1\"}]"
