import copy
import hashlib
import logging
import re
import threading
import time
from typing import Any
from urllib.parse import urlsplit

import requests


class GitHubReadTransport:
    """程序內共用不可變快照讀取；可變 PR 中繼資料永不快取。"""

    def __init__(self, *, max_entries: int = 2048, max_bytes: int = 67108864) -> None:
        self.lock = threading.RLock()
        self.cache: dict[tuple, Any] = {}
        self.missing: dict[tuple, requests.HTTPError] = {}
        self.cache_bytes = 0
        self.max_entries = max_entries
        self.max_bytes = max_bytes
        self.blocked: dict[str, requests.HTTPError] = {}

    @staticmethod
    def _immutable(url: str, params: dict) -> bool:
        parsed = urlsplit(url)
        if (parsed.scheme != "https" or parsed.netloc != "api.github.com"
                or parsed.query or parsed.fragment):
            return False
        if re.fullmatch(r"/repos/[^/]+/[^/]+/contents/.+", parsed.path):
            return bool(re.fullmatch(r"[0-9a-fA-F]{40}", str(params.get("ref", ""))))
        return bool(re.fullmatch(
            r"/repos/[^/]+/[^/]+/git/trees/[0-9a-fA-F]{40}", parsed.path,
        ))

    @staticmethod
    def _classification(response: Any) -> tuple[str, float | None]:
        headers = response.headers
        remaining = str(headers.get("X-RateLimit-Remaining", ""))
        retry = headers.get("Retry-After")
        try:
            delay = float(retry) if retry is not None else None
        except (ValueError, TypeError):
            delay = None
        if response.status_code == 429 or (response.status_code == 403 and retry is not None):
            return "RATE_LIMIT", delay if delay is not None and delay >= 0 else None
        if response.status_code == 403 and remaining == "0":
            try:
                reset = float(headers.get("X-RateLimit-Reset", ""))
                return "RATE_LIMIT", max(1.0, reset - time.time())
            except (ValueError, TypeError):
                return "RATE_LIMIT", None
        if response.status_code == 401:
            return "AUTHENTICATION", None
        if response.status_code == 403:
            return "FORBIDDEN_UNCLASSIFIED", None
        return "HTTP_ERROR", None

    @staticmethod
    def _safe_number(value: Any) -> str:
        text = str(value)
        return text if re.fullmatch(r"[0-9]{1,12}(\.[0-9]{1,3})?", text) else "unknown"

    def get(self, url: str, headers: dict, params: dict | None = None) -> Any:
        params = params or {}
        credential = hashlib.sha256(str(headers.get("Authorization", "")).encode()).hexdigest()
        key = (credential, url, tuple(sorted((str(name), str(val))
                                            for name, val in params.items())))
        immutable = self._immutable(url, params)
        # 鎖涵蓋網路呼叫，避免多批次同時對同一快照發出重複讀取。
        with self.lock:
            if credential in self.blocked:
                cached_error = self.blocked[credential]
                raise requests.HTTPError(str(cached_error), response=cached_error.response)
            if immutable and key in self.cache:
                return copy.deepcopy(self.cache[key])
            if immutable and key in self.missing:
                cached_error = self.missing[key]
                raise requests.HTTPError(str(cached_error), response=cached_error.response)
            for attempt in range(2):
                response = requests.get(url, headers=headers, params=params, timeout=30)
                if response.status_code < 400:
                    data = response.json()
                    size = len(response.content)
                    if (immutable and len(self.cache) < self.max_entries
                            and self.cache_bytes + size <= self.max_bytes):
                        self.cache[key] = copy.deepcopy(data)
                        self.cache_bytes += size
                    return data
                category, delay = self._classification(response)
                logging.warning(
                    "GitHub GET HTTP=%s category=%s remaining=%s reset=%s retry_after=%s",
                    response.status_code, category,
                    self._safe_number(response.headers.get("X-RateLimit-Remaining")),
                    self._safe_number(response.headers.get("X-RateLimit-Reset")),
                    self._safe_number(response.headers.get("Retry-After")),
                )
                if category == "RATE_LIMIT" and attempt == 0 and delay is not None:
                    if 0 <= delay <= 5:
                        time.sleep(max(0.1, delay))
                        continue
                # 不沿用 response 的原始 URL／本文建構訊息，避免錯誤紀錄洩漏憑證。
                error = requests.HTTPError(
                    f"GitHub GET failed: HTTP {response.status_code} ({category})",
                    response=response,
                )
                if response.status_code in (401, 403, 429):
                    self.blocked[credential] = error
                if (immutable and response.status_code == 404
                        and len(self.missing) < self.max_entries):
                    self.missing[key] = error
                raise error
        raise RuntimeError("GitHub GET 未取得回應")
