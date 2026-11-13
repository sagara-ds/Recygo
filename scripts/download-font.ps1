$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$fontDir = Join-Path $projectRoot 'app/src/main/assets/fonts'
New-Item -ItemType Directory -Force -Path $fontDir | Out-Null
& curl.exe --fail --silent --show-error --location https://raw.githubusercontent.com/rsms/inter/master/docs/font-files/InterVariable.ttf --output (Join-Path $fontDir 'InterVariable.ttf')
if ($LASTEXITCODE -ne 0) { throw 'Inter font download failed.' }
& curl.exe --fail --silent --show-error --location https://raw.githubusercontent.com/rsms/inter/master/LICENSE.txt --output (Join-Path $fontDir 'OFL.txt')
if ($LASTEXITCODE -ne 0) { throw 'Inter license download failed.' }
Write-Output 'Inter font and SIL Open Font License downloaded.'
