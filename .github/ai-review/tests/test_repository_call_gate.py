import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from claim_verifier import verify_and_refine_batch_findings
from engine import evaluate
from reporter import format_json_report
from review_snapshot import ReviewSnapshot
from rule_registry import RuleRegistry

TARGET_PATH = "module/src/main/java/sample/Repository.java"
CALLER_PATH = "module/src/main/java/sample/RepositoryCaller.java"
TARGET = "package sample;\npublic interface Repository {\n void removeCustom(String id);\n}"
CALLER = """package sample;
public class RepositoryCaller {
 private final Repository repository;
 public void remove(String id) { repository.removeCustom(id); }
}
"""


@pytest.mark.parametrize("target,caller,expected_status,blocked", [
    (TARGET, CALLER, "COMPLIANT", False),
    (TARGET, CALLER.replace("String id", "Integer id"), "INCONCLUSIVE", True),
    (TARGET.replace(" void removeCustom", ' @Query("""\nselect { example }\n""")\n'
                    " void removeCustom"),
     CALLER.replace("repository.removeCustom(id)",
                    "String localId = id; repository.removeCustom(localId)"),
     "COMPLIANT", False),
])
def test_caller_resolution_gate_audit_and_json_agree(target, caller, expected_status, blocked):
    sources = {TARGET_PATH: target, CALLER_PATH: caller}
    getter = MagicMock(return_value={
        "truncated": False, "tree": [{"type": "blob", "path": path} for path in sources],
    })
    snapshot = ReviewSnapshot(
        [{"filename": path, "full_content": content} for path, content in sources.items()],
        repo_name="owner/repo", head_sha="a" * 40, base_sha="b" * 40,
        repository_getter=getter,
    )
    method_line = target[:target.index("void removeCustom")].count("\n") + 1
    finding = {
        "location": f"{TARGET_PATH}:{method_line}", "rule": "RULE_DEAD_CODE_REPOSITORY",
        "problem": "Repository 方法沒有呼叫端", "evidence": "void removeCustom(String id);",
        "severity": "HIGH", "confidence": "HIGH", "category": "ARCHITECTURE",
        "risk": "無法確認方法使用狀態", "recommendation": "查證生產程式呼叫",
    }
    audit = []
    findings = verify_and_refine_batch_findings(
        "data", 1, [TARGET_PATH], [finding], snapshot, RuleRegistry(), None, "",
        gate_mode="enforce", audit_records=audit,
    )
    assert audit[0]["status"] == expected_status
    assert audit[0]["claim_id"] and audit[0]["tool_version"]
    result = evaluate(findings, [TARGET_PATH], [TARGET_PATH], {
        "blocking_severities": ["HIGH"], "blocking_confidence": ["HIGH"],
        "blocking_categories": ["COMPLIANCE", "ARCHITECTURE"],
    })
    assert bool(result["blocking_findings"]) is blocked
    if blocked:
        assert "REVIEW_INCOMPLETE" in findings[0]["problem"]
    else:
        assert findings == []
    report = json.loads(format_json_report(
        result["decision"], findings, result["blocking_findings"], 1, [TARGET_PATH],
        claim_audit=audit,
    ))
    assert report["claim_audit"][0]["status"] == expected_status
    getter.assert_called_once()
