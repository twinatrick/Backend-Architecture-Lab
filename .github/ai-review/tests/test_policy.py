import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))
from engine import is_blocking, load_policy

POLICY = load_policy()


def test_high_high_blocks():
    assert is_blocking({"severity": "HIGH", "confidence": "HIGH"}, POLICY)


def test_critical_high_blocks():
    assert is_blocking({"severity": "CRITICAL", "confidence": "HIGH"}, POLICY)


def test_high_low_does_not_block():
    assert not is_blocking({"severity": "HIGH", "confidence": "LOW"}, POLICY)


def test_medium_high_does_not_block():
    assert not is_blocking({"severity": "MEDIUM", "confidence": "HIGH"}, POLICY)


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
