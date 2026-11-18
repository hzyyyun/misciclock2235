[CmdletBinding()]
param(
    [string]$SdkRoot = "",
    [string]$AvdName = "musicclock_api35",
    [string]$ApkPath = "",
    [string]$Serial = "emulator-5554",
    [string]$JavaHome = "",
    [switch]$SkipBuild,
    [switch]$StopEmulator
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
    $ApkPath = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
}

$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$ApkPath = [System.IO.Path]::GetFullPath($ApkPath)
$Adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$Emulator = Join-Path $SdkRoot "emulator\emulator.exe"
$Gradle = Join-Path $RepoRoot "gradlew.bat"
$ArtifactRoot = Join-Path $RepoRoot "docs\artifacts\emulator"
$TestApk = Join-Path $RepoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$RemoteAcceptance = "/sdcard/Android/data/com.musicclock.alarm/files/acceptance"
$RemoteScreenshot = "/sdcard/musicclock-acceptance.png"
$PackageName = "com.musicclock.alarm"
$TestPackage = "com.musicclock.alarm.test"
$Runner = "$TestPackage/androidx.test.runner.AndroidJUnitRunner"

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_USER_HOME = Join-Path $RepoRoot ".tooling\android-user"
$env:ANDROID_AVD_HOME = Join-Path $RepoRoot ".tooling\avd"
$env:ANDROID_SERIAL = $Serial
$env:HOME = Join-Path $RepoRoot ".tooling\home"

New-Item -ItemType Directory -Force -Path $ArtifactRoot | Out-Null

function Wait-ForBoot {
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 3
        $boot = (& $Adb -s $Serial shell getprop sys.boot_completed 2>$null).Trim()
    } while ($boot -ne "1" -and (Get-Date) -lt $deadline)
    if ($boot -ne "1") {
        throw "Emulator $Serial did not finish booting."
    }
}

function Capture-DeviceScreenshot {
    param([string]$FileName)
    & $Adb -s $Serial shell screencap -p $RemoteScreenshot
    if ($LASTEXITCODE -ne 0) {
        throw "Could not capture emulator screenshot."
    }
    & $Adb -s $Serial pull $RemoteScreenshot (Join-Path $ArtifactRoot $FileName) | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not pull emulator screenshot."
    }
}

function Start-AppAndWaitForRender {
    & $Adb -s $Serial shell am force-stop $PackageName | Out-Null
    & $Adb -s $Serial shell am start -W -n "$PackageName/.ui.MainActivity" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not launch MusicClock."
    }
    Start-Sleep -Seconds 8
}

$launchedByScript = $false
try {
    $devices = & $Adb devices
    $connected = $devices -match "^$([regex]::Escape($Serial))\s+device$"
    if (-not $connected) {
        if (-not (Test-Path -LiteralPath $Emulator)) {
            throw "Android emulator not found at $Emulator"
        }
        $emulatorOut = Join-Path $RepoRoot ".tooling\emulator.out.log"
        $emulatorErr = Join-Path $RepoRoot ".tooling\emulator.err.log"
        Start-Process -FilePath $Emulator `
            -ArgumentList @(
                "-avd", $AvdName,
                "-no-window",
                "-no-snapshot",
                "-no-boot-anim",
                "-gpu", "swiftshader_indirect",
                "-no-audio"
            ) `
            -WindowStyle Hidden `
            -RedirectStandardOutput $emulatorOut `
            -RedirectStandardError $emulatorErr `
            | Out-Null
        $launchedByScript = $true
    }

    & $Adb -s $Serial wait-for-device
    Wait-ForBoot
    & $Adb -s $Serial shell settings put global window_animation_scale 0 | Out-Null
    & $Adb -s $Serial shell settings put global transition_animation_scale 0 | Out-Null
    & $Adb -s $Serial shell settings put global animator_duration_scale 0 | Out-Null
    & $Adb -s $Serial shell wm dismiss-keyguard | Out-Null

    if (-not $SkipBuild) {
        & $Gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle verification failed."
        }
    }

    & $Adb -s $Serial uninstall $PackageName 2>$null | Out-Null
    & $Gradle :app:connectedDebugAndroidTest
    if ($LASTEXITCODE -ne 0) {
        throw "Connected instrumentation tests failed."
    }

    & $Adb -s $Serial install -r $ApkPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install $ApkPath"
    }
    & $Adb -s $Serial install -r $TestApk | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install instrumentation test APK."
    }
    & $Adb -s $Serial shell pm grant $PackageName android.permission.POST_NOTIFICATIONS | Out-Null
    & $Adb -s $Serial shell cmd appops set $PackageName SCHEDULE_EXACT_ALARM allow | Out-Null
    & $Adb -s $Serial shell cmd appops set $PackageName USE_FULL_SCREEN_INTENT allow | Out-Null
    & $Adb -s $Serial shell settings put global heads_up_notifications_enabled 1 | Out-Null

    & $Adb -s $Serial logcat -c
    & $Adb -s $Serial shell am instrument -w -r `
        -e class "com.musicclock.alarm.VisualAcceptanceTest" `
        $Runner
    if ($LASTEXITCODE -ne 0) {
        throw "Visual acceptance test failed."
    }

    $latestRemote = (& $Adb -s $Serial shell "ls -td $RemoteAcceptance/*" 2>$null |
        Select-Object -First 1).Trim()
    if (-not $latestRemote) {
        throw "Visual acceptance screenshots were not generated."
    }
    & $Adb -s $Serial pull "$latestRemote/01-home.png" (Join-Path $ArtifactRoot "api35-home.png") | Out-Null
    & $Adb -s $Serial pull "$latestRemote/02-editor.png" (Join-Path $ArtifactRoot "api35-editor.png") | Out-Null
    & $Adb -s $Serial pull "$latestRemote/03-ring.png" (Join-Path $ArtifactRoot "api35-ring.png") | Out-Null

    & $Adb -s $Serial logcat -d -t 5000 |
        Set-Content -LiteralPath (Join-Path $ArtifactRoot "api35-logcat.txt")

    & $Adb -s $Serial shell pm clear $PackageName | Out-Null
    & $Adb -s $Serial shell pm grant $PackageName android.permission.POST_NOTIFICATIONS | Out-Null
    & $Adb -s $Serial shell settings put system font_scale 1.3 | Out-Null
    Start-AppAndWaitForRender
    Capture-DeviceScreenshot "api35-home-large-font.png"

    & $Adb -s $Serial shell settings put system font_scale 1.0 | Out-Null
    & $Adb -s $Serial shell wm size 720x1600 | Out-Null
    & $Adb -s $Serial shell wm density 320 | Out-Null
    Start-AppAndWaitForRender
    Capture-DeviceScreenshot "api35-home-360dp.png"

    Write-Output "Emulator acceptance completed."
    Write-Output "Artifacts: $ArtifactRoot"
}
finally {
    & $Adb -s $Serial shell wm size reset 2>$null | Out-Null
    & $Adb -s $Serial shell wm density reset 2>$null | Out-Null
    & $Adb -s $Serial shell settings put system font_scale 1.0 2>$null | Out-Null
    & $Adb -s $Serial shell settings put global heads_up_notifications_enabled 1 2>$null | Out-Null
    if ($StopEmulator -and $launchedByScript) {
        & $Adb -s $Serial emu kill 2>$null | Out-Null
    }
}
