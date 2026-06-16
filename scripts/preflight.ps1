param(
    [string]$EnvFile = $(Join-Path (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path ".env"),
    [switch]$Strict
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$ExampleEnvFile = Join-Path $ProjectRoot ".env.example"
$Errors = [System.Collections.Generic.List[string]]::new()
$Warnings = [System.Collections.Generic.List[string]]::new()
$EnvValues = [System.Collections.Hashtable]::new([System.StringComparer]::OrdinalIgnoreCase)

function Add-CheckError {
    param([string]$Message)
    $Errors.Add($Message) | Out-Null
}

function Add-CheckWarning {
    param([string]$Message)
    $Warnings.Add($Message) | Out-Null
}

function Read-EnvProperties {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        Add-CheckError "Env file not found: $Path"
        return
    }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $lineNumber++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            Add-CheckWarning "Ignoring malformed env line $lineNumber."
            continue
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $EnvValues[$name] = $value
    }
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

function Require-Value {
    param(
        [string]$Name,
        [string]$Reason
    )

    if (-not (Test-HasValue $Name)) {
        Add-CheckError "$Name is required. $Reason"
    }
}

function Require-PositiveInt {
    param(
        [string]$Name,
        [int]$DefaultValue
    )

    $value = Get-EnvValue $Name ([string]$DefaultValue)
    $parsed = 0
    if (-not [int]::TryParse($value, [ref]$parsed) -or $parsed -le 0) {
        Add-CheckError "$Name must be a positive integer."
    }
}

function Require-DecimalRange {
    param(
        [string]$Name,
        [double]$DefaultValue,
        [double]$Minimum,
        [double]$Maximum
    )

    $value = Get-EnvValue $Name ([string]$DefaultValue)
    $parsed = 0.0
    if (-not [double]::TryParse(
            $value,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed
        ) -or $parsed -lt $Minimum -or $parsed -gt $Maximum) {
        Add-CheckError "$Name must be a number between $Minimum and $Maximum."
    }
}

function Require-Boolean {
    param(
        [string]$Name,
        [string]$DefaultValue
    )

    $value = (Get-EnvValue $Name $DefaultValue).ToLowerInvariant()
    if ($value -notin @("true", "false")) {
        Add-CheckError "$Name must be true or false."
    }
}

function Require-AllowedValue {
    param(
        [string]$Name,
        [string]$DefaultValue,
        [string[]]$AllowedValues
    )

    $value = (Get-EnvValue $Name $DefaultValue).ToLowerInvariant()
    if ($AllowedValues -notcontains $value) {
        Add-CheckError "$Name has unsupported value '$value'. Allowed: $($AllowedValues -join ', ')."
    }
    return $value
}

Read-EnvProperties $EnvFile

$llmProvider = Require-AllowedValue "LLM_PROVIDER" "mock" @("mock", "openrouter")
$embeddingProvider = Require-AllowedValue "EMBEDDING_PROVIDER" "mock" @("mock", "openrouter")
$rerankerProvider = Require-AllowedValue "RERANKER_PROVIDER" "mock" @("mock", "cohere")
$vectorProvider = Require-AllowedValue "VECTOR_PROVIDER" "inmemory" @("inmemory", "qdrant")

Require-PositiveInt "HTTP_TIMEOUT_SECONDS" 30
Require-PositiveInt "SERVER_PORT" 8080
Require-PositiveInt "LLM_TIMEOUT_SECONDS" 30
Require-PositiveInt "LLM_MAX_TOKENS" 1200
Require-DecimalRange "LLM_TEMPERATURE" 0.1 0.0 2.0
Require-PositiveInt "EMBEDDING_DIMENSIONS" 1024
Require-PositiveInt "EMBEDDING_MAX_CHARS" 4000
Require-PositiveInt "EMBEDDING_BATCH_SIZE" 32
Require-PositiveInt "VECTOR_TOP_K" 5
Require-PositiveInt "RAG_TOP_K" 5
Require-PositiveInt "RERANKER_TOP_K" 5
Require-PositiveInt "POSTGRES_PORT" 5432
Require-PositiveInt "QDRANT_PORT" 6333
Require-PositiveInt "SEARCH_LOG_RETENTION_DAYS" 90
Require-Boolean "LOG_SENSITIVE_DATA" "false"
Require-Boolean "STORE_RAW_QUESTION" "false"
Require-Boolean "ENABLE_AGENT_TRACE" "true"
Require-Boolean "ADMIN_REINDEX_ENABLED" "false"

if ($llmProvider -eq "openrouter") {
    if (-not (Test-HasValue "OPENROUTER_API_KEY") -and -not (Test-HasValue "LLM_API_KEY")) {
        Add-CheckError "OPENROUTER_API_KEY or LLM_API_KEY is required for LLM_PROVIDER=openrouter."
    }
    Require-Value "LLM_MODEL" "Set the OpenRouter chat model."
    Require-Value "OPENROUTER_BASE_URL" "Set the OpenRouter base URL."
}

if ($embeddingProvider -eq "openrouter") {
    if (-not (Test-HasValue "OPENROUTER_API_KEY") -and -not (Test-HasValue "EMBEDDING_API_KEY")) {
        Add-CheckError "OPENROUTER_API_KEY or EMBEDDING_API_KEY is required for EMBEDDING_PROVIDER=openrouter."
    }
    Require-Value "EMBEDDING_MODEL" "Set the OpenRouter embedding model."
    Require-Value "OPENROUTER_BASE_URL" "Set the OpenRouter base URL."
}

if ($rerankerProvider -eq "cohere") {
    Require-Value "RERANKER_API_KEY" "Set the Cohere reranker API key."
    Require-Value "RERANKER_MODEL" "Set the Cohere reranker model."
    Require-Value "RERANKER_BASE_URL" "Set the Cohere API base URL."
}

if ($vectorProvider -eq "qdrant") {
    Require-Value "QDRANT_BASE_URL" "Set the Qdrant endpoint."
    Require-Value "VECTOR_COLLECTION" "Set the Qdrant collection name."
    Require-Value "QDRANT_DISTANCE" "Set the Qdrant distance metric."
}

if ((Get-EnvValue "LOG_SENSITIVE_DATA" "false").ToLowerInvariant() -eq "true") {
    Add-CheckError "LOG_SENSITIVE_DATA=true is not allowed for this project."
}

if ((Get-EnvValue "STORE_RAW_QUESTION" "false").ToLowerInvariant() -eq "true") {
    Add-CheckError "STORE_RAW_QUESTION=true is not allowed for this project."
}

if ((Get-EnvValue "ENABLE_AGENT_TRACE" "true").ToLowerInvariant() -eq "false") {
    Add-CheckWarning "ENABLE_AGENT_TRACE=false disables the request audit trail."
}

if ((Get-EnvValue "ADMIN_REINDEX_ENABLED" "false").ToLowerInvariant() -eq "true") {
    Add-CheckWarning "ADMIN_REINDEX_ENABLED=true allows manual reindex endpoints. Keep it disabled unless reindexing is intended."
}

if ($Strict -and $vectorProvider -ne "qdrant") {
    Add-CheckWarning "Strict mode: VECTOR_PROVIDER is not qdrant. Runtime still works, but it does not exercise external vector infrastructure."
}

if ($Strict -and $llmProvider -ne "openrouter") {
    Add-CheckWarning "Strict mode: LLM_PROVIDER is not openrouter. Runtime still works, but it does not exercise the external LLM provider."
}

if ($Strict -and $embeddingProvider -ne "openrouter") {
    Add-CheckWarning "Strict mode: EMBEDDING_PROVIDER is not openrouter. Runtime still works, but it does not exercise external embeddings."
}

Write-Host "Preflight env file: $EnvFile"
Write-Host "Provider profile: llm=$llmProvider embedding=$embeddingProvider reranker=$rerankerProvider vector=$vectorProvider"
Write-Host "Secrets: values are checked for presence only and are not printed."

if ($Warnings.Count -gt 0) {
    Write-Host ""
    Write-Host "Warnings:"
    foreach ($warning in $Warnings) {
        Write-Host " - $warning"
    }
}

if ($Errors.Count -gt 0) {
    Write-Host ""
    Write-Host "Errors:"
    foreach ($item in $Errors) {
        Write-Host " - $item"
    }
    exit 1
}

Write-Host ""
Write-Host "Preflight passed."
Write-Host "Next checks:"
Write-Host " - powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1"
Write-Host " - powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-scenarios.ps1"

if (-not (Test-Path -LiteralPath $ExampleEnvFile)) {
    exit 0
}
