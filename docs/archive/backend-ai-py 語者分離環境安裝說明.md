# 語者分離環境安裝說明（pyannote-env）

本文件說明「語者分離（Speaker Diarization）」所需的獨立 Python 環境 `pyannote-env`
如何建立與使用。此環境獨立於標準作業環境 `backend-ai-py`，用以徹底避免
PyTorch（pyannote.audio）與 faster-whisper（ctranslate2）在同一程序載入 CUDA 時產生的 DLL / 多執行緒衝突。

## 為何需要獨立環境？

- `backend-ai-py`（標準作業環境）：執行 Whisper 轉譯（faster-whisper / ctranslate2）與 SenseVoice（sherpa-onnx）。
- `pyannote-env`（語者分離專用環境）：執行 pyannote.audio（PyTorch + CUDA）。
- 兩者在同一個 Python 程序內同時初始化 CUDA 引擎會導致 Windows 底層 DLL 衝突崩潰，
  因此透過「子進程隔離」設計：`backend-ai-py` 主程序以 `subprocess` 呼叫
  `pyannote-env` 的 `python.exe` 執行語者分離，完成後再於主程序執行 Whisper 轉譯。

## 需求清單檔案

語者分離環境的需求清單位於專案內：

```
backend-ai-py/requirements_diarization.txt
```

內容鎖定版本如下（請務必遵守，勿隨意升級）：

```
--extra-index-url https://download.pytorch.org/whl/cu126
torch==2.13.0+cu126
torchaudio==2.11.1
pyannote.audio==4.0.7
numpy==2.2.6
soundfile==0.14.0
huggingface_hub==0.36.2
```

> **重要**：此次安全升級需要整組更新，請勿只升級 `torch`。
> `pyannote.audio 3.1.1` 使用新版 torchaudio 已移除的音訊 API，故同步升級至 4.x，
> 並採用其相依套件需要的 NumPy 2.x。`torchaudio 2.11.1` 為 ABI 穩定版本，
> 宣告支援 `torch>=2.11`，版本號不必與 torch 相同。
> Hugging Face 改用原生 `token` 參數，不再安裝 `use_auth_token` monkey patch。
>
> 建議先建立新的 Python 3.11 環境驗證，確認成功後才切換 `diarization_env_name`；
> 不要將這些套件安裝到執行 faster-whisper 的 `backend-ai-py` 主環境。

## 建立環境步驟

### 1. 建立虛擬環境（Python 3.11）

```bash
conda create -n pyannote-env python=3.11 -y
```

### 2. 啟用並安裝需求清單

```bash
conda activate pyannote-env
python -X utf8 -m pip install -r backend-ai-py/requirements_diarization.txt
```

安裝完成後即擁有 CUDA 12.6 版 PyTorch、torchaudio 與 pyannote.audio 4.0.7。
`-X utf8` 可避免 Windows 使用舊版 pip 時，以系統編碼讀取需求清單內的繁體中文註解而失敗。
GPU 執行需要支援 CUDA 12.6 的 NVIDIA 驅動；沒有可用 CUDA 時，worker 仍可使用 CPU。

### 3. 驗證 CUDA 可用

```bash
conda run -n pyannote-env python -c "import torch; print(torch.__version__, torch.cuda.is_available())"
```

預期輸出類似：`2.13.0+cu126 True`。另請執行 `python -m pip check` 檢查相依性。

### 4. 模型與輸出相容性

- 預設模型仍是 `pyannote/speaker-diarization-3.1`，不自動切換模型或要求 community-1 授權。
- worker 保留原模型的 `AgglomerativeClustering` 分群參數並設定 `legacy=True`。
  新版建構子不接受 `plda=None`，因此使用不載入權重的 `DisabledPLDA` 相容物件，
  避免額外下載 community-1 模型；此物件不參與凝聚式分群，誤用時會明確失敗。
- 音訊以 SoundFile 讀取為 float32 波形後傳入 pipeline，避免內建解碼依賴
  TorchCodec 與 FFmpeg 的平台 DLL；主程序原有的 FFmpeg 格式轉換仍保留。
- 同時接受舊的 `Annotation` 與新版 `DiarizeOutput`，輸出仍為 `start`、`end`、`speaker` JSON 陣列。
- 模型 checkpoint 只能使用已信任的來源；不得藉由全域關閉 `weights_only` 安全檢查來載入不可信模型。

## HuggingFace Token 設定（必須）

pyannote 官方語者分離模型（`pyannote/speaker-diarization-3.1`）需要：

1. 登入 HuggingFace，至模型頁面同意並接受 License：
   - https://huggingface.co/pyannote/speaker-diarization-3.1
   - https://huggingface.co/pyannote/segmentation-3.0
2. 建立 Access Token（建議 `read` 權限即可）。
3. 將 Token 寫入 `backend-ai-py/.env`：

```env
HF_TOKEN=hf_xxxxxxxxxxxxxxxxxxxxxxxx
```

> ⚠️ **安全提醒**：`HF_TOKEN` 為機密資訊，`backend-ai-py/.env` 已被 `.gitignore` 排除，
> 請勿將 Token 寫入任何會被 Git 追蹤的檔案。

## 組態設定（config）

`backend-ai-py/config.py` 中語者分離相關設定：

| 欄位 | 預設值 | 說明 |
|---|---|---|
| `hf_token` | (空) | HuggingFace Token，由 `.env` 的 `HF_TOKEN` 讀取 |
| `stt_diarization` | `False` | 是否啟用語者分離 |
| `stt_speaker_names` | `` | 語者顯示名稱 JSON 陣列，例：`["傑太HR/面試官","游先生"]` |
| `stt_dialogue_mode` | `False` | 中文小說體對話框輸出（`某人說：「...」`） |
| `stt_compute_strategy` | `auto` | `cpu` / `auto` / `mixed` / `full_gpu` |
| `diarization_env_name` | `pyannote-env` | 語者分離環境名稱，Python 路徑由 `conda env list --json` 動態解析 |
| `diarization_model` | `pyannote/speaker-diarization-3.1` | 語者分離模型名稱 |

## 使用方式

啟用語者分離後，呼叫既有 `/stt` API 即會在背景自動：

1. 主程序以 FFmpeg 將音訊轉為 16kHz mono WAV（若非 WAV）。
2. 以子進程呼叫 `pyannote-env` 執行 `services/diarization_worker.py`，取得語者時間區間 JSON。
   - 機密 Token 與模型名稱透過環境變數（`HF_TOKEN`、`DIARIZATION_MODEL`）注入子進程，不經過命令列。
3. 主程序載入 faster-whisper（large-v3-turbo / CUDA）進行轉譯。
4. 依時間軸重疊度將語音指派給語者、合併同語者連續段落，並套用對話框排版與台灣繁體 OpenCC 轉換。

也可以用 CLI 快速驗證（`backend-ai-py/transcribe_with_diarization.py`）：

```bash
conda run -n backend-ai-py python backend-ai-py/transcribe_with_diarization.py
```

## 簡單除錯檢查

```bash
# 檢查語者分離環境是否被 conda 正確解析
conda env list
# 檢查環境內是否安裝 torch/pyannote
conda run -n pyannote-env python -c "import importlib.util; print(bool(importlib.util.find_spec('pyannote.audio')))"
```

## backend-ai-py 環境的 CUDA 需求（2026-08-06 補充）

標準作業環境 `backend-ai-py` 要讓 faster-whisper（ctranslate2）在 Windows + CUDA GPU 上正常轉譯，
除了 `environment.yml` 內建套件外，**必須額外安裝以下 pip 套件**（提供 ctranslate2 延遲載入所需的 CUDA runtime DLL）：

```bash
conda run -n backend-ai-py python -m pip install nvidia-cublas-cu12 nvidia-cudnn-cu12 nvidia-cuda-runtime-cu12
```

- `nvidia-cublas-cu12`：提供 `cublas64_12.dll` / `cublasLt64_12.dll`（位於 site-packages\nvidia\cublas\bin）
- `nvidia-cudnn-cu12`：提供 `cudnn64_9.dll` / `cudnn_adv64_9.dll`（位於 site-packages\nvidia\cudnn\bin）
- `nvidia-cuda-runtime-cu12`：提供 `cudart64_12.dll`（位於 site-packages\nvidia\cuda_runtime\bin）

> **為什麼需要？** ctranslate2 在 Windows 上對 cuBLAS/cuDNN 為「延遲載入（delay-load）」，
> Python 3.8+ 不會搜尋 PATH，只搜尋 exe 目錄與 `os.add_dll_directory` 註冊的目錄。
> 若缺 DLL 會報 `RuntimeError: Library cublas64_12.dll is not found or cannot be loaded`。

### DLL 動態註冊機制（零硬編碼）

`backend-ai-py/services/stt_service.py` 的 `_register_whisper_dll_dirs()` 會在載入 Whisper 模型前自動處理：

1. 以 `importlib.util.find_spec` 動態定位 `ctranslate2` / `nvidia` / `onnxruntime` 套件的實際安裝位置。
2. `os.walk`（深度 ≤ 2）掃描所有含 `.dll` 的目錄並逐一 `os.add_dll_directory` 註冊。
3. 以 `ctypes.WinDLL` **預先載入** `cudart64_12.dll`、`cublas64_12.dll`、`cublasLt64_12.dll`、
   `nvcuda.dll`、`cudnn64_9.dll`、`cudnn_adv64_9.dll`（try/except 忽略缺失），
   讓 ctranslate2 的 delay-load 直接命中已載入模組。
4. 設定 `KMP_DUPLICATE_LIB_OK=TRUE` 避免 OpenMP 重複載入衝突。

全程無任何硬編碼路徑，環境遷移（如 conda 環境重建至其他路徑）後依然自動生效。

### numpy 必須使用 pip 版（openblas 後端）

- `environment.yml` 的 pip 區塊已加入 `- numpy`，確保安裝的是 openblas 後端版本。
- **切勿**讓 conda 版 numpy（mkl BLAS）進入環境：`av` 依賴會自動拉入 conda 版 numpy + mkl，
  而 mkl BLAS 在 Windows 執行矩陣運算（如 Whisper 特徵提取）會以 `0xC06D007F` 靜默崩潰。
- 症狀特徵：`import numpy` 與小運算正常，但大矩陣乘法（`np.random.rand(2000,2000) @ ...`）或
  Whisper 轉譯過程中無 traceback 直接退出（exit code -1066598273）。

### 語者分離輸出說明

- 轉譯結果以「小說體對話框」輸出：`某人說：「...」`
- 語者名稱依時間軸重疊度對齊指派；**若 pyannote 分出超過提供的 speaker 名稱數量的 cluster**，
  超出部分保留 `SPEAKER_XX` 原始標籤，可交由後期 AI 或人工重新命名整理。
