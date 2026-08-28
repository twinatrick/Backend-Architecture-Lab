import sys
import time
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import batch_runner


def test_fetch_file_content_fallback_success():
    mock_gh = MagicMock(return_value={
        "encoding": "base64",
        "content": "SGVsbG8gV29ybGQ=",  # "Hello World"
    })
    result = batch_runner.fetch_file_content_fallback(
        "owner/repo", "src/Main.java", "abc1234", gh_get_fn=mock_gh
    )
    assert result == "Hello World"
    mock_gh.assert_called_once_with(
        "https://api.github.com/repos/owner/repo/contents/src/Main.java",
        params={"ref": "abc1234"},
    )


def test_fetch_file_content_fallback_failure():
    mock_gh = MagicMock(side_effect=requests.RequestException("API rate limited"))
    result = batch_runner.fetch_file_content_fallback(
        "owner/repo", "src/Main.java", gh_get_fn=mock_gh
    )
    assert result is None


def test_enrich_files_with_full_content():
    files = [
        {"filename": "src/A.java", "status": "modified"},
        {"filename": "src/B.java", "status": "deleted"},
        {"filename": "src/C.java", "status": "added", "full_content": "existing"},
    ]
    mock_gh = MagicMock(return_value={
        "encoding": "base64",
        "content": "VXBkYXRlZA==",  # "Updated"
    })
    batch_runner.enrich_files_with_full_content(
        "owner/repo", files, "commit_sha", gh_get_fn=mock_gh
    )
    assert files[0].get("full_content") == "Updated"
    assert "full_content" not in files[1]
    assert files[2].get("full_content") == "existing"


def test_fetch_all_pr_files():
    mock_gh = MagicMock()
    mock_gh.side_effect = [
        [{"filename": f"file_{idx}.py"} for idx in range(100)],
        [{"filename": "file_100.py"}],
    ]
    all_files = batch_runner.fetch_all_pr_files("owner/repo", 123, gh_get_fn=mock_gh)
    assert len(all_files) == 101
    assert mock_gh.call_count == 2


def test_process_batch_success():
    files = [{"filename": "src/Foo.java", "patch": "@@ -1 +1 @@\n+class Foo {}"}]
    mock_chat = MagicMock(return_value="""```json
    {
      "files_reviewed": ["src/Foo.java"],
      "coverage": "COMPLETE",
      "findings": [],
      "passed_checks": ["SOLID"]
    }
    ```""")
    res = batch_runner.process_batch(
        "business",
        1,
        ["src/Foo.java"],
        files,
        "rules",
        "contract",
        {},
        123,
        chat_fn=mock_chat,
    )
    assert res["coverage"] == "COMPLETE"
    assert res["files_reviewed"] == ["src/Foo.java"]
    assert res["passed_checks"] == ["SOLID"]


def test_process_batch_missing_patch_fallback_success():
    files = [{"filename": "src/Foo.java", "status": "modified"}]
    mock_gh = MagicMock(return_value={
        "encoding": "base64",
        "content": "Y2xhc3MgRm9vIHt9",  # "class Foo {}"
    })
    mock_chat = MagicMock(return_value="""```json
    {
      "files_reviewed": ["src/Foo.java"],
      "coverage": "COMPLETE",
      "findings": []
    }
    ```""")
    res = batch_runner.process_batch(
        "business",
        1,
        ["src/Foo.java"],
        files,
        "rules",
        "contract",
        {},
        123,
        chat_fn=mock_chat,
        gh_get_fn=mock_gh,
    )
    assert res["files_reviewed"] == ["src/Foo.java"]


def test_process_batch_missing_patch_fallback_failure_adds_finding():
    files = [{"filename": "src/Foo.java", "status": "modified"}]
    mock_gh = MagicMock(side_effect=requests.RequestException("Contents 404"))
    mock_chat = MagicMock(return_value="""```json
    {
      "files_reviewed": ["src/Foo.java"],
      "coverage": "COMPLETE",
      "findings": []
    }
    ```""")
    res = batch_runner.process_batch(
        "business",
        1,
        ["src/Foo.java"],
        files,
        "rules",
        "contract",
        {},
        123,
        chat_fn=mock_chat,
        gh_get_fn=mock_gh,
    )
    findings = res.get("findings", [])
    assert len(findings) == 1
    assert "無法取得檔案 src/Foo.java 的 Patch 或原始內容" in findings[0]["problem"]


def test_process_batches_parallel_stagger_and_ordering():
    batches = [
        ("scope-a", ["file_a.py"]),
        ("scope-b", ["file_b.py"]),
        ("scope-c", ["file_c.py"]),
    ]
    files = [
        {"filename": "file_a.py", "patch": "+a"},
        {"filename": "file_b.py", "patch": "+b"},
        {"filename": "file_c.py", "patch": "+c"},
    ]

    def _mock_chat(prompt: str) -> str:
        current_file = "file_a.py" if "file_a.py" in prompt else (
            "file_b.py" if "file_b.py" in prompt else "file_c.py"
        )
        return f"""```json
        {{
          "files_reviewed": ["{current_file}"],
          "coverage": "COMPLETE",
          "findings": []
        }}
        ```"""

    start_time = time.time()
    results = batch_runner.process_batches_parallel(
        batches,
        files,
        "rules",
        "contract",
        {},
        123,
        max_workers=3,
        stagger_seconds=0.1,
        chat_fn=_mock_chat,
    )
    elapsed = time.time() - start_time

    assert len(results) == 3
    assert results[0]["files_reviewed"] == ["file_a.py"]
    assert results[1]["files_reviewed"] == ["file_b.py"]
    assert results[2]["files_reviewed"] == ["file_c.py"]
    assert elapsed >= 0.2  # 3 個 worker 錯峰 0.1s 累加
