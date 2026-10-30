import ast
from pathlib import Path
import sys

AI_REVIEW_DIR = Path(__file__).resolve().parents[1]
if str(AI_REVIEW_DIR) not in sys.path:
    sys.path.insert(0, str(AI_REVIEW_DIR))

import check_rules
import review_snapshot
from rule_registry import RuleRegistry


def test_every_existing_deterministic_rule_has_a_registered_inspector():
    registry = RuleRegistry()
    for name in dir(check_rules):
        value = getattr(check_rules, name)
        if name.startswith("RULE_") and isinstance(value, str):
            by_name = registry.get_inspector(name)
            by_value = registry.get_inspector(value)
            assert by_name is not None, name
            assert by_value is not None, value
            assert callable(by_name.inspect)
            assert callable(by_value.inspect)


def test_review_python_static_import_graph_remains_acyclic():
    modules = {path.stem: path for path in AI_REVIEW_DIR.glob("*.py")}
    graph = {}
    for name, path in modules.items():
        tree = ast.parse(path.read_text(encoding="utf-8-sig"))
        dependencies = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.ImportFrom) and node.module:
                dependencies.add(node.module.split(".")[0])
            elif isinstance(node, ast.Import):
                dependencies.update(alias.name.split(".")[0] for alias in node.names)
        graph[name] = dependencies & modules.keys()

    visited = set()
    active = []

    def visit(name):
        assert name not in active, f"循環依賴：{' -> '.join(active + [name])}"
        if name in visited:
            return
        active.append(name)
        for dependency in sorted(graph[name]):
            visit(dependency)
        active.pop()
        visited.add(name)

    for name in sorted(graph):
        visit(name)


def test_claim_identity_is_bound_to_trusted_rule_version(monkeypatch):
    monkeypatch.setattr(review_snapshot, "trusted_rule_version", lambda: "version-one")
    first = review_snapshot.compute_claim_identity("A.java:1", "RULE", "evidence")
    monkeypatch.setattr(review_snapshot, "trusted_rule_version", lambda: "version-two")
    second = review_snapshot.compute_claim_identity("A.java:1", "RULE", "evidence")
    assert first != second
    assert len(first) == 64
