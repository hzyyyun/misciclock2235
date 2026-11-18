# 真机验收报告

## 当前状态

```text
STATUS: PARTIAL PASS
REASON: Bluetooth basic scenarios passed; full hardware matrix incomplete.
UPDATED: 2026-11-18
```

模拟器验收已完成。当前物理设备已确认蓝牙耳机基础播放、耳机断连后无扬声器输出，
以及未连接耳机时的默认播放。完整耳机矩阵、队列、重启和系统策略仍需继续验收。

这些真机结果对应 `1.0.0`。`1.1.0` 的数字滚轮、手动停止和持久化响铃上限需要重新执行
蓝牙耳机连接、断连及后台停止回归后，才能更新对应 APK 的硬件结论。

## 设备信息

- 测试日期：2026-11-18
- 手机厂商与型号：NOT PROVIDED
- Android 版本 / API：NOT PROVIDED
- OEM 系统版本：NOT PROVIDED
- 蓝牙耳机型号 / 编码协议：NOT PROVIDED
- 有线耳机类型：NOT PROVIDED
- APK 版本与 SHA-256：
  `1.0.0 / E1C09C6C67FD3194FA2881B32D206CA17D9B432A54A1177689C68626F48A97D0`

## 硬件验收

| ID | 场景 | 结果 | 现象与证据 |
|---|---|---|---|
| AUD-BT-01 | 蓝牙耳机连接播放 | PASS | 已确认声音进入蓝牙耳机；锁屏未单独记录 |
| AUD-BT-02 | 蓝牙耳机响铃中断开 | PASS | 已确认断连后扬声器不出声 |
| AUD-WIRE-01 | 有线耳机连接触发 | NOT RUN | 无真机 |
| AUD-WIRE-02 | 有线耳机响铃中拔出 | NOT RUN | 无真机 |
| AUD-NOHEADSET | 未连接耳机触发 | PASS | 已确认未连接耳机时可播放 |
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
- 阻断原因：有线耳机、Snooze、队列、本地文件、重启和勿扰模式尚未完成真机验收
- 解除条件：剩余硬件用例通过，并补齐手机与耳机型号、Android/OEM 信息
