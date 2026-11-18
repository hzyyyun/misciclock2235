[CmdletBinding()]
param(
    [string]$SdkRoot = "",
    [string]$AvdName = "musicclock_api35",
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

if (-not $SdkRoot) {
    $SdkRoot = if ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } else {
        Join-Path $RepoRoot ".tooling\android-sdk"
    }
}
if (-not $JavaHome) {
    $JavaHome = if ($env:JAVA_HOME) {
        $env:JAVA_HOME
    } else {
        Join-Path $RepoRoot ".tooling\jdk\jdk17.0.21_1"
    }
}

$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$SdkManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$AvdManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_USER_HOME = Join-Path $RepoRoot ".tooling\android-user"
$env:ANDROID_AVD_HOME = Join-Path $RepoRoot ".tooling\avd"
$env:HOME = Join-Path $RepoRoot ".tooling\home"

if (-not (Test-Path -LiteralPath $SdkManager)) {
    throw "Android command-line tools not found at $SdkManager"
}

New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME, $env:ANDROID_AVD_HOME, $env:HOME |
    Out-Null

& $SdkManager --sdk_root=$SdkRoot "emulator" "system-images;android-35;google_apis;x86_64"
if ($LASTEXITCODE -ne 0) {
    throw "SDK installation failed. Run sdkmanager --licenses manually and retry."
}

$installedAvds = & $AvdManager list avd
if ($installedAvds -notmatch "Name:\s+$([regex]::Escape($AvdName))") {
    "no" | & $AvdManager create avd `
        --force `
        --name $AvdName `
        --package "system-images;android-35;google_apis;x86_64" `
        --device "pixel_6"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create AVD $AvdName."
    }
}

Write-Output "Emulator ready: $AvdName"
