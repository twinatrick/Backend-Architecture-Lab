import hashlib
import os
import random
import re
import threading
import time
from collections.abc import Generator
from contextlib import contextmanager

KEY_COOLDOWN_MAP: dict[str, float] = {}
GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")


def _make_cooldown_key(api_key: str, model: str | None = None) -> str:
    """構建模型感知冷卻鍵。若未指定模型則回傳純金鑰字串。"""
    if model and str(model).strip():
        return f"{str(model).strip()}::{api_key}"
    return api_key


def mask_api_key(key: str) -> str:
    """產出 API Key 的遮蔽摘要，避免明文日誌洩漏。"""
    if not key or not isinstance(key, str):
        return ""
    stripped = key.strip()
    if not stripped:
        return ""
    digest = hashlib.sha256(stripped.encode("utf-8")).hexdigest()[:8]
    return f"sha256:{digest}"


def get_provider_api_keys(prefix: str) -> list[tuple[str, str]]:
    """探索環境變數中特定 Provider 的所有 API Keys。"""
    raw_keys = {}
    pattern = re.compile(rf"^{prefix}(?:_\d+)?$", re.IGNORECASE)
    for var_name, var_val in os.environ.items():
        if pattern.match(var_name) and var_val and str(var_val).strip():
            raw_keys[var_name] = str(var_val).strip()

    def sort_key(item: tuple[str, str]) -> tuple[int, int, str]:
        name = item[0].upper()
        if name == prefix.upper():
            return (0, 0, name)
        match_num = re.match(rf"^{prefix}_(\d+)$", name, re.IGNORECASE)
        if match_num:
            return (1, int(match_num.group(1)), name)
        return (2, 0, name)

    sorted_items = sorted(raw_keys.items(), key=sort_key)
    seen_values = set()
    unique_keys = []
    for var_name, var_val in sorted_items:
        if var_val not in seen_values:
            seen_values.add(var_val)
            unique_keys.append((var_name, var_val))
    return unique_keys


def get_groq_api_keys() -> list[tuple[str, str]]:
    """取得所有 Groq API 金鑰清單。"""
    keys = get_provider_api_keys("GROQ_API_KEY")
    if not keys and GROQ_API_KEY and GROQ_API_KEY.strip():
        keys.append(("GROQ_API_KEY", GROQ_API_KEY.strip()))
    return keys


def get_gemini_api_keys() -> list[tuple[str, str]]:
    """取得所有 Google Gemini API 金鑰清單。"""
    return get_provider_api_keys("GEMINI_API_KEY")


def get_groq_api_key() -> str:
    """取得第一把可用的 Groq API 金鑰。"""
    keys = get_groq_api_keys()
    return keys[0][1] if keys else ""


class KeyPool:
    """管理 API 金鑰集與各金鑰的冷卻狀態。支援實例隔離、共享冷卻字典與多執行緒並發租借。"""

    def __init__(
        self,
        prefix: str = "",
        fallback_env_var: str = "",
        cooldown_map: dict[str, float] | None = None,
    ) -> None:
        self.prefix = prefix
        self.fallback_env_var = fallback_env_var
        self.cooldown_map: dict[str, float] = cooldown_map if cooldown_map is not None else {}
        self._lock = threading.RLock()
        self._in_use_keys: set[str] = set()

    def get_all_keys(self) -> list[tuple[str, str]]:
        if not self.prefix:
            return []
        keys = get_provider_api_keys(self.prefix)
        if not keys and self.fallback_env_var:
            fallback_val = os.environ.get(self.fallback_env_var, "").strip()
            if fallback_val:
                keys.append((self.fallback_env_var, fallback_val))
        return keys

    def reset_cooldowns(self) -> None:
        with self._lock:
            self.cooldown_map.clear()
            self._in_use_keys.clear()

    def mark_cooldown(
        self,
        api_key: str,
        cooldown_seconds: float,
        model: str | None = None,
    ) -> None:
        if not api_key:
            return
        c_key = _make_cooldown_key(api_key, model)
        with self._lock:
            self.cooldown_map[c_key] = time.time() + max(1.0, float(cooldown_seconds))

    def is_in_cooldown(self, api_key: str, model: str | None = None) -> bool:
        if not api_key:
            return False
        with self._lock:
            now = time.time()
            if api_key in self.cooldown_map:
                if now >= self.cooldown_map[api_key]:
                    self.cooldown_map.pop(api_key, None)
                else:
                    return True

            if model and str(model).strip():
                c_key = _make_cooldown_key(api_key, model)
                if c_key in self.cooldown_map:
                    if now >= self.cooldown_map[c_key]:
                        self.cooldown_map.pop(c_key, None)
                        return False
                    return True
            return False

    def get_cooldown_remaining(self, api_key: str, model: str | None = None) -> float:
        with self._lock:
            if not self.is_in_cooldown(api_key, model=model):
                return 0.0
            now = time.time()
            rem = max(0.0, self.cooldown_map.get(api_key, 0.0) - now)
            if model and str(model).strip():
                c_key = _make_cooldown_key(api_key, model)
                rem = max(rem, self.cooldown_map.get(c_key, 0.0) - now)
            return rem

    def get_active_keys(
        self,
        keys: list[tuple[str, str]] | None = None,
        model: str | None = None,
    ) -> list[tuple[str, str]]:
        target_keys = keys if keys is not None else self.get_all_keys()
        with self._lock:
            return [item for item in target_keys if not self.is_in_cooldown(item[1], model=model)]

    def pick_random_active_key(
        self,
        keys: list[tuple[str, str]] | None = None,
        excluded_keys: set[str] | None = None,
        model: str | None = None,
    ) -> tuple[str, str] | None:
        target_keys = keys if keys is not None else self.get_all_keys()
        excluded = excluded_keys or set()
        with self._lock:
            active_pool = [
                item
                for item in target_keys
                if item[1] not in excluded and not self.is_in_cooldown(item[1], model=model)
            ]
            if not active_pool:
                return None
            return random.choice(active_pool)

    def acquire_key(
        self,
        keys: list[tuple[str, str]] | None = None,
        excluded_keys: set[str] | None = None,
        model: str | None = None,
    ) -> tuple[str, str] | None:
        """
        在多執行緒環境中租借一把金鑰。
        優先挑選當前未被其他執行緒使用 (in-use) 且非冷卻中的金鑰；
        若皆在使用中，則退而隨機挑選非冷卻金鑰。
        """
        target_keys = keys if keys is not None else self.get_all_keys()
        excluded = excluded_keys or set()
        with self._lock:
            available_pool = [
                item
                for item in target_keys
                if item[1] not in excluded and not self.is_in_cooldown(item[1], model=model)
            ]
            if not available_pool:
                return None

            idle_pool = [item for item in available_pool if item[1] not in self._in_use_keys]
            picked = random.choice(idle_pool) if idle_pool else random.choice(available_pool)
            self._in_use_keys.add(picked[1])
            return picked

    def release_key(self, api_key: str | None) -> None:
        """歸還租借的金鑰，解除使用中標記。"""
        if not api_key:
            return
        with self._lock:
            self._in_use_keys.discard(api_key)

    def is_key_in_use(self, api_key: str) -> bool:
        """檢查指定金鑰目前是否正處於使用中狀態。"""
        with self._lock:
            return api_key in self._in_use_keys

    @contextmanager
    def lease_key(
        self,
        keys: list[tuple[str, str]] | None = None,
        excluded_keys: set[str] | None = None,
        model: str | None = None,
    ) -> Generator[tuple[str, str] | None, None, None]:
        picked = self.acquire_key(keys=keys, excluded_keys=excluded_keys, model=model)
        try:
            yield picked
        finally:
            if picked:
                self.release_key(picked[1])


GLOBAL_KEY_POOL_GROQ = KeyPool("GROQ_API_KEY", "GROQ_API_KEY", KEY_COOLDOWN_MAP)
GLOBAL_KEY_POOL_GEMINI = KeyPool("GEMINI_API_KEY", "GEMINI_API_KEY", KEY_COOLDOWN_MAP)
_DEFAULT_KEY_POOL = KeyPool(cooldown_map=KEY_COOLDOWN_MAP)


def reset_key_cooldowns() -> None:
    _DEFAULT_KEY_POOL.reset_cooldowns()


def mark_key_cooldown(api_key: str, cooldown_seconds: float, model: str | None = None) -> None:
    _DEFAULT_KEY_POOL.mark_cooldown(api_key, cooldown_seconds, model=model)


def is_key_in_cooldown(api_key: str, model: str | None = None) -> bool:
    return _DEFAULT_KEY_POOL.is_in_cooldown(api_key, model=model)


def get_key_cooldown_remaining(api_key: str, model: str | None = None) -> float:
    return _DEFAULT_KEY_POOL.get_cooldown_remaining(api_key, model=model)


def get_active_keys(keys: list[tuple[str, str]], model: str | None = None) -> list[tuple[str, str]]:
    return _DEFAULT_KEY_POOL.get_active_keys(keys, model=model)


def pick_random_active_key(
    keys: list[tuple[str, str]],
    excluded_keys: set[str] | None = None,
    model: str | None = None,
) -> tuple[str, str] | None:
    return _DEFAULT_KEY_POOL.pick_random_active_key(keys, excluded_keys=excluded_keys, model=model)


def acquire_key(
    keys: list[tuple[str, str]],
    excluded_keys: set[str] | None = None,
    model: str | None = None,
) -> tuple[str, str] | None:
    return _DEFAULT_KEY_POOL.acquire_key(keys, excluded_keys=excluded_keys, model=model)


def release_key(api_key: str | None) -> None:
    _DEFAULT_KEY_POOL.release_key(api_key)
