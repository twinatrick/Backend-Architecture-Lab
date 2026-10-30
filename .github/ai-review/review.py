import json
import os
import time
from pathlib import Path
from typing import Any

import requests

from batch_runner import (
    enrich_files_with_full_content,
    fetch_all_pr_files,
    fetch_file_content_fallback,
    process_batch,
    process_batches_parallel,
)
from batching import build_batches
from claim_verifier import validate_claim_gate_mode
from engine import load_policy, validate_finding
from github_client import (
    REPO,
    get_event_path,
    get_repo,
    gh_get,
    normalize_paths,
    publish_failure_report,
    publish_review,
    resolve_review_target,
    validate_target_pr,
)
from key_pool import get_gemini_api_keys, get_groq_api_keys
from model_pool import get_gemini_candidate_models
from orchestrator import chat_completion
from redaction import get_gh_token
from reporter import format_json_report, format_markdown_report
from snapshot_static_checks import evaluate_verified_review

ROOT = Path(__file__).resolve().parents[2]

# 提供既有模組與單元測試相容之包裝介面
def _fetch_file_content_fallback(repo_name: str, path: str, ref: str | None = None) -> str | None:
    return fetch_file_content_fallback(repo_name, path, ref=ref, gh_get_fn=gh_get)


def _process_batch(
    scope: str,
    index: int,
    paths: list[str],
    files: list[dict[str, Any]],
    rules_text: str,
    contract_text: str,
    policy: dict[str, Any],
    pr_number: int,
    repo_name: str = "",
    head_sha: str = "",
    delay: float = 0.0,
) -> dict[str, Any]:
    return process_batch(
        scope, index, paths, files, rules_text, contract_text, policy,
        pr_number, repo_name=repo_name, head_sha=head_sha, delay=delay,
        chat_fn=chat_completion, gh_get_fn=gh_get,
    )


def _process_batches_parallel(
    batches: list[tuple[str, list[str]]],
    files: list[dict[str, Any]],
    rules_text: str,
    contract_text: str,
    policy: dict[str, Any],
    pr_number: int,
    repo_name: str = "",
    head_sha: str = "",
    max_workers: int = 3,
    stagger_seconds: float = 1.5,
    base_sha: str = "",
) -> list[dict[str, Any]]:
    return process_batches_parallel(
        batches, files, rules_text, contract_text, policy, pr_number,
        repo_name=repo_name, head_sha=head_sha, max_workers=max_workers,
        stagger_seconds=stagger_seconds, chat_fn=chat_completion, gh_get_fn=gh_get,
        base_sha=base_sha,
    )


def _fetch_all_pr_files(repo_name: str, pr_number: int) -> list[dict[str, Any]]:
    return fetch_all_pr_files(repo_name, pr_number, gh_get_fn=gh_get)


def main() -> None:
    validate_claim_gate_mode()
    if not get_gh_token() or (not get_groq_api_keys() and not get_gemini_api_keys()):
        raise SystemExit("未配置必要的信任密鑰（GH_TOKEN 與至少一組 AI Provider 密鑰）。")

    event_path_str = get_event_path()
    if not event_path_str or not Path(event_path_str).exists():
        raise SystemExit(f"找不到 GitHub 事件檔案：{event_path_str}")

    event = json.loads(Path(event_path_str).read_text(encoding="utf-8"))
    try:
        target = resolve_review_target(event)
        pr_number = target["pr_number"]
        expected_sha = target.get("expected_head_sha") or target.get("head_sha")
    except SystemExit as exc:
        head_sha = (event.get("workflow_run") or {}).get("head_sha") or event.get("after")
        publish_failure_report(
            None,
            "審查目標解析失敗",
            str(exc),
            commit_id=head_sha,
            status_type="TARGET_RESOLUTION_FAILED",
        )
        raise

    repo_name = get_repo() or REPO
    try:
        pr_data = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(
            pr_number,
            "無法取得 PR 資訊",
            str(exc),
            commit_id=expected_sha,
            status_type="TARGET_VALIDATION_FAILED",
        )
        raise SystemExit(f"無法取得 PR 資訊：{exc}")

    is_valid, val_err = validate_target_pr(pr_data, expected_head_sha=expected_sha)
    if not is_valid:
        err_msg = f"PR #{pr_number} 未通過目標校驗（{val_err}），中止審查。"
        publish_failure_report(
            pr_number,
            "目標 PR 校驗未通過",
            err_msg,
            commit_id=expected_sha,
            status_type="TARGET_VALIDATION_FAILED",
        )
        raise SystemExit(err_msg)

    try:
        files = _fetch_all_pr_files(repo_name, pr_number)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(
            pr_number,
            "無法取得 PR 變更檔案清單",
            str(exc),
            commit_id=expected_sha,
            status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(f"無法取得 PR 變更檔案清單：{exc}")

    changed_files = [item["filename"] for item in files]
    if not changed_files:
        print(f"PR #{pr_number} 未包含任何變更檔案。")
        return

    rules_p = ROOT / "開發規範.md"
    contract_p = ROOT / ".github/AI_REVIEW.md"
    rules_text = rules_p.read_text(encoding="utf-8") if rules_p.exists() else ""
    contract_text = contract_p.read_text(encoding="utf-8") if contract_p.exists() else ""
    policy = load_policy()
    batches = build_batches(files, enable_dual_track=True)

    expected_files = [fname for _, paths in batches for fname in paths]
    is_batch_valid = (
        sorted(expected_files) == sorted(changed_files)
        and len(expected_files) == len(set(expected_files))
    )
    if not is_batch_valid:
        mismatch_msg = "覆蓋範圍規劃不符：每個變更檔案必須屬於且僅屬於一個批次。"
        publish_failure_report(
            pr_number,
            "批次檔案規劃異常",
            mismatch_msg,
            commit_id=expected_sha,
            status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(mismatch_msg)

    head_sha = pr_data.get("head", {}).get("sha", "")
    enrich_files_with_full_content(repo_name, files, head_sha, gh_get_fn=gh_get)

    default_workers = "6" if len(batches) > 4 else "4"
    default_stagger = "0.8" if len(batches) > 4 else "1.0"
    max_workers = int(os.environ.get("AI_REVIEW_MAX_WORKERS", default_workers))
    stagger_seconds = float(os.environ.get("AI_REVIEW_STAGGER_SECONDS", default_stagger))
    shadow_deadline = time.monotonic() + 900
    results = _process_batches_parallel(
        batches, files, rules_text, contract_text, policy, pr_number,
        repo_name=repo_name, head_sha=head_sha,
        base_sha=pr_data.get("base", {}).get("sha", ""),
        max_workers=max_workers, stagger_seconds=stagger_seconds,
    )
    reviewed_files = [file_name for res_item in results
        for file_name in normalize_paths(res_item.get("files_reviewed", []))
    ]
    llm_findings = [
        finding_item
        for res_item in results
        for finding_item in res_item.get("findings", [])
    ]
    for finding in llm_findings:
        if not validate_finding(finding, allowed_files=expected_files):
            invalid_err = f"發現超出本次 PR 範圍之 Finding：{finding.get('location')}"
            publish_failure_report(
                pr_number,
                "Finding 超出 PR 範圍",
                invalid_err,
                finding,
                commit_id=head_sha,
                status_type="REVIEW_EXECUTION_FAILED",
            )
            raise SystemExit(invalid_err)
    passed_checks = [chk for res in results for chk in res.get("passed_checks", [])]
    try:
        eval_result = evaluate_verified_review(
            results, files, normalize_paths(expected_files), reviewed_files, policy,
            repo_name, head_sha, gh_get, shadow_deadline,
            pr_data.get("base", {}).get("sha", ""), pr_number,
        )
    except (RuntimeError, ValueError, requests.RequestException):
        publish_failure_report(
            pr_number, "工具查證未完成", "缺少有效查證結果，禁止退回舊引擎或自動通過。",
            commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit("工具查證未完成；正式閘門維持阻擋")
    decision, unique_findings, blocking_findings = (
        eval_result["decision"], eval_result["findings"], eval_result["blocking_findings"]
    )
    requires_human = eval_result.get("requires_human_review", False)
    high_risk_files = eval_result.get("high_risk_files", [])

    try:
        latest_pr = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
        latest_head_sha = latest_pr.get("head", {}).get("sha", "")
        if (not latest_head_sha or latest_head_sha != head_sha
                or latest_pr.get("base", {}).get("sha") != pr_data.get("base", {}).get("sha")):
            toctou_err = (
                f"TOCTOU 衝突：PR #{pr_number} head SHA 於審查期間變更或缺失 "
                f"({head_sha[:8]} -> {str(latest_head_sha)[:8]})，中止審查。"
            )
            publish_failure_report(
                pr_number,
                "審查目標過期 (TOCTOU)",
                toctou_err,
                commit_id=head_sha,
                status_type="TOCTOU_CONFLICT",
            )
            raise SystemExit(toctou_err)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        err_msg = f"發布前二次 PR 校驗失敗：{exc}"
        publish_failure_report(
            pr_number,
            "發布前二次 PR 校驗失敗",
            err_msg,
            commit_id=head_sha,
            status_type="TOCTOU_CONFLICT",
        )
        raise SystemExit(err_msg)

    audit_info = {
        "trigger_type": target.get("trigger_type", "unknown"),
        "actor": target.get("actor", "unknown"),
        "head_sha": head_sha,
    }
    body = format_markdown_report(
        decision,
        changed_files,
        results,
        unique_findings,
        blocking_findings,
        passed_checks,
        audit_info=audit_info,
        requires_human_review=requires_human,
        high_risk_files=high_risk_files,
        shadow_review=eval_result,
    )
    publish_review(
        pr_number, body, decision,
        commit_id=head_sha,
        requires_human_review=requires_human,
    )
    Path("review.md").write_text(body + "\n", encoding="utf-8")
    json_report = format_json_report(
        decision, unique_findings, blocking_findings,
        len(results), changed_files,
        audit_info=audit_info,
        requires_human_review=requires_human,
        high_risk_files=high_risk_files,
        shadow_review=eval_result,
        claim_audit=[item for result in results for item in result.get("claim_audit", [])],
    )
    Path("ai-review.json").write_text(json_report, encoding="utf-8")
    print(body)
    if decision != "APPROVE":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
