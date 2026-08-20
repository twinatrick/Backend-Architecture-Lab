import hashlib
import os
import random
import re
import time

KEY_COOLDOWN_MAP: dict[str, float] = {}
GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")


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
    """
    探索環境變數中特定 Provider 的所有 API Keys。
    支援 {PREFIX} 與 {PREFIX}_1, {PREFIX}_2 等格式。
    回傳 [(var_name, key_value), ...] 依自然排序排列，過濾空值與重複金鑰。
    """
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
    """管理 API 金鑰集與各金鑰的冷卻狀態。支援實例隔離與共享冷卻字典。"""

    def __init__(
        self,
        prefix: str = "",
        fallback_env_var: str = "",
        cooldown_map: dict[str, float] | None = None,
    ) -> None:
        self.prefix = prefix
        self.fallback_env_var = fallback_env_var
        self.cooldown_map: dict[str, float] = (
            cooldown_map if cooldown_map is not None else {}
        )

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
        self.cooldown_map.clear()

    def mark_cooldown(self, api_key: str, cooldown_seconds: float) -> None:
        if not api_key:
            return
        self.cooldown_map[api_key] = time.time() + max(1.0, float(cooldown_seconds))

    def is_in_cooldown(self, api_key: str) -> bool:
        if not api_key or api_key not in self.cooldown_map:
            return False
        if time.time() >= self.cooldown_map[api_key]:
            self.cooldown_map.pop(api_key, None)
            return False
        return True

    def get_cooldown_remaining(self, api_key: str) -> float:
        if not self.is_in_cooldown(api_key):
            return 0.0
        return max(0.0, self.cooldown_map.get(api_key, 0.0) - time.time())

    def get_active_keys(
        self, keys: list[tuple[str, str]] | None = None
    ) -> list[tuple[str, str]]:
        target_keys = keys if keys is not None else self.get_all_keys()
        return [item for item in target_keys if not self.is_in_cooldown(item[1])]

    def pick_random_active_key(
        self,
        keys: list[tuple[str, str]] | None = None,
        excluded_keys: set[str] | None = None,
    ) -> tuple[str, str] | None:
        target_keys = keys if keys is not None else self.get_all_keys()
        excluded = excluded_keys or set()
        active_pool = [
            item
            for item in target_keys
            if item[1] not in excluded and not self.is_in_cooldown(item[1])
        ]
        if not active_pool:
            return None
        return random.choice(active_pool)


GLOBAL_KEY_POOL_GROQ = KeyPool("GROQ_API_KEY", "GROQ_API_KEY", KEY_COOLDOWN_MAP)
GLOBAL_KEY_POOL_GEMINI = KeyPool("GEMINI_API_KEY", "GEMINI_API_KEY", KEY_COOLDOWN_MAP)
_DEFAULT_KEY_POOL = KeyPool(cooldown_map=KEY_COOLDOWN_MAP)


def reset_key_cooldowns() -> None:
    """清空所有金鑰的冷卻狀態（供測試與初始化使用）。"""
    _DEFAULT_KEY_POOL.reset_cooldowns()


def mark_key_cooldown(api_key: str, cooldown_seconds: float) -> None:
    """將特定金鑰標記進入冷卻清單，設定解除冷卻的時間戳記。"""
    _DEFAULT_KEY_POOL.mark_cooldown(api_key, cooldown_seconds)


def is_key_in_cooldown(api_key: str) -> bool:
    """檢查金鑰是否仍處於冷卻期。若冷卻時間已過，自動解除並回傳 False。"""
    return _DEFAULT_KEY_POOL.is_in_cooldown(api_key)


def get_key_cooldown_remaining(api_key: str) -> float:
    """取得金鑰剩餘冷卻秒數，若未處於冷卻中則回傳 0.0。"""
    return _DEFAULT_KEY_POOL.get_cooldown_remaining(api_key)


def get_active_keys(keys: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """過濾出當前未處於冷卻清單中的可用金鑰清單。"""
    return _DEFAULT_KEY_POOL.get_active_keys(keys)


def pick_random_active_key(
    keys: list[tuple[str, str]],
    excluded_keys: set[str] | None = None,
) -> tuple[str, str] | None:
    """自候選金鑰清單中，排除冷卻中與本輪已嘗試過之金鑰，隨機抽取一把。"""
    return _DEFAULT_KEY_POOL.pick_random_active_key(keys, excluded_keys=excluded_keys)
