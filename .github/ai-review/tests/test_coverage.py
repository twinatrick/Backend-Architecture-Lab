import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))
from engine import validate_coverage


def test_exact_coverage_passes():
    assert validate_coverage(["a.java", "b.java"], ["a.java", "b.java"])


def test_missing_file_fails():
    assert not validate_coverage(["a.java", "b.java"], ["a.java"])


def test_extra_file_fails():
    assert not validate_coverage(["a.java"], ["a.java", "b.java"])


def test_duplicate_file_fails():
    assert not validate_coverage(["a.java", "b.java"], ["a.java", "a.java", "b.java"])
