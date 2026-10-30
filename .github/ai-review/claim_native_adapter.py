"""將原生工具對話接到既有批次 JSON 校驗，不改變模型之外的裁定權。"""

from typing import Any

from native_tool_dialogue import ToolDialogueState, run_native_tool_dialogue
from native_tool_transport import initial_history
from orchestrator import chat_completion, native_tool_transports
from redaction import sanitize_diff


class ClaimNativeAdapter:
    """工具執行只回覆已由本輪確定性檢查取得的結果，重複呼叫不重做查證。"""

    supports_rule_dialogue = True

    def __init__(self, results: list[Any], budget: Any) -> None:
        self.results = {claim.claim_id: result for claim, result in results}
        self.rules = {claim.claim_id: claim.rule_key for claim, _ in results}
        self.budget = budget
        self.state: ToolDialogueState | None = None
        self.transports: list[Any] | None = None

    def __call__(self, prompt: str, **kwargs: Any) -> str:
        if self.state is None:
            self.state = ToolDialogueState(initial_history(prompt), self.rules)
            self.transports = native_tool_transports()
        else:
            self.state.history.extend(initial_history(prompt))

        def executor(claim_id: str, rule_key: str) -> dict[str, Any]:
            result = self.results[claim_id]
            if result is None:
                return {"status": "INCONCLUSIVE", "reason": "尚無專屬規則工具。"}
            return {
                "status": result.status.value, "rule_key": rule_key,
                "reason": result.verdict_reason, "evidence": sanitize_diff(result.verified_snippet),
                "missing_context": list(result.missing_context),
            }

        reply = run_native_tool_dialogue(
            self.state, self.transports or [], executor, self.budget,
        )
        if reply is None:
            raise RuntimeError("原生工具對話未完成；保留原查證結果。")
        return reply


def adapt_native_caller(caller: Any, results: list[Any], budget: Any) -> Any:
    """僅正式供應商入口啟用；注入的測試／外部呼叫端不會意外觸發網路。"""
    return ClaimNativeAdapter(results, budget) if caller is chat_completion else caller
