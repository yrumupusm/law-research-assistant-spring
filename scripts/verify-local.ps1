param(
    [string]$EnvFile = "",
    [string]$MavenPath = "",
    [switch]$StrictPreflight,
    [switch]$SkipNode,
    [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DefaultEnvFile = Join-Path $ProjectRoot ".env"
$ExampleEnvFile = Join-Path $ProjectRoot ".env.example"

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    if (Test-Path -LiteralPath $DefaultEnvFile) {
        $EnvFile = $DefaultEnvFile
    } else {
        $EnvFile = $ExampleEnvFile
    }
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

function Test-PowerShellScript {
    param([string]$Path)

    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) {
        foreach ($errorItem in $errors) {
            Write-Error "${Path}: $($errorItem.Message)"
        }
        throw "PowerShell parser check failed: $Path"
    }
}

function Resolve-MavenCommand {
    if (-not [string]::IsNullOrWhiteSpace($MavenPath) -and (Test-Path -LiteralPath $MavenPath)) {
        return $MavenPath
    }

    $mvn = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -ne $mvn) {
        return $mvn.Source
    }

    throw "Maven command not found. Set -MavenPath or install mvn.cmd on PATH."
}

Set-Location -LiteralPath $ProjectRoot

Invoke-Step "Preflight environment" {
    $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $ProjectRoot "scripts\preflight.ps1"), "-EnvFile", $EnvFile)
    if ($StrictPreflight) {
        $args += "-Strict"
    }
    powershell @args
}

Invoke-Step "PowerShell script parser checks" {
    $scripts = @(
        "scripts\export-evidence-report.ps1",
        "scripts\verify-completion-evidence.ps1",
        "scripts\verify-final.ps1",
        "scripts\preflight.ps1",
        "scripts\verify-readiness.ps1",
        "scripts\verify-local.ps1",
        "scripts\verify-runtime.ps1",
        "scripts\run-scenarios.ps1",
        "scripts\server.ps1",
        "scripts\infra.ps1"
    )

    foreach ($script in $scripts) {
        Test-PowerShellScript (Join-Path $ProjectRoot $script)
    }
}

if (-not $SkipNode) {
    Invoke-Step "Static JavaScript syntax checks" {
        node --check (Join-Path $ProjectRoot "src\main\resources\static\app.js")
        node --check (Join-Path $ProjectRoot "src\main\resources\static\admin.js")
    }
}

if (-not $SkipMaven) {
    Invoke-Step "Maven test suite" {
        $mvn = Resolve-MavenCommand
        & $mvn "-q" "-Dmaven.compiler.useIncrementalCompilation=false" "test"
    }
}

Write-Host ""
Write-Host "Local verification passed."
Write-Host "Server/runtime checks still require a running application:"
Write-Host " - powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1"
Write-Host " - powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1"
