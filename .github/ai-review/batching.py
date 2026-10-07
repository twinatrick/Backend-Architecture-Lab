import os
from typing import Any

from model_pool import get_model_spec


def estimate_tokens(text: str) -> int:
    """估算純文字或 Diff 之 Token 總量（以代碼與自然語言通用比率約 3.5 字元/Token 為基準）。"""
    if not text:
        return 0
    char_count = len(text)
    line_count = text.count("\n")
    return max(1, int(char_count / 3.5) + line_count)


def estimate_file_tokens(filename: str, file_item: dict[str, Any]) -> int:
    """估算單一變更檔案納入提示詞後的預估 Token 消耗。"""
    patch = (
        file_item.get("patch")
        or file_item.get("content")
        or file_item.get("full_content")
        or ""
    )
    base_cost = estimate_tokens(filename) + 50
    patch_cost = estimate_tokens(patch)
    return max(patch_cost + base_cost, 250)


def estimate_batch_tokens(paths: list[str], file_map: dict[str, dict[str, Any]]) -> int:
    """計算一組檔案納入提示詞後之預估 Token 總量（包含提示詞模板開銷）。"""
    prompt_overhead = 1200
    file_tokens_total = sum(
        estimate_file_tokens(fname, file_map.get(fname, {}))
        for fname in paths
    )
    return prompt_overhead + file_tokens_total


def _classify_file(filename: str, file_item: dict) -> str:
    """依據檔案路徑層級、內容語義與副檔名精確劃分所屬審查領域批次。"""
    path_lower = filename.lower().replace("\\", "/")
    patch = file_item.get("patch", "")
    content = file_item.get("content", "")
    text_sample = f"{patch}\n{content}"

    if path_lower.endswith(".py"):
        return "python"

    if (
        path_lower.startswith(".github/")
        or path_lower.endswith((".yml", ".yaml"))
        or filename in ("Dockerfile", "compose.yaml", "pom.xml")
    ):
        return "ci"

    # 安全與 API 邊界特徵（優先於一般業務）
    if (
        any(keyword in path_lower for keyword in (
            "/controller/", "/security/", "/permission/", "/auth/",
            "/gateway/", "/filter/", "/aop/", "controller", "security",
            "permission", "auth", "openapi",
        ))
        or "@RestController" in text_sample
        or "@RequirePermission" in text_sample
        or "SecurityUtil" in text_sample
    ):
        return "security-api"

    # 外部整合與通訊特徵
    if (
        any(keyword in path_lower for keyword in (
            "/feign/", "/client/", "/external/", "/integration/",
            "/kafka/", "/consumer/", "/publisher/", "feign",
            "client", "integration", "external",
        ))
        or "@FeignClient" in text_sample
        or "KafkaTemplate" in text_sample
    ):
        return "integration"

    # 資料存取與持久化特徵
    if any(keyword in path_lower for keyword in (
        "/repository/", "/entity/", "/dataaccess/", "/dao/",
        "/migration/", "/mapper/", "repository", "entity",
        "dao", "migration", "mapper",
    )):
        return "data"

    # 核心業務邏輯特徵
    if any(keyword in path_lower for keyword in (
        "/service/", "/domain/", "/usecase/", "/timer/",
        "service", "domain", "usecase", "timer",
    )):
        return "business"

    return "other"


def build_batches(
    files: list[dict[str, Any]],
    max_chars: int | None = None,
    max_tokens: int | None = None,
    max_files: int | None = None,
    model_name: str | None = None,
) -> list[tuple[str, list[str]]]:
    """
    依據領域分類、Token 預估消耗與最大檔案數（雙重切分條件），將變更檔案切分為獨立批次。
    保證每個變更檔案屬於且僅屬於一個批次，無遺漏或重複。
    - 若傳入 model_name，自動讀取該模型之 ModelSpec (max_batch_tokens 與 max_files)。
    - 若未指定 model_name，支援環境變數或使用預設安全基準 (max_tokens=16000, max_files=4)。
    - 完整相容既有呼叫簽名 (max_chars)。
    """
    if model_name:
        spec = get_model_spec(model_name)
        target_tokens = max_tokens if max_tokens is not None else spec.max_batch_tokens
        target_files = max_files if max_files is not None else spec.max_files
    else:
        env_tokens = os.environ.get("AI_REVIEW_MAX_BATCH_TOKENS")
        env_files = os.environ.get("AI_REVIEW_MAX_BATCH_FILES")
        target_tokens = (
            max_tokens
            if max_tokens is not None
            else (int(env_tokens) if env_tokens and env_tokens.isdigit() else 16000)
        )
        target_files = (
            max_files
            if max_files is not None
            else (int(env_files) if env_files and env_files.isdigit() else 4)
        )

    char_limit = (
        max_chars
        if max_chars is not None
        else int(os.environ.get("AI_REVIEW_MAX_BATCH_CHARS", "24000"))
    )

    groups: dict[str, list[str]] = {
        "ci": [],
        "security-api": [],
        "business": [],
        "data": [],
        "integration": [],
        "python": [],
        "other": [],
    }
    file_map = {item["filename"]: item for item in files}
    for item in files:
        filename = item["filename"]
        scope = _classify_file(filename, item)
        groups[scope].append(filename)

    batches: list[tuple[str, list[str]]] = []
    for scope, paths in groups.items():
        if not paths:
            continue
        current_batch: list[str] = []
        current_tokens = 0
        current_chars = 0
        for filename in paths:
            file_item = file_map.get(filename, {})
            patch_content = file_item.get("patch") or ""
            cost_chars = len(filename) + max(len(patch_content), 1000)
            cost_tokens = estimate_file_tokens(filename, file_item)

            # 雙重切分條件：檔案數達上限 OR Token 達上限 OR 字元數達上限
            if current_batch and (
                len(current_batch) >= target_files
                or current_tokens + cost_tokens > target_tokens
                or current_chars + cost_chars > char_limit
            ):
                batches.append((scope, current_batch))
                current_batch = []
                current_tokens = 0
                current_chars = 0
            current_batch.append(filename)
            current_tokens += cost_tokens
            current_chars += cost_chars
        if current_batch:
            batches.append((scope, current_batch))

    return batches
