import os

DEFAULT_GEMINI_MODELS = [
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3-flash",
    "gemini-2.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-2.5-flash-lite",
]
ACTIVE_GEMINI_MODELS = list(DEFAULT_GEMINI_MODELS)

DEFAULT_MODEL_CANDIDATES = [
    "llama-3.1-8b-instant",
    "llama-3.3-70b-versatile",
    "openai/gpt-oss-120b",
    "qwen/qwen3.6-27b",
    "openai/gpt-oss-20b",
]
ACTIVE_MODEL_CANDIDATES = list(DEFAULT_MODEL_CANDIDATES)


class ModelPool:
    """管理模型候選清單，支援動態提升 (Promotion) 與降級 (Demotion)。"""

    def __init__(
        self,
        default_models: list[str],
        env_override_var: str | None = None,
    ) -> None:
        self.default_models = list(default_models)
        self.env_override_var = env_override_var
        self.active_models: list[str] = list(default_models)

    def get_candidates(self) -> list[str]:
        if self.env_override_var:
            custom_models = os.environ.get(self.env_override_var, "").strip()
            if custom_models:
                return [
                    item.strip()
                    for item in custom_models.split(",")
                    if item.strip()
                ]
        return list(self.active_models)

    def promote(self, model_name: str) -> None:
        if model_name in self.active_models:
            self.active_models.remove(model_name)
            self.active_models.insert(0, model_name)

    def demote(self, model_name: str) -> None:
        if model_name in self.active_models:
            self.active_models.remove(model_name)
            self.active_models.append(model_name)


GLOBAL_MODEL_POOL_GROQ = ModelPool(DEFAULT_MODEL_CANDIDATES)
GLOBAL_MODEL_POOL_GEMINI = ModelPool(DEFAULT_GEMINI_MODELS, "GEMINI_MODELS")


def get_gemini_candidate_models() -> list[str]:
    """取得 Google Gemini 候選模型清單。"""
    return GLOBAL_MODEL_POOL_GEMINI.get_candidates()
