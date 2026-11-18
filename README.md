# 耳机闹钟 MusicClock

一个 Android 原生、完全离线的耳机优先闹钟。连接耳机时，应用先静音确认实际音频路由，确认输出仍在耳机后才渐入播放；耳机断开、路由不匹配或音频焦点被抢占时立即暂停，不会主动改走扬声器。

## 手机直接下载

Android 9.0 及以上设备可直接下载 debug 测试版：

- [直接下载 APK](https://github.com/hzyyyun/misciclock2235/releases/download/v1.0.0-debug/MusicClock-1.0.0-debug.apk)
- [查看 GitHub 预发布页面](https://github.com/hzyyyun/misciclock2235/releases/tag/v1.0.0-debug)
- [Release 说明](docs/RELEASE_NOTES_v1.0.0-debug.md)

该版本使用 debug 签名，且真机耳机路由验收尚未完成，不应标记为正式稳定版。

## 当前交付

- 完整 Kotlin / Jetpack Compose Android 工程。
- 可安装 debug APK：`dist/MusicClock-1.0.0-debug.apk`。
- Gradle 构建产物：`app/build/outputs/apk/debug/app-debug.apk`。
- 产品需求：`docs/PRD.md`。
- 技术设计：`docs/TECHNICAL_DESIGN.md`。
- 权限说明：`docs/PERMISSIONS.md`。
- 验收用例：`docs/ACCEPTANCE_TESTS.md`。
- 模拟器验收：`docs/EMULATOR_TEST_REPORT.md`。
- 已通过 17 个 JVM/Robolectric 测试、7 个 API 35 模拟器测试及 Android Lint。

## 环境要求

- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Android 9.0（API 28）或更高版本的测试设备

`local.properties` 需要指向本机 Android SDK：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 构建

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
```

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

完整 API 35 模拟器验收：

```powershell
.\scripts\setup-emulator.ps1
.\scripts\run-emulator-acceptance.ps1
```

连接 Android 真机并准备好蓝牙/有线耳机后执行：

```powershell
.\scripts\run-device-acceptance.ps1
```

脚本会拒绝模拟器、安装 APK、授予所需权限，并在
`docs/artifacts/device/<timestamp>/` 生成设备信息、截图、Logcat 和待填写的
`summary.md`。当前硬件状态见 `docs/DEVICE_TEST_REPORT.md`。

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装与首次使用

1. 在 Android 9 以上真机安装 APK。
2. 打开“设置与隐私”，依次允许精确闹钟、通知、全屏响铃，并检查电池限制。
3. 连接蓝牙或有线耳机，新建闹钟并将触发时间设为 1 至 2 分钟后。
4. 锁屏并等待触发，确认声音进入耳机、震动开始、响铃页可延后 5 分钟。
5. 再次触发时直接断开耳机，确认应用立即停止声音且不会转为扬声器。

音频路由受 Android 和 OEM 音频策略影响。应用采取失败关闭策略，但操作系统在断开瞬间可能短暂自行改变路由；第三方应用无法在所有设备上消除这一系统级竞态。

