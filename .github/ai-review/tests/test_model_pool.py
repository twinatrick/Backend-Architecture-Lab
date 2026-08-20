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
