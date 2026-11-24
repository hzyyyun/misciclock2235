# MusicClock 1.1.1 Debug Preview

> 本版本使用 debug 签名，适合内部测试与直接侧载。`1.1.1` 修复提前响铃和残留响铃会话问题。
> 真实蓝牙/有线耳机硬件矩阵仍需在新 APK 上重新执行。

## 直接下载

- APK：[MusicClock-1.1.1-debug.apk](https://github.com/hzyyyun/misciclock2235/releases/download/v1.1.1-debug/MusicClock-1.1.1-debug.apk)
- Release 页面：[v1.1.1-debug](https://github.com/hzyyyun/misciclock2235/releases/tag/v1.1.1-debug)

## 本次修复

- 修复凌晨启用当天稍晚单次闹钟时可能直接响铃的问题；触发实例必须已到期，并兼容 2 秒时钟抖动。
- 修复应用重新进入时根据数据库残留 `RINGING` 状态主动拉起响铃的问题。
- 响铃实例认领时原子写入开始时间和绝对截止时间；缺失或已过期的截止时间直接标记为 `MISSED`。
- 系统重启或应用升级后会清理无法继续播放的残留响铃状态。
- 手动停止会同时清理当前实例和同一闹钟的排队实例，保留未来周期计划与已安排 Snooze。
- 通知栏停止操作改为前台服务 PendingIntent，避免后台启动限制导致停止失败。

## 界面更新

- 闹钟编辑页的数字时间卡片在对应闹钟响铃时显示“延时 5 分钟”和“停止响铃”。
- 控制按钮只出现在正在响铃的对应闹钟页面，停止后立即消失。
- 延时沿用固定 5 分钟 Snooze；关闭 Snooze 时只显示停止按钮。

## 文件信息

- 版本：`1.1.1` / `versionCode 3`
- 包名：`com.musicclock.alarm`
- 最低 Android：9.0 / API 28
- 目标 Android：15 / API 35
- 文件大小：`59,526,421` 字节
- 签名：Android debug
- SHA-256：

```text
667E0BBA7B86A0D648CC53C0C8880CEA5D9B516FCD6EBA5F317489497D61D600
```

## 验证范围

- 23 个 JVM/Robolectric 测试通过。
- 15 个 API 35 模拟器 connected tests 通过。
- Android Lint 通过。
- 覆盖凌晨启用稍晚闹钟、未来实例拦截、编辑页一一对应控制、停止后重新进入、过期/缺失截止时间恢复、队列推进、通知停止和 Room migration。

## 已知限制

- `1.1.1` 尚未完成真实蓝牙/有线耳机硬件回归。
- debug 签名只适合内部测试。
- 勿扰、静音、厂商电池管理和“强行停止”仍受系统策略影响。

硬件状态见 [DEVICE_TEST_REPORT.md](https://github.com/hzyyyun/misciclock2235/blob/main/docs/DEVICE_TEST_REPORT.md)。
