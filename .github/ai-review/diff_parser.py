import re


def extract_changed_lines(patch: str) -> set[int] | None:
    """從 Unified Diff Patch 中解析所有新增與修改的行號集合。"""
    if not patch or not patch.strip():
        return None
    changed_lines: set[int] = set()
    current_line = 0
    hunk_regex = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")

    for raw_line in patch.splitlines():
        hunk_match = hunk_regex.match(raw_line)
        if hunk_match:
            current_line = int(hunk_match.group(1))
            continue
        if current_line == 0:
            continue
        if raw_line.startswith("+") and not raw_line.startswith("+++"):
            changed_lines.add(current_line)
            current_line += 1
        elif raw_line.startswith("-") and not raw_line.startswith("---"):
            pass
        else:
            current_line += 1
    return changed_lines if changed_lines else None
