# MusicClock 1.1.0 Debug Preview

> 这是 debug 签名的测试预发布。蓝牙耳机基础场景已在 1.0.0 真机通过，
> 1.1.0 仍需重新执行蓝牙连接、断连和停止响铃回归。

## 直接下载

- APK：[MusicClock-1.1.0-debug.apk](https://github.com/hzyyyun/misciclock2235/releases/download/v1.1.0-debug/MusicClock-1.1.0-debug.apk)
- Release 页面：[v1.1.0-debug](https://github.com/hzyyyun/misciclock2235/releases/tag/v1.1.0-debug)

## 本次更新

- 时间设置改为底部 24 小时数字滚轮，小时 `00-23`、分钟 `00-59`，支持上下滑动和循环。
- 响铃页和通知提供“停止响铃”，手动关闭会立即停止音频、震动和响铃通知。
- 修复闹钟超过 10 分钟仍可能继续响铃的问题。
- 10 分钟上限改为持久化绝对截止时间，服务重建或 Activity 重新打开不会重置计时。
- 增加播放器级硬停止兜底和 Room v1→v2 数据库迁移。

## 文件信息

- 版本：`1.1.0` / `versionCode 2`
- 包名：`com.musicclock.alarm`
- 最低 Android：9.0 / API 28
- 大小：`59,493,653` 字节
- 签名：Android debug
- SHA-256：

```text
C60B30AB8D5D61A6F88162509FEA58AD5AFE817014261826EA64A7AECCB9DD32
```

## 验证范围

- 18 个 JVM/Robolectric 测试通过。
- 11 个 API 35 模拟器测试通过。
- Android Lint：0 issue。
- 覆盖数字滚轮、UI CRUD、手动停止、通知清理、2 秒短截止硬停止、过期实例恢复、Room migration、后台/锁屏触发、队列、Snooze 和 WAV 导入。

详细截图和结果见 [EMULATOR_TEST_REPORT.md](https://github.com/hzyyyun/misciclock2235/blob/main/docs/EMULATOR_TEST_REPORT.md)。

## 已知限制

- 1.1.0 尚未完成真实蓝牙/有线耳机硬件回归。
- debug 签名只适合内部测试。
- 勿扰、静音、厂商电池管理和“强行停止”仍受系统策略影响。

硬件状态见 [DEVICE_TEST_REPORT.md](https://github.com/hzyyyun/misciclock2235/blob/main/docs/DEVICE_TEST_REPORT.md)。
