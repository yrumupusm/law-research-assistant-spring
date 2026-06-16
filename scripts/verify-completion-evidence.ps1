param(
    [string]$ReadinessSummary = "$PSScriptRoot\..\target\readiness-summary.json",
    [string]$RuntimeSummary = "$PSScriptRoot\..\target\runtime-evidence\runtime-summary.json",
    [string]$ScenarioSummary = "$PSScriptRoot\..\target\scenario-responses\scenario-summary.json",
    [string]$EvidenceReport = "$PSScriptRoot\..\target\evidence-report.md"
)

$ErrorActionPreference = "Stop"
$Errors = [System.Collections.Generic.List[string]]::new()

function Add-Error {
    param([string]$Message)
    $Errors.Add($Message) | Out-Null
}

function Read-JsonIfExists {
    param(
        [string]$Path,
        [string]$Hint
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        Add-Error "Missing evidence file: $Path. $Hint"
        return $null
    }
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        Add-Error $Message
    }
}

function Test-Property {
    param(
        [object]$Object,
        [string]$Name
    )

    return $null -ne $Object -and ($Object.PSObject.Properties.Name -contains $Name)
}

$readiness = Read-JsonIfExists $ReadinessSummary "Run scripts\verify-readiness.ps1 first."
$runtime = Read-JsonIfExists $RuntimeSummary "Run scripts\verify-runtime.ps1 -IncludeProviderSmoke first."
$scenario = Read-JsonIfExists $ScenarioSummary "Run scripts\run-scenarios.ps1 first."

if ($null -ne $readiness) {
    Assert-True ([int]$readiness.failedCount -eq 0) "Readiness has failed checks: failedCount=$($readiness.failedCount)."
    Assert-True ($readiness.requireExternalProviders -eq $true) "Readiness should be captured with -RequireExternalProviders."
}

if ($null -ne $runtime) {
    Assert-True ($runtime.indexStatus -eq "healthy") "Runtime indexStatus must be healthy, got $($runtime.indexStatus)."
    Assert-True ($runtime.v1IndexStatus -eq "healthy") "Runtime v1IndexStatus must be healthy, got $($runtime.v1IndexStatus)."
    Assert-True ($runtime.askStatus -eq "OK") "Runtime askStatus must be OK, got $($runtime.askStatus)."
    Assert-True ($runtime.v1AskStatus -eq "ok") "Runtime v1AskStatus must be ok, got $($runtime.v1AskStatus)."
    Assert-True ([int]$runtime.askCitationCount -gt 0) "Runtime ask response must include citations."
    Assert-True ([int]$runtime.v1AskCitationCount -gt 0) "Runtime v1 ask response must include citations."
    Assert-True ($runtime.providerSmokeIncluded -eq $true) "Runtime verification must include provider smoke test."
    Assert-True (Test-Property $runtime "askRequestId") "Runtime summary must include askRequestId."
    Assert-True (Test-Property $runtime "v1AskRequestId") "Runtime summary must include v1AskRequestId."
}

if ($null -ne $scenario) {
    Assert-True ([int]$scenario.total -gt 0) "Scenario summary must include at least one scenario."
    Assert-True ([int]$scenario.failed -eq 0) "Scenario summary has failed scenarios: failed=$($scenario.failed)."
    foreach ($item in @($scenario.items)) {
        Assert-True ($item.ok -eq $true) "Scenario '$($item.id)' did not pass."
        Assert-True ($item.searchLogOk -eq $true) "Scenario '$($item.id)' is missing search log linkage."
        Assert-True ($item.traceOk -eq $true) "Scenario '$($item.id)' is missing agent trace linkage."
        Assert-True ([string]::IsNullOrWhiteSpace([string]$item.forbiddenCopy)) "Scenario '$($item.id)' contains forbidden copy: $($item.forbiddenCopy)."
    }
}

if (-not (Test-Path -LiteralPath $EvidenceReport)) {
    Add-Error "Missing evidence report: $EvidenceReport. Run scripts\export-evidence-report.ps1 first."
} else {
    $report = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $EvidenceReport).Path, [System.Text.Encoding]::UTF8)
    Assert-True $report.Contains("Configuration Readiness") "Evidence report must include Configuration Readiness."
    Assert-True $report.Contains("Runtime Smoke") "Evidence report must include Runtime Smoke."
    Assert-True $report.Contains("Scenario Summary") "Evidence report must include Scenario Summary."
    Assert-True $report.Contains("Scenario Details") "Evidence report must include Scenario Details."
    Assert-True (-not $report.Contains("OPENROUTER_API_KEY")) "Evidence report must not contain OPENROUTER_API_KEY."
    Assert-True (-not $report.Contains("RERANKER_API_KEY")) "Evidence report must not contain RERANKER_API_KEY."
    Assert-True (-not $report.Contains("EMBEDDING_API_KEY")) "Evidence report must not contain EMBEDDING_API_KEY."
    Assert-True (-not $report.Contains("LLM_API_KEY")) "Evidence report must not contain LLM_API_KEY."
}

Write-Host "Completion evidence check:"
Write-Host " - readiness: $ReadinessSummary"
Write-Host " - runtime: $RuntimeSummary"
Write-Host " - scenarios: $ScenarioSummary"
Write-Host " - report: $EvidenceReport"

if ($Errors.Count -gt 0) {
    Write-Host ""
    Write-Host "Errors:"
    foreach ($item in $Errors) {
        Write-Host " - $item"
    }
    exit 1
}

Write-Host ""
Write-Host "Completion evidence passed."
