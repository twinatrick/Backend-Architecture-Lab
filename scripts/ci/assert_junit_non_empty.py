#!/usr/bin/env python3
"""非空測試集守衛與發布門禁驗證工具 (Non-Empty Test Suite Guard & Release Gate)."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class JunitStats:
    """JUnit XML 統計結果."""

    file_count: int
    total_tests: int
    total_failures: int
    total_errors: int
    total_skipped: int

    @property
    def executed_tests(self) -> int:
        """實際執行的非略過測試案例數."""
        return max(0, self.total_tests - self.total_skipped)


def collect_junit_files(root_dir: Path, patterns: list[str]) -> list[Path]:
    """依萬用字元模式遞迴收集所有 JUnit XML 報告檔案."""
    matched: list[Path] = []
    seen: set[Path] = set()
    for pattern in patterns:
        for candidate in sorted(root_dir.glob(pattern)):
            resolved = candidate.resolve()
            if resolved.is_file() and resolved not in seen:
                seen.add(resolved)
                matched.append(resolved)
    return matched


def parse_single_junit_xml(xml_path: Path) -> tuple[int, int, int, int]:
    """解析單一 JUnit XML 檔案並回傳 (tests, failures, errors, skipped)."""
    tree = ElementTree.parse(xml_path)
    root_elem = tree.getroot()
    suites = (
        [root_elem] if root_elem.tag == "testsuite" else list(root_elem.findall(".//testsuite"))
    )
    tests_count = 0
    failures_count = 0
    errors_count = 0
    skipped_count = 0
    for suite in suites:
        tests_count += int(suite.attrib.get("tests", "0"))
        failures_count += int(suite.attrib.get("failures", "0"))
        errors_count += int(suite.attrib.get("errors", "0"))
        skipped_count += int(suite.attrib.get("skipped", "0"))
    return tests_count, failures_count, errors_count, skipped_count


def evaluate_junit_reports(root_dir: Path, patterns: list[str]) -> JunitStats:
    """彙整符合模式的所有 JUnit XML 測試報告數據."""
    xml_files = collect_junit_files(root_dir, patterns)
    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0
    for xml_file in xml_files:
        tests_val, failures_val, errors_val, skipped_val = parse_single_junit_xml(xml_file)
        total_tests += tests_val
        total_failures += failures_val
        total_errors += errors_val
        total_skipped += skipped_val
    return JunitStats(
        file_count=len(xml_files),
        total_tests=total_tests,
        total_failures=total_failures,
        total_errors=total_errors,
        total_skipped=total_skipped,
    )


def assert_non_empty_suite(
    suite_label: str,
    root_dir: Path,
    patterns: list[str],
) -> tuple[bool, str, JunitStats]:
    """驗證測試報告存在、實際執行數大於 0 且無失敗或錯誤."""
    try:
        stats = evaluate_junit_reports(root_dir, patterns)
    except (ElementTree.ParseError, OSError, ValueError) as exc:
        empty_stats = JunitStats(0, 0, 0, 0, 0)
        return False, f"[{suite_label}] 解析 JUnit XML 失敗: {exc}", empty_stats

    if stats.file_count == 0:
        return (
            False,
            f"[{suite_label}] 找不到任何 JUnit XML 測試報告 (patterns={patterns})",
            stats,
        )
    if stats.executed_tests <= 0:
        return (
            False,
            f"[{suite_label}] 非空測試集守衛攔截：實際執行測試數為 0 "
            f"(files={stats.file_count}, total={stats.total_tests}, "
            f"skipped={stats.total_skipped})",
            stats,
        )
    if stats.total_failures > 0 or stats.total_errors > 0:
        return (
            False,
            f"[{suite_label}] 測試報告包含失敗或錯誤案例 "
            f"(failures={stats.total_failures}, errors={stats.total_errors})",
            stats,
        )
    return (
        True,
        f"[{suite_label}] 非空測試集守衛通過 "
        f"(files={stats.file_count}, executed={stats.executed_tests}, "
        f"total={stats.total_tests}, skipped={stats.total_skipped})",
        stats,
    )


def write_not_applicable_junit(
    output_path: Path,
    suite_name: str,
    reason: str,
) -> None:
    """為經影響範圍判定為 Not Applicable 的群組寫入可稽核的略過紀錄 XML."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    suite_elem = ElementTree.Element(
        "testsuite",
        attrib={
            "name": suite_name,
            "tests": "1",
            "failures": "0",
            "errors": "0",
            "skipped": "1",
        },
    )
    case_elem = ElementTree.SubElement(
        suite_elem,
        "testcase",
        attrib={"classname": suite_name, "name": "impact_scoped_not_applicable"},
    )
    ElementTree.SubElement(
        case_elem,
        "skipped",
        attrib={"message": reason},
    )
    xml_bytes = ElementTree.tostring(suite_elem, encoding="utf-8", xml_declaration=True)
    output_path.write_bytes(xml_bytes + b"\n")


def verify_release_gate(
    impact_json_path: Path,
    expected_head_sha: str,
    python_result: str,
    java_unit_result: str,
    java_integration_result: str,
) -> tuple[bool, str]:
    """驗證映像發布或部署前的共通驗證門禁與 SHA 一致性."""
    if not impact_json_path.is_file():
        return False, f"找不到影響判定報告 ({impact_json_path})，拒絕放行發布／部署"
    try:
        payload = json.loads(impact_json_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return False, f"讀取影響判定報告失敗 ({exc})，拒絕放行發布／部署"

    recorded_sha = str(payload.get("head_sha", "")).strip()
    target_sha = expected_head_sha.strip()
    if not recorded_sha or not target_sha or recorded_sha != target_sha:
        return (
            False,
            f"影響判定 SHA ({recorded_sha}) 與當前發布／部署 SHA ({target_sha}) 不一致",
        )

    results_map = {
        "python": python_result.strip().lower(),
        "java_unit": java_unit_result.strip().lower(),
        "java_integration": java_integration_result.strip().lower(),
    }
    for group_key, actual_result in results_map.items():
        group_info = payload.get(group_key, {})
        should_run = bool(group_info.get("should_run", True))
        status_label = str(group_info.get("status", "run"))
        if should_run:
            if actual_result != "success":
                return (
                    False,
                    f"受影響的共通驗證群組 [{group_key}] 未通過 (result={actual_result})",
                )
        else:
            if status_label != "not_applicable":
                return (
                    False,
                    f"驗證群組 [{group_key}] 狀態異常 (status={status_label})",
                )
            if actual_result not in {"success", "skipped", "not_applicable"}:
                return (
                    False,
                    f"不適用群組 [{group_key}] 出現失敗或異常結果 (result={actual_result})",
                )
    return True, f"共通驗證門禁與 SHA ({target_sha}) 核對通過，允許繼續發布／部署"


def main(argv: list[str] | None = None) -> int:
    """CLI 主入口."""
    parser = argparse.ArgumentParser(description="非空測試集守衛與發布門禁檢查")
    subparsers = parser.add_subparsers(dest="command", required=True)

    check_parser = subparsers.add_parser("check-junit", help="檢查 JUnit XML 非空且全數通過")
    check_parser.add_argument("--suite-label", required=True, help="驗證群組名稱")
    check_parser.add_argument("--root-dir", default=".", help="搜尋根目錄")
    check_parser.add_argument("--pattern", action="append", required=True, help="Glob 模式")

    na_parser = subparsers.add_parser("write-na", help="產生 Not Applicable JUnit XML 報告")
    na_parser.add_argument("--output", required=True, help="輸出 XML 路徑")
    na_parser.add_argument("--suite-name", required=True, help="測試集名稱")
    na_parser.add_argument("--reason", required=True, help="略過原因")

    gate_parser = subparsers.add_parser("verify-gate", help="驗證發布／部署門禁")
    gate_parser.add_argument("--impact-json", required=True, help="影響判定 JSON 路徑")
    gate_parser.add_argument("--expected-sha", required=True, help="當前提交 SHA")
    gate_parser.add_argument("--python-result", required=True, help="Python 驗證結果")
    gate_parser.add_argument("--java-unit-result", required=True, help="Java 單元驗證結果")
    gate_parser.add_argument(
        "--java-integration-result",
        required=True,
        help="Java 整合驗證結果",
    )

    args = parser.parse_args(argv)
    if args.command == "check-junit":
        passed, message, _ = assert_non_empty_suite(
            suite_label=args.suite_label,
            root_dir=Path(args.root_dir).resolve(),
            patterns=list(args.pattern),
        )
        print(message)
        return 0 if passed else 1

    if args.command == "write-na":
        write_not_applicable_junit(
            output_path=Path(args.output),
            suite_name=args.suite_name,
            reason=args.reason,
        )
        print(f"[{args.suite_name}] 已寫入 Not Applicable 紀錄至 {args.output}: {args.reason}")
        return 0

    if args.command == "verify-gate":
        passed, message = verify_release_gate(
            impact_json_path=Path(args.impact_json),
            expected_head_sha=args.expected_sha,
            python_result=args.python_result,
            java_unit_result=args.java_unit_result,
            java_integration_result=args.java_integration_result,
        )
        print(message)
        return 0 if passed else 1

    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
