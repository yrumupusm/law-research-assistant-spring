param(
    [string]$BaseUrl = "",
    [string]$EnvFile = "",
    [string]$ScenarioFile = "$PSScriptRoot\..\harness\scenario-requests.json",
    [string]$OutputDir = "$PSScriptRoot\..\target\scenario-responses",
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$DefaultEnvFile = Join-Path $ProjectRoot ".env"

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

function Decode-Utf8Base64 {
    param([string]$Value)

    return [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Value))
}

function Test-Property {
    param(
        [object]$Object,
        [string]$Name
    )

    return $null -ne $Object -and ($Object.PSObject.Properties.Name -contains $Name)
}

function Get-Stat {
    param(
        [object]$Response,
        [string]$Name
    )

    if ($Response.diagnostics -and $Response.diagnostics.retrievalStats -and (Test-Property $Response.diagnostics.retrievalStats $Name)) {
        return [int]$Response.diagnostics.retrievalStats.$Name
    }
    return 0
}

function Test-ExpectedCitation {
    param(
        [object[]]$CitedArticles,
        [object[]]$ExpectedCitations
    )

    if ($ExpectedCitations.Count -eq 0) {
        return $true
    }

    foreach ($expected in $ExpectedCitations) {
        foreach ($article in $CitedArticles) {
            if ($article.lawTitle -eq $expected.lawTitle -and $article.articleNumber -eq $expected.articleNumber) {
                return $true
            }
        }
    }
    return $false
}

function Get-CitationSummary {
    param([object[]]$CitedArticles)

    return @($CitedArticles | ForEach-Object { "$($_.lawTitle) $($_.articleNumber)" }) -join "; "
}

function Get-HistoricalEntryCount {
    param([object[]]$CitedArticles)

    $count = 0
    foreach ($article in $CitedArticles) {
        if (Test-Property $article "historicalEntries") {
            $count += @($article.historicalEntries).Count
        }
    }
    return $count
}

function Invoke-GetJson {
    param([string]$Path)

    $httpResponse = $httpClient.GetAsync("$BaseUrl$Path").GetAwaiter().GetResult()
    $responseText = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $httpResponse.IsSuccessStatusCode) {
        throw "Request failed for GET $Path`: status=$([int]$httpResponse.StatusCode) body=$responseText"
    }
    return $responseText | ConvertFrom-Json
}

function Test-SearchLogForRequest {
    param([string]$RequestId)

    if ([string]::IsNullOrWhiteSpace($RequestId)) {
        return $false
    }
    $logs = Invoke-GetJson "/api/admin/search-logs"
    return @($logs.items | Where-Object { $_.requestId -eq $RequestId }).Count -ge 1
}

function Test-AgentTraceForRequest {
    param([string]$RequestId)

    if ([string]::IsNullOrWhiteSpace($RequestId)) {
        return $false
    }

    $encoded = [System.Uri]::EscapeDataString($RequestId)
    $traceResponse = Invoke-GetJson "/api/admin/agent-traces?requestId=$encoded"
    $steps = @($traceResponse.items | ForEach-Object { $_.stepName })
    $expectedSteps = @(
        "QueryAnalyzerAgent",
        "RetrievalAgent",
        "EvidenceValidatorAgent",
        "AnswerWriterAndCritic"
    )

    if ($steps.Count -lt $expectedSteps.Count) {
        return $false
    }
    for ($i = 0; $i -lt $expectedSteps.Count; $i++) {
        if ($steps[$i] -ne $expectedSteps[$i]) {
            return $false
        }
    }
    return $true
}

function Test-NoForbiddenCopy {
    param([string]$Text)

    $forbidden = @(
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

    foreach ($word in $forbidden) {
        if ($Text.Contains($word)) {
            return $word
        }
    }
    return $null
}

if (-not (Test-Path -LiteralPath $ScenarioFile)) {
    throw "Scenario file not found: $ScenarioFile"
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$scenarioText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $ScenarioFile).Path, [System.Text.Encoding]::UTF8)
$scenarios = $scenarioText | ConvertFrom-Json
$passed = 0
$failed = 0
$summaryItems = [System.Collections.Generic.List[object]]::new()
$summaryPath = Join-Path $OutputDir "scenario-summary.json"
$httpClient = [System.Net.Http.HttpClient]::new()
$httpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)

try {
    foreach ($scenario in $scenarios) {
        $body = $scenario.request | ConvertTo-Json -Depth 12 -Compress
        $content = [System.Net.Http.StringContent]::new($body, $Utf8NoBom, "application/json")
        $httpResponse = $httpClient.PostAsync("$BaseUrl/api/ask", $content).GetAwaiter().GetResult()
        $responseText = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()

        $responsePath = Join-Path $OutputDir "$($scenario.id).json"
        [System.IO.File]::WriteAllText($responsePath, $responseText, $Utf8NoBom)

        if (-not $httpResponse.IsSuccessStatusCode) {
            throw "Request failed for $($scenario.id): status=$([int]$httpResponse.StatusCode) body=$responseText"
        }

        $response = $responseText | ConvertFrom-Json

        $citedArticles = @($response.citedArticles)
        $requestId = $response.diagnostics.requestId
        $statusOk = $response.status -eq $scenario.expected.status
        $citationCount = $citedArticles.Count
        $citationOk = $citationCount -ge [int]$scenario.expected.minCitedArticles
        $historicalEntryCount = Get-HistoricalEntryCount $citedArticles
        $keywordHits = Get-Stat $response "keywordHits"
        $vectorHits = Get-Stat $response "vectorHits"
        $retrieved = Get-Stat $response "retrieved"
        $cited = Get-Stat $response "cited"
        $mergedHits = Get-Stat $response "mergedHits"

        $vectorOk = $true
        if (Test-Property $scenario.expected "minVectorHits") {
            $vectorOk = $vectorHits -ge [int]$scenario.expected.minVectorHits
        }
        if (Test-Property $scenario.expected "maxVectorHits") {
            $vectorOk = $vectorOk -and ($vectorHits -le [int]$scenario.expected.maxVectorHits)
        }

        $lawOk = $true
        if (Test-Property $scenario.expected "mustContainLawTitle") {
            $lawTitles = @($citedArticles | ForEach-Object { $_.lawTitle })
            $lawOk = $lawTitles -contains $scenario.expected.mustContainLawTitle
        }

        $expectedCitations = @()
        if (Test-Property $scenario.expected "expectedCitations") {
            $expectedCitations = @($scenario.expected.expectedCitations)
        }
        $citationPairOk = Test-ExpectedCitation $citedArticles $expectedCitations

        $historyOk = $true
        if (Test-Property $scenario.expected "minHistoricalEntries") {
            $historyOk = $historicalEntryCount -ge [int]$scenario.expected.minHistoricalEntries
        }

        $forbiddenCopy = Test-NoForbiddenCopy $responseText
        $copyOk = $null -eq $forbiddenCopy
        $requestIdOk = -not [string]::IsNullOrWhiteSpace($requestId)
        $searchLogOk = Test-SearchLogForRequest $requestId
        $traceOk = Test-AgentTraceForRequest $requestId

        $ok = $statusOk -and $citationOk -and $lawOk -and $vectorOk -and $citationPairOk -and $historyOk -and $copyOk -and $requestIdOk -and $searchLogOk -and $traceOk
        $summaryItems.Add([ordered]@{
            id = $scenario.id
            ok = [bool]$ok
            responsePath = $responsePath
            status = $response.status
            expectedStatus = $scenario.expected.status
            citationCount = [int]$citationCount
            expectedMinCitations = [int]$scenario.expected.minCitedArticles
            historicalEntryCount = [int]$historicalEntryCount
            keywordHits = [int]$keywordHits
            vectorHits = [int]$vectorHits
            retrieved = [int]$retrieved
            cited = [int]$cited
            mergedHits = [int]$mergedHits
            requestId = $requestId
            searchLogOk = [bool]$searchLogOk
            traceOk = [bool]$traceOk
            forbiddenCopy = $forbiddenCopy
            statusOk = [bool]$statusOk
            citationOk = [bool]$citationOk
            vectorOk = [bool]$vectorOk
            lawOk = [bool]$lawOk
            citationPairOk = [bool]$citationPairOk
            historyOk = [bool]$historyOk
        }) | Out-Null

        if ($ok) {
            $passed++
            Write-Host "[PASS] $($scenario.id) status=$($response.status) citations=$citationCount historicalEntries=$historicalEntryCount vectorHits=$vectorHits requestId=$requestId response=$responsePath"
        } else {
            $failed++
            Write-Host "[FAIL] $($scenario.id) status=$($response.status) citations=$citationCount historicalEntries=$historicalEntryCount keywordHits=$keywordHits vectorHits=$vectorHits retrieved=$retrieved cited=$cited mergedHits=$mergedHits"
            Write-Host "       response=$responsePath"
            Write-Host "       expectedStatus=$($scenario.expected.status) minCitations=$($scenario.expected.minCitedArticles)"
            if (Test-Property $scenario.expected "minVectorHits") {
                Write-Host "       minVectorHits=$($scenario.expected.minVectorHits)"
            }
            if (Test-Property $scenario.expected "maxVectorHits") {
                Write-Host "       maxVectorHits=$($scenario.expected.maxVectorHits)"
            }
            if (Test-Property $scenario.expected "mustContainLawTitle") {
                Write-Host "       expectedLaw=$($scenario.expected.mustContainLawTitle)"
            }
            if ($expectedCitations.Count -gt 0) {
                Write-Host "       actualCitations=$(Get-CitationSummary $citedArticles)"
            }
            if (Test-Property $scenario.expected "minHistoricalEntries") {
                Write-Host "       minHistoricalEntries=$($scenario.expected.minHistoricalEntries)"
            }
            if (-not $copyOk) {
                Write-Host "       forbiddenCopy=$forbiddenCopy"
            }
            if (-not $requestIdOk) {
                Write-Host "       diagnostics.requestId is missing"
            }
            if (-not $searchLogOk) {
                Write-Host "       search log not found for requestId=$requestId"
            }
            if (-not $traceOk) {
                Write-Host "       agent trace sequence invalid for requestId=$requestId"
            }
            Write-Host "       reasoning=$($response.reasoning)"
        }
    }

    $summary = [ordered]@{
        checkedAt = (Get-Date).ToString("o")
        baseUrl = $BaseUrl
        scenarioFile = (Resolve-Path -LiteralPath $ScenarioFile).Path
        outputDir = (Resolve-Path -LiteralPath $OutputDir).Path
        total = @($scenarios).Count
        passed = [int]$passed
        failed = [int]$failed
        items = @($summaryItems)
    }
    [System.IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 8), $Utf8NoBom)
    Write-Host "Scenario result: passed=$passed failed=$failed outputDir=$OutputDir"
    Write-Host "Scenario summary: $summaryPath"
    if ($failed -gt 0) {
        exit 1
    }
} finally {
    $httpClient.Dispose()
}
