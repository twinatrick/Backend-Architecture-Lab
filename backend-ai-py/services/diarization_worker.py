import json
import os
import sys

import soundfile
import torch
import yaml
from huggingface_hub import hf_hub_download
from numpy import ndarray
from pyannote.audio import Pipeline
from pyannote.audio.core.plda import PLDA

# 避免 Windows 下 OpenMP 重複載入衝突
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"


class DisabledPLDA(PLDA):
    """舊版凝聚式分群不使用 PLDA，僅滿足新版建構子的型別契約。"""

    def __init__(self) -> None:
        """刻意不載入新版 PLDA 權重；僅能搭配不使用 PLDA 的分群。"""

    @property
    def phi(self) -> ndarray:
        raise RuntimeError("既有 3.1 模型已停用 PLDA，請勿改用 VBx 分群。")

    def __call__(self, embeddings: ndarray) -> ndarray:
        raise RuntimeError("既有 3.1 模型已停用 PLDA，請勿改用 VBx 分群。")


def load_pipeline(model_name: str, hf_token: str) -> Pipeline:
    # 保留既有 3.1 模型與授權；避免 4.x 預設額外下載 community-1 的 PLDA。
    checkpoint = model_name
    if model_name == "pyannote/speaker-diarization-3.1":
        config_path = hf_hub_download(model_name, "config.yaml", token=hf_token)
        with open(config_path, encoding="utf-8") as config_file:
            checkpoint = yaml.safe_load(config_file)
        parameters = checkpoint["pipeline"]["params"]
        if parameters.get("clustering") != "AgglomerativeClustering":
            raise RuntimeError("既有 3.1 模型相容設定僅支援 AgglomerativeClustering。")
        parameters.update(plda=DisabledPLDA(), legacy=True)

    pipeline = Pipeline.from_pretrained(checkpoint, token=hf_token)
    if pipeline is None:
        raise RuntimeError("無法載入語者分離模型，請確認 HF_TOKEN 與模型存取權限。")
    return pipeline


def main() -> None:
    # 參數：<file_path> <output_json> [device]
    # 機密（HF_TOKEN）與模型名稱（DIARIZATION_MODEL）一律由父程序以環境變數注入
    if len(sys.argv) < 3:
        usage = "[Diarization Worker] Usage: python diarization_worker.py"
        print(f"{usage} <file_path> <output_json> [device]")
        sys.exit(1)

    file_path = sys.argv[1]
    output_json = sys.argv[2]
    device = sys.argv[3] if len(sys.argv) > 3 else "cuda"

    hf_token = os.environ.get("HF_TOKEN", "")
    model_name = os.environ.get("DIARIZATION_MODEL", "pyannote/speaker-diarization-3.1")

    print(f"[Diarization Worker] Loading pipeline {model_name} on device '{device}'...")
    pipeline = load_pipeline(model_name, hf_token)

    if device == "cuda" and torch.cuda.is_available():
        pipeline.to(torch.device("cuda"))
        print("[Diarization Worker] Successfully moved pipeline to CUDA GPU.")
    else:
        print("[Diarization Worker] Running pipeline on CPU.")

    print(f"[Diarization Worker] Analyzing audio: {file_path}")
    # 以既有 soundfile 解碼，避免引入平台相依的 TorchCodec / FFmpeg DLL 需求。
    samples, sample_rate = soundfile.read(file_path, dtype="float32", always_2d=True)
    waveform = torch.from_numpy(samples.T.copy())
    result = pipeline({"waveform": waveform, "sample_rate": sample_rate})
    diarization = getattr(result, "speaker_diarization", result)

    turns = []
    for turn, _, speaker in diarization.itertracks(yield_label=True):
        turns.append(
            {
                "start": turn.start,
                "end": turn.end,
                "speaker": speaker,
            }
        )

    with open(output_json, "w", encoding="utf-8") as json_file:
        json.dump(turns, json_file, ensure_ascii=False, indent=2)

    print(f"[Diarization Worker] Successfully exported {len(turns)} turns to {output_json}")


if __name__ == "__main__":
    main()
