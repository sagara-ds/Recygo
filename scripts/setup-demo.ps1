$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
function New-DemoPassword {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
}
if (Test-Path -LiteralPath $envPath) {
    $existing = [IO.File]::ReadAllLines($envPath)
    if (-not ($existing | Where-Object { $_ -match '^OTP_SIGNING_KEY=' })) {
        [IO.File]::AppendAllText($envPath,([Environment]::NewLine + 'OTP_SIGNING_KEY=' + (New-DemoPassword) + [Environment]::NewLine),(New-Object Text.UTF8Encoding($false)))
        Write-Output 'Added local OTP/profile encryption key; existing credentials left unchanged.'
    } else { Write-Output '.env already exists; credentials and encryption key left unchanged.' }
    exit 0
}
$residentPassword = New-DemoPassword
$collectorPassword = New-DemoPassword
$dataDir = Join-Path $projectRoot 'backend/data'
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$values = @('HOST=127.0.0.1','PORT=8080','DATA_DIR=backend/data','DEMO_MODE=true',"DEMO_RESIDENT_PASSWORD=$residentPassword","DEMO_COLLECTOR_PASSWORD=$collectorPassword",('OTP_SIGNING_KEY=' + (New-DemoPassword)))
[IO.File]::WriteAllLines($envPath,$values,(New-Object Text.UTF8Encoding($false)))
$accounts = @(
    @{ role='Warga'; email='warga@recygo.demo'; password=$residentPassword },
    @{ role='Pengepul'; email='pengepul@recygo.demo'; password=$collectorPassword }
)
$accountPath = Join-Path $dataDir 'demo-accounts.json'
[IO.File]::WriteAllText($accountPath,($accounts | ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
Write-Output 'Demo credentials generated locally. Passwords were not printed.'
Write-Output "Open this ignored local file for the two accounts: $accountPath"
