$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsDirectory = Join-Path $ProjectRoot ".tools"
$OutputDirectory = Join-Path $ProjectRoot "KESZ"
$InstallLog = Join-Path $ProjectRoot "build-install.log"
$PackageLog = Join-Path $ProjectRoot "build-package.log"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Green
}

function Get-NpmCommand {
    try {
        $NodeVersion = (& node --version 2>$null)
        if ($NodeVersion -match '^v(\d+)\.(\d+)\.') {
            $NodeMajor = [int]$Matches[1]
            $NodeMinor = [int]$Matches[2]
            if ($NodeMajor -gt 22 -or ($NodeMajor -eq 22 -and $NodeMinor -ge 12)) {
                return "npm.cmd"
            }
        }
    } catch {}

    Write-Step "Downloading portable Node.js LTS"
    New-Item -ItemType Directory -Force -Path $ToolsDirectory | Out-Null
    $Index = Invoke-RestMethod -Uri "https://nodejs.org/dist/index.json"
    $Release = $Index | Where-Object {
        $_.lts -and $_.version -match '^v(\d+)\.(\d+)\.' -and
            ([int]$Matches[1] -gt 22 -or ([int]$Matches[1] -eq 22 -and [int]$Matches[2] -ge 12))
    } | Select-Object -First 1
    if (-not $Release) { throw "A compatible Node.js LTS release was not found." }

    $Version = $Release.version
    $ArchiveName = "node-$Version-win-x64.zip"
    $ArchivePath = Join-Path $ToolsDirectory $ArchiveName
    $NodeDirectory = Join-Path $ToolsDirectory "node-$Version-win-x64"
    if (-not (Test-Path $NodeDirectory)) {
        Invoke-WebRequest -Uri "https://nodejs.org/dist/$Version/$ArchiveName" -OutFile $ArchivePath
        Expand-Archive -Path $ArchivePath -DestinationPath $ToolsDirectory -Force
    }

    $env:PATH = "$NodeDirectory;$env:PATH"
    return (Join-Path $NodeDirectory "npm.cmd")
}

function Run-Npm([string[]]$Arguments, [string]$LogPath, [string]$FailureMessage) {
    $PreviousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5 turns harmless native stderr text (for example
        # "npm notice") into NativeCommandError when Stop is active. Let npm
        # finish, convert every line to plain text, then trust its exit code.
        $ErrorActionPreference = "Continue"
        & $script:NpmCommand @Arguments 2>&1 |
            ForEach-Object { $_.ToString() } |
            Tee-Object -FilePath $LogPath
        $ExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $PreviousErrorActionPreference
    }

    if ($ExitCode -ne 0) {
        Write-Host ""
        Write-Host "Last lines from the log:" -ForegroundColor Yellow
        Get-Content $LogPath -Tail 35 | Write-Host
        throw "$FailureMessage Details: $LogPath"
    }
}

New-Item -ItemType Directory -Force -Path $ToolsDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$env:npm_config_cache = Join-Path $ToolsDirectory "npm-cache"
$env:npm_config_registry = "https://registry.npmjs.org/"
$env:npm_config_audit = "false"
$env:npm_config_fund = "false"
$env:npm_config_update_notifier = "false"
$env:ELECTRON_CACHE = Join-Path $ToolsDirectory "electron-cache"
$env:electron_config_cache = $env:ELECTRON_CACHE

$script:NpmCommand = Get-NpmCommand

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw "The .NET 8 SDK is required to build the native Minecraft input helper. Install it from https://dotnet.microsoft.com/download/dotnet/8.0"
}

Write-Step "Installing application packages"
Push-Location $ProjectRoot
try {
    Run-Npm @("ci", "--no-audit", "--no-fund") $InstallLog "Package installation failed."

    Write-Step "Creating CraftLive for Windows (NO JAR)"
    Run-Npm @("run", "dist:win") $PackageLog "Windows packaging failed."
} finally {
    Pop-Location
}

$Package = Get-Content (Join-Path $ProjectRoot "package.json") -Raw | ConvertFrom-Json
$InstallerPath = Join-Path $OutputDirectory "CraftLive-Setup-$($Package.version).exe"
$UpdateInfoPath = Join-Path $OutputDirectory "latest.yml"
$BlockMapPath = "$InstallerPath.blockmap"
if (-not (Test-Path $InstallerPath)) { throw "The completed CraftLive installer was not found: $InstallerPath" }
if (-not (Test-Path $UpdateInfoPath)) { throw "The auto-update metadata was not found: $UpdateInfoPath" }
if (-not (Test-Path $BlockMapPath)) { throw "The update block map was not found: $BlockMapPath" }

Copy-Item (Join-Path $ProjectRoot "TELEPITES.txt") (Join-Path $OutputDirectory "TELEPITES.txt") -Force

Write-Host ""
Write-Host "DONE! The Windows installer and update files are here:" -ForegroundColor Green
Write-Host $InstallerPath -ForegroundColor Cyan
Write-Host $UpdateInfoPath -ForegroundColor Cyan
Write-Host $BlockMapPath -ForegroundColor Cyan
Write-Host "Run the Setup EXE once. Future releases are downloaded and restarted by the installed application."
Write-Host ""
Read-Host "Press Enter to close"
