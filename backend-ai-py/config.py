import pathlib

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    server_port: int = 5001
    service_name: str = "ai-py-service"

    nacos_server_addr: str = "localhost:8848"
    nacos_namespace: str = ""

    ollama_base_url: str = "http://localhost:11434"
    llm_model: str = "gemma4:31b-cloud"
    llm_temperature: float = 0.5

    whisper_model_size: str = "large-v3-turbo"
    whisper_device: str = "cuda"
    whisper_compute_type: str = "float16"
    whisper_language: str = ""

    stt_provider: str = "whisper"
    sensevoice_model_path: str = "models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/model.onnx"
    sensevoice_tokens_path: str = "models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt"
    sensevoice_num_threads: int = 4
    sensevoice_use_itn: bool = True
    sensevoice_provider: str = "cpu"
    stt_opencc_convert: bool = True
    stt_timestamps: bool = False

    # 語者分離與 HuggingFace 整合
    hf_token: str = ""
    stt_diarization: bool = False
    stt_speaker_names: str = ""
    stt_dialogue_mode: bool = False
    stt_compute_strategy: str = "auto"
    # 語者分離用的獨立虛擬環境名稱（Python 路徑由 conda env list --json 動態解析）
    diarization_env_name: str = "pyannote-env"
    # 語者分離模型名稱（由父程序注入子進程環境變數 DIARIZATION_MODEL）
    diarization_model: str = "pyannote/speaker-diarization-3.1"

    tts_model_dir: str = "tts_models"
    gpt_sovit_url: str = "http://127.0.0.1:9880/tts"
    gpt_sovit_ref_audio_minio_key: str = "tts-refs/girlfriend-voice.wav"
    gpt_sovit_prompt_text: str = (
        "いいんじゃない。最近、一緒に歌ってる人の声に合わせられるようになってきたし"
    )
    gpt_sovit_prompt_lang: str = "ja"

    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket_audio: str = "audio"
    minio_bucket_stt: str = "user-audio"
    minio_region: str = "us-east-1"

    model_config = SettingsConfigDict(
        env_file=pathlib.Path(__file__).parent / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
