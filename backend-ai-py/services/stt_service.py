import os
import tempfile

from config import settings
from services.diarization_service import (
    _detect_diarization_device,
    _resolve_conda_python,
    _run_diarization,
)
from services.formatter_service import _convert_tw_traditional
from services.sensevoice_service import _transcribe_sensevoice
from services.whisper_service import _get_whisper_model, _transcribe_whisper
from utils.audio import _prepare_audio


def sound_to_text(file_path: str, language: str, provider: str = "") -> str:
    """將音訊檔案轉換為文字。

    流程：
    A. 依設定啟動語者分離子進程（獨立 pyannote-env 環境，避免 CUDA/DLL 衝突）
    B. 主程序依 provider 載入 SenseVoice 或 Whisper 進行轉譯

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
                print(
                    f"[STT] Launching Diarization subprocess "
                    f"({settings.diarization_env_name}) on device: {device}..."
                )
                diarization_result = _run_diarization(
                    pyannote_python, stt_input_file, diarization_json, device
                )
            else:
                print(
                    f"[STT] 找不到語者分離環境 '{settings.diarization_env_name}'，"
                    "略過語者分離（請參考語者分離環境安裝說明）。"
                )

        # B. 主程序依 provider 載入 SenseVoice 或 Whisper 進行轉錄
        effective_provider = provider or settings.stt_provider
        if effective_provider == "sensevoice":
            text = _transcribe_sensevoice(stt_input_file)
            if text:
                return _convert_tw_traditional(text)

        model = _get_whisper_model()
        lang = language if language else (settings.whisper_language if settings.whisper_language else None)
        segments, info = model.transcribe(stt_input_file, beam_size=5, language=lang)
        segment_list = list(segments)

        return _transcribe_whisper(segment_list, diarization_result)
    finally:
        for temp_f in (diarization_json, temp_wav):
            if temp_f and os.path.exists(temp_f):
                try:
                    os.remove(temp_f)
                except Exception:
                    pass