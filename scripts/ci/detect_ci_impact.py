#!/usr/bin/env python3
"""雙平台共通 CI 變更影響範圍判定工具 (Impact-Scoped Verification Detector)."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

ALL_ZERO_SHA = "0" * 40
SHARED_CI_EXACT_PATHS = frozenset({".github/workflows/ci.yml", ".gitlab-ci.yml"})
SHARED_CI_PREFIXES = ("scripts/ci/",)
PYTHON_EXACT_PATHS = frozenset({"ruff.toml", "pyproject.toml", "pytest.ini", ".flake8"})
PYTHON_PREFIXES = ("backend-ai-py/",)
JAVA_EXACT_PATHS = {
    "pom.xml",
    "mvnw",
    "mvnw.cmd",
    "Dockerfile",
    "compose.yaml",
    "docker-compose.yml",
    "lombok.config",
    "sonar-project.properties",
}
JAVA_PREFIXES = (
    ".mvn/",
    "src/",
    "backend-common/",
    "backend-gateway/",
    "backend-auth-service/",
    "backend-User-service/",
    "backend-Ai-service/",
    "backend-file-service/",
    "backend-log-service/",
)
NON_IMPACT_EXACT_PATHS = {
    ".gitignore",
    ".gitattributes",
    ".editorconfig",
    ".env.example",
    "LICENSE",
    "scripts/test_portainer_ci.py",
    "scripts/check_engineering_standards.py",
}
NON_IMPACT_PREFIXES = ("docs/", ".github/ai-review/", "scripts/quality/")


@dataclass(frozen=True)
class GroupDecision:
    """單一共通驗證群組的執行決策."""

    should_run: bool
    status: str
    reason: str


@dataclass(frozen=True)
class ImpactReport:
    """變更影響範圍完整判定報告."""

    event_kind: str
    base_sha: str
    head_sha: str
    merge_base_sha: str
    fallback_full_run: bool
    fallback_reason: str
    changed_files: list[str]
    python: GroupDecision
    java_unit: GroupDecision
    java_integration: GroupDecision


def normalize_repo_path(raw_path: str) -> str:
    """將路徑正規化為 POSIX 相對路徑格式."""
    cleaned = raw_path.strip().replace("\\", "/")
    while cleaned.startswith("./"):
        cleaned = cleaned[2:]
    return cleaned


def is_null_or_missing_sha(sha_value: str) -> bool:
    """檢查 SHA 是否為空字串或全零值."""
    trimmed = sha_value.strip()
    return not trimmed or set(trimmed) == {"0"}


def parse_name_status_lines(diff_output: str) -> list[str]:
    """解析 git diff --name-status 輸出，涵蓋新增、修改、刪除與改名前後路徑."""
    collected: list[str] = []
    seen: set[str] = set()
    for raw_line in diff_output.splitlines():
        stripped_line = raw_line.strip()
        if not stripped_line:
            continue
        parts = stripped_line.split("\t")
        for candidate in parts[1:] if len(parts) > 1 else parts:
            normalized = normalize_repo_path(candidate)
            if normalized and normalized not in seen:
                seen.add(normalized)
                collected.append(normalized)
    return collected


def resolve_git_changes(
    event_kind: str, base_sha: str, head_sha: str, repo_dir: Path
) -> tuple[bool, str, str, list[str]]:
    """透過 Git 計算比較基準與變更檔案清單；若無法可靠取得則回傳保守全跑."""
    normalized_event = event_kind.strip().lower()
    if normalized_event in {"tag", "manual", "schedule", "web", "workflow_dispatch"}:
        return True, f"事件類型 ({normalized_event}) 要求強制執行全量共通驗證", "", []
    if normalized_event not in {"pull_request", "merge_request", "push"}:
        return True, f"未知的事件類型 ({normalized_event or 'empty'})，保守全跑", "", []
    if is_null_or_missing_sha(base_sha) or is_null_or_missing_sha(head_sha):
        return True, "比較基準 SHA 或當前 SHA 缺失／為全零，保守執行全量共通驗證", "", []

    comparison_base = base_sha.strip()
    resolved_head = head_sha.strip()
    if normalized_event in {"pull_request", "merge_request"}:
        mb_cmd = ["git", "-c", "safe.directory=*", "merge-base", comparison_base, resolved_head]
        mb_proc = subprocess.run(mb_cmd, cwd=str(repo_dir), capture_output=True, text=True)
        if mb_proc.returncode != 0 or not mb_proc.stdout.strip():
            return True, "無法計算目標分支 merge-base（Git 歷史不足或基準不存在），保守全跑", "", []
        comparison_base = mb_proc.stdout.strip()

    diff_cmd = ["git", "-c", "safe.directory=*", "diff", "--name-status", "-M"]
    diff_cmd.extend([comparison_base, resolved_head])
    diff_proc = subprocess.run(diff_cmd, cwd=str(repo_dir), capture_output=True, text=True)
    if diff_proc.returncode != 0:
        return True, "執行 git diff 失敗，保守執行全量共通驗證", comparison_base, []

    changed_files = parse_name_status_lines(diff_proc.stdout)
    if not changed_files:
        return True, "計算出 0 個變更檔案，保守執行全量共通驗證", comparison_base, []
    return False, "", comparison_base, changed_files


def classify_single_path(file_path: str) -> str:
    """將單一檔案分類為 shared_ci、python、java、non_impact 或 unknown."""
    normalized = normalize_repo_path(file_path)
    if normalized in SHARED_CI_EXACT_PATHS or normalized.startswith(SHARED_CI_PREFIXES):
        return "shared_ci"
    if normalized in PYTHON_EXACT_PATHS or normalized.startswith(PYTHON_PREFIXES):
        return "python"
    if normalized in JAVA_EXACT_PATHS or normalized.startswith(JAVA_PREFIXES):
        return "java"
    is_non_impact = (
        normalized.endswith(".md")
        or normalized in NON_IMPACT_EXACT_PATHS
        or normalized.startswith(NON_IMPACT_PREFIXES)
    )
    return "non_impact" if is_non_impact else "unknown"


def evaluate_impact_from_files(
    event_kind: str,
    base_sha: str,
    head_sha: str,
    merge_base_sha: str,
    changed_files: list[str],
    fallback_full_run: bool = False,
    fallback_reason: str = "",
) -> ImpactReport:
    """根據變更檔案清單或保守回退旗標產生各共通驗證群組的判定結果."""
    norm_files = [normalize_repo_path(item) for item in changed_files if normalize_repo_path(item)]
    header = (event_kind, base_sha, head_sha, merge_base_sha)
    if fallback_full_run or not norm_files:
        reason_text = fallback_reason or "無明確變更清單，保守執行全量共通驗證"
        run_all = GroupDecision(True, "run", reason_text)
        return ImpactReport(*header, True, reason_text, norm_files, run_all, run_all, run_all)

    categories: dict[str, list[str]] = {"shared_ci": [], "python": [], "java": [], "unknown": []}
    for file_path in norm_files:
        cat = classify_single_path(file_path)
        if cat in categories:
            categories[cat].append(file_path)

    if categories["shared_ci"]:
        msg = f"偵測到共通 CI 設定或腳本變更 ({categories['shared_ci'][0]})，執行全量共通驗證"
        run_all = GroupDecision(True, "run", msg)
        return ImpactReport(*header, False, "", norm_files, run_all, run_all, run_all)
    if categories["unknown"]:
        msg = f"包含未分類路徑 ({categories['unknown'][0]})，保守執行全量共通驗證"
        run_all = GroupDecision(True, "run", msg)
        return ImpactReport(*header, True, msg, norm_files, run_all, run_all, run_all)

    py_na = "變更未涉及 Python 服務、測試或依賴 (Not Applicable)"
    py_decision = (
        GroupDecision(True, "run", f"偵測到 Python 相關變更 ({categories['python'][0]})")
        if categories["python"]
        else GroupDecision(False, "not_applicable", py_na)
    )
    java_na = "變更未涉及 Java 模組、POM 或容器建置設定 (Not Applicable)"
    java_decision = (
        GroupDecision(True, "run", f"偵測到 Java 或後端建置設定變更 ({categories['java'][0]})")
        if categories["java"]
        else GroupDecision(False, "not_applicable", java_na)
    )
    return ImpactReport(*header, False, "", norm_files, py_decision, java_decision, java_decision)


def format_key_value_lines(report: ImpactReport) -> str:
    """輸出供 GitHub Actions 或 Shell 環境變數載入的 KEY=VALUE 格式."""
    lines = [
        f"IMPACT_EVENT_KIND={report.event_kind}",
        f"IMPACT_BASE_SHA={report.base_sha}",
        f"IMPACT_HEAD_SHA={report.head_sha}",
        f"IMPACT_MERGE_BASE_SHA={report.merge_base_sha}",
        f"IMPACT_FALLBACK_FULL_RUN={'true' if report.fallback_full_run else 'false'}",
        f"RUN_PYTHON={'true' if report.python.should_run else 'false'}",
        f"STATUS_PYTHON={report.python.status}",
        f"RUN_JAVA_UNIT={'true' if report.java_unit.should_run else 'false'}",
        f"STATUS_JAVA_UNIT={report.java_unit.status}",
        f"RUN_JAVA_INTEGRATION={'true' if report.java_integration.should_run else 'false'}",
        f"STATUS_JAVA_INTEGRATION={report.java_integration.status}",
    ]
    return "\n".join(lines) + "\n"


def print_summary(report: ImpactReport) -> None:
    """於終端機列印結構化影響判定摘要，供雙平台 CI 日誌檢視."""
    print("=== 共通 CI 變更影響範圍判定摘要 (Impact-Scoped Verification) ===")
    print(f"事件類型: {report.event_kind} | Base: {report.base_sha} | Head: {report.head_sha}")
    print(f"MergeBase: {report.merge_base_sha} | Fallback: {report.fallback_full_run}")
    for changed_path in report.changed_files[:30]:
        print(f"  - {changed_path}")
    print(f"  [python]           status={report.python.status} | {report.python.reason}")
    print(f"  [java_unit]        status={report.java_unit.status} | {report.java_unit.reason}")
    java_int = report.java_integration
    print(f"  [java_integration] status={java_int.status} | {java_int.reason}")


def main(argv: list[str] | None = None) -> int:
    """CLI 主入口."""
    parser = argparse.ArgumentParser(description="判定雙平台共通 CI 變更影響範圍")
    parser.add_argument("--event-kind", required=True, help="CI 觸發事件類型")
    parser.add_argument("--base-sha", default="", help="比較基準 SHA")
    parser.add_argument("--head-sha", default="", help="當前提交 SHA")
    parser.add_argument("--repo-dir", default=".", help="Git 儲存庫根目錄")
    parser.add_argument("--output-json", default="", help="輸出判定報告 JSON 檔案路徑")
    parser.add_argument("--output-env", default="", help="輸出 KEY=VALUE 環境變數檔案路徑")
    args = parser.parse_args(argv)

    fallback, reason, merge_base, files = resolve_git_changes(
        args.event_kind, args.base_sha, args.head_sha, Path(args.repo_dir).resolve()
    )
    report = evaluate_impact_from_files(
        args.event_kind, args.base_sha, args.head_sha, merge_base, files, fallback, reason
    )
    print_summary(report)
    if args.output_json:
        json_path = Path(args.output_json)
        json_path.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(asdict(report), ensure_ascii=False, indent=2) + "\n"
        json_path.write_text(payload, encoding="utf-8")
    if args.output_env:
        env_path = Path(args.output_env)
        env_path.parent.mkdir(parents=True, exist_ok=True)
        with env_path.open("a", encoding="utf-8") as env_file:
            env_file.write(format_key_value_lines(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
