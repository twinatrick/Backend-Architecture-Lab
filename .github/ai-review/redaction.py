import os
import re
from typing import Any

from key_pool import get_gemini_api_keys, get_groq_api_keys

GH_TOKEN = os.environ.get("GH_TOKEN", "")

# 預先編譯敏感特徵的正則表達式（單一真源）
PRIVATE_KEY_PATTERN = re.compile(
    r"-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----[\s\S]*?-----END (?:[A-Z ]+)?PRIVATE KEY-----"
)
CERTIFICATE_PATTERN = re.compile(
    r"-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----"
)
GROQ_KEY_PATTERN = re.compile(r"\bgsk_[0-9A-Za-z_]{20,}\b")
GEMINI_KEY_PATTERN = re.compile(r"\bAIza[0-9A-Za-z\-_]{30,}\b")
GITHUB_TOKEN_PATTERN = re.compile(r"\bgh[pousr]_[0-9A-Za-z]{20,}\b")
GITHUB_PAT_PATTERN = re.compile(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b")
AWS_KEY_PATTERN = re.compile(r"\b(?:AKIA|ASIA|AROA|AIDA)[0-9A-Z]{16}\b")
SLACK_TOKEN_PATTERN = re.compile(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b")
JWT_PATTERN = re.compile(
    r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"
)
CONN_STRING_PATTERN = re.compile(
    r"(?i)((?:https?|jdbc:[a-zA-Z0-9_\-]+|postgresql|postgres|mysql|oracle|"
    r"redis|mongodb|amqp|grpc)://[^:\s/@]*):([^@\s/]+)@"
)
URL_PARAM_PATTERN = re.compile(
    r"(?i)([?&](?:token|key|secret|password|auth|access_token|api_key|credential)=)([^&\s]+)"
)
YAML_SECRET_PATTERN = re.compile(
    r"(?im)(^\s*[a-zA-Z0-9_.-]*(?:password|secret|token|credential|"
    r"api[_-]?key|auth|private[_-]?key)[a-zA-Z0-9_.-]*\s*:\s+)([^\"'\r\n#\s()]{3,})"
    r"(\s*(?:#.*)?)$"
)
PROP_ENV_SECRET_PATTERN = re.compile(
    r"(?im)(^\s*(?:[a-zA-Z0-9_]+\.[a-zA-Z0-9_.-]*|[A-Z0-9_]{3,})"
    r"(?:password|secret|token|credential|api[_-]?key|auth|private[_-]?key)"
    r"[a-zA-Z0-9_.-]*\s*=\s*)([^\"'\r\n#\s()]{3,})(\s*(?:#.*)?)$"
)
QUOTED_SECRET_PATTERN = re.compile(
    r"(?i)([\"']?(?:api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token|"
    r"password|client[_-]?secret|private[_-]?key|bearer)[\"']?\s*[:=]\s*)([\"'])"
    r"([^\"'\r\n]{3,})"
    r"(\2)"
)

# 供靜態檢測使用之標準機密特徵清單
STATIC_SECRET_REGEXES = (
    re.compile(r"-----BEGIN (?:[A-Z ]+)?PRIVATE KEY-----"),
    GROQ_KEY_PATTERN,
    GEMINI_KEY_PATTERN,
    GITHUB_TOKEN_PATTERN,
    GITHUB_PAT_PATTERN,
    AWS_KEY_PATTERN,
    SLACK_TOKEN_PATTERN,
    QUOTED_SECRET_PATTERN,
)


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
            if len(str_val) >= 6:
                known_secrets.add(str_val)

    return known_secrets


def sanitize_diff(diff: str) -> str:
    """對 Diff 或文字中的金鑰、密鑰、憑證與敏感特徵進行嚴格脫敏遮蔽。"""
    if not diff or not isinstance(diff, str):
        return ""
    sanitized = diff

    # 1. 遮蔽 Private Key 與 Certificate 區塊
    sanitized = PRIVATE_KEY_PATTERN.sub("[REDACTED_PRIVATE_KEY]", sanitized)
    sanitized = CERTIFICATE_PATTERN.sub("[REDACTED_CERTIFICATE]", sanitized)

    # 2. 遮蔽所有已知環境變數中的真實 Secret 值（長度降序排列以避免子字串替換干擾）
    known_secrets = _get_all_known_secrets()
    for secret in sorted(known_secrets, key=len, reverse=True):
        sanitized = sanitized.replace(secret, "[REDACTED]")

    # 3. 遮蔽各類標準 Token Regex（Groq, Gemini, GitHub, AWS, Slack, JWT）
    sanitized = GROQ_KEY_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = GEMINI_KEY_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = GITHUB_TOKEN_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = GITHUB_PAT_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = AWS_KEY_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = SLACK_TOKEN_PATTERN.sub("[REDACTED]", sanitized)
    sanitized = JWT_PATTERN.sub("[REDACTED_JWT]", sanitized)

    # 4. 遮蔽各類連線字串 (HTTP/JDBC/Redis/Mongo/AMQP/gRPC) 與 URL Query Parameter 中的帳密/Token
    sanitized = CONN_STRING_PATTERN.sub(r"\1:[REDACTED]@", sanitized)
    sanitized = URL_PARAM_PATTERN.sub(r"\1[REDACTED]", sanitized)

    # 5. 遮蔽程式碼與設定檔 (YAML/Properties/.env) 中的各類敏感鍵值
    sanitized = YAML_SECRET_PATTERN.sub(r"\1[REDACTED]\3", sanitized)
    sanitized = PROP_ENV_SECRET_PATTERN.sub(r"\1[REDACTED]\3", sanitized)
    sanitized = QUOTED_SECRET_PATTERN.sub(r"\1\2[REDACTED]\4", sanitized)

    return sanitized


def redact_secrets(text: str) -> str:
    """對文字中的敏感金鑰進行脫敏遮蔽（統一代理至 sanitize_diff）。"""
    return sanitize_diff(text)


def safe_print(*args: Any, **kwargs: Any) -> None:
    """安全的終端輸出，自動對所有傳入參數進行敏感資訊脫敏。"""
    redacted_args = [redact_secrets(str(arg)) for arg in args]
    print(*redacted_args, **kwargs)


