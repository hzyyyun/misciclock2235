# 权限与系统访问说明

## 1. 已申请权限

| 权限 | 类型 | 用途 | 拒绝后的行为 |
|---|---|---|---|
| `SCHEDULE_EXACT_ALARM` | 特殊应用访问 | 使用 `setAlarmClock()` 按分钟精确触发 | 改用 `setAndAllowWhileIdle()`，界面警告无法保证准点 |
| `POST_NOTIFICATIONS` | Android 13+ 运行时权限 | 响铃页、排队、路由失败和铃声失效通知 | 音频和震动仍可运行，但用户可能看不到通知 |
| `USE_FULL_SCREEN_INTENT` | 特殊应用访问 | 锁屏时直接显示响铃操作页 | 退回高优先级通知 |
| `VIBRATE` | 普通权限 | 循环闹铃震动和耳机路由失败提示 | 无震动；声音策略不变 |
| `RECEIVE_BOOT_COMPLETED` | 普通权限 | 重启后重建闹钟 | 重启前已注册的系统闹钟可能丢失 |
| `WAKE_LOCK` | 普通权限 | 保持响铃服务初始化期间设备可运行 | 后台响应可靠性下降 |
| `FOREGROUND_SERVICE` | 普通权限 | 后台持续执行响铃会话 | 无法可靠承载后台音频 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ 普通权限 | 声明服务正在播放媒体 | Android 14+ 无法合法启动媒体前台服务 |

## 2. 明确不申请的权限

| 权限/能力 | 不申请原因 |
|---|---|
| `INTERNET` | 产品完全离线；不声明该权限是从安装包层面禁用应用网络能力 |
| `READ_MEDIA_AUDIO` | 系统文件选择器授予单个 URI 的临时读取授权，不需要读取整个音频库 |
| `READ_EXTERNAL_STORAGE` | 同上，使用 Storage Access Framework |
| `BLUETOOTH_CONNECT` / 蓝牙扫描 | 通过 `AudioManager` 枚举当前音频输出，不直接连接或扫描蓝牙设备 |
| `RECORD_AUDIO` | 不录音、不分析环境声音 |
| 勿扰模式访问 | 不读取或修改用户勿扰策略 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 不直接请求绕过；仅引导用户打开系统电池设置 |

## 3. 特殊系统设置

- 精确闹钟设置：Android 12+ 通过 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 打开。
- 通知设置：权限被永久拒绝后打开应用通知设置。
- 全屏通知设置：Android 14+ 打开应用全屏通知设置。
- 电池设置：打开系统电池优化列表，由用户自行决定。
- 厂商自启动/后台运行：无统一 API，应用只展示说明，不伪造可自动修复状态。

## 4. 隐私数据

| 数据 | 存储位置 | 是否离开设备 |
|---|---|---|
| 闹钟名称、时间、重复规则 | Room 私有数据库 | 否 |
| 触发与队列状态 | Room 私有数据库 | 否 |
| 导入音频副本 | `filesDir/ringtones/` | 否 |
| 音量设置 | Preferences DataStore | 否 |

应用关闭自动备份和设备迁移备份。系统级厂商遥测不属于应用主动行为。

