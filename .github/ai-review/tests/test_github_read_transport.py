import base64
import json
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import github_client
import github_read_transport as transport_module
from github_content import fetch_file_content_fallback
from github_read_transport import GitHubReadTransport
from review_snapshot import ReviewSnapshot
from snapshot_static_checks import SnapshotRepositorySearch

SHA = "a" * 40
BASE = "b" * 40
URL = "https://api.github.com/repos/owner/repo/contents/Foo.java"


def response(status=200, *, headers=None, data=None):
    result = MagicMock(status_code=status, headers=headers or {})
    result.json.return_value = data if data is not None else {"value": []}
    result.content = json.dumps(result.json.return_value).encode()
    return result


def test_immutable_cache_is_shared_across_snapshots_and_cannot_be_mutated(monkeypatch):
    getter = MagicMock(return_value=response())
    monkeypatch.setattr(transport_module.requests, "get", getter)
    transport = GitHubReadTransport()
    first = transport.get(URL, {}, {"ref": SHA})
    first["value"].append("corruption")
    assert transport.get(URL, {}, {"ref": SHA}) == {"value": []}
    assert getter.call_count == 1
    transport.get(URL, {}, {"ref": BASE})
    transport.get(URL.replace("owner/repo", "other/repo"), {}, {"ref": SHA})
    transport.get(URL, {"Authorization": "Bearer fake-test-token"}, {"ref": SHA})
    assert getter.call_count == 4


@pytest.mark.parametrize("url,params", [
    (URL, {"ref": "master"}), (URL, {}),
    ("https://api.github.com/repos/owner/repo/pulls/188", {}),
    (URL + "?ref=master", {"ref": SHA}),
])
def test_mutable_metadata_and_branch_refs_are_never_cached(monkeypatch, url, params):
    getter = MagicMock(side_effect=[response(data={"n": 1}), response(data={"n": 2})])
    monkeypatch.setattr(transport_module.requests, "get", getter)
    transport = GitHubReadTransport()
    assert transport.get(url, {}, params)["n"] == 1
    assert transport.get(url, {}, params)["n"] == 2


def test_fixed_tree_cached_but_query_parameters_are_distinct(monkeypatch):
    getter = MagicMock(return_value=response())
    monkeypatch.setattr(transport_module.requests, "get", getter)
    transport = GitHubReadTransport()
    url = f"https://api.github.com/repos/owner/repo/git/trees/{SHA}"
    for recursive in ("1", "1", "0"):
        transport.get(url, {}, {"recursive": recursive})
    assert getter.call_count == 2


@pytest.mark.parametrize("status,headers", [
    (429, {"Retry-After": "0"}), (403, {"Retry-After": "1"}),
    (403, {"X-RateLimit-Remaining": "0", "X-RateLimit-Reset": "101"}),
])
def test_only_classified_short_rate_limits_get_one_retry(monkeypatch, status, headers):
    getter = MagicMock(side_effect=[response(status, headers=headers), response()])
    sleep = MagicMock()
    monkeypatch.setattr(transport_module.requests, "get", getter)
    monkeypatch.setattr(transport_module.time, "sleep", sleep)
    monkeypatch.setattr(transport_module.time, "time", lambda: 100)
    assert GitHubReadTransport().get(URL, {}) == {"value": []}
    assert getter.call_count == 2
    sleep.assert_called_once()


@pytest.mark.parametrize("status,headers,attempts", [
    (401, {}, 1), (403, {}, 1),
    (403, {"X-RateLimit-Remaining": "0"}, 1),
    (429, {"Retry-After": "60"}, 1), (429, {"Retry-After": "0"}, 2),
    (429, {}, 1),
])
def test_terminal_auth_or_limit_stops_request_storm(monkeypatch, status, headers, attempts):
    getter = MagicMock(return_value=response(status, headers=headers))
    monkeypatch.setattr(transport_module.requests, "get", getter)
    monkeypatch.setattr(transport_module.time, "sleep", lambda seconds: None)
    transport = GitHubReadTransport()
    for path in (URL, URL + "Other", URL + "Third"):
        with pytest.raises(requests.HTTPError):
            transport.get(path, {})
    assert getter.call_count == attempts


def test_diagnostics_do_not_echo_body_url_or_unsanitized_headers(monkeypatch, caplog):
    bad = response(403, headers={"Retry-After": "secret-test-value"})
    bad.text = "private-source-and-secret"
    monkeypatch.setattr(transport_module.requests, "get", MagicMock(return_value=bad))
    monkeypatch.setattr(transport_module.time, "sleep", lambda seconds: None)
    with pytest.raises(requests.HTTPError) as error:
        GitHubReadTransport().get(URL + "?token=private-token", {})
    text = caplog.text + str(error.value)
    assert "secret-test-value" not in text
    assert "private-source" not in text
    assert "private-token" not in text
    assert "category=RATE_LIMIT" in text


def test_missing_fixed_file_is_cached_but_other_versions_are_not_hidden(monkeypatch):
    getter = MagicMock(side_effect=[response(404), response()])
    monkeypatch.setattr(transport_module.requests, "get", getter)
    transport = GitHubReadTransport()
    for repeat in range(2):
        with pytest.raises(requests.HTTPError):
            transport.get(URL, {}, {"ref": SHA})
    assert transport.get(URL, {}, {"ref": BASE}) == {"value": []}
    assert getter.call_count == 2


def test_cache_has_size_bounds_and_does_not_poison_failed_json(monkeypatch):
    malformed = response()
    malformed.json.side_effect = ValueError("malformed")
    getter = MagicMock(side_effect=[malformed, response(), response(), response()])
    monkeypatch.setattr(transport_module.requests, "get", getter)
    transport = GitHubReadTransport(max_bytes=0)
    with pytest.raises(ValueError):
        transport.get(URL, {}, {"ref": SHA})
    transport.get(URL, {}, {"ref": SHA})
    transport.get(URL, {}, {"ref": SHA})
    assert getter.call_count == 3
    assert transport.cache == {}


def test_budget_rejection_prevents_repeated_and_subsequent_fetches():
    fetcher = MagicMock(return_value="more than one byte")
    snapshot = ReviewSnapshot([], head_sha=SHA, content_fetcher=fetcher, max_extra_tokens=1)
    for path in ("Foo.java", "Foo.java", "Bar.java"):
        assert snapshot.get_file_content(path) == ""
    assert fetcher.call_count == 1
    assert not snapshot.can_fetch_more()


def test_zero_budget_never_fetches_and_missing_content_is_not_retried():
    fetcher = MagicMock(return_value=None)
    snapshot = ReviewSnapshot([], head_sha=SHA, content_fetcher=fetcher, max_extra_tokens=0)
    assert snapshot.get_file_content("Foo.java") == ""
    fetcher.assert_not_called()
    snapshot.max_extra_tokens = 100
    for repeat in range(3):
        assert snapshot.get_file_content("Foo.java") == ""
    fetcher.assert_called_once()


def test_search_stops_at_budget_and_never_claims_complete_absence():
    fetcher = MagicMock(return_value="too big")
    snapshot = ReviewSnapshot([], head_sha=SHA, content_fetcher=fetcher, max_extra_tokens=1)
    tree = {"truncated": False, "tree": [
        {"path": f"Dep{index}.java", "type": "blob"} for index in range(200)
    ]}
    searcher = SnapshotRepositorySearch(snapshot, MagicMock(return_value=tree), float("inf"))
    assert searcher("deleteById", "Repo.java") == []
    assert searcher("otherMethod", "Repo.java") == []
    assert searcher.incomplete == {"Repo.java"}
    fetcher.assert_called_once()


def test_whole_run_content_reads_reuse_transport_but_each_snapshot_is_charged(monkeypatch):
    data = {"encoding": "base64", "content": base64.b64encode(b"class Foo {}").decode()}
    getter = MagicMock(return_value=response(data=data))
    monkeypatch.setattr(transport_module.requests, "get", getter)
    monkeypatch.setattr(github_client, "READ_TRANSPORT", GitHubReadTransport())
    for batch in range(3):
        snapshot = ReviewSnapshot([], head_sha=SHA, repo_name="owner/repo",
                                  content_fetcher=fetch_file_content_fallback)
        assert snapshot.get_file_content("Foo.java") == "class Foo {}"
        assert snapshot.extra_tokens_used == len(b"class Foo {}")
    getter.assert_called_once()


def test_truncated_tree_is_not_reloaded_for_each_method():
    snapshot = ReviewSnapshot([], head_sha=SHA)
    getter = MagicMock(return_value={"truncated": True, "tree": []})
    searcher = SnapshotRepositorySearch(snapshot, getter, float("inf"))
    for method in ("deleteOne", "deleteTwo", "deleteThree"):
        assert searcher(method, "Repo.java") == []
    getter.assert_called_once()
    assert searcher.incomplete == {"Repo.java"}


def test_expired_search_never_loads_tree():
    getter = MagicMock()
    searcher = SnapshotRepositorySearch(ReviewSnapshot([], head_sha=SHA), getter, 0)
    assert searcher("deleteOne", "Repo.java") == []
    getter.assert_not_called()
    assert searcher.incomplete == {"Repo.java"}
