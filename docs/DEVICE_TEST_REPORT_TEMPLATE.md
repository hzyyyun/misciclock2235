# 真机验收报告模板

## 设备信息

- 测试日期：
- 手机厂商与型号：
- Android 版本 / API：
- OEM 系统版本：
- 蓝牙耳机型号 / 编码协议：
- 有线耳机类型：
- APK 版本与 SHA-256：

## 自动准备结果

- APK 安装：PASS / FAIL
- 精确闹钟授权：PASS / FAIL
- 通知授权：PASS / FAIL
- 全屏通知授权：PASS / FAIL
- 首页截图：
- Logcat：

## 硬件验收

| ID | 场景 | 结果 | 现象与证据 |
|---|---|---|---|
| AUD-BT-01 | 蓝牙耳机连接，锁屏触发 | PASS / FAIL | |
| AUD-BT-02 | 蓝牙耳机响铃中断开 | PASS / FAIL | |
| AUD-WIRE-01 | 有线耳机连接触发 | PASS / FAIL | |
| AUD-WIRE-02 | 有线耳机响铃中拔出 | PASS / FAIL | |
| AUD-NOHEADSET | 未连接耳机触发 | PASS / FAIL | |
| SNOOZE | 延后 5 分钟 | PASS / FAIL | |
| QUEUE | 两个闹钟接近触发 | PASS / FAIL | |
| FILE | 本地音频导入和播放 | PASS / FAIL | |
| REBOOT | 重启后恢复触发 | PASS / FAIL | |
| DND-SILENT | 勿扰/静音行为 | PASS / FAIL | |

## 阻断问题

```text
设备与耳机：
触发时间：
预期行为：
实际行为：
是否听到扬声器声音：
复现步骤：
录屏或日志路径：
```

## 结论

- 是否允许进入发布流程：YES / NO
- 仍需修复：
- 验收人：
