#!/usr/bin/env bash
# ==============================================================================
# 雙平台共通 CI 驗證入口：Java 主驗證與覆蓋率門檻 (verify_java_unit.sh)
# 職責：
#   1. 若影響範圍判定為 Not Applicable (RUN_JAVA_UNIT=false)，輸出略過報告並結束。
#   2. 執行 Maven 單元測試與 JaCoCo 覆蓋率門檻檢查 (單一 fork 循序執行)。
#   3. 執行非空測試集守衛，確認各子模組產出有效且非空之 Surefire JUnit XML。
#   4. 若環境具備 SONAR_TOKEN，執行條件式 SonarQube 品質掃描。
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

NA_REPORT_DIR="${REPO_ROOT}/target/surefire-reports"

write_na_xml() {
  local out_file="$1"
  local suite_name="$2"
  local reason="$3"
  mkdir -p "$(dirname "${out_file}")"
  if command -v python3 >/dev/null 2>&1; then
    python3 scripts/ci/assert_junit_non_empty.py write-na \
      --output "${out_file}" \
      --suite-name "${suite_name}" \
      --reason "${reason}"
  else
    cat > "${out_file}" <<EOF
<?xml version='1.0' encoding='utf-8'?>
<testsuite name="${suite_name}" tests="1" failures="0" errors="0" skipped="1">
  <testcase classname="${suite_name}" name="impact_scoped_not_applicable">
    <skipped message="${reason}" />
  </testcase>
</testsuite>
EOF
  fi
}

check_junit_reports() {
  local suite_label="$1"
  local report_dir_name="$2"
  if command -v python3 >/dev/null 2>&1; then
    python3 scripts/ci/assert_junit_non_empty.py check-junit \
      --suite-label "${suite_label}" \
      --root-dir "${REPO_ROOT}" \
      --pattern "**/${report_dir_name}/TEST-*.xml"
    return 0
  fi

  mapfile -t xml_files < <(find "${REPO_ROOT}" -path "*/${report_dir_name}/TEST-*.xml" -type f | sort)
  if [ "${#xml_files[@]}" -eq 0 ]; then
    echo "[${suite_label}] 找不到任何 JUnit XML 測試報告 (${report_dir_name}/TEST-*.xml)" >&2
    exit 1
  fi

  awk -v label="${suite_label}" '
    /<testsuite / {
      if (match($0, /tests="[0-9]+"/)) {
        val = substr($0, RSTART + 7, RLENGTH - 8) + 0
        total_tests += val
      }
      if (match($0, /failures="[0-9]+"/)) {
        val = substr($0, RSTART + 10, RLENGTH - 11) + 0
        total_failures += val
      }
      if (match($0, /errors="[0-9]+"/)) {
        val = substr($0, RSTART + 8, RLENGTH - 9) + 0
        total_errors += val
      }
      if (match($0, /skipped="[0-9]+"/)) {
        val = substr($0, RSTART + 9, RLENGTH - 10) + 0
        total_skipped += val
      }
    }
    END {
      executed = total_tests - total_skipped
      if (executed <= 0) {
        printf "[%s] 非空測試集守衛攔截：實際執行測試數為 0 (total=%d, skipped=%d)\n", label, total_tests, total_skipped > "/dev/stderr"
        exit 1
      }
      if (total_failures > 0 || total_errors > 0) {
        printf "[%s] 測試報告含失敗或錯誤案例 (failures=%d, errors=%d)\n", label, total_failures, total_errors > "/dev/stderr"
        exit 1
      }
      printf "[%s] 非空測試集守衛通過 (executed=%d, total=%d, skipped=%d)\n", label, executed, total_tests, total_skipped
    }
  ' "${xml_files[@]}"
}

if [ "${RUN_JAVA_UNIT:-true}" = "false" ]; then
  echo "[verify_java_unit] 變更影響範圍判定為 Not Applicable，略過 Java 單元測試與覆蓋率驗證。"
  write_na_xml \
    "${NA_REPORT_DIR}/TEST-not-applicable.xml" \
    "java-unit-ci" \
    "${STATUS_JAVA_UNIT_REASON:-變更未涉及 Java 模組、POM 或容器建置設定 (Not Applicable)}"
  exit 0
fi

chmod +x mvnw
echo "=== [1/3] 執行 Maven 單元測試與 JaCoCo 覆蓋率檢查 ==="
./mvnw -B test jacoco:check -DforkCount=1

echo "=== [2/3] 執行非空測試集守衛 (Surefire JUnit XML) ==="
check_junit_reports "java-unit-ci" "surefire-reports"

echo "=== [3/3] 條件式 SonarQube 分析 ==="
if [ -n "${SONAR_TOKEN:-}" ]; then
  sonar_host="${SONAR_HOST_URL:-https://sonarcloud.io}"
  sonar_cmd=(
    ./mvnw -B sonar:sonar
    "-Dsonar.host.url=${sonar_host}"
    "-Dsonar.token=${SONAR_TOKEN}"
    "-Dsonar.login=${SONAR_TOKEN}"
  )
  if [ -n "${SONAR_ORGANIZATION:-}" ]; then
    sonar_cmd+=("-Dsonar.organization=${SONAR_ORGANIZATION}")
  fi
  if [ -n "${SONAR_PROJECT_KEY:-}" ]; then
    sonar_cmd+=("-Dsonar.projectKey=${SONAR_PROJECT_KEY}")
  fi
  "${sonar_cmd[@]}"
else
  echo "SONAR_TOKEN 未設定，略過 SonarQube 掃描。"
fi
