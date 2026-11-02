"""唯讀檢查本地快照完整性，不連線、不修復、不認證來源。"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


PROFILES = {"backend", "frontend"}
SHARED = {
    "standards/common.md",
    "governance/adoption.md",
    "LICENSES.md",
    "sources.json",
}


def required_paths(profile: str) -> set[str]:
    """完整快照不能只保留摘要或自行刪掉來源全文。"""
    if profile not in PROFILES:
        raise ValueError(f"未知 Profile：{profile}")
    return SHARED | {
        f"standards/{profile}.md",
        f"standards/{profile}/master-rules.md",
        f"standards/{profile}/master-agents.md",
        f"standards/{profile}/master-review.md",
    }


def confined_path(root: Path, relative: str) -> Path:
    """來源清單不得藉絕對路徑、反斜線或符號連結讀取範圍外檔案。"""
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise ValueError("檔案路徑必須是非空 POSIX 相對路徑")
    candidate = Path(relative)
    if candidate.is_absolute() or ":" in relative or ".." in candidate.parts:
        raise ValueError(f"不安全的相對路徑：{relative}")
    resolved = (root / candidate).resolve()
    if not resolved.is_relative_to(root.resolve()):
        raise ValueError(f"檔案超出快照範圍：{relative}")
    return resolved


def read_document(path: Path) -> dict:
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict) or document.get("schema_version") != 1:
        raise ValueError("不支援的清單結構或版本")
    return document


def check_files(root: Path, entries: list[dict], expected: set[str]) -> list[str]:
    errors = []
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("檔案項目必須是物件")
        relative = entry.get("path")
        target = confined_path(root, relative)
        if relative in seen:
            errors.append(f"重複路徑：{relative}")
        seen.add(relative)
        digest = entry.get("sha256", "")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
            errors.append(f"無效 SHA-256：{relative}")
        elif not target.is_file():
            errors.append(f"缺少檔案：{relative}")
        elif hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            errors.append(f"內容不符：{relative}")
    if seen != expected:
        errors.append(f"快照檔案集合不完整：缺少 {sorted(expected - seen)}；多出 {sorted(seen - expected)}")
    return errors


def validate_source(
    document: dict, require_published: bool, is_manifest: bool = True
) -> list[str]:
    errors = []
    source = document.get("source", {})
    if not isinstance(source, dict):
        return ["來源欄位必須是物件"]
    if source.get("repository") != "https://github.com/twinatrick/engineering-standards":
        errors.append("來源儲存庫不符")
    version = source.get("version")
    if not isinstance(version, str) or not version:
        errors.append("缺少版本")
        version = ""
    status = source.get("status")
    if status not in {"draft", "published"}:
        errors.append("未知發布狀態")
    if require_published or (is_manifest and status == "published"):
        revision = source.get("commit")
        if not isinstance(revision, str) or not re.fullmatch(r"[0-9a-f]{40}", revision):
            errors.append("正式採用需要真實的 40 位 commit SHA")
        if status != "published" or "draft" in version:
            errors.append("目前仍為未發布草案")
        if document.get("release_gates") != {
            "license_review": "approved",
            "conflicts": "resolved",
            "public_redaction": "approved",
        }:
            errors.append("發布授權、衝突或脫敏審查未完成")
    elif not is_manifest and status == "published":
        if "draft" in version:
            errors.append("已發布狀態不應包含 draft 版本字串")
        if document.get("release_gates") != {
            "license_review": "approved",
            "conflicts": "resolved",
            "public_redaction": "approved",
        }:
            errors.append("發布授權、衝突或脫敏審查未完成")
    return errors


def verify_manifest(path: Path, require_published: bool = False) -> list[str]:
    document = read_document(path)
    profile = document.get("profile")
    expected = required_paths(profile)
    errors = validate_source(document, require_published, is_manifest=True)
    entries = document.get("files")
    if not isinstance(entries, list):
        raise ValueError("缺少檔案清單")
    errors.extend(check_files(path.parent, entries, expected))
    for entry in entries:
        if entry.get("source_path") != entry.get("path"):
            errors.append(f"來源路徑與快照路徑不符：{entry.get('path')}")
    checker_digest = document.get("checker_sha256")
    if (
        document.get("checker_source_path") != "scripts/check_snapshot.py"
        or checker_digest != hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    ):
        errors.append("檢查器與清單記錄的來源不符")
    for folder in ("standards", "governance"):
        directory = path.parent / folder
        if directory.is_dir():
            extras = {
                item.relative_to(path.parent).as_posix()
                for item in directory.rglob("*")
                if item.is_file()
            } - expected
            if extras:
                errors.append(f"未列管快照檔案：{sorted(extras)}")
    return errors


def verify_catalog(path: Path) -> list[str]:
    document = read_document(path)
    entries = document.get("files")
    if not isinstance(entries, list):
        raise ValueError("缺少檔案清單")
    expected = set().union(*(required_paths(profile) for profile in sorted(PROFILES)))
    return (
        validate_source(document, False, is_manifest=False)
        + check_files(path.parent, entries, expected)
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--manifest", type=Path, default=Path("docs/engineering-standards/manifest.json")
    )
    parser.add_argument("--catalog", type=Path)
    parser.add_argument("--require-published", action="store_true")
    arguments = parser.parse_args()
    try:
        if arguments.catalog:
            if arguments.require_published:
                raise ValueError("--require-published 僅適用消費端 manifest")
            errors = verify_catalog(arguments.catalog)
        else:
            errors = verify_manifest(arguments.manifest, arguments.require_published)
    except (OSError, ValueError, TypeError, KeyError) as exception:
        print(f"檢查失敗：{exception}", file=sys.stderr)
        return 1
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print("本地清單與完整快照一致；此結果不證明已發布、授權通過或來源真實。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
