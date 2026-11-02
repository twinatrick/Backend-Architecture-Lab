#!/usr/bin/env bash
# ==============================================================================
# 雙平台共通 CI 驗證入口：Python 驗證 (verify_python.sh)
# 職責：
#   1. 若影響範圍判定為 Not Applicable (RUN_PYTHON=false)，輸出略過報告並結束。
#   2. 安裝鎖定依賴清單 (backend-ai-py/requirements-ci.txt)。
#   3. 執行共通 CI 影響判定工具自身之單元測試。
#   4. 執行 Ruff Lint 與 Format 檢查。
#   5. 執行 backend-ai-py pytest 並產出 JUnit XML 報告。
#   6. 執行非空測試集守衛 (Non-Empty Test Suite Guard)。
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

JUNIT_OUT="${REPO_ROOT}/backend-ai-py/test-results/junit.xml"
mkdir -p "$(dirname "${JUNIT_OUT}")"

if [ "${RUN_PYTHON:-true}" = "false" ]; then
  echo "[verify_python] 變更影響範圍判定為 Not Applicable，略過 Python 驗證。"
  python3 scripts/ci/assert_junit_non_empty.py write-na \
    --output "${JUNIT_OUT}" \
    --suite-name "python-ci" \
    --reason "${STATUS_PYTHON_REASON:-變更未涉及 Python 服務、測試或依賴 (Not Applicable)}"
  exit 0
fi

if ! command -v git >/dev/null 2>&1; then
  echo "[verify_python] 容器內未偵測到 git，正在安裝 git..."
  apt-get update && apt-get install -y --no-install-recommends git ca-certificates >/dev/null
fi

echo "=== [1/4] 安裝共通鎖定 Python CI 依賴 (backend-ai-py/requirements-ci.txt) ==="
python3 -m pip install --upgrade pip
python3 -m pip install -r backend-ai-py/requirements-ci.txt

echo "=== [2/4] 驗證共通 CI 影響範圍判定與守衛邏輯單元測試 ==="
python3 -m unittest discover -s scripts/ci -p "test_*.py" -v

echo "=== [3/4] 執行 Ruff Lint 與 Format 檢查 ==="
ruff check backend-ai-py/ scripts/ci/
ruff format --check backend-ai-py/ scripts/ci/

echo "=== [4/4] 執行 backend-ai-py 單元測試並產出 JUnit XML ==="
(
  cd backend-ai-py
  pytest -v --junitxml="test-results/junit.xml"
)

python3 scripts/ci/assert_junit_non_empty.py check-junit \
  --suite-label "python-ci" \
  --root-dir "${REPO_ROOT}" \
  --pattern "backend-ai-py/test-results/junit.xml"
