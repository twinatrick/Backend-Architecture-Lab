from typing import Any

from check_java import check_java_file
from check_python import check_python_file
import check_rules
from review_snapshot import (
    InspectionStatus,
    ReviewSnapshot,
    RuleClaim,
    RuleInspectionResult,
    RuleInspector,
    compute_claim_identity,
)
from rule_inspectors import (
    AspectSelfInvocationRuleInspector,
    FqnRuleInspector,
    LayerDependencyRuleInspector,
    RepositoryTransactionRuleInspector,
    RestfulEndpointRuleInspector,
)

# 舊掃描器只具備找違規的能力，未命中不得當成語意合規證明。
PROVABLE_DETERMINISTIC_RULES: set[str] = set()


class DeterministicFileRuleInspector(RuleInspector):
    """通用確定性規則查證器：僅對具備證明合規能力的已登錄規則回報 COMPLIANT，其餘回報 INCONCLUSIVE。"""

    def __init__(self, rule_name: str, supports_compliance_proof: bool = False) -> None:
        self.rule_name = rule_name
        self.supports_compliance_proof = supports_compliance_proof

    def inspect(
        self, finding: dict[str, Any], snapshot: ReviewSnapshot
    ) -> RuleInspectionResult:
        rel_path, line_num = snapshot.parse_location(str(finding.get("location", "")))
        if not rel_path:
            return RuleInspectionResult(
                status=InspectionStatus.NOT_APPLICABLE,
                rule_name=self.rule_name,
                location=str(finding.get("location", "")),
                verdict_reason="缺少有效檔案路徑。",
            )
        content = snapshot.get_file_content(rel_path)
        if not content:
            return RuleInspectionResult(
                status=InspectionStatus.INCONCLUSIVE,
                rule_name=self.rule_name,
                location=f"{rel_path}:{line_num}",
                verdict_reason="無法取得完整檔案內容（僅有局部 Patch 或超出預算），無法執行規則查證。",
                missing_context=(f"{rel_path}:full_content",),
            )

        changed_lines = snapshot.get_changed_lines(rel_path)
        all_findings: list[dict[str, Any]] = []
        if rel_path.endswith(".java"):
            all_findings.extend(
                check_java_file(rel_path, content, changed_lines, snapshot.repo_searcher)
            )
        elif rel_path.endswith(".py"):
            all_findings.extend(check_python_file(rel_path, content, changed_lines))

        matched = [
            item
            for item in all_findings
            if self.rule_name == str(item.get("rule", ""))
            and snapshot.parse_location(str(item.get("location", ""))) == (rel_path, line_num)
            and self.rule_name != check_rules.RULE_DEAD_CODE_REPOSITORY
        ]
        snippet = snapshot.get_lines_window(rel_path, line_num, radius=3)
        if matched:
            return RuleInspectionResult(
                status=InspectionStatus.VIOLATION,
                rule_name=self.rule_name,
                location=f"{rel_path}:{line_num}",
                verdict_reason=f"確定性規則檢查確認違規：{matched[0].get('problem', '')}",
                verified_snippet=snippet,
                confirmed_findings=tuple(matched),
            )

        if not self.supports_compliance_proof:
            return RuleInspectionResult(
                status=InspectionStatus.INCONCLUSIVE,
                rule_name=self.rule_name,
                location=f"{rel_path}:{line_num}",
                verdict_reason=(
                    f"規則 {self.rule_name} 尚未支援完整語意合規證明，未掃出違規不等於已證明合規。"
                ),
                verified_snippet=snippet,
            )

        return RuleInspectionResult(
            status=InspectionStatus.COMPLIANT,
            rule_name=self.rule_name,
            location=f"{rel_path}:{line_num}",
            verdict_reason=(
                f"經完整檔案確定性檢查：規則 {self.rule_name} 於 {rel_path} "
                "確認合規（含專案豁免名單、內部層級全量查詢豁免或類別頂部已標註權限註解）。"
            ),
            verified_snippet=snippet,
        )


class RuleRegistry:
    """集中管理所有確定性規則與對應 RuleInspector 的註冊表，並負責規則歸屬分類與多規則疑點拆分。"""

    def __init__(self) -> None:
        self._inspectors: dict[str, RuleInspector] = {}
        self._register_defaults()

    def _register_defaults(self) -> None:
        for attr_name in dir(check_rules):
            if attr_name.startswith("RULE_"):
                val = getattr(check_rules, attr_name)
                if isinstance(val, str):
                    can_prove = val in PROVABLE_DETERMINISTIC_RULES
                    self._inspectors[attr_name] = DeterministicFileRuleInspector(val, can_prove)
                    self._inspectors[val] = DeterministicFileRuleInspector(val, can_prove)

        fqn_inspector = FqnRuleInspector()
        self._inspectors["RULE_PROHIBIT_FQN"] = fqn_inspector
        self._inspectors["RULE_FQN_FORBIDDEN"] = fqn_inspector
        self._inspectors["RULE_NO_FQN_IN_CODE"] = fqn_inspector
        self._inspectors[check_rules.RULE_PROHIBIT_FQN] = fqn_inspector

        layer_insp = LayerDependencyRuleInspector()
        for rule_key in (
            "RULE_CONTROLLER_DATA_ISOLATION",
            "RULE_CONTROLLER_DEPENDENCY",
            "RULE_ENTITY_USAGE",
            "RULE_SERVICE_ENTITY_MANAGER",
        ):
            rule_val = getattr(check_rules, rule_key)
            self._inspectors[rule_key] = layer_insp
            self._inspectors[rule_val] = layer_insp
        for alias_key in (
            "RULE_SERVICE_NO_REPOSITORY",
            "RULE_CONTROLLER_NO_REPOSITORY",
            "RULE_SERVICE_DIRECT_REPOSITORY",
            "RULE_SERVICE_DIRECT_EM",
        ):
            self._inspectors[alias_key] = layer_insp

        aspect_insp = AspectSelfInvocationRuleInspector()
        self._inspectors["RULE_ASPECT_SELF_INVOCATION_AND_LAZY"] = aspect_insp
        self._inspectors["RULE_LAZY_INJECTION_MASKING"] = aspect_insp
        self._inspectors["RULE_SELF_INVOCATION_BYPASS"] = aspect_insp

        rest_insp = RestfulEndpointRuleInspector()
        self._inspectors["RULE_RESTFUL_ENDPOINT_NAMING"] = rest_insp
        self._inspectors["RULE_CONTROLLER_RESTFUL_URI"] = rest_insp
        self._inspectors["RULE_RESTFUL_PLURAL_NO_VERBS"] = rest_insp
        self._inspectors[check_rules.RULE_RESTFUL_ENDPOINT_NAMING] = rest_insp

        tx_insp = RepositoryTransactionRuleInspector()
        self._inspectors["RULE_DERIVED_DELETE_TRANSACTIONAL"] = tx_insp
        self._inspectors[check_rules.RULE_DERIVED_DELETE_TRANSACTIONAL] = tx_insp

    def match_rule_keys(self, rule_text: str, problem_text: str = "") -> list[str]:
        """根據模型回報的 rule 與 problem 識別所有對應之已登錄規則（支援一疑點多規則拆分）。"""
        combined = f"{rule_text} {problem_text}"
        combined_lower = combined.lower()
        matched_keys: list[str] = []

        for key in self._inspectors:
            if key in combined and key not in matched_keys:
                matched_keys.append(key)

        keyword_rules = [
            (("fqn", "完全限定名稱", "fully qualified"), "RULE_NO_FQN_IN_CODE"),
            (
                ("@lazy", "切面自調用", "self-invocation", "代理失效", "cacheexecutor"),
                "RULE_ASPECT_SELF_INVOCATION_AND_LAZY",
            ),
            (
                ("restful", "動詞", "複數名詞", "路徑命名"),
                "RULE_RESTFUL_PLURAL_NO_VERBS",
            ),
            (
                ("直接注入 repository", "跨過 dataaccess", "注入 repository", "呼叫 repository"),
                "RULE_SERVICE_DIRECT_REPOSITORY",
            ),
            (("entitymanager",), "RULE_SERVICE_DIRECT_EM"),
            (
                ("衍生刪除", "deleteby", "modifying", "@transactional"),
                "RULE_DERIVED_DELETE_TRANSACTIONAL",
            ),
            (("雙軌", "分頁與", "findall()", "無上限查詢"), "RULE_MISSING_DUAL_RAIL_RETRIEVAL"),
            (("@requirepermission", "權限註解"), "RULE_PERMISSION_DICTIONARY"),
            (
                ("@apidto", "@apifield", "@apibody", "@apiquery", "@apipath"),
                "RULE_OPENAPI_ANNOTATION",
            ),
        ]
        for keywords, target_key in keyword_rules:
            if any(kw in combined_lower for kw in keywords) and target_key not in matched_keys:
                matched_keys.append(target_key)

        return matched_keys

    def get_inspector(self, rule_text: str, problem_text: str = "") -> RuleInspector | None:
        if rule_text in self._inspectors:
            return self._inspectors[rule_text]
        keys = self.match_rule_keys(rule_text, problem_text)
        if not keys:
            return None
        return self._inspectors.get(keys[0])

    def split_finding_into_claims(self, finding: dict[str, Any]) -> list[RuleClaim]:
        """將單一 Finding 拆分為獨立可裁定之 RuleClaim（若觸及多條規則則拆為多筆獨立主張）。"""
        raw_rule = str(finding.get("rule", "")).strip()
        raw_prob = str(finding.get("problem", "")).strip()
        parts = [
            part_str.strip()
            for part_str in raw_rule.replace("；", ";").split(";")
            if part_str.strip()
        ]
        claims: list[RuleClaim] = []
        seen: set[str] = set()
        for part in parts or [raw_rule]:
            matched = self.match_rule_keys(part, raw_prob if len(parts) <= 1 else "")
            for key in matched or [part or "UNCLASSIFIED_BUSINESS_CLAIM"]:
                inspector = self._inspectors.get(key)
                canonical = getattr(check_rules, key, key)
                if inspector and not isinstance(inspector, LayerDependencyRuleInspector):
                    canonical = inspector.rule_name
                if canonical in seen:
                    continue
                seen.add(canonical)
                sub_finding = dict(finding, rule=canonical)
                cid = compute_claim_identity(
                    str(finding.get("location", "")), canonical, str(finding.get("evidence", ""))
                )
                claims.append(RuleClaim(claim_id=cid, rule_key=key, finding=sub_finding))
        return claims


class SuspicionEligibilityAuditor:
    """疑點資格審核器：審查無專屬規則工具或 INCONCLUSIVE 之業務疑點是否具備具體可核對證據。"""

    @staticmethod
    def audit(
        finding: dict[str, Any], snapshot: ReviewSnapshot
    ) -> tuple[bool, str]:
        rel_path, line_num = snapshot.parse_location(str(finding.get("location", "")))
        if not rel_path:
            return False, "缺少有效檔案路徑與行號"
        if not snapshot.verify_evidence_exists(
            rel_path, line_num, str(finding.get("evidence", ""))
        ):
            return False, f"引用之程式碼證據不存在於 {rel_path}:{line_num} 真實檔案或變更區段中"

        problem = str(finding.get("problem", "")).strip()
        risk = str(finding.get("risk", "")).strip()
        combined = f"{problem} {risk}"
        speculative_markers = ("請確認", "需確認", "可能未", "若未", "疑似", "不確定", "假設")
        concrete_markers = ("入口", "方法", "呼叫", "資源", "欄位", "保護", "條件", "交易", "狀態", "缺少")
        has_concrete = sum(1 for kw in concrete_markers if kw in combined) >= 2
        if any(marker in combined for marker in speculative_markers) and not has_concrete:
            return False, "疑點屬於缺乏具體保護條件與受影響資源之推測性描述"
        if len(problem) < 12 or len(str(finding.get("evidence", "")).strip()) < 4:
            return False, "疑點描述或程式碼證據過於簡略，不足以構成人工阻擋主張"
        groups = (("入口", "方法"), ("資源", "欄位"), ("保護", "條件"), ("上下文", "缺少"))
        if not all(any(marker in combined for marker in group) for group in groups):
            return False, "須明列操作入口、受影響資源、保護條件與缺少的上下文"
        return True, "具備可核對證據與最低人工追查條件；不代表業務違規已成立"
