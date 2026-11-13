param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $listener) { Write-Output 'No demo API listener found.'; exit 0 }
$serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
if ($serverProcess.Name -ne 'java.exe' -or $serverProcess.CommandLine -notlike '*id.recygo.backend.MainKt*' -or $serverProcess.CommandLine.IndexOf($projectRoot,[StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'The port is owned by a different process; it was not stopped.'
}
Stop-Process -Id $listener.OwningProcess
Write-Output 'Stopped the Recygo demo API from this workspace.'
