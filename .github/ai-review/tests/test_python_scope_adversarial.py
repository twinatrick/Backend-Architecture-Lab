import sys
from pathlib import Path

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from python_claim_inspectors import PythonClaimInspector, RULE_PYTHON_DUPLICATE_DEFINITION
from review_snapshot import InspectionStatus, ReviewSnapshot


@pytest.mark.parametrize("source,line", [
    ("callback = lambda: 1", 1),
    ("callback = lambda: 1\nif flag:\n    def run():\n        pass", 3),
    ("def run():\n    pass\nif flag:\n    def run():\n        pass", 1),
    ("def run():\n    pass\ntry:\n    def run():\n        pass\nexcept Exception:\n    pass", 1),
])
def test_conditional_redefinition_and_lambda_are_not_proven_unique(source, line):
    snapshot = ReviewSnapshot([{"filename": "code.py", "full_content": source}],
                              head_sha="fixed-head", base_sha="fixed-base")
    result = PythonClaimInspector(RULE_PYTHON_DUPLICATE_DEFINITION).inspect({
        "location": f"code.py:{line}", "problem": "函數重複定義", "evidence": "def run():",
    }, snapshot)
    assert result.status == InspectionStatus.INCONCLUSIVE
