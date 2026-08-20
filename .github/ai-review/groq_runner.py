import json
import time

import requests

from key_pool import KeyPool, mask_api_key
from model_pool import ModelPool
from parser import ReviewResponseParser
from providers import GroqClient
from redaction import redact_secrets
from retry_utils import calculate_backoff_delay, parse_retry_after


def execute_groq_loop(
    prompt: str,
    max_retries_per_model: int,
    groq_models: list[str],
    groq_key_pool: KeyPool,
    groq_model_pool: ModelPool,
    groq_client: GroqClient,
    parser: ReviewResponseParser,
    error_details: list[tuple[str, str]],
) -> str | None:
    groq_keys = groq_key_pool.get_all_keys()
    if not groq_keys:
        return None

    for model_name in groq_models:
        tried_keys = set()
        model_unsupported = False
        while True:
            picked = groq_key_pool.pick_random_active_key(
                groq_keys, excluded_keys=tried_keys
            )
            if picked is None:
                break
            var_name, api_key = picked
            tried_keys.add(api_key)
            masked_key = mask_api_key(api_key)
            key_tag = f"Groq/{model_name} [{var_name}:{masked_key}]"
            use_json_mode = True

            for attempt in range(1, max_retries_per_model + 1):
                try:
                    response = groq_client.call(
                        prompt, model_name, api_key, use_json_mode=use_json_mode
                    )
                    if response.ok:
                        raw_content = response.json()["choices"][0]["message"]["content"]
                        try:
                            parser.extract_json_payload(raw_content)
                            print(f"使用 Groq 模型：{model_name}（金鑰：{var_name} {masked_key}）")
                            groq_model_pool.promote(model_name)
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
                            groq_model_pool.demote(model_name)
                            time.sleep(2.0)
                            break

                    status_code = response.status_code
                    resp_text = response.text[:300]
                    reason_msg = f"HTTP {status_code}: {resp_text}"
                    error_details.append((key_tag, redact_secrets(reason_msg)))

                    if status_code == 429:
                        raw_retry_after = parse_retry_after(response)
                        is_tpd = (
                            raw_retry_after > 60.0
                            or "TPD" in resp_text
                            or "daily limit" in resp_text.lower()
                            or "quota" in resp_text.lower()
                            or "tokens per day" in resp_text.lower()
                        )
                        cooldown_seconds = max(raw_retry_after, 3600.0 if is_tpd else 30.0)
                        groq_key_pool.mark_cooldown(api_key, cooldown_seconds)

                        active_keys = groq_key_pool.get_active_keys(groq_keys)
                        if is_tpd:
                            print(
                                f"Groq 金鑰 {var_name} ({masked_key}) 當日配額耗盡 (TPD 429)，"
                                f"已冷卻 {cooldown_seconds:.0f} 秒。"
                            )
                            if not active_keys:
                                print("Groq 所有可用金鑰配額均已耗盡，立即交棒由備援 Provider 接管。")
                                return None
                            print(f"切換下一把可用 Groq 金鑰（剩餘 {len(active_keys)} 把）...")
                            time.sleep(1.0)
                            break

                        if len(active_keys) > 0 and len(groq_keys) > 1:
                            print(
                                f"Groq 金鑰 {var_name} 達到速率限制 (429)，"
                                f"冷卻 {cooldown_seconds:.1f} 秒並切換其他金鑰..."
                            )
                            time.sleep(1.0)
                            break

                        wait_seconds = calculate_backoff_delay(
                            attempt, raw_retry_after, base_delay=2.5, max_delay=60.0
                        )
                        if attempt < max_retries_per_model and raw_retry_after <= 60.0:
                            print(
                                f"模型 {model_name} 暫時速率限制 (429)，"
                                f"等待 {wait_seconds:.1f} 秒後重試"
                                f"（第 {attempt}/{max_retries_per_model} 次）..."
                            )
                            time.sleep(wait_seconds)
                            continue

                        print(f"模型 {model_name} 達到重試上限，切換下一個候選模型。")
                        groq_model_pool.demote(model_name)
                        time.sleep(1.0)
                        model_unsupported = True
                        break

                    if status_code == 403:
                        groq_key_pool.mark_cooldown(api_key, 3600.0)
                        print(
                            f"Groq 金鑰 {var_name} ({masked_key}) 授權失敗 (403)，"
                            "已放入冷卻清單，隨機切換下一把可用金鑰..."
                        )
                        time.sleep(1.0)
                        break

                    if status_code == 413:
                        print(
                            f"Groq 模型 {model_name} 請求負載過大 (413)：{reason_msg}，"
                            "立即降級並切換下一個模型。"
                        )
                        groq_model_pool.demote(model_name)
                        time.sleep(1.0)
                        model_unsupported = True
                        break

                    if status_code == 400 and use_json_mode and "json_validate_failed" in resp_text:
                        wait_seconds = calculate_backoff_delay(
                            attempt, 0.0, base_delay=3.0, max_delay=30.0
                        )
                        print(
                            f"Groq 模型 {model_name} JSON 模式校驗失敗 (400)，"
                            f"延後等待 {wait_seconds:.1f} 秒後降級為純文字模式重試..."
                        )
                        use_json_mode = False
                        time.sleep(wait_seconds)
                        continue

                    if status_code == 400:
                        print(
                            f"Groq 模型 {model_name} 請求參數或格式錯誤 (400)：{reason_msg}，"
                            "降級至備援清單尾端並切換下一個模型。"
                        )
                        groq_model_pool.demote(model_name)
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
    return None
