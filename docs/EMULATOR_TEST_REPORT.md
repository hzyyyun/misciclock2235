# API 35 模拟器验收报告

## 环境

- 日期：2026-11-18
- AVD：`musicclock_api35`
- 设备配置：Pixel 6，Android 15 / API 35，`x86_64`
- 加速：Windows Hypervisor Platform，可用
- 启动方式：无窗口、冷启动、`swiftshader_indirect`、`-no-audio`
- 应用版本：`1.1.0`，`versionCode 2`

## 自动化结果

| 类型 | 结果 |
|---|---|
| JVM 测试 | 6 个套件，18 个测试，0 failure |
| API 35 connected tests | 11 个测试，0 failure |
| Android Lint | 0 error |
| UI 截图 | 首页、编辑页、响铃页、大字体、360dp 小屏 |
| 重启恢复 | 实测通过 |

最终 APK SHA-256：

```text
C60B30AB8D5D61A6F88162509FEA58AD5AFE817014261826EA64A7AECCB9DD32
```

设备测试覆盖：

- 通过真实 UI 新建、启停、编辑和删除闹钟。
- 数字时间滚轮可打开、滚动并确认保存。
- AlarmManager 精确定时在应用后台时触发前台响铃服务。
- 屏幕熄灭/锁屏后精确闹钟仍能触发。
- 手动停止会终止音频、震动、响铃通知和前台服务。
- 已过期实例不启动播放器；2 秒短截止测试实例自动停止。
- Room v1→v2 migration 保留历史实例并新增响铃截止字段。
- 两个接近触发的实例按顺序进入 `RINGING` 和 `QUEUED`。
- Snooze 创建独立实例，触发时间为点击后约 5 分钟。
- 私有 WAV 文件通过真实平台解码器完成复制和导入。
- Compose 首页、编辑页、响铃页视觉产物生成。

## 视觉检查

| 页面 | 截图 |
|---|---|
| 首页 | [api35-home.png](artifacts/emulator/api35-home.png) |
| 编辑页 | [api35-editor.png](artifacts/emulator/api35-editor.png) |
| 时间滚轮 | [api35-time-wheel.png](artifacts/emulator/api35-time-wheel.png) |
| 响铃页 | [api35-ring.png](artifacts/emulator/api35-ring.png) |
| 1.3 倍字体 | [api35-home-large-font.png](artifacts/emulator/api35-home-large-font.png) |
| 360dp 小屏 | [api35-home-360dp.png](artifacts/emulator/api35-home-360dp.png) |

视觉检查未发现文字截断、主要按钮不可达或控件重叠。响铃页正常截图时已关闭系统 Heads-up 遮挡。

## 重启恢复实测

1. 通过 debug-only 广播创建 180 秒后的单次精确闹钟。
2. 确认 AlarmManager 中存在 `RTC_WAKEUP` 实例。
3. 重启模拟器并等待 `BOOT_COMPLETED`。
4. 闹钟恢复并在设定时间进入 `AlarmRingService`。
5. 系统记录 `mAllowStart=ALARM_MANAGER_ALARM_CLOCK`，服务成功进入前台。

证据：

- [api35-reboot-ring.png](artifacts/emulator/api35-reboot-ring.png)
- [api35-reboot-trigger.txt](artifacts/emulator/api35-reboot-trigger.txt)

模拟器以 `-no-audio` 启动，重启后该次实例出现音频焦点未授予，应用按设计保持震动和路由失败提示。此结果验证了失败关闭与重启调度，不代表真实音频输出。

## 复现命令

```powershell
.\scripts\setup-emulator.ps1
.\scripts\run-emulator-acceptance.ps1
```

快速重跑：

```powershell
.\scripts\run-emulator-acceptance.ps1 -SkipBuild
```

脚本会启动或复用 AVD、运行 connected tests、安装测试包、生成截图和 Logcat。默认不停止模拟器；传入 `-StopEmulator` 可在脚本启动 AVD 时自动关闭。

## 未覆盖项

- 蓝牙 A2DP、BLE Audio、有线耳机和 USB-C 的真实设备路由。
- 扬声器是否完全无声；模拟器不能证明硬件路由隔离。
- 震动马达的实际触感与强度。
- 不同 OEM 的电池管理、自启动、锁屏和音频策略。
- 勿扰模式与静音模式在具体厂商系统上的差异。

以上项目仍是发布前的真机阻断项。

当前真机状态见 `docs/DEVICE_TEST_REPORT.md`。
