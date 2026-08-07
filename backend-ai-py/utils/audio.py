import os
import subprocess
import sys
import tempfile

import av


def convert_to_wav(input_path: str, sample_rate: int = 16000) -> str:
    container = av.open(input_path)
    output_path = tempfile.NamedTemporaryFile(delete=False, suffix=".wav").name
    output = av.open(output_path, "w", format="wav")
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


def _prepare_audio(file_path: str):
    """智慧型格式預處理：非 WAV 格式（如 .mp3）以當前環境 ffmpeg 轉為 16kHz mono WAV"""
    ext = os.path.splitext(file_path.lower())[1]
    if ext == ".wav":
        return file_path, None

    temp_wav = None
    try:
        conda_env_dir = os.path.dirname(sys.executable)
        ffmpeg_exe = os.path.join(conda_env_dir, "Library", "bin", "ffmpeg.exe")
        if not os.path.exists(ffmpeg_exe):
            ffmpeg_exe = "ffmpeg"

        fd, temp_wav = tempfile.mkstemp(suffix="_stt_input.wav")
        os.close(fd)
        subprocess.run(
            [ffmpeg_exe, "-y", "-i", file_path, "-ar", "16000", "-ac", "1", temp_wav],
            capture_output=True,
            check=True,
        )
        print("[STT] Up-front WAV conversion completed successfully.")
        return temp_wav, temp_wav
    except Exception as e:
        print(f"[STT] FFmpeg 前置轉檔失敗，嘗試直接讀取原檔: {e}")
        if temp_wav and os.path.exists(temp_wav):
            try:
                os.remove(temp_wav)
            except Exception:
                pass
        return file_path, None


def convert_wav_to_m4a(wav_bytes: bytes) -> bytes:
    with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tf_in:
        tf_in.write(wav_bytes)
        tf_in_name = tf_in.name

    tf_out_name = tempfile.NamedTemporaryFile(delete=False, suffix=".m4a").name
    try:
        # 使用 ffmpeg 將 wav 轉成 aac 編碼的 m4a，並使用 faststart 優化串流播放
        cmd = [
            "ffmpeg",
            "-y",
            "-i",
            tf_in_name,
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-movflags",
            "+faststart",
            tf_out_name,
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        with open(tf_out_name, "rb") as f:
            m4a_bytes = f.read()
        return m4a_bytes
    finally:
        if os.path.exists(tf_in_name):
            os.remove(tf_in_name)
        if os.path.exists(tf_out_name):
            os.remove(tf_out_name)
