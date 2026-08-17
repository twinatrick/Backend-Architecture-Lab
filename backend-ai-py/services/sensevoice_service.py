import logging
from typing import Any

import sherpa_onnx
import soundfile as sf

from config import settings
from utils.mock_detection import _is_mock
from utils.paths import _resolve_path

logger = logging.getLogger(__name__)

_sensevoice_recognizer = None


def _get_sensevoice_recognizer() -> Any:
    """延遲載入 SenseVoice (sherpa-onnx) 離線辨識器並快取"""
    global _sensevoice_recognizer
    if _sensevoice_recognizer is None:
        if _is_mock(sherpa_onnx):
            raise ImportError("sherpa_onnx is mocked in testing environment")

        _sensevoice_recognizer = sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=_resolve_path(settings.sensevoice_model_path),
            tokens=_resolve_path(settings.sensevoice_tokens_path),
            num_threads=settings.sensevoice_num_threads,
            use_itn=settings.sensevoice_use_itn,
            provider=settings.sensevoice_provider,
            debug=False,
        )
    return _sensevoice_recognizer


def _transcribe_sensevoice(audio_path: str) -> str:
    """主程序載入 SenseVoice (sherpa-onnx) 進行本地辨識備援"""
    try:
        if _is_mock(sherpa_onnx) or _is_mock(sf):
            raise ImportError("sherpa_onnx or soundfile is mocked")

        recognizer = _get_sensevoice_recognizer()
        samples, sample_rate = sf.read(audio_path, dtype="float32")
        if len(samples.shape) > 1:
            samples = samples.mean(axis=1)

        stream = recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        recognizer.decode_stream(stream)
        return stream.result.text.strip()
    except (ImportError, RuntimeError, OSError, ValueError) as exc:
        logger.warning("[STT] SenseVoice failed: %s", exc)
        return ""
