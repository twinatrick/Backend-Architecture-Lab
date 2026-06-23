import uuid
from pydantic import BaseModel

from fastapi import APIRouter

from services.tts_service import text_to_sound
from utils.file_adapter import upload_to_minio
from utils.audio import convert_wav_to_m4a

router = APIRouter()


class TtsRequest(BaseModel):
    text: str
    language: str = "zh"
    voiceSampleKey: str | None = None
    voiceSampleText: str | None = None
    voiceSampleLang: str = "zh"


@router.post("/tts")
async def tts_endpoint(body: TtsRequest):
    audio_bytes = text_to_sound(
        body.text,
        body.language,
        body.voiceSampleKey,
        body.voiceSampleText,
        body.voiceSampleLang,
    )
    # 將 WAV 轉換為符合手機版 LINE 要求之 M4A 壓縮格式
    m4a_bytes = convert_wav_to_m4a(audio_bytes)
    object_key = f"tts/{uuid.uuid4().hex}.m4a"
    audio_url = upload_to_minio(m4a_bytes, object_key, "audio/x-m4a")
    return {"audio_url": audio_url}
