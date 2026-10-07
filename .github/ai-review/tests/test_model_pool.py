import concurrent.futures
import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import model_pool


def test_model_pool_isolation_and_order():
    pool1 = model_pool.ModelPool(["model-a", "model-b", "model-c"])
    pool2 = model_pool.ModelPool(["model-x", "model-y"])

    pool1.demote("model-a")
    assert pool1.get_candidates() == ["model-b", "model-c", "model-a"]

    pool1.promote("model-c")
    assert pool1.get_candidates() == ["model-c", "model-b", "model-a"]

    assert pool2.get_candidates() == ["model-x", "model-y"]


def test_default_gemini_models_tiered_order():
    models = model_pool.DEFAULT_GEMINI_MODELS
    assert models[0] == "gemini-3.8-flash"
    assert "gemini-3.8-flash" in models
    assert "gemini-flash-latest" in models
    assert "gemini-3.7-flash" in models
    assert "gemini-3.6-flash" in models
    assert "gemini-3-flash-preview" in models
    assert "gemini-flash-lite-latest" in models
    assert "gemini-3.5-flash-lite" in models
    assert "gemini-3.1-flash-lite" in models
    assert "gemini-2.0-flash" not in models
    assert "gemini-1.5-flash" not in models
    assert "gemini-2.5-flash" not in models
    # 高額度 Lite 模型排在主要 Flash 模型之後做為防線
    idx_38 = models.index("gemini-3.8-flash")
    idx_37 = models.index("gemini-3.7-flash")
    idx_35_lite = models.index("gemini-3.5-flash-lite")
    assert idx_38 < idx_37 < idx_35_lite


def test_default_groq_models_priority_order():
    models = model_pool.DEFAULT_MODEL_CANDIDATES
    assert models == [
        "groq/compound-mini",
        "groq/compound",
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
        "qwen/qwen3.6-27b",
    ]
    assert model_pool.get_groq_candidate_models() == models


def test_groq_models_env_override(monkeypatch):
    monkeypatch.setenv("GROQ_MODELS", "custom-model-1, custom-model-2")
    assert model_pool.GLOBAL_MODEL_POOL_GROQ.get_candidates() == [
        "custom-model-1",
        "custom-model-2",
    ]


def test_model_pool_reset():
    pool = model_pool.ModelPool(["m1", "m2", "m3"])
    pool.demote("m1")
    assert pool.get_candidates() == ["m2", "m3", "m1"]
    pool.reset()
    assert pool.get_candidates() == ["m1", "m2", "m3"]


def test_model_pool_thread_safety():
    pool = model_pool.ModelPool(["m1", "m2", "m3", "m4", "m5"])

    def _worker(worker_idx: int) -> None:
        if worker_idx % 2 == 0:
            pool.demote("m1")
        else:
            pool.promote("m5")

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(_worker, idx) for idx in range(40)]
        for future in futures:
            future.result()

    candidates = pool.get_candidates()
    assert len(candidates) == 5
    assert set(candidates) == {"m1", "m2", "m3", "m4", "m5"}


def test_model_spec_registry_and_capacity():
    spec_38 = model_pool.get_model_spec("gemini-3.8-flash")
    assert spec_38.max_batch_tokens == 24000
    assert spec_38.max_files == 6
    assert spec_38.max_output_tokens == 4096

    spec_lite = model_pool.get_model_spec("gemini-3.5-flash-lite")
    assert spec_lite.max_batch_tokens == 8000
    assert spec_lite.max_files == 3

    spec_groq = model_pool.get_model_spec("llama-3.3-70b-versatile")
    assert spec_groq.max_batch_tokens == 5000
    assert spec_groq.max_files == 3
    assert spec_groq.max_output_tokens == 1000

    spec_unknown = model_pool.get_model_spec("unknown-custom-model")
    assert spec_unknown.max_batch_tokens == model_pool.DEFAULT_MODEL_SPEC.max_batch_tokens
    assert spec_unknown.max_files == model_pool.DEFAULT_MODEL_SPEC.max_files


def test_model_spec_env_override(monkeypatch):
    monkeypatch.setenv("AI_REVIEW_MAX_BATCH_TOKENS", "9999")
    monkeypatch.setenv("AI_REVIEW_MAX_BATCH_FILES", "2")
    spec = model_pool.get_model_spec("gemini-3.8-flash")
    assert spec.max_batch_tokens == 9999
    assert spec.max_files == 2


def test_filter_eligible_models_by_tokens():
    candidates = ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash"]
    eligible = model_pool.filter_eligible_models(candidates, 15000)
    assert eligible == ["gemini-3.8-flash", "gemini-3.6-flash"]
    assert "gemini-3.5-flash-lite" not in eligible
