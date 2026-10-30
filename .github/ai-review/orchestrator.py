import json

import model_pool
import requests
from batching import estimate_tokens
from gemini_runner import execute_gemini_loop
from groq_runner import execute_groq_loop
from key_pool import (
    GLOBAL_KEY_POOL_GEMINI,
    GLOBAL_KEY_POOL_GROQ,
    KeyPool,
)
from model_pool import (
    GLOBAL_MODEL_POOL_GEMINI,
    GLOBAL_MODEL_POOL_GROQ,
    GROQ_CAPACITY,
    ModelPool,
)
from parser import ReviewResponseParser
from providers import GeminiClient, GroqClient
from native_tool_transport import NativeToolTransport
from retry_utils import DEFAULT_MAX_RETRIES_PER_MODEL


class ReviewOrchestrator:
    """整合 Provider 呼叫、金鑰池管理、雙供應商主動分流與錯誤處理的主控器。"""

    def __init__(
        self,
        groq_key_pool: KeyPool | None = None,
        gemini_key_pool: KeyPool | None = None,
        groq_model_pool: ModelPool | None = None,
        gemini_model_pool: ModelPool | None = None,
        groq_client: GroqClient | None = None,
        gemini_client: GeminiClient | None = None,
        parser: ReviewResponseParser | None = None,
    ) -> None:
        self.groq_key_pool = groq_key_pool or GLOBAL_KEY_POOL_GROQ
        self.gemini_key_pool = gemini_key_pool or GLOBAL_KEY_POOL_GEMINI
        self.groq_model_pool = groq_model_pool or GLOBAL_MODEL_POOL_GROQ
        self.gemini_model_pool = gemini_model_pool or GLOBAL_MODEL_POOL_GEMINI
        self.groq_client = groq_client or GroqClient()
        self.gemini_client = gemini_client or GeminiClient()
        self.parser = parser or ReviewResponseParser()
        self._cached_groq_available: set[str] | None = None

    def _get_groq_candidate_models(self) -> list[str]:
        candidates = self.groq_model_pool.get_candidates()
        groq_keys = self.groq_key_pool.get_all_keys()
        if not groq_keys:
            return candidates
        try:
            if self._cached_groq_available is None:
                self._cached_groq_available = self.groq_client.get_available_models(
                    groq_keys[0][1]
                )
            filtered = [
                model_name
                for model_name in candidates
                if model_name in self._cached_groq_available
            ]
            if filtered:
                return filtered
        except requests.RequestException as exc:
            print(
                f"無法列舉 Groq 可用模型清單：{exc}，直接依序嘗試備援候選模型。",
                flush=True,
            )
        return candidates

    def _try_gemini(
        self,
        prompt: str,
        max_retries_per_model: int,
        est_tokens: int,
        error_details: list[tuple[str, str]],
    ) -> str | None:
        raw_gemini_models = self.gemini_model_pool.get_candidates()
        if not raw_gemini_models:
            error_details.append(
                ("GEMINI_POOL", "所有 Gemini 候選模型均處於冷卻狀態，無可用模型")
            )
            return None

        gemini_models = model_pool.filter_eligible_models(raw_gemini_models, est_tokens)
        if not gemini_models:
            print(
                f"批次預估需 {est_tokens} Tokens，候選池無單一 Gemini 模型符合建議上限，"
                "依序嘗試全數 Gemini 候選模型...",
                flush=True,
            )
            gemini_models = list(raw_gemini_models)

        result = execute_gemini_loop(
            prompt,
            max_retries_per_model,
            gemini_models,
            self.gemini_key_pool,
            self.gemini_model_pool,
            self.gemini_client,
            self.parser,
            error_details,
        )
        if result is not None:
            return result

        fallback_gemini_models = [
            model_name
            for model_name in raw_gemini_models
            if model_name not in gemini_models
        ]
        if fallback_gemini_models:
            print(
                f"首選容量區間之 Gemini 模型均無法處理（預估 {est_tokens} Tokens），"
                f"降級嘗試其餘 {len(fallback_gemini_models)} 個 Gemini 候選模型...",
                flush=True,
            )
            return execute_gemini_loop(
                prompt,
                max_retries_per_model,
                fallback_gemini_models,
                self.gemini_key_pool,
                self.gemini_model_pool,
                self.gemini_client,
                self.parser,
                error_details,
            )
        return None

    def _try_groq(
        self,
        prompt: str,
        max_retries_per_model: int,
        est_tokens: int,
        error_details: list[tuple[str, str]],
        is_fallback: bool = True,
    ) -> str | None:
        if is_fallback:
            print(
                "所有 Gemini 金鑰與模型均無法取得有效回應，降級至備援 Provider：Groq...",
                flush=True,
            )
        raw_groq_models = self._get_groq_candidate_models()
        if not raw_groq_models:
            error_details.append(
                ("GROQ_POOL", "所有 Groq 候選模型均處於冷卻狀態，無可用模型")
            )
            return None

        groq_models = model_pool.filter_eligible_models(raw_groq_models, est_tokens)
        if not groq_models:
            msg = (
                f"批次預估需 {est_tokens} Tokens，超過 Groq 免費層級容量上限 "
                f"(<= {GROQ_CAPACITY.max_batch_tokens} Tokens)，略過 Groq 避免觸發 HTTP 413/429。"
            )
            print(msg, flush=True)
            error_details.append(("GROQ_CAPACITY", msg))
            return None

        return execute_groq_loop(
            prompt,
            max_retries_per_model,
            groq_models,
            self.groq_key_pool,
            self.groq_model_pool,
            self.groq_client,
            self.parser,
            error_details,
        )

    def chat_completion(
        self,
        prompt: str,
        max_retries_per_model: int = DEFAULT_MAX_RETRIES_PER_MODEL,
        required_tokens: int | None = None,
        prefer_groq: bool = False,
    ) -> str:
        groq_keys = self.groq_key_pool.get_all_keys()
        gemini_keys = self.gemini_key_pool.get_all_keys()

        if not groq_keys and not gemini_keys:
            raise RuntimeError(
                json.dumps(
                    [
                        (
                            "INIT",
                            "未配置任何 AI Provider 密鑰（GROQ_API_KEY_* 或 GEMINI_API_KEY_*）",
                        )
                    ],
                    ensure_ascii=False,
                )
            )

        est_tokens = (
            required_tokens if required_tokens is not None else estimate_tokens(prompt)
        )
        error_details: list[tuple[str, str]] = []

        if (
            prefer_groq
            and groq_keys
            and est_tokens <= GROQ_CAPACITY.max_batch_tokens
        ):
            print(
                f"輕量批次（預估 {est_tokens} Tokens <= {GROQ_CAPACITY.max_batch_tokens}），"
                "主動優先分流至 Groq 執行...",
                flush=True,
            )
            groq_res = self._try_groq(
                prompt, max_retries_per_model, est_tokens, error_details, is_fallback=False
            )
            if groq_res is not None:
                return groq_res
            if gemini_keys:
                print(
                    "Groq 輕量分流未能完成，無縫切換回 Google Gemini 池處理...",
                    flush=True,
                )
                gemini_res = self._try_gemini(
                    prompt, max_retries_per_model, est_tokens, error_details
                )
                if gemini_res is not None:
                    return gemini_res
            raise RuntimeError(json.dumps(error_details, ensure_ascii=False))

        if gemini_keys:
            gemini_res = self._try_gemini(
                prompt, max_retries_per_model, est_tokens, error_details
            )
            if gemini_res is not None:
                return gemini_res

        if groq_keys:
            groq_res = self._try_groq(
                prompt,
                max_retries_per_model,
                est_tokens,
                error_details,
                is_fallback=bool(gemini_keys),
            )
            if groq_res is not None:
                return groq_res

        raise RuntimeError(json.dumps(error_details, ensure_ascii=False))


DEFAULT_ORCHESTRATOR = ReviewOrchestrator()


def native_tool_transports() -> list[NativeToolTransport]:
    """依目前可用池建立候選，不把尚未實測能力的模型標為已驗證。"""
    orchestrator = DEFAULT_ORCHESTRATOR
    return [
        NativeToolTransport(provider, model, key)
        for provider, key_pool, candidate_pool in (
            ("groq", orchestrator.groq_key_pool, orchestrator.groq_model_pool),
            ("gemini", orchestrator.gemini_key_pool, orchestrator.gemini_model_pool),
        )
        for model in candidate_pool.get_candidates()
        for _, key in key_pool.get_all_keys()
    ]


def chat_completion(
    prompt: str,
    max_retries_per_model: int = DEFAULT_MAX_RETRIES_PER_MODEL,
    required_tokens: int | None = None,
    prefer_groq: bool = False,
) -> str:
    return DEFAULT_ORCHESTRATOR.chat_completion(
        prompt,
        max_retries_per_model=max_retries_per_model,
        required_tokens=required_tokens,
        prefer_groq=prefer_groq,
    )
