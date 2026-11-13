$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$assetDir = Join-Path $projectRoot 'app/src/main/assets/figma'
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
$manifest = Get-Content -Raw (Join-Path $projectRoot 'docs/figma-assets.json') | ConvertFrom-Json
foreach ($asset in $manifest) {
    $destination = Join-Path $assetDir $asset.file
    if (-not (Test-Path -LiteralPath $destination)) {
        & curl.exe --fail --silent --show-error --location $asset.url --output $destination
        if ($LASTEXITCODE -ne 0) { throw "Asset download failed: $($asset.file)" }
    }
    if ((Get-Item -LiteralPath $destination).Length -eq 0) { throw "Empty asset: $($asset.file)" }
}
Write-Output "Verified $($manifest.Count) local Figma assets."
