import json
import logging
import os
import random
import re
import time
from pathlib import Path

import requests
from engine import evaluate, load_policy, validate_coverage, validate_finding

ROOT = Path(__file__).resolve().parents[2]
REPO = os.environ.get("REPO", "")
EVENT_PATH = os.environ.get("EVENT_PATH", "")
GH_TOKEN = os.environ.get("GH_TOKEN", "")
GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
REVIEW_MARKER = "<!-- ai-review-gate -->"
MAX_RETRY_LIMIT = 12

DEFAULT_GEMINI_MODELS = [
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "gemini-1.5-flash",
]
ACTIVE_GEMINI_MODELS = list(DEFAULT_GEMINI_MODELS)


def parse_retry_limit(raw_value: str | None, default: int = 9) -> int:
    if raw_value is None or not str(raw_value).strip():
        return default
    try:
        val = int(raw_value)
        if val < 1:
            return default
        return min(val, MAX_RETRY_LIMIT)
    except (TypeError, ValueError):
        logging.warning("AI_REVIEW_MAX_RETRIES 設定值 '%s' 無效，改用預設值 %s", raw_value, default)
        return default


DEFAULT_MAX_RETRIES_PER_MODEL = parse_retry_limit(os.environ.get("AI_REVIEW_MAX_RETRIES"))

DEFAULT_MODEL_CANDIDATES = [
    "llama-3.1-8b-instant",
    "llama-3.3-70b-versatile",
    "openai/gpt-oss-120b",
    "qwen/qwen3.6-27b",
    "openai/gpt-oss-20b",
]
ACTIVE_MODEL_CANDIDATES = list(DEFAULT_MODEL_CANDIDATES)


def get_repo():
    return os.environ.get("REPO") or REPO


def get_gh_token():
    return os.environ.get("GH_TOKEN") or GH_TOKEN


def get_groq_api_key():
    keys = get_groq_api_keys()
    return keys[0][1] if keys else ""


def mask_api_key(key: str) -> str:
    if not key or not isinstance(key, str):
        return ""
    stripped = key.strip()
    if len(stripped) <= 8:
        return "***"
    return f"{stripped[:4]}...{stripped[-4:]}"


def get_provider_api_keys(prefix: str) -> list[tuple[str, str]]:
    """
    探索環境變數中特定 Provider 的所有 API Keys。
    支援 {PREFIX} 與 {PREFIX}_1, {PREFIX}_2, ..., {PREFIX}_10 等數字後綴命名格式。
    回傳 [(var_name, key_value), ...] 依照自然排序排列，並過濾空值與重複金鑰。
    """
    raw_keys = {}
    pattern = re.compile(rf"^{prefix}(?:_\d+)?$", re.IGNORECASE)
    for var_name, var_val in os.environ.items():
        if pattern.match(var_name) and var_val and str(var_val).strip():
            raw_keys[var_name] = str(var_val).strip()

    def sort_key(item: tuple[str, str]):
        name = item[0].upper()
        if name == prefix.upper():
            return (0, 0, name)
        match_num = re.match(rf"^{prefix}_(\d+)$", name, re.IGNORECASE)
        if match_num:
            return (1, int(match_num.group(1)), name)
        return (2, 0, name)

    sorted_items = sorted(raw_keys.items(), key=sort_key)

    seen_values = set()
    unique_keys = []
    for var_name, var_val in sorted_items:
        if var_val not in seen_values:
            seen_values.add(var_val)
            unique_keys.append((var_name, var_val))

    return unique_keys


def get_groq_api_keys() -> list[tuple[str, str]]:
    keys = get_provider_api_keys("GROQ_API_KEY")
    if not keys and GROQ_API_KEY and GROQ_API_KEY.strip():
        keys.append(("GROQ_API_KEY", GROQ_API_KEY.strip()))
    return keys


def get_gemini_api_keys() -> list[tuple[str, str]]:
    return get_provider_api_keys("GEMINI_API_KEY")


KEY_COOLDOWN_MAP: dict[str, float] = {}


def reset_key_cooldowns() -> None:
    """清空所有金鑰的冷卻狀態（供測試與初始化使用）。"""
    KEY_COOLDOWN_MAP.clear()


def mark_key_cooldown(api_key: str, cooldown_seconds: float) -> None:
    """將特定金鑰標記進入冷卻清單，設定解除冷卻的時間戳記。"""
    if not api_key:
        return
    KEY_COOLDOWN_MAP[api_key] = time.time() + max(1.0, float(cooldown_seconds))


def is_key_in_cooldown(api_key: str) -> bool:
    """檢查金鑰是否仍處於冷卻期。若冷卻時間已過，自動解除並回傳 False。"""
    if not api_key or api_key not in KEY_COOLDOWN_MAP:
        return False
    if time.time() >= KEY_COOLDOWN_MAP[api_key]:
        KEY_COOLDOWN_MAP.pop(api_key, None)
        return False
    return True


def get_key_cooldown_remaining(api_key: str) -> float:
    """取得金鑰剩餘冷卻秒數，若未處於冷卻中則回傳 0.0。"""
    if not is_key_in_cooldown(api_key):
        return 0.0
    return max(0.0, KEY_COOLDOWN_MAP.get(api_key, 0.0) - time.time())


def get_active_keys(keys: list[tuple[str, str]]) -> list[tuple[str, str]]:
    """過濾出當前未處於冷卻清單中的可用金鑰清單。"""
    return [item for item in keys if not is_key_in_cooldown(item[1])]


def pick_random_active_key(
    keys: list[tuple[str, str]],
    excluded_keys: set[str] | None = None,
) -> tuple[str, str] | None:
    """
    自候選金鑰清單中，排除冷卻中與本輪已嘗試過之金鑰，隨機抽取一把。
    若所有金鑰均已排除或在冷卻中，回傳 None。
    """
    excluded = excluded_keys or set()
    active_pool = [
        item for item in keys
        if item[1] not in excluded and not is_key_in_cooldown(item[1])
    ]
    if not active_pool:
        return None
    return random.choice(active_pool)


def redact_secrets(text: str) -> str:
    """
    對文字中的敏感金鑰（Groq, Gemini, GitHub Token）以及特徵 Key 進行脫敏遮蔽。
    """
    if not text or not isinstance(text, str):
        return ""
    sanitized = text
    known_secrets = set()
    for _, key_val in get_groq_api_keys() + get_gemini_api_keys():
        if key_val and len(key_val) >= 6:
            known_secrets.add(key_val)
    gh_token = get_gh_token()
    if gh_token and len(gh_token) >= 6:
        known_secrets.add(gh_token)

    # 替換已知的特定 secret
    for secret in sorted(known_secrets, key=len, reverse=True):
        sanitized = sanitized.replace(secret, "[REDACTED]")

    # 替換符合通用 API Key 特徵的字串 (如 gsk_*, AIza*, ghp_*)
    sanitized = re.sub(r"\bgsk_[0-9A-Za-z]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bAIza[0-9A-Za-z\-_]{30,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bghp_[0-9A-Za-z]{20,}\b", "[REDACTED]", sanitized)
    sanitized = re.sub(r"\bgithub_pat_[0-9A-Za-z_]{20,}\b", "[REDACTED]", sanitized)

    return sanitized


def get_gemini_candidate_models() -> list[str]:
    custom_models = os.environ.get("GEMINI_MODELS", "").strip()
    if custom_models:
        return [model_item.strip() for model_item in custom_models.split(",") if model_item.strip()]
    return list(ACTIVE_GEMINI_MODELS)


def call_gemini_api(
    prompt: str,
    model_name: str,
    api_key: str,
    timeout: int = 120,
) -> requests.Response:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent"
    system_content = (
        "你必須使用繁體中文。只輸出單一合法 JSON 物件，嚴禁輸出 <think> 思維鏈標籤、"
        "任何 Markdown 標記或解釋性文字。確保所有字串與引號正確閉合。不得捏造 Finding。"
    )
    payload = {
        "systemInstruction": {
            "parts": [{"text": system_content}],
        },
        "contents": [
            {
                "role": "user",
                "parts": [{"text": prompt}],
            }
        ],
        "generationConfig": {
            "temperature": 0.1,
            "maxOutputTokens": 4096,
            "responseMimeType": "application/json",
        },
    }
    headers = {
        "Content-Type": "application/json",
        "x-goog-api-key": api_key,
    }
    return requests.post(url, headers=headers, json=payload, timeout=timeout)


def extract_gemini_text(response_json: dict) -> str:
    candidates = response_json.get("candidates", [])
    if not candidates:
        raise ValueError(f"Gemini 回應未包含 candidates: {response_json}")
    candidate = candidates[0]
    content = candidate.get("content", {})
    parts = content.get("parts", [])
    if not parts or "text" not in parts[0]:
        raise ValueError(f"Gemini 回應 candidate 格式不正確: {candidate}")
    return parts[0]["text"]


def get_event_path():
    return os.environ.get("EVENT_PATH") or EVENT_PATH


def normalize_path(path_str: str) -> str:
    if not isinstance(path_str, str):
        return ""
    normalized = path_str.strip().replace("\\", "/")
    return normalized.removeprefix("./")


def normalize_paths(path_list) -> list:
    if not isinstance(path_list, list):
        return []
    return [normalize_path(p) for p in path_list if normalize_path(p)]


def get_github_headers():
    return {
        "Authorization": f"Bearer {get_gh_token()}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def gh_get(url, params=None):
    response = requests.get(
        url,
        headers=get_github_headers(),
        params=params,
        timeout=30,
    )
    response.raise_for_status()
    return response.json()


def resolve_pr_number(event):
    pull_request = event.get("pull_request") or {}
    if pull_request.get("number"):
        return int(pull_request["number"])

    workflow_run = event.get("workflow_run") or {}
    pull_requests = workflow_run.get("pull_requests") or event.get("pull_requests") or []
    if pull_requests and pull_requests[0].get("number"):
        return int(pull_requests[0]["number"])

    inputs = event.get("inputs") or {}
    if inputs.get("pr_number"):
        try:
            return int(inputs["pr_number"])
        except (ValueError, TypeError) as exc:
            logging.warning("無法將 inputs.pr_number '%s' 解析為整數: %s", inputs.get("pr_number"), exc)

    head_sha = workflow_run.get("head_sha") or event.get("after")
    repo_name = get_repo()
    token_value = get_gh_token()
    if head_sha and repo_name and token_value:
        try:
            commits_url = f"https://api.github.com/repos/{repo_name}/commits/{head_sha}/pulls"
            associated_pulls = gh_get(commits_url)
            if associated_pulls and associated_pulls[0].get("number"):
                return int(associated_pulls[0]["number"])
        except requests.RequestException as exc:
            print(f"無法透過 Commit SHA 查詢關聯 PR：{exc}")

    raise SystemExit("無法從 GitHub 事件或環境中解析對應的 Pull Request 編號。")


def post_issue_comment(pr_number, body):
    repo_name = get_repo()
    marked_body = body.rstrip() + "\n\n" + REVIEW_MARKER
    comments_url = f"https://api.github.com/repos/{repo_name}/issues/{pr_number}/comments"
    try:
        existing_comments = gh_get(comments_url, params={"per_page": 100})
        existing = next(
            (
                comment
                for comment in existing_comments
                if REVIEW_MARKER in (comment.get("body") or "")
            ),
            None,
        )
        if existing:
            comment_id = existing["id"]
            patch_url = f"https://api.github.com/repos/{repo_name}/issues/comments/{comment_id}"
            response = requests.patch(
                patch_url,
                headers=get_github_headers(),
                json={"body": marked_body},
                timeout=30,
            )
            response.raise_for_status()
            print(f"成功更新現有 PR Issue 留言（ID: {comment_id}）")
            return response.json()
        else:
            response = requests.post(
                comments_url,
                headers=get_github_headers(),
                json={"body": marked_body},
                timeout=30,
            )
            response.raise_for_status()
            print(f"成功在 PR #{pr_number} 建立新 Issue 留言")
            return response.json()
    except requests.RequestException as exc:
        print(f"發布/更新 PR Issue 留言時發生錯誤：{exc}")
        return None


def post_pr_review(pr_number, body, event_type="COMMENT"):
    repo_name = get_repo()
    review_url = f"https://api.github.com/repos/{repo_name}/pulls/{pr_number}/reviews"
    payload = {"body": body, "event": event_type}
    try:
        response = requests.post(
            review_url,
            headers=get_github_headers(),
            json=payload,
            timeout=30,
        )
        if response.status_code == 422:
            print("PR Review API 回傳 422（例如無法審查自身 PR），已由 Issue 留言保障可見性。")
            return None
        response.raise_for_status()
        print(f"成功提交 PR Review（狀態：{event_type}）")
        return response.json()
    except requests.RequestException as exc:
        print(f"提交 PR Review 時發生錯誤：{exc}")
        return None


def publish_review(pr_number, body, decision="COMMENT"):
    post_issue_comment(pr_number, body)
    if decision == "APPROVE":
        review_event = "APPROVE"
    elif decision == "REQUEST_CHANGES":
        review_event = "REQUEST_CHANGES"
    else:
        review_event = "COMMENT"
    post_pr_review(pr_number, body, review_event)


def publish_failure_report(pr_number, title, reason, details=None):
    clean_title = redact_secrets(str(title))
    clean_reason = redact_secrets(str(reason))
    report = [
        "# AI Architecture & Security Review",
        "",
        "## 審查結果",
        "REQUEST_CHANGES",
        "",
        f"## 🔴 {clean_title}",
        f"**原因**：{clean_reason}",
        "",
    ]
    if details:
        report.append("**詳細資訊**：")
        if isinstance(details, list):
            for detail_item in details:
                if isinstance(detail_item, (tuple, list)) and len(detail_item) == 2:
                    k = redact_secrets(str(detail_item[0]))
                    v = redact_secrets(str(detail_item[1]))
                    report.append(f"- `{k}`：{v}")
                else:
                    report.append(f"- {redact_secrets(str(detail_item))}")
        else:
            report.append(f"```\n{redact_secrets(str(details))}\n```")
        report.append("")
    report.extend([
        "這是 fail-closed 行為：AI Review 遭遇錯誤或未完成時不得產生 APPROVE。",
        "請檢查 CI 日誌或修復相關設定後重新觸發。",
        "",
        "## 執行原則",
        "- 所有自然語言內容使用繁體中文。",
        "- AI Provider 或執行失敗不會被當成 Review PASS。",
        "- 保持 PR 流程透明，隨時留存診斷 Message。",
    ])
    body = "\n".join(report)
    if pr_number:
        publish_review(pr_number, body, "REQUEST_CHANGES")
    return body


def get_available_models():
    groq_keys = get_groq_api_keys()
    if not groq_keys:
        return set()
    api_key = groq_keys[0][1]
    response = requests.get(
        "https://api.groq.com/openai/v1/models",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        timeout=30,
    )
    response.raise_for_status()
    model_entries = response.json().get("data", [])
    return {model_item.get("id") for model_item in model_entries}


def find_balanced_json_substrings(text: str) -> list:
    """掃描字串中所有括號平衡的最外層 { ... } 區塊，忽略字串引號內的括號。"""
    candidates = []
    i = 0
    n = len(text)
    while i < n:
        if text[i] == "{":
            start = i
            depth = 0
            in_string = False
            escape = False
            j = i
            while j < n:
                char = text[j]
                if escape:
                    escape = False
                elif char == "\\":
                    if in_string:
                        escape = True
                elif char == '"':
                    in_string = not in_string
                elif not in_string:
                    if char == "{":
                        depth += 1
                    elif char == "}":
                        depth -= 1
                        if depth == 0:
                            candidates.append(text[start : j + 1])
                            i = j
                            break
                j += 1
        i += 1
    return candidates


def repair_json_string(text: str) -> str:
    """嘗試修復常見的 LLM 格式損毀或非標準 JSON 字串。"""
    if not isinstance(text, str):
        return ""

    cleaned = text.strip()

    # 1. 移除 <think>...</think> 或未閉合的 <think> 思維鏈標籤
    if "<think>" in cleaned.lower():
        cleaned = re.sub(r"(?i)<think>[\s\S]*?</think>", "", cleaned).strip()
        if "<think>" in cleaned.lower():
            parts = re.split(r"(?i)</think>", cleaned)
            if len(parts) > 1:
                cleaned = parts[-1].strip()
            else:
                cleaned = re.sub(r"(?i)^<think>[\s\S]*?(?=\{)", "", cleaned).strip()

    # 2. 若包含 Markdown 代碼塊（```json ... ``` 或 ``` ... ```），優先測試代碼塊內容
    code_blocks = re.findall(
        r"```(?:json)?\s*([\s\S]*?)\s*```", cleaned, re.IGNORECASE
    )
    for block in code_blocks:
        block_cleaned = block.strip()
        try:
            parsed = json.loads(block_cleaned)
            if isinstance(parsed, dict):
                return block_cleaned
        except (json.JSONDecodeError, ValueError, TypeError) as exc:
            logging.debug("Markdown 代碼塊 JSON 解析略過: %s", exc)

    # 3. 使用括號平衡計數精確擷取頂層平衡的 JSON 物件
    candidates = find_balanced_json_substrings(cleaned)
    valid_review_payloads = []
    valid_other_dicts = []
    for cand in candidates:
        try:
            parsed = json.loads(cand)
            if isinstance(parsed, dict):
                if "batch" in parsed and "files_reviewed" in parsed and "findings" in parsed:
                    valid_review_payloads.append(cand)
                else:
                    valid_other_dicts.append(cand)
        except (json.JSONDecodeError, ValueError, TypeError) as exc:
            logging.debug("候選 JSON 子字串解析略過: %s", exc)
            continue

    if len(valid_review_payloads) == 1:
        return valid_review_payloads[0]
    elif len(valid_review_payloads) > 1:
        raise json.JSONDecodeError("輸出包含多個相衝的 Review JSON 物件，無法確定唯一根結構", cleaned, 0)

    if len(valid_other_dicts) == 1:
        return valid_other_dicts[0]
    elif len(valid_other_dicts) > 1:
        raise json.JSONDecodeError("輸出包含多個歧異 JSON 物件", cleaned, 0)

    if candidates:
        raise json.JSONDecodeError("輸出包含無法識別為合法物件的 JSON 片段", cleaned, 0)

    return cleaned


def extract_json_payload(raw_text: str) -> dict:
    if not raw_text or not isinstance(raw_text, str):
        raise json.JSONDecodeError("輸出為空或型別錯誤", "", 0)

    repaired = repair_json_string(raw_text)
    parsed = json.loads(repaired)
    if isinstance(parsed, dict):
        return parsed
    raise json.JSONDecodeError("JSON 頂層結構必須為物件（dict）", repaired, 0)


def parse_retry_after(response) -> float:
    header_val = None
    if hasattr(response, "headers") and response.headers:
        header_val = response.headers.get("retry-after")
    if header_val:
        try:
            return float(header_val)
        except (ValueError, TypeError) as exc:
            logging.warning("無法解析 retry-after header '%s': %s", header_val, exc)
    try:
        err_text = response.text if hasattr(response, "text") and response.text else ""
        match_ms = re.search(r"try again in (\d+(?:\.\d+)?)\s*ms", err_text, re.IGNORECASE)
        if match_ms:
            return float(match_ms.group(1)) / 1000.0
        match_s = re.search(r"try again in (\d+(?:\.\d+)?)\s*s\b", err_text, re.IGNORECASE)
        if match_s:
            return float(match_s.group(1))
        match_m = re.search(r"try again in (\d+(?:\.\d+)?)\s*m\b", err_text, re.IGNORECASE)
        if match_m:
            return float(match_m.group(1)) * 60.0
    except (ValueError, TypeError, AttributeError) as exc:
        logging.warning("無法從錯誤訊息文字解析重試延遲時間: %s", exc)
    return 5.0


def calculate_backoff_delay(
    attempt: int,
    retry_after: float = 0.0,
    base_delay: float = 2.5,
    max_delay: float = 90.0,
    jitter_range: tuple = (0.5, 1.5),
) -> float:
    exponential_delay = base_delay * (2 ** max(0, attempt - 1))
    effective_delay = max(retry_after, exponential_delay)
    jitter = random.uniform(jitter_range[0], jitter_range[1])
    return min(effective_delay + jitter, max_delay)


def get_candidate_models() -> list:
    candidates = list(ACTIVE_MODEL_CANDIDATES)
    if not get_groq_api_key():
        return candidates
    try:
        available_models = get_available_models()
        filtered_candidates = [model for model in candidates if model in available_models]
        if filtered_candidates:
            return filtered_candidates
    except requests.RequestException as exc:
        print(f"無法列舉 Groq 可用模型清單：{exc}，直接依序嘗試備援候選模型。")
    return candidates


def chat_completion(
    prompt: str,
    max_retries_per_model: int = DEFAULT_MAX_RETRIES_PER_MODEL,
) -> str:
    groq_keys = get_groq_api_keys()
    gemini_keys = get_gemini_api_keys()

    if not groq_keys and not gemini_keys:
        raise RuntimeError(
            json.dumps(
                [("INIT", "未配置任何 AI Provider 密鑰（GROQ_API_KEY_* 或 GEMINI_API_KEY_*）")],
                ensure_ascii=False,
            )
        )

    error_details = []

    # ========================================================
    # Stage 1: Groq (第一優先 Provider，隨機選 Key、冷卻清單與多模型備援)
    # ========================================================
    if groq_keys:
        groq_models = get_candidate_models()

        for model_name in groq_models:
            tried_keys = set()
            model_unsupported = False
            while True:
                picked = pick_random_active_key(groq_keys, excluded_keys=tried_keys)
                if picked is None:
                    break
                var_name, api_key = picked
                tried_keys.add(api_key)
                masked_key = mask_api_key(api_key)
                key_tag = f"Groq/{model_name} [{var_name}:{masked_key}]"
                use_json_mode = True

                for attempt in range(1, max_retries_per_model + 1):
                    try:
                        system_content = (
                            "你必須使用繁體中文。只輸出單一合法 JSON 物件，嚴禁輸出 <think> 思維鏈標籤、"
                            "任何 Markdown 標記或解釋性文字。確保所有字串與引號正確閉合。不得捏造 Finding。"
                        )
                        request_payload = {
                            "model": model_name,
                            "messages": [
                                {"role": "system", "content": system_content},
                                {"role": "user", "content": prompt},
                            ],
                            "temperature": 0.1,
                            "max_tokens": 4096,
                        }
                        if use_json_mode:
                            request_payload["response_format"] = {"type": "json_object"}

                        response = requests.post(
                            "https://api.groq.com/openai/v1/chat/completions",
                            headers={
                                "Authorization": f"Bearer {api_key}",
                                "Content-Type": "application/json",
                            },
                            json=request_payload,
                            timeout=120,
                        )
                        if response.ok:
                            raw_content = response.json()["choices"][0]["message"]["content"]
                            try:
                                extract_json_payload(raw_content)
                                print(f"使用 Groq 模型：{model_name}（金鑰：{var_name} {masked_key}）")
                                if model_name in ACTIVE_MODEL_CANDIDATES:
                                    ACTIVE_MODEL_CANDIDATES.remove(model_name)
                                    ACTIVE_MODEL_CANDIDATES.insert(0, model_name)
                                return raw_content
                            except json.JSONDecodeError as json_exc:
                                json_err_msg = f"第 {attempt} 次輸出非合法 JSON：{json_exc}"
                                error_details.append((key_tag, redact_secrets(json_err_msg)))
                                print(
                                    f"Groq 模型 {model_name} 金鑰 {var_name} 輸出非合法 JSON"
                                    f"（第 {attempt}/{max_retries_per_model} 次）：{json_exc}，退避等待後重試..."
                                )
                                if attempt < max_retries_per_model:
                                    wait_seconds = calculate_backoff_delay(
                                        attempt, 0.0, base_delay=2.0, max_delay=30.0
                                    )
                                    time.sleep(wait_seconds)
                                    continue
                                if model_name in ACTIVE_MODEL_CANDIDATES:
                                    ACTIVE_MODEL_CANDIDATES.remove(model_name)
                                    ACTIVE_MODEL_CANDIDATES.append(model_name)
                                time.sleep(2.0)
                                break

                        status_code = response.status_code
                        resp_text = response.text[:300]
                        reason_msg = f"HTTP {status_code}: {resp_text}"
                        error_details.append((key_tag, redact_secrets(reason_msg)))

                        # 429 速率限制 / 配額耗盡
                        if status_code == 429:
                            raw_retry_after = parse_retry_after(response)
                            # 若配置了多組金鑰，立即將該 Key 放入冷卻清單並隨機輪替下一把可用金鑰
                            if len(groq_keys) > 1:
                                if (
                                    raw_retry_after > 60.0
                                    or "TPD" in resp_text
                                    or "daily limit" in resp_text.lower()
                                    or "quota" in resp_text.lower()
                                ):
                                    cooldown_seconds = max(raw_retry_after, 300.0)
                                else:
                                    cooldown_seconds = max(raw_retry_after, 30.0)
                                mark_key_cooldown(api_key, cooldown_seconds)
                                print(
                                    f"Groq 金鑰 {var_name} ({masked_key}) 達到速率限制或配額耗盡 (429)，"
                                    f"已放入冷卻清單（{cooldown_seconds:.1f} 秒），隨機切換下一把可用金鑰..."
                                )
                                time.sleep(1.0)
                                break

                            # 單一金鑰情境：若為暫時性短速率限制且未達重試上限，進行指數退避重試
                            wait_seconds = calculate_backoff_delay(
                                attempt, raw_retry_after, base_delay=2.5, max_delay=60.0
                            )
                            if (
                                attempt < max_retries_per_model
                                and raw_retry_after <= 60.0
                                and "TPD" not in resp_text
                                and "daily limit" not in resp_text.lower()
                                and "quota" not in resp_text.lower()
                            ):
                                print(
                                    f"模型 {model_name} 達到速率限制 (429)，"
                                    f"指數退避等待 {wait_seconds:.1f} 秒後重試"
                                    f"（第 {attempt}/{max_retries_per_model} 次）..."
                                )
                                time.sleep(wait_seconds)
                                continue

                            # 若單一金鑰已達重試上限或屬於每日配額耗盡，降級當前模型
                            print(f"模型 {model_name} 達到重試上限或額度已滿，降級至備援清單尾端並切換下一個模型。")
                            if model_name in ACTIVE_MODEL_CANDIDATES:
                                ACTIVE_MODEL_CANDIDATES.remove(model_name)
                                ACTIVE_MODEL_CANDIDATES.append(model_name)
                            time.sleep(2.0)
                            model_unsupported = True
                            break

                        # 403 授權失敗：標記冷卻並輪替至下一組金鑰
                        if status_code == 403:
                            mark_key_cooldown(api_key, 3600.0)
                            print(
                                f"Groq 金鑰 {var_name} ({masked_key}) 授權失敗 (403)，"
                                "已放入冷卻清單，隨機切換下一把可用金鑰..."
                            )
                            time.sleep(1.0)
                            break

                        # 413 負載過大：切換下一模型
                        if status_code == 413:
                            print(f"Groq 模型 {model_name} 請求負載過大 (413)：{reason_msg}，立即降級並切換下一個模型。")
                            if model_name in ACTIVE_MODEL_CANDIDATES:
                                ACTIVE_MODEL_CANDIDATES.remove(model_name)
                                ACTIVE_MODEL_CANDIDATES.append(model_name)
                            time.sleep(1.0)
                            model_unsupported = True
                            break

                        # 400 JSON 模式校驗失敗
                        if status_code == 400 and use_json_mode and "json_validate_failed" in resp_text:
                            wait_seconds = calculate_backoff_delay(
                                attempt, 0.0, base_delay=3.0, max_delay=30.0
                            )
                            print(
                                f"Groq 模型 {model_name} JSON 模式校驗失敗 (400)，"
                                f"延後等待 {wait_seconds:.1f} 秒後降級為純文字模式並重試..."
                            )
                            use_json_mode = False
                            time.sleep(wait_seconds)
                            continue

                        if status_code == 400:
                            print(
                                f"Groq 模型 {model_name} 請求參數或格式錯誤 (400)：{reason_msg}，"
                                "降級至備援清單尾端並切換下一個模型。"
                            )
                            if model_name in ACTIVE_MODEL_CANDIDATES:
                                ACTIVE_MODEL_CANDIDATES.remove(model_name)
                                ACTIVE_MODEL_CANDIDATES.append(model_name)
                            time.sleep(2.0)
                            model_unsupported = True
                            break

                        print(f"Groq 模型 {model_name} 金鑰 {var_name} 請求失敗：{reason_msg}")
                        if status_code >= 500 and attempt < max_retries_per_model:
                            wait_seconds = calculate_backoff_delay(
                                attempt, 2.0, base_delay=2.5, max_delay=30.0
                            )
                            time.sleep(wait_seconds)
                            continue
                        time.sleep(2.0)
                        break
                    except requests.RequestException as exc:
                        error_details.append((key_tag, redact_secrets(str(exc))))
                        print(f"Groq 金鑰 {var_name} 連線異常：{exc}")
                        if attempt < max_retries_per_model:
                            wait_seconds = calculate_backoff_delay(
                                attempt, 2.0, base_delay=2.5, max_delay=60.0
                            )
                            time.sleep(wait_seconds)
                            continue
                        time.sleep(2.0)
                        break

                if model_unsupported:
                    break

    # ========================================================
    # Stage 2: Google Gemini Fallback (若未配置 Groq 或 Groq 全數失敗/冷卻)
    # ========================================================
    if gemini_keys:
        if groq_keys:
            print("所有 Groq 金鑰與模型均無法取得有效回應或已冷卻，降級至備援 Provider：Google Gemini...")

        gemini_models = get_gemini_candidate_models()

        for model_name in gemini_models:
            tried_keys = set()
            model_unsupported = False
            while True:
                picked = pick_random_active_key(gemini_keys, excluded_keys=tried_keys)
                if picked is None:
                    break
                var_name, api_key = picked
                tried_keys.add(api_key)
                masked_key = mask_api_key(api_key)
                key_tag = f"Gemini/{model_name} [{var_name}:{masked_key}]"

                for attempt in range(1, max_retries_per_model + 1):
                    try:
                        response = call_gemini_api(prompt, model_name, api_key, timeout=120)
                        if response.ok:
                            try:
                                resp_json = response.json()
                                raw_content = extract_gemini_text(resp_json)
                                extract_json_payload(raw_content)
                                print(f"使用 Google Gemini 模型：{model_name}（金鑰：{var_name} {masked_key}）")
                                if model_name in ACTIVE_GEMINI_MODELS:
                                    ACTIVE_GEMINI_MODELS.remove(model_name)
                                    ACTIVE_GEMINI_MODELS.insert(0, model_name)
                                return raw_content
                            except (ValueError, KeyError, json.JSONDecodeError) as parse_exc:
                                json_err_msg = f"第 {attempt} 次輸出非合法 JSON：{parse_exc}"
                                error_details.append((key_tag, redact_secrets(json_err_msg)))
                                print(
                                    f"Gemini 模型 {model_name} 金鑰 {var_name} 輸出非合法 JSON"
                                    f"（第 {attempt}/{max_retries_per_model} 次）：{parse_exc}，退避等待後重試..."
                                )
                                if attempt < max_retries_per_model:
                                    wait_sec = calculate_backoff_delay(
                                        attempt, 0.0, base_delay=2.0, max_delay=30.0
                                    )
                                    time.sleep(wait_sec)
                                    continue
                                break

                        status_code = response.status_code
                        resp_text = response.text[:300]
                        reason_msg = f"HTTP {status_code}: {resp_text}"
                        error_details.append((key_tag, redact_secrets(reason_msg)))

                        # 429 速率限制 / 配額耗盡 / RESOURCE_EXHAUSTED
                        if status_code == 429 or "RESOURCE_EXHAUSTED" in resp_text:
                            raw_retry_after = parse_retry_after(response)
                            cooldown_seconds = max(raw_retry_after, 60.0)
                            if "quota" in resp_text.lower() or "RESOURCE_EXHAUSTED" in resp_text:
                                cooldown_seconds = max(cooldown_seconds, 300.0)

                            if len(gemini_keys) > 1:
                                mark_key_cooldown(api_key, cooldown_seconds)
                                print(
                                    f"Gemini 金鑰 {var_name} ({masked_key}) 達到速率限制或配額耗盡 (429)，"
                                    f"已放入冷卻清單（{cooldown_seconds:.1f} 秒），隨機切換下一把可用金鑰..."
                                )
                                time.sleep(1.0)
                                break

                            # 單一金鑰情境
                            wait_sec = calculate_backoff_delay(
                                attempt, raw_retry_after, base_delay=2.5, max_delay=60.0
                            )
                            if (
                                attempt < max_retries_per_model
                                and raw_retry_after <= 60.0
                                and "quota" not in resp_text.lower()
                                and "RESOURCE_EXHAUSTED" not in resp_text
                            ):
                                print(
                                    f"Gemini 模型 {model_name} 達到速率限制 (429)，"
                                    f"指數退避等待 {wait_sec:.1f} 秒後重試"
                                    f"（第 {attempt}/{max_retries_per_model} 次）..."
                                )
                                time.sleep(wait_sec)
                                continue

                            print(f"Gemini 模型 {model_name} 達到重試上限或額度耗盡，切換下一個模型。")
                            if model_name in ACTIVE_GEMINI_MODELS:
                                ACTIVE_GEMINI_MODELS.remove(model_name)
                                ACTIVE_GEMINI_MODELS.append(model_name)
                            time.sleep(2.0)
                            model_unsupported = True
                            break

                        # 403 授權無效或配額限制
                        if status_code == 403:
                            mark_key_cooldown(api_key, 3600.0)
                            print(
                                f"Gemini 金鑰 {var_name} ({masked_key}) 授權失敗或額度無效 (403)，"
                                "已放入冷卻清單，隨機切換下一把可用金鑰..."
                            )
                            time.sleep(1.0)
                            break

                        # 413 請求過大：切換下一個模型
                        if status_code == 413:
                            print(f"Gemini 模型 {model_name} 請求負載過大 (413)，切換下一候選模型...")
                            if model_name in ACTIVE_GEMINI_MODELS:
                                ACTIVE_GEMINI_MODELS.remove(model_name)
                                ACTIVE_GEMINI_MODELS.append(model_name)
                            time.sleep(1.0)
                            model_unsupported = True
                            break

                        # 其他錯誤 (400, 500, 503 等)
                        print(f"Gemini 金鑰 {var_name} 呼叫失敗：{reason_msg}")
                        if status_code >= 500 and attempt < max_retries_per_model:
                            wait_sec = calculate_backoff_delay(
                                attempt, 2.0, base_delay=2.5, max_delay=30.0
                            )
                            time.sleep(wait_sec)
                            continue
                        break

                    except requests.RequestException as req_exc:
                        error_details.append((key_tag, redact_secrets(str(req_exc))))
                        print(f"Gemini 金鑰 {var_name} 連線異常：{req_exc}")
                        if attempt < max_retries_per_model:
                            wait_sec = calculate_backoff_delay(
                                attempt, 2.0, base_delay=2.5, max_delay=30.0
                            )
                            time.sleep(wait_sec)
                            continue
                        break

                if model_unsupported:
                    break

    raise RuntimeError(json.dumps(error_details, ensure_ascii=False))


def main():
    has_token = bool(get_gh_token())
    has_groq = bool(get_groq_api_keys())
    has_gemini = bool(get_gemini_api_keys())
    if not has_token or (not has_groq and not has_gemini):
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
    except requests.RequestException as exc:
        publish_failure_report(pr_number, "無法取得 PR 資訊", str(exc))
        raise SystemExit(f"無法取得 PR 資訊：{exc}")

    if pull_request_data.get("state") != "open":
        print(f"PR #{pr_number} 目前非開啟狀態（state: {pull_request_data.get('state')}），略過審查發布。")
        return

    files = []
    page = 1
    try:
        while True:
            file_chunk = gh_get(
                f"https://api.github.com/repos/{REPO}/pulls/{pr_number}/files",
                params={"per_page": 100, "page": page},
            )
            files.extend(file_chunk)
            if len(file_chunk) < 100:
                break
            page += 1
    except requests.RequestException as exc:
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

    groups = {
        "ci": [],
        "security-api": [],
        "business": [],
        "data": [],
        "integration": [],
        "python": [],
        "other": [],
    }
    for filename in changed_files:
        path_lower = filename.lower()
        if path_lower.startswith(".github/"):
            groups["ci"].append(filename)
        elif path_lower.endswith(".py"):
            groups["python"].append(filename)
        elif any(
            token in path_lower
            for token in ("controller", "security", "permission", "auth", "openapi")
        ):
            groups["security-api"].append(filename)
        elif any(token in path_lower for token in ("feign", "client", "integration", "external")):
            groups["integration"].append(filename)
        elif any(
            token in path_lower
            for token in ("repository", "entity", "dao", "migration", "mapper")
        ):
            groups["data"].append(filename)
        elif any(token in path_lower for token in ("service", "domain", "usecase")):
            groups["business"].append(filename)
        else:
            groups["other"].append(filename)

    max_chars = 6000
    batches = []
    for scope, paths in groups.items():
        if not paths:
            continue
        current_batch = []
        current_size = 0
        for filename in paths:
            file_item = next(item for item in files if item["filename"] == filename)
            patch_content = file_item.get("patch") or ""
            cost = len(filename) + max(len(patch_content), 1000)
            if current_batch and current_size + cost > max_chars:
                batches.append((scope, current_batch))
                current_batch = []
                current_size = 0
            current_batch.append(filename)
            current_size += cost
        if current_batch:
            batches.append((scope, current_batch))

    expected_files = [filename for _, paths in batches for filename in paths]
    if sorted(expected_files) != sorted(changed_files) or (
        len(expected_files) != len(set(expected_files))
    ):
        mismatch_msg = "覆蓋範圍規劃不符：每個變更檔案必須屬於且僅屬於一個批次。"
        publish_failure_report(pr_number, "批次檔案規劃異常", mismatch_msg)
        raise SystemExit(mismatch_msg)

    keywords = {
        "ci": (
            "GitHub Actions", "workflow", "permissions", "Secrets",
            "GITHUB_TOKEN", "pull_request", "shell", "artifact", "cache",
            "supply-chain",
        ),
        "security-api": ("BOLA", "權限", "OpenAPI", "Controller", "IAM", "Security", "API"),
        "business": ("SOLID", "DRY", "KISS", "YAGNI", "Service", "架構"),
        "data": ("Repository", "Entity", "DataAccess", "資料庫", "EntityManager", "Entity"),
        "integration": ("Feign", "跨服務", "外部", "Microservice", "Client"),
        "python": ("Python", "Ruff", "Exception", "Type Hint", "snake_case"),
        "other": ("CI", "規範", "品質"),
    }

    results = []
    for index, (scope, paths) in enumerate(batches, 1):
        if index > 1:
            print("批次間隔節流：等待 5 秒以平滑 API 速率限制...")
            time.sleep(5.0)

        relevant_rules_list = []
        rule_lines = rules_text.splitlines()
        for line_index, line_content in enumerate(rule_lines):
            if any(keyword.lower() in line_content.lower() for keyword in keywords[scope]):
                start_pos = max(0, line_index - 2)
                end_pos = min(len(rule_lines), line_index + 14)
                relevant_rules_list.extend(rule_lines[start_pos:end_pos])
        relevant_rules = "\n".join(dict.fromkeys(relevant_rules_list))[:2000] or rules_text[:1500]

        diff_parts = []
        for file_item in files:
            if file_item["filename"] in paths:
                patch_text = (
                    file_item.get("patch")
                    or "[GitHub did not provide a patch; review metadata only]"
                )
                diff_parts.append(f"diff -- {file_item['filename']}\n{patch_text}")
        diff = "\n\n".join(diff_parts)[:6000]

        json_template = (
            f'{{"batch":"{scope}-{index}",'
            f'"files_reviewed":{json.dumps(paths, ensure_ascii=False)},'
            '"findings":[{"severity":"CRITICAL|HIGH|MEDIUM|LOW",'
            '"confidence":"HIGH|MEDIUM|LOW","location":"file:line",'
            '"rule":"繁體中文規範依據","problem":"繁體中文問題",'
            '"evidence":"繁體中文證據","risk":"繁體中文風險",'
            '"recommendation":"繁體中文修正建議"}],'
            '"passed_checks":["繁體中文"],"coverage":"COMPLETE"}'
        )

        prompt = f'''你是此 repository 的 Senior Code Reviewer，負責「{scope}」批次。
所有自然語言輸出必須使用繁體中文（zh-TW），禁止簡體中文。

開發規範.md 是唯一專案規則來源。AI_REVIEW.md 只定義 Review 執行與 Gate 原則。

【長度與格式約束】
各欄位描述務必簡潔扼要，單一 Finding 不得贅述；若無違規，findings 輸出空陣列 []。確保回應在 1000 Tokens 內結束。

【Review Contract】
{contract_text[:1500]}

【相關規範】
{relevant_rules}

【本批次檔案】（files_reviewed 欄位必須完整包含下列所有路徑字串，不可修改或遺漏）
{chr(10).join(paths)}

【PR Diff】
```diff
{diff}
```

只審查本批次。必須有程式碼或 workflow 證據才能提出 Finding。
不得提出與本 PR 無關的既有技術債或純風格建議。
CI 批次特別檢查最小權限、Secret trust boundary、untrusted input、Action pinning、
artifact/cache、fail-open 與 Review bypass。

只輸出合法 JSON，不得輸出 markdown：
{json_template}

不得輸出 blocking 或 decision；最終 Gate 完全由 deterministic policy 決定。'''

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

        norm_expected_paths = normalize_paths(paths)
        raw_reviewed_paths = batch_data.get("files_reviewed")
        norm_reviewed_paths = normalize_paths(raw_reviewed_paths)
        coverage_status = str(batch_data.get("coverage", "")).strip().upper()

        if coverage_status != "COMPLETE" or not validate_coverage(
            norm_expected_paths, norm_reviewed_paths
        ):
            cov_err = (
                f"批次覆蓋範圍驗證失敗：{scope}-{index}\n"
                f"- 預期檔案：{paths}\n"
                f"- 模型回傳檔案：{raw_reviewed_paths}\n"
                f"- 覆蓋標記：{batch_data.get('coverage')}"
            )
            publish_failure_report(pr_number, "批次覆蓋範圍不符", cov_err)
            raise SystemExit(cov_err)

        for finding in batch_data.get("findings", []):
            if not validate_finding(finding, policy):
                schema_err = f"批次 {scope}-{index} 中的 Finding 未通過格式驗證。"
                publish_failure_report(pr_number, "Finding 格式驗證失敗", schema_err, finding)
                raise SystemExit(schema_err)

        results.append(batch_data)

    reviewed_files = [
        filename
        for data in results
        for filename in normalize_paths(data.get("files_reviewed", []))
    ]
    findings = [finding for data in results for finding in data.get("findings", [])]
    passed_checks = [check for data in results for check in data.get("passed_checks", [])]

    expected_all_files = normalize_paths(expected_files)
    evaluation_result = evaluate(findings, expected_all_files, reviewed_files, policy)
    decision = evaluation_result["decision"]
    unique_findings = evaluation_result["findings"]
    blocking_findings = evaluation_result["blocking_findings"]

    report = [
        "# AI Code Review",
        "",
        f"## 審查結果\n{decision}",
        "",
        f"已審查 {len(changed_files)} 個變更檔案、{len(results)} 個批次；"
        f"共 {len(unique_findings)} 個 Finding，其中 {len(blocking_findings)} 個阻擋項目。",
        "",
    ]
    if unique_findings:
        report.append("## Findings")
        for finding in unique_findings:
            report.extend([
                "",
                f"### [{finding['severity']}] {finding['problem']}",
                f"**位置**：`{finding['location']}`",
                f"**規範依據**：{finding['rule']}",
                f"**證據**：{finding['evidence']}",
                f"**風險**：{finding['risk']}",
                f"**修正建議**：{finding['recommendation']}",
                f"**信心度**：{finding['confidence']}",
            ])
    else:
        report.extend(["## Findings", "無。"])

    report.extend([
        "",
        "## 已通過檢查",
    ])
    for item in sorted(set(passed_checks)):
        report.append(f"- {item}")
    report.extend([
        "",
        "## 審查結論",
        "本次 Review 由分批 AI 分析，並由 deterministic engine 與 policy 統一計算阻擋條件。",
    ])

    body = "\n".join(report)
    publish_review(pr_number, body, decision)
    Path("review.md").write_text(body + "\n", encoding="utf-8")
    Path("ai-review.json").write_text(
        json.dumps(
            {
                "decision": decision,
                "findings": unique_findings,
                "blocking_findings": blocking_findings,
                "batches": len(results),
                "files_reviewed": changed_files,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(body)
    if decision == "REQUEST_CHANGES":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
