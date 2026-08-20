import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_FIELDS = {
    "location", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}
ALLOWED_SEVERITY = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
ALLOWED_CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}


def load_policy(path: str | Path | None = None) -> dict:
    policy_path = path or ROOT / ".github/ai-review/policy.json"
    return json.loads(Path(policy_path).read_text(encoding="utf-8"))


def is_blocking(finding: dict, policy: dict) -> bool:
    return (
        finding.get("severity") in set(policy.get("blocking_severities", []))
        and finding.get("confidence") in set(policy.get("blocking_confidence", []))
    )


def validate_finding(finding: dict, policy: dict | None = None) -> bool:
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


def validate_coverage(expected: list[str], reviewed: list[str]) -> bool:
    return sorted(expected) == sorted(reviewed) and len(reviewed) == len(set(reviewed))


def deduplicate(findings: list[dict]) -> list[dict]:
    result: list[dict] = []
    seen: set[tuple[str | None, str | None, str | None]] = set()
    for finding in findings:
        key = (finding.get("location"), finding.get("problem"), finding.get("rule"))
        if key not in seen:
            seen.add(key)
            result.append(finding)
    return result


def evaluate(
    findings: list[dict],
    expected_files: list[str],
    reviewed_files: list[str],
    policy: dict | None = None,
) -> dict:
    active_policy = policy or load_policy()
    if not validate_coverage(expected_files, reviewed_files):
        return {"decision": "FAIL", "blocking_findings": [], "findings": []}
    unique = deduplicate(findings)
    blocking = [f for f in unique if is_blocking(f, active_policy)]
    return {
        "decision": "REQUEST_CHANGES" if blocking else "APPROVE",
        "blocking_findings": blocking,
        "findings": unique,
    }

