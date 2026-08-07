import json
import os
import sys

import huggingface_hub
import torch
from pyannote.audio import Pipeline

# 避免 Windows 下 OpenMP 重複載入衝突
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

# HuggingFace 參數相容層：huggingface_hub 新版本已移除 use_auth_token，
# pyannote.audio 3.1.1 內部仍以舊參數呼叫 hf_hub_download，於此統一轉為 token。
try:
    _orig_download = huggingface_hub.hf_hub_download

    def _patched_download(*args, **kwargs):
        if "use_auth_token" in kwargs:
            kwargs["token"] = kwargs.pop("use_auth_token")
        return _orig_download(*args, **kwargs)

    huggingface_hub.hf_hub_download = _patched_download
    if hasattr(huggingface_hub, "file_download"):
        huggingface_hub.file_download.hf_hub_download = _patched_download
except Exception:
    pass


def main():
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
    pipeline = Pipeline.from_pretrained(model_name, use_auth_token=hf_token)

    if device == "cuda" and torch.cuda.is_available():
        pipeline.to(torch.device("cuda"))
        print("[Diarization Worker] Successfully moved pipeline to CUDA GPU.")
    else:
        print("[Diarization Worker] Running pipeline on CPU.")

    print(f"[Diarization Worker] Analyzing audio: {file_path}")
    diarization = pipeline(file_path)

    turns = []
    for turn, _, speaker in diarization.itertracks(yield_label=True):
        turns.append(
            {
                "start": turn.start,
                "end": turn.end,
                "speaker": speaker,
            }
        )

    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(turns, f, ensure_ascii=False, indent=2)

    print(f"[Diarization Worker] Successfully exported {len(turns)} turns to {output_json}")


if __name__ == "__main__":
    main()
