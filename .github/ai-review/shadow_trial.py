import argparse
import json
import os
import re
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator
from unittest.mock import patch
from urllib.parse import urlsplit

import requests

from batch_runner import process_batches_parallel
from batching import build_batches
from claim_verifier import validate_claim_gate_mode
from engine import evaluate, load_policy
from github_client import gh_get, normalize_paths
from github_content import enrich_files_with_full_content, fetch_all_pr_files
from key_pool import get_gemini_api_keys, get_groq_api_keys
from redaction import redact_secrets
from reporter import format_json_report
from rule_version import trusted_rule_version
from snapshot_static_checks import evaluate_snapshot_shadow_review
from source_budget import SourceReadBudget
from static_checks import run_static_checks

ROOT = Path(__file__).resolve().parents[2]
REPO_PATTERN = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
SHA_PATTERN = re.compile(r"[0-9a-f]{40}")


def assert_trial_request(method: str, url: str, allow_models: bool) -> None:
    """連重新導向都重新檢查；試用禁止 GitHub 寫入及任意外部目的地。"""
    target = urlsplit(url)
    if target.scheme != "https" or target.username or target.password:
        raise RuntimeError("試用拒絕非 HTTPS 或含 URL 憑證的請求")
    if target.port not in (None, 443):
        raise RuntimeError("試用拒絕非標準連接埠")
    allowed = method.upper() == "GET" and target.hostname in ("api.github.com", "pypi.org")
    allowed_model = allow_models and method.upper() == "POST" and target.hostname in (
        "api.groq.com", "generativelanguage.googleapis.com",
    )
    allowed_discovery = (
        allow_models and method.upper() == "GET" and target.hostname == "api.groq.com"
        and target.path == "/openai/v1/models" and not target.query and not target.fragment
    )
    if not (allowed or allowed_model or allowed_discovery):
        raise RuntimeError("試用網路邊界拒絕此請求；不允許發布 PR 留言、狀態或裁定")


@contextmanager
def trial_network(allow_models: bool) -> Iterator[None]:
    original_send = requests.Session.send

    def guarded_send(session: Any, request: Any, **kwargs: Any) -> Any:
        assert_trial_request(request.method, request.url, allow_models)
        return original_send(session, request, **kwargs)

    with patch.object(requests.Session, "send", guarded_send):
        yield


def snapshot_identity(pr_data: dict[str, Any]) -> dict[str, str]:
    result = {key: (pr_data.get(key) or {}).get("sha", "") for key in ("base", "head")}
    if any(not isinstance(value, str) or not SHA_PATTERN.fullmatch(value)
           for value in result.values()):
        raise ValueError("PR 缺少完整 Base／Head SHA")
    return result


def prepare_trial(repo: str, pr_number: int, getter: Any = gh_get) -> tuple[dict, list, list]:
    if not REPO_PATTERN.fullmatch(repo) or pr_number <= 0:
        raise ValueError("儲存庫或 PR 編號不合法")
    url = f"https://api.github.com/repos/{repo}/pulls/{pr_number}"
    initial = getter(url)
    identity = snapshot_identity(initial)
    files = fetch_all_pr_files(repo, pr_number, gh_get_fn=getter)
    if initial.get("changed_files") != len(files) or not files:
        raise ValueError("PR 檔案清單不完整或沒有變更，不建立成功試用計畫")
    batches = build_batches(files, enable_dual_track=True)
    paths = [path for _, group in batches for path in group]
    if sorted(paths) != sorted(item["filename"] for item in files) or len(set(paths)) != len(paths):
        raise ValueError("批次覆蓋不完整或重複")
    if snapshot_identity(getter(url)) != identity:
        raise ValueError("取得檔案清單期間 Base／Head 已變更，請重新準備")
    manifest = {
        "mode": "shadow", "status": "PREPARED_NOT_EXECUTED", "repository": repo,
        "pr_number": pr_number, "base_sha": identity["base"], "head_sha": identity["head"],
        "tool_version": trusted_rule_version(), "changed_files": paths,
        "batches": [{"scope": scope, "paths": group} for scope, group in batches],
        "github_writes": False, "max_workers": 1,
    }
    return manifest, files, batches


def execute_trial(manifest: dict, files: list, batches: list, getter: Any = gh_get) -> dict:
    """明確選擇執行才消耗模型配額；原有閘門僅作比較，不發布任何結果。"""
    repo, number = manifest["repository"], manifest["pr_number"]
    url = f"https://api.github.com/repos/{repo}/pulls/{number}"
    expected = {"base": manifest["base_sha"], "head": manifest["head_sha"]}
    if snapshot_identity(getter(url)) != expected:
        raise ValueError("試用開始前審查快照已過期")
    deadline = time.monotonic() + 900
    source_budget = SourceReadBudget(deadline=deadline)
    policy = load_policy()
    enrich_files_with_full_content(repo, files, expected["head"], gh_get_fn=getter)

    def local_failure(*args: Any, **kwargs: Any) -> None:
        # 不把可能包含供應商內容的失敗細節直接寫出或發布。
        print("試用批次未完成；未發布遠端失敗報告。")

    with patch("batch_runner.publish_failure_report", local_failure):
        results = process_batches_parallel(
            batches, files, (ROOT / "開發規範.md").read_text(encoding="utf-8"),
            (ROOT / ".github/AI_REVIEW.md").read_text(encoding="utf-8"), policy, number,
            repo_name=repo, head_sha=expected["head"], base_sha=expected["base"],
            max_workers=1, gh_get_fn=getter, source_budget=source_budget,
        )
    paths = normalize_paths(manifest["changed_files"])
    reviewed = [path for result in results
                for path in normalize_paths(result.get("files_reviewed", []))]
    original = [finding for result in results for finding in result.get("findings", [])]
    baseline = evaluate(original + run_static_checks(files), paths, reviewed, policy)
    shadow = evaluate_snapshot_shadow_review(
        results, files, paths, reviewed, policy, repo, expected["head"], getter, deadline,
        expected["base"], number, source_budget=source_budget,
    )
    if shadow is None:
        raise RuntimeError("未產生影子結果，不得視為試用完成")
    if snapshot_identity(getter(url)) != expected:
        raise ValueError("試用期間 Base／Head 已變更，結果不可作為接管驗收")
    report = format_json_report(
        baseline["decision"], baseline["findings"], baseline["blocking_findings"],
        len(results), manifest["changed_files"], audit_info={
            "trigger_type": "local_read_only_trial", "head_sha": expected["head"],
            "base_sha": expected["base"], "tool_version": manifest["tool_version"],
        }, requires_human_review=baseline["requires_human_review"],
        high_risk_files=baseline["high_risk_files"], shadow_review=shadow,
        claim_audit=[record for result in results for record in result.get("claim_audit", [])],
    )
    return json.loads(report)


def main() -> int:
    parser = argparse.ArgumentParser(description="唯讀影子試用；預設只準備，不呼叫模型")
    parser.add_argument("--repo", required=True)
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True, help="必須是不存在的新目錄")
    parser.add_argument("--run", action="store_true", help="執行模型與工具，會消耗配額")
    args = parser.parse_args()
    validate_claim_gate_mode()
    if not (os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")):
        parser.error("請先提供唯讀 GH_TOKEN；不會自動讀取或輸出本地秘密")
    if args.run and not (get_groq_api_keys() or get_gemini_api_keys()):
        parser.error("執行需要至少一組模型憑證；準備模式不需要模型憑證")
    args.output.mkdir(parents=True, exist_ok=False)
    try:
        with trial_network(allow_models=args.run):
            manifest, files, batches = prepare_trial(args.repo, args.pr)
            (args.output / "manifest.json").write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8",
            )
            if args.run:
                report = execute_trial(manifest, files, batches)
                (args.output / "comparison.json").write_text(
                    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8",
                )
            status = "EXECUTED_NOT_APPROVED_FOR_ROLLOUT" if args.run else "PREPARED_NOT_EXECUTED"
            outcome = {"status": status, "github_writes": False}
            (args.output / "outcome.json").write_text(json.dumps(outcome), encoding="utf-8")
        return 0
    except (SystemExit, RuntimeError, ValueError, OSError, requests.RequestException) as exc:
        outcome = {"status": "FAILED", "error_type": type(exc).__name__, "github_writes": False}
        (args.output / "outcome.json").write_text(json.dumps(outcome), encoding="utf-8")
        print(redact_secrets("試用未完成；請查閱 outcome.json，未發布遠端結果。"))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
