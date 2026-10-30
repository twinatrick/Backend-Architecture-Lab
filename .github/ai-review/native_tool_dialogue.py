"""供應商無關的工具對話狀態機，不授予模型執行任意工具之能力。"""

from dataclasses import dataclass, field
import json
import time
from typing import Any

import requests

from native_tool_transport import validate_tool_call


@dataclass
class ToolDialogueState:
    """切換模型時保留相同主張、歷史、預算及能力驗證結果。"""

    history: list[dict[str, Any]]
    allowed_claims: dict[str, str]
    verified_models: set[tuple[str, str]] = field(default_factory=set)
    calls_by_claim: dict[str, int] = field(default_factory=dict)
    errors: list[str] = field(default_factory=list)


def probe_tool_capability(transport: Any, timeout: float, budget: Any = None) -> bool:
    """實際完成一次無副作用工具往返；不得僅依模型名稱宣告支援。"""
    prompt = (
        'Call inspect_rule_claim with claim_id="capability-probe" and rule_key="PROBE". '
        'After receiving the tool result reply exactly: {"probe":"ok"}.'
    )
    started = time.monotonic()
    history = [{"role": "user", "text": prompt}]
    if budget is not None:
        if (not budget.can_call_tool() or budget.extra_context_tokens_used
                + len(prompt.encode("utf-8")) > budget.max_extra_context_tokens):
            return False
        budget.tool_calls_used += 1
        budget.extra_context_tokens_used += len(prompt.encode("utf-8"))
    first = transport.exchange(history, timeout=min(60, timeout))
    calls = first.get("calls")
    if not isinstance(calls, list) or len(calls) != 1:
        return False
    validate_tool_call(calls[0], {"capability-probe": "PROBE"})
    history.extend([first, {"role": "tool", "id": calls[0]["id"],
                            "result": {"probe": "ok"}}])
    remaining = timeout - (time.monotonic() - started)
    if remaining <= 0:
        return False
    if budget is not None:
        context_size = len(json.dumps(history, ensure_ascii=False).encode("utf-8"))
        if (not budget.can_call_tool() or budget.extra_context_tokens_used + context_size
                > budget.max_extra_context_tokens):
            return False
        budget.extra_context_tokens_used += context_size
    last = transport.exchange(history, timeout=min(60, remaining))
    return not last.get("calls") and json.loads(last.get("text", "")) == {"probe": "ok"}


def run_native_tool_dialogue(
    state: ToolDialogueState, transports: list[Any], executor: Any, budget: Any,
) -> str | None:
    """能力驗證後才允許工具對話；模型失敗仍保留全部已確認結果。"""
    for transport in transports:
        identity = (transport.provider, transport.model)
        try:
            remaining = budget.max_total_seconds - (
                time.monotonic() - budget.session_start_monotonic
            )
            if remaining <= 0 or not budget.can_call_tool():
                return None
            if identity not in state.verified_models:
                if not probe_tool_capability(transport, min(60, remaining), budget):
                    state.errors.append("模型未通過原生工具能力驗證。")
                    continue
                state.verified_models.add(identity)
            while budget.can_call_tool():
                remaining = budget.max_total_seconds - (
                    time.monotonic() - budget.session_start_monotonic
                )
                context_size = len(json.dumps(state.history, ensure_ascii=False).encode("utf-8"))
                if (remaining <= 0 or budget.extra_context_tokens_used + context_size
                        > budget.max_extra_context_tokens):
                    return None
                budget.extra_context_tokens_used += context_size
                reply = transport.exchange(state.history, timeout=min(60, remaining))
                calls = reply.get("calls", [])
                if not isinstance(calls, list):
                    raise ValueError("工具呼叫清單格式錯誤。")
                if not calls:
                    return str(reply.get("text", ""))
                # 整組先驗證，再執行；未知主張不能偷渡任何上下文讀取。
                identities = [validate_tool_call(call, state.allowed_claims) for call in calls]
                call_ids = [call.get("id") for call in calls]
                if (any(not isinstance(value, str) or not value for value in call_ids)
                        or len(set(call_ids)) != len(call_ids)):
                    raise ValueError("工具呼叫識別碼缺失或重複。")
                state.history.append(reply)
                for call, (claim_id, rule_key) in zip(calls, identities):
                    used = state.calls_by_claim.get(claim_id, 0)
                    if not budget.can_call_tool() or used >= 1 + budget.max_rechecks_per_claim:
                        result = {"status": "INCONCLUSIVE", "reason": "查證預算已耗盡"}
                    else:
                        budget.tool_calls_used += 1
                        state.calls_by_claim[claim_id] = used + 1
                        result = executor(claim_id, rule_key)
                    state.history.append({"role": "tool", "id": call["id"], "result": result})
        except (requests.RequestException, ValueError, TypeError, KeyError, IndexError):
            # 不記錄例外字串，避免供應商把密鑰或原始碼寫入錯誤資訊。
            state.errors.append("模型工具對話失敗；保留紀錄並切換候選模型。")
    return None
