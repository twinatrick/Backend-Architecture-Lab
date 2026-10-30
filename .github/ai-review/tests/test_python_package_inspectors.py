import ast
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

from github_content import fetch_file_content_fallback
from package_version_inspector import PackageVersionInspector
from python_claim_inspectors import (
    PythonClaimInspector,
    RULE_PYTHON_DUPLICATE_DEFINITION,
    RULE_PYTHON_IMPORT_POSITION,
    RULE_PYTHON_UNDEFINED_NAME,
)
from review_snapshot import InspectionStatus, ReviewSnapshot
from rule_registry import RuleRegistry


def inspect_python(source, line, rule, evidence="", problem=""):
    snapshot = ReviewSnapshot([{"filename": "app.py", "full_content": source}], head_sha="head")
    return PythonClaimInspector(rule).inspect(
        {"location": f"app.py:{line}", "evidence": evidence, "problem": problem}, snapshot,
    )


def test_class_method_and_module_wrapper_do_not_duplicate():
    source = "class Runner:\n    def chat_completion(self):\n        return 1\n\n"
    source += "def chat_completion():\n    return Runner().chat_completion()\n"
    for line in (2, 5):
        assert inspect_python(source, line, RULE_PYTHON_DUPLICATE_DEFINITION).status == (
            InspectionStatus.COMPLIANT
        )


def test_same_scope_duplicate_and_decorators():
    source = "def run():\n    return 1\ndef run():\n    return 2\n"
    assert inspect_python(source, 3, RULE_PYTHON_DUPLICATE_DEFINITION).status == (
        InspectionStatus.VIOLATION
    )
    assert inspect_python("@overload\n" + source, 2,
                          RULE_PYTHON_DUPLICATE_DEFINITION).status == InspectionStatus.INCONCLUSIVE


def test_preceding_unconditional_assignment_refutes_nameerror():
    source = ("def run():\n    missing_findings: list = []\n"
              "    if missing_findings:\n        return 1")
    assert inspect_python(source, 3, RULE_PYTHON_UNDEFINED_NAME,
                          "missing_findings").status == InspectionStatus.COMPLIANT


@pytest.mark.parametrize("source,line", [
    ("def run(flag):\n    if flag:\n        value = 1\n    return value", 4),
    ("def run():\n    value = value + 1\n    return value", 2),
    ("def run():\n    value = 1\n    del value\n    return value", 4),
    ("def run():\n    return value\n    value = 1", 2),
    ("def run():\n    return value", 2),
    ("def run():\n    value = (\n        value + 1\n    )", 3),
    ("def run():\n    value = 1\n    try:\n        pass\n    except Exception as value:\n"
     "        pass\n    return value", 7),
    ("def run():\n    value = 1\n    exec('del value')\n    return value", 4),
])
def test_unresolved_name_paths_never_claim_compliance(source, line):
    assert inspect_python(source, line, RULE_PYTHON_UNDEFINED_NAME,
                          "value").status == InspectionStatus.INCONCLUSIVE


def test_import_target_and_conditional_scope():
    source = '"""說明"""\nfrom __future__ import annotations\nimport os\nVALUE = 1\nimport sys'
    assert inspect_python(source, 3, RULE_PYTHON_IMPORT_POSITION).status == (
        InspectionStatus.COMPLIANT
    )
    assert inspect_python(source, 5, RULE_PYTHON_IMPORT_POSITION).status == (
        InspectionStatus.VIOLATION
    )
    assert inspect_python(source, 4, RULE_PYTHON_IMPORT_POSITION).status == (
        InspectionStatus.INCONCLUSIVE
    )
    assert inspect_python("if TYPE_CHECKING:\n    import os", 2,
                          RULE_PYTHON_IMPORT_POSITION).status == InspectionStatus.INCONCLUSIVE


def package_inspect(source="requests==2.34.2", response=None, getter=None):
    snapshot = ReviewSnapshot([{"filename": "requirements.txt", "full_content": source}],
                              head_sha="head")
    get_fn = getter or MagicMock(return_value=response)
    return PackageVersionInspector(get_fn).inspect({"location": "requirements.txt:1"}, snapshot)


def valid_response(yanked=False):
    return MagicMock(status_code=200, json=lambda: {
        "info": {"name": "requests", "version": "2.34.2"},
        "urls": [{"filename": "requests-2.34.2.whl", "yanked": yanked}],
    })


@pytest.mark.parametrize("yanked", [False, True])
def test_version_exists_including_yanked_does_not_mean_safe(yanked):
    getter = MagicMock(return_value=valid_response(yanked))
    result = package_inspect(getter=getter)
    assert result.status == InspectionStatus.COMPLIANT
    assert "不代表" in result.verdict_reason
    assert '"yanked":' in result.verified_snippet
    getter.assert_called_once_with("https://pypi.org/pypi/requests/2.34.2/json",
                                   timeout=8, allow_redirects=False)


@pytest.mark.parametrize("status", [404, 429, 500, 302, 401])
def test_index_failure_is_not_proof_of_nonexistence(status):
    assert package_inspect(response=MagicMock(status_code=status)).status == (
        InspectionStatus.INCONCLUSIVE
    )


@pytest.mark.parametrize("source", [
    "requests>=2", "requests==2.34.2; python_version > '3.10'",
    "requests==2.34.2\n--index-url https://private.example/simple",
    "requests==2.34.2\n-r other.txt", "requests @ https://attacker.example/payload",
])
def test_non_pypi_or_ambiguous_dependencies_do_not_trigger_network(source):
    getter = MagicMock()
    assert package_inspect(source, getter=getter).status == InspectionStatus.INCONCLUSIVE
    getter.assert_not_called()


@pytest.mark.parametrize("payload", [
    None, {}, {"info": {"name": "requests", "version": "2.34.2"}, "urls": []},
    {"info": {"name": "other", "version": "2.34.2"},
     "urls": [{"filename": "x", "yanked": False}]},
])
def test_malformed_registry_payload_is_inconclusive(payload):
    response = MagicMock(status_code=200, json=lambda: payload)
    assert package_inspect(response=response).status == InspectionStatus.INCONCLUSIVE


def test_network_exception_and_missing_snapshot():
    assert package_inspect(getter=MagicMock(side_effect=requests.Timeout())).status == (
        InspectionStatus.INCONCLUSIVE
    )
    getter = MagicMock()
    assert PackageVersionInspector(getter).inspect(
        {"location": "requirements.txt:1"}, ReviewSnapshot([], head_sha="head"),
    ).status == InspectionStatus.INCONCLUSIVE
    getter.assert_not_called()


def test_registry_routes_and_splits_python_allegations():
    registry = RuleRegistry()
    claims = registry.split_finding_into_claims({
        "rule": "Python 規範", "problem": "函數重複定義且導入未置頂",
        "location": "app.py:1", "evidence": "def run():",
    })
    assert {claim.rule_key for claim in claims} == {
        RULE_PYTHON_DUPLICATE_DEFINITION, RULE_PYTHON_IMPORT_POSITION,
    }
    assert isinstance(registry.get_inspector("供應鏈", "requirements 套件版本不存在"),
                      PackageVersionInspector)


def test_real_reviewed_sources_refute_three_python_allegations():
    for filename, rule, name in (
        ("orchestrator.py", RULE_PYTHON_DUPLICATE_DEFINITION, "chat_completion"),
        ("batch_runner.py", RULE_PYTHON_UNDEFINED_NAME, "missing_findings"),
        ("orchestrator.py", RULE_PYTHON_IMPORT_POSITION, "NativeToolTransport"),
    ):
        source = (AI_REVIEW_DIR / filename).read_text(encoding="utf-8")
        tree = ast.parse(source)
        if rule == RULE_PYTHON_DUPLICATE_DEFINITION:
            lines = [node.lineno for node in ast.walk(tree)
                     if isinstance(node, ast.FunctionDef) and node.name == name]
            assert len(lines) == 2
        elif rule == RULE_PYTHON_UNDEFINED_NAME:
            lines = [node.lineno for node in ast.walk(tree) if isinstance(node, ast.If)
                     and isinstance(node.test, ast.Name) and node.test.id == name]
        else:
            lines = [node.lineno for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)
                     and any(alias.name == name for alias in node.names)]
        assert lines
        for line in lines:
            assert inspect_python(source, line, rule, name).status == InspectionStatus.COMPLIANT


def test_error_logging_keeps_safe_diagnostics_without_secret_message(caplog):
    from_error = requests.HTTPError("secret-token must not be logged")
    from_error.response = MagicMock(status_code=403)
    assert fetch_file_content_fallback("owner/repo", "app.py", "sha",
                                       MagicMock(side_effect=from_error)) is None
    assert "HTTPError" in caplog.text and "403" in caplog.text
    assert "secret-token" not in caplog.text
