<#
.SYNOPSIS
    使用團隊共用設定執行 Qodana，將本機分析結果放入 .local/。
#>

[CmdletBinding()]
param ()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$configPath = Join-Path $repoRoot 'config/quality/qodana.yaml'
$resultsPath = Join-Path $repoRoot '.local/review/qodana'

if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    Write-Error '找不到共用 Qodana 設定：config/quality/qodana.yaml'
    exit 1
}

$qodanaCommand = Get-Command qodana -ErrorAction SilentlyContinue
if (-not $qodanaCommand) {
    Write-Error '找不到 Qodana CLI，請先安裝並確認 qodana 已加入 PATH。'
    exit 1
}

New-Item -ItemType Directory -Force -Path $resultsPath | Out-Null
$scanExitCode = 1
Push-Location $repoRoot
try {
    # 不讀取或保存 Token；認證由呼叫端環境提供。
    & $qodanaCommand scan --config 'config/quality/qodana.yaml' --results-dir $resultsPath
    $scanExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $scanExitCode
