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
    assert models[0] == "gemini-3.7-flash"
    assert "gemini-3.6-flash" in models
    assert "gemini-3.5-flash-lite" in models
    assert "gemini-3.1-flash-lite" in models
    assert "gemini-2.0-flash" not in models
    assert "gemini-1.5-flash" not in models
    # 高額度 Lite 模型排在主要 Flash 模型之後做為防線
    idx_37 = models.index("gemini-3.7-flash")
    idx_35_lite = models.index("gemini-3.5-flash-lite")
    assert idx_37 < idx_35_lite


def test_default_groq_models_priority_order():
    models = model_pool.DEFAULT_MODEL_CANDIDATES
    assert models == [
        "openai/gpt-oss-120b",
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
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

