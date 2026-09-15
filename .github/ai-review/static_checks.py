import logging
from pathlib import Path
from typing import Any

from check_java import check_java_file
from check_python import check_python_file
from check_rules import (
    RULE_PYTHON_MODULE_LOC,
    RULE_SECRET_PROTECTION,
    SECRET_REGEXES,
    make_finding,
)
from check_workflow import check_workflow_file
from diff_parser import extract_changed_lines


def sanitize_source_content(raw_content: str) -> str:
    """若傳入內容為 raw patch，過濾 diff 標頭與 + 前綴以還原純程式碼供 AST/YAML 解析。"""
    if not raw_content or not any(line.startswith("@@") for line in raw_content.splitlines()):
        return raw_content or ""
    clean_lines = []
    for line in raw_content.splitlines():
        if line.startswith(("@@", "---", "+++", "-")):
            continue
        clean_lines.append(line[1:] if line.startswith("+") else line)
    return "\n".join(clean_lines)


def check_secrets(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """檢查原始碼或配置檔中是否含有硬編碼之機密金鑰。"""
    findings: list[dict[str, Any]] = []
    norm_path = path.replace("\\", "/").lower()
    is_test = any(
        test_path in norm_path
        for test_path in ("src/test/", "/tests/", "tests/", "/test_", "test_")
    )

    for idx, raw_line in enumerate(content.splitlines(), start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        for pattern in SECRET_REGEXES:
            for match in pattern.finditer(line):
                matched = match.group(0).lower()
                if is_test and any(
                    token in matched for token in ("dummy", "mock", "fake", "placeholder")
                ):
                    continue
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    RULE_SECRET_PROTECTION,
                    "程式碼中發現疑似硬編碼之機密金鑰或 Token",
                    "發現符合 API Key / Private Key 格式之敏感字串",
                    "原始碼提交至版控將造成金鑰洩漏與未授權存取",
                    "將金鑰移除並改由環境變數或 Secret Manager 注入",
                ))
                break
    return findings


def run_static_checks(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """對變更檔案清單執行確定性靜態規則檢查。"""
    all_findings: list[dict[str, Any]] = []

    for file_item in files:
        path = file_item.get("filename") or file_item.get("path") or ""
        patch = file_item.get("patch") or ""
        status = file_item.get("status") or ""
        content = file_item.get("full_content") or file_item.get("content") or patch
        if not path or status in ("removed", "deleted"):
            continue
        norm_path = path.replace("\\", "/")
        lines_count = len(content.splitlines()) if content else 0

        # 如果檔案有內容但無 patch 且狀態非新增，代表 patch 缺失無法界定變更範圍
        if content and not patch and status not in ("added", "new") and lines_count > 0:
            all_findings.append(make_finding(
                path, 1, "HIGH", "COMPLIANCE",
                RULE_PYTHON_MODULE_LOC,
                f"檔案 {path} 缺少 Diff Patch，無法精確界定變更範圍",
                "Patch unavailable for modified file in diff inspection",
                "缺少 Patch 將導致行級靜態檢查與安全審查產生盲區",
                "確保 PR 包含有效 Patch 資訊或重新觸發 CI 審查",
            ))
            continue

        changed_lines = extract_changed_lines(patch, status=status, total_lines=lines_count)
        clean_content = sanitize_source_content(content)

        if content:
            all_findings.extend(check_secrets(path, content, changed_lines=changed_lines))

        is_workflow = norm_path.startswith(".github/workflows/") and norm_path.endswith(
            (".yml", ".yaml")
        )
        if is_workflow:
            wf_code = _read_local_content(path, file_item, clean_content)
            if wf_code:
                all_findings.extend(
                    check_workflow_file(path, wf_code, changed_lines=changed_lines)
                )
        elif norm_path.endswith(".java") and clean_content:
            all_findings.extend(
                check_java_file(path, clean_content, changed_lines=changed_lines)
            )
        elif norm_path.endswith(".py"):
            py_code = _read_local_content(path, file_item, clean_content)
            if py_code:
                all_findings.extend(
                    check_python_file(path, py_code, changed_lines=changed_lines)
                )

    return all_findings


def _read_local_content(path: str, item: dict[str, Any], fallback: str) -> str:
    if not item.get("full_content") and Path(path).exists():
        try:
            return Path(path).read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            logging.warning("無法讀取檔案 %s: %s", path, exc)
    return fallback
