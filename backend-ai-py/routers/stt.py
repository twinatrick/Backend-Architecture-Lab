import os

from fastapi import APIRouter

from config import settings
from services.stt_service import sound_to_text
from utils.audio import convert_to_wav
from utils.audio import get_audio_duration
from utils.file_adapter import download_from_minio

router = APIRouter()


@router.post("/stt")
async def stt_endpoint(
    object_key: str,
    language: str = "zh",
):
    tmp_input = None
    wav_path = None

    try:
        tmp_input = download_from_minio(object_key, settings.minio_bucket_stt)

        wav_path = convert_to_wav(tmp_input)
        duration = get_audio_duration(wav_path)
        text = sound_to_text(wav_path, language)

        audio_url = (
            f"{settings.minio_endpoint.rstrip('/')}/{settings.minio_bucket_audio}/{object_key}"
        )

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
