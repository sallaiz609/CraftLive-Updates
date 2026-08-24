param(
    [string]$Alias = "craftlive",
    [string]$OutputDirectory = "signing"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null
$keystorePath = Join-Path $signingDirectory "craftlive-release.jks"

if (Test-Path $keystorePath) {
    throw "A kiadási kulcs már létezik: $keystorePath. Ne írd felül, mert a régi telepítések többé nem frissíthetők."
}

$securePassword = Read-Host "Adj meg egy erős jelszót a kiadási kulcshoz" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    & keytool -genkeypair -v -keystore $keystorePath -alias $Alias -keyalg RSA -keysize 4096 `
        -validity 10000 -storepass $password -keypass $password `
        -dname "CN=CraftLive, OU=CraftLive, O=CraftLive, L=Budapest, C=HU"
    if ($LASTEXITCODE -ne 0) { throw "A keytool hibával leállt." }
    $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
    $base64Path = Join-Path $signingDirectory "ANDROID_KEYSTORE_BASE64.txt"
    [IO.File]::WriteAllText($base64Path, $base64)
    Write-Host "Kész: $keystorePath"
    Write-Host "A GitHub ANDROID_KEYSTORE_BASE64 titkába másolandó érték: $base64Path"
    Write-Host "Őrizd meg a JKS fájlt és a jelszót több, biztonságos helyen. Soha ne töltsd fel a tárolóba."
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
