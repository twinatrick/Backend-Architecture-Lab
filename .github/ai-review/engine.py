import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_FIELDS = {
    "location", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}
ALLOWED_SEVERITY = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
ALLOWED_CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}


def load_policy(path=None):
    path = path or ROOT / ".github/ai-review/policy.json"
    return json.loads(Path(path).read_text(encoding="utf-8"))


def is_blocking(finding, policy):
    return (
        finding.get("severity") in set(policy["blocking_severities"])
        and finding.get("confidence") in set(policy["blocking_confidence"])
    )


def validate_finding(finding, policy=None):
    if not isinstance(finding, dict):
        return False
    if set(finding.keys()) != REQUIRED_FIELDS:
        return False
    location = finding.get("location")
    if not isinstance(location, str) or not location.strip():
        return False
    if finding.get("severity") not in ALLOWED_SEVERITY:
        return False
    if finding.get("confidence") not in ALLOWED_CONFIDENCE:
        return False
    for field in ("rule", "problem", "evidence", "risk", "recommendation"):
        val = finding.get(field)
        if not isinstance(val, str) or not val.strip():
            return False
    return True


def validate_coverage(expected, reviewed):
    return sorted(expected) == sorted(reviewed) and len(reviewed) == len(set(reviewed))


def deduplicate(findings):
    result = []
    seen = set()
    for finding in findings:
        key = (finding.get("location"), finding.get("problem"), finding.get("rule"))
        if key not in seen:
            seen.add(key)
            result.append(finding)
    return result


def evaluate(findings, expected_files, reviewed_files, policy=None):
    policy = policy or load_policy()
    if not validate_coverage(expected_files, reviewed_files):
        return {"decision": "FAIL", "blocking_findings": [], "findings": []}
    unique = deduplicate(findings)
    blocking = [f for f in unique if is_blocking(f, policy)]
    return {
        "decision": "REQUEST_CHANGES" if blocking else "APPROVE",
        "blocking_findings": blocking,
        "findings": unique,
    }
