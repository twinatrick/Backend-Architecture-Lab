import base64
import concurrent.futures
import json
import logging
import time
from typing import Any

import requests

from batching import estimate_tokens
from engine import validate_coverage, validate_finding
from github_client import gh_get, normalize_paths, publish_failure_report
from model_pool import GROQ_CAPACITY
from orchestrator import chat_completion
from parser import extract_json_payload, normalize_batch_paths
from prompt_builder import build_batch_prompt, filter_relevant_rules
from redaction import sanitize_diff


def fetch_all_pr_files(
    repo_name: str, pr_number: int, gh_get_fn: Any = None
) -> list[dict[str, Any]]:
    """分頁讀取 PR 變更的所有檔案清單。"""
    getter = gh_get_fn or gh_get
    files: list[dict[str, Any]] = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/files"
        chunk = getter(url, params={"per_page": 100, "page": page})
        files.extend(chunk)
        if len(chunk) < 100:
            break
        page += 1
    return files


def fetch_file_content_fallback(
    repo_name: str, path: str, ref: str | None = None, gh_get_fn: Any = None
) -> str | None:
    """透過 GitHub Contents API 補齊 Patch 缺失之檔案原始內容。"""
    getter = gh_get_fn or gh_get
    try:
        url = f"https://api.github.com/repos/{repo_name}/contents/{path}"
        data = getter(url, params={"ref": ref} if ref else None)
        if isinstance(data, dict) and data.get("encoding") == "base64" and data.get("content"):
            return base64.b64decode(data["content"]).decode("utf-8", errors="replace")
    except (requests.RequestException, UnicodeDecodeError, ValueError, KeyError) as exc:
        logging.warning("無法自 Contents API 讀取檔案 %s: %s", path, exc)
    return None


def enrich_files_with_full_content(
    repo_name: str, files: list[dict[str, Any]], head_sha: str, gh_get_fn: Any = None
) -> None:
    """針對缺少 patch 且未被刪除的檔案，嘗試透過 Contents API 補充完整內容。"""
    for file_item in files:
        if (
            file_item.get("status") not in ("removed", "deleted")
            and not file_item.get("patch")
            and not file_item.get("full_content")
        ):
            blob = fetch_file_content_fallback(
                repo_name, file_item.get("filename", ""), head_sha, gh_get_fn=gh_get_fn
            )
            if blob is not None:
                file_item["full_content"] = blob


def process_batch(
    scope: str, index: int, paths: list[str], files: list[dict[str, Any]],
    rules_text: str, contract_text: str, policy: dict[str, Any], pr_number: int,
    repo_name: str = "", head_sha: str = "", delay: float = 0.0,
    chat_fn: Any = None, gh_get_fn: Any = None,
) -> dict[str, Any]:
    """執行單一批次的 LLM 提示建構、呼叫、回傳解析與安全結構驗證（支援自動重發）。"""
    if delay > 0:
        print(f"批次間隔節流：等待 {delay:.1f} 秒以平滑 API 速率限制...")
        time.sleep(delay)

    caller = chat_fn or chat_completion
    getter = gh_get_fn or gh_get
    relevant_rules = filter_relevant_rules(rules_text, scope)
    diff_parts: list[str] = []
    missing_findings: list[dict[str, Any]] = []

    for file_item in files:
        fname = file_item.get("filename", "")
        if fname in paths:
            raw_patch = file_item.get("patch")
            if not raw_patch and file_item.get("status") not in ("removed", "deleted"):
                fallback = file_item.get("full_content") or fetch_file_content_fallback(
                    repo_name, fname, head_sha, gh_get_fn=getter
                )
                if fallback is not None:
                    file_item["full_content"] = file_item["content"] = fallback
                    raw_patch = f"[Content fallback fetched]\n{fallback}"
                else:
                    missing_findings.append({
                        "location": f"{fname}:1", "category": "COMPLIANCE",
                        "severity": "HIGH", "confidence": "HIGH",
                        "rule": "開發規範 §5 Review 完整性與可見度",
                        "problem": f"無法取得檔案 {fname} 的 Patch 或原始內容",
                        "evidence": "GitHub API 未提供 patch 且 Contents API 讀取失敗",
                        "risk": "變更內容未經審查即合併可能引入潛在安全缺陷",
                        "recommendation": "手動檢查該檔案變更內容，確認符合架構與安全規範",
                    })
                    raw_patch = (
                        f"[Patch unavailable; status={file_item.get('status', 'unknown')}]"
                    )
            diff_parts.append(f"diff -- {fname}\n{sanitize_diff(raw_patch or '[Empty diff]')}")

    diff = sanitize_diff("\n\n".join(diff_parts))
    prompt = build_batch_prompt(scope, index, paths, diff, contract_text, relevant_rules)
    batch_tokens = estimate_tokens(prompt)
    is_light_batch = batch_tokens <= GROQ_CAPACITY.max_batch_tokens
    norm_exp = normalize_paths(paths)
    common_args = (files, rules_text, contract_text, policy, pr_number)
    common_kw = {
        "repo_name": repo_name, "head_sha": head_sha, "chat_fn": chat_fn, "gh_get_fn": gh_get_fn,
    }

    def _split_and_retry(reason: str) -> dict[str, Any]:
        mid = len(paths) // 2
        left_p, right_p = paths[:mid], paths[mid:]
        print(
            f"批次 {scope}-{index} ({len(paths)} 檔, 預估 {batch_tokens} Tokens) "
            f"{reason}，自動拆分為子批次 ({len(left_p)} + {len(right_p)} 檔) 重試...",
            flush=True,
        )
        left_d = process_batch(scope, index, left_p, *common_args, delay=0.0, **common_kw)
        right_d = process_batch(scope, index, right_p, *common_args, delay=1.0, **common_kw)
        return {
            "batch": f"{scope}-{index}",
            "files_reviewed": left_d.get("files_reviewed", []) + right_d.get("files_reviewed", []),
            "coverage": "COMPLETE",
            "findings": left_d.get("findings", []) + right_d.get("findings", []),
            "passed_checks": left_d.get("passed_checks", []) + right_d.get("passed_checks", []),
        }

    max_attempts = 3
    fail_title, fail_summary, fail_detail = "", "", None

    for attempt in range(1, max_attempts + 1):
        try:
            try:
                text_output = caller(
                    prompt, required_tokens=batch_tokens, prefer_groq=is_light_batch
                ).strip()
            except TypeError:
                try:
                    text_output = caller(prompt, required_tokens=batch_tokens).strip()
                except TypeError:
                    text_output = caller(prompt).strip()
        except RuntimeError as run_exc:
            if len(paths) > 1:
                return _split_and_retry("呼叫受限")
            try:
                details = json.loads(str(run_exc))
            except (json.JSONDecodeError, ValueError):
                details = str(run_exc)
            publish_failure_report(
                pr_number, "AI Provider 呼叫失敗",
                "所有已配置的 AI 模型/金鑰均無法取得有效回應。", details,
                commit_id=head_sha, status_type="AI_PROVIDER_FAILED",
            )
            raise SystemExit(f"AI Provider 無法使用：{details}")

        try:
            batch_data = extract_json_payload(text_output)
        except json.JSONDecodeError as json_exc:
            fail_title = "AI 模型輸出非合法 JSON"
            fail_summary = f"批次 {scope}-{index} 模型輸出解析失敗：{json_exc}"
            fail_detail = text_output[:500]
            if attempt < max_attempts:
                print(f"批次 {scope}-{index} 第 {attempt} 次 JSON 解析失敗，重新發送...", flush=True)
                continue
            break

        normalize_batch_paths(batch_data, paths)
        norm_rev = normalize_paths(batch_data.get("files_reviewed"))
        is_cov_complete = str(batch_data.get("coverage", "")).strip().upper() == "COMPLETE"
        if not is_cov_complete or not validate_coverage(norm_exp, norm_rev):
            fail_title = "批次覆蓋範圍不符"
            fail_summary = (
                f"批次覆蓋範圍驗證失敗：{scope}-{index}\n預期：{paths}\n模型回傳：{norm_rev}"
            )
            fail_detail = None
            if attempt < max_attempts:
                print(
                    f"批次 {scope}-{index} 第 {attempt} 次覆蓋範圍不符 "
                    f"(回傳 {norm_rev})，重新發送請求...", flush=True,
                )
                continue
            break

        bad_finding = next(
            (f_item for f_item in batch_data.get("findings", [])
             if not validate_finding(f_item, allowed_files=paths)),
            None,
        )
        if bad_finding is not None:
            fail_title = "Finding 格式驗證失敗"
            fail_summary = f"批次 {scope}-{index} 中的 Finding 未通過格式或範圍驗證。"
            fail_detail = bad_finding
            if attempt < max_attempts:
                print(
                    f"批次 {scope}-{index} 第 {attempt} 次 Finding 驗證未通過，重新發送...",
                    flush=True,
                )
                continue
            break

        if missing_findings:
            batch_data.setdefault("findings", []).extend(missing_findings)
        return batch_data

    if len(paths) > 1:
        return _split_and_retry(f"{fail_title}已重試 {max_attempts} 次")

    publish_failure_report(
        pr_number, fail_title, fail_summary, fail_detail,
        commit_id=head_sha, status_type="REVIEW_EXECUTION_FAILED",
    )
    raise SystemExit(fail_summary)


def process_batches_parallel(
    batches: list[tuple[str, list[str]]], files: list[dict[str, Any]],
    rules_text: str, contract_text: str, policy: dict[str, Any], pr_number: int,
    repo_name: str = "", head_sha: str = "", max_workers: int = 3,
    stagger_seconds: float = 1.5, chat_fn: Any = None, gh_get_fn: Any = None,
) -> list[dict[str, Any]]:
    """多執行緒平行處理批次審查，並結合錯峰啟動機制平滑 API 速率限制。"""
    if not batches:
        return []

    actual_workers = max(1, min(len(batches), max_workers))
    if actual_workers == 1:
        return [
            process_batch(
                scope, idx, paths, files, rules_text, contract_text, policy, pr_number,
                repo_name=repo_name, head_sha=head_sha, delay=5.0 if idx > 1 else 0.0,
                chat_fn=chat_fn, gh_get_fn=gh_get_fn,
            )
            for idx, (scope, paths) in enumerate(batches, 1)
        ]

    print(
        f"啟動平行批次審查：共 {len(batches)} 個批次，"
        f"啟用 {actual_workers} 個平行 Worker，首波錯峰間隔 {stagger_seconds:.1f} 秒...",
        flush=True,
    )

    def _worker(
        worker_idx: int, worker_scope: str, worker_paths: list[str]
    ) -> tuple[int, dict[str, Any]]:
        stagger = (worker_idx - 1) * stagger_seconds if worker_idx <= actual_workers else 0.0
        if stagger > 0:
            time.sleep(stagger)
        batch_data = process_batch(
            worker_scope, worker_idx, worker_paths, files, rules_text, contract_text,
            policy, pr_number, repo_name=repo_name, head_sha=head_sha,
            delay=0.0, chat_fn=chat_fn, gh_get_fn=gh_get_fn,
        )
        return worker_idx, batch_data

    with concurrent.futures.ThreadPoolExecutor(max_workers=actual_workers) as executor:
        futures = [
            executor.submit(_worker, idx, scope, paths)
            for idx, (scope, paths) in enumerate(batches, 1)
        ]
        results_with_idx = [fut.result() for fut in futures]

    results_with_idx.sort(key=lambda item: item[0])
    return [data for _, data in results_with_idx]