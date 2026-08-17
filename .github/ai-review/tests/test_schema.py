import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / ".github/ai-review"))
from engine import validate_finding

REQUIRED = {
    "location", "rule", "problem", "evidence", "risk",
    "recommendation", "severity", "confidence",
}


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
