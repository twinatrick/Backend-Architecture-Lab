#!/usr/bin/env python3
"""本地確定性規範稽核與 Pre-commit 通行閘門 CLI 工具。

提供與 CI 靜態規則 100% 鏡像之離線、零相依規範掃描。
預設檢查 Git 暫存區（Staged Diff-Aware），支援 --all 全量掃描。
發現 HIGH 或 MEDIUM 等級違規時以 Exit Code 1 阻擋提交。
"""

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Any

AI_REVIEW_DIR = Path(__file__).resolve().parent
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import static_checks


def run_git_cmd(cmd: list[str]) -> str:
    """安全執行 Git 命令並取得輸出。"""
    try:
        res = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        return res.stdout.strip()
    except OSError:
        return ""


def get_staged_files() -> list[dict[str, Any]]:
    """自 Git 暫存區取得變更檔案清單與 Diff Patch。"""
    status_output = run_git_cmd(["git", "diff", "--cached", "--name-status"])
    if not status_output:
        return []

    file_payloads: list[dict[str, Any]] = []
    for line in status_output.splitlines():
        parts = line.strip().split(maxsplit=1)
        if len(parts) < 2:
            continue
        status_code, filename = parts[0], parts[1]
        norm_path = filename.replace("\\", "/")

        if status_code.startswith("D"):
            continue

        staged_content = run_git_cmd(["git", "show", f":{norm_path}"])
        if not staged_content:
            target_path = Path(filename)
            if target_path.exists():
                staged_content = target_path.read_text(
                    encoding="utf-8", errors="replace"
                )

        status = "added" if status_code.startswith("A") else "modified"
        patch = run_git_cmd(["git", "diff", "--cached", "-U0", "--", norm_path])

        file_payloads.append({
            "filename": norm_path,
            "status": status,
            "full_content": staged_content,
            "patch": patch,
        })

    return file_payloads


def get_all_repo_files() -> list[dict[str, Any]]:
    """取得專案全量關注檔案清單。"""
    ls_output = run_git_cmd(["git", "ls-files"])
    if not ls_output:
        return []

    file_payloads: list[dict[str, Any]] = []
    for line in ls_output.splitlines():
        filename = line.strip()
        norm_path = filename.replace("\\", "/")
        is_target = (
            norm_path.endswith((".java", ".py", ".yml", ".yaml"))
            or norm_path.endswith((".properties", ".env.example"))
        )
        if not is_target:
            continue
        target_path = Path(filename)
        if not target_path.exists() or not target_path.is_file():
            continue
        try:
            content = target_path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue

        file_payloads.append({
            "filename": norm_path,
            "status": "added",
            "full_content": content,
            "patch": "",
        })

    return file_payloads


def print_report(findings: list[dict[str, Any]]) -> tuple[int, int]:
    """格式化輸出稽核報告並回傳 (錯誤數, 警告數)。"""
    blocking_count = 0
    warning_count = 0

    print("=" * 70)
    print(" [GATE] Backend Architecture Lab 專案規範與架構邊界本地稽核")
    print("=" * 70)

    for item in findings:
        severity = item.get("severity", "MEDIUM")
        loc = item.get("location", "")
        rule = item.get("rule", "")
        problem = item.get("problem", "")
        evidence = item.get("evidence", "")
        rec = item.get("recommendation", "")

        if severity in ("HIGH", "MEDIUM"):
            blocking_count += 1
            tag = "[BLOCKING]"
        else:
            warning_count += 1
            tag = "[WARNING]"

        print(f"\n{tag} {loc} ({severity})")
        print(f"  * 規則: {rule}")
        print(f"  * 問題: {problem}")
        if evidence:
            print(f"  * 代碼: {evidence}")
        if rec:
            print(f"  * 指引: {rec}")

    print("\n" + "-" * 70)
    summary_text = (
        f"稽核結果: 發現 {blocking_count} 項阻擋違規 (HIGH/MEDIUM)，"
        f"{warning_count} 項警告 (LOW)。"
    )
    print(summary_text)

    if blocking_count > 0:
        print("[FAIL] 提交遭阻擋！請修正上述違反《開發規範.md》之程式碼後再行 commit。")
    else:
        print("[PASS] 規範稽核通過！程式碼符合專案分層與架構標準。")
    print("=" * 70 + "\n")

    return blocking_count, warning_count


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="Backend Architecture Lab 本機規範通行閘門"
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="全量掃描整個儲存庫的所有程式碼檔案",
    )
    args = parser.parse_args()

    if args.all:
        print("[INFO] 執行專案全量檔案規範稽核...")
        files = get_all_repo_files()
    else:
        print("[INFO] 執行 Git 暫存區（Staged Changes）差異稽核...")
        files = get_staged_files()
        if not files:
            print("[INFO] Git 暫存區無變更檔案，跳過規範檢查（若欲全量檢查請加 --all）。")
            return 0

    findings = static_checks.run_static_checks(files)
    blocking, _ = print_report(findings)
    return 1 if blocking > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
