import json
import logging
import re


class ReviewResponseParser:
    """負責 LLM 審查回應的 JSON 提取與格式修復。"""

    @staticmethod
    def find_balanced_json_substrings(text: str) -> list[str]:
        candidates = []
        idx = 0
        text_len = len(text)
        while idx < text_len:
            if text[idx] == "{":
                start = idx
                depth = 0
                in_string = False
                escape = False
                char_idx = idx
                while char_idx < text_len:
                    char = text[char_idx]
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
                                candidates.append(text[start : char_idx + 1])
                                idx = char_idx
                                break
                    char_idx += 1
            idx += 1
        return candidates

    @staticmethod
    def repair_json_string(text: str) -> str:
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
        candidates = ReviewResponseParser.find_balanced_json_substrings(cleaned)
        valid_review_payloads = []
        valid_other_dicts = []
        for cand in candidates:
            try:
                parsed = json.loads(cand)
                if isinstance(parsed, dict):
                    if (
                        "batch" in parsed
                        and "files_reviewed" in parsed
                        and "findings" in parsed
                    ):
                        valid_review_payloads.append(cand)
                    else:
                        valid_other_dicts.append(cand)
            except (json.JSONDecodeError, ValueError, TypeError) as exc:
                logging.debug("候選 JSON 子字串解析略過: %s", exc)
                continue

        if len(valid_review_payloads) == 1:
            return valid_review_payloads[0]
        elif len(valid_review_payloads) > 1:
            raise json.JSONDecodeError(
                "輸出包含多個相衝的 Review JSON 物件，無法確定唯一根結構", cleaned, 0
            )

        if len(valid_other_dicts) == 1:
            return valid_other_dicts[0]
        elif len(valid_other_dicts) > 1:
            raise json.JSONDecodeError("輸出包含多個歧異 JSON 物件", cleaned, 0)

        if candidates:
            raise json.JSONDecodeError(
                "輸出包含無法識別為合法物件的 JSON 片段", cleaned, 0
            )

        return cleaned

    @staticmethod
    def extract_json_payload(raw_text: str) -> dict:
        if not raw_text or not isinstance(raw_text, str):
            raise json.JSONDecodeError("輸出為空或型別錯誤", "", 0)

        repaired = ReviewResponseParser.repair_json_string(raw_text)
        parsed = json.loads(repaired)
        if isinstance(parsed, dict):
            findings = parsed.get("findings")
            if isinstance(findings, list):
                for f in findings:
                    if isinstance(f, dict):
                        cat = str(f.get("category", "")).strip().upper()
                        if not cat:
                            rule_text = (
                                str(f.get("rule", "")) + " " + str(f.get("problem", ""))
                            ).lower()
                            if any(
                                k in rule_text
                                for k in (
                                    "security", "secret", "permission", "auth", "token",
                                    "inject", "injection", "注入", "安全", "機密", "權限"
                                )
                            ):
                                f["category"] = "SECURITY"
                            elif any(
                                k in rule_text
                                for k in (
                                    "architecture", "entity", "layer", "feign", "controller",
                                    "service", "架構", "分層"
                                )
                            ):
                                f["category"] = "ARCHITECTURE"
                            else:
                                f["category"] = "COMPLIANCE"
                        else:
                            f["category"] = cat
            return parsed
        raise json.JSONDecodeError("JSON 頂層結構必須為物件（dict）", repaired, 0)


def find_balanced_json_substrings(text: str) -> list[str]:
    return ReviewResponseParser.find_balanced_json_substrings(text)


def repair_json_string(text: str) -> str:
    return ReviewResponseParser.repair_json_string(text)


def extract_json_payload(raw_text: str) -> dict:
    return ReviewResponseParser.extract_json_payload(raw_text)
