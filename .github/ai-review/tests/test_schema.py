REQUIRED = {
    "location", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}
ALLOWED_SEVERITY = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
ALLOWED_CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}


def validate_finding(finding):
    return (
        REQUIRED.issubset(finding)
        and finding["severity"] in ALLOWED_SEVERITY
        and finding["confidence"] in ALLOWED_CONFIDENCE
        and "blocking" not in finding
        and "decision" not in finding
    )


def test_valid_finding_passes():
    finding = {key: "x" for key in REQUIRED}
    finding.update(severity="HIGH", confidence="HIGH")
    assert validate_finding(finding)


def test_missing_required_field_fails():
    finding = {key: "x" for key in REQUIRED if key != "evidence"}
    finding.update(severity="HIGH", confidence="HIGH")
    assert not validate_finding(finding)


def test_ai_blocking_field_is_rejected():
    finding = {key: "x" for key in REQUIRED}
    finding.update(severity="HIGH", confidence="HIGH", blocking=False)
    assert not validate_finding(finding)


def test_ai_decision_field_is_rejected():
    finding = {key: "x" for key in REQUIRED}
    finding.update(severity="HIGH", confidence="HIGH", decision="APPROVE")
    assert not validate_finding(finding)
