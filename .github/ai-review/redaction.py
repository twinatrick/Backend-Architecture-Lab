import os
import re
from typing import Any

from key_pool import get_gemini_api_keys, get_groq_api_keys

GH_TOKEN = os.environ.get("GH_TOKEN", "")

# 預先編譯敏感特徵的正則表達式
TOKEN_REGEX_PATTERNS = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----"),
    re.compile(r"\bgsk_[0-9A-Za-z_]{20,}\b"),
    re.compile(r"\bAIza[0-9A-Za-z\-_]{30,}\b"),
    re.compile(r"\bgh[pousr]_[0-9A-Za-z]{20,}\b"),
    re.compile(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b"),
    re.compile(r"\b(?:AKIA|ASIA|AROA|AIDA)[0-9A-Z]{16}\b"),
    re.compile(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b"),
    re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"),
    re.compile(r"(?i)(https?://[a-zA-Z0-9_\-\.]+):([a-zA-Z0-9_\-\.+=~@]+)@"),
    re.compile(
        r"(?i)([\"']?(?:api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token|"
        r"password|client[_-]?secret|private[_-]?key|bearer)[\"']?\s*[:=]\s*[\"']?)"
        r"([a-zA-Z0-9_\-\.\/+=~]{8,})"
        r"([\"']?)"
    ),
]


def get_gh_token() -> str:
    return os.environ.get("GH_TOKEN") or GH_TOKEN


def _get_all_known_secrets() -> set[str]:
    known_secrets: set[str] = set()
    for _, key_val in get_groq_api_keys() + get_gemini_api_keys():
        if key_val and len(key_val) >= 6:
            known_secrets.add(key_val)
    token = get_gh_token()
    if token and len(token) >= 6:
        known_secrets.add(token)

    for env_name, env_val in os.environ.items():
        if any(
            token_keyword in env_name.upper()
            for token_keyword in ("KEY", "SECRET", "TOKEN", "PASSWORD", "AUTH", "CREDENTIAL")
        ):
            str_val = str(env_val).strip()
            if len(str_val) >= 8:
                known_secrets.add(str_val)

    return known_secrets


def sanitize_diff(diff: str) -> str:
    """對 Diff 或文字中的金鑰、密鑰、憑證與敏感特徵進行嚴格脫敏遮蔽。"""
    if not diff or not isinstance(diff, str):
        return ""
    sanitized = diff

    # 1. 遮蔽 Private Key 與 Certificate 區塊
    sanitized = re.sub(
        r"-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----",
        "[REDACTED_PRIVATE_KEY]",
        sanitized,
    )
    sanitized = re.sub(
        r"-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----",
        "[REDACTED_CERTIFICATE]",
        sanitized,
    )

    # 2. 遮蔽所有已知環境變數中的真實 Secret 值（長度降序排列以避免子字串替換干擾）
    known_secrets = _get_all_known_secrets()
    for secret in sorted(known_secrets, key=len, reverse=True):
        sanitized = sanitized.replace(secret, "[REDACTED]")

    # 3. 遮蔽各類標準 Token Regex（Groq, Gemini, GitHub, AWS, Slack, JWT）
    sanitized = re.sub(r"\bgsk_[0-9A-Za-z_]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bAIza[0-9A-Za-z\-_]{30,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bgh[pousr]_[0-9A-Za-z]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\b(?:AKIA|ASIA|AROA|AIDA)[0-9A-Z]{16}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(
        r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b",
        "[REDACTED_JWT]",
        sanitized,
    )

    # 4. 遮蔽 URL Basic Auth 密碼
    sanitized = re.sub(
        r"(?i)(https?://[a-zA-Z0-9_\-\.]+):([a-zA-Z0-9_\-\.+=~@]+)@",
        r"\1:[REDACTED]@",
        sanitized,
    )

    # 5. 遮蔽程式碼/設定檔中的 key-value 密鑰指派
    assignment_pattern = re.compile(
        r"(?i)([\"']?(?:api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token|"
        r"password|client[_-]?secret|private[_-]?key|bearer)[\"']?\s*[:=]\s*[\"']?)"
        r"([a-zA-Z0-9_\-\.\/+=~]{8,})"
        r"([\"']?)"
    )
    sanitized = assignment_pattern.sub(r"\1[REDACTED]\3", sanitized)

    return sanitized


def redact_secrets(text: str) -> str:
    """對文字中的敏感金鑰進行脫敏遮蔽（統一代理至 sanitize_diff）。"""
    return sanitize_diff(text)


def safe_print(*args: Any, **kwargs: Any) -> None:
    """安全的終端輸出，自動對所有傳入參數進行敏感資訊脫敏。"""
    redacted_args = [redact_secrets(str(arg)) for arg in args]
    print(*redacted_args, **kwargs)


