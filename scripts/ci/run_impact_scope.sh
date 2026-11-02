#!/usr/bin/env bash
# ==============================================================================
# 雙平台共通 CI 變更影響範圍判定入口 (run_impact_scope.sh)
# 職責：
#   1. 於 python:3.11-slim 容器內確保 git 可用。
#   2. 正規化 GitHub Actions / GitLab CI 的事件與比較基準 SHA。
#   3. 呼叫 scripts/ci/detect_ci_impact.py 輸出結構化摘要、JSON 報告與環境變數檔。
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

if ! command -v git >/dev/null 2>&1; then
  echo "[run_impact_scope] 容器內未偵測到 git，正在安裝 git..."
  apt-get update && apt-get install -y --no-install-recommends git ca-certificates >/dev/null
fi

IMPACT_JSON_OUT="${IMPACT_JSON_OUT:-.ci-artifacts/impact_report.json}"
IMPACT_ENV_OUT="${IMPACT_ENV_OUT:-.ci-artifacts/impact.env}"
mkdir -p "$(dirname "${IMPACT_JSON_OUT}")" "$(dirname "${IMPACT_ENV_OUT}")"
rm -f "${IMPACT_ENV_OUT}"

EVENT_KIND="${CI_EVENT_KIND:-}"
BASE_SHA="${CI_BASE_SHA:-}"
HEAD_SHA="${CI_HEAD_SHA:-}"

if [ -z "${EVENT_KIND}" ]; then
  if [ -n "${GITHUB_ACTIONS:-}" ]; then
    HEAD_SHA="${HEAD_SHA:-${GITHUB_SHA:-}}"
    case "${GITHUB_EVENT_NAME:-}" in
      pull_request)
        EVENT_KIND="pull_request"
        BASE_SHA="${BASE_SHA:-${GH_PR_BASE_SHA:-}}"
        HEAD_SHA="${GH_PR_HEAD_SHA:-${HEAD_SHA}}"
        ;;
      push)
        if [[ "${GITHUB_REF:-}" == refs/tags/v* ]]; then
          EVENT_KIND="tag"
          BASE_SHA=""
        else
          EVENT_KIND="push"
          BASE_SHA="${BASE_SHA:-${GH_PUSH_BEFORE_SHA:-}}"
        fi
        ;;
      workflow_dispatch|schedule)
        EVENT_KIND="manual"
        BASE_SHA=""
        ;;
      *)
        EVENT_KIND="${GITHUB_EVENT_NAME:-unknown}"
        ;;
    esac
  elif [ -n "${GITLAB_CI:-}" ]; then
    HEAD_SHA="${HEAD_SHA:-${CI_COMMIT_SHA:-}}"
    if [ -n "${CI_COMMIT_TAG:-}" ]; then
      EVENT_KIND="tag"
      BASE_SHA=""
    elif [ "${CI_PIPELINE_SOURCE:-}" = "merge_request_event" ]; then
      EVENT_KIND="merge_request"
      BASE_SHA="${BASE_SHA:-${CI_MERGE_REQUEST_DIFF_BASE_SHA:-${CI_MERGE_REQUEST_TARGET_BRANCH_SHA:-}}}"
      if [ -z "${BASE_SHA}" ] && [ -n "${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:-}" ]; then
        git -c safe.directory=* fetch origin "${CI_MERGE_REQUEST_TARGET_BRANCH_NAME}" --depth=100 >/dev/null 2>&1 || true
        BASE_SHA="$(git -c safe.directory=* rev-parse "origin/${CI_MERGE_REQUEST_TARGET_BRANCH_NAME}" 2>/dev/null || true)"
      fi
    elif [ "${CI_PIPELINE_SOURCE:-}" = "push" ]; then
      EVENT_KIND="push"
      BASE_SHA="${BASE_SHA:-${CI_COMMIT_BEFORE_SHA:-}}"
    elif [ "${CI_PIPELINE_SOURCE:-}" = "web" ] || [ "${CI_PIPELINE_SOURCE:-}" = "schedule" ] || [ "${CI_PIPELINE_SOURCE:-}" = "api" ]; then
      EVENT_KIND="manual"
      BASE_SHA=""
    else
      EVENT_KIND="${CI_PIPELINE_SOURCE:-unknown}"
    fi
  else
    EVENT_KIND="manual"
  fi
fi

python3 scripts/ci/detect_ci_impact.py \
  --event-kind "${EVENT_KIND}" \
  --base-sha "${BASE_SHA}" \
  --head-sha "${HEAD_SHA}" \
  --repo-dir "${REPO_ROOT}" \
  --output-json "${IMPACT_JSON_OUT}" \
  --output-env "${IMPACT_ENV_OUT}"

if [ -n "${GITHUB_OUTPUT:-}" ] && [ -f "${IMPACT_ENV_OUT}" ]; then
  cat "${IMPACT_ENV_OUT}" >> "${GITHUB_OUTPUT}"
fi
