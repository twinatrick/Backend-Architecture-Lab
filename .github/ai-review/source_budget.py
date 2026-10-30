from dataclasses import dataclass, field
import threading
import time
from typing import Any, Callable


@dataclass
class SourceReadBudget:
    """全場固定版本原始碼預算；與傳給模型的上下文完全獨立。"""

    max_file_bytes: int = 2 * 1024 * 1024
    max_total_bytes: int = 32 * 1024 * 1024
    deadline: float = field(default_factory=lambda: time.monotonic() + 900)
    bytes_used: int = 0
    contents: dict[tuple[str, str, str], str] = field(default_factory=dict)
    unavailable: set[tuple[str, str, str]] = field(default_factory=set)
    parsed: dict[tuple[Any, ...], Any] = field(default_factory=dict)
    _lock: Any = field(default_factory=threading.RLock, repr=False)

    def can_read(self) -> bool:
        return time.monotonic() < self.deadline and self.bytes_used < self.max_total_bytes

    def read(self, repo: str, sha: str, path: str, loader: Callable[[], str | None]) -> str:
        identity = (repo, sha, path)
        # 同一版本只取一次；序列化首次讀取，避免並行批次同時越過總額。
        with self._lock:
            if identity in self.contents:
                return self.contents[identity]
            if identity in self.unavailable or not self.can_read():
                return ""
            content = loader()
            if not isinstance(content, str) or not content.strip():
                self.unavailable.add(identity)
                return ""
            size = len(content.encode("utf-8"))
            if (time.monotonic() >= self.deadline or size > self.max_file_bytes
                    or self.bytes_used + size > self.max_total_bytes):
                self.unavailable.add(identity)
                return ""
            self.bytes_used += size
            self.contents[identity] = content
            return content

    def parse_once(self, key: tuple[Any, ...], parser: Callable[[], Any]) -> Any:
        """解析結果綁定同一版本與工具名稱，不重複消耗解析工作。"""
        with self._lock:
            if key not in self.parsed:
                if time.monotonic() >= self.deadline:
                    return None
                result = parser()
                if time.monotonic() >= self.deadline:
                    return None
                self.parsed[key] = result
            return self.parsed[key]
