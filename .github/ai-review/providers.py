import requests

from key_pool import get_groq_api_keys

SYSTEM_CONTENT = (
    "你必須使用繁體中文。只輸出單一合法 JSON 物件，嚴禁輸出 <think> 思維鏈標籤、"
    "任何 Markdown 標記或解釋性文字。確保所有字串與引號正確閉合。不得捏造 Finding。"
)


class GroqClient:
    """封裝 Groq Chat Completions API 請求。"""

    SYSTEM_CONTENT = SYSTEM_CONTENT

    def __init__(self, timeout: int = 120):
        self.timeout = timeout

    def call(
        self,
        prompt: str,
        model_name: str,
        api_key: str,
        use_json_mode: bool = True,
    ) -> requests.Response:
        request_payload = {
            "model": model_name,
            "messages": [
                {"role": "system", "content": self.SYSTEM_CONTENT},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.1,
            "max_tokens": 4096,
        }
        if use_json_mode:
            request_payload["response_format"] = {"type": "json_object"}

        return requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json=request_payload,
            timeout=self.timeout,
        )

    def get_available_models(self, api_key: str) -> set[str]:
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
        return {
            model_item.get("id")
            for model_item in model_entries
            if "id" in model_item
        }


class GeminiClient:
    """封裝 Google Gemini API 請求與文字提取。"""

    SYSTEM_CONTENT = SYSTEM_CONTENT

    def __init__(self, timeout: int = 120):
        self.timeout = timeout

    def call(
        self,
        prompt: str,
        model_name: str,
        api_key: str,
    ) -> requests.Response:
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{model_name}:generateContent"
        )
        payload = {
            "systemInstruction": {
                "parts": [{"text": self.SYSTEM_CONTENT}],
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
        return requests.post(url, headers=headers, json=payload, timeout=self.timeout)

    @staticmethod
    def extract_text(response_json: dict) -> str:
        candidates = response_json.get("candidates", [])
        if not candidates:
            raise ValueError(f"Gemini 回應未包含 candidates: {response_json}")
        candidate = candidates[0]
        content = candidate.get("content", {})
        parts = content.get("parts", [])
        if not parts or "text" not in parts[0]:
            raise ValueError(f"Gemini 回應 candidate 格式不正確: {candidate}")
        return parts[0]["text"]


def call_gemini_api(
    prompt: str,
    model_name: str,
    api_key: str,
    timeout: int = 120,
) -> requests.Response:
    client = GeminiClient(timeout=timeout)
    return client.call(prompt, model_name, api_key)


def extract_gemini_text(response_json: dict) -> str:
    return GeminiClient.extract_text(response_json)


def get_available_models():
    groq_keys = get_groq_api_keys()
    if not groq_keys:
        return set()
    api_key = groq_keys[0][1]
    client = GroqClient()
    return client.get_available_models(api_key)
