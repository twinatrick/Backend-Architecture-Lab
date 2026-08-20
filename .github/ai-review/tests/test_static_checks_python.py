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
    rules = [f["rule"] for f in findings]
    assert any("錯誤處理與安全規範" in r for r in rules)
    assert any("禁止使用 except: pass" in f["problem"] for f in findings)


def test_check_python_function_level_import_detected():
    content = (
        "def parse_data(raw):\n"
        "    import json\n"
        "    return json.loads(raw)\n"
    )
    patch = "@@ -1,3 +1,3 @@\n" + "".join(f"+{line}" for line in content.splitlines(True))
    files = [{"filename": "scripts/parser.py", "patch": patch, "full_content": content}]
    findings = static_checks.run_static_checks(files)
    rules = [f["rule"] for f in findings]
    assert any("Import 置頂規範" in r for r in rules)
    assert any("禁止在函式或方法內部宣告 import" in f["problem"] for f in findings)


def test_check_python_top_level_import_not_flagged():
    content = (
        "import json\n"
        "import sys\n\n"
        "def parse_data(raw):\n"
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
        "def new_func():\n"
        "    return 42\n"
    )
    patch = (
        "@@ -6,2 +6,2 @@\n"
        "+def new_func():\n"
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
