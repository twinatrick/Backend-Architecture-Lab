import os

from fastapi import APIRouter

from config import settings
from services.stt_service import sound_to_text
from utils.audio import convert_to_wav, get_audio_duration
from utils.file_adapter import download_from_minio

router = APIRouter()


async def _run_stt(object_key: str, language: str, provider: str = "") -> dict:
    """STT 共用管線：下載 → 轉檔 → 辨識 → 回傳結果。"""
    tmp_input = None
    wav_path = None

    try:
        tmp_input = download_from_minio(object_key, settings.minio_bucket_stt)

        wav_path = convert_to_wav(tmp_input)
        duration = get_audio_duration(wav_path)
        text = sound_to_text(wav_path, language, provider)

        audio_url = f"{settings.minio_endpoint.rstrip('/')}/{settings.minio_bucket_audio}/{object_key}"

    finally:
        paths_to_clean = []
        if wav_path:
            paths_to_clean.append(wav_path)
        if tmp_input:
            paths_to_clean.append(tmp_input)

        for p in paths_to_clean:
            if os.path.exists(p):
                try:
                    os.remove(p)
                except Exception as e:
                    print(f"Failed to remove temp file {p}: {e}")

    return {
        "text": text,
        "language": language,
        "duration_sec": round(duration, 2),
        "audio_url": audio_url,
    }


@router.post("/stt")
async def stt_endpoint(
    object_key: str,
    language: str = "zh",
    provider: str = "",
):
    """預設 STT 端點，可依 provider 參數指定轉譯引擎。"""
    return await _run_stt(object_key, language, provider)


@router.post("/stt/whisper")
async def stt_whisper_endpoint(
    object_key: str,
    language: str = "zh",
):
    """指定 Whisper 引擎的 STT 端點。"""
    return await _run_stt(object_key, language, "whisper")


@router.post("/stt/sensevoice")
async def stt_sensevoice_endpoint(
    object_key: str,
    language: str = "zh",
):
    """指定 SenseVoice 引擎的 STT 端點。"""
    return await _run_stt(object_key, language, "sensevoice")
