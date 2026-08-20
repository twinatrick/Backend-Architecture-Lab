import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))
from engine import validate_finding

REQUIRED = {
    "location", "category", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}


def test_valid_finding_passes():
    finding = {key: "x" for key in REQUIRED}
    finding.update(location="App.java:10", severity="HIGH", confidence="HIGH")
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


def test_non_dict_finding_fails():
    assert not validate_finding(["not", "a", "dict"])
    assert not validate_finding("string finding")
    assert not validate_finding(None)


def test_empty_or_whitespace_location_fails():
    finding = {key: "x" for key in REQUIRED}
    finding.update(severity="HIGH", confidence="HIGH", location="   ")
    assert not validate_finding(finding)

    finding["location"] = ""
    assert not validate_finding(finding)


def test_non_string_or_empty_text_fields_fail():
    for field in ("rule", "problem", "evidence", "risk", "recommendation"):
        finding = {key: "x" for key in REQUIRED}
        finding.update(severity="HIGH", confidence="HIGH")
        finding[field] = ""
        assert not validate_finding(finding)

        finding[field] = 123
        assert not validate_finding(finding)


def test_extra_unexpected_field_is_rejected():
    finding = {key: "x" for key in REQUIRED}
    finding.update(severity="HIGH", confidence="HIGH", unexpected_extra_field="invalid")
    assert not validate_finding(finding)


