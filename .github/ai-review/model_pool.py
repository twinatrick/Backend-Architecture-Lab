from dataclasses import dataclass
import os
import threading
from typing import Any

@dataclass(frozen=True)
class ModelSpec:
    """定義 AI 模型之 Token 容量與審查邊界規格。"""
    name: str
    max_batch_tokens: int
    max_files: int
    max_output_tokens: int = 4096


MODEL_SPECS: dict[str, ModelSpec] = {
    # Google Gemini Flash - Tier 1
    "gemini-3.8-flash": ModelSpec(
        "gemini-3.8-flash", max_batch_tokens=24000, max_files=6
    ),
    "gemini-flash-latest": ModelSpec(
        "gemini-flash-latest", max_batch_tokens=24000, max_files=6
    ),
    "gemini-3.7-flash": ModelSpec(
        "gemini-3.7-flash", max_batch_tokens=20000, max_files=5
    ),
    "gemini-3.6-flash": ModelSpec(
        "gemini-3.6-flash", max_batch_tokens=18000, max_files=5
    ),
    # Google Gemini Flash - Tier 2
    "gemini-3.5-flash": ModelSpec(
        "gemini-3.5-flash", max_batch_tokens=16000, max_files=4
    ),
    "gemini-3-flash-preview": ModelSpec(
        "gemini-3-flash-preview", max_batch_tokens=16000, max_files=4
    ),
    # Google Gemini Flash Lite - Tier 3
    "gemini-flash-lite-latest": ModelSpec(
        "gemini-flash-lite-latest", max_batch_tokens=8000, max_files=3
    ),
    "gemini-3.5-flash-lite": ModelSpec(
        "gemini-3.5-flash-lite", max_batch_tokens=8000, max_files=3
    ),
    "gemini-3.1-flash-lite": ModelSpec(
        "gemini-3.1-flash-lite", max_batch_tokens=8000, max_files=3
    ),
    "gemini-2.5-flash-lite": ModelSpec(
        "gemini-2.5-flash-lite", max_batch_tokens=6000, max_files=3
    ),
    # Groq / Open Source Models (針對免費層級硬性門檻：TPM 8,000 / ITPM 7,000 / OTPM 1,000)
    "groq/compound": ModelSpec(
        "groq/compound", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "groq/compound-mini": ModelSpec(
        "groq/compound-mini", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "llama-3.3-70b-versatile": ModelSpec(
        "llama-3.3-70b-versatile", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "llama-3.1-8b-instant": ModelSpec(
        "llama-3.1-8b-instant", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "openai/gpt-oss-120b": ModelSpec(
        "openai/gpt-oss-120b", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "openai/gpt-oss-20b": ModelSpec(
        "openai/gpt-oss-20b", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "qwen/qwen3.8-27b": ModelSpec(
        "qwen/qwen3.8-27b", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
    "qwen/qwen3.6-27b": ModelSpec(
        "qwen/qwen3.6-27b", max_batch_tokens=5000, max_files=3, max_output_tokens=1000
    ),
}

DEFAULT_MODEL_SPEC = ModelSpec(
    "default", max_batch_tokens=12000, max_files=4, max_output_tokens=4096
)


def get_model_spec(model_name: str) -> ModelSpec:
    """取得指定模型的 Token 與檔案容量規格，支援環境變數全域覆寫。"""
    spec = MODEL_SPECS.get(model_name, DEFAULT_MODEL_SPEC)
    env_tokens = os.environ.get("AI_REVIEW_MAX_BATCH_TOKENS")
    env_files = os.environ.get("AI_REVIEW_MAX_BATCH_FILES")
    tokens = int(env_tokens) if env_tokens and env_tokens.isdigit() else spec.max_batch_tokens
    files = int(env_files) if env_files and env_files.isdigit() else spec.max_files
    return ModelSpec(
        name=model_name,
        max_batch_tokens=tokens,
        max_files=files,
        max_output_tokens=spec.max_output_tokens,
    )


def filter_eligible_models(candidate_models: list[str], required_tokens: int) -> list[str]:
    """根據批次所需之 Token 總量，評估並篩選出具備足夠容量處理該批次之候選模型。"""
    eligible: list[str] = []
    for model_name in candidate_models:
        spec = get_model_spec(model_name)
        if spec.max_batch_tokens >= required_tokens:
            eligible.append(model_name)
        else:
            print(
                f"略過模型 {model_name}：批次預估需 {required_tokens} Tokens，"
                f"超過該模型容量上限 {spec.max_batch_tokens} Tokens"
            )
    return eligible


DEFAULT_GEMINI_MODELS = [
    "gemini-3.8-flash",
    "gemini-flash-latest",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3-flash-preview",
    "gemini-flash-lite-latest",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-2.5-flash-lite",
]
ACTIVE_GEMINI_MODELS = list(DEFAULT_GEMINI_MODELS)

DEFAULT_MODEL_CANDIDATES = [
    "groq/compound-mini",
    "groq/compound",
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "openai/gpt-oss-120b",
    "openai/gpt-oss-20b",
    "qwen/qwen3.8-27b",
    "qwen/qwen3.6-27b",
]
ACTIVE_MODEL_CANDIDATES = list(DEFAULT_MODEL_CANDIDATES)


class ModelPool:
    """管理模型候選清單，支援動態提升 (Promotion) 與降級 (Demotion) 以及多執行緒安全。"""

    def __init__(
        self,
        default_models: list[str],
        env_override_var: str | None = None,
    ) -> None:
        self.default_models = list(default_models)
        self.env_override_var = env_override_var
        self.active_models: list[str] = list(default_models)
        self._lock = threading.Lock()

    def get_candidates(self) -> list[str]:
        if self.env_override_var:
            custom_models = os.environ.get(self.env_override_var, "").strip()
            if custom_models:
                return [item.strip() for item in custom_models.split(",") if item.strip()]
        with self._lock:
            return list(self.active_models)

    def promote(self, model_name: str) -> None:
        with self._lock:
            if model_name in self.active_models:
                self.active_models.remove(model_name)
                self.active_models.insert(0, model_name)

    def demote(self, model_name: str) -> None:
        with self._lock:
            if model_name in self.active_models:
                self.active_models.remove(model_name)
                self.active_models.append(model_name)

    def reset(self) -> None:
        with self._lock:
            self.active_models = list(self.default_models)


GLOBAL_MODEL_POOL_GROQ = ModelPool(DEFAULT_MODEL_CANDIDATES, "GROQ_MODELS")
GLOBAL_MODEL_POOL_GEMINI = ModelPool(DEFAULT_GEMINI_MODELS, "GEMINI_MODELS")


def get_groq_candidate_models() -> list[str]:
    """取得 Groq 候選模型清單。"""
    return GLOBAL_MODEL_POOL_GROQ.get_candidates()


def get_gemini_candidate_models() -> list[str]:
    """取得 Google Gemini 候選模型清單。"""
    return GLOBAL_MODEL_POOL_GEMINI.get_candidates()
