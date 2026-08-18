import json
import logging
import os
import subprocess

from config import settings

logger = logging.getLogger(__name__)


def _resolve_conda_python(env_name: str) -> str:
    """透過 conda env list --json 動態解析指定環境的 python.exe 路徑"""
    try:
        res = subprocess.run(
            ["conda", "env", "list", "--json"],
            capture_output=True,
            text=True,
            check=True,
            timeout=60,
        )
        data = json.loads(res.stdout)
        for env_path in data.get("envs", []):
            if os.path.basename(env_path.rstrip("\\/")) == env_name:
                candidate = os.path.join(env_path, "python.exe")
                if os.path.exists(candidate):
                    return candidate
    except Exception as e:
        logger.warning("[STT] conda env 解析失敗: %s", e)
    return ""


def _get_gpu_vram_gb() -> float:
    """透過 nvidia-smi 查詢 GPU 總視訊記憶體，主進程全程不載入 PyTorch"""
    try:
        res = subprocess.run(
            ["nvidia-smi", "--query-gpu=memory.total", "--format=csv,noheader,nounits"],
            capture_output=True,
            text=True,
            check=True,
            timeout=30,
        )
        vram_mb = float(res.stdout.strip().split("\n")[0])
        return vram_mb / 1024.0
    except Exception as exc:
        logger.warning("[STT] 查詢 GPU VRAM 失敗: %s", exc)
        return 0.0


def _detect_diarization_device() -> str:
    """動態分析 GPU VRAM 並決定語者分離子進程的設備"""
    strategy = settings.stt_compute_strategy.lower()
    if strategy == "cpu":
        return "cpu"
    total_vram_gb = _get_gpu_vram_gb()
    if total_vram_gb <= 0:
        logger.info("[STT] 無法偵測 GPU VRAM，語者分離改用 CPU。")
        return "cpu"
    logger.info("[STT] Detected GPU total VRAM: %.2f GB", total_vram_gb)
    if strategy == "full_gpu" or (strategy == "auto" and total_vram_gb >= 8.0):
        logger.info("[STT] 語者分離策略：使用 CUDA GPU")
        return "cuda"
    logger.info("[STT] 語者分離策略：使用 CPU")
    return "cpu"


def _run_diarization(pyannote_python: str, audio_path: str, output_json: str, device: str) -> list:
    """以獨立語者分離環境 (pyannote-env) 子進程執行聲紋分離，回傳語者時間區間"""
    worker_script = os.path.join(os.path.dirname(__file__), "diarization_worker.py")
    cmd = [
        pyannote_python,
        "-u",
        worker_script,
        audio_path,
        output_json,
        device,
    ]
    env = os.environ.copy()
    env["PYTHONIOENCODING"] = "utf-8"
    # 機密 Token 與模型名稱一律透過環境變數注入子進程，避免出現於命令列
    env["HF_TOKEN"] = settings.hf_token
    env["DIARIZATION_MODEL"] = settings.diarization_model
    try:
        res = subprocess.run(cmd, env=env, capture_output=True, text=True, encoding="utf-8")
        if res.stdout:
            logger.debug("[STT] Diarization stdout: %s", res.stdout)
        if res.returncode != 0 and res.stderr:
            logger.warning("[STT] Diarization stderr (tail): %s", res.stderr[-500:])
    except Exception as e:
        logger.error("[STT] Diarization 子進程執行失敗: %s", e)
        return []

    if os.path.exists(output_json) and os.path.getsize(output_json) > 0:
        try:
            with open(output_json, encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            logger.warning("[STT] Diarization 結果解析失敗: %s", e)
    return []
