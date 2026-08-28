param(
    [object]$ConcurrencyLevels = @(50, 200, 500),
    [ValidateSet("all", "iam", "competency", "job", "alert", "external", "suite")]
    [string]$Service = "all",
    [int]$Duration = 30,
    [ValidateSet("true", "false", "both")]
    [string]$CacheMode = "true",
    [ValidateSet("VirtualThreads", "PlatformThreads", "both")]
    [string]$ThreadModel = "VirtualThreads",
    [string]$Server = "localhost",
    [int]$Port = 8000,
    [string]$K6Bin = "",
    [string]$ReportDir = "target\k6-reports"
)

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

# 嘗試由 .env 讀取 K6_BIN
if ([string]::IsNullOrWhiteSpace($K6Bin)) {
    $envPath = Join-Path $PSScriptRoot "..\.env"
    if (Test-Path $envPath) {
        $envLines = Get-Content $envPath
        foreach ($line in $envLines) {
            if ($line -match '^\s*K6_BIN\s*=\s*(.*)$') {
                $K6Bin = $Matches[1].Trim(" '`"")
                break
            }
        }
    }
}

# 若未指定則預設使用環境變數內的 k6
if ([string]::IsNullOrWhiteSpace($K6Bin)) {
    $K6Bin = "k6"
}

# 檢查 k6 執行檔是否存在
$k6Exists = $false
try {
    $cmd = Get-Command $K6Bin -ErrorAction SilentlyContinue
    if ($null -ne $cmd) {
        $k6Exists = $true
    }
} catch {
    $k6Exists = $false
}

if (-not $k6Exists) {
    Write-Host "=================================================================" -ForegroundColor Red
    Write-Host "  [錯誤] 找不到 k6 執行檔 ($K6Bin)" -ForegroundColor Red
    Write-Host "-----------------------------------------------------------------" -ForegroundColor Yellow
    Write-Host "請選擇下列任一種方式安裝 Grafana k6:" -ForegroundColor Yellow
    Write-Host "  1. Windows (winget): winget install k6 --source winget" -ForegroundColor White
    Write-Host "  2. Windows (choco) : choco install k6" -ForegroundColor White
    Write-Host "  3. macOS (brew)    : brew install k6" -ForegroundColor White
    Write-Host "  4. Linux           : sudo apt install k6" -ForegroundColor White
    Write-Host "  5. 或在 .env 指定  : K6_BIN=C:\path\to\k6.exe" -ForegroundColor White
    Write-Host "=================================================================" -ForegroundColor Red
    exit 1
}

# 建立輸出目錄
if (-not (Test-Path $ReportDir)) {
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
}

$k6Dir = Join-Path $PSScriptRoot "k6"
$durationStr = "$Duration" + "s"
$baseUrlStr = "http://" + $Server + ":" + $Port

# 解析微服務清單
$serviceList = @()
if ($Service -eq "all") {
    $serviceList = @("iam", "competency", "job", "alert", "external", "suite")
} else {
    $serviceList = @($Service)
}

# 解析快取選項
$cacheOptions = @()
if ($CacheMode -eq "both") {
    $cacheOptions = @($true, $false)
} elseif ($CacheMode -eq "true") {
    $cacheOptions = @($true)
} else {
    $cacheOptions = @($false)
}

# 解析執行緒模型選項
$threadOptions = @()
if ($ThreadModel -eq "both") {
    $threadOptions = @("VirtualThreads", "PlatformThreads")
} else {
    $threadOptions = @($ThreadModel)
}

$totalRuns = $serviceList.Count * $vusList.Count * $cacheOptions.Count * $threadOptions.Count
$currentRun = 0
$results = @()

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "      微服務完整壓力測試矩陣 (Master Benchmark Runner)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  微服務清單   : $($serviceList -join ', ')" -ForegroundColor Magenta
Write-Host "  併發階梯     : $($vusList -join ', ') VUs" -ForegroundColor Magenta
Write-Host "  快取模式     : $CacheMode" -ForegroundColor Magenta
Write-Host "  執行緒架構   : $ThreadModel" -ForegroundColor Magenta
Write-Host "  單輪持續時間 : $durationStr" -ForegroundColor Magenta
Write-Host "  預計執行總輪數: $totalRuns 輪" -ForegroundColor Magenta
Write-Host "  目標閘道位址 : $baseUrlStr" -ForegroundColor Magenta
Write-Host "=================================================================" -ForegroundColor Cyan

foreach ($tm in $threadOptions) {
    $isVirtual = if ($tm -eq "VirtualThreads") { "true" } else { "false" }

    foreach ($withCache in $cacheOptions) {
        $cacheLabel = if ($withCache) { "有快取" } else { "無快取" }
        $cacheTag = if ($withCache) { "with-cache" } else { "without-cache" }
        $isCacheStr = if ($withCache) { "true" } else { "false" }

        foreach ($vus in $vusList) {
            foreach ($svc in $serviceList) {
                $currentRun++
                $scriptFile = if ($svc -eq "suite") { "test-suite.js" } else { "test-$svc.js" }
                $scriptPath = Join-Path $k6Dir $scriptFile
                $summaryFile = Join-Path $ReportDir "summary-$svc-$cacheTag-$tm-$vus-vus.json"

                Write-Host ""
                Write-Host "[$currentRun/$totalRuns] 正在執行: 服務=[$svc], 併發=[$vus VUs], 快取=[$cacheLabel], 執行緒=[$tm]..." -ForegroundColor Yellow

                $envArgs = @(
                    "run",
                    "-e", "BASE_URL=$baseUrlStr",
                    "-e", "VUS=$vus",
                    "-e", "DURATION=$durationStr",
                    "-e", "WITH_CACHE=$isCacheStr",
                    "-e", "VIRTUAL_THREADS=$isVirtual",
                    "--summary-export=$summaryFile",
                    $scriptPath
                )

                & $K6Bin $envArgs

                # 解析 k6 產生的 summary JSON
                if (Test-Path $summaryFile) {
                    try {
                        $jsonContent = Get-Content $summaryFile -Raw | ConvertFrom-Json
                        $metrics = $jsonContent.metrics

                        $rps = 0.0
                        if ($metrics.http_reqs -and $metrics.http_reqs.values -and $metrics.http_reqs.values.rate) {
                            $rps = [math]::Round([double]$metrics.http_reqs.values.rate, 1)
                        }

                        $p50 = 0.0
                        $p90 = 0.0
                        $p95 = 0.0
                        $p99 = 0.0
                        $avg = 0.0

                        if ($metrics.http_req_duration -and $metrics.http_req_duration.values) {
                            $vals = $metrics.http_req_duration.values
                            $p50 = if ($vals."p(50)") { [math]::Round([double]$vals."p(50)", 2) } elseif ($vals.med) { [math]::Round([double]$vals.med, 2) } else { 0.0 }
                            $p90 = if ($vals."p(90)") { [math]::Round([double]$vals."p(90)", 2) } else { 0.0 }
                            $p95 = if ($vals."p(95)") { [math]::Round([double]$vals."p(95)", 2) } else { 0.0 }
                            $p99 = if ($vals."p(99)") { [math]::Round([double]$vals."p(99)", 2) } else { 0.0 }
                            $avg = if ($vals.avg) { [math]::Round([double]$vals.avg, 2) } else { 0.0 }
                        }

                        $failRate = 0.0
                        if ($metrics.http_req_failed -and $metrics.http_req_failed.values -and $metrics.http_req_failed.values.rate) {
                            $failRate = [math]::Round([double]$metrics.http_req_failed.values.rate * 100, 2)
                        }

                        $totalReqs = 0
                        if ($metrics.http_reqs -and $metrics.http_reqs.values -and $metrics.http_reqs.values.count) {
                            $totalReqs = [int]$metrics.http_reqs.values.count
                        }

                        $serviceDisplayName = switch ($svc) {
                            "iam" { "IAM 認證授權" }
                            "competency" { "Competency 職能專案" }
                            "job" { "Job 職缺企業" }
                            "alert" { "Alert 告警感測" }
                            "external" { "External 外部整合" }
                            "suite" { "Gateway 全鏈路" }
                            default { $svc }
                        }

                        $record = [PSCustomObject]@{
                            Service       = $serviceDisplayName
                            VUs           = $vus
                            CacheMode     = $cacheLabel
                            ThreadModel   = $tm
                            RPS           = $rps
                            P50_ms        = $p50
                            P90_ms        = $p90
                            P95_ms        = $p95
                            P99_ms        = $p99
                            Avg_ms        = $avg
                            ErrorRate_pct = "$failRate%"
                            TotalRequests = $totalReqs
                        }
                        $results += $record

                        Write-Host "  -> 完成: RPS=$rps req/s, P50=$p50 ms, P95=$p95 ms, P99=$p99 ms, 錯誤率=$failRate%" -ForegroundColor Green
                    } catch {
                        Write-Host "  -> 解析報告 JSON 失敗 ($summaryFile): $($_.Exception.Message)" -ForegroundColor Red
                    }
                } else {
                    Write-Host "  -> 警告: 未找到輸出報告 $summaryFile" -ForegroundColor DarkYellow
                }
            }
        }
    }
}

# =============================================================================
# 輸出終端機 Markdown 矩陣報告
# =============================================================================
Write-Host ""
Write-Host "=========================================================================================================================" -ForegroundColor Cyan
Write-Host "                                         微服務壓力測試綜合基準報告" -ForegroundColor Cyan
Write-Host "=========================================================================================================================" -ForegroundColor Cyan

if ($results.Count -gt 0) {
    $results | Format-Table -Property Service, VUs, CacheMode, ThreadModel, RPS, P50_ms, P90_ms, P95_ms, P99_ms, ErrorRate_pct, TotalRequests -AutoSize

    # 輸出 Markdown 與 CSV 報告檔案
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $mdReportPath = Join-Path $ReportDir "benchmark-summary-$timestamp.md"
    $csvReportPath = Join-Path $ReportDir "benchmark-summary-$timestamp.csv"

    $mdContent = @()
    $mdContent += "# 微服務壓力測試綜合基準報告"
    $mdContent += ""
    $mdContent += "- **測試時間**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $mdContent += "- **併發階梯**: $($vusList -join ', ') VUs"
    $mdContent += "- **單輪時長**: $durationStr"
    $mdContent += "- **閘道位址**: $baseUrlStr"
    $mdContent += ""
    $mdContent += "| 微服務模組 | 併發 (VUs) | 快取狀態 | 執行緒模型 | RPS (req/s) | P50 (ms) | P90 (ms) | P95 (ms) | P99 (ms) | 平均延遲 (ms) | 錯誤率 | 總請求數 |"
    $mdContent += "|:---|:---:|:---:|:---|---:|---:|---:|---:|---:|---:|---:|---:|"

    foreach ($r in $results) {
        $mdContent += "| $($r.Service) | $($r.VUs) | $($r.CacheMode) | $($r.ThreadModel) | $($r.RPS) | $($r.P50_ms) | $($r.P90_ms) | $($r.P95_ms) | $($r.P99_ms) | $($r.Avg_ms) | $($r.ErrorRate_pct) | $($r.TotalRequests) |"
    }

    $mdContent += ""
    $mdContent += "---"
    $mdContent += "*(本報告由 stress-test/run-all-benchmarks.ps1 自動化生成)*"

    $mdContent | Out-File -FilePath $mdReportPath -Encoding utf8
    $results | Export-Csv -Path $csvReportPath -NoTypeInformation -Encoding utf8

    Write-Host "=========================================================================================================================" -ForegroundColor Cyan
    Write-Host "  Markdown 報告已輸出至: $mdReportPath" -ForegroundColor Green
    Write-Host "  CSV 報告已輸出至     : $csvReportPath" -ForegroundColor Green
    Write-Host "=========================================================================================================================" -ForegroundColor Cyan
} else {
    Write-Host "  [提示] 本次未蒐集到測試數據，請檢查微服務與閘道是否已啟動。" -ForegroundColor Yellow
}
