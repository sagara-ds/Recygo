$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw 'Run scripts/setup-demo.ps1 first.' }
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z_][A-Z_0-9]*)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process') }
}
$launcher = Join-Path $projectRoot 'backend/build/install/backend/bin/backend.bat'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'Run .\gradlew.bat :backend:installDist first.' }
Push-Location $projectRoot
try { & $launcher; exit $LASTEXITCODE } finally { Pop-Location }
