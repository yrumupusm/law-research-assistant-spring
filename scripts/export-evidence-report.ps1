param(
    [string]$ReadinessSummary = "$PSScriptRoot\..\target\readiness-summary.json",
    [string]$RuntimeSummary = "$PSScriptRoot\..\target\runtime-evidence\runtime-summary.json",
    [string]$ScenarioSummary = "$PSScriptRoot\..\target\scenario-responses\scenario-summary.json",
    [string]$OutputFile = "$PSScriptRoot\..\target\evidence-report.md"
)

$ErrorActionPreference = "Stop"

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Hint
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required evidence file not found: $Path. $Hint"
    }
}

function Read-Json {
    param([string]$Path)

    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
}

function Escape-Markdown {
    param([object]$Value)

    $text = ""
    if ($null -ne $Value) {
        $text = [string]$Value
    }
    return $text.Replace("|", "\|").Replace('`', "'")
}

function Format-Bool {
    param([object]$Value)

    if ($Value -eq $true) {
        return "OK"
    }
    return "FAIL"
}

Assert-FileExists $RuntimeSummary "Run scripts\verify-runtime.ps1 first."
Assert-FileExists $ScenarioSummary "Run scripts\run-scenarios.ps1 first."

$readiness = $null
if (Test-Path -LiteralPath $ReadinessSummary) {
    $readiness = Read-Json $ReadinessSummary
}
$runtime = Read-Json $RuntimeSummary
$scenario = Read-Json $ScenarioSummary

$outputPath = Split-Path -Parent $OutputFile
if (-not [string]::IsNullOrWhiteSpace($outputPath)) {
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# Law Research Assistant Runtime Evidence") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("Generated at: $(Get-Date -Format o)") | Out-Null
$lines.Add("") | Out-Null
if ($null -ne $readiness) {
    $lines.Add("## Configuration Readiness") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| Item | Value |") | Out-Null
    $lines.Add("|---|---|") | Out-Null
    $lines.Add("| Env File | $(Escape-Markdown $readiness.envFile) |") | Out-Null
    $lines.Add("| Base URL | $(Escape-Markdown $readiness.baseUrl) |") | Out-Null
    $lines.Add("| Failures | $(Escape-Markdown $readiness.failedCount) |") | Out-Null
    $lines.Add("| Warnings | $(Escape-Markdown $readiness.warningCount) |") | Out-Null
    $lines.Add("| Require External Providers | $(Escape-Markdown $readiness.requireExternalProviders) |") | Out-Null
    $lines.Add("| Require Qdrant | $(Escape-Markdown $readiness.requireQdrant) |") | Out-Null
    $lines.Add("| Require Cohere | $(Escape-Markdown $readiness.requireCohere) |") | Out-Null
    $lines.Add("") | Out-Null

    $warningChecks = @($readiness.checks | Where-Object { $_.status -eq "warning" })
    if ($warningChecks.Count -gt 0) {
        $lines.Add("### Readiness Warnings") | Out-Null
        $lines.Add("") | Out-Null
        $lines.Add("| Check | Message |") | Out-Null
        $lines.Add("|---|---|") | Out-Null
        foreach ($check in $warningChecks) {
            $lines.Add("| $(Escape-Markdown $check.name) | $(Escape-Markdown $check.message) |") | Out-Null
        }
        $lines.Add("") | Out-Null
    }
}
$lines.Add("## Runtime Smoke") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("| Item | Value |") | Out-Null
$lines.Add("|---|---|") | Out-Null
$lines.Add("| Base URL | $(Escape-Markdown $runtime.baseUrl) |") | Out-Null
$lines.Add("| Snapshot | $(Escape-Markdown $runtime.snapshotVersion) |") | Out-Null
$lines.Add("| Index Status | $(Escape-Markdown $runtime.indexStatus) |") | Out-Null
$lines.Add("| Laws | $(Escape-Markdown $runtime.lawsCount) |") | Out-Null
$lines.Add("| Articles | $(Escape-Markdown $runtime.articlesCount) |") | Out-Null
$lines.Add("| Indexed Articles | $(Escape-Markdown $runtime.indexedArticlesCount) |") | Out-Null
$lines.Add("| Unindexed Articles | $(Escape-Markdown $runtime.unindexedArticlesCount) |") | Out-Null
$lines.Add("| Ask Status | $(Escape-Markdown $runtime.askStatus) |") | Out-Null
$lines.Add("| Ask Request ID | $(Escape-Markdown $runtime.askRequestId) |") | Out-Null
$lines.Add("| Ask Citations | $(Escape-Markdown $runtime.askCitationCount) |") | Out-Null
$lines.Add("| v1 Ask Status | $(Escape-Markdown $runtime.v1AskStatus) |") | Out-Null
$lines.Add("| v1 Ask Request ID | $(Escape-Markdown $runtime.v1AskRequestId) |") | Out-Null
$lines.Add("| v1 Ask Citations | $(Escape-Markdown $runtime.v1AskCitationCount) |") | Out-Null
$lines.Add("| Provider Smoke Included | $(Escape-Markdown $runtime.providerSmokeIncluded) |") | Out-Null
$lines.Add("| Reindex Contract Included | $(Escape-Markdown $runtime.reindexContractIncluded) |") | Out-Null
$lines.Add("| Runtime Evidence Dir | $(Escape-Markdown $runtime.evidenceDir) |") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("## Scenario Summary") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("| Item | Value |") | Out-Null
$lines.Add("|---|---|") | Out-Null
$lines.Add("| Scenario File | $(Escape-Markdown $scenario.scenarioFile) |") | Out-Null
$lines.Add("| Output Dir | $(Escape-Markdown $scenario.outputDir) |") | Out-Null
$lines.Add("| Total | $(Escape-Markdown $scenario.total) |") | Out-Null
$lines.Add("| Passed | $(Escape-Markdown $scenario.passed) |") | Out-Null
$lines.Add("| Failed | $(Escape-Markdown $scenario.failed) |") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("## Scenario Details") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("| ID | OK | Status | Citations | Vector Hits | Request ID | Search Log | Trace | Forbidden Copy |") | Out-Null
$lines.Add("|---|---|---|---:|---:|---|---|---|---|") | Out-Null

foreach ($item in @($scenario.items)) {
    $forbidden = "-"
    if (-not [string]::IsNullOrWhiteSpace([string]$item.forbiddenCopy)) {
        $forbidden = $item.forbiddenCopy
    }
    $lines.Add("| $(Escape-Markdown $item.id) | $(Format-Bool $item.ok) | $(Escape-Markdown $item.status) | $(Escape-Markdown $item.citationCount) | $(Escape-Markdown $item.vectorHits) | $(Escape-Markdown $item.requestId) | $(Format-Bool $item.searchLogOk) | $(Format-Bool $item.traceOk) | $(Escape-Markdown $forbidden) |") | Out-Null
}

$lines.Add("") | Out-Null
$lines.Add("## Safety Notes") | Out-Null
$lines.Add("") | Out-Null
$lines.Add("- This report is generated from readiness, runtime, and scenario evidence JSON files.") | Out-Null
$lines.Add("- It does not include API keys or full user question text.") | Out-Null
$lines.Add("- Request IDs are retained to connect answer, search log, and agent trace records.") | Out-Null

[System.IO.File]::WriteAllText($OutputFile, ($lines -join [Environment]::NewLine), [System.Text.UTF8Encoding]::new($false))
Write-Host "Evidence report written: $OutputFile"
