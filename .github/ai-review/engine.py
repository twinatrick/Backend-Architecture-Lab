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
    "MICROSERVICE BOUNDARY",
    "CI SUPPLY CHAIN",
    "SECRET EXPOSURE",
    "FUNCTIONAL CORRECTNESS",
    "DATA INTEGRITY",
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
        str(category).upper() for category in policy.get("blocking_categories", [])
    }
    sev = str(finding.get("severity", "")).upper()
    conf = str(finding.get("confidence", "")).upper()
    cat = str(finding.get("category", "")).upper()
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
    cat = str(finding.get("category", "")).strip().upper()
    valid_categories = {
        str(category_item).strip().upper()
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


def deduplicate(findings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    seen: set[tuple[str | None, str | None, str | None]] = set()
    for finding in findings:
        key = (finding.get("location"), finding.get("problem"), finding.get("rule"))
        if key not in seen:
            seen.add(key)
            result.append(finding)
    return result


def evaluate(
    findings: list[dict[str, Any]],
    expected_files: list[str],
    reviewed_files: list[str],
    policy: dict[str, Any] | None = None,
) -> dict[str, Any]:
    active_policy = policy or load_policy()
    if not validate_coverage(expected_files, reviewed_files):
        cov_finding = {
            "location": f"{expected_files[0] if expected_files else 'PR'}:1",
            "category": "COMPLIANCE",
            "rule": "開發規範 §5 Review 完整性與可見度",
            "problem": "審查檔案覆蓋範圍與預期檔案清單不一致，存在未覆蓋或多餘檔案。",
            "evidence": f"預期: {expected_files}，審查回傳: {reviewed_files}",
            "risk": "未經完整審查之程式碼可能暗藏架構缺陷或安全漏洞。",
            "recommendation": "確保所有變更檔案均納入批次並完成覆蓋驗證。",
            "severity": "HIGH",
            "confidence": "HIGH",
        }
        return {
            "decision": "REQUEST_CHANGES",
            "blocking_findings": [cov_finding],
            "findings": [cov_finding],
        }
    unique = deduplicate(findings)
    blocking = [finding for finding in unique if is_blocking(finding, active_policy)]
    if has_high_risk_scope(expected_files):
        # 高風險核心範疇變更禁止 LLM 自動放行，強制 REQUEST_CHANGES (Fail-Closed) 要求人工審核
        high_risk_decision = str(
            active_policy.get("high_risk_decision", "REQUEST_CHANGES")
        ).upper()
        high_risk_files = [
            file_path for file_path in expected_files if is_high_risk_path(file_path)
        ]
        mandatory_finding = {
            "location": f"{high_risk_files[0]}:1",
            "category": "Security",
            "rule": "Mandatory Human Architecture & Security Review",
            "problem": "變更涉及核心安全或基礎架構高風險範疇，禁止由 AI 自動放行。",
            "evidence": f"涉及高風險檔案: {', '.join(high_risk_files[:5])}",
            "risk": "未經資深工程師或架構師人工審查可能引入特權繞過或架構缺陷。",
            "recommendation": "請指派專案資深維護者進行人工審查並批准 PR。",
            "severity": "HIGH",
            "confidence": "HIGH",
        }
        unique.append(mandatory_finding)
        if high_risk_decision == "REQUEST_CHANGES":
            blocking.append(mandatory_finding)
        decision = high_risk_decision
    elif blocking:
        decision = "REQUEST_CHANGES"
    else:
        decision = "APPROVE"
    return {
        "decision": decision,
        "blocking_findings": blocking,
        "findings": unique,
    }

