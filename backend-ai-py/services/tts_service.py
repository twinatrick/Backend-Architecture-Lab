import logging
import os
import tempfile
import time

from services.fallback_tts_engine import FallbackTtsEngine
from services.gpt_sovits_client import GptSovitsClient
from services.tts_exceptions import GptSovitsError
from services.tts_exceptions import TtsTimeoutError
from services.voice_sample_provider import VoiceSampleProvider

logger = logging.getLogger(__name__)


class TtsService:
    """TTS 編排服務：參考音檔解析 → GptSoVits 合成 → 失敗時本地備援。"""

    def __init__(self) -> None:
        self.gpt_sovits = GptSovitsClient()
        self.fallback_engine = FallbackTtsEngine()
        self.voice_sample_provider = VoiceSampleProvider()

    def text_to_sound(
        self,
        text: str,
        language: str,
        voice_sample_key: str | None = None,
        voice_sample_text: str | None = None,
        voice_sample_lang: str = "zh",
    ) -> bytes:
        """合成語音並回傳 WAV bytes。

        僅 GptSoVits 逾時或外部服務錯誤會觸發本地備援；
        其他例外（程式或設定錯誤）直接向上拋出，不隱藏問題。
        """
        ref_payload = self.voice_sample_provider.resolve(
            voice_sample_key, voice_sample_text, voice_sample_lang
        )

        try:
            return self.gpt_sovits.synthesize(text, language, ref_payload)
        except (TtsTimeoutError, GptSovitsError) as exc:
            logger.warning("[TTS] GptSoVits 合成失敗，改用本地備援: %s", exc)
            tmp_path = os.path.join(
                tempfile.gettempdir(), f"tts_fallback_{int(time.time() * 1000)}.wav"
            )
            try:
                return self.fallback_engine.synthesize(text, tmp_path)
            finally:
                if os.path.exists(tmp_path):
                    os.remove(tmp_path)


tts_service = TtsService()
