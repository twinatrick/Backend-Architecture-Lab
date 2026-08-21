import sys
from pathlib import Path

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import static_checks


def test_check_python_except_pass_ast_detected():
    content = (
        "def safe_run():\n"
        "    try:\n"
        "        compute()\n"
        "    except ValueError:\n"
        "        pass\n"
    )
    patch = "@@ -1,5 +1,5 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/calc.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("錯誤處理與安全規範" in rule_text for rule_text in rules)
    assert any("禁止使用 except: pass" in finding_item["problem"] for finding_item in findings)


def test_check_python_function_level_import_detected():
    content = (
        "def parse_data(raw):\n"
        "    import json\n"
        "    return json.loads(raw)\n"
    )
    patch = "@@ -1,3 +1,3 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/parser.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("Import 置頂規範" in rule_text for rule_text in rules)
    assert any(
        "禁止在函式、類別、條件或迴圈等非頂層 scope 宣告 import" in finding_item["problem"]
        for finding_item in findings
    )


def test_check_python_nested_import_in_if_condition_detected():
    content = (
        "def run_job(flag: bool) -> None:\n"
        "    if flag:\n"
        "        import sys\n"
        "        sys.exit(0)\n"
    )
    patch = "@@ -1,4 +1,4 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/job.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("Import 置頂規範" in rule_text for rule_text in rules)
    assert any(
        "禁止在函式、類別、條件或迴圈等非頂層 scope 宣告 import" in finding_item["problem"]
        for finding_item in findings
    )


def test_check_python_top_level_import_not_flagged():
    content = (
        "import json\n"
        "import sys\n\n"
        "def parse_data(raw: str) -> dict:\n"
        "    return json.loads(raw)\n"
    )
    patch = "@@ -1,5 +1,5 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/parser.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    assert findings == []


def test_check_python_ast_unmodified_lines_ignored():
    full_content = (
        "def old_func():\n"
        "    try:\n"
        "        calc()\n"
        "    except ValueError:\n"
        "        pass\n"
        "def new_func() -> int:\n"
        "    return 42\n"
    )
    patch = (
        "@@ -6,2 +6,2 @@\n"
        "+def new_func() -> int:\n"
        "+    return 42\n"
    )
    files = [
        {
            "filename": "scripts/mixed.py",
            "patch": patch,
            "full_content": full_content,
        }
    ]
    findings = static_checks.run_static_checks(files)
    assert findings == []


def test_check_python_single_letter_variable_detected():
    content = (
        "def compute_total(price: int) -> int:\n"
        "    total_val = price * 2\n"
        "    var_x = total_val\n"
        "    x = var_x\n"
        "    return x\n"
    )
    patch = "@@ -1,5 +1,5 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/calc.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁止單字母變數規範" in rule_text for rule_text in rules)
    assert any("變數 'x' 使用單字母命名" in finding_item["problem"] for finding_item in findings)


def test_check_python_single_letter_parameter_detected():
    content = (
        "def transform(param_a: int) -> int:\n"
        "    a = param_a\n"
        "    return a + 1\n"
    )
    patch = "@@ -1,3 +1,3 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/transform.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("禁止單字母變數規範" in rule_text for rule_text in rules)
    assert any("變數 'a' 使用單字母命名" in finding_item["problem"] for finding_item in findings)


def test_check_python_loop_counter_i_and_underscore_allowed():
    content = (
        "def process_items(items: list[str]) -> list[str]:\n"
        "    results: list[str] = []\n"
        "    for i, item in enumerate(items):\n"
        "        results.append(f'{i}:{item}')\n"
        "    for _ in range(2):\n"
        "        results.append('pad')\n"
        "    return results\n"
    )
    patch = "@@ -1,7 +1,7 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/processor.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    single_letter_findings = [
        finding_item for finding_item in findings
        if "禁止單字母變數規範" in finding_item["rule"]
    ]
    assert single_letter_findings == []


def test_check_python_missing_type_hints_detected():
    content = (
        "def format_summary(title, count: int):\n"
        "    return f'{title}: {count}'\n"
    )
    patch = "@@ -1,2 +1,2 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/formatter.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [finding_item["rule"] for finding_item in findings]
    assert any("型別標註規範" in rule_text for rule_text in rules)
    assert any("參數 'title' 缺少型別標註" in finding_item["problem"] for finding_item in findings)
    assert any("缺少明確的回傳型別標註" in finding_item["problem"] for finding_item in findings)


def test_all_ai_review_python_files_compliance():
    violations = []
    for py_file in AI_REVIEW_DIR.rglob("*.py"):
        content = py_file.read_text(encoding="utf-8")
        lines = content.splitlines()
        if len(lines) > 300:
            violations.append(f"{py_file.name}: {len(lines)} lines (> 300)")
        for idx, line in enumerate(lines, 1):
            if len(line) > 100:
                violations.append(f"{py_file.name}:{idx} length {len(line)} (> 100)")
    assert violations == [], f"Python 規範違規：{violations}"
