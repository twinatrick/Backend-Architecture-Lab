<#
.SYNOPSIS
    Strix AI Automated Security Penetration Testing Launcher

.DESCRIPTION
    Supported scan modes:
    - white (White-box): Static & Dynamic analysis on source code (supports diff-scope)
    - black (Black-box): Unauthenticated probing against endpoints with OpenAPI pairing
    - gray  (Gray-box):  Probing authenticated flows and IDOR with JWT token / credentials

.PARAMETER Mode
    white, black, gray (default: white)

.PARAMETER ScanMode
    quick, standard, deep (default: quick)

.PARAMETER ScopeMode
    diff, full, auto (default: diff)

.PARAMETER Target
    Target path or URL (default ./ for white, http://localhost:8000 for black/gray)

.PARAMETER AuthToken
    Bearer JWT Token for gray-box testing

.PARAMETER Instruction
    Custom instructions for the AI penetration agent

.PARAMETER NonInteractive
    Enable headless/non-interactive mode
#>

[CmdletBinding()]
param (
    [ValidateSet("white", "black", "gray")]
    [string]$Mode = "white",

    [ValidateSet("quick", "standard", "deep")]
    [string]$ScanMode = "quick",

    [ValidateSet("diff", "full", "auto")]
    [string]$ScopeMode = "diff",

    [string]$Target = "",

    [string]$AuthToken = "",

    [string]$Instruction = "",

    [switch]$NonInteractive
)

# 1. Check Docker
Write-Host "[1/4] Checking Docker status..." -ForegroundColor Cyan
try {
    $dockerInfo = docker info --format '{{.ServerVersion}}' 2>$null
    if (-not $dockerInfo) {
        Write-Error "Error: Docker is not running. Strix requires Docker for its isolated sandbox."
        exit 1
    }
    Write-Host "   [OK] Docker is running (Version: $dockerInfo)" -ForegroundColor Green
} catch {
    Write-Error "Error: Docker command not found. Please verify Docker installation."
    exit 1
}

# 2. Check/Load Environment Variables (.env)
Write-Host "[2/4] Checking LLM API configuration..." -ForegroundColor Cyan
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim()
            if (-not [Environment]::GetEnvironmentVariable($k)) {
                [Environment]::SetEnvironmentVariable($k, $v, "Process")
            }
        }
    }
}

if (-not $env:LLM_API_KEY) {
    if ($env:GEMINI_API_KEY) {
        $env:LLM_API_KEY = $env:GEMINI_API_KEY
        if (-not $env:STRIX_LLM) {
            $env:STRIX_LLM = "gemini/gemini-2.5-pro"
        }
    } elseif ($env:OPENAI_API_KEY) {
        $env:LLM_API_KEY = $env:OPENAI_API_KEY
        if (-not $env:STRIX_LLM) {
            $env:STRIX_LLM = "openai/gpt-4o"
        }
    } elseif ($env:GROQ_API_KEY) {
        $env:LLM_API_KEY = $env:GROQ_API_KEY
        if (-not $env:STRIX_LLM) {
            $env:STRIX_LLM = "groq/llama-3.3-70b-versatile"
        }
    }
}

if (-not $env:LLM_API_KEY) {
    Write-Warning "Warning: LLM_API_KEY, GEMINI_API_KEY or OPENAI_API_KEY not found in environment or .env"
} else {
    Write-Host "   [OK] LLM Model configured: $($env:STRIX_LLM)" -ForegroundColor Green
}

# 3. Build Arguments
Write-Host "[3/4] Configuring parameters for $Mode mode..." -ForegroundColor Cyan

$strixArgs = @()

if ($NonInteractive) {
    $strixArgs += "-n"
}

$strixArgs += "--scan-mode"
$strixArgs += $ScanMode

# White-Box
if ($Mode -eq "white") {
    $targetPath = if ($Target) { $Target } else { "./" }
    $strixArgs += "-t"
    $strixArgs += $targetPath

    $strixArgs += "--scope-mode"
    $strixArgs += $ScopeMode
    if ($ScopeMode -eq "diff") {
        $strixArgs += "--diff-base"
        $strixArgs += "origin/master"
    }
}

# Black-Box and Gray-Box
if ($Mode -eq "black" -or $Mode -eq "gray") {
    $targetUrl = if ($Target) { $Target } else { "http://localhost:8000" }

    # Check OpenAPI endpoint
    $openApiUrl = "$targetUrl/v3/api-docs"
    $hasOpenApi = $false
    try {
        $resp = Invoke-WebRequest -Uri $openApiUrl -Method Get -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($resp -and $resp.StatusCode -eq 200) {
            $hasOpenApi = $true
        }
    } catch {}

    if ($hasOpenApi) {
        Write-Host "   [OK] OpenAPI specification detected ($openApiUrl). Enabling API Spec pairing." -ForegroundColor Green
        $strixArgs += "-t"
        $strixArgs += $openApiUrl
    }

    $strixArgs += "-t"
    $strixArgs += $targetUrl

    # Gray-Box JWT Token & Authentication instructions
    if ($Mode -eq "gray") {
        $token = $AuthToken
        if (-not $token) {
            Write-Host "   [INFO] Attempting to auto-register test account for JWT token..." -ForegroundColor Yellow
            $testEmail = "strix_test_$(Get-Random)@example.com"
            $testPass = "StrixPass123!"
            $signupPayload = @{
                email = $testEmail
                password = $testPass
                username = "strix_tester"
            } | ConvertTo-Json

            try {
                $signupResp = Invoke-RestMethod -Uri "$targetUrl/api/v1/auth/signup" -Method Post -Body $signupPayload -ContentType "application/json" -TimeoutSec 3 -ErrorAction Stop
                if ($signupResp -and $signupResp.data -and $signupResp.data.accessToken) {
                    $token = $signupResp.data.accessToken
                    Write-Host "   [OK] Successfully retrieved JWT token from /signup endpoint." -ForegroundColor Green
                }
            } catch {
                Write-Warning "   [WARN] Auto-signup failed ($($_.Exception.Message)). Falling back to autonomous exploration."
            }
        }

        $authInstruction = if ($token) {
            "Use Bearer JWT Authorization header: '$token' for testing protected endpoints and IDOR."
        } else {
            "Please probe authentication flows, IDOR and role escalation. You may sign up a new user via /api/v1/auth/signup if needed."
        }

        $Instruction = if ($Instruction) { "$Instruction. $authInstruction" } else { $authInstruction }
    }
}

if ($Instruction) {
    $strixArgs += "--instruction"
    $strixArgs += $Instruction
}

# 4. Launch Strix
Write-Host "[4/4] Launching Strix Security Scanner..." -ForegroundColor Cyan
$displayArgs = $strixArgs | ForEach-Object {
    if ($_ -like "*eyJ*" -or $_ -like "*Bearer*") {
        "******"
    } else {
        $_
    }
}
Write-Host "   Command: strix $($displayArgs -join ' ')" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor DarkGray

& strix @strixArgs
