import json
import os
import time
from pathlib import Path

from batching import build_batches
from engine import evaluate, load_policy, validate_coverage, validate_finding
from github_client import (
    EVENT_PATH,
    GH_TOKEN,
    REPO,
    REVIEW_MARKER,
    get_event_path,
    get_github_headers,
    get_repo,
    gh_get,
    normalize_path,
    normalize_paths,
    post_issue_comment,
    post_pr_review,
    publish_failure_report,
    publish_review,
    resolve_pr_number,
)
from key_pool import (
    GLOBAL_KEY_POOL_GEMINI,
    GLOBAL_KEY_POOL_GROQ,
    GROQ_API_KEY,
    KEY_COOLDOWN_MAP,
    KeyPool,
    get_active_keys,
    get_gemini_api_keys,
    get_groq_api_key,
    get_groq_api_keys,
    get_key_cooldown_remaining,
    get_provider_api_keys,
    is_key_in_cooldown,
    mark_key_cooldown,
    mask_api_key,
    pick_random_active_key,
    reset_key_cooldowns,
)
from model_pool import (
    ACTIVE_GEMINI_MODELS,
    ACTIVE_MODEL_CANDIDATES,
    DEFAULT_GEMINI_MODELS,
    DEFAULT_MODEL_CANDIDATES,
    GLOBAL_MODEL_POOL_GEMINI,
    GLOBAL_MODEL_POOL_GROQ,
    ModelPool,
    get_gemini_candidate_models,
)
from orchestrator import (
    DEFAULT_ORCHESTRATOR,
    ReviewOrchestrator,
    chat_completion,
    get_candidate_models,
)
from parser import (
    ReviewResponseParser,
    extract_json_payload,
    find_balanced_json_substrings,
    repair_json_string,
)
from prompt_builder import build_batch_prompt, filter_relevant_rules
from providers import (
    GeminiClient,
    GroqClient,
    call_gemini_api,
    extract_gemini_text,
    get_available_models,
)
from redaction import get_gh_token, redact_secrets
from reporter import format_json_report, format_markdown_report
from retry_utils import (
    DEFAULT_MAX_RETRIES_PER_MODEL,
    MAX_RETRY_LIMIT,
    calculate_backoff_delay,
    parse_retry_after,
    parse_retry_limit,
)

ROOT = Path(__file__).resolve().parents[2]


def _fetch_all_pr_files(repo_name: str, pr_number: int) -> list[dict]:
    files = []
    page = 1
    while True:
        file_chunk = gh_get(
            f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/files",
            params={"per_page": 100, "page": page},
        )
        files.extend(file_chunk)
        if len(file_chunk) < 100:
            break
        page += 1
    return files


def _process_batch(
    scope: str,
    index: int,
    paths: list[str],
    files: list[dict],
    rules_text: str,
    contract_text: str,
    policy: dict,
    pr_number: int,
) -> dict:
    if index > 1:
        print("批次間隔節流：等待 5 秒以平滑 API 速率限制...")
        time.sleep(5.0)

    relevant_rules = filter_relevant_rules(rules_text, scope)

    diff_parts = []
    for file_item in files:
        if file_item["filename"] in paths:
            patch_text = (
                file_item.get("patch")
                or f"[GitHub patch not provided; status={file_item.get('status', 'unknown')}, changes={file_item.get('changes', 0)}]"
            )
            diff_parts.append(f"diff -- {file_item['filename']}\n{patch_text}")
    diff = "\n\n".join(diff_parts)

    prompt = build_batch_prompt(scope, index, paths, diff, contract_text, relevant_rules)

    try:
        text_output = chat_completion(prompt).strip()
    except RuntimeError as exc:
        error_details = json.loads(str(exc))
        publish_failure_report(
            pr_number,
            "AI Provider 呼叫失敗",
            "所有已配置的 AI 模型/金鑰（Google Gemini / Groq）均無法取得有效回應。",
            error_details,
        )
        raise SystemExit(f"AI Provider 無法使用：{error_details}")

    try:
        batch_data = extract_json_payload(text_output)
    except json.JSONDecodeError as exc:
        publish_failure_report(
            pr_number,
            "AI 模型輸出非合法 JSON",
            f"批次 {scope}-{index} 模型輸出解析失敗：{exc}",
            text_output[:500],
        )
        raise SystemExit(f"批次 {scope}-{index} JSON 解析失敗：{exc}")

    norm_expected = normalize_paths(paths)
    raw_reviewed = batch_data.get("files_reviewed")
    norm_reviewed = normalize_paths(raw_reviewed)
    coverage_status = str(batch_data.get("coverage", "")).strip().upper()

    if coverage_status != "COMPLETE" or not validate_coverage(norm_expected, norm_reviewed):
        cov_err = (
            f"批次覆蓋範圍驗證失敗：{scope}-{index}\n"
            f"- 預期檔案：{paths}\n"
            f"- 模型回傳檔案：{raw_reviewed}\n"
            f"- 覆蓋標記：{batch_data.get('coverage')}"
        )
        publish_failure_report(pr_number, "批次覆蓋範圍不符", cov_err)
        raise SystemExit(cov_err)

    for finding in batch_data.get("findings", []):
        if not validate_finding(finding, policy):
            schema_err = f"批次 {scope}-{index} 中的 Finding 未通過格式驗證。"
            publish_failure_report(pr_number, "Finding 格式驗證失敗", schema_err, finding)
            raise SystemExit(schema_err)

    return batch_data


def main():
    if not get_gh_token() or (not get_groq_api_keys() and not get_gemini_api_keys()):
        raise SystemExit(
            "未配置必要的信任密鑰（GH_TOKEN 與至少一組 AI Provider 密鑰：GROQ_API_KEY_* 或 GEMINI_API_KEY_*）。"
        )

    event_path_str = get_event_path()
    if not event_path_str or not Path(event_path_str).exists():
        raise SystemExit(f"找不到 GitHub 事件檔案：{event_path_str}")

    event = json.loads(Path(event_path_str).read_text(encoding="utf-8"))
    pr_number = resolve_pr_number(event)

    try:
        pull_request_data = gh_get(f"https://api.github.com/repos/{REPO}/pulls/{pr_number}")
    except Exception as exc:
        publish_failure_report(pr_number, "無法取得 PR 資訊", str(exc))
        raise SystemExit(f"無法取得 PR 資訊：{exc}")

    if pull_request_data.get("state") != "open":
        print(f"PR #{pr_number} 目前非開啟狀態（state: {pull_request_data.get('state')}），略過審查發布。")
        return

    try:
        files = _fetch_all_pr_files(REPO, pr_number)
    except Exception as exc:
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
    if sorted(expected_files) != sorted(changed_files) or (
        len(expected_files) != len(set(expected_files))
    ):
        mismatch_msg = "覆蓋範圍規劃不符：每個變更檔案必須屬於且僅屬於一個批次。"
        publish_failure_report(pr_number, "批次檔案規劃異常", mismatch_msg)
        raise SystemExit(mismatch_msg)

    results = [
        _process_batch(
            scope, idx, paths, files, rules_text, contract_text, policy, pr_number
        )
        for idx, (scope, paths) in enumerate(batches, 1)
    ]

    reviewed_files = [
        fn for d in results for fn in normalize_paths(d.get("files_reviewed", []))
    ]
    findings = [f for d in results for f in d.get("findings", [])]
    passed_checks = [c for d in results for c in d.get("passed_checks", [])]

    eval_result = evaluate(findings, normalize_paths(expected_files), reviewed_files, policy)
    decision = eval_result["decision"]
    unique_findings = eval_result["findings"]
    blocking_findings = eval_result["blocking_findings"]

    body = format_markdown_report(
        decision, changed_files, results, unique_findings, blocking_findings, passed_checks
    )
    publish_review(pr_number, body, decision)
    Path("review.md").write_text(body + "\n", encoding="utf-8")
    Path("ai-review.json").write_text(
        format_json_report(decision, unique_findings, blocking_findings, len(results), changed_files),
        encoding="utf-8",
    )
    print(body)
    if decision == "REQUEST_CHANGES":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
