import os
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import review

MOCK_BATCH_APPROVED = (
    '{"batch":"business-1","files_reviewed":["App.java"],'
    '"findings":[],"passed_checks":[],"coverage":"COMPLETE"}'
)


def test_fetch_file_content_fallback_success(monkeypatch):
    mock_getter = MagicMock(return_value={
        "encoding": "base64",
        "content": "cHJpbnQoJ2hlbGxvIHdvcmxkJyk=",  # print('hello world')
    })
    monkeypatch.setattr(review, "gh_get", mock_getter)
    content = review._fetch_file_content_fallback("owner/repo", "test.py", "sha123")
    assert content == "print('hello world')"
    assert mock_getter.called


def test_fetch_file_content_fallback_failure(monkeypatch):
    mock_getter = MagicMock(return_value=None)
    monkeypatch.setattr(review, "gh_get", mock_getter)
    content = review._fetch_file_content_fallback("owner/repo", "test.py", "sha123")
    assert content is None


def test_process_batch_with_llm_response(monkeypatch):
    monkeypatch.setattr(
        review, "chat_completion",
        MagicMock(return_value="""```json
{
  "batch": "business-1",
  "files_reviewed": ["App.java"],
  "findings": [
    {
      "location": "App.java:10", "category": "Functional Correctness",
      "rule": "RULE-01", "problem": "Bad logic", "evidence": "if (true)",
      "risk": "Bug", "recommendation": "Fix", "severity": "HIGH", "confidence": "HIGH"
    }
  ],
  "passed_checks": ["Check 1"],
  "coverage": "COMPLETE"
}
```"""),
    )
    files = [{"filename": "App.java", "patch": "+ if (true) return;"}]
    policy = {"blocking_severities": ["CRITICAL", "HIGH"], "blocking_confidence": ["HIGH"]}
    result = review._process_batch(
        scope="business", index=1, paths=["App.java"], files=files,
        rules_text="## 1. 規則", contract_text="contract", policy=policy, pr_number=42,
    )
    assert len(result["findings"]) == 1
    assert result["findings"][0]["location"] == "App.java:10"
    assert result["findings"][0]["severity"] == "HIGH"


def test_main_with_static_checks_fail_closed(monkeypatch, tmp_path):
    event_file = tmp_path / "event.json"
    event_file.write_text('{"pull_request": {"number": 123}}', encoding="utf-8")

    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("GH_TOKEN", "mock_token")
    monkeypatch.setenv("GROQ_API_KEY", "gsk_test123456789012345678901234")
    monkeypatch.setenv("GITHUB_EVENT_PATH", str(event_file))

    mock_target_pr = {
        "state": "open",
        "base": {"ref": "master", "repo": {"full_name": "owner/repo"}},
        "head": {"sha": "sha123"},
    }
    mock_pr_files = [{
        "filename": ".github/workflows/bad.yml",
        "patch": "@@ -0,0 +1,5 @@\n+on: pull_request_target\n+jobs:\n+  build:\n",
    }]

    def mock_gh_get(url, params=None):
        if "/pulls/123/files" in url:
            return mock_pr_files
        if "/pulls/123" in url:
            return mock_target_pr
        return {}

    monkeypatch.setattr(review, "gh_get", mock_gh_get)
    monkeypatch.setattr(
        review, "chat_completion",
        MagicMock(return_value=(
            '{"batch":"ci-1","files_reviewed":[".github/workflows/bad.yml"],'
            '"findings":[],"passed_checks":[],"coverage":"COMPLETE"}'
        )),
    )
    mock_publish = MagicMock()
    monkeypatch.setattr(review, "publish_review", mock_publish)

    with pytest.raises(SystemExit) as exc_info:
        review.main()
    assert exc_info.value.code == 1
    assert mock_publish.called
    assert mock_publish.call_args[0][2] == "HUMAN_REVIEW_REQUIRED"


def test_main_toctou_head_sha_mismatch_raises_system_exit(monkeypatch, tmp_path):
    event_file = tmp_path / "event.json"
    event_file.write_text('{"pull_request": {"number": 123}}', encoding="utf-8")

    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("GH_TOKEN", "mock_token")
    monkeypatch.setenv("GROQ_API_KEY", "gsk_test123456789012345678901234")
    monkeypatch.setenv("GITHUB_EVENT_PATH", str(event_file))

    mock_pr_initial = {
        "state": "open", "base": {"ref": "master", "repo": {"full_name": "owner/repo"}},
        "head": {"sha": "sha_old"},
    }
    mock_pr_latest = {
        "state": "open", "base": {"ref": "master", "repo": {"full_name": "owner/repo"}},
        "head": {"sha": "sha_new"},
    }
    mock_pr_files = [{"filename": "App.java", "patch": "+ class App {}"}]
    call_count = {"pull": 0}

    def mock_gh_get(url, params=None):
        if "/pulls/123/files" in url:
            return mock_pr_files
        if "/pulls/123" in url:
            call_count["pull"] += 1
            return mock_pr_initial if call_count["pull"] == 1 else mock_pr_latest
        return {}

    monkeypatch.setattr(review, "gh_get", mock_gh_get)
    monkeypatch.setattr(review, "chat_completion", MagicMock(return_value=MOCK_BATCH_APPROVED))
    mock_publish = MagicMock()
    monkeypatch.setattr(review, "publish_review", mock_publish)

    with pytest.raises(SystemExit) as exc_info:
        review.main()
    assert "TOCTOU" in str(exc_info.value) or exc_info.value.code != 0


def test_main_toctou_network_error_raises_system_exit(monkeypatch, tmp_path):
    event_file = tmp_path / "event.json"
    event_file.write_text('{"pull_request": {"number": 123}}', encoding="utf-8")

    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("GH_TOKEN", "mock_token")
    monkeypatch.setenv("GROQ_API_KEY", "gsk_test123456789012345678901234")
    monkeypatch.setenv("GITHUB_EVENT_PATH", str(event_file))

    mock_pr_initial = {
        "state": "open", "base": {"ref": "master", "repo": {"full_name": "owner/repo"}},
        "head": {"sha": "sha_old"},
    }
    mock_pr_files = [{"filename": "App.java", "patch": "+ class App {}"}]
    call_count = {"pull": 0}

    def mock_gh_get(url, params=None):
        if "/pulls/123/files" in url:
            return mock_pr_files
        if "/pulls/123" in url:
            call_count["pull"] += 1
            if call_count["pull"] == 1:
                return mock_pr_initial
            raise requests.RequestException("GitHub API 500 Server Error")
        return {}

    monkeypatch.setattr(review, "gh_get", mock_gh_get)
    monkeypatch.setattr(review, "chat_completion", MagicMock(return_value=MOCK_BATCH_APPROVED))
    mock_publish = MagicMock()
    monkeypatch.setattr(review, "publish_review", mock_publish)

    with pytest.raises(SystemExit) as exc_info:
        review.main()
    assert exc_info.value.code != 0


def test_process_batch_finding_outside_allowed_files_raises_system_exit(monkeypatch):
    monkeypatch.setattr(
        review, "chat_completion",
        MagicMock(return_value="""```json
{
  "batch": "business-1", "files_reviewed": ["App.java"],
  "findings": [
    {
      "location": "OtherFile.java:10", "category": "ARCHITECTURE",
      "rule": "RULE-01", "problem": "Bad logic", "evidence": "if (true)",
      "risk": "Bug", "recommendation": "Fix", "severity": "HIGH", "confidence": "HIGH"
    }
  ],
  "passed_checks": ["Check 1"], "coverage": "COMPLETE"
}
```"""),
    )
    files = [{"filename": "App.java", "patch": "+ if (true) return;"}]
    policy = {"blocking_severities": ["CRITICAL", "HIGH"], "blocking_confidence": ["HIGH"]}
    with pytest.raises(SystemExit) as exc_info:
        review._process_batch(
            scope="business", index=1, paths=["App.java"], files=files,
            rules_text="## 1. 規則", contract_text="contract", policy=policy, pr_number=42,
        )
    assert "未通過格式驗證" in str(exc_info.value) or exc_info.value.code != 0


def test_main_with_pr_metadata_artifact(monkeypatch, tmp_path):
    event_file = tmp_path / "event.json"
    event_file.write_text(
        '{"action": "completed", "workflow_run": {"head_sha": "sha_meta"}}', encoding="utf-8"
    )
    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("GH_TOKEN", "mock_token")
    monkeypatch.setenv("GROQ_API_KEY", "gsk_test123456789012345678901234")
    monkeypatch.setenv("GITHUB_EVENT_PATH", str(event_file))

    monkeypatch.setattr(
        "github_client.load_pr_metadata",
        lambda metadata_path=None: {
            "pr_number": 62, "head_sha": "sha_meta", "repo": "owner/repo", "actor": "twinatrick"
        },
    )

    mock_target_pr = {
        "state": "open", "base": {"ref": "master", "repo": {"full_name": "owner/repo"}},
        "head": {"sha": "sha_meta"},
    }
    mock_pr_files = [{"filename": "App.java", "patch": "+ class App {}"}]

    def mock_gh_get(url, params=None):
        if "/pulls/62/files" in url:
            return mock_pr_files
        if "/pulls/62" in url:
            return mock_target_pr
        return {}

    monkeypatch.setattr(review, "gh_get", mock_gh_get)
    monkeypatch.setattr(review, "chat_completion", MagicMock(return_value=MOCK_BATCH_APPROVED))
    mock_publish = MagicMock()
    monkeypatch.setattr(review, "publish_review", mock_publish)

    review.main()
    assert mock_publish.called
    call_args = mock_publish.call_args[0]
    assert call_args[0] == 62
    assert call_args[2] == "APPROVE"
