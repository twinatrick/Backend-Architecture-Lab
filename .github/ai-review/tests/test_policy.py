import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = json.loads((ROOT / ".github/ai-review/policy.json").read_text(encoding="utf-8"))


def blocks(severity, confidence):
    return severity in set(POLICY["blocking_severities"]) and confidence in set(POLICY["blocking_confidence"])


def test_high_high_blocks():
    assert blocks("HIGH", "HIGH")


def test_critical_high_blocks():
    assert blocks("CRITICAL", "HIGH")


def test_high_low_does_not_block():
    assert not blocks("HIGH", "LOW")


def test_medium_high_does_not_block():
    assert not blocks("MEDIUM", "HIGH")


def test_policy_required_fields_are_present():
    required = {
        "location", "rule", "problem", "evidence", "risk",
        "recommendation", "severity", "confidence",
    }
    assert required == set(POLICY["required_fields"])


def test_fail_closed_events_exist():
    expected = {
        "missing_batch", "invalid_json", "invalid_schema",
        "missing_artifact", "coverage_incomplete", "review_execution_failure",
    }
    assert expected.issubset(set(POLICY["fail_closed"]))
