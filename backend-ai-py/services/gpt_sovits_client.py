import requests

from config import settings
from services.tts_exceptions import GptSovitsError
from services.tts_exceptions import TtsTimeoutError


class GptSovitsClient:
    """GptSoVits 服務客戶端：負責組裝 payload 並呼叫外部 HTTP 合成服務。"""

    def synthesize(self, text: str, language: str, ref_payload: dict) -> bytes:
        """呼叫 GptSoVits 合成語音，成功回傳 WAV bytes。

        逾時拋出 TtsTimeoutError；連線失敗或 HTTP 錯誤拋出 GptSovitsError；
        其餘例外（程式或設定錯誤）不在此攔截，交由上層處理。
        """
        payload = {
            "text": text,
            "text_lang": language,
            "text_split_method": "cut5",
        }
        payload.update(ref_payload)

        try:
            response = requests.post(settings.gpt_sovit_url, json=payload, timeout=60)
            response.raise_for_status()
        except requests.Timeout as exc:
            raise TtsTimeoutError(
                f"GptSoVits 請求逾時（60s）: {settings.gpt_sovit_url}"
            ) from exc
        except requests.RequestException as exc:
            raise GptSovitsError(
                f"GptSoVits 呼叫失敗（{exc.__class__.__name__}）: {exc}"
            ) from exc

        return response.content
