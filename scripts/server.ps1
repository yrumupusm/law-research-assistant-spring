param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "restart", "status")]
    [string]$Action = "start"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$TargetDir = Join-Path $ProjectRoot "target"
$EnvFile = Join-Path $ProjectRoot ".env"
$PidFile = Join-Path $TargetDir "server.pid"
$OutLog = Join-Path $TargetDir "bootrun.out.log"
$ErrLog = Join-Path $TargetDir "bootrun.err.log"
$MavenCmd = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "mvn.cmd" }

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

$serverPortValue = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { Get-EnvFileValue -Path $EnvFile -Name "SERVER_PORT" }
if ([string]::IsNullOrWhiteSpace($serverPortValue)) {
    $serverPortValue = "8080"
}
$ServerPort = [int]$serverPortValue
$BaseUrl = "http://localhost:$ServerPort"
$StatusUrl = "$BaseUrl/api/admin/status"
$HealthzUrl = "$BaseUrl/healthz"
$V1HealthUrl = "$BaseUrl/api/v1/health"

function Get-ServerPid {
    if (-not (Test-Path -LiteralPath $PidFile)) {
        return $null
    }

    $pidText = (Get-Content -LiteralPath $PidFile -Raw).Trim()
    $serverPid = 0
    if ([int]::TryParse($pidText, [ref]$serverPid)) {
        return $serverPid
    }

    return $null
}

function Clear-PidFile {
    if (-not (Test-Path -LiteralPath $PidFile)) {
        return
    }

    try {
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction Stop
    } catch {
        Set-Content -LiteralPath $PidFile -Value "" -Encoding ASCII
    }
}

function Test-ProcessRunning {
    param([int]$ProcessId)

    try {
        $process = Get-Process -Id $ProcessId -ErrorAction Stop
        return -not $process.HasExited
    } catch {
        return $false
    }
}

function Get-PortProcessIds {
    param([int]$Port)

    $lines = @(netstat -ano | Select-String -Pattern ":$Port\s+.*LISTENING\s+(\d+)")
    foreach ($line in $lines) {
        $match = [regex]::Match($line.Line, "LISTENING\s+(\d+)\s*$")
        if ($match.Success) {
            [int]$match.Groups[1].Value
        }
    }
}

function Stop-ProcessIds {
    param([int[]]$ProcessIds)

    foreach ($processId in @($ProcessIds | Where-Object { $_ -gt 0 } | Select-Object -Unique)) {
        if ($processId -eq $PID) {
            continue
        }
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
}

function Wait-PortReleased {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 4
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $remaining = @(Get-PortProcessIds -Port $Port | Select-Object -Unique)
        if ($remaining.Count -eq 0) {
            return $true
        }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)

    return $false
}

function ConvertTo-PowerShellSingleQuotedLiteral {
    param([string]$Value)

    return "'" + ($Value -replace "'", "''") + "'"
}

function Copy-SanitizedEnvironment {
    param([System.Diagnostics.ProcessStartInfo]$StartInfo)

    if ($null -ne $StartInfo.Environment) {
        $environment = $StartInfo.Environment
    } else {
        $environment = $StartInfo.EnvironmentVariables
    }

    $environment.Clear()
    $pathValue = $null
    $seenKeys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($entry in [System.Environment]::GetEnvironmentVariables().GetEnumerator()) {
        $key = [string]$entry.Key
        $value = [string]$entry.Value

        if ($key -ieq "PATH") {
            if ($null -eq $pathValue -or $key -ceq "Path") {
                $pathValue = $value
            }
            continue
        }

        if ($seenKeys.Add($key)) {
            $environment[$key] = $value
        }
    }

    if ($null -ne $pathValue) {
        $environment["Path"] = $pathValue
    }
}

function Test-HttpEndpoint {
    param([string]$Url)

    try {
        Add-Type -AssemblyName System.Net.Http
        $httpClient = [System.Net.Http.HttpClient]::new()
        $httpClient.Timeout = [TimeSpan]::FromSeconds(2)
        try {
            $response = $httpClient.GetAsync($Url).GetAwaiter().GetResult()
            return [pscustomobject]@{
                Available = $true
                StatusCode = [int]$response.StatusCode
                Success = $response.IsSuccessStatusCode
            }
        } finally {
            $httpClient.Dispose()
        }
    } catch {
        return [pscustomobject]@{
            Available = $false
            StatusCode = $null
            Success = $false
        }
    }
}

function Start-Server {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

    $existingPid = Get-ServerPid
    $portProcessIds = @(Get-PortProcessIds -Port $ServerPort | Select-Object -Unique)

    if ($null -ne $existingPid -and (Test-ProcessRunning -ProcessId $existingPid)) {
        Write-Host "Server is already running or starting. pid=$existingPid port=$ServerPort url=$BaseUrl"
        return
    }

    if ($portProcessIds.Count -gt 0) {
        Write-Host "Server is already listening on port $ServerPort. listenerPid=$($portProcessIds -join ',') url=$BaseUrl"
        return
    }

    Clear-PidFile

    if (-not (Test-Path -LiteralPath $MavenCmd)) {
        throw "Maven command not found: $MavenCmd"
    }

    $projectRootLiteral = ConvertTo-PowerShellSingleQuotedLiteral -Value $ProjectRoot
    $mavenCmdLiteral = ConvertTo-PowerShellSingleQuotedLiteral -Value $MavenCmd
    $outLogLiteral = ConvertTo-PowerShellSingleQuotedLiteral -Value $OutLog
    $errLogLiteral = ConvertTo-PowerShellSingleQuotedLiteral -Value $ErrLog

    $runnerScript = @"
`$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $projectRootLiteral
& $mavenCmdLiteral "spring-boot:run" 1>> $outLogLiteral 2>> $errLogLiteral
exit `$LASTEXITCODE
"@
    $encodedRunnerScript = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($runnerScript))

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = Join-Path $PSHOME "powershell.exe"
    $startInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -EncodedCommand $encodedRunnerScript"
    $startInfo.WorkingDirectory = $ProjectRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    Copy-SanitizedEnvironment -StartInfo $startInfo

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Failed to start server."
    }

    Set-Content -LiteralPath $PidFile -Value $process.Id -Encoding ASCII
    Write-Host "Server launch requested. pid=$($process.Id) port=$ServerPort url=$BaseUrl"
    Write-Host "Admin status: $StatusUrl"
    Write-Host "stdout: $OutLog"
    Write-Host "stderr: $ErrLog"
}

function Stop-Server {
    $serverPid = Get-ServerPid
    $portProcessIds = @(Get-PortProcessIds -Port $ServerPort | Select-Object -Unique)
    $processIds = @()

    if ($null -ne $serverPid -and (Test-ProcessRunning -ProcessId $serverPid)) {
        $processIds += $serverPid
    }

    $processIds += $portProcessIds

    if ($processIds.Count -eq 0) {
        Clear-PidFile
        Write-Host "Server is not running. port=$ServerPort"
        return
    }

    Stop-ProcessIds -ProcessIds $processIds
    $released = Wait-PortReleased -Port $ServerPort -TimeoutSeconds 4
    Clear-PidFile

    if ($released) {
        Write-Host "Server stopped. port=$ServerPort stoppedPid=$($processIds -join ',')"
    } else {
        $remaining = @(Get-PortProcessIds -Port $ServerPort | Select-Object -Unique)
        Write-Host "Stop requested, but port is still listening. port=$ServerPort remainingPid=$($remaining -join ',')"
    }
}

function Show-Status {
    $serverPid = Get-ServerPid
    $portProcessIds = @(Get-PortProcessIds -Port $ServerPort | Select-Object -Unique)
    $adminStatus = Test-HttpEndpoint -Url $StatusUrl
    $healthzStatus = Test-HttpEndpoint -Url $HealthzUrl
    $v1HealthStatus = Test-HttpEndpoint -Url $V1HealthUrl
    $adminText = if ($adminStatus.Available) { "adminStatusHttp=$($adminStatus.StatusCode)" } else { "adminStatusHttp=unavailable" }
    $healthzText = if ($healthzStatus.Available) { "healthzHttp=$($healthzStatus.StatusCode)" } else { "healthzHttp=unavailable" }
    $v1HealthText = if ($v1HealthStatus.Available) { "v1HealthHttp=$($v1HealthStatus.StatusCode)" } else { "v1HealthHttp=unavailable" }
    $healthText = "$adminText $healthzText $v1HealthText"

    if ($null -ne $serverPid -and (Test-ProcessRunning -ProcessId $serverPid)) {
        Write-Host "Server is running or starting. pid=$serverPid port=$ServerPort listenerPid=$($portProcessIds -join ',') url=$BaseUrl $healthText"
        return
    }

    if ($portProcessIds.Count -gt 0) {
        Write-Host "Server is listening without a live pid file. port=$ServerPort listenerPid=$($portProcessIds -join ',') url=$BaseUrl $healthText"
        return
    }

    if ($null -ne $serverPid) {
        Write-Host "Server pid file is stale. stalePid=$serverPid port=$ServerPort $healthText"
        return
    }

    Write-Host "Server is not running. port=$ServerPort url=$BaseUrl $healthText"
}

switch ($Action) {
    "start" { Start-Server }
    "stop" { Stop-Server }
    "restart" {
        Stop-Server
        Start-Server
    }
    "status" { Show-Status }
}
