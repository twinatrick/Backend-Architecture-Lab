import json
import sys

import requests

from gemini_runner import execute_gemini_loop
from groq_runner import execute_groq_loop
from key_pool import (
    GLOBAL_KEY_POOL_GEMINI,
    GLOBAL_KEY_POOL_GROQ,
    KeyPool,
    get_groq_api_key,
)
from model_pool import (
    GLOBAL_MODEL_POOL_GEMINI,
    GLOBAL_MODEL_POOL_GROQ,
    ModelPool,
)
from parser import ReviewResponseParser
from providers import GeminiClient, GroqClient
from retry_utils import (
    DEFAULT_MAX_RETRIES_PER_MODEL,
    MAX_RETRY_LIMIT,
    calculate_backoff_delay,
    parse_retry_after,
    parse_retry_limit,
)


def get_candidate_models() -> list[str]:
    candidates = GLOBAL_MODEL_POOL_GROQ.get_candidates()
    if not get_groq_api_key():
        return candidates
    try:
        review_mod = sys.modules.get("review")
        getter = getattr(review_mod, "get_available_models", None)
        if getter is not None:
            available_models = getter()
        else:
            client = GroqClient()
            available_models = client.get_available_models(get_groq_api_key())
        filtered_candidates = [model for model in candidates if model in available_models]
        if filtered_candidates:
            return filtered_candidates
    except requests.RequestException as exc:
        print(f"無法列舉 Groq 可用模型清單：{exc}，直接依序嘗試備援候選模型。")
    return candidates


class ReviewOrchestrator:
    """整合 Provider 呼叫、金鑰池管理、重試退避與錯誤處理的主控器。"""

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

    def _get_groq_candidate_models(self) -> list[str]:
        candidates = self.groq_model_pool.get_candidates()
        groq_keys = self.groq_key_pool.get_all_keys()
        if not groq_keys:
            return candidates
        try:
            review_mod = sys.modules.get("review")
            getter = getattr(review_mod, "get_available_models", None)
            if getter is not None:
                available = getter()
            else:
                available = self.groq_client.get_available_models(groq_keys[0][1])
            filtered = [m for m in candidates if m in available]
            if filtered:
                return filtered
        except requests.RequestException as exc:
            print(f"無法列舉 Groq 可用模型清單：{exc}，直接依序嘗試備援候選模型。")
        return candidates

    def chat_completion(
        self,
        prompt: str,
        max_retries_per_model: int = DEFAULT_MAX_RETRIES_PER_MODEL,
    ) -> str:
        groq_keys = self.groq_key_pool.get_all_keys()
        gemini_keys = self.gemini_key_pool.get_all_keys()

        if not groq_keys and not gemini_keys:
            raise RuntimeError(
                json.dumps(
                    [("INIT", "未配置任何 AI Provider 密鑰（GROQ_API_KEY_* 或 GEMINI_API_KEY_*）")],
                    ensure_ascii=False,
                )
            )

        error_details: list[tuple[str, str]] = []

        if gemini_keys:
            gemini_models = self.gemini_model_pool.get_candidates()
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

        if groq_keys:
            if gemini_keys:
                print("所有 Gemini 金鑰與模型均無法取得有效回應，降級至備援 Provider：Groq...")
            groq_models = self._get_groq_candidate_models()
            result = execute_groq_loop(
                prompt,
                max_retries_per_model,
                groq_models,
                self.groq_key_pool,
                self.groq_model_pool,
                self.groq_client,
                self.parser,
                error_details,
            )
            if result is not None:
                return result

        raise RuntimeError(json.dumps(error_details, ensure_ascii=False))


DEFAULT_ORCHESTRATOR = ReviewOrchestrator()


def chat_completion(
    prompt: str,
    max_retries_per_model: int = DEFAULT_MAX_RETRIES_PER_MODEL,
) -> str:
    return DEFAULT_ORCHESTRATOR.chat_completion(prompt, max_retries_per_model)
