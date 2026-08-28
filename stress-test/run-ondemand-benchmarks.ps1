param(
    [object]$ConcurrencyLevels = @(50, 200, 500),
    [int]$Duration = 10,
    [string]$ReportDir = "target\k6-reports"
)

$ErrorActionPreference = "Continue"

# 安全解析併發階梯陣列
$vusList = @()
if ($ConcurrencyLevels -is [string]) {
    $vusList = @($ConcurrencyLevels.Split(', ') | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
} elseif ($ConcurrencyLevels -is [System.Collections.IEnumerable]) {
    $vusList = @($ConcurrencyLevels | ForEach-Object { [int]$_ })
} else {
    $vusList = @([int]$ConcurrencyLevels)
}
if ($vusList.Count -eq 0) { $vusList = @(50, 200, 500) }

# 自動讀取專案根目錄 .env 中的 Java 21 設定
$envFilePath = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFilePath) {
    Get-Content $envFilePath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split('=', 2)
            if ($parts.Count -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                if ($key -ieq "java-now-home" -or $key -ieq "JAVA_NOW_HOME") {
                    $env:JAVA_HOME = $val
                }
            }
        }
    }
}

# 確保環境變數中 k6 與 Java 21 可用
if ($env:JAVA_HOME) {
    $env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
} else {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}
$javaCmd = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

$targetReportDir = if ([System.IO.Path]::IsPathRooted($ReportDir)) { $ReportDir } else { Join-Path (Join-Path $PSScriptRoot "..") $ReportDir }
if (-not (Test-Path $targetReportDir)) {
    New-Item -ItemType Directory -Path $targetReportDir -Force | Out-Null
}

$k6Dir = Join-Path $PSScriptRoot "k6"
$durationStr = "${Duration}s"
$baseUrl = "http://localhost:8000"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "     微服務隨選生命週期壓力測試 (On-Demand Benchmark Runner)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  併發階梯     : $($vusList -join ', ') VUs" -ForegroundColor Magenta
Write-Host "  單輪測試時長 : $durationStr" -ForegroundColor Magenta
Write-Host "  目標閘道位址 : $baseUrl" -ForegroundColor Magenta
Write-Host "  執行原則     : 依序啟動單一微服務 -> 壓測 -> 立即終止釋放資源" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan

# 輔助函式：等待端口就緒
function Wait-ForPort {
    param([int]$Port, [int]$TimeoutSec = 45)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($null -ne $conn) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

# 輔助函式：安全終止行程
function Stop-JavaProcess {
    param($ProcessObj, [string]$Name)
    if ($null -ne $ProcessObj) {
        try {
            Write-Host "  [釋放資源] 正在終止 $Name (PID: $($ProcessObj.Id))..." -ForegroundColor Gray
            Stop-Process -Id $ProcessObj.Id -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $ProcessObj.Id -Timeout 5 -ErrorAction SilentlyContinue
        } catch {}
    }
}

# 輔助函式：從 k6 JSON metrics 安全取得指標數值 (兼容量測值 flat 與 nested 結構)
function Get-MetricValue($metricsObj, [string]$metricName, [string]$field) {
    if ($null -eq $metricsObj) { return $null }
    $m = $metricsObj.$metricName
    if ($null -eq $m) { return $null }
    if ($m.PSObject.Properties[$field]) {
        return $m.$field
    }
    if ($m.values -and $m.values.PSObject.Properties[$field]) {
        return $m.values.$field
    }
    return $null
}

# 1. 啟動 Gateway (Port 8000)
Write-Host "`n[1/6] 啟動 API Gateway (Port 8000)..." -ForegroundColor Green
$gwJar = Join-Path $PSScriptRoot "..\backend-gateway\target\backend-gateway-0.0.1-SNAPSHOT.jar"
$gwProcess = Start-Process -FilePath $javaCmd -ArgumentList "-Xms256m", "-Xmx384m", "-jar", "`"$gwJar`"" -PassThru -WindowStyle Hidden

if (-not (Wait-ForPort -Port 8000 -TimeoutSec 40)) {
    Write-Host "  [錯誤] Gateway 啟動超時！" -ForegroundColor Red
    Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway"
    exit 1
}
Write-Host "  -> Gateway 已就緒於 Port 8000" -ForegroundColor Green

# 2. 啟動 IAM Service (Port 8002) 作為認證與權限驗證中心
Write-Host "`n[2/6] 啟動 IAM 服務 (Port 8002，提供全域認證與權限驗證)..." -ForegroundColor Green
$iamJar = Join-Path $PSScriptRoot "..\backend-iam-service\target\backend-iam-service-0.0.1-SNAPSHOT.jar"
$iamProcess = Start-Process -FilePath $javaCmd -ArgumentList "-Xms256m", "-Xmx512m", "-jar", "`"$iamJar`"" -PassThru -WindowStyle Hidden

if (-not (Wait-ForPort -Port 8002 -TimeoutSec 45)) {
    Write-Host "  [錯誤] IAM 服務啟動超時！" -ForegroundColor Red
    Stop-JavaProcess -ProcessObj $iamProcess -Name "IAM"
    Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway"
    exit 1
}
Write-Host "  -> IAM 服務已就緒於 Port 8002" -ForegroundColor Green

$allResults = @()
$authToken = ""

# 直接向 IAM 服務進行 superuser 建立與登入以取得全域 JWT
try {
    & curl.exe -s -X POST "http://localhost:8002/auth/superuser" -H "Content-Type: application/json" -d '{\"key\":\"super_secret_key_change_in_production\",\"email\":\"admin@tsmc.com\"}' | Out-Null
    $rawLogin = & curl.exe -s -X POST "http://localhost:8002/auth/login" -H "Content-Type: application/json" -d '{\"email\":\"admin@tsmc.com\",\"password\":\"admin\"}'
    if ($rawLogin) {
        $loginJson = $rawLogin | ConvertFrom-Json
        if ($loginJson.data -and $loginJson.data.accessToken) {
            $authToken = $loginJson.data.accessToken
        } elseif ($loginJson.accessToken) {
            $authToken = $loginJson.accessToken
        }
    }
} catch {}

if ($authToken) {
    Write-Host "  -> 成功獲取全域 JWT 認證憑證！" -ForegroundColor Cyan
} else {
    Write-Host "  [警告] 預登入失敗，將由 k6 腳本自動嘗試。" -ForegroundColor DarkGray
}

# 等候 Gateway 與 Nacos 路由快取同步 (5秒)
Start-Sleep -Seconds 5

# 壓測 IAM 服務
Write-Host "`n  [壓測] 開始執行 IAM 認證授權服務階梯壓測..." -ForegroundColor Yellow
$iamScript = Join-Path $k6Dir "test-iam.js"
foreach ($vus in $vusList) {
    $summaryFile = Join-Path $targetReportDir "summary-iam-$vus-vus.json"
    Write-Host "  -> IAM 併發 [$vus VUs], 持續 [$durationStr]..." -ForegroundColor White

    $envArgs = @(
        "run",
        "-e", "BASE_URL=$baseUrl",
        "-e", "VUS=$vus",
        "-e", "DURATION=$durationStr",
        "-e", "AUTH_TOKEN=$authToken",
        "-e", "WITH_CACHE=true",
        "-e", "VIRTUAL_THREADS=true",
        "--summary-trend-stats=min,avg,med,max,p(90),p(95),p(99)",
        "--summary-export=$summaryFile",
        $iamScript
    )

    & k6 $envArgs | Out-Null

    if (Test-Path $summaryFile) {
        try {
            $json = Get-Content $summaryFile -Raw | ConvertFrom-Json
            $metrics = $json.metrics

            $rpsVal = Get-MetricValue $metrics "http_reqs" "rate"
            $rps = if ($rpsVal) { [math]::Round([double]$rpsVal, 1) } else { 0.0 }

            $p50Val = Get-MetricValue $metrics "http_req_duration" "p(50)"
            if ($null -eq $p50Val) { $p50Val = Get-MetricValue $metrics "http_req_duration" "med" }
            $p50 = if ($p50Val) { [math]::Round([double]$p50Val, 2) } else { 0.0 }

            $p90Val = Get-MetricValue $metrics "http_req_duration" "p(90)"
            $p90 = if ($p90Val) { [math]::Round([double]$p90Val, 2) } else { 0.0 }

            $p95Val = Get-MetricValue $metrics "http_req_duration" "p(95)"
            $p95 = if ($p95Val) { [math]::Round([double]$p95Val, 2) } else { 0.0 }

            $p99Val = Get-MetricValue $metrics "http_req_duration" "p(99)"
            $p99 = if ($p99Val) { [math]::Round([double]$p99Val, 2) } else { 0.0 }

            $avgVal = Get-MetricValue $metrics "http_req_duration" "avg"
            $avg = if ($avgVal) { [math]::Round([double]$avgVal, 2) } else { 0.0 }

            $failVal = Get-MetricValue $metrics "http_req_failed" "value"
            if ($null -eq $failVal) { $failVal = Get-MetricValue $metrics "http_req_failed" "rate" }
            $failRate = if ($failVal) { [math]::Round([double]$failVal * 100, 2) } else { 0.0 }

            $totalReqsVal = Get-MetricValue $metrics "http_reqs" "count"
            $totalReqs = if ($totalReqsVal) { [int]$totalReqsVal } else { 0 }

            $allResults += [PSCustomObject]@{
                Service       = "IAM 認證授權"
                VUs           = $vus
                RPS           = $rps
                P50_ms        = $p50
                P90_ms        = $p90
                P95_ms        = $p95
                P99_ms        = $p99
                Avg_ms        = $avg
                ErrorRate_pct = "$failRate%"
                TotalRequests = $totalReqs
            }
            Write-Host "     [結果] RPS: $rps req/s | P50: $p50 ms | P95: $p95 ms | P99: $p99 ms | 錯誤率: $failRate%" -ForegroundColor Green
        } catch {
            Write-Host "     [錯誤] 解析 JSON 報告失敗: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# 壓測 IAM 服務完畢後，立即終止 IAM 服務釋放執行緒與記憶體
Stop-JavaProcess -ProcessObj $iamProcess -Name "IAM 認證授權服務"
Start-Sleep -Seconds 2

# 3. 定義其餘隨選受測服務清單
$domainServices = @(
    @{ Name = "competency"; Port = 8004; Display = "Competency 職能專案"; Jar = "..\backend-competency-service\target\backend-competency-service-0.0.1-SNAPSHOT.jar"; Script = "test-competency.js" },
    @{ Name = "job"; Port = 8006; Display = "Job 職缺企業"; Jar = "..\backend-job-service\target\backend-job-service-0.0.1-SNAPSHOT.jar"; Script = "test-job.js" },
    @{ Name = "alert"; Port = 8008; Display = "Alert 告警感測"; Jar = "..\backend-alert-service\target\backend-alert-service-0.0.1-SNAPSHOT.jar"; Script = "test-alert.js" },
    @{ Name = "external"; Port = 8007; Display = "External 外部整合"; Jar = "..\backend-external-api-service\target\backend-external-api-service-0.0.1-SNAPSHOT.jar"; Script = "test-external.js" }
)

$step = 2
foreach ($svc in $domainServices) {
    $step++
    $svcName = $svc.Name
    $svcDisplay = $svc.Display
    $svcPort = $svc.Port
    $jarPath = Join-Path $PSScriptRoot $svc.Jar
    $scriptPath = Join-Path $k6Dir $svc.Script

    Write-Host "`n[$step/6] 隨選啟動服務: $svcDisplay (Port: $svcPort)..." -ForegroundColor Yellow
    $svcProcess = Start-Process -FilePath $javaCmd -ArgumentList "-Xms256m", "-Xmx512m", "-jar", "`"$jarPath`"" -PassThru -WindowStyle Hidden

    if (-not (Wait-ForPort -Port $svcPort -TimeoutSec 45)) {
        Write-Host "  [警告] $svcDisplay 啟動超時，跳過此服務測試。" -ForegroundColor Red
        Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay
        continue
    }

    # 等候 5 秒確保 Nacos 註冊與 Gateway 路由更新完成
    Start-Sleep -Seconds 5
    Write-Host "  -> $svcDisplay 啟動成功並已完成註冊！" -ForegroundColor Green

    # 執行 50, 200, 500 VUs 階梯壓測
    foreach ($vus in $vusList) {
        $summaryFile = Join-Path $targetReportDir "summary-$svcName-$vus-vus.json"
        Write-Host "  -> 正在執行階梯壓測: 併發 [$vus VUs], 持續 [$durationStr]..." -ForegroundColor White

        $envArgs = @(
            "run",
            "-e", "BASE_URL=$baseUrl",
            "-e", "VUS=$vus",
            "-e", "DURATION=$durationStr",
            "-e", "AUTH_TOKEN=$authToken",
            "-e", "WITH_CACHE=true",
            "-e", "VIRTUAL_THREADS=true",
            "--summary-trend-stats=min,avg,med,max,p(90),p(95),p(99)",
            "--summary-export=$summaryFile",
            $scriptPath
        )

        & k6 $envArgs | Out-Null

        # 解析報告
        if (Test-Path $summaryFile) {
            try {
                $json = Get-Content $summaryFile -Raw | ConvertFrom-Json
                $metrics = $json.metrics

                $rpsVal = Get-MetricValue $metrics "http_reqs" "rate"
                $rps = if ($rpsVal) { [math]::Round([double]$rpsVal, 1) } else { 0.0 }

                $p50Val = Get-MetricValue $metrics "http_req_duration" "p(50)"
                if ($null -eq $p50Val) { $p50Val = Get-MetricValue $metrics "http_req_duration" "med" }
                $p50 = if ($p50Val) { [math]::Round([double]$p50Val, 2) } else { 0.0 }

                $p90Val = Get-MetricValue $metrics "http_req_duration" "p(90)"
                $p90 = if ($p90Val) { [math]::Round([double]$p90Val, 2) } else { 0.0 }

                $p95Val = Get-MetricValue $metrics "http_req_duration" "p(95)"
                $p95 = if ($p95Val) { [math]::Round([double]$p95Val, 2) } else { 0.0 }

                $p99Val = Get-MetricValue $metrics "http_req_duration" "p(99)"
                $p99 = if ($p99Val) { [math]::Round([double]$p99Val, 2) } else { 0.0 }

                $avgVal = Get-MetricValue $metrics "http_req_duration" "avg"
                $avg = if ($avgVal) { [math]::Round([double]$avgVal, 2) } else { 0.0 }

                $failVal = Get-MetricValue $metrics "http_req_failed" "value"
                if ($null -eq $failVal) { $failVal = Get-MetricValue $metrics "http_req_failed" "rate" }
                $failRate = if ($failVal) { [math]::Round([double]$failVal * 100, 2) } else { 0.0 }

                $totalReqsVal = Get-MetricValue $metrics "http_reqs" "count"
                $totalReqs = if ($totalReqsVal) { [int]$totalReqsVal } else { 0 }

                $record = [PSCustomObject]@{
                    Service       = $svcDisplay
                    VUs           = $vus
                    RPS           = $rps
                    P50_ms        = $p50
                    P90_ms        = $p90
                    P95_ms        = $p95
                    P99_ms        = $p99
                    Avg_ms        = $avg
                    ErrorRate_pct = "$failRate%"
                    TotalRequests = $totalReqs
                }
                $allResults += $record

                Write-Host "     [結果] RPS: $rps req/s | P50: $p50 ms | P95: $p95 ms | P99: $p99 ms | 錯誤率: $failRate%" -ForegroundColor Green
            } catch {
                Write-Host "     [錯誤] 解析 JSON 報告失敗: $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    }

    # 立即終止當前微服務，釋放執行緒與記憶體
    Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay
    Start-Sleep -Seconds 2
}

# 關閉 Gateway 釋放所有測試資源
Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway"

# 輸出彙總報表
Write-Host "`n=========================================================================================================================" -ForegroundColor Cyan
Write-Host "                                   微服務隨選生命週期壓力測試基準結果彙總" -ForegroundColor Cyan
Write-Host "=========================================================================================================================" -ForegroundColor Cyan

if ($allResults.Count -gt 0) {
    $allResults | Format-Table -Property Service, VUs, RPS, P50_ms, P90_ms, P95_ms, P99_ms, Avg_ms, ErrorRate_pct, TotalRequests -AutoSize

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $mdReportPath = Join-Path $targetReportDir "ondemand-benchmark-$timestamp.md"
    $csvReportPath = Join-Path $targetReportDir "ondemand-benchmark-$timestamp.csv"

    $mdContent = @()
    $mdContent += "# 微服務隨選生命週期壓力測試基準報告"
    $mdContent += ""
    $mdContent += "- **測試時間**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $mdContent += "- **測試模式**: 單一服務隨選啟動 / 壓測後即刻銷毀 (On-Demand Zero-Waste Lifecycle)"
    $mdContent += "- **執行緒架構**: Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`)"
    $mdContent += "- **併發階梯**: $($vusList -join ', ') VUs"
    $mdContent += ""
    $mdContent += "| 微服務模組 | 併發 (VUs) | RPS (req/s) | P50 延遲 (ms) | P90 延遲 (ms) | P95 延遲 (ms) | P99 延遲 (ms) | 平均延遲 (ms) | 錯誤率 | 總請求數 |"
    $mdContent += "|:---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|"

    foreach ($r in $allResults) {
        $mdContent += "| $($r.Service) | $($r.VUs) | $($r.RPS) | $($r.P50_ms) | $($r.P90_ms) | $($r.P95_ms) | $($r.P99_ms) | $($r.Avg_ms) | $($r.ErrorRate_pct) | $($r.TotalRequests) |"
    }

    $mdContent += ""
    $mdContent += "---"
    $mdContent += "*(本報告由 stress-test/run-ondemand-benchmarks.ps1 自動化生成)*"

    $mdContent | Out-File -FilePath $mdReportPath -Encoding utf8
    $allResults | Export-Csv -Path $csvReportPath -NoTypeInformation -Encoding utf8

    Write-Host "=========================================================================================================================" -ForegroundColor Cyan
    Write-Host "  Markdown 報告已保存至: $mdReportPath" -ForegroundColor Green
    Write-Host "  CSV 報告已保存至     : $csvReportPath" -ForegroundColor Green
    Write-Host "=========================================================================================================================" -ForegroundColor Cyan
}
