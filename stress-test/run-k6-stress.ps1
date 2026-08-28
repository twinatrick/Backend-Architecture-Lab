param(
    [string]$K6Bin = "",
    [string]$Server = "localhost",
    [int]$Port = 8000,
    [int]$VUs = 50,
    [int]$Duration = 30,
    [ValidateSet("suite", "iam", "competency", "job", "alert", "external", "all")]
    [string]$Scenario = "suite",
    [bool]$WithCache = $true,
    [ValidateSet("VirtualThreads", "PlatformThreads")]
    [string]$ThreadModel = "VirtualThreads",
    [string]$ReportDir = "target\k6-reports"
)

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

# 若還是為空，預設使用環境變數內的 k6
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
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host "  找不到 k6 執行檔 ($K6Bin)" -ForegroundColor Red
    Write-Host "------------------------------------------" -ForegroundColor Yellow
    Write-Host "請選擇下列任一種方式安裝 Grafana k6:" -ForegroundColor Yellow
    Write-Host "  1. Windows (winget): winget install k6 --source winget" -ForegroundColor White
    Write-Host "  2. Windows (choco) : choco install k6" -ForegroundColor White
    Write-Host "  3. macOS (brew)    : brew install k6" -ForegroundColor White
    Write-Host "  4. Linux           : sudo apt install k6" -ForegroundColor White
    Write-Host "  5. 或在 .env 指定  : K6_BIN=C:\path\to\k6.exe" -ForegroundColor White
    Write-Host "==========================================" -ForegroundColor Red
    exit 1
}

# 建立輸出目錄
if (-not (Test-Path $ReportDir)) {
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
}

$cacheLabel = if ($WithCache) { "有快取" } else { "無快取" }
$cacheTag = if ($WithCache) { "with-cache" } else { "without-cache" }
$durationStr = "$Duration" + "s"
$baseUrlStr = "http://" + $Server + ":" + $Port

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Grafana k6 微服務壓力測試" -ForegroundColor Cyan
Write-Host "  執行緒架構: $ThreadModel" -ForegroundColor Magenta
Write-Host "  快取狀態  : $cacheLabel" -ForegroundColor Magenta
Write-Host "  測試併發量: $VUs VUs" -ForegroundColor Magenta
Write-Host "  持續時間  : $durationStr" -ForegroundColor Magenta
Write-Host "  目標主機  : $baseUrlStr" -ForegroundColor Magenta
Write-Host "==========================================" -ForegroundColor Cyan

# 確定要執行的腳本清單
$scriptsToRun = @()
$k6Dir = Join-Path $PSScriptRoot "k6"

if ($Scenario -eq "all") {
    $scriptsToRun += @{ Name = "iam"; File = (Join-Path $k6Dir "test-iam.js") }
    $scriptsToRun += @{ Name = "competency"; File = (Join-Path $k6Dir "test-competency.js") }
    $scriptsToRun += @{ Name = "job"; File = (Join-Path $k6Dir "test-job.js") }
    $scriptsToRun += @{ Name = "alert"; File = (Join-Path $k6Dir "test-alert.js") }
    $scriptsToRun += @{ Name = "external"; File = (Join-Path $k6Dir "test-external.js") }
} elseif ($Scenario -eq "suite") {
    $scriptsToRun += @{ Name = "suite"; File = (Join-Path $k6Dir "test-suite.js") }
} else {
    $scriptsToRun += @{ Name = $Scenario; File = (Join-Path $k6Dir "test-$Scenario.js") }
}

foreach ($item in $scriptsToRun) {
    $scriptName = $item.Name
    $scriptPath = $item.File
    $summaryFile = Join-Path $ReportDir "summary-$scriptName-$cacheTag-$ThreadModel-$VUs-vus.json"

    Write-Host "[$scriptName] 開始測試 ($VUs VUs, $durationStr)..." -ForegroundColor Yellow

    $isVirtual = if ($ThreadModel -eq "VirtualThreads") { "true" } else { "false" }
    $isCacheStr = if ($WithCache) { "true" } else { "false" }

    $envArgs = @(
        "run",
        "-e", "BASE_URL=$baseUrlStr",
        "-e", "VUS=$VUs",
        "-e", "DURATION=$durationStr",
        "-e", "WITH_CACHE=$isCacheStr",
        "-e", "VIRTUAL_THREADS=$isVirtual",
        "--summary-export=$summaryFile",
        $scriptPath
    )

    & $K6Bin $envArgs
    
    if (Test-Path $summaryFile) {
        Write-Host "[$scriptName] 完成，摘要報告: $summaryFile" -ForegroundColor Green
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  所有 k6 壓測執行完畢！報告於: $ReportDir" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
