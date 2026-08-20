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
        "category": "Security",
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


def test_is_blocking_with_all_categories_and_severities():
    policy = {
        "blocking_severities": ["CRITICAL", "HIGH"],
        "blocking_confidence": ["HIGH"],
        "blocking_categories": [
            "Security", "Architecture", "Compliance",
        ],
    }

    # 驗證 Architecture 類別之 HIGH 違規判定為阻擋
    f_arch = {
        "location": "src/Service.java:10",
        "category": "Architecture",
        "severity": "HIGH",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_arch, policy) is True

    # 驗證 Compliance 類別之 HIGH 違規判定為阻擋
    f_comp = {
        "location": "script.py:20",
        "category": "Compliance",
        "severity": "HIGH",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_comp, policy) is True

    # 驗證任意自定義/未知類別之 CRITICAL 違規判定為阻擋
    f_custom = {
        "location": "src/Util.java:5",
        "category": "CustomCategory",
        "severity": "CRITICAL",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_custom, policy) is True

    # 驗證大小寫混合之類別判定為阻擋
    f_lower = {
        "location": "src/App.java:1",
        "category": "architecture",
        "severity": "HIGH",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_lower, policy) is True

    # 驗證 MEDIUM 與 LOW 違規不判定為阻擋
    f_med = {
        "location": "src/App.java:1",
        "category": "Architecture",
        "severity": "MEDIUM",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_med, policy) is False

    f_low = {
        "location": "src/App.java:1",
        "category": "Compliance",
        "severity": "LOW",
        "confidence": "HIGH",
    }
    assert engine.is_blocking(f_low, policy) is False


def test_evaluate_with_architecture_and_compliance_findings_triggers_request_changes():
    policy = {
        "blocking_severities": ["CRITICAL", "HIGH"],
        "blocking_confidence": ["HIGH"],
        "blocking_categories": ["Security", "Architecture", "Compliance"],
    }
    findings = [
        {
            "location": "src/main/Service.java:42",
            "category": "Architecture",
            "rule": "ARCH-01",
            "problem": "Controller 直接操作 EntityManager",
            "evidence": "entityManager.persist(entity)",
            "risk": "破壞分層架構",
            "recommendation": "改由 DataAccess 層操作",
            "severity": "HIGH",
            "confidence": "HIGH",
        },
        {
            "location": "scripts/tool.py:100",
            "category": "Compliance",
            "rule": "PY-01",
            "problem": "單檔超過 300 行且包含泛型 Exception",
            "evidence": "except Exception: pass",
            "risk": "違反開發規範 4.3 條",
            "recommendation": "拆分模組並捕捉具體例外",
            "severity": "HIGH",
            "confidence": "HIGH",
        },
    ]
    res = engine.evaluate(
        findings=findings,
        expected_files=["src/main/Service.java", "scripts/tool.py"],
        reviewed_files=["src/main/Service.java", "scripts/tool.py"],
        policy=policy,
    )
    assert res["decision"] == "REQUEST_CHANGES"
    assert len(res["blocking_findings"]) == 2
