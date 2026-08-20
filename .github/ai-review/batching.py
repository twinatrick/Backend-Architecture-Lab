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
        path_lower = filename.lower()
        if path_lower.startswith(".github/"):
            groups["ci"].append(filename)
        elif path_lower.endswith(".py"):
            groups["python"].append(filename)
        elif any(
            token in path_lower
            for token in ("controller", "security", "permission", "auth", "openapi")
        ):
            groups["security-api"].append(filename)
        elif any(token in path_lower for token in ("feign", "client", "integration", "external")):
            groups["integration"].append(filename)
        elif any(
            token in path_lower
            for token in ("repository", "entity", "dao", "migration", "mapper")
        ):
            groups["data"].append(filename)
        elif any(token in path_lower for token in ("service", "domain", "usecase")):
            groups["business"].append(filename)
        else:
            groups["other"].append(filename)

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
