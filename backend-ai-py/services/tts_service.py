import os
import time

import requests
import sherpa_onnx
import soundfile as sf

from config import settings


def _text_to_sound_fallback(text: str, save_path: str) -> bytes:
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


def text_to_sound(text: str, language: str) -> bytes:
    payload = {
        "text": text,
        "text_lang": language,
        "text_split_method": "cut5",
    }
    try:
        response = requests.post(settings.gpt_sovit_url, json=payload, timeout=60)
        response.raise_for_status()
        return response.content
    except Exception:
        tmp_path = f"tmp_tts_{int(time.time())}.wav"
        return _text_to_sound_fallback(text, tmp_path)
