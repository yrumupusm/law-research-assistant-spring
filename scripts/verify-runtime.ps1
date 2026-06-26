param(
    [string]$BaseUrl = "",
    [string]$EnvFile = "",
    [int]$TimeoutSeconds = 10,
    [string]$EvidenceDir = "",
    [switch]$IncludeProviderSmoke,
    [switch]$IncludeReindexContract
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$TargetDir = Join-Path $ProjectRoot "target"
$DefaultEnvFile = Join-Path $ProjectRoot ".env"
$DefaultEvidenceDir = Join-Path $TargetDir "runtime-evidence"

function Get-EnvFileValue {
    param(
        [string]$Path,
        [string]$Name
    )

    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) {
        return $null
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
        $key = $trimmed.Substring(0, $separator).Trim()
        if ($key -ieq $Name) {
            return $trimmed.Substring($separator + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($EnvFile) -and (Test-Path -LiteralPath $DefaultEnvFile)) {
    $EnvFile = $DefaultEnvFile
}
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $serverPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { Get-EnvFileValue -Path $EnvFile -Name "SERVER_PORT" }
    if ([string]::IsNullOrWhiteSpace($serverPort)) {
        $serverPort = "8080"
    }
    $BaseUrl = "http://localhost:$serverPort"
}
if ([string]::IsNullOrWhiteSpace($EvidenceDir)) {
    $EvidenceDir = $DefaultEvidenceDir
}
$AskResponseFile = Join-Path $TargetDir "runtime-ask-response.json"

function Decode-Utf8Base64 {
    param([string]$Value)

    return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

$Text = @{
    RootTitle = Decode-Utf8Base64 "67KV66C5IOumrOyEnOy5mCDslrTsi5zsiqTthLTtirg="
    AdminTitle = Decode-Utf8Base64 "6rSA66asIOuMgOyLnOuztOuTnA=="
    AskQuestion = Decode-Utf8Base64 "7KCE656166y87J6Q66W8IOyImOy2nO2VmOugpOuptCDslrTrlqQg7ZeI6rCAIOyalOqxtOydhCDtmZXsnbjtlbTslbwg7ZWY64KY7JqUPw=="
    ForeignTradeAct = Decode-Utf8Base64 "64yA7Jm466y07Jet67KV"
    StrategicItem = Decode-Utf8Base64 "7KCE656166y87J6Q"
    KoreanDisclaimer = Decode-Utf8Base64 "67KV66C5IOyhsOyCrCDrs7TsobAg6rKw6rO8"
}

$ForbiddenCopy = @(
    "LEGACY-SAMPLE",
    "legacy-domain-sample",
    "demo",
    "sample",
    "mini project",
    (Decode-Utf8Base64 "7Y+s7Yq47Y+066as7Jik"),
    (Decode-Utf8Base64 "66+464uIIO2UhOuhnOygne2KuA=="),
    (Decode-Utf8Base64 "642w66qo"),
    (Decode-Utf8Base64 "7IOY7ZSM"),
    (Decode-Utf8Base64 "7Iuk7KCcIO2ajOyCrCDrgrTrtoAg642w7J207YSw"),
    "This response",
    "sample articles",
    "demo project"
)

New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null

Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Test-Property {
    param(
        [object]$Object,
        [string]$Name
    )

    return $null -ne $Object -and ($Object.PSObject.Properties.Name -contains $Name)
}

function Invoke-GetText {
    param([string]$Path)

    $response = $client.GetAsync("$BaseUrl$Path").GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    Assert-True $response.IsSuccessStatusCode "GET $Path failed with HTTP $([int]$response.StatusCode): $text"
    return $text
}

function Invoke-PostJson {
    param(
        [string]$Path,
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 12 -Compress
    $content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
    $response = $client.PostAsync("$BaseUrl$Path", $content).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    Assert-True $response.IsSuccessStatusCode "POST $Path failed with HTTP $([int]$response.StatusCode): $text"
    return $text
}

function Write-Evidence {
    param(
        [string]$Name,
        [string]$Content
    )

    $path = Join-Path $EvidenceDir $Name
    [System.IO.File]::WriteAllText($path, $Content, [System.Text.Encoding]::UTF8)
    return $path
}

function Assert-NoForbiddenCopy {
    param(
        [string]$Name,
        [string]$Content
    )

    foreach ($word in $ForbiddenCopy) {
        Assert-True (-not $Content.Contains($word)) "$Name contains forbidden copy: $word"
    }
}

try {
    $provider = $null
    $reindex = $null
    $providerPath = $null
    $reindexPath = $null

    $rootHtml = Invoke-GetText "/"
    Assert-True $rootHtml.Contains($Text.RootTitle) "Root page does not contain readable Korean title."
    Assert-NoForbiddenCopy "Root page" $rootHtml

    $adminHtml = Invoke-GetText "/admin.html"
    Assert-True $adminHtml.Contains($Text.AdminTitle) "Admin page does not contain readable Korean heading."
    Assert-NoForbiddenCopy "Admin page" $adminHtml

    $healthzText = Invoke-GetText "/healthz"
    Write-Evidence "healthz.json" $healthzText | Out-Null
    Assert-NoForbiddenCopy "Liveness health" $healthzText
    $healthz = $healthzText | ConvertFrom-Json
    Assert-True ($healthz.status -eq "ok") "Expected /healthz status ok, got $($healthz.status)."

    $v1HealthText = Invoke-GetText "/api/v1/health"
    Write-Evidence "v1-health.json" $v1HealthText | Out-Null
    Assert-NoForbiddenCopy "V1 health" $v1HealthText
    $v1Health = $v1HealthText | ConvertFrom-Json
    Assert-True ($v1Health.status -eq "ok") "Expected /api/v1/health status ok, got $($v1Health.status)."
    Assert-True (Test-Property $v1Health "checks") "V1 health response is missing checks."
    Assert-True (Test-Property $v1Health.checks "db") "V1 health response is missing DB check."
    Assert-True (Test-Property $v1Health.checks "index") "V1 health response is missing index check."
    Assert-True ($v1Health.checks.db -eq "ok") "Expected DB health ok, got $($v1Health.checks.db)."
    Assert-True ($v1Health.checks.index.status -eq "healthy") "Expected healthy index in v1 health, got $($v1Health.checks.index.status)."

    $statusText = Invoke-GetText "/api/admin/status"
    Write-Evidence "admin-status.json" $statusText | Out-Null
    Assert-NoForbiddenCopy "Admin status" $statusText
    $status = $statusText | ConvertFrom-Json
    Assert-True ($status.indexStatus -eq "healthy") "Expected healthy indexStatus, got $($status.indexStatus)."
    Assert-True ([int]$status.lawsCount -ge 1) "Expected at least one law."
    Assert-True ([int]$status.articlesCount -ge 1) "Expected at least one article."
    Assert-True ($status.lastSnapshotVersion -ne "legacy-domain-sample-2026-001") "Old snapshot version is still active."

    $v1StatusText = Invoke-GetText "/api/v1/admin/status"
    Write-Evidence "v1-admin-status.json" $v1StatusText | Out-Null
    Assert-NoForbiddenCopy "V1 admin status" $v1StatusText
    $v1Status = $v1StatusText | ConvertFrom-Json
    Assert-True ($v1Status.index_status -eq "healthy") "Expected healthy v1 index_status, got $($v1Status.index_status)."
    Assert-True ([int]$v1Status.laws_count -ge 1) "Expected at least one law in v1 admin status."
    Assert-True ([int]$v1Status.articles_count -ge 1) "Expected at least one article in v1 admin status."
    Assert-True (Test-Property $v1Status "reindex_enabled") "Expected v1 admin status to expose reindex_enabled."
    Assert-True (-not (Test-Property $v1Status "indexStatus")) "V1 admin status leaked camelCase indexStatus."
    Assert-True (-not (Test-Property $v1Status "lawsCount")) "V1 admin status leaked camelCase lawsCount."

    $lawsText = Invoke-GetText "/api/laws"
    Write-Evidence "laws.json" $lawsText | Out-Null
    Assert-NoForbiddenCopy "Law list" $lawsText
    $laws = $lawsText | ConvertFrom-Json
    Assert-True ([int]$laws.total -ge 1) "Expected at least one law in /api/laws."
    $lawTitles = @($laws.items | ForEach-Object { $_.title })
    Assert-True ($lawTitles -contains $Text.ForeignTradeAct) "Expected Foreign Trade Act in law list."

    $askText = Invoke-PostJson "/api/ask" @{
        question = $Text.AskQuestion
        asOf = $null
    }
    [System.IO.File]::WriteAllText($AskResponseFile, $askText, [System.Text.Encoding]::UTF8)
    Write-Evidence "ask-response.json" $askText | Out-Null
    Assert-NoForbiddenCopy "Ask response" $askText
    Assert-True $askText.Contains($Text.ForeignTradeAct) "Ask response does not contain expected law title."
    Assert-True $askText.Contains($Text.StrategicItem) "Ask response does not contain expected query object."
    Assert-True $askText.Contains($Text.KoreanDisclaimer) "Ask response does not contain Korean disclaimer."

    $ask = $askText | ConvertFrom-Json
    Assert-True ($ask.status -eq "OK") "Expected ask status OK, got $($ask.status)."
    $cited = @($ask.citedArticles)
    Assert-True ($cited.Count -ge 1) "Expected at least one cited article."
    Assert-True (@($cited | Where-Object { $_.lawTitle -like "$($Text.ForeignTradeAct)*" }).Count -ge 1) "Expected Foreign Trade Act family citation."

    $v1AskText = Invoke-PostJson "/api/v1/ask" @{
        question = $Text.AskQuestion
        as_of = $null
    }
    Write-Evidence "v1-ask-response.json" $v1AskText | Out-Null
    Assert-NoForbiddenCopy "V1 ask response" $v1AskText
    Assert-True $v1AskText.Contains($Text.ForeignTradeAct) "V1 ask response does not contain expected law title."
    Assert-True $v1AskText.Contains($Text.StrategicItem) "V1 ask response does not contain expected query object."
    Assert-True $v1AskText.Contains($Text.KoreanDisclaimer) "V1 ask response does not contain Korean disclaimer."

    $v1Ask = $v1AskText | ConvertFrom-Json
    Assert-True ($v1Ask.status -eq "ok") "Expected v1 ask status ok, got $($v1Ask.status)."
    $v1Cited = @($v1Ask.cited_articles)
    Assert-True ($v1Cited.Count -ge 1) "Expected at least one v1 cited article."
    Assert-True (@($v1Cited | Where-Object { $_.law_title -like "$($Text.ForeignTradeAct)*" }).Count -ge 1) "Expected Foreign Trade Act family citation in v1 ask response."
    Assert-True (Test-Property $v1Ask "effective_basis") "V1 ask response is missing effective_basis."
    Assert-True (Test-Property $v1Ask.effective_basis "snapshot_version") "V1 ask response is missing effective_basis.snapshot_version."
    Assert-True (Test-Property $v1Ask "diagnostics") "V1 ask response is missing diagnostics."
    Assert-True (Test-Property $v1Ask.diagnostics "request_id") "V1 ask response is missing diagnostics.request_id."
    Assert-True (-not (Test-Property $v1Ask "citedArticles")) "V1 ask response leaked camelCase citedArticles."
    Assert-True (-not (Test-Property $v1Ask "effectiveBasis")) "V1 ask response leaked camelCase effectiveBasis."

    if ($IncludeReindexContract) {
        $reindexText = Invoke-PostJson "/api/v1/admin/reindex" @{}
        $reindexPath = Write-Evidence "v1-reindex-response.json" $reindexText
        Assert-NoForbiddenCopy "V1 reindex contract" $reindexText
        $reindex = $reindexText | ConvertFrom-Json
        Assert-True ($reindex.status -eq "accepted") "Expected v1 reindex status accepted, got $($reindex.status)."
        Assert-True (Test-Property $reindex "ingestion_run_id") "V1 reindex response is missing ingestion_run_id."
        Assert-True (Test-Property $reindex "snapshot_version") "V1 reindex response is missing snapshot_version."
        Assert-True (-not (Test-Property $reindex "ingestionRunId")) "V1 reindex response leaked camelCase ingestionRunId."
    }

    if ($IncludeProviderSmoke) {
        $providerText = Invoke-PostJson "/api/admin/provider-smoke-test" @{}
        $providerPath = Write-Evidence "provider-smoke-response.json" $providerText
        Assert-NoForbiddenCopy "Provider smoke test" $providerText
        $provider = $providerText | ConvertFrom-Json
        Assert-True ($provider.llmStatus -eq "ok") "LLM provider status is $($provider.llmStatus)."
        Assert-True ($provider.embeddingStatus -eq "ok") "Embedding provider status is $($provider.embeddingStatus)."
        Assert-True ($provider.rerankerStatus -eq "ok") "Reranker provider status is $($provider.rerankerStatus)."
    }

    $indexedArticlesCount = 0
    if (Test-Property $status "indexedArticlesCount") {
        $indexedArticlesCount = [int]$status.indexedArticlesCount
    }
    $unindexedArticlesCount = 0
    if (Test-Property $status "unindexedArticlesCount") {
        $unindexedArticlesCount = [int]$status.unindexedArticlesCount
    }

    $summary = [ordered]@{
        checkedAt = (Get-Date).ToString("o")
        baseUrl = $BaseUrl
        snapshotVersion = $status.lastSnapshotVersion
        lawsCount = [int]$status.lawsCount
        articlesCount = [int]$status.articlesCount
        indexedArticlesCount = $indexedArticlesCount
        unindexedArticlesCount = $unindexedArticlesCount
        indexStatus = $status.indexStatus
        v1IndexStatus = $v1Status.index_status
        askStatus = $ask.status
        askRequestId = $ask.diagnostics.requestId
        askCitationCount = @($ask.citedArticles).Count
        v1AskStatus = $v1Ask.status
        v1AskRequestId = $v1Ask.diagnostics.request_id
        v1AskCitationCount = @($v1Ask.cited_articles).Count
        providerSmokeIncluded = [bool]$IncludeProviderSmoke
        providerSmokeEvidence = $providerPath
        reindexContractIncluded = [bool]$IncludeReindexContract
        reindexContractEvidence = $reindexPath
        evidenceDir = $EvidenceDir
        checks = @(
            "root-page",
            "admin-page",
            "healthz",
            "v1-health",
            "admin-status",
            "v1-admin-status",
            "law-list",
            "ask",
            "v1-ask",
            "forbidden-copy",
            "v1-snake-case"
        )
    }

    $summaryPath = Write-Evidence "runtime-summary.json" ($summary | ConvertTo-Json -Depth 8)

    Write-Host "Runtime verification passed."
    Write-Host "BaseUrl=$BaseUrl"
    Write-Host "Snapshot=$($status.lastSnapshotVersion)"
    Write-Host "Laws=$($status.lawsCount) Articles=$($status.articlesCount)"
    Write-Host "AskResponse=$AskResponseFile"
    Write-Host "EvidenceDir=$EvidenceDir"
    Write-Host "RuntimeSummary=$summaryPath"
    Write-Host "V1Compatibility=checked"
} finally {
    $client.Dispose()
}
