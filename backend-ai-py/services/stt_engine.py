import logging
import os
import tempfile

from config import settings
from services.diarization_service import _detect_diarization_device
from services.diarization_service import _resolve_conda_python
from services.diarization_service import _run_diarization
from services.sensevoice_service import _transcribe_sensevoice
from services.traditional_chinese_converter import traditional_chinese_converter
from services.whisper_service import _get_whisper_model
from services.whisper_service import _transcribe_whisper
from utils.audio import _prepare_audio

logger = logging.getLogger(__name__)


class SttEngine:
    """語音辨識引擎：負責音訊前置處理、語者分離與依 provider 分派轉譯。"""

    def transcribe(self, file_path: str, language: str, provider: str = "") -> str:
        """將音訊檔案轉換為文字。

        流程：
        A. 依設定啟動語者分離子進程（獨立 pyannote-env 環境，避免 CUDA/DLL 衝突）
        B. 依 provider 載入 SenseVoice 或 Whisper 進行轉譯

        provider 可顯式指定轉譯引擎（"sensevoice" / "whisper"），
        傳入空白時回退到 settings.stt_provider。
        """
        stt_input_file, temp_wav = _prepare_audio(file_path)
        diarization_json = None

        try:
            # A. 語者分離子進程（獨立 pyannote-env 環境，避免 CUDA/DLL 衝突）
            diarization_result = []
            if settings.stt_diarization and settings.hf_token:
                pyannote_python = _resolve_conda_python(settings.diarization_env_name)
                if pyannote_python:
                    fd, diarization_json = tempfile.mkstemp(suffix="_diar.json")
                    os.close(fd)
                    device = _detect_diarization_device()
                    logger.info(
                        "[STT] Launching Diarization subprocess (%s) on device: %s...",
                        settings.diarization_env_name,
                        device,
                    )
                    diarization_result = _run_diarization(
                        pyannote_python, stt_input_file, diarization_json, device
                    )
                else:
                    logger.warning(
                        "[STT] 找不到語者分離環境 '%s'，"
                        "略過語者分離（請參考語者分離環境安裝說明）。",
                        settings.diarization_env_name,
                    )

            # B. 依 provider 載入 SenseVoice 或 Whisper 進行轉錄
            effective_provider = provider or settings.stt_provider
            if effective_provider == "sensevoice":
                text = _transcribe_sensevoice(stt_input_file)
                if text:
                    return traditional_chinese_converter.convert(text)

            model = _get_whisper_model()
            default_lang = settings.whisper_language if settings.whisper_language else None
            lang = language if language else default_lang
            segments, _ = model.transcribe(stt_input_file, beam_size=5, language=lang)
            segment_list = list(segments)

            return _transcribe_whisper(segment_list, diarization_result)
        finally:
            for temp_f in (diarization_json, temp_wav):
                if temp_f and os.path.exists(temp_f):
                    try:
                        os.remove(temp_f)
                    except OSError as exc:
                        # 暫存檔清理失敗不影響辨識結果，僅記錄供排查
                        logger.warning("[STT] 暫存檔清理失敗 %s: %s", temp_f, exc)
