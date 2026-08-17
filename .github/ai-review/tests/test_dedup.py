import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / ".github/ai-review"))
from engine import deduplicate


def test_duplicate_findings_are_removed():
    finding = {"location": "a.java:10", "problem": "問題", "rule": "規範"}
    assert len(deduplicate([finding, dict(finding)])) == 1


def test_distinct_findings_are_kept():
    first = {"location": "a.java:10", "problem": "問題一", "rule": "規範"}
    second = {"location": "a.java:20", "problem": "問題二", "rule": "規範"}
    assert len(deduplicate([first, second])) == 2
