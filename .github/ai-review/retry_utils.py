import logging
import os
import random
import re
from typing import Any

MAX_RETRY_LIMIT = 12


def parse_retry_limit(raw_value: str | None, default: int = 3) -> int:
    if raw_value is None or not str(raw_value).strip():
        return default
    try:
        val = int(raw_value)
        if val < 1:
            return default
        return min(val, MAX_RETRY_LIMIT)
    except (TypeError, ValueError):
        logging.warning("AI_REVIEW_MAX_RETRIES 設定值 '%s' 無效，改用預設值 %s", raw_value, default)
        return default


DEFAULT_MAX_RETRIES_PER_MODEL = parse_retry_limit(os.environ.get("AI_REVIEW_MAX_RETRIES"))
DEFAULT_MAX_429_RETRIES_PER_PROVIDER = int(
    os.environ.get("AI_REVIEW_MAX_429_RETRIES_PER_PROVIDER", "4")
)


def parse_retry_after(response: Any) -> float:
    header_val = None
    if hasattr(response, "headers") and response.headers:
        header_val = response.headers.get("retry-after")
    if header_val:
        try:
            return float(header_val)
        except (ValueError, TypeError) as exc:
            logging.warning("無法解析 retry-after header '%s': %s", header_val, exc)
    try:
        err_text = response.text if hasattr(response, "text") and response.text else ""
        match_ms = re.search(
            r"(?:try again|retry) in (\d+(?:\.\d+)?)\s*ms", err_text, re.IGNORECASE
        )
        if match_ms:
            return float(match_ms.group(1)) / 1000.0
        match_s = re.search(
            r"(?:try again|retry) in (\d+(?:\.\d+)?)\s*s\b", err_text, re.IGNORECASE
        )
        if match_s:
            return float(match_s.group(1))
        match_delay = re.search(
            r'"retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s"', err_text, re.IGNORECASE
        )
        if match_delay:
            return float(match_delay.group(1))
        match_m = re.search(
            r"(?:try again|retry) in (\d+(?:\.\d+)?)\s*m\b", err_text, re.IGNORECASE
        )
        if match_m:
            return float(match_m.group(1)) * 60.0
    except (ValueError, TypeError, AttributeError) as exc:
        logging.warning("無法從錯誤訊息文字解析重試延遲時間: %s", exc)
    return 5.0


def is_invalid_api_key_error(status_code: int, resp_text: str) -> bool:
    """判定回應是否屬於無效、過期或未授權之 API 金鑰錯誤（含 HTTP 400 API_KEY_INVALID）。"""
    if status_code in (401, 403):
        return True
    if status_code == 400 and resp_text:
        lowered = resp_text.lower()
        return any(
            marker in lowered
            for marker in (
                "api_key_invalid",
                "api key not valid",
                "api key expired",
                "invalid api key",
                "invalid_api_key",
            )
        )
    return False


def is_daily_quota_exhausted(resp_text: str, retry_after: float) -> bool:
    """區分長週期每日配額耗盡 (RPD/TPD) 與短週期每分鐘速率限制 (RPM/TPM)。"""
    if retry_after > 120.0:
        return True
    if not resp_text:
        return False
    lowered = resp_text.lower()
    if "billing" in lowered and "limit: 0" in lowered:
        return True
    return any(
        marker in lowered
        for marker in (
            "perday",
            "per_day",
            "per day",
            "daily limit",
            "daily quota",
            "tpd",
            "rpd",
        )
    )


def calculate_backoff_delay(
    attempt: int,
    retry_after: float = 0.0,
    base_delay: float = 2.5,
    max_delay: float = 90.0,
    jitter_range: tuple[float, float] = (0.5, 1.5),
) -> float:
    exponential_delay = base_delay * (2 ** max(0, attempt - 1))
    effective_delay = max(retry_after, exponential_delay)
    jitter = random.uniform(jitter_range[0], jitter_range[1])
    return min(effective_delay + jitter, max_delay)

