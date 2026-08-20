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
        any(k in path_lower for k in (
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
        any(k in path_lower for k in (
            "/feign/", "/client/", "/external/", "/integration/",
            "/kafka/", "/consumer/", "/publisher/", "feign",
            "client", "integration", "external",
        ))
        or "@FeignClient" in text_sample
        or "KafkaTemplate" in text_sample
    ):
        return "integration"

    # 資料存取與持久化特徵
    if any(k in path_lower for k in (
        "/repository/", "/entity/", "/dataaccess/", "/dao/",
        "/migration/", "/mapper/", "repository", "entity",
        "dao", "migration", "mapper",
    )):
        return "data"

    # 核心業務邏輯特徵
    if any(k in path_lower for k in (
        "/service/", "/domain/", "/usecase/", "/timer/",
        "service", "domain", "usecase", "timer",
    )):
        return "business"

    return "other"


def build_batches(
    files: list[dict],
    max_chars: int = 24000,
) -> list[tuple[str, list[str]]]:
    """
    依據領域分類與 diff 大小，將變更檔案切分為獨立批次。
    保證每個變更檔案屬於且僅屬於一個批次，無遺漏或重複。
    """
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
        current_size = 0
        for filename in paths:
            file_item = file_map.get(filename, {})
            patch_content = file_item.get("patch") or ""
            cost = len(filename) + max(len(patch_content), 1000)
            if current_batch and current_size + cost > max_chars:
                batches.append((scope, current_batch))
                current_batch = []
                current_size = 0
            current_batch.append(filename)
            current_size += cost
        if current_batch:
            batches.append((scope, current_batch))

    return batches
