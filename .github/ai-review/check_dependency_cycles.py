import ast
import logging
import re
from pathlib import Path
from typing import Any

from check_rules import (
    RULE_CIRCULAR_DEPENDENCY,
    RULE_JPA_CHILD_CASCADE,
    RULE_PROHIBIT_CROSS_BEAN_LAZY,
    RULE_SELF_INVOCATION_BYPASS,
    RULE_SPRING_CIRCULAR_REF_CONFIG,
    SERVICE_MODULES,
    make_finding,
)

CLASS_DECL_RE = re.compile(
    r"\bpublic\s+(?:final\s+)?class\s+(\w+)"
    r"(?:\s+extends\s+[\w<>.,\s]+?)?(?:\s+implements\s+([^{]+))?\s*\{"
)
PRIVATE_FINAL_FIELD_RE = re.compile(r"private\s+final\s+([\w<>.,?\s]+?)\s+(\w+)\s*;")
MANY_TO_ONE_CASCADE_RE = re.compile(
    r"@ManyToOne\s*\([^)]*cascade\s*=\s*[^)]*(?:CascadeType\.ALL|CascadeType\.REMOVE)"
)
CIRCULAR_CONFIG_RE = re.compile(
    r"allow-circular-references\s*[:=]\s*true\b|setAllowCircularReferences\s*\(\s*true\s*\)"
)
ASPECT_ANNOT_RE = re.compile(r"@(?:Transactional|Cacheable|CacheEvict|CachePut|Caching)\b")
METHOD_DECL_RE = re.compile(
    r"^\s*(?:public|protected|private)\s+(?:[\w<>?,\[\]\s]+?)\s+(\w+)\s*\([^;]*$"
)


def _extract_class_and_self_types(content: str) -> tuple[str, set[str]]:
    match_obj = CLASS_DECL_RE.search(content)
    if not match_obj:
        return "", set()
    class_name = match_obj.group(1)
    allowed = {class_name, f"I{class_name}"}
    if class_name.endswith("Impl"):
        allowed.update((class_name[:-4], f"I{class_name[:-4]}"))
    if match_obj.group(2):
        allowed.update(
            re.sub(r"<.*>", "", tok).strip()
            for tok in match_obj.group(2).split(",")
            if re.sub(r"<.*>", "", tok).strip()
        )
    return class_name, allowed


def _normalize_type_name(raw_type: str) -> str:
    stripped = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", raw_type).strip()
    outer_match = re.match(r"^(?:Optional|ObjectProvider)<\s*(\w+)\s*>$", stripped)
    return outer_match.group(1) if outer_match else re.sub(r"<.*>", "", stripped).strip()


def _collect_aspect_methods(lines: list[str], has_class_tx: bool) -> dict[str, int]:
    aspect_methods, pending_aspect, in_class_body = {}, False, False
    for idx, raw_line in enumerate(lines, start=1):
        clean_line = re.sub(r"//.*$", "", raw_line.lstrip("+- ")).strip()
        if not in_class_body:
            in_class_body = bool(re.search(r"\bclass\s+\w+", clean_line))
            continue
        pending_aspect = pending_aspect or bool(ASPECT_ANNOT_RE.search(clean_line))
        method_match = METHOD_DECL_RE.match(clean_line)
        if method_match:
            if pending_aspect or (has_class_tx and clean_line.startswith("public ")):
                aspect_methods[method_match.group(1)] = idx
            pending_aspect = False
        elif clean_line.endswith(";"):
            pending_aspect = False
    return aspect_methods


def check_java_acyclic_and_lazy(
    path: str, content: str, changed_lines: set[int] | None = None
) -> list[dict[str, Any]]:
    """檢查 Java 檔案是否違規使用 @Lazy、切面自調用、開啟環形依賴配置或 @ManyToOne 反向級聯。"""
    findings: list[dict[str, Any]] = []
    norm_path = path.replace("\\", "/")
    if "/src/test/" in norm_path or norm_path.endswith(("Test.java", "Tests.java")):
        return findings

    lines = content.splitlines()
    class_idx = next(
        (i for i, ln in enumerate(lines) if re.search(r"\bclass\s+\w+", ln)), len(lines)
    )
    has_class_tx = any("@Transactional" in ln for ln in lines[:class_idx])
    aspect_methods = _collect_aspect_methods(lines, has_class_tx)
    current_method: str | None = None

    for idx, raw_line in enumerate(lines, start=1):
        line = raw_line.lstrip("+- ")
        stripped = line.strip()
        if stripped.startswith(("//", "/*", "*")):
            continue
        clean_code = re.sub(r'"[^"]*"', '""', re.sub(r"//.*$", "", line))
        method_decl = METHOD_DECL_RE.match(clean_code)
        if method_decl:
            current_method = method_decl.group(1)
        if changed_lines is not None and idx not in changed_lines:
            continue

        if CIRCULAR_CONFIG_RE.search(clean_code):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE", RULE_SPRING_CIRCULAR_REF_CONFIG,
                "嚴禁開啟 allow-circular-references 允許環形依賴", stripped,
                "掩蓋元件職責邊界混亂並導致啟動期與執行期代理注入風險",
                "移除該設定並透過職責下沉或上提編排消除環形依賴",
            ))
        if MANY_TO_ONE_CASCADE_RE.search(clean_code):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE", RULE_JPA_CHILD_CASCADE,
                "JPA @ManyToOne 嚴禁配置 CascadeType.ALL 或 CascadeType.REMOVE", stripped,
                "刪除子表或中介表紀錄時將反向級聯刪除父表實體，引發連鎖資料滅失",
                "移除 @ManyToOne 上的 cascade 屬性，改由父向子單向刪除控制",
            ))
        if re.search(r"@Lazy\b", clean_code):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE", RULE_PROHIBIT_CROSS_BEAN_LAZY,
                "生產程式碼全面禁止使用 @Lazy（包含跨元件與 @Lazy self 自我代理注入）",
                stripped, "以 @Lazy 掩蓋環形依賴或自我引用違反有向無環依賴圖 (DAG) 規範",
                "跨元件環路請透過職責下沉解耦；同類別事務/快取請改用 TransactionExecutor / CacheExecutor",
            ))
        if not method_decl and aspect_methods:
            for target_m in aspect_methods:
                if target_m != current_method and re.search(
                    rf"(?<![.\w])(?:this\.)?{re.escape(target_m)}\s*\(", clean_code
                ):
                    findings.append(make_finding(
                        path, idx, "HIGH", "ARCHITECTURE", RULE_SELF_INVOCATION_BYPASS,
                        f"禁止同類別內部直接自調用切面方法 '{target_m}(...)'（將繞過 Spring AOP 代理）",
                        stripped, "同類別 this.method() 呼叫無法觸發 @Transactional 或快取切面代理",
                        "請注入 TransactionExecutor 或 CacheExecutor 並以顯式 Lambda 委派執行",
                    ))
    return findings


def check_config_circular_ref(
    path: str, content: str, changed_lines: set[int] | None = None
) -> list[dict[str, Any]]:
    """檢查設定檔中是否開啟 allow-circular-references: true。"""
    findings: list[dict[str, Any]] = []
    for idx, raw_line in enumerate(content.splitlines(), start=1):
        if changed_lines is not None and idx not in changed_lines:
            continue
        if CIRCULAR_CONFIG_RE.search(raw_line.lstrip("+- ")):
            findings.append(make_finding(
                path, idx, "HIGH", "ARCHITECTURE", RULE_SPRING_CIRCULAR_REF_CONFIG,
                "設定檔嚴禁開啟 allow-circular-references: true", raw_line.strip(),
                "允許 Spring Bean 環形依賴將破壞單向無環架構邊界",
                "移除 allow-circular-references 設定並重構消除循環依賴",
            ))
    return findings


def check_cross_file_dependency_cycles(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """針對同微服務 Java 元件與同目錄 Python 模組執行跨檔案有向圖 DFS 環路偵測。"""
    return _detect_java_module_cycles(files) + _detect_python_module_cycles(files)


def _detect_java_module_cycles(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    module_files: dict[str, dict[str, str]] = {}
    target_paths: set[str] = set()
    for file_item in files:
        raw_path = file_item.get("filename") or file_item.get("path") or ""
        norm_path = raw_path.replace("\\", "/")
        is_deleted = (file_item.get("status") or "") in ("removed", "deleted")
        is_test = "/src/test/" in norm_path or norm_path.endswith(("Test.java", "Tests.java"))
        if not norm_path.endswith(".java") or is_deleted or is_test:
            continue
        content = file_item.get("full_content") or file_item.get("content") or ""
        if not content and Path(raw_path).exists():
            try:
                content = Path(raw_path).read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError) as exc:
                logging.debug("讀取 Java 檔案 %s 略過: %s", raw_path, exc)
        if content:
            mod_name = next(
                (mod for mod in SERVICE_MODULES if norm_path.startswith(f"{mod}/")), "default"
            )
            module_files.setdefault(mod_name, {})[norm_path] = content
            target_paths.add(norm_path)

    findings: list[dict[str, Any]] = []
    for mod_name, path_map in module_files.items():
        if mod_name != "default" and (Path(mod_name) / "src" / "main" / "java").exists():
            for disk_file in (Path(mod_name) / "src" / "main" / "java").rglob("*.java"):
                rel_str = disk_file.as_posix()
                if rel_str not in path_map:
                    try:
                        path_map[rel_str] = disk_file.read_text(encoding="utf-8")
                    except (OSError, UnicodeDecodeError) as exc:
                        logging.debug("讀取磁碟檔案 %s 略過: %s", rel_str, exc)
        findings.extend(_find_cycles_in_java_group(path_map, target_paths))
    return findings


def _find_cycles_in_java_group(
    path_map: dict[str, str], target_paths: set[str]
) -> list[dict[str, Any]]:
    class_to_path, iface_to_classes, raw_deps = {}, {}, {}
    for norm_path, content in path_map.items():
        class_name, self_types = _extract_class_and_self_types(content)
        if not class_name:
            continue
        class_to_path[class_name] = norm_path
        for stype in self_types - {class_name}:
            iface_to_classes.setdefault(stype, set()).add(class_name)
        raw_deps[class_name] = {
            _normalize_type_name(fm.group(1)) for fm in PRIVATE_FINAL_FIELD_RE.finditer(content)
        }
    adj: dict[str, set[str]] = {}
    for class_name, dep_types in raw_deps.items():
        resolved = {dep for dep in dep_types if dep in class_to_path and dep != class_name}
        for dep in dep_types:
            resolved.update(im for im in iface_to_classes.get(dep, set()) if im != class_name)
        adj[class_name] = resolved
    return _run_dfs_cycle_report(adj, class_to_path, target_paths)


def _detect_python_module_cycles(files: list[dict[str, Any]]) -> list[dict[str, Any]]:
    dir_groups: dict[str, dict[str, tuple[str, str]]] = {}
    target_paths: set[str] = set()
    for file_item in files:
        norm_path = (file_item.get("filename") or file_item.get("path") or "").replace("\\", "/")
        content = file_item.get("full_content") or file_item.get("content") or ""
        if norm_path.endswith(".py") and "/tests/" not in norm_path and "test_" not in norm_path:
            if content:
                parent_dir = str(Path(norm_path).parent.as_posix())
                dir_groups.setdefault(parent_dir, {})[Path(norm_path).stem] = (norm_path, content)
                target_paths.add(norm_path)

    findings: list[dict[str, Any]] = []
    for _, mod_map in dir_groups.items():
        mod_to_path = {stem: pair[0] for stem, pair in mod_map.items()}
        adj: dict[str, set[str]] = {}
        for stem, (_, content) in mod_map.items():
            imported: set[str] = set()
            try:
                for node in ast.parse(content).body:
                    if isinstance(node, ast.Import):
                        imported.update(
                            al.name.split(".")[-1] for al in node.names
                            if al.name.split(".")[-1] in mod_to_path
                            and al.name.split(".")[-1] != stem
                        )
                    elif isinstance(node, ast.ImportFrom) and node.module:
                        top_name = node.module.split(".")[-1]
                        if top_name in mod_to_path and top_name != stem:
                            imported.add(top_name)
            except SyntaxError as exc:
                logging.debug("AST 解析略過 %s: %s", stem, exc)
            adj[stem] = imported
        findings.extend(_run_dfs_cycle_report(adj, mod_to_path, target_paths))
    return findings


def _run_dfs_cycle_report(
    adj: dict[str, set[str]], node_to_path: dict[str, str], target_paths: set[str]
) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    visited, active_stack, reported_cycles = set(), [], set()

    def dfs_visit(current: str) -> None:
        visited.add(current)
        active_stack.append(current)
        for neighbor in sorted(adj.get(current, set())):
            if neighbor in active_stack:
                start_idx = active_stack.index(neighbor)
                cycle_nodes = active_stack[start_idx:] + [neighbor]
                cycle_key = frozenset(active_stack[start_idx:])
                if cycle_key not in reported_cycles:
                    reported_cycles.add(cycle_key)
                    target_node = next(
                        (nd for nd in cycle_nodes if node_to_path.get(nd) in target_paths),
                        cycle_nodes[0],
                    )
                    t_path = node_to_path.get(target_node, "")
                    if t_path in target_paths:
                        chain = " -> ".join(cycle_nodes)
                        findings.append(make_finding(
                            t_path, 1, "HIGH", "ARCHITECTURE", RULE_CIRCULAR_DEPENDENCY,
                            f"偵測到跨元件環形依賴 (Circular Dependency): {chain}", chain,
                            "元件雙向或環狀耦合違反有向無環依賴圖 (DAG) 標準",
                            "抽離共享狀態至底層 Manager/Util（職責下沉）或引入上層 Orchestrator 解耦",
                        ))
            elif neighbor not in visited:
                dfs_visit(neighbor)
        active_stack.pop()

    for start_node in sorted(adj.keys()):
        if start_node not in visited:
            dfs_visit(start_node)
    return findings
