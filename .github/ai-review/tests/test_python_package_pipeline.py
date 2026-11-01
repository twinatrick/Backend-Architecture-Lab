import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from claim_verifier import apply_batch_claim_verification
from package_version_inspector import PackageVersionInspector
from reporter import evaluate_shadow_review, format_json_report
from rule_registry import RuleRegistry


def finding(path, line, rule, problem, evidence):
    return {"location": f"{path}:{line}", "rule": rule, "problem": problem,
            "evidence": evidence, "severity": "HIGH", "confidence": "HIGH",
            "category": "COMPLIANCE", "risk": "可能無法執行", "recommendation": "查證後修正"}


def apply(data, path, source, monkeypatch, *, base=None, caller=None):
    monkeypatch.setenv("AI_REVIEW_CLAIM_GATE_MODE", "shadow")
    monkeypatch.delenv("AI_REVIEW_VERDICT_LEDGER_WRITER", raising=False)
    apply_batch_claim_verification(
        data, "ci", 1, [path], [{"filename": path, "full_content": source,
                                "status": "added" if base is None else "modified"}],
        "test contract", caller or MagicMock(), 10, False, head_sha="fixed-head",
        base_sha="fixed-base", content_fetcher=lambda repo, file, sha: base,
    )


@pytest.mark.parametrize("source,line,rule,problem,evidence", [
    ("class Runner:\n    def chat_completion(self):\n        return 1\n"
     "def chat_completion():\n    return Runner().chat_completion()",
     4, "Python 規範", "函數重複定義", "def chat_completion():"),
    ("import os\nVALUE = 1", 1, "Python 規範", "import 未置頂", "import os"),
    ("def run():\n    missing_findings = []\n    if missing_findings:\n        return 1",
     3, "Python 規範", "missing_findings 未定義", "if missing_findings:"),
])
def test_allegation_through_registry_shadow_audit_and_report(
    source, line, rule, problem, evidence, monkeypatch,
):
    original = finding("ci.py", line, rule, problem, evidence)
    data = {"findings": [original]}
    apply(data, "ci.py", source, monkeypatch)
    assert data["findings"] == [original]
    assert data["shadow_verified_findings"] == []
    assert {item["status"] for item in data["claim_audit"]} == {"COMPLIANT"}
    assert all(item["tool_version"] and item["claim_id"] for item in data["claim_audit"])
    shadow = evaluate_shadow_review([data], [], ["ci.py"], ["ci.py"], {})
    assert shadow is not None
    encoded = format_json_report("REQUEST_CHANGES", [original], [original], 1, ["ci.py"],
                                 shadow_review=shadow, claim_audit=data["claim_audit"])
    assert "COMPLIANT" in encoded
    payload = json.loads(encoded)
    assert payload["decision"] == "REQUEST_CHANGES"
    assert payload["findings"] == [original]


def test_real_duplicate_cannot_disappear_when_model_omits_it(monkeypatch):
    original = finding("ci.py", 3, "RULE_PYTHON_DUPLICATE_DEFINITION",
                       "函數重複定義", "def run():")
    data = {"findings": [original]}
    source = "def run():\n    return 1\ndef run():\n    return 2"
    caller = MagicMock(return_value='{"coverage":"COMPLETE","files_reviewed":["ci.py"],'
                                    '"findings":[]}')
    caller.supports_rule_dialogue = True
    apply(data, "ci.py", source, monkeypatch, caller=caller)
    assert data["shadow_verified_findings"][0]["severity"] == "HIGH"
    assert data["claim_audit"][0]["status"] == "VIOLATION"
    caller.assert_not_called()


@pytest.mark.parametrize("status,payload,expected", [
    (200, {"info": {"name": "requests", "version": "2.34.2"},
           "urls": [{"filename": "requests.whl", "yanked": False}]}, "COMPLIANT"),
    (404, {}, "INCONCLUSIVE"), (429, {}, "INCONCLUSIVE"),
    (200, {"info": {"name": "requests", "version": "9.9.9"}, "urls": []}, "INCONCLUSIVE"),
])
def test_package_tool_enters_real_pipeline_without_changing_official_findings(
    status, payload, expected, monkeypatch,
):
    response = MagicMock(status_code=status, json=lambda: payload)
    getter = MagicMock(return_value=response)
    original_init = RuleRegistry._register_defaults

    def registered(registry):
        original_init(registry)
        registry._inspectors["RULE_PACKAGE_VERSION_EXISTS"] = PackageVersionInspector(getter)

    monkeypatch.setattr(RuleRegistry, "_register_defaults", registered)
    original = finding("requirements.txt", 1, "依賴規範", "requests 套件版本不存在",
                       "requests==2.34.2")
    data = {"findings": [original]}
    apply(data, "requirements.txt", "requests==2.34.2", monkeypatch)
    assert data["findings"] == [original]
    assert data["claim_audit"][0]["status"] == expected
    if expected == "COMPLIANT":
        assert data["shadow_verified_findings"] == []
    else:
        assert data["shadow_verified_findings"]
    getter.assert_called_once()


def test_duplicate_outside_direct_change_stays_pending_not_new_violation(monkeypatch):
    source = "def run():\n    return 1\ndef run():\n    return 2"
    data = {"findings": [finding("ci.py", 3, "RULE_PYTHON_DUPLICATE_DEFINITION",
                                 "函數重複定義", "def run():")]}
    apply(data, "ci.py", source, monkeypatch, base=source)
    assert data["claim_audit"][0]["status"] == "INCONCLUSIVE"
    assert "base_head_impact_attribution" in data["claim_audit"][0]["missing_context"]
    assert data["shadow_verified_findings"][0]["severity"] == "HIGH"


def test_pytorch_multi_package_false_positive_is_removed_from_verified_report(monkeypatch):
    source = ("--extra-index-url https://download.pytorch.org/whl/cu126\n"
              "torch==2.13.0+cu126\ntorchaudio==2.11.1")
    getter = MagicMock(side_effect=[
        MagicMock(status_code=200, text='<a href="https://download.pytorch.org/whl/cu126/'
                  'torch-2.13.0%2Bcu126-cp311-cp311-win_amd64.whl">wheel</a>'),
        MagicMock(status_code=200, json=lambda: {
            "info": {"name": "torchaudio", "version": "2.11.1"},
            "urls": [{"filename": "torchaudio.whl", "yanked": False}],
        }),
    ])
    original_init = RuleRegistry._register_defaults

    def registered(registry):
        original_init(registry)
        registry._inspectors["RULE_PACKAGE_VERSION_EXISTS"] = PackageVersionInspector(getter)

    monkeypatch.setattr(RuleRegistry, "_register_defaults", registered)
    original = finding("requirements.txt", 2, "依賴規範", "指定了不存在的套件版本，尚未發布",
                       "torch==2.13.0+cu126\ntorchaudio==2.11.1")
    data = {"findings": [original]}
    apply(data, "requirements.txt", source, monkeypatch)
    assert data["findings"] == [original]  # 影子模式保留原始報告供比較。
    assert data["shadow_verified_findings"] == []
    assert data["claim_audit"][0]["status"] == "COMPLIANT"
