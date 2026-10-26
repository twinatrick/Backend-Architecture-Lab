param(
    [object]$ConcurrencyLevels = @(50, 200, 500),
    [int]$Duration = 5,
    [string]$ReportDir = "target\k6-reports",
    [string]$Quadrant = "all",
    [string]$Service = "all",
    [switch]$RefreshDb = $false,
    [switch]$RunProbes = $false,
    [switch]$SkipK6 = $false,
    [switch]$NoUpdateMarkdown = $false
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

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# 1. 自動讀取專案根目錄 .env 中的 Java 21 路徑與認證金鑰（避免載入單體時期遺留的 SERVER_PORT / SPRING_DATASOURCE_URL 與外部 Bot Token 覆蓋壓測配置）
$env:SERVER_PORT = $null
$env:SPRING_DATASOURCE_URL = $null
$env:DISCORD_GF_TOKEN = $null
$env:DISCORD_DIARY_TOKEN = $null
$env:LINE_GF_CHANNEL_ACCESS_TOKEN = $null
$env:LINE_DIARY_CHANNEL_ACCESS_TOKEN = $null
$env:LINE_CHANNEL_ACCESS_TOKEN = $null
$envFilePath = Join-Path $repoRoot ".env"
if (Test-Path $envFilePath) {
    Get-Content $envFilePath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line -split '=', 2
            if ($parts.Count -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                if ($key -ieq "java-now-home" -or $key -ieq "JAVA_NOW_HOME") {
                    $env:JAVA_HOME = $val
                } elseif ($key -in @("JWT_SECRET_USE", "JWT_ISSUER", "JWT_AUDIENCE", "SUPERUSER_KEY")) {
                    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
                }
            }
        }
    }
}

# 2. 優先檢查並讀取 .run 目錄下的執行配置 (VM_PARAMETERS / ACTIVE_PROFILES)
$defaultVmArgs = @("-Xms128m", "-Xmx384m", "-XX:+UseG1GC")
$activeProfiles = "local"
$runDir = Join-Path $repoRoot ".run"
if (Test-Path $runDir) {
    $firstRunXml = Get-ChildItem -Path $runDir -Filter "*.run.xml" | Select-Object -First 1
    if ($null -ne $firstRunXml) {
        try {
            [xml]$xmlDoc = Get-Content $firstRunXml.FullName
            $vmOpt = $xmlDoc.SelectSingleNode("//option[@name='VM_PARAMETERS']")
            if ($null -ne $vmOpt -and $vmOpt.value) {
                $defaultVmArgs = @($vmOpt.value.Split(' ') | Where-Object { $_ -ne "" })
            }
            $profOpt = $xmlDoc.SelectSingleNode("//option[@name='ACTIVE_PROFILES']")
            if ($null -ne $profOpt -and $profOpt.value) {
                $activeProfiles = $profOpt.value
            }
        } catch {}
    }
}

# 確保環境變數中 k6 與 Java 21 可用
if ($env:JAVA_HOME) {
    $env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
} else {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
}
$javaCmd = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

$targetReportDir = if ([System.IO.Path]::IsPathRooted($ReportDir)) { $ReportDir } else { Join-Path $repoRoot $ReportDir }
if (-not (Test-Path $targetReportDir)) {
    New-Item -ItemType Directory -Path $targetReportDir -Force | Out-Null
}

# 3. 選擇性一鍵重刷本機 Docker PostgreSQL 壓測種子資料 (-RefreshDb)
if ($RefreshDb) {
    Write-Host "`n=================================================================" -ForegroundColor Cyan
    Write-Host "  [資料治理] 開始重刷本機 Docker PostgreSQL 四大微服務壓測種子資料" -ForegroundColor Cyan
    Write-Host "=================================================================" -ForegroundColor Cyan

    & docker cp $PSScriptRoot "postgres_db_backend:/" | Out-Null
    $dbs = @("iam_service", "competency_service", "job_service", "alert_service", "external_api_service")
    foreach ($db in $dbs) {
        Write-Host "  -> 確保資料庫 $db 結構完整 (01_create_tables.sql)..." -ForegroundColor Gray
        & docker exec postgres_db_backend psql -U postgres -d $db -f /stress-test/01_create_tables.sql | Out-Null
    }

    if ($Service -ieq "all") {
        Write-Host "  -> 執行全領域種子資料灌入 (run_all.sql)..." -ForegroundColor Yellow
        & docker exec -w /stress-test postgres_db_backend psql -U postgres -d postgres -f /stress-test/run_all.sql
    } else {
        $svcSqlMap = @{
            "iam"        = @{ Db = "iam_service"; File = "run_iam.sql" }
            "competency" = @{ Db = "competency_service"; File = "run_competency.sql" }
            "job"        = @{ Db = "job_service"; File = "run_job.sql" }
            "alert"      = @{ Db = "alert_service"; File = "run_alert.sql" }
        }
        $targetKey = $Service.ToLower()
        if ($svcSqlMap.ContainsKey($targetKey)) {
            $entry = $svcSqlMap[$targetKey]
            Write-Host "  -> 執行單一領域種子資料灌入 ($($entry.File) -> $($entry.Db))..." -ForegroundColor Yellow
            & docker exec -w /stress-test postgres_db_backend psql -U postgres -d $entry.Db -f "/stress-test/$($entry.File)"
        }
    }

    Write-Host "  -> 驗證各微服務資料庫資料筆數 (verify_counts.sql)..." -ForegroundColor Green
    & docker exec -w /stress-test postgres_db_backend psql -U postgres -d postgres -f /stress-test/verify_counts.sql
}

# 4. 選擇性執行定向瓶頸探針 (-RunProbes)
if ($RunProbes) {
    Write-Host "`n=================================================================" -ForegroundColor Cyan
    Write-Host "  [瓶頸探針] 執行定向瓶頸探針測試套件 (Targeted Bottleneck Probes)" -ForegroundColor Cyan
    Write-Host "=================================================================" -ForegroundColor Cyan
    Push-Location $repoRoot
    try {
        & rtk ./mvnw test -pl backend-test-support "-Dtest=TargetedBottleneckProbeIT" "-DfailIfNoTests=false" "-DforkCount=1" "-Dsurefire.useFile=false"
    } finally {
        Pop-Location
    }
}

$k6Dir = Join-Path $PSScriptRoot "k6"
$durationStr = "${Duration}s"
$baseUrl = "http://localhost:8000"

# 定義 4 象限測試配置矩陣
$allQuadrants = @(
    @{
        Key            = "D"
        Name           = "象限 D: 有快取 + 虛擬執行緒 (黃金組合)"
        ShortTableName = "**象限 D (黃金)**"
        CacheIcon      = "🚀 開啟"
        ThreadIcon     = "🚀 虛擬"
        CacheEnabled   = $true
        VirtualEnabled = $true
        CacheTypeArg   = "redis"
        VtArg          = "true"
        CacheLabel     = "有快取 (Redis+Redisson)"
        ThreadLabel    = "虛擬執行緒 (Virtual)"
    },
    @{
        Key            = "B"
        Name           = "象限 B: 無快取 + 虛擬執行緒 (純資料庫高併發 I/O)"
        ShortTableName = "**象限 B (虛擬)**"
        CacheIcon      = "🛑 關閉"
        ThreadIcon     = "🚀 虛擬"
        CacheEnabled   = $false
        VirtualEnabled = $true
        CacheTypeArg   = "none"
        VtArg          = "true"
        CacheLabel     = "無快取 (NoOp/DirectDB)"
        ThreadLabel    = "虛擬執行緒 (Virtual)"
    },
    @{
        Key            = "C"
        Name           = "象限 C: 有快取 + 平台執行緒 (傳統執行緒池快取加速)"
        ShortTableName = "**象限 C (快取)**"
        CacheIcon      = "🚀 開啟"
        ThreadIcon     = "🐢 平臺"
        CacheEnabled   = $true
        VirtualEnabled = $false
        CacheTypeArg   = "redis"
        VtArg          = "false"
        CacheLabel     = "有快取 (Redis+Redisson)"
        ThreadLabel    = "平台執行緒 (Platform)"
    },
    @{
        Key            = "A"
        Name           = "象限 A: 無快取 + 平台執行緒 (基準對照組 / 傳統架構)"
        ShortTableName = "**象限 A (全關)**"
        CacheIcon      = "🛑 關閉"
        ThreadIcon     = "🐢 平臺"
        CacheEnabled   = $false
        VirtualEnabled = $false
        CacheTypeArg   = "none"
        VtArg          = "false"
        CacheLabel     = "無快取 (NoOp/DirectDB)"
        ThreadLabel    = "平台執行緒 (Platform)"
    }
)

# 定義微服務受測清單
$allServices = @(
    @{ Index = 1; Key = "iam"; Name = "iam"; Port = 8002; Display = "IAM 認證授權"; HeaderTitle = "#### (1) IAM 認證授權服務 (Port 8002)"; GoldenCell = "**IAM 認證授權**<br>*(Port 8002)*"; Jar = "..\backend-iam-service\target\backend-iam-service-0.0.1-SNAPSHOT.jar"; Script = "test-iam.js" },
    @{ Index = 2; Key = "competency"; Name = "competency"; Port = 8004; Display = "Competency 職能專案"; HeaderTitle = "#### (2) Competency 職能專案服務 (Port 8004)"; GoldenCell = "**Competency 職能專案**<br>*(Port 8004)*"; Jar = "..\backend-competency-service\target\backend-competency-service-0.0.1-SNAPSHOT.jar"; Script = "test-competency.js" },
    @{ Index = 3; Key = "job"; Name = "job"; Port = 8006; Display = "Job 職缺企業"; HeaderTitle = "#### (3) Job 職缺企業服務 (Port 8006)"; GoldenCell = "**Job 職缺企業**<br>*(Port 8006)*"; Jar = "..\backend-job-service\target\backend-job-service-0.0.1-SNAPSHOT.jar"; Script = "test-job.js" },
    @{ Index = 4; Key = "alert"; Name = "alert"; Port = 8008; Display = "Alert 告警感測"; HeaderTitle = "#### (4) Alert 告警感測服務 (Port 8008)"; GoldenCell = "**Alert 告警感測**<br>*(Port 8008)*"; Jar = "..\backend-alert-service\target\backend-alert-service-0.0.1-SNAPSHOT.jar"; Script = "test-alert.js" },
    @{ Index = 5; Key = "external"; Name = "external"; Port = 8007; Display = "External 外部整合"; HeaderTitle = "#### (5) External API 外部整合服務 (Port 8007)"; GoldenCell = "**External 外部整合**<br>*(Port 8007)*"; Jar = "..\backend-external-api-service\target\backend-external-api-service-0.0.1-SNAPSHOT.jar"; Script = "test-external.js" }
)

# 輔助函式：從 k6 JSON metrics 安全取得指標數值
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

function Parse-SummaryJson([string]$summaryPath, $qObj, $svcObj, [int]$vus) {
    if (-not (Test-Path $summaryPath)) { return $null }
    try {
        $json = Get-Content $summaryPath -Raw | ConvertFrom-Json
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

        return [PSCustomObject]@{
            Quadrant      = $qObj.Key
            CacheMode     = $qObj.CacheLabel
            ThreadModel   = $qObj.ThreadLabel
            ServiceKey    = $svcObj.Key
            Service       = $svcObj.Display
            VUs           = $vus
            RPS           = $rps
            P50_ms        = $p50
            P90_ms        = $p90
            P95_ms        = $p95
            P99_ms        = $p99
            Avg_ms        = $avg
            FailRateNum   = $failRate
            ErrorRate_pct = ("{0:N2}%" -f $failRate)
            TotalRequests = $totalReqs
        }
    } catch {
        return $null
    }
}

if (-not $SkipK6) {
    # 依使用者輸入篩選執行象限
    $selectedQuadrants = @()
    switch -Regex ($Quadrant.ToUpper()) {
        "^D$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "D" }) }
        "^B$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "B" }) }
        "^C$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "C" }) }
        "^A$" { $selectedQuadrants = @($allQuadrants | Where-Object { $_.Key -eq "A" }) }
        default { $selectedQuadrants = $allQuadrants }
    }

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
    Write-Host "  JVM 參數配置 : $($defaultVmArgs -join ' ') (Profile: $activeProfiles)" -ForegroundColor Magenta
    Write-Host "  目標閘道位址 : $baseUrl" -ForegroundColor Magenta
    Write-Host "  執行原則     : 依序啟動單一受測服務 -> 預熱 -> 壓測 -> 立即終止釋放資源" -ForegroundColor Yellow
    Write-Host "=================================================================" -ForegroundColor Cyan

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

    function Wait-ForPortClosed {
        param([int]$Port, [int]$TimeoutSec = 15)
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
            $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
            if ($null -eq $conn) {
                return $true
            }
            Start-Sleep -Milliseconds 300
        }
        return $false
    }

    function Stop-JavaProcess {
        param($ProcessObj, [string]$Name, [int]$Port = 0)
        if ($null -ne $ProcessObj) {
            try {
                if (-not $ProcessObj.HasExited) {
                    Write-Host "  [釋放資源] 正在終止 $Name (PID: $($ProcessObj.Id))..." -ForegroundColor Gray
                    Stop-Process -Id $ProcessObj.Id -Force -ErrorAction SilentlyContinue
                    Wait-Process -Id $ProcessObj.Id -Timeout 5 -ErrorAction SilentlyContinue
                }
            } catch {}
        }
        if ($Port -gt 0) {
            $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
            foreach ($c in $conns) {
                if ($c.OwningProcess -gt 0) {
                    Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
                }
            }
            Wait-ForPortClosed -Port $Port -TimeoutSec 10 | Out-Null
        }
    }

    # 0. 清除 Redis 中可能因預設容量 10,000 溢位而損毀的空布隆過濾器 (bf:*)，確保壓測以 500,000 容量重新初始化
    try {
        & docker exec redis_container sh -c "redis-cli KEYS 'bf:*' | xargs -r redis-cli DEL" | Out-Null
    } catch {}

    # 1. 啟動 Gateway (Port 8000)
    Write-Host "`n[準備] 啟動 API Gateway (Port 8000)..." -ForegroundColor Green
    $gwJar = Join-Path $PSScriptRoot "..\backend-gateway\target\backend-gateway-0.0.1-SNAPSHOT.jar"
    $gwArgs = $defaultVmArgs + @(
        "-jar",
        "`"$gwJar`"",
        "--spring.profiles.active=$activeProfiles",
        "--spring.cloud.nacos.discovery.enabled=false",
        "--spring.cloud.discovery.client.simple.instances.iam-service[0].uri=http://localhost:8002",
        "--spring.cloud.discovery.client.simple.instances.competency-service[0].uri=http://localhost:8004",
        "--spring.cloud.discovery.client.simple.instances.job-service[0].uri=http://localhost:8006",
        "--spring.cloud.discovery.client.simple.instances.external-api-service[0].uri=http://localhost:8007",
        "--spring.cloud.discovery.client.simple.instances.alert-service[0].uri=http://localhost:8008",
        "--spring.cloud.gateway.httpclient.pool.max-connections=1000",
        "--spring.cloud.gateway.httpclient.pool.acquire-timeout=10000",
        "--server.tomcat.accept-count=1000",
        "--server.tomcat.max-connections=10000",
        "--spring.threads.virtual.enabled=true"
    )
    $gwProcess = Start-Process -FilePath $javaCmd -ArgumentList $gwArgs -PassThru -WindowStyle Hidden

    if (-not (Wait-ForPort -Port 8000 -TimeoutSec 45)) {
        Write-Host "  [錯誤] Gateway 啟動超時！" -ForegroundColor Red
        Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway" -Port 8000
        exit 1
    }
    Write-Host "  -> Gateway 已就緒於 Port 8000" -ForegroundColor Green

    # 2. 啟動輔助 IAM 服務以生成全域 Superuser JWT Token，並在測試非 IAM 服務時提供首次權限預熱
    $iamJar = Join-Path $PSScriptRoot "..\backend-iam-service\target\backend-iam-service-0.0.1-SNAPSHOT.jar"
    $auxIamProcess = $null

    function Ensure-AuxIamRunning {
        if ($null -ne $script:auxIamProcess -and -not $script:auxIamProcess.HasExited) {
            if (Wait-ForPort -Port 8002 -TimeoutSec 2) {
                return
            }
        }
        Write-Host "  [準備] 啟動輔助 IAM 服務 (Port 8002, 供權限集合預熱與認證)..." -ForegroundColor DarkCyan
        $iamArgs = @(
            "-Xms128m",
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-jar",
            "`"$iamJar`"",
            "--spring.profiles.active=$activeProfiles",
            "--spring.cloud.nacos.discovery.enabled=false",
            "--spring.liquibase.enabled=false",
            "--cache.bloom-filter.default-expected-insertions=500000"
        )
        $script:auxIamProcess = Start-Process -FilePath $javaCmd -ArgumentList $iamArgs -PassThru -WindowStyle Hidden
        Wait-ForPort -Port 8002 -TimeoutSec 45 | Out-Null
        Start-Sleep -Seconds 2
    }

    Ensure-AuxIamRunning

    $authToken = ""
    if (Wait-ForPort -Port 8002 -TimeoutSec 5) {
        try {
            $superKey = if ($env:SUPERUSER_KEY) { $env:SUPERUSER_KEY } else { "super_secret_key_change_in_production" }
            $adminEmail = if ($env:ADMIN_EMAIL) { $env:ADMIN_EMAIL } else { "admin@tsmc.com" }
            $adminPass = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "admin" }

            $superBody = @{ bootstrapSecret = $superKey; email = $adminEmail } | ConvertTo-Json
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

            if ($svcKey -ieq "iam") {
                # 壓測 IAM 本身時，先關閉輔助 IAM 行程，確保受測 IAM 以指定象限參數獨佔 Port 8002
                Stop-JavaProcess -ProcessObj $auxIamProcess -Name "輔助 IAM 服務" -Port 8002
                $auxIamProcess = $null
            } else {
                # 壓測非 IAM 服務時，確保輔助 IAM 在線供首次 DefaultPermissionValidator 預熱 Caffeine 權限快取
                Ensure-AuxIamRunning
            }

            Write-Host "`n  [$svcStep/$($selectedServices.Count)] 隨選啟動受測服務: $svcDisplay (Port: $svcPort)..." -ForegroundColor Cyan

            $bootArgs = $defaultVmArgs + @(
                "-jar",
                "`"$jarPath`"",
                "--spring.profiles.active=$activeProfiles",
                "--spring.cloud.nacos.discovery.enabled=false",
                "--spring.cloud.discovery.client.simple.instances.iam-service[0].uri=http://localhost:8002",
                "--spring.cloud.discovery.client.simple.instances.external-api-service[0].uri=http://localhost:8007",
                "--spring.liquibase.enabled=false",
                "--cache.bloom-filter.default-expected-insertions=500000",
                "--server.tomcat.accept-count=1000",
                "--server.tomcat.max-connections=10000",
                "--spring.cache.type=$qCacheType",
                "--spring.threads.virtual.enabled=$qVtArg"
            )
            $svcProcess = Start-Process -FilePath $javaCmd -ArgumentList $bootArgs -PassThru -WindowStyle Hidden

            if (-not (Wait-ForPort -Port $svcPort -TimeoutSec 45)) {
                Write-Host "    [警告] $svcDisplay 啟動超時，跳過此服務測試。" -ForegroundColor Red
                Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay -Port $svcPort
                continue
            }

            Start-Sleep -Seconds 2
            Write-Host "    -> $svcDisplay 啟動成功，執行 1 秒預熱 (Warm-up Caffeine 權限快取與路由)..." -ForegroundColor DarkGreen

            # 1 秒 2 VUs 預熱，使 DefaultPermissionValidator 的 userPermissionsCache 與 Redis L1/L2 熱點載入完畢
            $warmupArgs = @(
                "run",
                "-e", "BASE_URL=$baseUrl",
                "-e", "VUS=2",
                "-e", "DURATION=1s",
                "-e", "AUTH_TOKEN=$authToken",
                "-e", "WITH_CACHE=$($qCacheEnabled.ToString().ToLower())",
                "-e", "VIRTUAL_THREADS=$($qVirtualEnabled.ToString().ToLower())",
                $scriptPath
            )
            & k6 $warmupArgs | Out-Null

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

                $record = Parse-SummaryJson -summaryPath $summaryFile -qObj $q -svcObj $svc -vus $vus
                if ($null -ne $record) {
                    $allResults += $record
                    Write-Host "       [結果] RPS: $($record.RPS) req/s | P50: $($record.P50_ms) ms | P95: $($record.P95_ms) ms | P99: $($record.P99_ms) ms | 錯誤率: $($record.ErrorRate_pct)" -ForegroundColor Green
                } else {
                    Write-Host "       [錯誤] 無法解析報告: $summaryFile" -ForegroundColor Red
                }
            }

            # 立即終止當前受測微服務，釋放執行緒與記憶體
            Stop-JavaProcess -ProcessObj $svcProcess -Name $svcDisplay -Port $svcPort
            Start-Sleep -Seconds 1
        }
    }

    # 關閉輔助 IAM 與 Gateway 釋放所有測試資源
    Stop-JavaProcess -ProcessObj $auxIamProcess -Name "輔助 IAM 服務" -Port 8002
    Stop-JavaProcess -ProcessObj $gwProcess -Name "Gateway" -Port 8000

    if ($allResults.Count -gt 0) {
        Write-Host "`n=========================================================================================================================" -ForegroundColor Cyan
        Write-Host "                             微服務四象限隨選生命週期壓力測試基準結果彙總" -ForegroundColor Cyan
        Write-Host "=========================================================================================================================" -ForegroundColor Cyan
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
        $mdContent += "- **併發階梯**: $($vusList -join ', ') VUs (單輪時長: $durationStr)"
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

        Write-Host "  Markdown 快照報告已保存至: $mdReportPath" -ForegroundColor Green
        Write-Host "  CSV 報告已保存至         : $csvReportPath" -ForegroundColor Green
    }
}

# 5. 自動根據 target/k6-reports/summary-*.json 回填更新 stress-test/壓力測試結果.md
if (-not $NoUpdateMarkdown) {
    $docPath = Join-Path $PSScriptRoot "壓力測試結果.md"
    if (Test-Path $docPath) {
        Write-Host "`n[自動同步] 正在由 $targetReportDir 彙整最新壓測數據並回填至 stress-test/壓力測試結果.md..." -ForegroundColor Cyan

        $quadOrder = @("A", "B", "C", "D")
        $matrixLines = @()

        foreach ($svc in $allServices) {
            if ($matrixLines.Count -gt 0) {
                $matrixLines += ""
            }
            $matrixLines += $svc.HeaderTitle
            $matrixLines += "| 併發階梯 | 測試象限 | 快取狀態 | 執行緒模型 | 吞吐量 (req/s) | P50 (ms) | P90 (ms) | P95 (ms) | P99 (ms) | 平均延遲 (ms) | 錯誤率 |"
            $matrixLines += "| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |"

            $vIdx = 0
            foreach ($vus in @(50, 200, 500)) {
                $vIdx++
                if ($vIdx -gt 1) {
                    $matrixLines += "| ─── | ─── | ─── | ─── | ─── | ─── | ─── | ─── | ─── | ─── | ─── |"
                }
                $qIdx = 0
                foreach ($qKey in $quadOrder) {
                    $qIdx++
                    $qObj = $allQuadrants | Where-Object { $_.Key -eq $qKey } | Select-Object -First 1
                    $sumFile = Join-Path $targetReportDir "summary-$qKey-$($svc.Name)-$vus-vus.json"
                    $rec = Parse-SummaryJson -summaryPath $sumFile -qObj $qObj -svcObj $svc -vus $vus
                    $vuCol = if ($qIdx -eq 1) { "**$vus VUs**" } else { "" }
                    if ($null -ne $rec) {
                        $rpsStr = "{0:N1}" -f $rec.RPS
                        $p50Str = "{0:N2}" -f $rec.P50_ms
                        $p90Str = "{0:N2}" -f $rec.P90_ms
                        $p95Str = "{0:N2}" -f $rec.P95_ms
                        $p99Str = "{0:N2}" -f $rec.P99_ms
                        $avgStr = "{0:N2}" -f $rec.Avg_ms
                        $matrixLines += "| $vuCol | $($qObj.ShortTableName) | $($qObj.CacheIcon) | $($qObj.ThreadIcon) | $rpsStr | $p50Str | $p90Str | $p95Str | $p99Str | $avgStr | $($rec.ErrorRate_pct) |"
                    } else {
                        $matrixLines += "| $vuCol | $($qObj.ShortTableName) | $($qObj.CacheIcon) | $($qObj.ThreadIcon) | - | - | - | - | - | - | - |"
                    }
                }
            }
        }

        $goldenLines = @()
        $goldenLines += "| 微服務模組 | 併發階梯 (VUs) | 吞吐量 (req/s) | P50 延遲 (ms) | P90 延遲 (ms) | P95 延遲 (ms) | P99 延遲 (ms) | 平均延遲 (ms) | 錯誤率 (%) | 總請求數 (筆) |"
        $goldenLines += "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |"

        $qD = $allQuadrants | Where-Object { $_.Key -eq "D" } | Select-Object -First 1
        foreach ($svc in $allServices) {
            $vIdx = 0
            foreach ($vus in @(50, 200, 500)) {
                $vIdx++
                $svcCol = if ($vIdx -eq 1) { $svc.GoldenCell } else { "" }
                $sumFile = Join-Path $targetReportDir "summary-D-$($svc.Name)-$vus-vus.json"
                $rec = Parse-SummaryJson -summaryPath $sumFile -qObj $qD -svcObj $svc -vus $vus
                if ($null -ne $rec) {
                    $rpsStr = "{0:N1}" -f $rec.RPS
                    $p50Str = "{0:N2}" -f $rec.P50_ms
                    $p90Str = "{0:N2}" -f $rec.P90_ms
                    $p95Str = "{0:N2}" -f $rec.P95_ms
                    $p99Str = "{0:N2}" -f $rec.P99_ms
                    $avgStr = "{0:N2}" -f $rec.Avg_ms
                    $reqStr = "{0:N0}" -f $rec.TotalRequests
                    $goldenLines += "| $svcCol | $vus VUs | $rpsStr | $p50Str | $p90Str | $p95Str | $p99Str | $avgStr | **$($rec.ErrorRate_pct)** | $reqStr |"
                }
            }
        }

        $rawDoc = Get-Content $docPath -Raw -Encoding utf8
        $matrixBlock = "<!-- BENCHMARK_MATRIX_START -->`r`n" + ($matrixLines -join "`r`n") + "`r`n<!-- BENCHMARK_MATRIX_END -->"
        $goldenBlock = "<!-- GOLDEN_QUADRANT_START -->`r`n" + ($goldenLines -join "`r`n") + "`r`n<!-- GOLDEN_QUADRANT_END -->"

        $updatedDoc = [regex]::Replace($rawDoc, "(?s)<!-- BENCHMARK_MATRIX_START -->.*?<!-- BENCHMARK_MATRIX_END -->", $matrixBlock)
        $updatedDoc = [regex]::Replace($updatedDoc, "(?s)<!-- GOLDEN_QUADRANT_START -->.*?<!-- GOLDEN_QUADRANT_END -->", $goldenBlock)

        [System.IO.File]::WriteAllText($docPath, $updatedDoc, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  -> 已成功更新 stress-test/壓力測試結果.md 基準大表！" -ForegroundColor Green
    }
}
