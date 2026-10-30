"""由受信任檢出內容計算查證版本；不讀取或執行 PR 程式。"""

from functools import lru_cache
import hashlib
from pathlib import Path


@lru_cache(maxsize=1)
def trusted_rule_version() -> str:
    """規則、查證與政策程式任一改變，都使舊主張裁定失效。"""
    directory = Path(__file__).resolve().parent
    paths = sorted(directory.glob("*.py")) + [
        directory / "policy.json", directory / "requirements.txt",
        directory / "approved_reviewers.json",
        directory.parents[1] / "開發規範.md",
    ]
    if not paths or not (directory / "rule_inspectors.py").is_file():
        raise RuntimeError("受信任規則版本來源不完整。")
    digest = hashlib.sha256()
    for path in paths:
        content = path.read_bytes()
        name = path.name.encode("utf-8")
        digest.update(len(name).to_bytes(8, "big"))
        digest.update(name)
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()
