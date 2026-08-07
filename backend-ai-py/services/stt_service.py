from faster_whisper import WhisperModel

from config import settings

_model: WhisperModel | None = None


def _get_model() -> WhisperModel:
    global _model
    if _model is None:
        _model = WhisperModel(
            model_size_or_path=settings.whisper_model_size,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
        )
    return _model


def sound_to_text(file_path: str, language: str) -> str:
    model = _get_model()
    segments, info = model.transcribe(file_path, beam_size=5, language=language)
    result = "".join(segment.text for segment in segments)
    return result
