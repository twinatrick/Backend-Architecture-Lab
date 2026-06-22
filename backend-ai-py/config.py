import pathlib

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    server_port: int = 5001
    service_name: str = "ai-py-service"

    nacos_server_addr: str = "localhost:8848"
    nacos_namespace: str = ""

    ollama_base_url: str = "http://localhost:11434"
    llm_model: str = "qwen:7b-q4_K_M"
    llm_temperature: float = 0.5

    whisper_model_size: str = "base"
    whisper_device: str = "cpu"
    whisper_compute_type: str = "int8"

    tts_model_dir: str = "tts_models"
    gpt_sovit_url: str = "http://127.0.0.1:9880/tts"
    gpt_sovit_ref_audio_minio_key: str = "tts-refs/girlfriend-voice.wav"
    gpt_sovit_prompt_text: str = "いいんじゃない。最近、一緒に歌ってる人の声に合わせられるようになってきたし"
    gpt_sovit_prompt_lang: str = "ja"

    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket_audio: str = "audio"
    minio_region: str = "us-east-1"

    model_config = SettingsConfigDict(
        env_file=pathlib.Path(__file__).parent / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
