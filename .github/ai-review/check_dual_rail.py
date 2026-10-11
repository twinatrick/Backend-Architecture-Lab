import os
import re
from typing import Any

from check_rules import (
    ALLOWED_STUBS,
    NON_AGGREGATE_CONTROLLERS,
    PENDING_DUAL_RAIL_MIGRATION,
    RULE_FORBIDDEN_GETALL_ENDPOINT,
    RULE_MISSING_DUAL_RAIL_RETRIEVAL,
    RULE_UNIMPLEMENTED_METHOD_STUB,
    make_finding,
)

STUB_EXCEPTION_PATTERN = re.compile(
    r"\bthrow\s+new\s+(?:UnsupportedOperationException|NotImplementedException)\b"
)
TODO_IMPLEMENT_PATTERN = re.compile(
    r"(?://|/\*)\s*(?:TODO|FIXME)\s*:\s*implement\b", re.IGNORECASE
)

COLLECTION_RETURN_PATTERN = re.compile(
    r"\b(?:ResponseType|ResponseEntity)?\s*<\s*(?:List|Set|Collection)\s*<"
    r"|\b(?:List|Set|Collection)\s*<\s*[A-Z]\w+Vo\s*>"
)
PAGING_PARAM_PATTERN = re.compile(
    r"\b(?:SearchQuery|BaseSearchQuery|Pageable|chunkSize)\b"
    r"|@RequestParam\b[^\)]*\bpage\b"
)
STREAM_RETURN_PATTERN = re.compile(r"\bFlux\s*<\s*ServerSentEvent\b")
CLASS_DECL_PATTERN = re.compile(
    r"\bpublic\s+(?:abstract\s+)?class\s+(\w+Controller)\b"
)
METHOD_DECL_PATTERN = re.compile(
    r"((?:@(?:Get|Post|Put|Delete|Patch|Request)Mapping[^\n]*\n\s*)*)"
    r"public\s+([^\n{]+)\s+(\w+)\s*\(([^)]*)\)\s*(?:throws[^{]+)?\{",
    re.MULTILINE,
)


def extract_controller_class(content: str) -> str | None:
    """提取宣告 Controller 之類別名稱（若為 RestController 或 Controller）。"""
    if "@RestController" not in content and "@Controller" not in content:
        return None
    match_obj = CLASS_DECL_PATTERN.search(content)
    return match_obj.group(1) if match_obj else None


def check_unimplemented_stubs(
    path: str,
    lines: list[str],
    changed_lines: set[int] | None = None,
    is_test: bool = False,
) -> list[dict[str, Any]]:
    """檢測程式碼中是否殘留未實作例外或未實作之 TODO 標記。"""
    if is_test:
        return []
    filename = os.path.basename(path)
    if filename in ALLOWED_STUBS:
        return []

    findings: list[dict[str, Any]] = []
    for line_idx, raw_line in enumerate(lines, start=1):
        if changed_lines is not None and line_idx not in changed_lines:
            continue
        line = raw_line.lstrip("+- ")
        stub_match = STUB_EXCEPTION_PATTERN.search(line)
        if stub_match:
            findings.append(
                make_finding(
                    path,
                    line_idx,
                    "HIGH",
                    "architecture",
                    RULE_UNIMPLEMENTED_METHOD_STUB,
                    "方法內部丟出未實作例外，屬於未完成之存根代碼 (Stub)。",
                    stub_match.group(0),
                    "未完成之存根代碼可能導致執行時拋出異常，破壞契約一致性與功能完整性。",
                    "請落實業務邏輯或底層實作，嚴禁在生產代碼中殘留未實作存根。",
                )
            )
            continue

        todo_match = TODO_IMPLEMENT_PATTERN.search(line)
        if todo_match:
            findings.append(
                make_finding(
                    path,
                    line_idx,
                    "HIGH",
                    "maintainability",
                    RULE_UNIMPLEMENTED_METHOD_STUB,
                    "代碼中留存未實作之 TODO/FIXME 標記。",
                    todo_match.group(0),
                    "殘留 TODO 存根可能導致代碼處於未閉環狀態即提交上線。",
                    "請於提交前完成所有宣告功能並移除 TODO 存根註記。",
                )
            )

    return findings


def check_forbidden_getall_endpoints(
    path: str,
    content: str,
    lines: list[str],
    changed_lines: set[int] | None = None,
    is_test: bool = False,
) -> list[dict[str, Any]]:
    """檢測實體 Controller 是否暴露無分頁或無邊界之 getAll/findAll 端點。"""
    if is_test or not path.endswith("Controller.java"):
        return []

    controller_name = extract_controller_class(content)
    if not controller_name:
        return []
    if (
        controller_name in PENDING_DUAL_RAIL_MIGRATION
        or controller_name in NON_AGGREGATE_CONTROLLERS
    ):
        return []

    findings: list[dict[str, Any]] = []
    for match_obj in METHOD_DECL_PATTERN.finditer(content):
        annotations_text = match_obj.group(1)
        return_type = match_obj.group(2)
        method_name = match_obj.group(3)
        params_text = match_obj.group(4)

        if not annotations_text:
            continue
        if not re.search(r"@(?:Get|Post|Request)Mapping\b", annotations_text):
            continue

        start_pos = match_obj.start()
        line_num = content[:start_pos].count("\n") + 1
        if changed_lines is not None and line_num not in changed_lines:
            continue

        if not COLLECTION_RETURN_PATTERN.search(return_type):
            continue
        if STREAM_RETURN_PATTERN.search(return_type) or "stream" in method_name.lower():
            continue
        if PAGING_PARAM_PATTERN.search(params_text):
            continue
        if "{" in annotations_text:
            continue

        findings.append(
            make_finding(
                path,
                line_num,
                "HIGH",
                "performance",
                RULE_FORBIDDEN_GETALL_ENDPOINT,
                f"Controller 方法 '{method_name}' 暴露無分頁之集合查詢端點，違反無界端點根除紀律。",
                f"{return_type} {method_name}({params_text.strip()[:60]})",
                "全量無分頁查詢可能導致巨量資料載入導致 OOM 或閘道連線逾時崩潰。",
                "請改為 POST /search 分頁查詢或 GET /stream 串流端點，嚴禁公開無分頁集合查詢。",
            )
        )

    return findings


def check_missing_dual_rail_endpoints(
    path: str,
    content: str,
    changed_lines: set[int] | None = None,
    is_test: bool = False,
) -> list[dict[str, Any]]:
    """檢測聚合根 Controller 是否具備雙軌獲取端點（POST /search 與 GET /stream）。"""
    if is_test or not path.endswith("Controller.java"):
        return []

    controller_name = extract_controller_class(content)
    if not controller_name:
        return []
    if (
        controller_name in PENDING_DUAL_RAIL_MIGRATION
        or controller_name in NON_AGGREGATE_CONTROLLERS
    ):
        return []

    class_match = CLASS_DECL_PATTERN.search(content)
    class_line = content[: class_match.start()].count("\n") + 1 if class_match else 1
    if changed_lines is not None and class_line not in changed_lines:
        return []

    has_search = bool(
        re.search(
            r"@PostMapping\b[^{};]*?\bsearch\w*\s*\(",
            content,
            re.DOTALL | re.IGNORECASE,
        )
        or re.search(r"@PostMapping\b[^{};]*?\bSearchQuery\b", content, re.DOTALL)
    )

    has_stream = bool(
        re.search(
            r"@GetMapping\b[^{};]*?\bFlux\s*<\s*ServerSentEvent",
            content,
            re.DOTALL,
        )
    )

    findings: list[dict[str, Any]] = []
    if not has_search:
        findings.append(
            make_finding(
                path,
                class_line,
                "HIGH",
                "architecture",
                RULE_MISSING_DUAL_RAIL_RETRIEVAL,
                f"聚合根 Controller '{controller_name}' 缺少 POST /search 分頁檢索端點。",
                f"class {controller_name}",
                "未提供標準分頁搜尋端點，無法支援前端分頁檢索與條件篩選功能。",
                "請依規範實作 POST /search 分頁搜尋端點，並繼承 BaseSearchQuery 結構。",
            )
        )

    if not has_stream:
        findings.append(
            make_finding(
                path,
                class_line,
                "HIGH",
                "architecture",
                RULE_MISSING_DUAL_RAIL_RETRIEVAL,
                f"聚合根 Controller '{controller_name}' 缺少 GET /stream 串流獲取端點。",
                f"class {controller_name}",
                "未提供 Server-Sent Events (SSE) 串流端點，大量資料全量獲取時將引發記憶體與連線問題。",
                "請依規範實作 GET /stream 串流端點，回傳 Flux<ServerSentEvent<List<Vo>>>。",
            )
        )

    return findings
