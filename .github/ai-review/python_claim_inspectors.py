"""用 AST 查證指定 Python 主張；不 import、編譯執行或執行 PR 程式。"""

import ast
from typing import Any

from review_snapshot import InspectionStatus, ReviewSnapshot, RuleInspectionResult, RuleInspector

RULE_PYTHON_DUPLICATE_DEFINITION = "RULE_PYTHON_DUPLICATE_DEFINITION"
RULE_PYTHON_UNDEFINED_NAME = "RULE_PYTHON_UNDEFINED_NAME"
RULE_PYTHON_IMPORT_POSITION = "RULE_PYTHON_IMPORT_POSITION"
SCOPES = (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)
FUNCTIONS = (ast.FunctionDef, ast.AsyncFunctionDef)


def scope_nodes(scope: ast.AST) -> list[ast.AST]:
    """包含目前作用域但不穿透其他函式、類別或推導式作用域。"""
    nodes: list[ast.AST] = []
    pending = list(ast.iter_child_nodes(scope))
    while pending:
        node = pending.pop()
        nodes.append(node)
        if not isinstance(node, SCOPES + (ast.ListComp, ast.SetComp, ast.DictComp,
                                         ast.GeneratorExp)):
            pending.extend(ast.iter_child_nodes(node))
    return nodes


class PythonClaimInspector(RuleInspector):
    """只判定單一規則，超出保守作用域／賦值證明時回報無法判定。"""

    def __init__(self, rule_name: str) -> None:
        self.rule_name = rule_name

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        path, line = snapshot.parse_location(str(finding.get("location", "")))
        location = f"{path}:{line}"
        content = snapshot.get_file_content(path)

        def result(status: InspectionStatus, reason: str) -> RuleInspectionResult:
            return RuleInspectionResult(status, self.rule_name, location, reason,
                                        verified_snippet=snapshot.get_lines_window(path, line, 2))

        if not path.endswith(".py"):
            return result(InspectionStatus.NOT_APPLICABLE, "此工具僅適用 Python 原始碼。")
        if content is None:
            return RuleInspectionResult(
                InspectionStatus.INCONCLUSIVE, self.rule_name, location,
                "缺少固定 SHA 完整 Python 原始碼。", missing_context=(f"{path}:full_content",),
            )
        try:
            tree = ast.parse(content)
        except (SyntaxError, ValueError, RecursionError):
            return result(InspectionStatus.INCONCLUSIVE, "Python 語法無法完整解析。")
        if not 1 <= line <= len(content.splitlines()):
            return result(InspectionStatus.INCONCLUSIVE, "指定原始碼行不存在。")
        if self.rule_name == RULE_PYTHON_DUPLICATE_DEFINITION:
            status, reason = self._duplicate(tree, line)
        elif self.rule_name == RULE_PYTHON_IMPORT_POSITION:
            status, reason = self._import_position(tree, line)
        else:
            status, reason = self._name(tree, line, finding)
        return result(status, reason)

    @staticmethod
    def _duplicate(tree: ast.Module, line: int) -> tuple[InspectionStatus, str]:
        for scope in ast.walk(tree):
            if not isinstance(scope, (ast.Module, ast.ClassDef) + FUNCTIONS):
                continue
            definitions = [node for node in getattr(scope, "body", [])
                           if isinstance(node, FUNCTIONS) and node.lineno == line]
            for target in definitions:
                conditional = [node for node in scope_nodes(scope)
                               if isinstance(node, FUNCTIONS) and node.name == target.name
                               and node not in scope.body]
                if conditional:
                    return InspectionStatus.INCONCLUSIVE, "同一作用域含條件式同名定義，需控制流程查證。"
                same = [node for node in getattr(scope, "body", [])
                        if isinstance(node, FUNCTIONS) and node.name == target.name]
                if any(node.decorator_list for node in same):
                    return InspectionStatus.INCONCLUSIVE, "裝飾器可能表示 overload 或屬性，需額外查證。"
                if len(same) > 1:
                    return InspectionStatus.VIOLATION, "同一直接作用域重複宣告同名函式。"
                return InspectionStatus.COMPLIANT, "此宣告在直接作用域內唯一；其他作用域同名不會覆蓋。"
        return InspectionStatus.INCONCLUSIVE, "指定行不是可確認的直接函式宣告。"

    @staticmethod
    def _import_position(tree: ast.Module, line: int) -> tuple[InspectionStatus, str]:
        target = next((node for node in ast.walk(tree)
                       if isinstance(node, (ast.Import, ast.ImportFrom))
                       and node.lineno <= line <= (node.end_lineno or node.lineno)), None)
        if target is None:
            return InspectionStatus.INCONCLUSIVE, "指定行不是 import，不臆測其他位置的主張。"
        if target not in tree.body:
            return InspectionStatus.INCONCLUSIVE, "條件式或區域 import 需依專案豁免另行確認。"
        for node in tree.body:
            if node is target:
                return InspectionStatus.COMPLIANT, "此 import 位於模組頂部匯入區段。"
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                continue
            if (node is tree.body[0] and isinstance(node, ast.Expr)
                    and isinstance(node.value, ast.Constant) and isinstance(node.value.value, str)):
                continue
            return InspectionStatus.VIOLATION, "頂層 import 位於非 docstring 執行陳述之後。"
        return InspectionStatus.INCONCLUSIVE, "無法確認 import 區段。"

    @staticmethod
    def _name(tree: ast.Module, line: int, finding: dict) -> tuple[InspectionStatus, str]:
        scopes = [node for node in ast.walk(tree) if isinstance(node, SCOPES)
                  and (isinstance(node, ast.Module) or node.lineno <= line <= node.end_lineno)]
        scope = min(scopes, key=lambda node: (
            getattr(node, "end_lineno", 10**9) - getattr(node, "lineno", 0)
        ))
        loads = {node.id for node in scope_nodes(scope)
                 if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load)
                 and node.lineno == line}
        allegation = str(finding.get("problem", "")) + " " + str(finding.get("evidence", ""))
        names = [name for name in loads if name in allegation]
        if len(names) != 1 or not isinstance(scope, FUNCTIONS):
            return InspectionStatus.INCONCLUSIVE, "需明確單一名稱與函式使用位置。"
        name = names[0]
        nodes = scope_nodes(scope)
        if any(isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
               and node.func.id in ("exec", "eval", "locals", "globals") for node in nodes):
            return InspectionStatus.INCONCLUSIVE, "包含動態名稱操作，不能以簡單賦值證明。"
        if any(isinstance(node, (ast.Global, ast.Nonlocal, ast.Delete))
               for node in nodes):
            return InspectionStatus.INCONCLUSIVE, "含非區域狀態、刪除或動態控制流程，尚未支援。"
        if any((isinstance(node, ast.ExceptHandler) and node.name == name)
               or (isinstance(node, ast.Nonlocal) and name in node.names)
               for node in ast.walk(scope)):
            return InspectionStatus.INCONCLUSIVE, "例外變數清除或閉包可能改變名稱狀態。"
        writes = [node for node in nodes if isinstance(node, ast.Name)
                  and node.id == name and isinstance(node.ctx, ast.Store)]
        if any(node.lineno >= line for node in writes):
            return InspectionStatus.INCONCLUSIVE, "同列或後續重新賦值需要控制流程查證。"
        for statement in scope.body:
            if statement.lineno >= line:
                break
            if isinstance(statement, (ast.Assign, ast.AnnAssign)):
                if statement.end_lineno is None or statement.end_lineno >= line:
                    continue
                targets = (statement.targets if isinstance(statement, ast.Assign)
                           else [statement.target])
                if any(isinstance(target, ast.Name) and target.id == name for target in targets):
                    if getattr(statement, "value", None) is not None:
                        return InspectionStatus.COMPLIANT, "同一函式先行無條件賦值，並非未定義名稱。"
        return InspectionStatus.INCONCLUSIVE, "未完成所有路徑的名稱解析，不以未找到賦值確認違規。"
