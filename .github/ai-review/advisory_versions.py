"""有限的 Maven 發布版本比較；不以字串排序或猜測限定詞先後。"""

import re


def numeric_maven_version(version: str) -> tuple[int, ...] | None:
    """只接受純數字發布版本；預發布、區間與 Maven 特殊限定詞保持未知。"""
    if not re.fullmatch(r"[0-9]{1,9}(?:\.[0-9]{1,9}){0,8}", version):
        return None
    return tuple(int(part) for part in version.split("."))


def maven_range_contains(version: str, affected: str) -> bool | None:
    """GitHub 公告的逗號合取比較式；每個子式都必須可解析才可下結論。"""
    target = numeric_maven_version(version)
    if target is None or not isinstance(affected, str) or len(affected) > 500:
        return None
    outcomes: list[bool] = []
    for part in affected.split(","):
        match = re.fullmatch(r"\s*(<=|>=|<|>|=)?\s*([0-9.]+)\s*", part)
        limit = numeric_maven_version(match[2]) if match else None
        if not match or limit is None:
            return None
        width = max(len(target), len(limit))
        left = target + (0,) * (width - len(target))
        right = limit + (0,) * (width - len(limit))
        comparisons = {"<": left < right, "<=": left <= right, ">": left > right,
                       ">=": left >= right, "=": left == right}
        outcomes.append(comparisons[match[1] or "="])
    return all(outcomes) if outcomes else None
