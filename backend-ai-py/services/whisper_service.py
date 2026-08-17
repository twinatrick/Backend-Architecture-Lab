import ctypes
import importlib.util
import logging
import os
import sys
from typing import Any

from faster_whisper import WhisperModel

from config import settings
from services.transcript_formatter import transcript_formatter
from utils.mock_detection import _is_mock

logger = logging.getLogger(__name__)

_whisper_model = None


def _register_whisper_dll_dirs() -> None:
    """動態註冊 Whisper/ctranslate2 相依 CUDA 原生 DLL 的搜尋目錄。

    使用 importlib 探索 nvidia.* 與 ctranslate2 套件的真實安裝位置，
    掃描其底下所有含 .dll 目錄並登入 os.add_dll_directory，完全不寫死路徑。
    僅在 Windows 且非測試環境時生效。
    """
    if os.name != "nt" or sys.platform != "win32":
        return
    if os.environ.get("KMP_DUPLICATE_LIB_OK") != "TRUE":
        os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

    candidates = []
    for pkg_name in ("ctranslate2", "nvidia", "onnxruntime"):
        try:
            spec = importlib.util.find_spec(pkg_name)
        except (ImportError, ValueError):
            spec = None
        if spec is not None and spec.submodule_search_locations:
            for loc in spec.submodule_search_locations:
                candidates.append(loc)

    extra_dirs = set()
    for base in candidates:
        if not base or not os.path.isdir(base):
            continue
        for root, dirs, files in os.walk(base):
            depth = root[len(base) :].count(os.sep) if root.startswith(base) else 0
            if any(f.lower().endswith(".dll") for f in files):
                extra_dirs.add(root)
            if depth >= 2:
                dirs[:] = []

    registered = []
    for d in sorted(extra_dirs):
        try:
            os.add_dll_directory(d)
            registered.append(d)
        except (OSError, ValueError) as exc:
            # 單一目錄註冊失敗不影響其他目錄，僅記錄
            logger.warning("[Whisper] DLL 目錄註冊失敗 %s: %s", d, exc)

    # 預先載入 CUDA runtime 相關 DLL 進進程，避免 ctranslate2 延遲載入失敗
    if registered:
        preload_names = (
            "cudart64_12.dll",
            "cublas64_12.dll",
            "cublasLt64_12.dll",
            "nvcuda.dll",
            "cudnn64_9.dll",
            "cudnn_adv64_9.dll",
            "cudnn_cnn64_9.dll",
        )
        for name in preload_names:
            try:
                ctypes.WinDLL(name)
            except OSError as exc:
                # 單一 CUDA 動態程式庫未安裝屬正常情況，記錄後繼續嘗試其餘 DLL
                logger.warning("[Whisper] 預載 CUDA DLL 失敗 %s: %s", name, exc)


def _get_whisper_model() -> Any:
    """延遲載入 faster-whisper 模型並快取"""
    global _whisper_model
    if _whisper_model is None:
        _register_whisper_dll_dirs()

        if _is_mock(WhisperModel):
            raise ImportError("faster_whisper is mocked in testing environment")

        _whisper_model = WhisperModel(
            model_size_or_path=settings.whisper_model_size,
            device=settings.whisper_device,
            compute_type=settings.whisper_compute_type,
        )
    return _whisper_model


def _transcribe_whisper(segment_list: list, diarization_result: list) -> str:
    """將 Whisper 主程序轉譯結果依語者分離資料排版，或輸出純文字/時間戳記"""
    if diarization_result:
        return transcript_formatter.format_diarized(segment_list, diarization_result)
    return transcript_formatter.format_plain(segment_list)
