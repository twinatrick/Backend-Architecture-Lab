import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import engine


def test_evaluate_policy_severity_and_decision():
    policy = {
        "blocking_severities": ["CRITICAL", "HIGH"],
        "blocking_confidence": ["HIGH"],
    }
    findings_high = [
        {
            "location": "src/App.java:10",
            "rule": "SEC-01",
            "problem": "SQL injection",
            "evidence": "foo",
            "risk": "Leak",
            "recommendation": "Fix",
            "severity": "HIGH",
            "confidence": "HIGH",
        }
    ]
    res_high = engine.evaluate(
        findings=findings_high,
        expected_files=["src/App.java"],
        reviewed_files=["src/App.java"],
        policy=policy,
    )
    assert res_high["decision"] == "REQUEST_CHANGES"
    assert len(res_high["blocking_findings"]) == 1

    findings_low = [
        {
            "location": "src/App.java:10",
            "rule": "STYLE-01",
            "problem": "Typo",
            "evidence": "bar",
            "risk": "None",
            "recommendation": "Fix typo",
            "severity": "LOW",
            "confidence": "HIGH",
        }
    ]
    res_low = engine.evaluate(
        findings=findings_low,
        expected_files=["src/App.java"],
        reviewed_files=["src/App.java"],
        policy=policy,
    )
    assert res_low["decision"] == "APPROVE"
    assert len(res_low["blocking_findings"]) == 0

    # 高風險核心範疇 (CI/安全/IAM/pom) 變更無阻擋項目時應降級為 COMMENT
    res_high_risk_ci = engine.evaluate(
        findings=[],
        expected_files=[".github/workflows/ci.yml"],
        reviewed_files=[".github/workflows/ci.yml"],
        policy=policy,
    )
    assert res_high_risk_ci["decision"] == "COMMENT"

    # 覆蓋率未達標時應回傳 FAIL 決策
    res_cov_fail = engine.evaluate(
        findings=[],
        expected_files=["src/App.java"],
        reviewed_files=[],
        policy=policy,
    )
    assert res_cov_fail["decision"] == "FAIL"


def test_validate_finding_schema_and_defaults():
    raw_finding = {
        "location": "src/main/App.java:10",
        "rule": "RULE-01",
        "problem": "Bad code",
        "evidence": "foo = bar",
        "risk": "Crash",
        "recommendation": "Fix it",
        "severity": "HIGH",
        "confidence": "HIGH",
    }
    assert engine.validate_finding(raw_finding) is True


def test_validate_finding_invalid_or_missing_fields():
    assert engine.validate_finding(None) is False
    assert engine.validate_finding("not a dict") is False
    assert engine.validate_finding({}) is False
    # 缺少必要欄位 location
    assert engine.validate_finding({
        "rule": "R1",
        "problem": "Crash",
        "evidence": "foo",
        "risk": "high",
        "recommendation": "fix",
        "severity": "HIGH",
        "confidence": "HIGH",
    }) is False
    # 無效的 severity 嚴重等級
    assert engine.validate_finding({
        "location": "foo.py:1",
        "rule": "R1",
        "problem": "Crash",
        "evidence": "foo",
        "risk": "high",
        "recommendation": "fix",
        "severity": "INVALID_SEVERITY",
        "confidence": "HIGH",
    }) is False
