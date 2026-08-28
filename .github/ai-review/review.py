import base64
import json
import logging
import os
import time
from pathlib import Path
import requests

from batching import build_batches
from engine import evaluate, load_policy, validate_coverage, validate_finding
from github_client import (
    REPO, get_event_path, get_repo, gh_get, normalize_paths,
    publish_failure_report, publish_review, resolve_review_target, validate_target_pr,
)
from key_pool import get_gemini_api_keys, get_groq_api_keys
from orchestrator import chat_completion
from parser import extract_json_payload
from prompt_builder import build_batch_prompt, filter_relevant_rules
from redaction import get_gh_token, sanitize_diff
from reporter import format_json_report, format_markdown_report
from static_checks import run_static_checks

ROOT = Path(__file__).resolve().parents[2]


def _fetch_all_pr_files(repo_name: str, pr_number: int) -> list[dict]:
    files, page = [], 1
    while True:
        url = f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/files"
        chunk = gh_get(url, params={"per_page": 100, "page": page})
        files.extend(chunk)
        if len(chunk) < 100:
            break
        page += 1
    return files


def _fetch_file_content_fallback(
    repo_name: str, path: str, ref: str | None = None
) -> str | None:
    try:
        url = f"https://api.github.com/repos/{repo_name}/contents/{path}"
        data = gh_get(url, params={"ref": ref} if ref else None)
        if isinstance(data, dict) and data.get("encoding") == "base64" and data.get("content"):
            return base64.b64decode(data["content"]).decode("utf-8", errors="replace")
    except (requests.RequestException, UnicodeDecodeError, ValueError) as exc:
        logging.warning("無法自 Contents API 讀取檔案 %s: %s", path, exc)
    return None


def _process_batch(
    scope: str, index: int, paths: list[str], files: list[dict],
    rules_text: str, contract_text: str, policy: dict, pr_number: int,
    repo_name: str = "", head_sha: str = "",
) -> dict:
    if index > 1:
        print("批次間隔節流：等待 5 秒以平滑 API 速率限制...")
        time.sleep(5.0)

    relevant_rules = filter_relevant_rules(rules_text, scope)
    diff_parts, missing_findings = [], []

    for file_item in files:
        fname = file_item.get("filename", "")
        if fname in paths:
            raw_patch = file_item.get("patch")
            if not raw_patch and file_item.get("status") not in ("removed", "deleted"):
                fallback = file_item.get("full_content") or _fetch_file_content_fallback(
                    repo_name, fname, head_sha
                )
                if fallback is not None:
                    file_item["full_content"] = file_item["content"] = fallback
                    raw_patch = f"[Content fallback fetched]\n{fallback}"
                else:
                    missing_findings.append({
                        "location": f"{fname}:1", "severity": "HIGH", "confidence": "HIGH",
                        "rule": "開發規範 §5 Review 完整性與可見度",
                        "problem": f"無法取得檔案 {fname} 的 Patch 或原始內容",
                        "evidence": "GitHub API 未提供 patch 且 Contents API 讀取失敗",
                        "risk": "變更內容未經審查即合併可能引入潛在安全缺陷",
                        "recommendation": "手動檢查該檔案變更內容，確認符合架構與安全規範",
                    })
                    raw_patch = f"[Patch unavailable; status={file_item.get('status', 'unknown')}]"
            diff_parts.append(f"diff -- {fname}\n{sanitize_diff(raw_patch or '[Empty diff]')}")

    diff = sanitize_diff("\n\n".join(diff_parts))
    prompt = build_batch_prompt(scope, index, paths, diff, contract_text, relevant_rules)
    try:
        text_output = chat_completion(prompt).strip()
    except RuntimeError as exc:
        try:
            details = json.loads(str(exc))
        except (json.JSONDecodeError, ValueError):
            details = str(exc)
        publish_failure_report(
            pr_number, "AI Provider 呼叫失敗", "所有已配置的 AI 模型/金鑰均無法取得有效回應。",
            details, commit_id=head_sha, status_type="AI_PROVIDER_FAILED",
        )
        raise SystemExit(f"AI Provider 無法使用：{details}")

    try:
        batch_data = extract_json_payload(text_output)
    except json.JSONDecodeError as exc:
        publish_failure_report(
            pr_number, "AI 模型輸出非合法 JSON",
            f"批次 {scope}-{index} 模型輸出解析失敗：{exc}",
            text_output[:500], commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(f"批次 {scope}-{index} JSON 解析失敗：{exc}")

    norm_exp, norm_rev = normalize_paths(paths), normalize_paths(batch_data.get("files_reviewed"))
    is_cov_complete = str(batch_data.get("coverage", "")).strip().upper() == "COMPLETE"
    if not is_cov_complete or not validate_coverage(norm_exp, norm_rev):
        cov_err = f"批次覆蓋範圍驗證失敗：{scope}-{index}\n預期：{paths}\n模型回傳：{norm_rev}"
        publish_failure_report(
            pr_number, "批次覆蓋範圍不符", cov_err,
            commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(cov_err)

    if missing_findings:
        batch_data.setdefault("findings", []).extend(missing_findings)
    for finding in batch_data.get("findings", []):
        if not validate_finding(finding, allowed_files=paths):
            schema_err = f"批次 {scope}-{index} 中的 Finding 未通過格式或範圍驗證。"
            publish_failure_report(
                pr_number, "Finding 格式驗證失敗", schema_err,
                finding, commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
            )
            raise SystemExit(schema_err)
    return batch_data


def main() -> None:
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
            None, "審查目標解析失敗", str(exc),
            commit_id=head_sha, status_type="TARGET_RESOLUTION_FAILED",
        )
        raise

    repo_name = get_repo() or REPO
    try:
        pr_data = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(
            pr_number, "無法取得 PR 資訊", str(exc),
            commit_id=expected_sha, status_type="TARGET_VALIDATION_FAILED",
        )
        raise SystemExit(f"無法取得 PR 資訊：{exc}")

    is_valid, val_err = validate_target_pr(pr_data, expected_head_sha=expected_sha)
    if not is_valid:
        err_msg = f"PR #{pr_number} 未通過目標校驗（{val_err}），中止審查。"
        publish_failure_report(
            pr_number, "目標 PR 校驗未通過", err_msg,
            commit_id=expected_sha, status_type="TARGET_VALIDATION_FAILED",
        )
        raise SystemExit(err_msg)

    try:
        files = _fetch_all_pr_files(repo_name, pr_number)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(
            pr_number, "無法取得 PR 變更檔案清單", str(exc),
            commit_id=expected_sha, status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(f"無法取得 PR 變更檔案清單：{exc}")

    changed_files = [item["filename"] for item in files]
    if not changed_files:
        print(f"PR #{pr_number} 未包含任何變更檔案。")
        return

    rules_p, contract_p = ROOT / "開發規範.md", ROOT / ".github/AI_REVIEW.md"
    rules_text = rules_p.read_text(encoding="utf-8") if rules_p.exists() else ""
    contract_text = contract_p.read_text(encoding="utf-8") if contract_p.exists() else ""
    policy = load_policy()
    max_batch_chars = int(os.environ.get("AI_REVIEW_MAX_BATCH_CHARS", "24000"))
    batches = build_batches(files, max_chars=max_batch_chars)

    expected_files = [fname for _, paths in batches for fname in paths]
    is_batch_valid = (
        sorted(expected_files) == sorted(changed_files)
        and len(expected_files) == len(set(expected_files))
    )
    if not is_batch_valid:
        mismatch_msg = "覆蓋範圍規劃不符：每個變更檔案必須屬於且僅屬於一個批次。"
        publish_failure_report(
            pr_number, "批次檔案規劃異常", mismatch_msg,
            commit_id=expected_sha, status_type="REVIEW_EXECUTION_FAILED",
        )
        raise SystemExit(mismatch_msg)

    head_sha = pr_data.get("head", {}).get("sha", "")
    for file_item in files:
        if file_item.get("status") not in ("removed", "deleted"):
            blob = _fetch_file_content_fallback(repo_name, file_item.get("filename", ""), head_sha)
            if blob is not None:
                file_item["full_content"] = blob

    results = [
        _process_batch(
            scope, idx, paths, files, rules_text, contract_text,
            policy, pr_number, repo_name=repo_name, head_sha=head_sha,
        )
        for idx, (scope, paths) in enumerate(batches, 1)
    ]
    reviewed_files = [
        file_name for res_item in results
        for file_name in normalize_paths(res_item.get("files_reviewed", []))
    ]
    llm_findings = [
        finding_item for res_item in results
        for finding_item in res_item.get("findings", [])
    ]
    for finding in llm_findings:
        if not validate_finding(finding, allowed_files=expected_files):
            invalid_err = f"發現超出本次 PR 範圍之 Finding：{finding.get('location')}"
            publish_failure_report(
                pr_number, "Finding 超出 PR 範圍", invalid_err, finding,
                commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
            )
            raise SystemExit(invalid_err)
    passed_checks = [chk for res in results for chk in res.get("passed_checks", [])]

    static_findings = run_static_checks(files)
    eval_result = evaluate(
        llm_findings + static_findings, normalize_paths(expected_files), reviewed_files, policy
    )
    decision = eval_result["decision"]
    unique_findings = eval_result["findings"]
    blocking_findings = eval_result["blocking_findings"]
    requires_human = eval_result.get("requires_human_review", False)
    high_risk_files = eval_result.get("high_risk_files", [])

    try:
        latest_pr = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
        latest_head_sha = latest_pr.get("head", {}).get("sha", "")
        if not latest_head_sha or latest_head_sha != head_sha:
            toctou_err = (
                f"TOCTOU 衝突：PR #{pr_number} head SHA 於審查期間變更或缺失 "
                f"({head_sha[:8]} -> {str(latest_head_sha)[:8]})，中止審查。"
            )
            publish_failure_report(
                pr_number, "審查目標過期 (TOCTOU)", toctou_err,
                commit_id=head_sha, status_type="TOCTOU_CONFLICT",
            )
            raise SystemExit(toctou_err)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        err_msg = f"發布前二次 PR 校驗失敗：{exc}"
        publish_failure_report(
            pr_number, "發布前二次 PR 校驗失敗", err_msg,
            commit_id=head_sha, status_type="TOCTOU_CONFLICT",
        )
        raise SystemExit(err_msg)

    audit_info = {
        "trigger_type": target.get("trigger_type", "unknown"),
        "actor": target.get("actor", "unknown"), "head_sha": head_sha,
    }
    body = format_markdown_report(
        decision, changed_files, results, unique_findings, blocking_findings,
        passed_checks, audit_info=audit_info,
        requires_human_review=requires_human, high_risk_files=high_risk_files,
    )
    publish_review(
        pr_number, body, decision, commit_id=head_sha, requires_human_review=requires_human,
    )
    Path("review.md").write_text(body + "\n", encoding="utf-8")
    json_report = format_json_report(
        decision, unique_findings, blocking_findings, len(results),
        changed_files, audit_info=audit_info,
        requires_human_review=requires_human, high_risk_files=high_risk_files,
    )
    Path("ai-review.json").write_text(json_report, encoding="utf-8")
    print(body)
    if decision != "APPROVE":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
