import os
import tempfile

import av


def convert_to_wav(input_path: str, sample_rate: int = 16000) -> str:
    container = av.open(input_path)
    output_path = tempfile.NamedTemporaryFile(delete=False, suffix=".wav").name
    output = av.open(output_path, "w", format="wav")
    input_stream = container.streams.audio[0]
    output_stream = output.add_stream("pcm_s16le", rate=sample_rate, layout="mono")
    for frame in container.decode(audio=0):
        frame.pts = None
        for packet in output_stream.encode(frame):
            output.mux(packet)
    for packet in output_stream.encode(None):
        output.mux(packet)
    output.close()
    container.close()
    return output_path


def get_audio_duration(file_path: str) -> float:
    container = av.open(file_path)
    duration = float(container.duration) / 1_000_000 if container.duration else 0.0
    container.close()
    return duration
