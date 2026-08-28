param(
    [object]$ConcurrencyLevels = @(50, 200, 500),
    [int]$Duration = 10,
    [string]$ReportDir = "target\k6-reports",
    [string]$Quadrant = "all",
    [string]$Service = "all"
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

# 定義 4 象限測試配置矩陣
$allQuadrants = @(
    @{
        Key           = "D"
        Name          = "象限 D: 有快取 + 虛擬執行緒 (黃金組合)"
        CacheEnabled  = $true
        VirtualEnabled= $true
        CacheTypeArg  = "redis"
        VtArg         = "true"
        CacheLabel    = "有快取 (Redis+Redisson)"
        ThreadLabel   = "虛擬執行緒 (Virtual)"
    },
    @{
        Key           = "B"
        Name          = "象限 B: 無快取 + 虛擬執行緒 (純資料庫高併發 I/O)"
        CacheEnabled  = $false
        VirtualEnabled= $true
        CacheTypeArg  = "none"
        VtArg         = "true"
        CacheLabel    = "無快取 (NoOp/DirectDB)"
        ThreadLabel   = "虛擬執行緒 (Virtual)"
    },
    @{
        Key           = "C"
        Name          = "象限 C: 有快取 + 平台執行緒 (傳統執行緒池快取加速)"
        CacheEnabled  = $true
        VirtualEnabled= $false
        CacheTypeArg  = "redis"
        VtArg         = "false"
        CacheLabel    = "有快取 (Redis+Redisson)"
        ThreadLabel   = "平台執行緒 (Platform)"
    },
    @{
        Key           = "A"
        Name          = "象限 A: 無快取 + 平台執行緒 (基準對照組 / 傳統架構)"
        CacheEnabled  = $false
        VirtualEnabled= $false
        CacheTypeArg  = "none"
        VtArg         = "false"
        CacheLabel    = "無快取 (NoOp/DirectDB)"
        ThreadLabel   = "平台執行緒 (Platform)"
    }
)

# 依使用者輸入篩選執行象限
$selectedQuadrants = @()
switch -Regex ($Quadrant.ToUpper()) {
    "^D$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "D" }) }
    "^B$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "B" }) }
    "^C$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "C" }) }
    "^A$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "A" }) }
    default { $selectedQuadrants = $allQuadrants }
}

# 定義微服務受測清單
$allServices = @(
    @{ Key = "iam"; Name = "iam"; Port = 8002; Display = "IAM 認證授權"; Jar = "..\backend-iam-service\target\backend-iam-service-0.0.1-SNAPSHOT.jar"; Script = "test-iam.js" },
    @{ Key = "competency"; Name = "competency"; Port = 8004; Display = "Competency 職能專案"; Jar = "..\backend-competency-service\target\backend-competency-service-0.0.1-SNAPSHOT.jar"; Script = "test-competency.js" },
    @{ Key = "job"; Name = "job"; Port = 8006; Display = "Job 職缺企業"; Jar = "..\backend-job-service\target\backend-job-service-0.0.1-SNAPSHOT.jar"; Script = "test-job.js" },
    @{ Key = "alert"; Name = "alert"; Port = 8008; Display = "Alert 告警感測"; Jar = "..\backend-alert-service\target\backend-alert-service-0.0.1-SNAPSHOT.jar"; Script = "test-alert.js" },
    @{ Key = "external"; Name = "external"; Port = 8007; Display = "External 外部整合"; Jar = "..\backend-external-api-service\target\backend-external-api-service-0.0.1-SNAPSHOT.jar"; Script = "test-external.js" }
)

$selectedServices = @()
if ($Service -ieq "all") {
    $selectedServices = $allServices
} else {
    $selectedServices = @($allServices | Where-Object { $_.Key -ieq $Service })
    if ($selectedServices.Count -eq 0) { $selectedServices = $allServices }
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  微服務四象限隨選生命週期壓力測試 (4-Quadrant On-Demand Benchmark)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  併發階梯     : $($vusList -join ', ') VUs" -ForegroundColor Magenta
Write-Host "  單輪測試時長 : $durationStr" -ForegroundColor Magenta
Write-Host "  受測象限     : $(($selectedQuadrants | ForEach-Object { $_.Key }) -join ', ')" -ForegroundColor Magenta
Write-Host "  受測服務     : $(($selectedServices | ForEach-Object { $_.Key }) -join ', ')" -ForegroundColor Magenta
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
Write-Host "`n[準備] 啟動 API Gateway (Port 8000)..." -ForegroundColor Green
$gwJar = Join-Path $PSScriptRoot "..\backend-gateway\target\backend-gateway-0.0.1-SNAPSHOT.jar"
$gwProcess = Start-Process -FilePath $javaCmd -ArgumentList "-Xms256m", "-Xmx384m", "-jar", "`"$gwJar`"" -PassThru -WindowStyle Hidden

if (-not (Wait-ForPort -Port 8000 -TimeoutSec 40)) {
    Write-Host "  [錯誤] Gateway 啟動超時！" -ForegroundColor Red
    Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway"
    exit 1
}
Write-Host "  -> Gateway 已就緒於 Port 8000" -ForegroundColor Green

# 2. 啟動一次 IAM 以取得全域 Superuser JWT Token
Write-Host "`n[準備] 啟動 IAM 服務生成全域認證憑證 (JWT)..." -ForegroundColor Green
$iamJar = Join-Path $PSScriptRoot "..\backend-iam-service\target\backend-iam-service-0.0.1-SNAPSHOT.jar"
$initIamProcess = Start-Process -FilePath $javaCmd -ArgumentList "-Xms256m", "-Xmx512m", "-jar", "`"$iamJar`"" -PassThru -WindowStyle Hidden

$authToken = ""
if (Wait-ForPort -Port 8002 -TimeoutSec 45) {
    Start-Sleep -Seconds 3
    try {
        $superKey = if ($env:SUPERUSER_KEY) { $env:SUPERUSER_KEY } else { "super_secret_key_change_in_production" }
        $adminEmail = if ($env:ADMIN_EMAIL) { $env:ADMIN_EMAIL } else { "admin@tsmc.com" }
        $adminPass = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "admin" }

        $superBody = @{ key = $superKey; email = $adminEmail } | ConvertTo-Json
        $loginBody = @{ email = $adminEmail; password = $adminPass } | ConvertTo-Json

        try {
            Invoke-RestMethod -Uri "http://localhost:8002/auth/superuser" -Method Post -ContentType "application/json" -Body $superBody -TimeoutSec 10 | Out-Null
        } catch {}

        try {
            $loginRes = Invoke-RestMethod -Uri "http://localhost:8002/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 10
            if ($loginRes) {
                if ($loginRes.data -and $loginRes.data.accessToken) {
                    $authToken = $loginRes.data.accessToken
                } elseif ($loginRes.accessToken) {
                    $authToken = $loginRes.accessToken
                }
            }
        } catch {}
    } catch {}
    Stop-JavaProcess -ProcessObj $initIamProcess -Name "IAM 初始化行程"
} else {
    Write-Host "  [警告] IAM 初始化超時，停止行程" -ForegroundColor DarkGray
    Stop-JavaProcess -ProcessObj $initIamProcess -Name "IAM 初始化行程"
}

if ($authToken) {
    Write-Host "  -> 成功獲取全域 JWT 認證憑證！" -ForegroundColor Cyan
} else {
    Write-Host "  [警告] 預登入失敗，將由 k6 腳本自動嘗試。" -ForegroundColor DarkGray
}

$allResults = @()
$quadrantIndex = 0

foreach ($q in $selectedQuadrants) {
    $quadrantIndex++
    $qKey = $q.Key
    $qName = $q.Name
    $qCacheEnabled = $q.CacheEnabled
    $qVirtualEnabled = $q.VirtualEnabled
    $qCacheType = $q.CacheTypeArg
    $qVtArg = $q.VtArg

    Write-Host "`n#################################################################" -ForegroundColor Yellow
    Write-Host "  [象限 $quadrantIndex/$($selectedQuadrants.Count)] $qName" -ForegroundColor Yellow
    Write-Host "  參數: --spring.cache.type=$qCacheType --spring.threads.virtual.enabled=$qVtArg" -ForegroundColor Yellow
    Write-Host "#################################################################" -ForegroundColor Yellow

    $svcStep = 0
    foreach ($svc in $selectedServices) {
        $svcStep++
        $svcKey = $svc.Key
        $svcName = $svc.Name
        $svcDisplay = $svc.Display
        $svcPort = $svc.Port
        $jarPath = Join-Path $PSScriptRoot $svc.Jar
        $scriptPath = Join-Path $k6Dir $svc.Script

        Write-Host "`n  [$svcStep/$($selectedServices.Count)] 隨選啟動服務: $svcDisplay (Port: $svcPort)..." -ForegroundColor Cyan
        
        $bootArgs = @(
            "-Xms256m",
            "-Xmx512m",
            "-jar",
            "`"$jarPath`"",
            "--spring.cache.type=$qCacheType",
            "--spring.threads.virtual.enabled=$qVtArg"
        )
        $svcProcess = Start-Process -FilePath $javaCmd -ArgumentList $bootArgs -PassThru -WindowStyle Hidden

        if (-not (Wait-ForPort -Port $svcPort -TimeoutSec 45)) {
            Write-Host "    [警告] $svcDisplay 啟動超時，跳過此服務測試。" -ForegroundColor Red
            Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay
            continue
        }

        # 等候 5 秒確保 Nacos 註冊與 Gateway 路由更新完成
        Start-Sleep -Seconds 5
        Write-Host "    -> $svcDisplay 啟動成功並已完成註冊！" -ForegroundColor Green

        # 執行 50, 200, 500 VUs 階梯壓測
        foreach ($vus in $vusList) {
            $summaryFile = Join-Path $targetReportDir "summary-$qKey-$svcName-$vus-vus.json"
            Write-Host "    -> 執行階梯壓測: 象限 [$qKey] | 服務 [$svcKey] | 併發 [$vus VUs], 持續 [$durationStr]..." -ForegroundColor White

            $envArgs = @(
                "run",
                "-e", "BASE_URL=$baseUrl",
                "-e", "VUS=$vus",
                "-e", "DURATION=$durationStr",
                "-e", "AUTH_TOKEN=$authToken",
                "-e", "WITH_CACHE=$($qCacheEnabled.ToString().ToLower())",
                "-e", "VIRTUAL_THREADS=$($qVirtualEnabled.ToString().ToLower())",
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
                        Quadrant      = $qKey
                        CacheMode     = $q.CacheLabel
                        ThreadModel   = $q.ThreadLabel
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

                    Write-Host "       [結果] RPS: $rps req/s | P50: $p50 ms | P95: $p95 ms | P99: $p99 ms | 錯誤率: $failRate%" -ForegroundColor Green
                } catch {
                    Write-Host "       [錯誤] 解析 JSON 報告失敗: $($_.Exception.Message)" -ForegroundColor Red
                }
            }
        }

        # 立即終止當前微服務，釋放執行緒與記憶體
        Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay
        Start-Sleep -Seconds 2
    }
}

# 關閉 Gateway 釋放所有測試資源
Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway"

# 輸出彙總報表
Write-Host "`n=========================================================================================================================" -ForegroundColor Cyan
Write-Host "                             微服務四象限隨選生命週期壓力測試基準結果彙總" -ForegroundColor Cyan
Write-Host "=========================================================================================================================" -ForegroundColor Cyan

if ($allResults.Count -gt 0) {
    $allResults | Format-Table -Property Quadrant, Service, VUs, RPS, P50_ms, P90_ms, P95_ms, P99_ms, Avg_ms, ErrorRate_pct, TotalRequests -AutoSize

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $mdReportPath = Join-Path $targetReportDir "four-quadrants-benchmark-$timestamp.md"
    $csvReportPath = Join-Path $targetReportDir "four-quadrants-benchmark-$timestamp.csv"

    $mdContent = @()
    $mdContent += "# 微服務四象限壓力測試基準報告 (4-Quadrant Benchmark Matrix)"
    $mdContent += ""
    $mdContent += "- **測試時間**: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $mdContent += "- **測試模式**: 單一服務隨選啟動 / 壓測後即刻銷毀 (On-Demand Zero-Waste Lifecycle)"
    $mdContent += "- **四象限維度**: 快取開/關 (Redis vs None) × 執行緒模型 (Virtual Threads vs Platform Threads)"
    $mdContent += "- **併發階梯**: $($vusList -join ', ') VUs"
    $mdContent += ""
    $mdContent += "| 象限 | 快取配置 | 執行緒架構 | 微服務模組 | 併發 (VUs) | RPS (req/s) | P50 延遲 (ms) | P90 延遲 (ms) | P95 延遲 (ms) | P99 延遲 (ms) | 平均延遲 (ms) | 錯誤率 | 總請求數 |"
    $mdContent += "|:---:|:---|:---|:---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|"

    foreach ($r in $allResults) {
        $mdContent += "| $($r.Quadrant) | $($r.CacheMode) | $($r.ThreadModel) | $($r.Service) | $($r.VUs) | $($r.RPS) | $($r.P50_ms) | $($r.P90_ms) | $($r.P95_ms) | $($r.P99_ms) | $($r.Avg_ms) | $($r.ErrorRate_pct) | $($r.TotalRequests) |"
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
