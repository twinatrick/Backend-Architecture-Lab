import re
from typing import Any

BANNED_PERMISSIONS = (
    "PersonalEdit",
    "EditAll",
    "SkillManagement",
    "RolePermission",
    "AquarkDataAvg",
    "LimitSetting",
)

SECRET_REGEXES = (
    re.compile(r"-----BEGIN (?:RSA |OPENSSH |DSA |EC )?PRIVATE KEY-----"),
    re.compile(r"\bghp_[A-Za-z0-9_]{36}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{82}\b"),
    re.compile(r"\bAIza[0-9A-Za-z-_]{35}\b"),
    re.compile(r"\bgsk_[a-zA-Z0-9]{48}\b"),
    re.compile(
        r"(?:api[_-]?key|secret[_-]?key|access[_-]?token)\s*[:=]\s*[\"'][A-Za-z0-9_\-\.]{20,}[\"']",
        re.I,
    ),
)


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
