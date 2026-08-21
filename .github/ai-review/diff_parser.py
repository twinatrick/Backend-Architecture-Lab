import re


def extract_changed_lines(
    patch: str | None,
    status: str = "",
    total_lines: int = 0,
) -> set[int]:
    """從 Unified Diff Patch 或檔案狀態中解析所有新增與修改的行號集合。"""
    if status in ("added", "new") and total_lines > 0:
        return set(range(1, total_lines + 1))

    if not patch or not patch.strip():
        return set()

    changed_lines: set[int] = set()
    current_line = 0
    hunk_regex = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
    has_hunk = False

    for raw_line in patch.splitlines():
        hunk_match = hunk_regex.match(raw_line)
        if hunk_match:
            has_hunk = True
            current_line = int(hunk_match.group(1))
            continue
        if raw_line.startswith("+") and not raw_line.startswith("+++"):
            target_line = current_line if has_hunk else (len(changed_lines) + 1)
            changed_lines.add(target_line)
            if has_hunk:
                current_line += 1
        elif raw_line.startswith("-") and not raw_line.startswith("---"):
            pass
        else:
            if has_hunk:
                current_line += 1

    return changed_lines
