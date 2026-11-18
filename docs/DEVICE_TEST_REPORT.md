# 真机验收报告

## 当前状态

```text
STATUS: BLOCKED
REASON: No physical Android device connected through ADB.
UPDATED: 2026-11-18
```

模拟器验收已完成，但模拟器不能证明真实耳机路由、扬声器隔离、震动触感和 OEM
后台策略。本报告中的硬件用例必须由物理 Android 设备完成。

## 设备信息

- 测试日期：NOT RUN
- 手机厂商与型号：NOT RUN
- Android 版本 / API：NOT RUN
- OEM 系统版本：NOT RUN
- 蓝牙耳机型号 / 编码协议：NOT RUN
- 有线耳机类型：NOT RUN
- APK 版本与 SHA-256：
  `1.0.0 / E1C09C6C67FD3194FA2881B32D206CA17D9B432A54A1177689C68626F48A97D0`

## 硬件验收

| ID | 场景 | 结果 | 现象与证据 |
|---|---|---|---|
| AUD-BT-01 | 蓝牙耳机连接，锁屏触发 | NOT RUN | 无真机 |
| AUD-BT-02 | 蓝牙耳机响铃中断开 | NOT RUN | 无真机 |
| AUD-WIRE-01 | 有线耳机连接触发 | NOT RUN | 无真机 |
| AUD-WIRE-02 | 有线耳机响铃中拔出 | NOT RUN | 无真机 |
| AUD-NOHEADSET | 未连接耳机触发 | NOT RUN | 无真机 |
| SNOOZE | 延后 5 分钟 | NOT RUN | 无真机 |
| QUEUE | 两个闹钟接近触发 | NOT RUN | 无真机 |
| FILE | 本地音频导入和播放 | NOT RUN | 无真机 |
| REBOOT | 重启后恢复触发 | NOT RUN | 无真机 |
| DND-SILENT | 勿扰/静音行为 | NOT RUN | 无真机 |

## 执行方式

连接真机、蓝牙耳机和有线耳机后，在仓库根目录运行：

```powershell
.\scripts\run-device-acceptance.ps1
```

脚本不会接受模拟器作为硬件验收设备。它会安装 APK、配置权限，并在
`docs/artifacts/device/<timestamp>/` 生成设备信息、首页截图、Logcat 和手工结果表。

## 发布结论

- 是否允许进入正式发布：NO
- 阻断原因：核心耳机路由的物理设备验收尚未执行
- 解除条件：所有音频、断连、后台、重启和 Snooze 用例通过并记录证据
