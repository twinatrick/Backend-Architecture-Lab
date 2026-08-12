import logging

from minio.error import MinioError

from config import settings
from utils.file_adapter import download_from_minio

logger = logging.getLogger(__name__)


class VoiceSampleProvider:
    """參考音檔提供者：解析 voice sample 並準備 GptSoVits 所需之參考欄位。"""

    def resolve(
        self,
        voice_sample_key: str | None,
        voice_sample_text: str | None,
        voice_sample_lang: str,
    ) -> dict:
        """準備參考音檔與提示文字欄位。

        參考音檔下載失敗（外部儲存服務錯誤）時回傳空 dict，
        讓上層以純文字方式合成，不中斷流程。
        """
        ref_key = voice_sample_key or settings.gpt_sovit_ref_audio_minio_key
        if not ref_key:
            return {}

        try:
            ref_path = download_from_minio(ref_key)
        except (MinioError, OSError) as exc:
            # MinIO 連線或存取失敗屬外部服務錯誤，允許退回純文字合成
            logger.warning("[TTS] 參考音檔下載失敗，改以無參考音檔方式合成: %s", exc)
            return {}

        ref_payload = {"ref_audio_path": ref_path}
        if voice_sample_text:
            ref_payload["prompt_text"] = voice_sample_text
            ref_payload["prompt_lang"] = voice_sample_lang
        else:
            ref_payload["prompt_text"] = settings.gpt_sovit_prompt_text
            ref_payload["prompt_lang"] = settings.gpt_sovit_prompt_lang
        return ref_payload
