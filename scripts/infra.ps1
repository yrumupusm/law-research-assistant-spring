param(
    [Parameter(Position = 0)]
    [ValidateSet("up", "down", "restart", "status")]
    [string]$Action = "up"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

function Invoke-Compose {
    param([string[]]$Arguments)

    docker compose --project-directory $ProjectRoot @Arguments
}

switch ($Action) {
    "up" {
        Invoke-Compose @("up", "-d", "postgres", "qdrant")
    }
    "down" {
        Invoke-Compose @("down")
    }
    "restart" {
        Invoke-Compose @("restart", "postgres", "qdrant")
    }
    "status" {
        Invoke-Compose @("ps")
    }
}
