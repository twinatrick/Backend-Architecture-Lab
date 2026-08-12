import os

import sherpa_onnx
import soundfile as sf

from config import settings


class FallbackTtsEngine:
    """本地備援 TTS 引擎：使用 sherpa-onnx 離線合成，於外部服務不可用時接管。"""

    def synthesize(self, text: str, save_path: str) -> bytes:
        """以 sherpa-onnx 合成語音並寫入 save_path，回傳 WAV bytes。"""
        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=os.path.join(settings.tts_model_dir, "breeze2-vits.onnx"),
                    lexicon=os.path.join(settings.tts_model_dir, "lexicon.txt"),
                    tokens=os.path.join(settings.tts_model_dir, "tokens.txt"),
                ),
                provider="cpu",
                num_threads=1,
                debug=False,
            ),
            max_num_sentences=1,
        )
        tts = sherpa_onnx.OfflineTts(config)
        audio = tts.generate(text, sid=0, speed=1.0)
        sf.write(save_path, audio.samples, audio.sample_rate)
        with open(save_path, "rb") as f:
            return f.read()
