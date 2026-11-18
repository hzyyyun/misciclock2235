# MusicClock 1.0.0 Debug Preview

> 这是 debug 签名的测试预发布，不是正式稳定版。蓝牙/有线耳机真机硬件验收尚未完成。

## 直接下载

- APK：[MusicClock-1.0.0-debug.apk](https://github.com/hzyyyun/misciclock2235/releases/download/v1.0.0-debug/MusicClock-1.0.0-debug.apk)
- Release 页面：[v1.0.0-debug](https://github.com/hzyyyun/misciclock2235/releases/tag/v1.0.0-debug)

## 文件信息

- 版本：`1.0.0`
- 包名：`com.musicclock.alarm`
- 最低 Android：9.0 / API 28
- 目标 Android：15 / API 35
- 大小：`59,469,661` 字节
- 签名：Android debug
- SHA-256：

```text
E1C09C6C67FD3194FA2881B32D206CA17D9B432A54A1177689C68626F48A97D0
```

## 功能

- 多个闹钟、单次或每周重复、名称、启停、编辑和删除。
- 系统精确闹钟和前台响铃服务，支持后台、锁屏及重启恢复。
- 耳机优先的失败关闭播放：先静音确认实际路由，再渐入耳机声音。
- 耳机断开、错误路由或音频焦点丢失时停止声音，保持震动和提示。
- 5 分钟 Snooze、多闹钟串行队列和 10 分钟自动静音。
- 本地音频导入、私有副本、平台解码验证和损坏文件回退。
- 默认 40% 应用音量上限，可配置范围 10% 至 80%。
- 完全离线：Manifest 不包含 `INTERNET` 权限，系统备份已禁用。

## 安装

1. 使用 Android 9 或更高版本手机，在浏览器中打开 APK 链接。
2. 如果系统提示，请允许当前浏览器“安装未知应用”。
3. 安装后打开“设置与隐私”，允许精确闹钟、通知和全屏响铃，并检查电池限制。
4. 耳机功能需要连接蓝牙 A2DP、BLE Audio、3.5mm、USB-C 或助听器类设备。

## 验证范围

- 17 个 JVM/Robolectric 测试通过。
- 7 个 API 35 模拟器设备测试通过。
- Android Lint：0 issue。
- 已验证 UI、精确闹钟、锁屏触发、队列、Snooze、WAV 导入和模拟器重启恢复。

详见 [EMULATOR_TEST_REPORT.md](EMULATOR_TEST_REPORT.md)。

## 已知限制

- 未完成真实蓝牙/有线耳机、扬声器隔离、震动触感和 OEM 后台策略验收。
- Android 无法保证第三方应用绝对控制音频路由；应用采用失败关闭策略。
- 勿扰、静音、厂商电池管理和“强行停止”仍会受系统策略影响。
- debug 签名只适合内部测试，后续正式版本将使用独立 release 签名。

当前硬件状态见 [DEVICE_TEST_REPORT.md](DEVICE_TEST_REPORT.md)。
