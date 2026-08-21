from typing import Any

from redaction import STATIC_SECRET_REGEXES

BANNED_PERMISSIONS = (
    "PersonalEdit",
    "EditAll",
    "SkillManagement",
    "RolePermission",
    "AquarkDataAvg",
    "LimitSetting",
    "ViewPersonal",
    "DeleteAll",
    "SkillView",
    "UserManagement",
)

SECRET_REGEXES = STATIC_SECRET_REGEXES


def make_finding(
    path: str,
    line: int,
    severity: str,
    category: str,
    rule: str,
    problem: str,
    evidence: str,
    risk: str,
    recommendation: str,
) -> dict[str, Any]:
    return {
        "location": f"{path}:{line}",
        "severity": severity,
        "confidence": "HIGH",
        "category": category,
        "rule": rule,
        "problem": problem,
        "evidence": evidence[:100],
        "risk": risk,
        "recommendation": recommendation,
    }
