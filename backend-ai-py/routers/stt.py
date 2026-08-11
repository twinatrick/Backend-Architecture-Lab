from fastapi import APIRouter

from services.stt_service import stt_service

router = APIRouter()


@router.post("/stt")
async def stt_endpoint(
    object_key: str,
    language: str = "zh",
    provider: str = "",
):
    """預設 STT 端點，可依 provider 參數指定轉譯引擎。"""
    return stt_service.transcribe_audio(object_key, language, provider)


@router.post("/stt/whisper")
async def stt_whisper_endpoint(
    object_key: str,
    language: str = "zh",
):
    """指定 Whisper 引擎的 STT 端點。"""
    return stt_service.transcribe_audio(object_key, language, "whisper")


@router.post("/stt/sensevoice")
async def stt_sensevoice_endpoint(
    object_key: str,
    language: str = "zh",
):
    """指定 SenseVoice 引擎的 STT 端點。"""
    return stt_service.transcribe_audio(object_key, language, "sensevoice")
