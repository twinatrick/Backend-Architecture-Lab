import ast
import logging
import re
from pathlib import Path
from typing import Any

from check_java import check_java_file
from check_rules import (
    RULE_PYTHON_ERROR_HANDLING,
    RULE_PYTHON_IMPORT_TOP,
    RULE_PYTHON_LINE_LENGTH,
    RULE_PYTHON_MODULE_LOC,
    RULE_PYTHON_SINGLE_LETTER,
    RULE_PYTHON_SPECIFIC_EXCEPTION,
    RULE_PYTHON_TYPE_HINTS,
    RULE_SECRET_PROTECTION,
    SECRET_REGEXES,
    make_finding,
)
from check_workflow import check_workflow_file
from diff_parser import extract_changed_lines


def sanitize_source_content(raw_content: str) -> str:
    """若傳入內容為 raw patch，過濾 diff 標頭與 + 前綴以還原純程式碼供 AST/YAML 解析。"""
    if not raw_content:
        return ""
    lines = raw_content.splitlines()
    if any(line.startswith("@@") for line in lines):
        clean_lines = []
        for line in lines:
            if line.startswith("@@") or line.startswith("---") or line.startswith("+++"):
                continue
            if line.startswith("+"):
                clean_lines.append(line[1:])
            elif not line.startswith("-"):
                clean_lines.append(line)
        return "\n".join(clean_lines)
    return raw_content


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
            RULE_PYTHON_MODULE_LOC,
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
                RULE_PYTHON_LINE_LENGTH,
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
                RULE_PYTHON_SPECIFIC_EXCEPTION,
                "捕捉過於寬泛的泛型 Exception 或 bare except",
                raw_line.strip(),
                "隱蔽非預期系統錯誤或鍵盤中斷",
                "改為捕捉具體的例外型別 (如 RequestException, KeyError)",
            ))

    is_test_file = any(
        test_path in path.replace("\\", "/").lower()
        for test_path in ("src/test/", "/tests/", "tests/", "/test_", "test_")
    )
    try:
        tree = ast.parse(content)
        top_import_ids = {
            id(child_node) for child_node in tree.body
            if isinstance(child_node, (ast.Import, ast.ImportFrom))
        }

        for node in ast.walk(tree):
            lineno = getattr(node, "lineno", None)
            if lineno is None:
                continue

            # 1. AST: 禁止 except: pass
            if isinstance(node, ast.ExceptHandler):
                handler_lineno = getattr(node, "lineno", lineno)
                is_single_pass = len(node.body) == 1 and isinstance(node.body[0], ast.Pass)
                if (changed_lines is None or handler_lineno in changed_lines) and is_single_pass:
                    findings.append(make_finding(
                        path, handler_lineno, "MEDIUM", "COMPLIANCE",
                        RULE_PYTHON_ERROR_HANDLING,
                        "禁止使用 except: pass 靜默吞掉錯誤",
                        "except 區塊僅包含 pass 語句",
                        "靜默忽略異常導致系統狀態不一致且難以除錯",
                        "記錄具體警告日誌 (logging.warning) 或拋出具體例外",
                    ))

            # 2. AST: 巢狀 import 檢查（非 Module 頂層宣告之 import）
            elif isinstance(node, (ast.Import, ast.ImportFrom)):
                if id(node) not in top_import_ids:
                    import_lineno = getattr(node, "lineno", lineno)
                    if changed_lines is None or import_lineno in changed_lines:
                        findings.append(make_finding(
                            path, import_lineno, "LOW", "COMPLIANCE",
                            RULE_PYTHON_IMPORT_TOP,
                            "禁止在函式、類別、條件或迴圈等非頂層 scope 宣告 import",
                            "發現非模組頂層之局部 import 宣告",
                            "降低依賴可見性並增加執行期載入開銷",
                            "將 import 宣告移至檔案最頂層",
                        ))

            # 3. AST: 函式定義（參數與回傳型別標註）
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                fn_start = node.lineno
                fn_end = getattr(node, "end_lineno", fn_start)
                fn_changed = changed_lines is None or any(
                    line_idx in changed_lines for line_idx in range(fn_start, fn_end + 1)
                )
                if fn_changed and not is_test_file:
                    is_decl_changed = changed_lines is None or fn_start in changed_lines
                    # 檢查回傳值 Type Hint
                    if node.returns is None and is_decl_changed:
                        findings.append(make_finding(
                            path, fn_start, "LOW", "COMPLIANCE",
                            RULE_PYTHON_TYPE_HINTS,
                            f"函式 '{node.name}' 缺少明確的回傳型別標註",
                            f"def {node.name}(...)",
                            "缺少 Type Hints 降低程式碼可讀性與靜態檢查強度",
                            "為函式補齊回傳值 Type Hint (例如 -> None / -> str)",
                        ))
                    # 檢查參數 Type Hint
                    all_args = (
                        list(node.args.posonlyargs)
                        + list(node.args.args)
                        + list(node.args.kwonlyargs)
                    )
                    for arg_node in all_args:
                        if arg_node.arg in ("self", "cls"):
                            continue
                        arg_lineno = getattr(arg_node, "lineno", fn_start)
                        is_arg_changed = (
                            changed_lines is None
                            or arg_lineno in changed_lines
                            or is_decl_changed
                        )
                        if is_arg_changed and arg_node.annotation is None:
                            findings.append(make_finding(
                                path, arg_lineno, "LOW", "COMPLIANCE",
                                RULE_PYTHON_TYPE_HINTS,
                                f"函式 '{node.name}' 的參數 '{arg_node.arg}' 缺少型別標註",
                                f"def {node.name}(..., {arg_node.arg}, ...)",
                                "缺少參數 Type Hints 降低程式碼可讀性與靜態檢查強度",
                                f"為參數補齊 Type Hint (例如 {arg_node.arg}: str)",
                            ))

            # 4. AST: 禁止單字母變數 (除迴圈 i 與忽略變數 _ 外)
            elif isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store):
                var_name = node.id
                if len(var_name) == 1 and var_name not in ("i", "_"):
                    if changed_lines is None or lineno in changed_lines:
                        findings.append(make_finding(
                            path, lineno, "MEDIUM", "COMPLIANCE",
                            RULE_PYTHON_SINGLE_LETTER,
                            f"變數 '{var_name}' 使用單字母命名（僅允許迴圈計數器 i 或忽略變數 _）",
                            var_name,
                            "單字母變數降低程式碼可讀性並增加維護與審查成本",
                            "將變數名稱重構為具備明確業務或技術語意的 snake_case 名稱",
                        ))
            elif isinstance(node, ast.arg):
                param_name = node.arg
                if len(param_name) == 1 and param_name not in ("i", "_"):
                    if changed_lines is None or lineno in changed_lines:
                        findings.append(make_finding(
                            path, lineno, "MEDIUM", "COMPLIANCE",
                            RULE_PYTHON_SINGLE_LETTER,
                            f"參數 '{param_name}' 使用單字母命名（僅允許迴圈計數器 i）",
                            param_name,
                            "單字母參數降低函式介面可讀性與維護性",
                            "將參數名稱重構為具備明確業務或技術語意的 snake_case 名稱",
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

        if (
            norm_path.startswith(".github/workflows/")
            and (norm_path.endswith(".yml") or norm_path.endswith(".yaml"))
        ):
            if clean_content:
                all_findings.extend(
                    check_workflow_file(path, clean_content, changed_lines=changed_lines)
                )
        elif norm_path.endswith(".java"):
            if clean_content:
                all_findings.extend(
                    check_java_file(path, clean_content, changed_lines=changed_lines)
                )
        elif norm_path.endswith(".py"):
            py_code = clean_content
            if not file_item.get("full_content") and Path(path).exists():
                try:
                    py_code = Path(path).read_text(encoding="utf-8")
                except (OSError, UnicodeDecodeError) as exc:
                    logging.warning("無法讀取本地 Python 檔案 %s：%s", path, exc)
            if py_code:
                all_findings.extend(
                    check_python_file(path, py_code, changed_lines=changed_lines)
                )

    return all_findings
