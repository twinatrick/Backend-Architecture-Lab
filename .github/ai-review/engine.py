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
        str(s).upper() for s in policy.get("blocking_severities", ["CRITICAL", "HIGH"])
    }
    blocking_confidences = {
        str(c).upper() for c in policy.get("blocking_confidence", ["HIGH"])
    }
    blocking_categories = {
        str(k).upper() for k in policy.get("blocking_categories", [])
    }
    sev = str(finding.get("severity", "")).upper()
    conf = str(finding.get("confidence", "")).upper()
    cat = str(finding.get("category", "")).upper()
    cat_match = (
        not blocking_categories
        or not cat
        or cat in blocking_categories
        or sev == "CRITICAL"
    )
    return bool(sev in blocking_severities and conf in blocking_confidences and cat_match)


def is_high_risk_path(path: str) -> bool:
    normalized = path.replace("\\", "/").lower()
    return any(keyword in normalized for keyword in HIGH_RISK_PATH_KEYWORDS)


def has_high_risk_scope(files: list[str]) -> bool:
    return any(is_high_risk_path(f) for f in files)


def validate_finding(
    finding: dict[str, Any],
    allowed_files: list[str] | None = None,
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
        norm_allowed = {f.replace("\\", "/").lower() for f in allowed_files}
        if path.replace("\\", "/").lower() not in norm_allowed:
            return False
    if finding.get("severity") not in ALLOWED_SEVERITY:
        return False
    if finding.get("confidence") not in ALLOWED_CONFIDENCE:
        return False
    for field in (
        "category", "rule", "problem", "evidence", "risk", "recommendation"
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
        return {"decision": "FAIL", "blocking_findings": [], "findings": []}
    unique = deduplicate(findings)
    blocking = [f for f in unique if is_blocking(f, active_policy)]
    if blocking:
        decision = "REQUEST_CHANGES"
    elif has_high_risk_scope(expected_files):
        # 高風險核心範疇 (CI/安全/IAM/pom) 變更禁止 LLM 自動 APPROVE，強制降級為 COMMENT 供人工審查
        decision = "COMMENT"
    else:
        decision = "APPROVE"
    return {
        "decision": decision,
        "blocking_findings": blocking,
        "findings": unique,
    }

