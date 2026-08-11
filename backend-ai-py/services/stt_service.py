import os

from config import settings
from services.audio_converter import AudioConverter
from services.audio_storage import AudioStorage
from services.stt_engine import SttEngine


def sound_to_text(file_path: str, language: str, provider: str = "") -> str:
    """將音訊檔案轉換為文字（相容入口，實際委託 SttEngine）。"""
    return SttEngine().transcribe(file_path, language, provider)


class SttService:
    """STT 編排服務：整合下載、轉檔、辨識與清理，組裝 API 回應資料。"""

    def __init__(self) -> None:
        self.storage = AudioStorage()
        self.converter = AudioConverter()
        self.engine = SttEngine()

    def transcribe_audio(
        self, object_key: str, language: str = "zh", provider: str = ""
    ) -> dict:
        """執行 STT 完整管線（下載 → 轉檔 → 辨識）並回傳回應資料。"""
        tmp_input = None
        wav_path = None

        try:
            tmp_input = self.storage.download(object_key, settings.minio_bucket_stt)

            wav_path = self.converter.convert_to_wav(tmp_input)
            duration = self.converter.get_duration(wav_path)
            text = self.engine.transcribe(wav_path, language, provider)

            audio_url = self.storage.build_audio_url(object_key)
        finally:
            self._cleanup([wav_path, tmp_input])

        return {
            "text": text,
            "language": language,
            "duration_sec": round(duration, 2),
            "audio_url": audio_url,
        }

    @staticmethod
    def _cleanup(paths) -> None:
        """清理暫存檔案，失敗僅記錄不影響主流程。"""
        for p in paths:
            if p and os.path.exists(p):
                try:
                    os.remove(p)
                except Exception as e:
                    print(f"Failed to remove temp file {p}: {e}")


stt_service = SttService()
