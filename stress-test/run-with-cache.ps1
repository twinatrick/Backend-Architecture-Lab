param(
    [string]$JmeterBin = "",
    [string]$Server = "localhost",
    [int]$Port = 8000,
    [int]$Threads = 500,
    [int]$AlertThreads = 200,
    [int]$Duration = 60,
    [string]$ResultCsv = "",
    [string]$ThreadModel = "VirtualThreads" # VirtualThreads or PlatformThreads
)

# 若未指定 CSV 檔名，自動根據執行緒模型與併發量產生對應名稱
if ([string]::IsNullOrWhiteSpace($ResultCsv)) {
    $ResultCsv = "stress-test-result-with-cache-${ThreadModel}-${Threads}threads.csv"
}

# 嘗試由 .env 讀取 JMETER_BIN
if ([string]::IsNullOrWhiteSpace($JmeterBin)) {
    $envPath = Join-Path $PSScriptRoot "..\.env"
    if (Test-Path $envPath) {
        $envLines = Get-Content $envPath
        foreach ($line in $envLines) {
            if ($line -match '^\s*JMETER_BIN\s*=\s*(.*)$') {
                $JmeterBin = $Matches[1].Trim(" '`"")
                break
            }
        }
    }
}

# 若還是為空，預設使用環境變數內的 jmeter
if ([string]::IsNullOrWhiteSpace($JmeterBin)) {
    $JmeterBin = "jmeter"
}

$RawDir = "target\jmeter-raw-cache"
$ReportDir = "target\jmeter-report-cache"
if (Test-Path $RawDir) {
    Remove-Item -Path "$RawDir\*" -Force -Recurse -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $RawDir -Force | Out-Null

$actualAlertThreads = [math]::Min($AlertThreads, $Threads)

$scripts = @(
    @{Name="iam"; File="stress-test\test-iam.jmx"; Threads=$Threads},
    @{Name="project-skill"; File="stress-test\test-project-skill.jmx"; Threads=$Threads},
    @{Name="job"; File="stress-test\test-job.jmx"; Threads=$Threads},
    @{Name="alert"; File="stress-test\test-alert.jmx"; Threads=$actualAlertThreads}
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Round 1: 有快取模式壓力測試" -ForegroundColor Cyan
Write-Host "  執行緒架構: $ThreadModel" -ForegroundColor Magenta
Write-Host "  壓測併發量: $Threads 併發 (Alert: $actualAlertThreads)" -ForegroundColor Magenta
Write-Host "  目標輸出檔: $ResultCsv" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

foreach ($s in $scripts) {
    $rawFile = "$RawDir\raw-$($s.Name).csv"
    Write-Host "[$($s.Name)] 開始測試..." -ForegroundColor Yellow
    $jmeterArgs = @(
        "-n",
        "-t", $s.File,
        "-l", $rawFile,
        "-JTHREADS=$($s.Threads)",
        "-JDURATION=$Duration",
        "-JRAMP_UP=30",
        "-JSERVER=$Server",
        "-JPORT=$Port",
        "-JADMIN_EMAIL=admin@tsmc.com",
        "-JADMIN_PASSWORD=password",
        "-Jjmeter.save.saveservice.output_format=csv",
        "-Jjmeter.save.saveservice.response_data=false",
        "-j", "$RawDir\jmeter-$($s.Name).log"
    )
    & $JmeterBin $jmeterArgs
    Write-Host "[$($s.Name)] 完成，原始結果: $rawFile" -ForegroundColor Green
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  彙總測試結果" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$allResults = @()
foreach ($s in $scripts) {
    $rawFile = "$RawDir\raw-$($s.Name).csv"
    if (Test-Path $rawFile) {
        Import-Csv $rawFile | Group-Object label | ForEach-Object {
            $label = $_.Name
            $samples = $_.Group
            $count = $samples.Count
            $elapsed = $samples | ForEach-Object { [double]$_.elapsed }
            $errors = ($samples | Where-Object { $_.success -ne "true" }).Count
            $avg = [math]::Round(($elapsed | Measure-Object -Average).Average, 1)
            $min = [math]::Round(($elapsed | Measure-Object -Minimum).Minimum, 1)
            $max = [math]::Round(($elapsed | Measure-Object -Maximum).Maximum, 1)
            $errPct = [math]::Round(($errors / $count) * 100, 2)
            $sorted = $elapsed | Sort-Object
            $p50 = [math]::Round($sorted[[math]::Floor($count * 0.50)], 1)
            $p90 = [math]::Round($sorted[[math]::Floor($count * 0.90)], 1)
            $p95 = [math]::Round($sorted[[math]::Floor($count * 0.95)], 1)
            $p99 = [math]::Round($sorted[[math]::Floor($count * 0.99)], 1)
            $throughput = [math]::Round($count / $Duration, 2)

            $allResults += [PSCustomObject]@{
                Service = $s.Name
                Endpoint = $label
                Samples = $count
                Average = $avg
                Min = $min
                Max = $max
                ErrorPct = $errPct
                Throughput = $throughput
                P50 = $p50
                P90 = $p90
                P95 = $p95
                P99 = $p99
            }
        }
    }
}

$allResults | Sort-Object Service, Endpoint | Export-Csv -Path $ResultCsv -NoTypeInformation -Encoding UTF8

Write-Host "結果已儲存至: $ResultCsv" -ForegroundColor Green

# ===== 自動分析結論 =====
$conclusions = @()
$conclusions += "# 壓力測試結論（有快取模式 - $ThreadModel）"
$conclusions += "# 測試時間: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$conclusions += "# 測試參數: ${Threads} 併發 (IAM/Project-Skill/Job), ${actualAlertThreads} (Alert), ${Duration}秒持續"
$conclusions += "# 執行緒模型: $ThreadModel"
$conclusions += ""

$totalSamples = ($allResults | Measure-Object Samples -Sum).Sum
$overallAvg = [math]::Round(($allResults | Measure-Object Average -Average).Average, 1)
$maxError = ($allResults | Measure-Object ErrorPct -Maximum).Maximum
$totalThroughput = [math]::Round(($allResults | Measure-Object Throughput -Sum).Sum, 1)

$conclusions += "【整體概覽】"
$conclusions += "總請求數: $totalSamples"
$conclusions += "整體平均延遲: ${overallAvg}ms"
$conclusions += "總吞吐量: ${totalThroughput} req/s"
$conclusions += "最大錯誤率: ${maxError}%"
$conclusions += ""

$conclusions += "【服務分組分析】"
foreach ($s in $scripts) {
    $svcResults = $allResults | Where-Object Service -eq $s.Name
    $svcSamples = ($svcResults | Measure-Object Samples -Sum).Sum
    $svcAvg = [math]::Round(($svcResults | Measure-Object Average -Average).Average, 1)
    $svcThru = [math]::Round(($svcResults | Measure-Object Throughput -Sum).Sum, 1)
    $conclusions += "  $($s.Name): $svcSamples 請求, 平均 ${svcAvg}ms, ${svcThru} req/s"
}

$conclusions += ""
$conclusions += "【關鍵發現】"
$conclusions += "1. 有 Redis 快取輔助下，讀取密集型端點 (company/get, skill/get 等) 延遲應明顯低於寫入端點"
$conclusions += "2. 99%ile 延遲若超過 2000ms 表示有尾端延遲問題"
$conclusions += "3. 錯誤率應接近 0%，若有錯誤需檢查服務日誌"

$conclusions | Out-File -FilePath $ResultCsv -Append -Encoding UTF8
Write-Host "結論已附加至: $ResultCsv" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  有快取壓力測試完成" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
