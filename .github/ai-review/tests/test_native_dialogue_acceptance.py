import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest
import requests

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import claim_native_adapter
from claim_native_adapter import ClaimNativeAdapter, adapt_native_caller
from claim_verifier import VerificationBudget
from native_tool_dialogue import ToolDialogueState, probe_tool_capability, run_native_tool_dialogue
from review_snapshot import InspectionStatus, RuleInspectionResult


def tool_reply(claim="claim", rule="RULE", call_id="call"):
    return {"role": "assistant", "calls": [{"id": call_id, "name": "inspect_rule_claim",
            "arguments": {"claim_id": claim, "rule_key": rule}}]}


def transport(*replies):
    client = MagicMock(provider="groq", model="local-test")
    client.exchange.side_effect = replies
    return client


def test_real_state_machine_probes_before_executing_claim_and_caches_capability():
    client = transport(tool_reply("capability-probe", "PROBE"),
                       {"text": '{"probe":"ok"}'}, tool_reply(), {"text": "done"})
    state = ToolDialogueState([{"role": "user", "text": "verify"}], {"claim": "RULE"})
    budget = VerificationBudget()
    executor = MagicMock(return_value={"status": "VIOLATION"})
    assert run_native_tool_dialogue(state, [client], executor, budget) == "done"
    assert state.verified_models == {("groq", "local-test")}
    assert budget.tool_calls_used == 2
    assert budget.extra_context_tokens_used > 0
    executor.assert_called_once_with("claim", "RULE")


@pytest.mark.parametrize("reply", [{"calls": []}, {"calls": "invalid"},
                                   tool_reply("other", "PROBE")])
def test_failed_probe_never_executes_any_claim(reply):
    client = transport(reply)
    state = ToolDialogueState([], {"claim": "RULE"})
    executor = MagicMock()
    assert run_native_tool_dialogue(state, [client], executor, VerificationBudget()) is None
    assert not state.verified_models
    executor.assert_not_called()


def test_probe_checks_second_round_context_budget_before_network():
    client = transport(tool_reply("capability-probe", "PROBE"))
    budget = VerificationBudget(max_extra_context_tokens=200)
    assert not probe_tool_capability(client, 60, budget)
    assert client.exchange.call_count == 1
    assert budget.tool_calls_used == 1


@pytest.mark.parametrize("calls", [
    [tool_reply()["calls"][0], tool_reply("forbidden")["calls"][0]],
    [tool_reply()["calls"][0], tool_reply()["calls"][0]],
    [tool_reply(call_id="")["calls"][0]],
])
def test_invalid_tool_batch_is_atomic_no_partial_execution(calls):
    client = transport({"role": "assistant", "calls": calls})
    state = ToolDialogueState([], {"claim": "RULE"},
                              verified_models={("groq", "local-test")})
    executor = MagicMock()
    budget = VerificationBudget()
    assert run_native_tool_dialogue(state, [client], executor, budget) is None
    executor.assert_not_called()
    assert budget.tool_calls_used == 0
    assert state.history == []


def test_claim_rechecks_stop_after_three_and_preserve_budget_across_fallback():
    first = transport(tool_reply(call_id="one"), requests.Timeout("do not log secret"))
    second = transport(tool_reply(call_id="two"), tool_reply(call_id="three"),
                       tool_reply(call_id="four"), {"text": "done"})
    second.model = "fallback"
    state = ToolDialogueState([], {"claim": "RULE"}, verified_models={
        ("groq", "local-test"), ("groq", "fallback"),
    })
    executor = MagicMock(return_value={"status": "VIOLATION"})
    budget = VerificationBudget()
    assert run_native_tool_dialogue(state, [first, second], executor, budget) == "done"
    assert executor.call_count == budget.tool_calls_used == 3
    assert state.calls_by_claim == {"claim": 3}
    assert state.history[-1]["result"]["status"] == "INCONCLUSIVE"
    assert "secret" not in str(state.errors)
    assert state.history[1]["result"]["status"] == "VIOLATION"


def test_context_exhaustion_never_calls_provider_even_with_cached_capability():
    client = transport()
    state = ToolDialogueState([{"text": "x" * 100}], {},
                              verified_models={("groq", "local-test")})
    assert run_native_tool_dialogue(state, [client], MagicMock(), VerificationBudget(
        max_extra_context_tokens=10,
    )) is None
    client.exchange.assert_not_called()


def test_adapter_reuses_state_and_returns_only_trusted_cached_results(monkeypatch):
    client = transport(tool_reply("capability-probe", "PROBE"), {"text": '{"probe":"ok"}'},
                       tool_reply(), {"text": "first"}, tool_reply(), {"text": "second"})
    factory = MagicMock(return_value=[client])
    monkeypatch.setattr(claim_native_adapter, "native_tool_transports", factory)
    claim = SimpleNamespace(claim_id="claim", rule_key="RULE")
    result = RuleInspectionResult(InspectionStatus.VIOLATION, "RULE", "A.py:1", "verified")
    adapter = ClaimNativeAdapter([(claim, result)], VerificationBudget())
    assert adapter("first question") == "first"
    state = adapter.state
    assert adapter("second question") == "second"
    assert adapter.state is state
    factory.assert_called_once()
    assert state.calls_by_claim == {"claim": 2}
    assert state.history[-1]["result"]["status"] == "VIOLATION"


def test_adapter_unknown_tool_and_no_candidates_never_fabricate_completion(monkeypatch):
    monkeypatch.setattr(claim_native_adapter, "native_tool_transports", lambda: [])
    adapter = ClaimNativeAdapter([], VerificationBudget())
    with pytest.raises(RuntimeError):
        adapter("question")
    fake = MagicMock()
    assert adapt_native_caller(fake, [], VerificationBudget()) is fake
    assert isinstance(adapt_native_caller(
        claim_native_adapter.chat_completion, [], VerificationBudget(),
    ), ClaimNativeAdapter)


@pytest.mark.parametrize("error_type", [
    requests.Timeout, requests.HTTPError, ValueError, TypeError, KeyError, IndexError,
])
def test_safe_failure_diagnostics_preserve_state_and_fallback(caplog, error_type):
    failure = error_type("fake_test_secret and private source code")
    if isinstance(failure, requests.HTTPError):
        failure.response = requests.Response()
        failure.response.status_code = 502
    first = transport(failure)
    second = transport({"text": "done"})
    second.model = "fallback"
    history = [{"role": "tool", "id": "previous", "result": {"status": "VIOLATION"}}]
    state = ToolDialogueState(list(history), {"claim": "RULE"}, verified_models={
        ("groq", "local-test"), ("groq", "fallback"),
    })
    budget = VerificationBudget(tool_calls_used=1)
    executor = MagicMock()
    assert run_native_tool_dialogue(state, [first, second], executor, budget) == "done"
    assert state.history == history
    assert budget.tool_calls_used == 1
    executor.assert_not_called()
    assert error_type.__name__ in caplog.text
    assert ("HTTP=502" if error_type is requests.HTTPError else "HTTP=None") in caplog.text
    assert "fake_test_secret" not in caplog.text + str(state.errors)
    assert "private source code" not in caplog.text + str(state.errors)


@pytest.mark.parametrize("status", [True, "fake_test_secret", 999])
def test_invalid_http_status_is_not_logged(caplog, status):
    failure = requests.HTTPError("fake_test_secret")
    failure.response = requests.Response()
    failure.response.status_code = status
    state = ToolDialogueState([], {}, verified_models={("groq", "local-test")})
    assert run_native_tool_dialogue(
        state, [transport(failure)], MagicMock(), VerificationBudget(),
    ) is None
    assert "HTTP=None" in caplog.text
    assert "fake_test_secret" not in caplog.text
