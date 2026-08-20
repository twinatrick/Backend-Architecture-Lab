import json
import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import reporter


def test_format_markdown_report_approves():
    report = reporter.format_markdown_report(
        decision="APPROVE",
        changed_files=["src/App.java"],
        results=[{}],
        unique_findings=[],
        blocking_findings=[],
        passed_checks=["Check A", "Check B"],
    )
    assert "## 審查結果\nAPPROVE" in report
    assert "已審查 1 個變更檔案" in report
    assert "Check A" in report


def test_format_markdown_report_with_findings_and_redaction():
    findings = [
        {
            "location": "app.py:10",
            "rule": "SEC-01",
            "problem": "Found secret gsk_secret_123456789012345678901234 in code",
            "evidence": "api_key = 'gsk_secret_123456789012345678901234'",
            "risk": "Leakage",
            "recommendation": "Use env var",
            "severity": "BLOCKER",
            "confidence": "HIGH",
        }
    ]
    report = reporter.format_markdown_report(
        decision="REQUEST_CHANGES",
        changed_files=["app.py"],
        results=[{}],
        unique_findings=findings,
        blocking_findings=findings,
        passed_checks=[],
    )
    assert "REQUEST_CHANGES" in report
    assert "gsk_secret_" not in report
    assert "[REDACTED]" in report
    assert "app.py:10" in report


def test_format_json_report():
    findings = [
        {
            "location": "app.py:1",
            "rule": "R1",
            "problem": "Bad code",
            "evidence": "foo",
            "risk": "Crash",
            "recommendation": "Fix",
            "severity": "HIGH",
            "confidence": "HIGH",
        }
    ]
    json_str = reporter.format_json_report(
        decision="REQUEST_CHANGES",
        unique_findings=findings,
        blocking_findings=findings,
        batch_count=1,
        changed_files=["app.py"],
    )
    data = json.loads(json_str)
    assert data["decision"] == "REQUEST_CHANGES"
    assert len(data["findings"]) == 1
    assert len(data["blocking_findings"]) == 1
    assert data["batches"] == 1
    assert data["files_reviewed"] == ["app.py"]


def test_format_reports_with_audit_info():
    audit_info = {
        "trigger_type": "workflow_dispatch",
        "actor": "admin-bob",
        "head_sha": "1234567890abcdef",
    }
    report = reporter.format_markdown_report(
        decision="APPROVE",
        changed_files=["src/App.java"],
        results=[{}],
        unique_findings=[],
        blocking_findings=[],
        passed_checks=["Check A"],
        audit_info=audit_info,
    )
    assert "## 審查稽核軌跡" in report
    assert "admin-bob" in report
    assert "1234567890abcdef" in report

    json_str = reporter.format_json_report(
        decision="APPROVE",
        unique_findings=[],
        blocking_findings=[],
        batch_count=1,
        changed_files=["src/App.java"],
        audit_info=audit_info,
    )
    data = json.loads(json_str)
    assert data["audit"]["actor"] == "admin-bob"
    assert data["audit"]["trigger_type"] == "workflow_dispatch"

