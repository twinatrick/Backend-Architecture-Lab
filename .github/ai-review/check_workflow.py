import re
from typing import Any

from check_rules import (
    RULE_CI_ACTION_PINNING,
    RULE_CI_EXPRESSION_INJECTION,
    RULE_CI_LEAST_PRIVILEGE,
    RULE_CI_TRUST_BOUNDARY,
    make_finding,
)


def check_workflow_file(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
) -> list[dict[str, Any]]:
    """針對 GitHub Actions Workflow 檔案進行權限、觸發與安全表達式檢查。"""
    findings: list[dict[str, Any]] = []
    lines = content.splitlines()

    for idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        if "pull_request_target" in line:
            has_checkout = "actions/checkout" in content
            has_dynamic_ref = re.search(
                r"ref:\s*['\"]?\$\{\{\s*github\.event\.pull_request\.head",
                content,
            )
            if has_checkout and has_dynamic_ref:
                findings.append(make_finding(
                    path, idx, "HIGH", "SECURITY",
                    RULE_CI_TRUST_BOUNDARY,
                    "pull_request_target 搭配檢出不信任 PR 程式碼存在 RCE 風險",
                    raw_line.strip(),
                    "攻擊者可透過 PR 注入惡意程式碼並讀取 Repository Secrets",
                    "改用 workflow_run 機制或移除動態 untrusted ref checkout",
                ))
        if re.search(r"\$\{\{\s*github\.event\.(?:issue|pull_request|comment)\.", line):
            findings.append(make_finding(
                path, idx, "HIGH", "SECURITY",
                RULE_CI_EXPRESSION_INJECTION,
                "在腳本中直接內嵌 github.event 上下文表達式",
                raw_line.strip(),
                "攻擊者可構造特殊 PR 標題或內容進行 Bash 命令注入",
                "將 github.event 參數映射至 env 變數後於腳本使用",
            ))
        uses_match = re.search(r"uses:\s+([\w\-\.\/]+)@([^\s#]+)", line)
        if uses_match:
            action_name, ref_sha = uses_match.groups()
            if not action_name.startswith("./") and not action_name.startswith("docker://"):
                if not re.fullmatch(r"[0-9a-fA-F]{40}", ref_sha):
                    findings.append(make_finding(
                        path, idx, "HIGH", "SECURITY",
                        RULE_CI_ACTION_PINNING,
                        "Action 未鎖定 40 位元 Commit SHA",
                        raw_line.strip(),
                        "可變版本標籤或分支可能遭遇供應鏈投毒攻擊",
                        "將 Action 鎖定為 40 位元 commit SHA (如 @11bd719... # v4.2.2)",
                    ))
        if "permissions: write-all" in line or re.search(r"permissions:\s*write-all", line):
            findings.append(make_finding(
                path, idx, "MEDIUM", "SECURITY",
                RULE_CI_LEAST_PRIVILEGE,
                "Workflow 宣告 permissions: write-all",
                raw_line.strip(),
                "授予 Workflow 過多非必要權限，增加 Token 洩漏風險",
                "依據 Job 實際需求宣告最小必要權限",
            ))

    return findings
