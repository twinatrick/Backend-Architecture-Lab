import json
import time
import requests

from key_pool import KeyPool, mask_api_key
from model_pool import ModelPool
from parser import ReviewResponseParser
from providers import GeminiClient
from redaction import redact_secrets
from retry_utils import calculate_backoff_delay, parse_retry_after


def execute_gemini_loop(
    prompt: str,
    max_retries_per_model: int,
    gemini_models: list[str],
    gemini_key_pool: KeyPool,
    gemini_model_pool: ModelPool,
    gemini_client: GeminiClient,
    parser: ReviewResponseParser,
    error_details: list[tuple[str, str]],
) -> str | None:
    gemini_keys = gemini_key_pool.get_all_keys()
    if not gemini_keys:
        return None

    for model_name in gemini_models:
        tried_keys = set()
        model_unsupported = False
        while True:
            picked = gemini_key_pool.pick_random_active_key(
                gemini_keys, excluded_keys=tried_keys
            )
            if picked is None:
                break
            var_name, api_key = picked
            tried_keys.add(api_key)
            masked_key = mask_api_key(api_key)
            key_tag = f"Gemini/{model_name} [{var_name}:{masked_key}]"

            for attempt in range(1, max_retries_per_model + 1):
                try:
                    response = gemini_client.call(prompt, model_name, api_key)
                    if response.ok:
                        try:
                            resp_json = response.json()
                            raw_content = gemini_client.extract_text(resp_json)
                            parser.extract_json_payload(raw_content)
                            print(f"使用 Google Gemini 模型：{model_name}（金鑰：{var_name} {masked_key}）")
                            gemini_model_pool.promote(model_name)
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

                    if status_code == 429 or "RESOURCE_EXHAUSTED" in resp_text:
                        raw_retry_after = parse_retry_after(response)
                        cooldown_seconds = max(raw_retry_after, 60.0)
                        if "quota" in resp_text.lower() or "RESOURCE_EXHAUSTED" in resp_text:
                            cooldown_seconds = max(cooldown_seconds, 300.0)

                        if len(gemini_keys) > 1:
                            gemini_key_pool.mark_cooldown(api_key, cooldown_seconds)
                            print(
                                f"Gemini 金鑰 {var_name} ({masked_key}) 達到速率限制或配額耗盡 (429)，"
                                f"已放入冷卻清單（{cooldown_seconds:.1f} 秒），隨機切換下一把可用金鑰..."
                            )
                            time.sleep(1.0)
                            break

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
                        gemini_model_pool.demote(model_name)
                        time.sleep(2.0)
                        model_unsupported = True
                        break

                    if status_code == 403:
                        gemini_key_pool.mark_cooldown(api_key, 3600.0)
                        print(
                            f"Gemini 金鑰 {var_name} ({masked_key}) 授權失敗或額度無效 (403)，"
                            "已放入冷卻清單，隨機切換下一把可用金鑰..."
                        )
                        time.sleep(1.0)
                        break

                    if status_code == 413:
                        print(f"Gemini 模型 {model_name} 請求負載過大 (413)，切換下一候選模型...")
                        gemini_model_pool.demote(model_name)
                        time.sleep(1.0)
                        model_unsupported = True
                        break

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
    return None
