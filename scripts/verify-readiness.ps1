param(
    [string]$EnvFile = "",
    [switch]$Strict,
    [switch]$RequireExternalProviders,
    [switch]$RequireQdrant,
    [switch]$RequireCohere
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DefaultEnvFile = Join-Path $ProjectRoot ".env"
$ExampleEnvFile = Join-Path $ProjectRoot ".env.example"
$TargetDir = Join-Path $ProjectRoot "target"
$SummaryPath = Join-Path $TargetDir "readiness-summary.json"
$Checks = [System.Collections.Generic.List[object]]::new()
$EnvValues = [System.Collections.Hashtable]::new([System.StringComparer]::OrdinalIgnoreCase)

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    if (Test-Path -LiteralPath $DefaultEnvFile) {
        $EnvFile = $DefaultEnvFile
    } else {
        $EnvFile = $ExampleEnvFile
    }
}

function Add-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Message
    )

    $Checks.Add([ordered]@{
        name = $Name
        status = $Status
        message = $Message
    }) | Out-Null
}

function Read-EnvProperties {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        Add-Check "env-file" "failed" "Env file not found: $Path"
        return
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            continue
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $EnvValues[$name] = $value
    }

    Add-Check "env-file" "ok" "Env file loaded. Secret values are never printed."
}

function Get-EnvValue {
    param(
        [string]$Name,
        [string]$DefaultValue = ""
    )

    if ($EnvValues.ContainsKey($Name)) {
        return [string]$EnvValues[$Name]
    }
    return $DefaultValue
}

function Test-HasValue {
    param([string]$Name)

    $value = Get-EnvValue $Name
    return -not [string]::IsNullOrWhiteSpace($value) -and $value -ne "[REDACTED]"
}

function Add-RequiredValueCheck {
    param(
        [string]$Name,
        [string]$Reason
    )

    if (Test-HasValue $Name) {
        Add-Check $Name "ok" "$Name is set."
    } else {
        Add-Check $Name "failed" "$Name is required. $Reason"
    }
}

function Add-ProviderModeCheck {
    param(
        [string]$Name,
        [string]$Expected,
        [string]$DefaultValue
    )

    $actual = (Get-EnvValue $Name $DefaultValue).ToLowerInvariant()
    $providerMustMatch = $false
    if ($RequireExternalProviders -and $Name -in @("LLM_PROVIDER", "EMBEDDING_PROVIDER")) {
        $providerMustMatch = $true
    }
    if ($Name -eq "VECTOR_PROVIDER" -and $RequireQdrant) {
        $providerMustMatch = $true
    }
    if ($Name -eq "RERANKER_PROVIDER" -and $RequireCohere) {
        $providerMustMatch = $true
    }

    if ($actual -eq $Expected) {
        Add-Check $Name "ok" "$Name=$actual"
    } elseif ($providerMustMatch) {
        Add-Check $Name "failed" "$Name=$actual, expected $Expected for this readiness profile."
    } else {
        Add-Check $Name "warning" "$Name=$actual. Runtime can work, but this does not exercise $Expected."
    }
}

function Test-FileContains {
    param(
        [string]$Path,
        [string]$Needle
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }
    return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8).Contains($Needle)
}

Set-Location -LiteralPath $ProjectRoot
New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

Read-EnvProperties $EnvFile

$preflightArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $ProjectRoot "scripts\preflight.ps1"), "-EnvFile", $EnvFile)
if ($Strict) {
    $preflightArgs += "-Strict"
}

try {
    powershell @preflightArgs | Out-Null
    Add-Check "preflight" "ok" "scripts/preflight.ps1 passed."
} catch {
    Add-Check "preflight" "failed" "scripts/preflight.ps1 failed. Run it directly for details."
}

Add-ProviderModeCheck "LLM_PROVIDER" "openrouter" "mock"
Add-ProviderModeCheck "EMBEDDING_PROVIDER" "openrouter" "mock"
Add-ProviderModeCheck "VECTOR_PROVIDER" "qdrant" "inmemory"
Add-ProviderModeCheck "RERANKER_PROVIDER" "cohere" "mock"

if ((Get-EnvValue "LLM_PROVIDER" "mock").ToLowerInvariant() -eq "openrouter" -or $RequireExternalProviders) {
    if ((Test-HasValue "OPENROUTER_API_KEY") -or (Test-HasValue "LLM_API_KEY")) {
        Add-Check "llm-api-key" "ok" "OpenRouter LLM key is present."
    } else {
        Add-Check "llm-api-key" "failed" "OPENROUTER_API_KEY or LLM_API_KEY is required for OpenRouter LLM."
    }
    Add-RequiredValueCheck "LLM_MODEL" "Set the OpenRouter chat model name."
}

if ((Get-EnvValue "EMBEDDING_PROVIDER" "mock").ToLowerInvariant() -eq "openrouter" -or $RequireExternalProviders) {
    if ((Test-HasValue "OPENROUTER_API_KEY") -or (Test-HasValue "EMBEDDING_API_KEY")) {
        Add-Check "embedding-api-key" "ok" "OpenRouter embedding key is present."
    } else {
        Add-Check "embedding-api-key" "failed" "OPENROUTER_API_KEY or EMBEDDING_API_KEY is required for OpenRouter embeddings."
    }
    Add-RequiredValueCheck "EMBEDDING_MODEL" "Set the OpenRouter embedding model name."
}

if ((Get-EnvValue "VECTOR_PROVIDER" "inmemory").ToLowerInvariant() -eq "qdrant" -or $RequireQdrant) {
    Add-RequiredValueCheck "QDRANT_BASE_URL" "Set the Qdrant endpoint."
    Add-RequiredValueCheck "VECTOR_COLLECTION" "Set the Qdrant collection name."
}

if ((Get-EnvValue "RERANKER_PROVIDER" "mock").ToLowerInvariant() -eq "cohere" -or $RequireCohere) {
    Add-RequiredValueCheck "RERANKER_API_KEY" "Set the Cohere reranker key."
    Add-RequiredValueCheck "RERANKER_MODEL" "Set the Cohere reranker model."
}

if (Test-FileContains (Join-Path $ProjectRoot ".gitignore") ".env") {
    Add-Check "secret-gitignore" "ok" ".env is ignored."
} else {
    Add-Check "secret-gitignore" "failed" ".env must be ignored before using real API keys."
}

$requiredFiles = @(
    "harness\scenario-requests.json",
    "harness\questions.json",
    "harness\answer-quality-v2.json",
    "docs\original-parity.md",
    "docs\runtime-readiness.md",
    "src\main\resources\static\index.html",
    "src\main\resources\static\admin.html",
    "scripts\verify-readiness.ps1",
    "scripts\verify-runtime.ps1",
    "scripts\run-scenarios.ps1",
    "scripts\export-evidence-report.ps1",
    "scripts\verify-completion-evidence.ps1",
    "scripts\verify-final.ps1"
)

foreach ($relative in $requiredFiles) {
    if (Test-Path -LiteralPath (Join-Path $ProjectRoot $relative)) {
        Add-Check "file:$relative" "ok" "Required artifact exists."
    } else {
        Add-Check "file:$relative" "failed" "Required artifact is missing."
    }
}

$failedCount = @($Checks | Where-Object { $_.status -eq "failed" }).Count
$warningCount = @($Checks | Where-Object { $_.status -eq "warning" }).Count
$serverPort = Get-EnvValue "SERVER_PORT" "8080"
$baseUrl = "http://localhost:$serverPort"

$summary = [ordered]@{
    checkedAt = (Get-Date).ToString("o")
    envFile = $EnvFile
    baseUrl = $baseUrl
    strict = [bool]$Strict
    requireExternalProviders = [bool]$RequireExternalProviders
    requireQdrant = [bool]$RequireQdrant
    requireCohere = [bool]$RequireCohere
    failedCount = $failedCount
    warningCount = $warningCount
    checks = $Checks
    nextManualCommands = @(
        "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-final.ps1",
        "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1 -IncludeProviderSmoke",
        "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1",
        "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-evidence-report.ps1",
        "powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-completion-evidence.ps1"
    )
}

[System.IO.File]::WriteAllText($SummaryPath, ($summary | ConvertTo-Json -Depth 8), [System.Text.Encoding]::UTF8)

Write-Host "Readiness verification completed."
Write-Host "EnvFile=$EnvFile"
Write-Host "BaseUrl=$baseUrl"
Write-Host "Failures=$failedCount Warnings=$warningCount"
Write-Host "Summary=$SummaryPath"
Write-Host "Secrets: values were checked for presence only and were not printed."
Write-Host ""
Write-Host "Next manual runtime commands after you start or restart the server:"
foreach ($command in $summary.nextManualCommands) {
    Write-Host " - $command"
}

if ($failedCount -gt 0) {
    exit 1
}
