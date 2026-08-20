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
    REPO,
    get_event_path,
    get_repo,
    gh_get,
    normalize_paths,
    publish_failure_report,
    publish_review,
    resolve_pr_number,
    resolve_review_target,
    validate_target_pr,
)
from key_pool import (
    get_gemini_api_keys,
    get_groq_api_keys,
    reset_key_cooldowns,
)
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
        chunk = gh_get(
            f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/files",
            params={"per_page": 100, "page": page},
        )
        files.extend(chunk)
        if len(chunk) < 100:
            break
        page += 1
    return files


def _fetch_file_content_fallback(
    repo_name: str,
    path: str,
    ref: str | None = None,
) -> str | None:
    try:
        url = f"https://api.github.com/repos/{repo_name}/contents/{path}"
        params = {"ref": ref} if ref else None
        data = gh_get(url, params=params)
        if isinstance(data, dict) and data.get("encoding") == "base64" and data.get("content"):
            return base64.b64decode(data["content"]).decode("utf-8", errors="replace")
    except (requests.RequestException, UnicodeDecodeError, ValueError) as exc:
        logging.warning("無法自 Contents API 讀取檔案 %s: %s", path, exc)
    return None


def _process_batch(
    scope: str,
    index: int,
    paths: list[str],
    files: list[dict],
    rules_text: str,
    contract_text: str,
    policy: dict,
    pr_number: int,
    repo_name: str = "",
    head_sha: str = "",
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
        details = json.loads(str(exc))
        publish_failure_report(
            pr_number, "AI Provider 呼叫失敗",
            "所有已配置的 AI 模型/金鑰（Google Gemini / Groq）均無法取得有效回應。", details,
        )
        raise SystemExit(f"AI Provider 無法使用：{details}")

    try:
        batch_data = extract_json_payload(text_output)
    except json.JSONDecodeError as exc:
        publish_failure_report(
            pr_number, "AI 模型輸出非合法 JSON",
            f"批次 {scope}-{index} 模型輸出解析失敗：{exc}", text_output[:500],
        )
        raise SystemExit(f"批次 {scope}-{index} JSON 解析失敗：{exc}")

    norm_exp, norm_rev = normalize_paths(paths), normalize_paths(batch_data.get("files_reviewed"))
    is_cov_valid = (
        str(batch_data.get("coverage", "")).strip().upper() == "COMPLETE"
        and validate_coverage(norm_exp, norm_rev)
    )
    if not is_cov_valid:
        cov_err = f"批次覆蓋範圍驗證失敗：{scope}-{index}\n預期：{paths}\n模型回傳：{norm_rev}"
        publish_failure_report(pr_number, "批次覆蓋範圍不符", cov_err)
        raise SystemExit(cov_err)

    if missing_findings:
        batch_data.setdefault("findings", []).extend(missing_findings)

    for finding in batch_data.get("findings", []):
        if not validate_finding(finding, allowed_files=paths):
            schema_err = f"批次 {scope}-{index} 中的 Finding 未通過格式或範圍驗證。"
            publish_failure_report(pr_number, "Finding 格式驗證失敗", schema_err, finding)
            raise SystemExit(schema_err)

    return batch_data


def main() -> None:
    if not get_gh_token() or (not get_groq_api_keys() and not get_gemini_api_keys()):
        raise SystemExit(
            "未配置必要的信任密鑰（GH_TOKEN 與至少一組 AI Provider 密鑰）。"
        )

    event_path_str = get_event_path()
    if not event_path_str or not Path(event_path_str).exists():
        raise SystemExit(f"找不到 GitHub 事件檔案：{event_path_str}")

    event = json.loads(Path(event_path_str).read_text(encoding="utf-8"))
    target = resolve_review_target(event)
    pr_number = target["pr_number"]
    expected_sha = target.get("expected_head_sha") or target.get("head_sha")
    repo_name = get_repo() or REPO

    try:
        pull_request_data = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(pr_number, "無法取得 PR 資訊", str(exc))
        raise SystemExit(f"無法取得 PR 資訊：{exc}")

    is_valid, validation_err = validate_target_pr(
        pull_request_data,
        expected_head_sha=expected_sha,
    )
    if not is_valid:
        err_msg = f"PR #{pr_number} 未通過目標校驗（{validation_err}），中止審查。"
        publish_failure_report(pr_number, "目標 PR 校驗未通過", err_msg)
        raise SystemExit(err_msg)

    try:
        files = _fetch_all_pr_files(repo_name, pr_number)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        publish_failure_report(pr_number, "無法取得 PR 變更檔案清單", str(exc))
        raise SystemExit(f"無法取得 PR 變更檔案清單：{exc}")

    changed_files = [item["filename"] for item in files]
    if not changed_files:
        print(f"PR #{pr_number} 未包含任何變更檔案。")
        return

    rules_path = ROOT / "開發規範.md"
    contract_path = ROOT / ".github/AI_REVIEW.md"
    rules_text = rules_path.read_text(encoding="utf-8") if rules_path.exists() else ""
    contract_text = contract_path.read_text(encoding="utf-8") if contract_path.exists() else ""
    policy = load_policy()
    max_batch_chars = int(os.environ.get("AI_REVIEW_MAX_BATCH_CHARS", "24000"))
    batches = build_batches(files, max_chars=max_batch_chars)

    expected_files = [fn for _, paths in batches for fn in paths]
    is_batch_valid = (
        sorted(expected_files) == sorted(changed_files)
        and len(expected_files) == len(set(expected_files))
    )
    if not is_batch_valid:
        mismatch_msg = "覆蓋範圍規劃不符：每個變更檔案必須屬於且僅屬於一個批次。"
        publish_failure_report(pr_number, "批次檔案規劃異常", mismatch_msg)
        raise SystemExit(mismatch_msg)

    head_sha = pull_request_data.get("head", {}).get("sha", "")
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
        fn for d in results for fn in normalize_paths(d.get("files_reviewed", []))
    ]
    llm_findings = [f for d in results for f in d.get("findings", [])]
    for finding in llm_findings:
        if not validate_finding(finding, allowed_files=expected_files):
            invalid_err = f"發現超出本次 PR 範圍之 Finding：{finding.get('location')}"
            publish_failure_report(pr_number, "Finding 超出 PR 範圍", invalid_err, finding)
            raise SystemExit(invalid_err)
    passed_checks = [c for d in results for c in d.get("passed_checks", [])]

    # 執行確定性靜態規則檢查
    static_findings = run_static_checks(files)
    all_findings = llm_findings + static_findings

    eval_result = evaluate(all_findings, normalize_paths(expected_files), reviewed_files, policy)
    decision = eval_result["decision"]
    unique_findings = eval_result["findings"]
    blocking_findings = eval_result["blocking_findings"]

    # 發布前二次驗證 head_sha，阻斷 TOCTOU 競態衝突
    try:
        latest_pr = gh_get(f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}")
        latest_head_sha = latest_pr.get("head", {}).get("sha", "")
        if not latest_head_sha:
            toctou_err = f"無法取得 PR #{pr_number} 最新 head SHA，拒絕發布過期 Review。"
            publish_failure_report(pr_number, "無法驗證最新 PR head SHA", toctou_err)
            raise SystemExit(toctou_err)
        if latest_head_sha != head_sha:
            toctou_err = (
                f"TOCTOU 衝突：PR #{pr_number} head SHA 於審查期間已變更 "
                f"({head_sha[:8]} -> {latest_head_sha[:8]})，中止本次過期審查。"
            )
            publish_failure_report(pr_number, "審查目標過期 (TOCTOU)", toctou_err)
            raise SystemExit(toctou_err)
    except (requests.RequestException, json.JSONDecodeError, KeyError) as exc:
        err_msg = f"發布前二次 PR 校驗失敗：{exc}"
        publish_failure_report(pr_number, "發布前二次 PR 校驗失敗", err_msg)
        raise SystemExit(err_msg)

    audit_info = {
        "trigger_type": target.get("trigger_type", "unknown"),
        "actor": target.get("actor", "unknown"),
        "head_sha": head_sha,
    }
    body = format_markdown_report(
        decision, changed_files, results, unique_findings, blocking_findings,
        passed_checks, audit_info=audit_info,
    )
    publish_review(pr_number, body, decision)
    Path("review.md").write_text(body + "\n", encoding="utf-8")
    json_report = format_json_report(
        decision, unique_findings, blocking_findings, len(results),
        changed_files, audit_info=audit_info,
    )
    Path("ai-review.json").write_text(json_report, encoding="utf-8")
    print(body)
    if decision == "REQUEST_CHANGES":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
