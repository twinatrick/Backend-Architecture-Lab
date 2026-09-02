<#
.SYNOPSIS
    SonarQube Code Quality and Test Coverage Scanner Launcher

.DESCRIPTION
    1. Check and start local SonarQube Docker container (docker-compose.sonarqube.yaml)
    2. Read Java 21 path from .env (JAVA_NOW_HOME)
    3. Run single-thread unit tests and generate JaCoCo reports
    4. Run sonar-maven-plugin to publish analysis to SonarQube

.PARAMETER Token
    SonarQube analysis token

.PARAMETER HostUrl
    SonarQube server URL (default: http://localhost:9000)

.PARAMETER SkipTest
    Skip running tests (if JaCoCo reports are already present)

.PARAMETER StartServer
    Force start SonarQube Docker service
#>

[CmdletBinding()]
param (
    [string]$Token = "",
    [string]$HostUrl = "http://localhost:9000",
    [switch]$SkipTest,
    [switch]$StartServer
)

# 1. Check JAVA_HOME
Write-Host "[1/4] Checking Java 21 environment..." -ForegroundColor Cyan
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim()
            if ($k -eq "java-now-home" -or $k -eq "JAVA_NOW_HOME") {
                $env:JAVA_HOME = $v
            }
            if (-not [Environment]::GetEnvironmentVariable($k)) {
                [Environment]::SetEnvironmentVariable($k, $v, "Process")
            }
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    Write-Error "Error: JAVA_HOME is not set or invalid. Please check java-now-home in .env"
    exit 1
}
Write-Host "   [OK] JAVA_HOME: $($env:JAVA_HOME)" -ForegroundColor Green

# 2. Check SonarQube Server Status
Write-Host "[2/4] Checking SonarQube server status ($HostUrl)..." -ForegroundColor Cyan
$serverReady = $false
try {
    $resp = Invoke-RestMethod -Uri "$HostUrl/api/system/status" -TimeoutSec 3 -ErrorAction SilentlyContinue
    if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "STARTING")) {
        $serverReady = $true
        Write-Host "   [OK] SonarQube server connected (Status: $($resp.status))" -ForegroundColor Green
    }
} catch {}

if (-not $serverReady) {
    Write-Host "   [INFO] SonarQube server not ready. Starting Docker container..." -ForegroundColor Yellow
    $composeFile = Join-Path $PSScriptRoot "docker-compose.sonarqube.yaml"
    if (-not (Test-Path $composeFile)) {
        Write-Error "Error: Could not find $composeFile"
        exit 1
    }

    docker compose -f $composeFile up -d
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error: Docker Compose failed to start."
        exit 1
    }

    Write-Host "   [INFO] Waiting for SonarQube service initialization..." -ForegroundColor Yellow
    $maxRetries = 30
    $retryCount = 0
    while ($retryCount -lt $maxRetries) {
        Start-Sleep -Seconds 3
        try {
            $statusResp = Invoke-RestMethod -Uri "$HostUrl/api/system/status" -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($statusResp -and $statusResp.status -eq "UP") {
                Write-Host "   [OK] SonarQube server is UP and ready." -ForegroundColor Green
                $serverReady = $true
                break
            } elseif ($statusResp -and $statusResp.status -eq "STARTING") {
                Write-Host "   [INFO] Server status: $($statusResp.status)..." -ForegroundColor Gray
            }
        } catch {}
        $retryCount++
    }
}

# 3. Unit Tests & JaCoCo Generation
if (-not $SkipTest) {
    Write-Host "[3/4] Running single-thread tests for JaCoCo report generation..." -ForegroundColor Cyan
    Write-Host "   (Using -DforkCount=1 to prevent CPU starvation)" -ForegroundColor Gray
    
    $testCommand = "rtk ./mvnw clean test `"-DforkCount=1`" `"-Dsurefire.useFile=false`""
    Write-Host "   Running: $testCommand" -ForegroundColor Gray
    Invoke-Expression $testCommand
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Error: Unit tests failed. Aborting SonarQube analysis."
        exit 1
    }
    Write-Host "   [OK] Tests passed and JaCoCo reports generated." -ForegroundColor Green
} else {
    Write-Host "[3/4] Skipping test execution phase (-SkipTest enabled)." -ForegroundColor Yellow
}

# 4. SonarQube Scanner Execution
Write-Host "[4/4] Running SonarQube scanner analysis..." -ForegroundColor Cyan

$sonarToken = if ($Token) { $Token } else { $env:SONAR_TOKEN }
$sonarArgs = @("sonar:sonar", "-DforkCount=1", "-Dsurefire.useFile=false", "-Dsonar.host.url=$HostUrl")

if ($sonarToken) {
    $sonarArgs += "-Dsonar.token=$sonarToken"
    $sonarArgs += "-Dsonar.login=$sonarToken"
}

$displayArgs = $sonarArgs | ForEach-Object {
    if ($_ -like "-Dsonar.token=*" -or $_ -like "-Dsonar.login=*") {
        ($_.Split("=")[0]) + "=******"
    } else {
        $_
    }
}
Write-Host "   Running: ./mvnw $($displayArgs -join ' ')" -ForegroundColor Gray
& ./mvnw @sonarArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host "SonarQube Code Quality Analysis Completed Successfully!" -ForegroundColor Green
    Write-Host "Dashboard: $HostUrl/dashboard?id=BackendArchitectureLab" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Error "Error: SonarQube scan failed. Check console logs."
    exit 1
}
