import os
import sys
import time
from pathlib import Path
from unittest.mock import patch

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import key_pool


@pytest.fixture(autouse=True)
def reset_cooldowns_fixture():
    key_pool.reset_key_cooldowns()
    yield
    key_pool.reset_key_cooldowns()


def test_mask_api_key():
    assert key_pool.mask_api_key("") == ""
    assert key_pool.mask_api_key(None) == ""
    assert key_pool.mask_api_key("   ") == ""
    masked_short = key_pool.mask_api_key("12345678")
    assert masked_short.startswith("sha256:")
    assert len(masked_short) == 15
    assert "1234" not in masked_short
    assert "5678" not in masked_short

    key = "AIzaSyDummyTestKeyForUnitTestsOnly12345"
    masked = key_pool.mask_api_key(key)
    assert masked.startswith("sha256:")
    assert "AIza" not in masked


def test_get_gemini_api_keys_discovery_and_sorting():
    env_vars = {
        "GEMINI_API_KEY_2": "key_two",
        "GEMINI_API_KEY_10": "key_ten",
        "GEMINI_API_KEY": "key_default",
        "GEMINI_API_KEY_1": "key_one",
        "GEMINI_API_KEY_B": "key_beta",
        "GEMINI_API_KEY_A": "key_alpha",
        "GEMINI_API_KEY_EMPTY": "",
        "GEMINI_API_KEY_BLANK": "   ",
        "GEMINI_API_KEY_DUP": "key_one",
        "OTHER_VAR": "something_else",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        keys = key_pool.get_gemini_api_keys()
        var_names = [key_item[0] for key_item in keys]
        key_vals = [key_item[1] for key_item in keys]
        assert var_names == [
            "GEMINI_API_KEY",
            "GEMINI_API_KEY_1",
            "GEMINI_API_KEY_2",
            "GEMINI_API_KEY_10",
        ]
        assert key_vals == ["key_default", "key_one", "key_two", "key_ten"]


def test_get_groq_api_keys_discovery_and_sorting():
    env_vars = {
        "GROQ_API_KEY_3": "groq_three",
        "GROQ_API_KEY": "groq_default",
        "GROQ_API_KEY_1": "groq_one",
        "GROQ_API_KEY_XYZ": "invalid_suffix",
        "GROQ_API_KEY_DUP": "groq_default",
    }
    with patch.dict(os.environ, env_vars, clear=True):
        keys = key_pool.get_groq_api_keys()
        var_names = [key_item[0] for key_item in keys]
        key_vals = [key_item[1] for key_item in keys]
        assert var_names == [
            "GROQ_API_KEY",
            "GROQ_API_KEY_1",
            "GROQ_API_KEY_3",
        ]
        assert key_vals == ["groq_default", "groq_one", "groq_three"]


def test_key_cooldown_marking_and_expiration():
    key_pool.reset_key_cooldowns()
    test_key = "test_cooldown_key_123"

    assert key_pool.is_key_in_cooldown(test_key) is False
    assert key_pool.get_key_cooldown_remaining(test_key) == 0.0

    key_pool.mark_key_cooldown(test_key, 2.0)
    assert key_pool.is_key_in_cooldown(test_key) is True
    assert key_pool.get_key_cooldown_remaining(test_key) > 0.0

    with patch("time.time", return_value=time.time() + 3.0):
        assert key_pool.is_key_in_cooldown(test_key) is False
        assert key_pool.get_key_cooldown_remaining(test_key) == 0.0


def test_get_active_keys_filters_cooldown_keys():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    key_pool.mark_key_cooldown("val_2", 60.0)

    active = key_pool.get_active_keys(keys)
    assert len(active) == 2
    assert ("KEY_1", "val_1") in active
    assert ("KEY_3", "val_3") in active
    assert ("KEY_2", "val_2") not in active


def test_pick_random_active_key_behavior():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    key_pool.mark_key_cooldown("val_2", 60.0)
    picked = key_pool.pick_random_active_key(keys, excluded_keys={"val_1"})
    assert picked == ("KEY_3", "val_3")

    picked_none = key_pool.pick_random_active_key(keys, excluded_keys={"val_1", "val_3"})
    assert picked_none is None


def test_pick_random_active_key_distribution():
    keys = [
        ("KEY_1", "val_1"),
        ("KEY_2", "val_2"),
        ("KEY_3", "val_3"),
    ]
    picked_counts = {"val_1": 0, "val_2": 0, "val_3": 0}
    for _ in range(300):
        res = key_pool.pick_random_active_key(keys)
        assert res is not None
        picked_counts[res[1]] += 1

    assert picked_counts["val_1"] > 30
    assert picked_counts["val_2"] > 30
    assert picked_counts["val_3"] > 30


def test_key_pool_isolation():
    pool1 = key_pool.KeyPool("PREFIX1")
    pool2 = key_pool.KeyPool("PREFIX2")

    pool1.mark_cooldown("key_a", 100.0)
    assert pool1.is_in_cooldown("key_a") is True
    assert pool2.is_in_cooldown("key_a") is False

    pool1.reset_cooldowns()
    assert pool1.is_in_cooldown("key_a") is False
