import re
from typing import Any

from java_code_analyzer import analyze_java_source
from review_snapshot import InspectionStatus, ReviewSnapshot, RuleInspectionResult, RuleInspector


class MethodFactInspector(RuleInspector):
    """僅否定可核對的方法存在性／可見性錯述，不確認業務流程安全。"""

    rule_name = "RULE_JAVA_METHOD_FACT"

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        path, line = snapshot.parse_location(str(finding.get("location", "")))
        text = str(finding.get("problem", ""))
        names = set(re.findall(r"`([a-zA-Z_]\w*)`", text))
        names.update(re.findall(r"\b(do[A-Z]\w*)\b", text))
        content = snapshot.get_file_content(path)
        structure = analyze_java_source(content)
        status, reason = InspectionStatus.INCONCLUSIVE, "缺少唯一方法名稱或完整可解析類別"
        snippet = ""
        if len(names) == 1 and structure.parse_complete:
            name = next(iter(names))
            methods = [item for item in structure.methods if item.name == name]
            if not methods and not re.search(rf"\b{re.escape(name)}\s*\(", content):
                status = InspectionStatus.COMPLIANT
                reason = f"指定類別未宣告／呼叫 {name}，此方法引用主張不成立；未判定其他業務風險"
            elif len(methods) == 1 and re.search(r"公開|非私有|非 private|public", text):
                method = methods[0]
                header = "\n".join(content.splitlines()[method.decl_line - 1:method.decl_line + 2])
                if re.search(rf"\bprivate\s+[^;{{}}]*\b{re.escape(name)}\s*\(", header):
                    status = InspectionStatus.COMPLIANT
                    reason = f"{name} 實際宣告為 private，公開可見性的指控不成立"
                    snippet = snapshot.get_lines_window(path, method.decl_line, radius=2)
        return RuleInspectionResult(
            status=status, rule_name=self.rule_name, location=f"{path}:{line}",
            verdict_reason=reason, verified_snippet=snippet,
        )
