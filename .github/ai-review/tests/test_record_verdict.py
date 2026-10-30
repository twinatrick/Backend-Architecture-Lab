import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import record_verdict


@pytest.fixture
def recorder(tmp_path, monkeypatch):
    event = {"action": "created", "issue": {"number": 1, "pull_request": {}},
             "comment": {"id": 123}}
    event["issue"]["pull_request"] = {"url": "https://api.github.com/repos/owner/repo/pulls/1"}
    path = tmp_path / "event.json"
    monkeypatch.setenv("GITHUB_EVENT_PATH", str(path))
    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo")
    monkeypatch.setenv("AI_REVIEW_LEDGER_WRITE_TOKEN", "local-write-token")
    monkeypatch.setenv("AI_REVIEW_VERDICT_LEDGER_WRITER", "writer")
    comment = {"id": 123, "created_at": "2026-10-30T00:00:00Z",
               "updated_at": "2026-10-30T00:00:00Z",
               "issue_url": "https://api.github.com/repos/owner/repo/issues/1"}
    getter = MagicMock(return_value=comment)
    append = MagicMock(return_value=1)
    monkeypatch.setattr(record_verdict, "gh_get", getter)
    monkeypatch.setattr(record_verdict, "append_comment_verdicts", append)
    return path, event, comment, getter, append


def execute(recorder):
    path, event, *_ = recorder
    path.write_text(json.dumps(event), encoding="utf-8")
    record_verdict.main()


def test_recorder_refetches_comment_and_write_client_uses_only_write_token(
    recorder, monkeypatch, capsys,
):
    response = MagicMock()
    put = MagicMock(return_value=response)
    monkeypatch.setattr(record_verdict.requests, "put", put)
    monkeypatch.setenv("AI_REVIEW_LEDGER_READ_TOKEN", "local-read-token")

    def append(repo, number, comment, getter, putter, writer):
        assert (repo, number, comment, getter, writer) == (
            "owner/repo", 1, recorder[2], recorder[3], "writer",
        )
        putter("https://api.github.com/repos/owner/repo/contents/verdicts/pr-1.jsonl", {})
        return 1

    recorder[4].side_effect = append
    execute(recorder)
    recorder[3].assert_called_once_with(
        "https://api.github.com/repos/owner/repo/issues/comments/123",
    )
    assert put.call_args.kwargs["headers"]["Authorization"] == "Bearer local-write-token"
    response.raise_for_status.assert_called_once()
    assert "local-write-token" not in capsys.readouterr().out


@pytest.mark.parametrize("action", ["edited", "deleted", "opened", None])
def test_recorder_rejects_other_events_before_api(recorder, action):
    recorder[1]["action"] = action
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[3].assert_not_called()
    recorder[4].assert_not_called()


@pytest.mark.parametrize("field,value", [
    ("number", True), ("number", 0), ("number", -1), ("number", "1"),
    ("id", True), ("id", 0), ("id", -1), ("id", "123"),
])
def test_recorder_identifiers_are_strict_positive_integers(recorder, field, value):
    parent = recorder[1]["issue"] if field == "number" else recorder[1]["comment"]
    parent[field] = value
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[4].assert_not_called()


@pytest.mark.parametrize("change", [
    {"updated_at": "2026-10-31T00:00:00Z"},
    {"created_at": None, "updated_at": None},
    {"created_at": "", "updated_at": ""},
    {"id": 999}, {"issue_url": "https://api.github.com/repos/owner/repo/issues/2"},
    {"created_at": "invalid", "updated_at": "invalid"},
    {"created_at": "2026-10-30T00:00:00", "updated_at": "2026-10-30T00:00:00"},
])
def test_recorder_refetched_comment_must_match_unedited_event(recorder, change):
    recorder[2].update(change)
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[4].assert_not_called()


@pytest.mark.parametrize("name", [
    "AI_REVIEW_LEDGER_WRITE_TOKEN", "AI_REVIEW_VERDICT_LEDGER_WRITER",
])
def test_recorder_empty_write_configuration_never_appends(recorder, monkeypatch, name):
    monkeypatch.setenv(name, "")
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[4].assert_not_called()


def test_write_http_failure_propagates_without_success_message(recorder, monkeypatch, capsys):
    response = MagicMock()
    response.raise_for_status.side_effect = requests.HTTPError("local failure")
    monkeypatch.setattr(record_verdict.requests, "put", MagicMock(return_value=response))

    def append(repo, number, comment, getter, putter, writer):
        putter("https://api.github.com/repos/owner/repo/contents/verdicts/pr-1.jsonl", {})
        return 1

    recorder[4].side_effect = append
    with pytest.raises(requests.HTTPError):
        execute(recorder)
    assert capsys.readouterr().out == ""


@pytest.mark.parametrize("event", [[], None, {}, {"action": "created", "issue": None},
                                  {"action": "created", "issue": {"number": 1}}])
def test_recorder_malformed_or_non_pr_event_never_calls_api(recorder, event):
    recorder[0].write_text(json.dumps(event), encoding="utf-8")
    with pytest.raises(ValueError):
        record_verdict.main()
    recorder[3].assert_not_called()
    recorder[4].assert_not_called()


@pytest.mark.parametrize("comment", [None, [], {}, {"id": True}])
def test_recorder_malformed_api_response_never_appends(recorder, comment):
    recorder[3].return_value = comment
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[4].assert_not_called()


def test_recorder_rejects_invalid_repository_before_api(recorder, monkeypatch):
    monkeypatch.setenv("GITHUB_REPOSITORY", "owner/repo?ref=other")
    with pytest.raises(ValueError):
        execute(recorder)
    recorder[3].assert_not_called()
