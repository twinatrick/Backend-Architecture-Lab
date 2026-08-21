import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_FIELDS = {
    "location", "category", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}
ALLOWED_SEVERITY = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
ALLOWED_CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}
ALLOWED_CATEGORIES = {
    "ARCHITECTURE",
    "SECURITY",
    "COMPLIANCE",
    "BUG",
    "RELIABILITY",
    "AUTHENTICATION",
    "AUTHORIZATION",
    "BOLA",
    "IDOR",
    "PERMISSION",
    "MICROSERVICE_BOUNDARY",
    "CI_SUPPLY_CHAIN",
    "SECRET_EXPOSURE",
    "FUNCTIONAL_CORRECTNESS",
    "DATA_INTEGRITY",
}

HIGH_RISK_PATH_KEYWORDS = (
    ".github/workflows/",
    ".github/ai-review/",
    "pom.xml",
    "Dockerfile",
    "compose.yaml",
    "/security/",
    "/filter/",
    "/aop/",
    "/controller/",
    "/feign/",
    "backend-gateway/",
    "backend-iam-service/",
)


def load_policy(path: str | Path | None = None) -> dict[str, Any]:
    policy_path = path or ROOT / ".github/ai-review/policy.json"
    return json.loads(Path(policy_path).read_text(encoding="utf-8"))


def is_blocking(finding: dict[str, Any], policy: dict[str, Any]) -> bool:
    blocking_severities = {
        str(severity).upper()
        for severity in policy.get("blocking_severities", ["CRITICAL", "HIGH"])
    }
    blocking_confidences = {
        str(confidence).upper()
        for confidence in policy.get("blocking_confidence", ["HIGH"])
    }
    blocking_categories = {
        str(category).upper().replace(" ", "_")
        for category in policy.get("blocking_categories", [])
    }
    sev = str(finding.get("severity", "")).upper()
    conf = str(finding.get("confidence", "")).upper()
    cat = str(finding.get("category", "")).upper().replace(" ", "_")
    cat_match = (
        not blocking_categories
        or (bool(cat) and cat in blocking_categories)
        or sev == "CRITICAL"
    )
    return bool(sev in blocking_severities and conf in blocking_confidences and cat_match)


def is_high_risk_path(path: str) -> bool:
    normalized = path.replace("\\", "/").lower()
    return any(keyword in normalized for keyword in HIGH_RISK_PATH_KEYWORDS)


def has_high_risk_scope(files: list[str]) -> bool:
    return any(is_high_risk_path(file_path) for file_path in files)


def validate_finding(
    finding: dict[str, Any],
    allowed_files: list[str] | None = None,
    allowed_categories: set[str] | list[str] | None = None,
) -> bool:
    if not isinstance(finding, dict):
        return False
    if set(finding.keys()) != REQUIRED_FIELDS:
        return False
    location = finding.get("location")
    if not isinstance(location, str) or not location.strip():
        return False
    if ":" not in location:
        return False
    path, line_str = location.rsplit(":", 1)
    if not path.strip() or not line_str.isdigit() or int(line_str) < 1:
        return False
    if allowed_files is not None:
        norm_allowed = {
            allowed_path.replace("\\", "/").lower() for allowed_path in allowed_files
        }
        if path.replace("\\", "/").lower() not in norm_allowed:
            return False
    if finding.get("severity") not in ALLOWED_SEVERITY:
        return False
    if finding.get("confidence") not in ALLOWED_CONFIDENCE:
        return False
    cat = str(finding.get("category", "")).strip().upper().replace(" ", "_")
    valid_categories = {
        str(category_item).strip().upper().replace(" ", "_")
        for category_item in (allowed_categories or ALLOWED_CATEGORIES)
    }
    if not cat or cat not in valid_categories:
        return False
    for field in (
        "rule", "problem", "evidence", "risk", "recommendation"
    ):
        val = finding.get(field)
        if not isinstance(val, str) or not val.strip():
            return False
    return True


def validate_coverage(expected: list[str], reviewed: list[str]) -> bool:
    return sorted(expected) == sorted(reviewed) and len(reviewed) == len(set(reviewed))


def evaluate_coverage(
    expected_files: list[str],
    reviewed_files: list[str],
) -> tuple[bool, dict[str, Any] | None]:
    """驗證批次審查檔案清單之完整性，未達完全覆蓋時產生阻擋 Finding。"""
    if validate_coverage(expected_files, reviewed_files):
        return True, None

    fallback_location = expected_files[0] if expected_files else "PR"
    coverage_finding = {
        "location": f"{fallback_location}:1",
        "category": "COMPLIANCE",
        "rule": "開發規範 §5 Review 完整性與可見度",
        "problem": "審查檔案覆蓋範圍與預期檔案清單不一致，存在未覆蓋或多餘檔案。",
        "evidence": f"預期: {expected_files}，審查回傳: {reviewed_files}",
        "risk": "未經完整審查之程式碼可能暗藏架構缺陷或安全漏洞。",
        "recommendation": "確保所有變更檔案均納入批次並完成覆蓋驗證。",
        "severity": "HIGH",
        "confidence": "HIGH",
    }
    return False, coverage_finding


def deduplicate(findings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[tuple[str | None, str | None, str | None]] = set()
    for finding in findings:
        key = (finding.get("location"), finding.get("problem"), finding.get("rule"))
        if key not in seen:
            seen.add(key)
            result.append(finding)
    return result


def evaluate_findings(
    findings: list[dict[str, Any]],
    policy: dict[str, Any],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """評估程式碼 Findings 之去重與 Policy 阻擋條件（SRP：純粹關注程式碼瑕疵）。"""
    unique_findings = deduplicate(findings)
    blocking_findings = [
        finding_item for finding_item in unique_findings
        if is_blocking(finding_item, policy)
    ]
    return unique_findings, blocking_findings


def evaluate_review_requirements(
    expected_files: list[str],
    policy: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """評估 PR 是否觸及高風險路徑並需額外人工架構/安全簽核（SRP：審查流程策略）。"""
    active_policy = policy or load_policy()
    requires_human_policy = bool(
        active_policy.get("high_risk_requires_human_review", True)
    )
    high_risk_files = [
        file_path for file_path in expected_files
        if is_high_risk_path(file_path)
    ]
    is_high_risk = bool(high_risk_files)
    return {
        "requires_human_review": is_high_risk and requires_human_policy,
        "high_risk_files": high_risk_files,
    }


def evaluate(
    findings: list[dict[str, Any]],
    expected_files: list[str],
    reviewed_files: list[str],
    policy: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """聚合覆蓋率、程式碼 Findings 與審查流程需求，產出最終決策字典。"""
    active_policy = policy or load_policy()
    coverage_valid, coverage_finding = evaluate_coverage(
        expected_files, reviewed_files
    )
    if not coverage_valid and coverage_finding is not None:
        return {
            "decision": "REQUEST_CHANGES",
            "blocking_findings": [coverage_finding],
            "findings": [coverage_finding],
            "requires_human_review": has_high_risk_scope(expected_files),
            "high_risk_files": [
                file_path for file_path in expected_files
                if is_high_risk_path(file_path)
            ],
            "coverage_valid": False,
        }

    unique_findings, blocking_findings = evaluate_findings(
        findings, active_policy
    )
    requirements = evaluate_review_requirements(
        expected_files, active_policy
    )

    if blocking_findings:
        decision = "REQUEST_CHANGES"
    elif requirements["requires_human_review"]:
        decision = "HUMAN_REVIEW_REQUIRED"
    else:
        decision = "APPROVE"

    return {
        "decision": decision,
        "blocking_findings": blocking_findings,
        "findings": unique_findings,
        "requires_human_review": requirements["requires_human_review"],
        "high_risk_files": requirements["high_risk_files"],
        "coverage_valid": True,
    }

