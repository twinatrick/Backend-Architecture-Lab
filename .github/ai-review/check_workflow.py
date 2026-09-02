import re
from typing import Any

import yaml
from check_rules import (
    RULE_CI_ACTION_PINNING,
    RULE_CI_EXPRESSION_INJECTION,
    RULE_CI_LEAST_PRIVILEGE,
    RULE_CI_TRUST_BOUNDARY,
    make_finding,
)

INJECTION_REGEX = re.compile(
    r"\$\{\{\s*github\.(?:event\.(?:pull_request|issue|comment)|head_ref)\b"
)
HEX_SHA_REGEX = re.compile(r"^[0-9a-fA-F]{40}$")


def _find_line_number(lines: list[str], pattern: str | re.Pattern) -> int:
    for line_idx, line_text in enumerate(lines, start=1):
        if isinstance(pattern, re.Pattern) and pattern.search(line_text):
            return line_idx
        if isinstance(pattern, str) and pattern in line_text:
            return line_idx
    return 1


def _is_line_changed(lineno: int, changed_lines: set[int] | None) -> bool:
    return changed_lines is None or lineno in changed_lines


def _inspect_node(
    node: Any,
    lines: list[str],
    path: str,
    has_pr_target: bool,
    changed_lines: set[int] | None,
    findings: list[dict[str, Any]],
) -> None:
    if isinstance(node, dict):
        # 1. 檢查 permissions
        if node.get("permissions") == "write-all":
            lineno = _find_line_number(lines, re.compile(r"permissions:\s*write-all"))
            if _is_line_changed(lineno, changed_lines):
                findings.append(
                    make_finding(
                        path,
                        lineno,
                        "MEDIUM",
                        "SECURITY",
                        RULE_CI_LEAST_PRIVILEGE,
                        "Workflow 或 Job 宣告 permissions: write-all",
                        "permissions: write-all",
                        "授予 Workflow 過多非必要權限，增加 Token 洩漏與越權風險",
                        "依據實際需求宣告最小必要權限 (Least Privilege)",
                    )
                )

        # 2. 檢查 step.run 腳本注入
        run_script = node.get("run")
        if isinstance(run_script, str) and INJECTION_REGEX.search(run_script):
            match_obj = INJECTION_REGEX.search(run_script)
            matched_snippet = match_obj.group(0) if match_obj else "github.event..."
            lineno = _find_line_number(lines, matched_snippet)
            if _is_line_changed(lineno, changed_lines):
                findings.append(
                    make_finding(
                        path,
                        lineno,
                        "HIGH",
                        "SECURITY",
                        RULE_CI_EXPRESSION_INJECTION,
                        "在 run 腳本中直接內嵌上下文表達式存在命令注入風險",
                        matched_snippet,
                        "攻擊者可構造特殊 PR 標題或內容進行 Bash 命令注入",
                        "將參數映射至 step.env 變數後於腳本中使用環境變數",
                    )
                )

        # 3. 檢查 step.uses
        uses_str = str(node.get("uses", ""))
        if uses_str:
            if has_pr_target and "actions/checkout" in uses_str:
                with_data = node.get("with") or {}
                ref_val = str(with_data.get("ref", ""))
                if "github.event.pull_request.head" in ref_val or "github.head_ref" in ref_val:
                    lineno = _find_line_number(lines, "actions/checkout")
                    if _is_line_changed(lineno, changed_lines):
                        findings.append(
                            make_finding(
                                path,
                                lineno,
                                "HIGH",
                                "SECURITY",
                                RULE_CI_TRUST_BOUNDARY,
                                "pull_request_target 搭配檢出不信任 PR 程式碼存在 RCE 風險",
                                f"uses: {uses_str} with ref: {ref_val}",
                                "攻擊者可透過 PR 注入惡意程式碼並讀取 Repository Secrets",
                                "改用 workflow_run 機制或移除動態 untrusted ref checkout",
                            )
                        )

            is_external_action = not uses_str.startswith(
                ("./", "docker://")
            )
            if is_external_action and "@" in uses_str:
                action_name, action_ref = uses_str.split("@", 1)
                if not HEX_SHA_REGEX.fullmatch(action_ref.strip()):
                    lineno = _find_line_number(lines, uses_str)
                    if _is_line_changed(lineno, changed_lines):
                        findings.append(
                            make_finding(
                                path,
                                lineno,
                                "HIGH",
                                "SECURITY",
                                RULE_CI_ACTION_PINNING,
                                f"Action '{action_name}' 未鎖定 40 位元 Commit SHA",
                                f"uses: {uses_str}",
                                "可變版本標籤或分支可能遭遇供應鏈投毒攻擊",
                                f"將 Action 鎖定為 40 位元 SHA (如 {action_name}@<sha>)",
                            )
                        )

        for _key_item, val_item in node.items():
            _inspect_node(val_item, lines, path, has_pr_target, changed_lines, findings)

    elif isinstance(node, list):
        for elem_item in node:
            _inspect_node(elem_item, lines, path, has_pr_target, changed_lines, findings)


def check_workflow_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """針對 GitHub Actions Workflow 檔案進行結構化 YAML 權限、觸發與安全表達式檢查。"""
    findings: list[dict[str, Any]] = []
    lines = content.splitlines()

    data = None
    try:
        data = yaml.safe_load(content)
    except yaml.YAMLError as exc:
        is_full_workflow = any(line.strip().startswith(("jobs:", "name:")) for line in lines)
        if is_full_workflow:
            findings.append(
                make_finding(
                    path,
                    1,
                    "HIGH",
                    "COMPLIANCE",
                    RULE_CI_LEAST_PRIVILEGE,
                    f"GitHub Actions Workflow YAML 語法解析失敗: {exc}",
                    "YAML safe_load failed",
                    "語法錯誤導致 CI Workflow 無法正常執行或產生安全盲點",
                    "修正 Workflow YAML 語法格式",
                )
            )
            return findings

    has_pr_target = False
    if isinstance(data, dict):
        triggers = data.get("on") or data.get(True) or {}
        if (
            isinstance(triggers, str)
            and triggers == "pull_request_target"
            or isinstance(triggers, list)
            and "pull_request_target" in triggers
            or isinstance(triggers, dict)
            and "pull_request_target" in triggers
        ):
            has_pr_target = True
    elif "pull_request_target" in content:
        has_pr_target = True

    if data is not None and isinstance(data, (dict, list)):
        _inspect_node(data, lines, path, has_pr_target, changed_lines, findings)
    else:
        for lineno, raw_line in enumerate(lines, start=1):
            if not _is_line_changed(lineno, changed_lines):
                continue
            if "permissions: write-all" in raw_line:
                findings.append(
                    make_finding(
                        path,
                        lineno,
                        "MEDIUM",
                        "SECURITY",
                        RULE_CI_LEAST_PRIVILEGE,
                        "Workflow 或 Job 宣告 permissions: write-all",
                        raw_line.strip(),
                        "授予 Workflow 過多非必要權限，增加 Token 洩漏與越權風險",
                        "依據實際需求宣告最小必要權限 (Least Privilege)",
                    )
                )
            if INJECTION_REGEX.search(raw_line):
                findings.append(
                    make_finding(
                        path,
                        lineno,
                        "HIGH",
                        "SECURITY",
                        RULE_CI_EXPRESSION_INJECTION,
                        "在 run 腳本中直接內嵌上下文表達式存在命令注入風險",
                        raw_line.strip(),
                        "攻擊者可構造特殊 PR 標題或內容進行 Bash 命令注入",
                        "將參數映射至 step.env 變數後於腳本中使用環境變數",
                    )
                )

    return findings
