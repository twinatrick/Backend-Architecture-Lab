import os
from typing import Any

from model_pool import GROQ_CAPACITY, get_model_spec


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


def estimate_batch_tokens(
    paths: list[str], file_map: dict[str, dict[str, Any]]
) -> int:
    """計算一組檔案納入提示詞後之預估 Token 總量（包含提示詞模板開銷）。"""
    prompt_overhead = 1200
    file_tokens_total = sum(
        estimate_file_tokens(fname, file_map.get(fname, {})) for fname in paths
    )
    return prompt_overhead + file_tokens_total


def _classify_file(filename: str, file_item: dict[str, Any]) -> str:
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

    if (
        any(
            keyword in path_lower
            for keyword in (
                "/controller/",
                "/security/",
                "/permission/",
                "/auth/",
                "/gateway/",
                "/filter/",
                "/aop/",
                "controller",
                "security",
                "permission",
                "auth",
                "openapi",
            )
        )
        or "@RestController" in text_sample
        or "@RequirePermission" in text_sample
        or "SecurityUtil" in text_sample
    ):
        return "security-api"

    if (
        any(
            keyword in path_lower
            for keyword in (
                "/feign/",
                "/client/",
                "/external/",
                "/integration/",
                "/kafka/",
                "/consumer/",
                "/publisher/",
                "feign",
                "client",
                "integration",
                "external",
            )
        )
        or "@FeignClient" in text_sample
        or "KafkaTemplate" in text_sample
    ):
        return "integration"

    if any(
        keyword in path_lower
        for keyword in (
            "/repository/",
            "/entity/",
            "/dataaccess/",
            "/dao/",
            "/migration/",
            "/mapper/",
            "repository",
            "entity",
            "dao",
            "migration",
            "mapper",
        )
    ):
        return "data"

    if any(
        keyword in path_lower
        for keyword in (
            "/service/",
            "/domain/",
            "/usecase/",
            "/timer/",
            "service",
            "domain",
            "usecase",
            "timer",
        )
    ):
        return "business"

    return "other"


def _pack_scope_paths(
    scope: str,
    paths: list[str],
    file_map: dict[str, dict[str, Any]],
    target_tokens: int,
    target_files: int,
    char_limit: int,
) -> list[tuple[str, list[str]]]:
    """依指定 Token、檔案數與字元數上限打包單一領域內的檔案清單。"""
    packed: list[tuple[str, list[str]]] = []
    current_batch: list[str] = []
    current_tokens = 1200
    current_chars = 0
    for filename in paths:
        file_item = file_map.get(filename, {})
        patch_content = file_item.get("patch") or ""
        cost_chars = len(filename) + max(len(patch_content), 1000)
        cost_tokens = estimate_file_tokens(filename, file_item)

        if current_batch and (
            len(current_batch) >= target_files
            or current_tokens + cost_tokens > target_tokens
            or current_chars + cost_chars > char_limit
        ):
            packed.append((scope, current_batch))
            current_batch = []
            current_tokens = 1200
            current_chars = 0
        current_batch.append(filename)
        current_tokens += cost_tokens
        current_chars += cost_chars
    if current_batch:
        packed.append((scope, current_batch))
    return packed


def build_batches(
    files: list[dict[str, Any]],
    max_chars: int | None = None,
    max_tokens: int | None = None,
    max_files: int | None = None,
    model_name: str | None = None,
    enable_dual_track: bool = False,
) -> list[tuple[str, list[str]]]:
    """
    依據領域分類、Token 預估消耗與最大檔案數（雙重切分條件），將變更檔案切分為獨立批次。
    保證每個變更檔案屬於且僅屬於一個批次，無遺漏或重複。
    - 預設對齊 Gemini Tier 1/Tier 2 共通容量基準 (max_tokens=20000, max_files=6, max_chars=80000)。
    - 當 enable_dual_track=True 時，自動將領域內小型變更優先打包為符合 GROQ_CAPACITY 之輕量批次。
    """
    if model_name:
        spec = get_model_spec(model_name)
        target_tokens = (
            max_tokens if max_tokens is not None else spec.max_batch_tokens
        )
        target_files = max_files if max_files is not None else spec.max_files
    else:
        env_tokens = os.environ.get("AI_REVIEW_MAX_BATCH_TOKENS")
        env_files = os.environ.get("AI_REVIEW_MAX_BATCH_FILES")
        target_tokens = (
            max_tokens
            if max_tokens is not None
            else (int(env_tokens) if env_tokens and env_tokens.isdigit() else 20000)
        )
        target_files = (
            max_files
            if max_files is not None
            else (int(env_files) if env_files and env_files.isdigit() else 6)
        )

    char_limit = (
        max_chars
        if max_chars is not None
        else int(os.environ.get("AI_REVIEW_MAX_BATCH_CHARS", "80000"))
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
        if enable_dual_track and len(paths) >= 4:
            light_paths: list[str] = []
            heavy_paths: list[str] = []
            for fname in paths:
                ftokens = estimate_file_tokens(fname, file_map.get(fname, {}))
                if ftokens <= 1500 and len(light_paths) < max(3, len(paths) // 3):
                    light_paths.append(fname)
                else:
                    heavy_paths.append(fname)
            if light_paths:
                batches.extend(
                    _pack_scope_paths(
                        scope,
                        light_paths,
                        file_map,
                        GROQ_CAPACITY.max_batch_tokens,
                        GROQ_CAPACITY.max_files,
                        min(char_limit, 18000),
                    )
                )
            if heavy_paths:
                batches.extend(
                    _pack_scope_paths(
                        scope,
                        heavy_paths,
                        file_map,
                        target_tokens,
                        target_files,
                        char_limit,
                    )
                )
        else:
            batches.extend(
                _pack_scope_paths(
                    scope, paths, file_map, target_tokens, target_files, char_limit
                )
            )

    return batches
