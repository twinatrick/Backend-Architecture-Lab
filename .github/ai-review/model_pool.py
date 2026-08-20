import os
import sys

DEFAULT_GEMINI_MODELS = [
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "gemini-1.5-flash",
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
        module_var_name: str | None = None,
        target_module_name: str = "review",
    ) -> None:
        self.default_models = list(default_models)
        self.env_override_var = env_override_var
        self.module_var_name = module_var_name
        self.target_module_name = target_module_name
        self.active_models: list[str] = list(default_models)

    def _sync_from_module(self) -> list[str]:
        if self.module_var_name:
            for module_key in (self.target_module_name, __name__):
                mod = sys.modules.get(module_key)
                if mod and hasattr(mod, self.module_var_name):
                    return getattr(mod, self.module_var_name)
        return self.active_models

    def _sync_to_module(self, models: list[str]) -> None:
        self.active_models = models
        if self.module_var_name:
            for module_key in (self.target_module_name, __name__):
                mod = sys.modules.get(module_key)
                if mod and hasattr(mod, self.module_var_name):
                    setattr(mod, self.module_var_name, models)

    def get_candidates(self) -> list[str]:
        if self.env_override_var:
            custom_models = os.environ.get(self.env_override_var, "").strip()
            if custom_models:
                return [
                    item.strip()
                    for item in custom_models.split(",")
                    if item.strip()
                ]
        return list(self._sync_from_module())

    def promote(self, model_name: str) -> None:
        models = list(self._sync_from_module())
        if model_name in models:
            models.remove(model_name)
            models.insert(0, model_name)
            self._sync_to_module(models)

    def demote(self, model_name: str) -> None:
        models = list(self._sync_from_module())
        if model_name in models:
            models.remove(model_name)
            models.append(model_name)
            self._sync_to_module(models)


GLOBAL_MODEL_POOL_GROQ = ModelPool(
    DEFAULT_MODEL_CANDIDATES, None, "ACTIVE_MODEL_CANDIDATES"
)
GLOBAL_MODEL_POOL_GEMINI = ModelPool(
    DEFAULT_GEMINI_MODELS, "GEMINI_MODELS", "ACTIVE_GEMINI_MODELS"
)


def get_gemini_candidate_models() -> list[str]:
    """取得 Google Gemini 候選模型清單。"""
    return GLOBAL_MODEL_POOL_GEMINI.get_candidates()
