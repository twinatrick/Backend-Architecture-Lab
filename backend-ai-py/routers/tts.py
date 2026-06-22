from pydantic import BaseModel

from fastapi import APIRouter

from services.tts_service import text_to_sound
from utils.file_adapter import generate_object_key, upload_to_minio

router = APIRouter()


class TtsRequest(BaseModel):
    text: str
    language: str = "zh"
    voiceSampleKey: str | None = None
    voiceSampleText: str | None = None
    voiceSampleLang: str = "zh"


@router.post("/tts")
async def tts_endpoint(body: TtsRequest):
    audio_bytes = text_to_sound(body.text, body.language,
                                body.voiceSampleKey,
                                body.voiceSampleText,
                                body.voiceSampleLang)
    object_key = generate_object_key("tts", ".wav")
    audio_url = upload_to_minio(audio_bytes, object_key, "audio/wav")
    return {"audio_url": audio_url}
