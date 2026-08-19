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
def parse_retry_limit(raw_value: str | None, default: int = 9) -> int:
    if raw_value is None or not str(raw_value).strip():
        return default
    try:
        val = int(raw_value)
        return val if val >= 1 else default
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
    return os.environ.get("GROQ_API_KEY") or GROQ_API_KEY


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
    report = [
        "# AI Architecture & Security Review",
        "",
        "## 審查結果",
        "REQUEST_CHANGES",
        "",
        f"## 🔴 {title}",
        f"**原因**：{reason}",
        "",
    ]
    if details:
        report.append("**詳細資訊**：")
        if isinstance(details, list):
            for detail_item in details:
                if isinstance(detail_item, (tuple, list)) and len(detail_item) == 2:
                    report.append(f"- `{detail_item[0]}`：{detail_item[1]}")
                else:
                    report.append(f"- {detail_item}")
        else:
            report.append(f"```\n{details}\n```")
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
    api_key = get_groq_api_key()
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
        return candidates[-1]

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
    api_key = get_groq_api_key()
    candidates = get_candidate_models()

    attempted_models = []
    error_details = []
    for model_name in candidates:
        attempted_models.append(model_name)
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
                        # 驗證輸出是否可被成功解析為合法 JSON 物件
                        extract_json_payload(raw_content)
                        print(f"使用 Groq 模型：{model_name}")
                        # 自適應調度：將運作成功的模型提升為第一優先順位
                        if model_name in ACTIVE_MODEL_CANDIDATES:
                            ACTIVE_MODEL_CANDIDATES.remove(model_name)
                            ACTIVE_MODEL_CANDIDATES.insert(0, model_name)
                        return raw_content
                    except json.JSONDecodeError as json_exc:
                        json_err_msg = f"第 {attempt} 次輸出非合法 JSON：{json_exc}"
                        error_details.append((model_name, json_err_msg))
                        print(
                            f"模型 {model_name} 輸出非合法 JSON"
                            f"（第 {attempt}/{max_retries_per_model} 次）：{json_exc}，退避等待後重試..."
                        )
                        if attempt < max_retries_per_model:
                            wait_seconds = calculate_backoff_delay(
                                attempt, 0.0, base_delay=2.0, max_delay=30.0
                            )
                            time.sleep(wait_seconds)
                            continue

                        print(
                            f"模型 {model_name} 連續 {max_retries_per_model} 次輸出非合法 JSON，"
                            "降級至備援清單尾端並切換下一個模型。"
                        )
                        if model_name in ACTIVE_MODEL_CANDIDATES:
                            ACTIVE_MODEL_CANDIDATES.remove(model_name)
                            ACTIVE_MODEL_CANDIDATES.append(model_name)
                        time.sleep(3.0)
                        break

                status_code = response.status_code
                reason_msg = f"HTTP {status_code}: {response.text[:300]}"
                error_details.append((model_name, reason_msg))

                # 處理 429 速率限制指數退避重試
                if status_code == 429:
                    raw_retry_after = parse_retry_after(response)
                    wait_seconds = calculate_backoff_delay(
                        attempt, raw_retry_after, base_delay=2.5, max_delay=90.0
                    )
                    if attempt < max_retries_per_model:
                        print(
                            f"模型 {model_name} 達到速率限制 (429)，"
                            f"指數退避等待 {wait_seconds:.1f} 秒後重試"
                            f"（第 {attempt}/{max_retries_per_model} 次）..."
                        )
                        time.sleep(wait_seconds)
                        continue

                    print(f"模型 {model_name} 達到重試上限且額度已滿，降級至備援清單尾端並切換下一個模型。")
                    if model_name in ACTIVE_MODEL_CANDIDATES:
                        ACTIVE_MODEL_CANDIDATES.remove(model_name)
                        ACTIVE_MODEL_CANDIDATES.append(model_name)
                    time.sleep(3.0)
                    break

                # 處理 400 JSON 模式校驗失敗：延後退避並降級為一般文字模式重試
                if status_code == 400 and use_json_mode and "json_validate_failed" in response.text:
                    wait_seconds = calculate_backoff_delay(
                        attempt, 0.0, base_delay=3.0, max_delay=30.0
                    )
                    print(
                        f"模型 {model_name} JSON 模式校驗失敗 (400)，"
                        f"延後等待 {wait_seconds:.1f} 秒後降級為純文字模式並重試..."
                    )
                    use_json_mode = False
                    time.sleep(wait_seconds)
                    continue

                # 其他 400 格式錯誤：延後降級該模型並冷卻切換
                if status_code == 400:
                    print(
                        f"模型 {model_name} 請求參數或格式錯誤 (400)：{reason_msg}，"
                        "降級至備援清單尾端並切換下一個模型。"
                    )
                    if model_name in ACTIVE_MODEL_CANDIDATES:
                        ACTIVE_MODEL_CANDIDATES.remove(model_name)
                        ACTIVE_MODEL_CANDIDATES.append(model_name)
                    time.sleep(3.0)
                    break

                print(f"模型 {model_name} 請求失敗：{reason_msg}")
                time.sleep(3.0)
                break
            except requests.RequestException as exc:
                error_details.append((model_name, str(exc)))
                print(f"模型 {model_name} 連線異常：{exc}")
                if attempt < max_retries_per_model:
                    wait_seconds = calculate_backoff_delay(
                        attempt, 2.0, base_delay=2.5, max_delay=90.0
                    )
                    time.sleep(wait_seconds)
                    continue
                time.sleep(3.0)
                break

    raise RuntimeError(json.dumps(error_details, ensure_ascii=False))


def main():
    if not get_gh_token() or not get_groq_api_key():
        raise SystemExit("未配置必要的信任密鑰（GH_TOKEN 或 GROQ_API_KEY）。")

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
                "所有已配置的 Groq 模型均無法取得有效回應。",
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
