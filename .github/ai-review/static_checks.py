import ast
import logging
import os
import re
from pathlib import Path
from typing import Any

from check_java import check_java_file
from check_rules import SECRET_REGEXES, make_finding
from diff_parser import extract_changed_lines


def check_secrets(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """檢查原始碼或配置檔中是否含有硬編碼之機密金鑰。"""
    findings: list[dict[str, Any]] = []
    norm_path = path.replace("\\", "/").lower()
    is_test = any(p in norm_path for p in ("src/test/", "/tests/", "tests/", "/test_", "test_"))

    for idx, raw_line in enumerate(content.splitlines(), start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        for pattern in SECRET_REGEXES:
            for match in pattern.finditer(line):
                matched = match.group(0).lower()
                if is_test and any(k in matched for k in ("dummy", "mock", "fake", "placeholder")):
                    continue
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    "開發規範 §2 敏感資訊與金鑰保護規範",
                    "程式碼中發現疑似硬編碼之機密金鑰或 Token",
                    "發現符合 API Key / Private Key 格式之敏感字串",
                    "原始碼提交至版控將造成金鑰洩漏與未授權存取",
                    "將金鑰移除並改由環境變數或 Secret Manager 注入",
                ))
                break
    return findings


def check_workflow_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """針對 GitHub Actions Workflow 檔案進行確定性安全檢查。"""
    findings: list[dict[str, Any]] = []
    if "pull_request_target" in content:
        has_checkout = "actions/checkout" in content
        has_dyn = re.search(r"ref:\s*['\"]?\$\{\{\s*github\.event\.pull_request\.head", content)
        if has_checkout and has_dyn:
            findings.append(make_finding(
                path, 1, "HIGH", "SECURITY",
                "開發規範 §2 CI 信任邊界防護",
                "pull_request_target 搭配檢出不信任 PR 程式碼存在 RCE 風險",
                "發現 pull_request_target 搭配動態 ref checkout",
                "攻擊者可透過 PR 注入惡意程式碼並讀取 Repository Secrets",
                "改用 workflow_run 機制或移除動態 untrusted ref checkout",
            ))

    for idx, raw_line in enumerate(content.splitlines(), start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        if re.search(r"\$\{\{\s*github\.event\.(?:issue|pull_request|comment)\.", line):
            findings.append(make_finding(
                path, idx, "HIGH", "SECURITY",
                "開發規範 §2 CI 腳本表達式注入防護",
                "在腳本中直接內嵌 github.event 上下文表達式",
                raw_line.strip(),
                "攻擊者可構造特殊 PR 標題或內容進行 Bash 命令注入",
                "將 github.event 參數映射至 env 變數後於腳本使用",
            ))
        if re.search(r"uses:\s+[\w\-\.\/]+@(main|master)\b", line):
            findings.append(make_finding(
                path, idx, "HIGH", "SECURITY",
                "開發規範 §2 CI Action 版本鎖定規範",
                "Action 使用未鎖定的浮動分支 (@main/@master)",
                raw_line.strip(),
                "浮動分支可能被上游篡改或遭遇供應鏈投毒攻擊",
                "將 Action 鎖定為具體 commit SHA 或明確版本 tag (如 @v4)",
            ))

    if "permissions: write-all" in content:
        findings.append(make_finding(
            path, 1, "MEDIUM", "SECURITY",
            "開發規範 §2 CI 最小權限原則",
            "Workflow 宣告 permissions: write-all",
            "permissions: write-all",
            "授予 Workflow 過多非必要權限，增加 Token 洩漏風險",
            "依據 Job 實際需求宣告最小必要權限",
        ))
    return findings


def check_python_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """針對 Python 原始碼進行單檔行數、單行長度、例外捕捉與 AST 靜態檢查。"""
    findings: list[dict[str, Any]] = []
    lines = content.splitlines()
    if len(lines) > 300:
        findings.append(make_finding(
            path, 1, "HIGH", "COMPLIANCE",
            "開發規範 §4.3 單一職責與單檔行數限制",
            f"Python 檔案總行數 ({len(lines)}) 超過 300 行上限",
            f"Total lines: {len(lines)}",
            "God Module 難以維護且違反 SRP 原則",
            "依職責拆分模組至獨立檔案 (< 300 行)",
        ))

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        if len(raw_line) > 100 and not raw_line.strip().startswith("#"):
            findings.append(make_finding(
                path, idx, "MEDIUM", "COMPLIANCE",
                "開發規範 §4.2 程式碼格式與行長規範",
                f"單行長度 ({len(raw_line)}) 超過 100 字元上限",
                raw_line[:90] + "...",
                "降低程式碼可讀性與審查效率",
                "適當換行拆分表達式，確保行長 <= 100 字元",
            ))
        if re.search(r"^\s*except\s*:\s*$", line) or re.search(
            r"^\s*except\s+Exception(?:\s+as\s+\w+)?:\s*$", line
        ):
            findings.append(make_finding(
                path, idx, "MEDIUM", "COMPLIANCE",
                "開發規範 §4.4 具體例外處理規範",
                "捕捉過於寬泛的泛型 Exception 或 bare except",
                raw_line.strip(),
                "隱蔽非預期系統錯誤或鍵盤中斷",
                "改為捕捉具體的例外型別 (如 RequestException, KeyError)",
            ))

    try:
        tree = ast.parse(content)
        for node in ast.walk(tree):
            lineno = getattr(node, "lineno", None)
            if lineno is None or (changed_lines is not None and lineno not in changed_lines):
                continue
            if isinstance(node, ast.ExceptHandler) and len(node.body) == 1:
                if isinstance(node.body[0], ast.Pass):
                    findings.append(make_finding(
                        path, lineno, "MEDIUM", "COMPLIANCE",
                        "開發規範 §4.4 錯誤處理與安全規範",
                        "禁止使用 except: pass 靜默吞掉錯誤",
                        "except 區塊僅包含 pass 語句",
                        "靜默忽略異常導致系統狀態不一致且難以除錯",
                        "記錄具體警告日誌 (logging.warning) 或拋出具體例外",
                    ))
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                for child in node.body:
                    if isinstance(child, (ast.Import, ast.ImportFrom)):
                        c_lineno = getattr(child, "lineno", lineno)
                        if changed_lines is None or c_lineno in changed_lines:
                            findings.append(make_finding(
                                path, c_lineno, "LOW", "COMPLIANCE",
                                "開發規範 §4.1 Import 置頂規範",
                                "禁止在函式或方法內部宣告 import",
                                "發現函式內部局部 import 宣告",
                                "降低依賴可見性並增加執行期載入開銷",
                                "將 import 宣告移至檔案最頂層",
                            ))
    except (SyntaxError, ValueError) as exc:
        logging.warning("Python AST 解析檔案 %s 失敗: %s", path, exc)

    return findings


def run_static_checks(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """對變更檔案清單執行確定性靜態規則檢查。"""
    all_findings: list[dict[str, Any]] = []

    for file_item in files:
        path = file_item.get("filename") or file_item.get("path") or ""
        patch = file_item.get("patch") or ""
        content = file_item.get("full_content") or file_item.get("content") or patch
        if not path:
            continue
        norm_path = path.replace("\\", "/")
        changed_lines = extract_changed_lines(patch)

        if content:
            all_findings.extend(check_secrets(path, content, changed_lines=changed_lines))

        if (
            norm_path.startswith(".github/workflows/")
            and (norm_path.endswith(".yml") or norm_path.endswith(".yaml"))
        ):
            if content:
                all_findings.extend(
                    check_workflow_file(path, content, changed_lines=changed_lines)
                )
        elif norm_path.endswith(".java"):
            if content:
                all_findings.extend(
                    check_java_file(path, content, changed_lines=changed_lines)
                )
        elif norm_path.endswith(".py"):
            if not file_item.get("full_content") and Path(path).exists():
                try:
                    content = Path(path).read_text(encoding="utf-8")
                except (OSError, UnicodeDecodeError) as exc:
                    logging.warning("無法讀取本地 Python 檔案 %s：%s", path, exc)
            if content:
                all_findings.extend(
                    check_python_file(path, content, changed_lines=changed_lines)
                )

    return all_findings
