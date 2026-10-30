"""原生工具傳輸層：僅傳遞結構化資料，工具執行權保留在受信任呼叫端。"""

import json
from typing import Any

import requests

from redaction import sanitize_diff


INSPECT_TOOL = {
    "name": "inspect_rule_claim",
    "description": "查證指定規則主張；不得執行原始碼或任意命令。",
    "parameters": {
        "type": "object",
        "properties": {
            "claim_id": {"type": "string"},
            "rule_key": {"type": "string"},
        },
        "required": ["claim_id", "rule_key"],
        "additionalProperties": False,
    },
}


class NativeToolTransport:
    """保留標準對話紀錄；傳輸失敗不會清除工具結果、疑點或預算。"""

    def __init__(self, provider: str, model: str, api_key: str, post_fn: Any = None) -> None:
        if provider not in ("groq", "gemini"):
            raise ValueError("不支援的工具供應商。")
        self.provider = provider
        self.model = model
        self.api_key = api_key
        self.post_fn = post_fn or requests.post

    def exchange(self, history: list[dict[str, Any]], timeout: float = 60) -> dict[str, Any]:
        """系統持有紀錄可重建；供應商簽章僅供同模型不透明回傳，不作為工具授權。"""
        if not 0 < timeout <= 60:
            raise ValueError("工具請求必須受剩餘期限限制。")
        if self.provider == "groq":
            return self._groq(history, timeout)
        return self._gemini(history, timeout)

    def _groq(self, history: list[dict[str, Any]], timeout: float) -> dict[str, Any]:
        messages: list[dict[str, Any]] = []
        for entry in history:
            if entry["role"] == "tool":
                messages.append({
                    "role": "tool", "tool_call_id": entry["id"],
                    "content": json.dumps(entry["result"], ensure_ascii=False),
                })
            elif entry["role"] == "assistant" and entry.get("calls"):
                messages.append({
                    "role": "assistant", "content": entry.get("text") or None,
                    "tool_calls": [{
                        "id": call["id"], "type": "function", "function": {
                            "name": call["name"], "arguments": json.dumps(call["arguments"]),
                        },
                    } for call in entry["calls"]],
                })
            else:
                messages.append({"role": entry["role"], "content": entry.get("text", "")})
        response = self.post_fn(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {self.api_key}"},
            json={"model": self.model, "messages": messages,
                  "tools": [{"type": "function", "function": INSPECT_TOOL}],
                  "tool_choice": "auto", "temperature": 0.1, "max_tokens": 4096},
            timeout=timeout,
        )
        response.raise_for_status()
        message = response.json()["choices"][0]["message"]
        calls = []
        for call in message.get("tool_calls") or []:
            calls.append({
                "id": call["id"], "name": call["function"]["name"],
                "arguments": json.loads(call["function"]["arguments"]),
            })
        return {"role": "assistant", "text": message.get("content") or "", "calls": calls}

    def _gemini(self, history: list[dict[str, Any]], timeout: float) -> dict[str, Any]:
        contents: list[dict[str, Any]] = []
        for entry in history:
            if entry["role"] == "tool":
                parts = [{"functionResponse": {
                    "name": "inspect_rule_claim", "response": entry["result"],
                }}]
            else:
                parts = [{"text": entry["text"]}] if entry.get("text") else []
                parts.extend({"functionCall": {"name": call["name"],
                                               "args": call["arguments"]}}
                             for call in entry.get("calls", []))
                if entry.get("native_model") == self.model and entry.get("native_parts"):
                    parts = entry["native_parts"]
            contents.append({"role": "model" if entry["role"] == "assistant" else "user",
                             "parts": parts})
        response = self.post_fn(
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{self.model}:generateContent",
            headers={"x-goog-api-key": self.api_key},
            json={"contents": contents,
                  "tools": [{"functionDeclarations": [INSPECT_TOOL]}],
                  "generationConfig": {"maxOutputTokens": 4096}},
            timeout=timeout,
        )
        response.raise_for_status()
        parts = response.json()["candidates"][0]["content"]["parts"]
        calls = [{"id": f"call-{index}", "name": part["functionCall"]["name"],
                  "arguments": part["functionCall"].get("args", {})}
                 for index, part in enumerate(parts) if "functionCall" in part]
        return {"role": "assistant", "text": "".join(part.get("text", "") for part in parts),
                "calls": calls, "native_model": self.model, "native_parts": parts}


def validate_tool_call(call: dict[str, Any], allowed_claims: dict[str, str]) -> tuple[str, str]:
    """拒絕未知工具、任意檔案讀取與模型自行新增的規則／主張識別碼。"""
    if call.get("name") != INSPECT_TOOL["name"]:
        raise ValueError("未知的規則工具。")
    arguments = call.get("arguments")
    if not isinstance(arguments, dict) or set(arguments) != {"claim_id", "rule_key"}:
        raise ValueError("工具參數不符合契約。")
    claim_id, rule_key = arguments["claim_id"], arguments["rule_key"]
    if (not isinstance(claim_id, str) or not isinstance(rule_key, str)
            or claim_id not in allowed_claims or allowed_claims[claim_id] != rule_key):
        raise ValueError("工具主張未經系統登錄。")
    return claim_id, rule_key


def initial_history(prompt: str) -> list[dict[str, Any]]:
    """來源內容先去除機密；原始碼內容一律視為不受信任資料。"""
    return [{"role": "user", "text": sanitize_diff(prompt)}]
