import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import groq_runner
from parser import ReviewResponseParser

KEY = "gsk_local_failure_test_key_0123456789abcdefgh"
VALID = '{"files_reviewed":["A.py"],"findings":[],"coverage":"COMPLETE"}'


def response(status=200, text="", payload=None, retry_after="1"):
    item = MagicMock(ok=status == 200, status_code=status, text=text)
    item.headers = {"retry-after": retry_after}
    item.json.return_value = payload if payload is not None else {
        "choices": [{"message": {"content": VALID}}],
    }
    return item


@pytest.fixture
def runner(monkeypatch):
    monkeypatch.setattr(groq_runner.time, "sleep", MagicMock())
    pool = MagicMock()
    pool.get_all_keys.return_value = [("KEY", KEY)]
    pool.acquire_key.side_effect = lambda keys, excluded_keys, model: (
        None if KEY in excluded_keys else ("KEY", KEY)
    )
    pool.get_active_keys.return_value = []
    return pool, MagicMock(), MagicMock(), []


def execute(runner, replies, *, models=None, retries=2, limit=4):
    pool, models_pool, client, errors = runner
    client.call.side_effect = replies
    return groq_runner.execute_groq_loop(
        "prompt", retries, models or ["model"], pool, models_pool, client,
        ReviewResponseParser(), errors, max_429_retries=limit,
    )


def test_no_keys_does_not_call_provider(runner):
    runner[0].get_all_keys.return_value = []
    assert execute(runner, []) is None
    runner[2].call.assert_not_called()


def test_success_promotes_and_releases_acquired_key(runner):
    assert execute(runner, [response()]) == VALID
    runner[1].promote.assert_called_once_with("model")
    runner[0].release_key.assert_called_once_with(KEY)


@pytest.mark.parametrize("payload", [{}, {"choices": []}, {"choices": None},
                                     {"choices": [{"message": {"content": None}}]}])
def test_malformed_success_response_retries_and_releases_key(runner, payload):
    assert execute(runner, [response(payload=payload), response()]) == VALID
    assert runner[2].call.call_count == 2
    runner[0].release_key.assert_called_once_with(KEY)


def test_invalid_json_exhaustion_demotes_and_releases(runner):
    invalid = response(payload={"choices": [{"message": {"content": "not json"}}]})
    assert execute(runner, [invalid, invalid]) is None
    runner[1].demote.assert_called_once_with("model")
    assert len(runner[3]) == 2
    runner[0].release_key.assert_called_once_with(KEY)


@pytest.mark.parametrize("status", [400, 413, 500, 502, 401, 403])
def test_http_errors_do_not_log_full_key(runner, capsys, status):
    assert execute(runner, [response(status, "provider echoed " + KEY)], retries=1) is None
    assert KEY not in capsys.readouterr().out
    assert KEY not in json.dumps(runner[3])
    runner[0].release_key.assert_called_once_with(KEY)


def test_connection_failure_retries_without_leaking_credentials(runner, capsys):
    assert execute(runner, [requests.Timeout("request token=" + KEY), response()]) == VALID
    assert KEY not in capsys.readouterr().out
    assert KEY not in json.dumps(runner[3])
    runner[0].release_key.assert_called_once_with(KEY)


def test_json_mode_failure_retries_using_text_mode(runner):
    assert execute(runner, [response(400, "json_validate_failed"), response()]) == VALID
    assert [call.kwargs["use_json_mode"] for call in runner[2].call.call_args_list] == [True, False]


def test_transient_500_retries_same_key_and_promotes_on_success(runner):
    assert execute(runner, [response(500, "temporary"), response()]) == VALID
    assert runner[2].call.call_count == 2


@pytest.mark.parametrize("text,retry_after", [("quota exceeded", "200"), ("rate limit", "1")])
def test_429_budget_applies_to_quota_and_all_candidate_models(runner, text, retry_after):
    assert execute(runner, [response(429, text, retry_after=retry_after)] * 3,
                   models=["first", "second", "third"], limit=1) is None
    assert runner[2].call.call_count == 1
    runner[0].mark_cooldown.assert_called_once()


def test_temporary_429_retries_within_budget(runner):
    assert execute(runner, [response(429, "temporary"), response()]) == VALID
    assert runner[2].call.call_count == 2


def test_timeout_exhaustion_releases_key(runner):
    assert execute(runner, [requests.Timeout(), requests.Timeout()]) is None
    runner[0].release_key.assert_called_once_with(KEY)


def test_invalid_response_json_retries_before_fallback(runner):
    invalid = response()
    invalid.json.side_effect = ValueError("malformed provider payload")
    assert execute(runner, [invalid, response()]) == VALID


def test_secret_is_redacted_before_error_body_truncation(runner, capsys):
    assert execute(runner, [response(500, "x" * 290 + KEY)], retries=1) is None
    assert KEY[:15] not in capsys.readouterr().out
    assert KEY[:15] not in json.dumps(runner[3])


@pytest.mark.parametrize("status,text", [(401, "invalid key"), (429, "quota exhausted")])
def test_provider_rotates_keys_and_releases_each_attempt(runner, status, text):
    pool = runner[0]
    second_key = "gsk_second_fake_test_key_0123456789abcdefgh"
    pool.get_all_keys.return_value = [("FIRST", KEY), ("SECOND", second_key)]
    pool.acquire_key.side_effect = lambda keys, excluded_keys, model: next(
        (item for item in keys if item[1] not in excluded_keys), None,
    )
    pool.get_active_keys.return_value = [("SECOND", second_key)]
    assert execute(runner, [response(status, text), response()]) == VALID
    assert [call.args[0] for call in pool.release_key.call_args_list] == [KEY, second_key]
    cooldown = pool.mark_cooldown.call_args
    if status == 401:
        assert cooldown.args == (KEY, 86400.0)
        assert cooldown.kwargs == {}
    else:
        assert cooldown.kwargs == {"model": "model"}


def test_large_payload_demotes_then_uses_next_model(runner):
    assert execute(runner, [response(413), response()], models=["first", "second"]) == VALID
    assert [call.args[1] for call in runner[2].call.call_args_list] == ["first", "second"]
    runner[1].demote.assert_called_once_with("first")
