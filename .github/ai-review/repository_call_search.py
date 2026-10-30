import re
import time
from typing import Any

import requests

from java_call_model import JavaCallModel, parse_java_calls, resolved_calls, target_signature
from review_snapshot import ReviewSnapshot


class SnapshotRepositorySearch:
    """以固定版本生產來源查證接收者；有效反證可提前結案，不要求入口可達。"""

    def __init__(self, snapshot: ReviewSnapshot, getter: Any, deadline: float) -> None:
        self.snapshot, self.getter, self.deadline = snapshot, getter, deadline
        self.paths: list[str] | None = None
        self.tree_attempted = False
        self.unknown_sources = False
        self.outcomes: dict[tuple[str, str], dict[str, Any]] = {}

    @property
    def incomplete(self) -> set[str]:
        return {path for (path, _), result in self.outcomes.items()
                if result["status"] == "INCONCLUSIVE"}

    def _load_tree(self) -> list[str] | None:
        if time.monotonic() >= self.deadline:
            return None
        if self.paths is not None:
            return self.paths
        if self.tree_attempted:
            return None
        self.tree_attempted = True
        tree = self.getter(
            f"https://api.github.com/repos/{self.snapshot.repo_name}/git/trees/"
            f"{self.snapshot.head_sha}", params={"recursive": "1"},
        )
        if not isinstance(tree, dict) or tree.get("truncated") is not False:
            return None
        entries = tree.get("tree")
        if not isinstance(entries, list) or any(not isinstance(item, dict) for item in entries):
            return None
        self.unknown_sources = any(
            item.get("type") == "commit" or (
                isinstance(item.get("path"), str)
                and item["path"].endswith((".kt", ".scala", ".groovy"))
                and "/src/test/" not in "/" + item["path"]
            ) for item in entries
        )
        self.paths = [item["path"] for item in entries if item.get("type") == "blob"
                      and isinstance(item.get("path"), str) and item["path"].endswith(".java")]
        return self.paths

    def _model(self, path: str) -> JavaCallModel | None:
        content = self.snapshot.get_file_content(path)
        if not content:
            return None
        key = ("java_calls", self.snapshot.repo_name, self.snapshot.head_sha, path)
        return self.snapshot.source_budget.parse_once(key, lambda: parse_java_calls(content))

    def __call__(self, method_name: str, repository_path: str) -> list[str]:
        key = (repository_path, method_name)
        if key in self.outcomes:
            return list(self.outcomes[key]["callers"])
        outcome: dict[str, Any] = {
            "status": "INCONCLUSIVE", "callers": [], "evidence": [],
            "reason": "必要生產來源、方法簽名或符號解析尚未完整",
        }
        self.outcomes[key] = outcome
        try:
            target = self._model(repository_path)
            signature = target_signature(target, method_name) if target else None
            paths = self._load_tree()
            if target is None or signature is None or paths is None or repository_path not in paths:
                return []
            outcome["excluded_test_sources"] = [path for path in paths
                                                 if "/src/test/" in "/" + path]
            # 同一完整型別出現於不同模組時，不假定 imports 已唯一決定接收者。
            suffix = target.qualified_name.replace(".", "/") + ".java"
            if sum(path.endswith("/" + suffix) for path in paths) > 1:
                return []
            stem = target.name.removesuffix("Repository")
            ordered = sorted(paths, key=lambda path: (
                stem not in path.rsplit("/", 1)[-1],
                self.snapshot.find_file_entry(path) is None, path,
            ))
            complete = not self.unknown_sources
            for path in ordered:
                if path == repository_path or "/src/test/" in "/" + path:
                    continue
                if "/src/main/java/" not in "/" + path:
                    complete = False
                    continue
                if time.monotonic() >= self.deadline:
                    return []
                model = self._model(path)
                if model is None:
                    complete = False
                    if not self.snapshot.can_fetch_more():
                        return []
                    continue
                calls = resolved_calls(model, target, method_name, signature[2])
                if calls:
                    if time.monotonic() >= self.deadline:
                        return []
                    outcome.update(status="COMPLIANT", callers=[path], reason=(
                        "已確認生產程式中有無歧義靜態呼叫；未推論入口可達性"
                    ), evidence=[{"location": f"{path}:{line}", "snippet": snippet}
                                 for line, snippet in calls])
                    return [path]
                # 未解析語法、方法參照、反射或相關接收者無法定位，都不能作負向證明。
                if (not model.complete or re.search(
                    rf"\b{re.escape(method_name)}\b|\b(?:reflect|invoke|getMethod)\b", model.code,
                )):
                    complete = False
            entrypoint = re.search(
                r"@(?:EventListener|Scheduled|Bean|RequestMapping|GetMapping|PostMapping|"
                r"RepositoryRestResource|RestResource)\b",
                target.code,
            )
            if (complete and "/src/main/java/" in "/" + repository_path
                    and repository_path.endswith("Repository.java") and not entrypoint):
                outcome.update(status="VIOLATION", reason=(
                    "完整已辨識生產來源中未找到靜態呼叫；不等於執行時不可達"
                ))
            return []
        except (requests.RequestException, ValueError, KeyError):
            outcome["reason"] = "固定版本讀取或解析失敗，未形成完整證明"
            return []
