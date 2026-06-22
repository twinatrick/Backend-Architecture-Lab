import os

from fastapi import APIRouter, File, Form, UploadFile

from services.stt_service import sound_to_text
from utils.audio import convert_to_wav, get_audio_duration
from utils.file_adapter import generate_object_key, upload_to_minio

router = APIRouter()


@router.post("/stt")
async def stt_endpoint(
    file: UploadFile = File(...),
    language: str = Form("zh"),
):
    tmp_input = f"/tmp/stt_input_{id(file)}"
    with open(tmp_input, "wb") as f:
        f.write(await file.read())
    try:
        wav_path = convert_to_wav(tmp_input)
        duration = get_audio_duration(wav_path)
        text = sound_to_text(wav_path, language)
        ext = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"
        object_key = generate_object_key("stt", ext)
        audio_url = upload_to_minio(
            open(tmp_input, "rb").read(), object_key, file.content_type or "audio/wav"
        )
    finally:
        for p in [tmp_input, wav_path]:
            if os.path.exists(p):
                os.remove(p)
    return {
        "text": text,
        "language": language,
        "duration_sec": round(duration, 2),
        "audio_url": audio_url,
    }
