import re
from typing import Any

from check_java import check_java_file
from check_rules import (
    RULE_CONTROLLER_DATA_ISOLATION,
    RULE_CONTROLLER_DEPENDENCY,
    RULE_DERIVED_DELETE_TRANSACTIONAL,
    RULE_ENTITY_USAGE,
    RULE_PROHIBIT_FQN,
    RULE_RESTFUL_ENDPOINT_NAMING,
    RULE_SERVICE_ENTITY_MANAGER,
)
from java_code_analyzer import JavaClassStructure, analyze_java_source
from review_snapshot import (
    InspectionStatus,
    ReviewSnapshot,
    RuleInspectionResult,
    RuleInspector,
)

RULE_ALLOW_CIRCULAR_REFS = "RULE_ALLOW_CIRCULAR_REFS"
RULE_CIRCULAR_BEAN_DEPENDENCY = "RULE_CIRCULAR_BEAN_DEPENDENCY"
RULE_LAZY_INJECTION_MASKING = "RULE_LAZY_INJECTION_MASKING"
RULE_SELF_INVOCATION_BYPASS = "RULE_SELF_INVOCATION_BYPASS"

FQN_TOKEN_RE = re.compile(
    r"(?<![a-zA-Z0-9_.\"'/@])"
    r"([a-z][a-zA-Z0-9_]*(?:\.[a-z][a-zA-Z0-9_]*)+"
    r"\.[A-Z][a-zA-Z0-9_]*)"
)
REST_VERB_SEGMENT_RE = re.compile(
    r"/(?:add|create|insert|save|update|modify|edit|delete|remove|del|get|fetch|query|list|find)"
    r"(?:[A-Z0-9_-][a-zA-Z0-9_-]*|(?=/|$))",
)


def _load_analyzed_java(
    finding: dict[str, Any], snapshot: ReviewSnapshot, rule_name: str
) -> tuple[str, int, str, JavaClassStructure | None, RuleInspectionResult | None]:
    rel_path, line_num = snapshot.parse_location(str(finding.get("location", "")))
    loc_label = f"{rel_path}:{line_num}" if rel_path else str(finding.get("location", ""))
    if not rel_path.endswith(".java"):
        res = RuleInspectionResult(
            InspectionStatus.NOT_APPLICABLE, rule_name, loc_label, "目標檔案非 Java 原始碼。"
        )
        return rel_path, line_num, loc_label, None, res
    content = snapshot.get_file_content(rel_path)
    if not content:
        res = RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE, rule_name, loc_label,
            "缺少完整 Java 原始碼內容（僅有局部 Patch），無法執行結構查證。",
            missing_context=(f"{rel_path} (full_content)",),
        )
        return rel_path, line_num, loc_label, None, res
    structure = analyze_java_source(content)
    snippet = snapshot.get_lines_window(rel_path, line_num, radius=6)
    if not structure.parse_complete or not 1 <= line_num <= len(structure.raw_lines):
        res = RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE, rule_name, loc_label,
            "Java 原始碼詞法或大括號結構不完整，無法完成確定性解析。", verified_snippet=snippet,
        )
        return rel_path, line_num, loc_label, None, res
    return rel_path, line_num, loc_label, structure, None


class FqnRuleInspector(RuleInspector):
    """查證 RULE_PROHIBIT_FQN：僅在剝離註解與字串後的真實程式碼行核對 FQN 與同名類別衝突例外。"""

    rule_name = RULE_PROHIBIT_FQN

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        rel_path, line_num, loc_label, structure, early = _load_analyzed_java(
            finding, snapshot, self.rule_name
        )
        if early is not None or structure is None:
            return early  # type: ignore[return-value]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=6)
        start_idx = line_num
        end_idx = line_num
        found_fqns: list[tuple[int, str]] = []
        for idx in range(start_idx, end_idx + 1):
            s_line = structure.stripped_lines[idx - 1]
            if not re.match(r"^\s*(?:package|import)\s+", s_line):
                for match in FQN_TOKEN_RE.finditer(s_line):
                    found_fqns.append((idx, match.group(1)))
        if not found_fqns:
            return RuleInspectionResult(
                InspectionStatus.COMPLIANT, self.rule_name, loc_label,
                f"經剝離註解與字串後檢查 `{rel_path}` 第 {line_num} 行周邊，無 FQN 違規調用。",
                verified_snippet=snippet,
            )
        for hit_line, fqn_str in found_fqns:
            simple_name = fqn_str.rsplit(".", 1)[-1]
            imported_set = set(structure.imports_by_simple.get(simple_name, set()))
            imported_set.add(fqn_str)
            if len(imported_set) >= 2:
                code = "\n".join(
                    line for line in structure.stripped_lines
                    if not re.match(r"^\s*(?:package|import)\s+", line)
                )
                if re.search(rf"(?<![.\w]){re.escape(simple_name)}\b", code):
                    continue
            if structure.wildcard_imports:
                return RuleInspectionResult(
                    InspectionStatus.INCONCLUSIVE, self.rule_name, f"{rel_path}:{hit_line}",
                    f"檔案含萬用字元 import ({structure.wildcard_imports})，無法確定 `{fqn_str}`。",
                    verified_snippet=snippet,
                )
            return RuleInspectionResult(
                InspectionStatus.VIOLATION, self.rule_name, f"{rel_path}:{hit_line}",
                f"確認第 {hit_line} 行程式碼直接使用了完全限定名稱 `{fqn_str}`。",
                verified_snippet=snippet, confirmed_findings=(finding,),
            )
        return RuleInspectionResult(
            InspectionStatus.COMPLIANT, self.rule_name, loc_label,
            "該處 FQN 屬於同檔案使用兩個同名不同套件類別之合法消歧義例外。",
            verified_snippet=snippet,
        )


class LayerDependencyRuleInspector(RuleInspector):
    """查證架構分層依賴：精確核對注入欄位型別、只比對架構分層違規，不吃無關品質違規。"""

    rule_name = "LAYER_DEPENDENCY_CHECK"
    LAYER_RULES = {
        RULE_CONTROLLER_DATA_ISOLATION,
        RULE_CONTROLLER_DEPENDENCY,
        RULE_ENTITY_USAGE,
        RULE_SERVICE_ENTITY_MANAGER,
    }

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        rel_path, line_num, loc_label, structure, early = _load_analyzed_java(
            finding, snapshot, self.rule_name
        )
        if early is not None or structure is None:
            return early  # type: ignore[return-value]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=8)
        content = "\n".join(structure.stripped_lines)
        static_res = check_java_file(rel_path, content, repo_searcher=snapshot.repo_searcher)
        layer_hits = [
            f_item for f_item in static_res
            if str(f_item.get("rule", "")) in self.LAYER_RULES
            and snapshot.parse_location(str(f_item.get("location", ""))) == (rel_path, line_num)
        ]
        if layer_hits:
            return RuleInspectionResult(
                InspectionStatus.VIOLATION, self.rule_name, loc_label,
                f"確定性分層檢查確認違規：{layer_hits[0].get('problem')}",
                verified_snippet=snippet, confirmed_findings=tuple(layer_hits),
            )
        decls = [f"{f_type} {f_name}" for f_type, f_name, _ in structure.injected_fields]
        return RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE, self.rule_name, loc_label,
            f"已查注入欄位（{decls}）；未命中不能證明跨模組型別與繼承關係合規。",
            verified_snippet=snippet,
        )


class AspectSelfInvocationRuleInspector(RuleInspector):
    """查證切面自調用與 @Lazy：在去註解/去字串之方法體上核對同類別切面自調用與參數數量。"""

    rule_name = RULE_SELF_INVOCATION_BYPASS
    ASPECT_ANNOTS = {"Transactional", "Cacheable", "CacheEvict", "CachePut", "Caching", "Async"}

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        rel_path, line_num, loc_label, structure, early = _load_analyzed_java(
            finding, snapshot, self.rule_name
        )
        if early is not None or structure is None:
            return early  # type: ignore[return-value]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=8)
        target_line = structure.stripped_lines[line_num - 1]
        if re.search(r"@\s*Lazy\b", target_line):
            return RuleInspectionResult(
                InspectionStatus.VIOLATION, RULE_LAZY_INJECTION_MASKING, loc_label,
                f"`{rel_path}` 程式碼中存在被禁止的 `@Lazy` 標註。",
                verified_snippet=snippet, confirmed_findings=(finding,),
            )
        aspect_methods = [
            m_info for m_info in structure.methods
            if any(ann in self.ASPECT_ANNOTS for ann in m_info.annotations)
        ]
        for caller_method in structure.methods:
            if not caller_method.decl_line <= line_num <= caller_method.end_line:
                continue
            for target_method in aspect_methods:
                call_pat = re.compile(
                    rf"(?<![.\w])(?:this\.)?{re.escape(target_method.name)}\s*\(([^)]*)\)"
                )
                for call_match in call_pat.finditer(caller_method.body_stripped):
                    call_line = caller_method.body_start_line + (
                        caller_method.body_stripped[:call_match.start()].count("\n")
                    )
                    if call_line != line_num:
                        continue
                    args_str = call_match.group(1).strip()
                    arg_cnt = (
                        0 if not args_str
                        else len([arg_s for arg_s in args_str.split(",") if arg_s.strip()])
                    )
                    overloads = [item for item in structure.methods
                                 if item.name == target_method.name]
                    if arg_cnt == target_method.param_count and len(overloads) == 1:
                        return RuleInspectionResult(
                            InspectionStatus.VIOLATION, self.rule_name,
                            f"{rel_path}:{call_line}",
                            f"確認 `{caller_method.name}` 自調用切面方法 `{target_method.name}`。",
                            verified_snippet=snippet, confirmed_findings=(finding,),
                        )
        status = InspectionStatus.INCONCLUSIVE
        if (finding.get("rule") == RULE_LAZY_INJECTION_MASKING
                and not structure.has_lazy_annotation):
            status = InspectionStatus.COMPLIANT
        return RuleInspectionResult(
            status, self.rule_name, loc_label,
            "已檢查明確 @Lazy 與同類方法；其他隱含／繼承切面呼叫尚無充分合規證明。",
            verified_snippet=snippet,
        )


class RestfulEndpointRuleInspector(RuleInspector):
    """查證 RULE_RESTFUL_ENDPOINT_NAMING：僅檢查 `@*Mapping` 雙引號內的真實 URI 字串，不誤判 Java 方法名。"""

    rule_name = RULE_RESTFUL_ENDPOINT_NAMING

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        rel_path, line_num, loc_label, structure, early = _load_analyzed_java(
            finding, snapshot, self.rule_name
        )
        if early is not None or structure is None:
            return early  # type: ignore[return-value]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=6)
        target_method = structure.find_method_at_line(line_num)
        uris = list(structure.class_mapping_paths)
        if target_method is not None:
            uris.extend(target_method.mapping_paths)
        bad_uris = [uri for uri in uris if REST_VERB_SEGMENT_RE.search(uri)]
        if bad_uris:
            return RuleInspectionResult(
                InspectionStatus.VIOLATION, self.rule_name, loc_label,
                f"確認 `@*Mapping` URI 路徑包含動作動詞片段：{bad_uris}。",
                verified_snippet=snippet, confirmed_findings=(finding,),
            )
        return RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE, self.rule_name, loc_label,
            f"已查 URI {uris}，未發現已知動詞；不代表複數命名與動態路由已證明合規。",
            verified_snippet=snippet,
        )


class RepositoryTransactionRuleInspector(RuleInspector):
    """查證 RULE_DERIVED_DELETE_TRANSACTIONAL：區分 Repository 介面與 DataAccessImpl/ServiceImpl。"""

    rule_name = RULE_DERIVED_DELETE_TRANSACTIONAL

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        rel_path, line_num, loc_label, structure, early = _load_analyzed_java(
            finding, snapshot, self.rule_name
        )
        if early is not None or structure is None:
            return early  # type: ignore[return-value]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=8)
        if "/Repository/" in rel_path and structure.is_interface:
            return RuleInspectionResult(
                InspectionStatus.COMPLIANT, self.rule_name, loc_label,
                f"`{rel_path}` 為 Repository 介面，規範要求交易邊界標註於實作層。",
                verified_snippet=snippet,
            )
        static_res = check_java_file(
            rel_path, "\n".join(structure.raw_lines), repo_searcher=snapshot.repo_searcher
        )
        tx_hits = [
            f_item for f_item in static_res
             if str(f_item.get("rule", "")) == RULE_DERIVED_DELETE_TRANSACTIONAL
             and snapshot.parse_location(str(f_item.get("location", ""))) == (rel_path, line_num)
        ]
        if tx_hits:
            return RuleInspectionResult(
                InspectionStatus.VIOLATION, self.rule_name, loc_label,
                f"確認刪除方法缺少 `@Transactional`：{tx_hits[0].get('problem')}",
                verified_snippet=snippet, confirmed_findings=tuple(tx_hits),
            )
        return RuleInspectionResult(
            InspectionStatus.INCONCLUSIVE, self.rule_name, loc_label,
            "未命中刪除交易規則；尚未證明指定方法及其交易邊界合規。", verified_snippet=snippet,
        )


__all__ = [
    "AspectSelfInvocationRuleInspector", "FqnRuleInspector", "InspectionStatus",
    "LayerDependencyRuleInspector", "RepositoryTransactionRuleInspector",
    "RestfulEndpointRuleInspector", "ReviewSnapshot", "RuleInspectionResult",
    "RuleInspector", "RULE_ALLOW_CIRCULAR_REFS", "RULE_CIRCULAR_BEAN_DEPENDENCY",
]
