#!/usr/bin/env python3
"""雙平台共通 CI 變更影響範圍判定與非空測試集守衛單元測試."""

from __future__ import annotations

import json
import tempfile
import unittest
from dataclasses import asdict
from pathlib import Path

from assert_junit_non_empty import (
    assert_non_empty_suite,
    verify_release_gate,
    write_not_applicable_junit,
)
from detect_ci_impact import (
    ALL_ZERO_SHA,
    evaluate_impact_from_files,
    parse_name_status_lines,
    resolve_git_changes,
)


class DetectCiImpactTest(unittest.TestCase):
    """驗證變更影響範圍判定邏輯."""

    def test_pure_docs_marks_all_shared_groups_not_applicable(self) -> None:
        report = evaluate_impact_from_files(
            event_kind="pull_request",
            base_sha="abc1234",
            head_sha="def5678",
            merge_base_sha="abc1234",
            changed_files=[
                "CONTEXT.md",
                "docs/adr/0031-shared-ci-verification-and-impact-scoped-gate.md",
                "README.md",
            ],
        )
        self.assertFalse(report.fallback_full_run)
        self.assertFalse(report.python.should_run)
        self.assertEqual(report.python.status, "not_applicable")
        self.assertFalse(report.java_unit.should_run)
        self.assertEqual(report.java_unit.status, "not_applicable")
        self.assertFalse(report.java_integration.should_run)
        self.assertEqual(report.java_integration.status, "not_applicable")

    def test_python_only_changes_run_python_and_skip_java(self) -> None:
        report = evaluate_impact_from_files(
            event_kind="merge_request",
            base_sha="abc1234",
            head_sha="def5678",
            merge_base_sha="abc1234",
            changed_files=[
                "backend-ai-py/services/diarization_worker.py",
                "backend-ai-py/requirements-ci.txt",
            ],
        )
        self.assertTrue(report.python.should_run)
        self.assertEqual(report.python.status, "run")
        self.assertFalse(report.java_unit.should_run)
        self.assertEqual(report.java_unit.status, "not_applicable")
        self.assertFalse(report.java_integration.should_run)
        self.assertEqual(report.java_integration.status, "not_applicable")

    def test_java_only_changes_run_java_unit_and_integration_only(self) -> None:
        report = evaluate_impact_from_files(
            event_kind="push",
            base_sha="abc1234",
            head_sha="def5678",
            merge_base_sha="abc1234",
            changed_files=[
                "backend-auth-service/src/main/java/com/example/AuthApp.java",
                "pom.xml",
            ],
        )
        self.assertFalse(report.python.should_run)
        self.assertEqual(report.python.status, "not_applicable")
        self.assertTrue(report.java_unit.should_run)
        self.assertEqual(report.java_unit.status, "run")
        self.assertTrue(report.java_integration.should_run)
        self.assertEqual(report.java_integration.status, "run")

    def test_shared_ci_or_workflow_changes_trigger_all_groups(self) -> None:
        for shared_file in [
            ".github/workflows/ci.yml",
            ".gitlab-ci.yml",
            "scripts/ci/verify_python.sh",
        ]:
            report = evaluate_impact_from_files(
                event_kind="pull_request",
                base_sha="abc1234",
                head_sha="def5678",
                merge_base_sha="abc1234",
                changed_files=[shared_file],
            )
            self.assertTrue(report.python.should_run, msg=shared_file)
            self.assertTrue(report.java_unit.should_run, msg=shared_file)
            self.assertTrue(report.java_integration.should_run, msg=shared_file)

    def test_unknown_file_path_conservatively_triggers_full_run(self) -> None:
        report = evaluate_impact_from_files(
            event_kind="pull_request",
            base_sha="abc1234",
            head_sha="def5678",
            merge_base_sha="abc1234",
            changed_files=["unclassified_folder/mystery.cfg"],
        )
        self.assertTrue(report.fallback_full_run)
        self.assertTrue(report.python.should_run)
        self.assertTrue(report.java_unit.should_run)
        self.assertTrue(report.java_integration.should_run)

    def test_parse_name_status_captures_deletions_and_both_rename_paths(self) -> None:
        raw_diff = (
            "D\tbackend-ai-py/services/legacy_worker.py\n"
            "R095\tbackend-common/src/Old.java\tdocs/Old.md\n"
            "M\tREADME.md\n"
        )
        parsed_files = parse_name_status_lines(raw_diff)
        self.assertEqual(
            parsed_files,
            [
                "backend-ai-py/services/legacy_worker.py",
                "backend-common/src/Old.java",
                "docs/Old.md",
                "README.md",
            ],
        )
        report = evaluate_impact_from_files(
            event_kind="pull_request",
            base_sha="abc",
            head_sha="def",
            merge_base_sha="abc",
            changed_files=parsed_files,
        )
        self.assertTrue(report.python.should_run)
        self.assertTrue(report.java_unit.should_run)
        self.assertTrue(report.java_integration.should_run)

    def test_missing_or_zero_sha_and_tag_or_manual_fallback_to_full_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir_name:
            tmp_dir = Path(tmp_dir_name)
            for event_name, base_val, head_val in [
                ("tag", "abc1234", "def5678"),
                ("manual", "abc1234", "def5678"),
                ("push", ALL_ZERO_SHA, "def5678"),
                ("pull_request", "", "def5678"),
                ("pull_request", "non_existent_sha_1", "non_existent_sha_2"),
            ]:
                fallback, reason, _, _ = resolve_git_changes(
                    event_kind=event_name,
                    base_sha=base_val,
                    head_sha=head_val,
                    repo_dir=tmp_dir,
                )
                self.assertTrue(fallback, msg=f"{event_name}:{reason}")
                self.assertTrue(bool(reason))


class NonEmptySuiteAndGateTest(unittest.TestCase):
    """驗證非空測試集守衛與發布門禁檢查."""

    def test_non_empty_suite_guard_accepts_valid_and_rejects_empty_or_failed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir_name:
            root_path = Path(tmp_dir_name)
            valid_xml = root_path / "reports" / "TEST-valid.xml"
            valid_xml.parent.mkdir(parents=True, exist_ok=True)
            valid_xml.write_text(
                '<testsuite name="suite" tests="4" failures="0" errors="0" skipped="1">'
                '<testcase classname="a" name="t1"/></testsuite>',
                encoding="utf-8",
            )
            passed, _, stats = assert_non_empty_suite(
                "unit",
                root_path,
                ["reports/TEST-*.xml"],
            )
            self.assertTrue(passed)
            self.assertEqual(stats.executed_tests, 3)

            write_not_applicable_junit(
                root_path / "na" / "junit.xml",
                "python-ci",
                "Not Applicable",
            )
            na_passed, _, _ = assert_non_empty_suite(
                "python-ci",
                root_path,
                ["na/*.xml"],
            )
            self.assertFalse(na_passed)

            failed_xml = root_path / "failed" / "TEST-fail.xml"
            failed_xml.parent.mkdir(parents=True, exist_ok=True)
            failed_xml.write_text(
                '<testsuite name="suite" tests="2" failures="1" errors="0" skipped="0"/>',
                encoding="utf-8",
            )
            fail_passed, _, _ = assert_non_empty_suite(
                "unit",
                root_path,
                ["failed/*.xml"],
            )
            self.assertFalse(fail_passed)

    def test_verify_release_gate_enforces_sha_and_group_outcomes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir_name:
            impact_file = Path(tmp_dir_name) / "impact.json"
            report = evaluate_impact_from_files(
                event_kind="push",
                base_sha="1111111",
                head_sha="2222222",
                merge_base_sha="1111111",
                changed_files=["backend-ai-py/services/diarization_worker.py"],
            )
            impact_file.write_text(json.dumps(asdict(report)), encoding="utf-8")

            ok_flag, _ = verify_release_gate(
                impact_file,
                expected_head_sha="2222222",
                python_result="success",
                java_unit_result="skipped",
                java_integration_result="success",
            )
            self.assertTrue(ok_flag)

            bad_sha_flag, _ = verify_release_gate(
                impact_file,
                expected_head_sha="9999999",
                python_result="success",
                java_unit_result="skipped",
                java_integration_result="skipped",
            )
            self.assertFalse(bad_sha_flag)

            skipped_required_flag, _ = verify_release_gate(
                impact_file,
                expected_head_sha="2222222",
                python_result="skipped",
                java_unit_result="skipped",
                java_integration_result="skipped",
            )
            self.assertFalse(skipped_required_flag)


if __name__ == "__main__":
    unittest.main()
