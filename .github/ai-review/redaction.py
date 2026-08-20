import os
import re

from key_pool import get_gemini_api_keys, get_groq_api_keys

GH_TOKEN = os.environ.get("GH_TOKEN", "")


def get_gh_token() -> str:
    return os.environ.get("GH_TOKEN") or GH_TOKEN


def redact_secrets(text: str) -> str:
    """對文字中的敏感金鑰（Groq, Gemini, GitHub Token）以及特徵 Key 進行脫敏遮蔽。"""
    if not text or not isinstance(text, str):
        return ""
    sanitized = text
    known_secrets = set()
    for _, key_val in get_groq_api_keys() + get_gemini_api_keys():
        if key_val and len(key_val) >= 6:
            known_secrets.add(key_val)
    token = get_gh_token()
    if token and len(token) >= 6:
        known_secrets.add(token)

    for secret in sorted(known_secrets, key=len, reverse=True):
        sanitized = sanitized.replace(secret, "[REDACTED]")

    sanitized = re.sub(r"\bgsk_[0-9A-Za-z]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bAIza[0-9A-Za-z\-_]{30,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bghp_[0-9A-Za-z]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b", "[REDACTED]", sanitized)

    return sanitized

