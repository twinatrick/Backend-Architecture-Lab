import re
import time
from typing import Any

from check_rules import RULE_DEAD_CODE_REPOSITORY
from github_client import gh_get
from repository_call_search import SnapshotRepositorySearch
from review_snapshot import InspectionStatus, ReviewSnapshot, RuleInspectionResult, RuleInspector


class RepositoryCallInspector(RuleInspector):
    rule_name = RULE_DEAD_CODE_REPOSITORY

    def inspect(self, finding: dict[str, Any], snapshot: ReviewSnapshot) -> RuleInspectionResult:
        path, line = snapshot.parse_location(str(finding.get("location", "")))
        content = snapshot.get_file_content(path)
        lines = content.splitlines()
        method = None
        if 0 < line <= len(lines):
            match = re.search(r"\b(\w+)\s*\(", lines[line - 1])
            method = match.group(1) if match else None
        search = SnapshotRepositorySearch(
            snapshot, snapshot.repository_getter or gh_get,
            min(snapshot.source_budget.deadline, time.monotonic() + 900),
        )
        if method and snapshot.repo_name and snapshot.head_sha:
            search(method, path)
        outcome = search.outcomes.get((path, method), {})
        status = InspectionStatus(outcome.get("status", "INCONCLUSIVE"))
        return RuleInspectionResult(
            status=status, rule_name=self.rule_name, location=f"{path}:{line}",
            verdict_reason=outcome.get("reason", "缺少方法宣告或固定版本搜尋範圍"),
            verified_snippet="\n".join(
                f"{item['location']}: {item['snippet']}" for item in outcome.get("evidence", [])
            ),
            missing_context=("repository_call_resolution",)
            if status == InspectionStatus.INCONCLUSIVE else (),
        )
