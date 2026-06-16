param(
    [string]$BaseUrl = "",
    [string]$EnvFile = "",
    [int]$RuntimeTimeoutSeconds = 10,
    [int]$ScenarioTimeoutSeconds = 20,
    [switch]$RequireQdrant,
    [switch]$RequireCohere,
    [switch]$IncludeReindexContract
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DefaultEnvFile = Join-Path $ProjectRoot ".env"
$ReadinessSummary = Join-Path $ProjectRoot "target\readiness-summary.json"
$RuntimeSummary = Join-Path $ProjectRoot "target\runtime-evidence\runtime-summary.json"
$ScenarioSummary = Join-Path $ProjectRoot "target\scenario-responses\scenario-summary.json"
$EvidenceReport = Join-Path $ProjectRoot "target\evidence-report.md"

if ([string]::IsNullOrWhiteSpace($EnvFile) -and (Test-Path -LiteralPath $DefaultEnvFile)) {
    $EnvFile = $DefaultEnvFile
}

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$ScriptBlock
    )

    $started = Get-Date
    Write-Host ""
    Write-Host "==> $Name"
    & $ScriptBlock
    $elapsed = [int]((Get-Date) - $started).TotalSeconds
    Write-Host "OK: $Name ($elapsed sec)"
}

function Assert-LastExitCode {
    param([string]$Name)

    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

Set-Location -LiteralPath $ProjectRoot

Invoke-Step "Readiness with external providers" {
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        (Join-Path $ProjectRoot "scripts\verify-readiness.ps1"),
        "-EnvFile",
        $EnvFile,
        "-RequireExternalProviders"
    )
    if ($RequireQdrant) {
        $args += "-RequireQdrant"
    }
    if ($RequireCohere) {
        $args += "-RequireCohere"
    }
    powershell @args
    Assert-LastExitCode "verify-readiness.ps1"
}

Invoke-Step "Runtime verification with provider smoke" {
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        (Join-Path $ProjectRoot "scripts\verify-runtime.ps1"),
        "-EnvFile",
        $EnvFile,
        "-TimeoutSeconds",
        $RuntimeTimeoutSeconds,
        "-IncludeProviderSmoke"
    )
    if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) {
        $args += "-BaseUrl"
        $args += $BaseUrl
    }
    if ($IncludeReindexContract) {
        $args += "-IncludeReindexContract"
    }
    powershell @args
    Assert-LastExitCode "verify-runtime.ps1"
}

Invoke-Step "Scenario harness" {
    $args = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        (Join-Path $ProjectRoot "scripts\run-scenarios.ps1"),
        "-EnvFile",
        $EnvFile,
        "-TimeoutSeconds",
        $ScenarioTimeoutSeconds
    )
    if (-not [string]::IsNullOrWhiteSpace($BaseUrl)) {
        $args += "-BaseUrl"
        $args += $BaseUrl
    }
    powershell @args
    Assert-LastExitCode "run-scenarios.ps1"
}

Invoke-Step "Evidence report" {
    powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ProjectRoot "scripts\export-evidence-report.ps1") `
        -ReadinessSummary $ReadinessSummary `
        -RuntimeSummary $RuntimeSummary `
        -ScenarioSummary $ScenarioSummary `
        -OutputFile $EvidenceReport
    Assert-LastExitCode "export-evidence-report.ps1"
}

Invoke-Step "Completion evidence gate" {
    powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ProjectRoot "scripts\verify-completion-evidence.ps1") `
        -ReadinessSummary $ReadinessSummary `
        -RuntimeSummary $RuntimeSummary `
        -ScenarioSummary $ScenarioSummary `
        -EvidenceReport $EvidenceReport
    Assert-LastExitCode "verify-completion-evidence.ps1"
}

Write-Host ""
Write-Host "Final runtime verification passed."
Write-Host "Evidence report: $EvidenceReport"
