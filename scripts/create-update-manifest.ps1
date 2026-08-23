param(
    [Parameter(Mandatory = $true)][string]$InstallerPath,
    [Parameter(Mandatory = $true)][string]$DownloadUrl,
    [string]$NotesHu = "Új funkciók és javítások.",
    [string]$NotesEn = "New features and fixes.",
    [string]$FeaturesJsonPath = "",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Package = Get-Content (Join-Path $ProjectRoot "package.json") -Raw | ConvertFrom-Json
$ResolvedInstaller = (Resolve-Path $InstallerPath).Path

if (-not $DownloadUrl.StartsWith("https://")) {
    throw "A DownloadUrl must use HTTPS."
}

$Features = @()
if ($FeaturesJsonPath) {
    $Features = @(Get-Content (Resolve-Path $FeaturesJsonPath) -Raw | ConvertFrom-Json)
}

$Manifest = [ordered]@{
    version = $Package.version
    downloadUrl = $DownloadUrl
    sha256 = (Get-FileHash -LiteralPath $ResolvedInstaller -Algorithm SHA256).Hash.ToLowerInvariant()
    notesHu = $NotesHu
    notesEn = $NotesEn
    features = $Features
}

if (-not $OutputPath) { $OutputPath = Join-Path $ProjectRoot "latest.json" }
$Json = $Manifest | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText($OutputPath, $Json, (New-Object Text.UTF8Encoding($false)))
Write-Host "Update manifest created:" -ForegroundColor Green
Write-Host (Resolve-Path $OutputPath).Path -ForegroundColor Cyan
