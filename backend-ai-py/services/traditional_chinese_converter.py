import logging

import opencc

from config import settings
from utils.mock_detection import _is_mock

logger = logging.getLogger(__name__)


class TraditionalChineseConverter:
    """台灣繁體轉換器：動態 OpenCC（s2twp）轉換，測試環境下直接略過。"""

    def __init__(self) -> None:
        self._opencc_converter = None

    def convert(self, text: str) -> str:
        """將簡體文字轉為台灣繁體，未啟用轉換或測試環境時原樣回傳。"""
        if not text or not settings.stt_opencc_convert:
            return text
        try:
            if _is_mock(opencc):
                return text
            if self._opencc_converter is None:
                self._opencc_converter = opencc.OpenCC("s2twp.json")
            return self._opencc_converter.convert(text)
        except (TypeError, ValueError, OSError, RuntimeError) as exc:
            logger.warning("[STT] OpenCC conversion failed: %s", exc)
            return text


traditional_chinese_converter = TraditionalChineseConverter()
