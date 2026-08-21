import json
import sys
from pathlib import Path

import pytest

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import parser as review_parser


def test_extract_json_payload_clean_json():
    raw = (
        '{"batch": "ci-1", "coverage": "COMPLETE", '
        '"files_reviewed": ["test.java"], "findings": []}'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_think_tag():
    raw = """<think>
Here's a thinking process:
1. Review the diff.
2. Formulate JSON.
</think>
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}"""
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_markdown_codeblock():
    raw = """```json
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}
```"""
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_with_think_and_surrounding_prose():
    raw = """<think>
Analysis steps...
</think>
Here is the review result:
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["test.java"], "findings": []}
Hope this helps!"""
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["coverage"] == "COMPLETE"


def test_extract_json_payload_invalid_json_raises():
    raw = "<think>thinking</think>Not a json at all"
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload(raw)


def test_extract_json_payload_preserves_string_literals_with_commas_and_brackets():
    raw = (
        '{"batch": "ci-1", "files_reviewed": ["a.py"], '
        '"findings": [{"evidence": "items = [1,];", "problem": "issue with mapping,}"}]}'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    assert parsed["findings"][0]["evidence"] == "items = [1,];"
    assert parsed["findings"][0]["problem"] == "issue with mapping,}"


def test_extract_json_payload_preserves_newlines_tabs_and_code_snippets():
    raw = (
        '{\n'
        '  "batch": "business-1",\n'
        '  "files_reviewed": ["Service.java"],\n'
        '  "findings": [{\n'
        '    "severity": "HIGH",\n'
        '    "confidence": "HIGH",\n'
        '    "location": "Service.java:42",\n'
        '    "rule": "SOLID 原則",\n'
        '    "problem": "多行問題說明\\n第二行說明\\t含 Tab",\n'
        '    "evidence": "if (a) {\\n\\treturn 1;\\n}",\n'
        '    "risk": "架構風險",\n'
        '    "recommendation": "重構為介面注入"\n'
        '  }],\n'
        '  "passed_checks": ["SOLID"],\n'
        '  "coverage": "COMPLETE"\n'
        '}'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "business-1"
    finding = parsed["findings"][0]
    assert finding["problem"] == "多行問題說明\n第二行說明\t含 Tab"
    assert finding["evidence"] == "if (a) {\n\treturn 1;\n}"


def test_extract_json_payload_invalid_trailing_comma_raises_decode_error():
    raw = '{"batch": "ci-1", "files_reviewed": ["a.py", "b.py",], "findings": [],}'
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload(raw)


def test_repair_json_string_with_unclosed_think_tag():
    raw = """<think>
Some thinking that got truncated before closing tag
{"batch": "ci-1", "coverage": "COMPLETE", "files_reviewed": ["a.py"], "findings": []}"""
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"


def test_repair_json_string_with_embedded_markdown_codeblock():
    raw = """Below is the review result in JSON format:
```json
{
  "batch": "ci-1",
  "coverage": "COMPLETE",
  "files_reviewed": ["a.py"],
  "findings": []
}
```
End of review."""
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"


def test_repair_json_string_with_prose_containing_brackets_before_payload():
    raw = (
        'Here is a note with an example: {"example_key": "val"}.\n'
        'Below is the actual review output:\n'
        '{"batch": "ci-actual", "coverage": "COMPLETE", '
        '"files_reviewed": ["b.py"], "findings": []}'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-actual"


def test_repair_json_string_with_nested_brackets_and_strings():
    raw = (
        'Explanation: {\n'
        '  "batch": "nested-1",\n'
        '  "files_reviewed": ["c.java"],\n'
        '  "findings": [{\n'
        '    "severity": "HIGH",\n'
        '    "evidence": "void fn() { map.put(\\"key\\", new Object() {}); }",\n'
        '    "problem": "nested braces in {string}"\n'
        '  }]\n'
        '}\n'
        'End of output'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "nested-1"
    assert parsed["findings"][0]["evidence"] == 'void fn() { map.put("key", new Object() {}); }'


def test_extract_json_payload_rejects_non_dict():
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload('["item1", "item2"]')
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload('"just a string"')
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload('12345')


def test_extract_json_payload_rejects_conflicting_multiple_review_payloads():
    raw = (
        '{"batch": "ci-1", "files_reviewed": ["a.py"], "findings": []}\n'
        '{"batch": "ci-2", "files_reviewed": ["b.py"], "findings": []}'
    )
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload(raw)


def test_extract_json_payload_rejects_unparseable_balanced_candidates():
    raw = "Some invalid candidates { invalid: json } without quotes."
    with pytest.raises(json.JSONDecodeError):
        review_parser.extract_json_payload(raw)


def test_extract_json_payload_autofills_missing_category():
    raw = (
        '{"batch": "ci-1", "files_reviewed": ["a.py"], '
        '"findings": [{"severity": "HIGH", "confidence": "HIGH", '
        '"location": "a.py:10", "rule": "SQL 注入與安全性規範", '
        '"problem": "潛在注入風險", "evidence": "query()", '
        '"risk": "資料洩漏", "recommendation": "改用參數化查詢"}]}'
    )
    parsed = review_parser.extract_json_payload(raw)
    assert parsed["batch"] == "ci-1"
    finding = parsed["findings"][0]
    assert finding.get("category") == "SECURITY"

