[CmdletBinding()]
param(
    [string]$SdkRoot = "",
    [string]$Serial = "",
    [string]$ApkPath = "",
    [string]$JavaHome = "",
    [switch]$SkipBuild,
    [switch]$RunInstrumentedTests
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
if (-not $ApkPath) {
    $ApkPath = Join-Path $RepoRoot "dist\MusicClock-1.1.0-debug.apk"
}

$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$PackageName = "com.musicclock.alarm"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutputDir = Join-Path $RepoRoot "docs\artifacts\device\$Timestamp"
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_USER_HOME = Join-Path $RepoRoot ".tooling\android-user"
$env:HOME = Join-Path $RepoRoot ".tooling\home"

if (-not (Test-Path -LiteralPath $Adb)) {
    throw "adb not found at $Adb"
}

$deviceLines = & $Adb devices -l |
    Select-Object -Skip 1 |
    Where-Object { $_ -match "\sdevice(\s|$)" -and $_ -notmatch "^emulator-" }
if (-not $Serial) {
    if ($deviceLines.Count -ne 1) {
        throw "Connect exactly one physical Android device or pass -Serial. Found $($deviceLines.Count)."
    }
    $Serial = ($deviceLines[0] -split "\s+")[0]
}

$selected = & $Adb devices -l | Select-String -Pattern "^$([regex]::Escape($Serial))\s+device"
if (-not $selected) {
    throw "Physical device $Serial is not connected and authorized."
}
if ($Serial -match "^emulator-") {
    throw "Hardware acceptance requires a physical device, not $Serial."
}
$env:ANDROID_SERIAL = $Serial

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Get-DeviceProperty {
    param([string]$Name)
    return (& $Adb -s $Serial shell getprop $Name).Trim()
}

function Save-CommandOutput {
    param(
        [string]$FileName,
        [string[]]$Command
    )
    & $Adb -s $Serial @Command |
        Set-Content -LiteralPath (Join-Path $OutputDir $FileName) -Encoding utf8NoBOM
}

$manufacturer = Get-DeviceProperty "ro.product.manufacturer"
$model = Get-DeviceProperty "ro.product.model"
$androidVersion = Get-DeviceProperty "ro.build.version.release"
$sdk = Get-DeviceProperty "ro.build.version.sdk"
$fingerprint = Get-DeviceProperty "ro.build.fingerprint"
$apiLevel = [int]$sdk

if (-not $SkipBuild) {
    & $Gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Build/test verification failed."
    }
}

if ($RunInstrumentedTests) {
    & $Gradle :app:connectedDebugAndroidTest
    if ($LASTEXITCODE -ne 0) {
        throw "Connected tests failed on the physical device."
    }
}

& $Adb -s $Serial logcat -c
& $Adb -s $Serial install -r $ApkPath | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Could not install $ApkPath"
}

if ($apiLevel -ge 33) {
    & $Adb -s $Serial shell pm grant $PackageName android.permission.POST_NOTIFICATIONS |
        Out-Null
}
& $Adb -s $Serial shell cmd appops set $PackageName SCHEDULE_EXACT_ALARM allow |
    Out-Null
& $Adb -s $Serial shell cmd appops set $PackageName USE_FULL_SCREEN_INTENT allow |
    Out-Null
& $Adb -s $Serial shell am force-stop $PackageName | Out-Null
& $Adb -s $Serial shell am start -W -n "$PackageName/.ui.MainActivity" | Out-Null
Start-Sleep -Seconds 3

$remoteScreenshot = "/sdcard/musicclock-device-home.png"
& $Adb -s $Serial shell screencap -p $remoteScreenshot
& $Adb -s $Serial pull $remoteScreenshot (Join-Path $OutputDir "home.png") | Out-Null
& $Adb -s $Serial logcat -d -t 5000 |
    Set-Content -LiteralPath (Join-Path $OutputDir "logcat.txt") -Encoding utf8NoBOM

Save-CommandOutput "appops.txt" @("shell", "cmd", "appops", "get", $PackageName)
Save-CommandOutput "audio-devices.txt" @("shell", "dumpsys", "audio")
Save-CommandOutput "power.txt" @("shell", "dumpsys", "deviceidle", "whitelist")

$summary = @(
    "# Physical Device Acceptance Session",
    "",
    "- Serial: ``$Serial``",
    "- Manufacturer: ``$manufacturer``",
    "- Model: ``$model``",
    "- Android: ``$androidVersion`` / API ``$sdk``",
    "- Fingerprint: ``$fingerprint``",
    "- APK: ``$ApkPath``",
    "- Captured: $Timestamp",
    "",
    "## Required Manual Cases",
    "",
    "| ID | Result | Notes |",
    "|---|---|---|",
    "| AUD-BT-01 | NOT RUN | Bluetooth connected: audio only in headset, vibration works |",
    "| AUD-BT-02 | NOT RUN | Disconnect during ring: audio stops, no sustained speaker output |",
    "| AUD-WIRE-01 | NOT RUN | Wired connected: audio only in headset |",
    "| AUD-WIRE-02 | NOT RUN | Disconnect during ring: audio stops |",
    "| AUD-NOHEADSET | NOT RUN | Default alarm output and vibration |",
    "| SNOOZE | NOT RUN | Re-trigger after approximately five minutes |",
    "| QUEUE | NOT RUN | Two close alarms play sequentially |",
    "| FILE | NOT RUN | Local audio import and playback |",
    "| REBOOT | NOT RUN | Alarm survives reboot |",
    "| DND-SILENT | NOT RUN | Record system behavior and limitations |",
    "",
    "Artifacts are in this directory. Mark every hardware case manually; do not infer audio routing from emulator results."
)
$summary | Set-Content -LiteralPath (Join-Path $OutputDir "summary.md") -Encoding utf8NoBOM

Write-Output "Physical device prepared: $manufacturer $model, Android $androidVersion (API $sdk)"
Write-Output "Acceptance directory: $OutputDir"
Write-Output "Complete summary.md while testing Bluetooth and wired headsets."
