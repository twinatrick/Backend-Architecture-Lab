from dataclasses import dataclass, field
import re

MAPPING_ANNOT_RE = re.compile(
    r"@(?:RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b"
    r"(?:\s*\(([^)]*)\))?"
)
QUOTED_STRING_RE = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
IMPORT_DECL_RE = re.compile(
    r"^\s*import\s+(?:static\s+)?([a-zA-Z0-9_.]+?)\.([A-Za-z0-9_*]+)\s*;",
    re.MULTILINE,
)
PACKAGE_DECL_RE = re.compile(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;", re.MULTILINE)
FIELD_INJECT_RE = re.compile(
    r"\b(?:private|protected)\s+final\s+([A-Za-z0-9_<>?,\s.]+?)\s+([A-Za-z0-9_]+)\s*;"
)
METHOD_HEADER_RE = re.compile(
    r"\b(?:public|protected|private|default)\s+"
    r"(?:static\s+|final\s+|synchronized\s+|abstract\s+)*"
    r"(?:<[^>]+>\s+)?"
    r"([A-Za-z0-9_<>?,\[\].\s]+?)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)\s*"
    r"(?:throws\s+[A-Za-z0-9_,\s.]+\s*)?\{"
)


@dataclass(frozen=True)
class JavaMethodInfo:
    """Java 類別方法之語法結構與註解資訊。"""

    name: str
    param_count: int
    annotations: list[str]
    mapping_paths: list[str]
    decl_line: int
    end_line: int
    body_stripped: str
    annotation_start_line: int = 1
    body_start_line: int = 1


@dataclass
class JavaClassStructure:
    """Java 原始碼經詞法去註解／字串與大括號平衡解析後的結構模型。"""

    package_name: str = ""
    imports_by_simple: dict[str, set[str]] = field(default_factory=dict)
    wildcard_imports: list[str] = field(default_factory=list)
    class_annotations: list[str] = field(default_factory=list)
    class_mapping_paths: list[str] = field(default_factory=list)
    has_lazy_annotation: bool = False
    is_interface: bool = False
    injected_fields: list[tuple[str, str, int]] = field(default_factory=list)
    methods: list[JavaMethodInfo] = field(default_factory=list)
    stripped_lines: list[str] = field(default_factory=list)
    raw_lines: list[str] = field(default_factory=list)
    parse_complete: bool = False

    def find_method_at_line(self, line_num: int) -> JavaMethodInfo | None:
        for method_item in self.methods:
            if method_item.annotation_start_line <= line_num <= method_item.end_line:
                return method_item
        return None


def strip_comments_and_strings(source: str, preserve_literals: bool = False) -> tuple[str, bool]:
    """移除 Java 單行/多行註解並清空字串常值內容，100% 保留原始行號與大括號位置。"""
    out: list[str] = []
    if '"""' in source:
        return "", False  # 尚不支援 Java text block，不推論合規。
    idx = 0
    length = len(source)
    state = "CODE"
    while idx < length:
        ch_cur = source[idx]
        ch_next = source[idx + 1] if idx + 1 < length else ""
        if state == "CODE":
            if ch_cur == "/" and ch_next == "/":
                out.append("  ")
                idx += 2
                state = "LINE_COMMENT"
            elif ch_cur == "/" and ch_next == "*":
                out.append("  ")
                idx += 2
                state = "BLOCK_COMMENT"
            elif ch_cur == '"':
                out.append('"')
                idx += 1
                state = "STRING"
            elif ch_cur == "'":
                out.append("'")
                idx += 1
                state = "CHAR"
            else:
                out.append(ch_cur)
                idx += 1
        elif state == "LINE_COMMENT":
            if ch_cur == "\n":
                out.append("\n")
                state = "CODE"
            else:
                out.append(" ")
            idx += 1
        elif state == "BLOCK_COMMENT":
            if ch_cur == "*" and ch_next == "/":
                out.append("  ")
                idx += 2
                state = "CODE"
            else:
                out.append("\n" if ch_cur == "\n" else " ")
                idx += 1
        elif state == "STRING":
            if ch_cur == "\\" and idx + 1 < length:
                out.append(source[idx:idx + 2] if preserve_literals else "  ")
                idx += 2
            elif ch_cur == '"':
                out.append('"')
                idx += 1
                state = "CODE"
            else:
                out.append(ch_cur if preserve_literals or ch_cur == "\n" else " ")
                idx += 1
        elif state == "CHAR":
            if ch_cur == "\\" and idx + 1 < length:
                out.append("  ")
                idx += 2
            elif ch_cur == "'":
                out.append("'")
                idx += 1
                state = "CODE"
            else:
                out.append(" ")
                idx += 1
    return "".join(out), state in ("CODE", "LINE_COMMENT")


def _extract_mapping_paths(raw_region: str) -> list[str]:
    paths: list[str] = []
    for match in MAPPING_ANNOT_RE.finditer(raw_region):
        args_str = match.group(1) or ""
        named_path = re.search(r'\b(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', args_str)
        if named_path:
            args_str = named_path.group(1)
        elif "=" in args_str:
            args_str = ""
        quoted = QUOTED_STRING_RE.findall(args_str)
        if quoted:
            paths.extend(quoted)
        else:
            paths.append("")
    return paths


def analyze_java_source(source: str) -> JavaClassStructure:
    """對完整 Java 原始碼進行詞法清理與結構解析。"""
    raw_lines = source.splitlines()
    stripped, lex_ok = strip_comments_and_strings(source)
    uncommented, _ = strip_comments_and_strings(source, preserve_literals=True)
    uncommented_lines = uncommented.splitlines()
    stripped_lines = stripped.splitlines()
    structure = JavaClassStructure(raw_lines=raw_lines, stripped_lines=stripped_lines)
    if not source.strip() or not lex_ok:
        return structure

    pkg_match = PACKAGE_DECL_RE.search(stripped)
    if pkg_match:
        structure.package_name = pkg_match.group(1)

    for imp_match in IMPORT_DECL_RE.finditer(stripped):
        pkg_prefix, target_sym = imp_match.group(1), imp_match.group(2)
        if target_sym == "*":
            structure.wildcard_imports.append(pkg_prefix)
        else:
            structure.imports_by_simple.setdefault(target_sym, set()).add(
                f"{pkg_prefix}.{target_sym}"
            )

    structure.has_lazy_annotation = bool(re.search(r"@\s*Lazy\b", stripped))
    type_match = re.search(r"\b(class|interface|enum|record)\s+([A-Za-z0-9_]+)", stripped)
    if not type_match:
        return structure
    if len(re.findall(r"\b(?:class|interface|enum|record)\s+\w+", stripped)) != 1:
        return structure
    structure.is_interface = type_match.group(1) == "interface"

    class_decl_line = stripped[: type_match.start()].count("\n") + 1
    header_raw = "\n".join(uncommented_lines[:class_decl_line])
    structure.class_annotations = re.findall(r"@([A-Za-z0-9_]+)", stripped[: type_match.start()])
    structure.class_mapping_paths = _extract_mapping_paths(header_raw)

    depth = 0
    for character in stripped:
        depth += (character == "{") - (character == "}")
        if depth < 0:
            return structure
    if "{" not in stripped or depth != 0:
        return structure

    for line_idx, s_line in enumerate(stripped_lines, start=1):
        for field_match in FIELD_INJECT_RE.finditer(s_line):
            f_type = " ".join(field_match.group(1).split())
            f_name = field_match.group(2).strip()
            structure.injected_fields.append((f_type, f_name, line_idx))

    for m_match in METHOD_HEADER_RE.finditer(stripped):
        m_name = m_match.group(2)
        if m_name in ("if", "for", "while", "switch", "catch", "synchronized"):
            continue
        params_raw = m_match.group(3).strip()
        param_count = (
            0
            if not params_raw
            else len([param_str for param_str in params_raw.split(",") if param_str.strip()])
        )
        decl_line = stripped[: m_match.start()].count("\n") + 1
        brace_start = m_match.end() - 1
        depth = 0
        pos = brace_start
        end_pos = len(stripped) - 1
        while pos < len(stripped):
            if stripped[pos] == "{":
                depth += 1
            elif stripped[pos] == "}":
                depth -= 1
                if depth == 0:
                    end_pos = pos
                    break
            pos += 1
        end_line = stripped[:end_pos].count("\n") + 1
        previous_end = max(stripped.rfind(char, 0, m_match.start()) for char in ";{}") + 1
        annot_start_line = stripped[:previous_end].count("\n") + 1
        annot_stripped = stripped[previous_end:m_match.start()]
        annot_raw = uncommented[previous_end:m_match.start()]
        annots = re.findall(r"@([A-Za-z0-9_]+)", annot_stripped)
        m_paths = _extract_mapping_paths(annot_raw)
        body_stripped = stripped[brace_start + 1 : end_pos]
        structure.methods.append(
            JavaMethodInfo(
                name=m_name,
                param_count=param_count,
                annotations=annots,
                mapping_paths=m_paths,
                decl_line=decl_line,
                end_line=end_line,
                body_stripped=body_stripped,
                annotation_start_line=annot_start_line,
                body_start_line=stripped[:brace_start + 1].count("\n") + 1,
            )
        )

    structure.parse_complete = True
    return structure
