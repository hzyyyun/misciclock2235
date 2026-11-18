# 耳机闹钟技术设计

## 1. 技术栈

| 领域 | 方案 |
|---|---|
| 语言 | Kotlin 2.0 |
| UI | Jetpack Compose Material 3 |
| 数据 | Room、Preferences DataStore |
| 异步 | Coroutines、Flow |
| 调度 | AlarmManager `setAlarmClock()` |
| 后台播放 | 前台服务，类型 `mediaPlayback` |
| 音频 | Android 平台 `MediaPlayer` |
| 文件选择 | Storage Access Framework |
| 构建 | AGP 8.7、Gradle 8.9、JDK 17、compile/target SDK 35、min SDK 28 |

选择平台 `MediaPlayer` 而不是 Media3，是因为需要直接使用 `setPreferredDevice()`、`getRoutedDevice()` 和路由变化回调完成实际输出校验。格式能力以平台解码器为准。

## 2. 模块

```text
alarm/     精确调度、广播接收器、系统事件恢复、下次触发算法
audio/     耳机设备识别、安全播放器、音频导入、震动、内置音生成
data/      Room 实体、DAO、Repository、DataStore 设置
ring/      前台响铃服务、通知、响铃 UI、会话状态
ui/        首页、编辑页、设置页、ViewModel、主题
core/      依赖容器、权限状态
```

## 3. 数据模型

### 3.1 AlarmEntity

```text
id, label, hour, minute, scheduleType, weekdaysMask,
oneShotAtMillis, enabled, ringtoneId, vibrationEnabled, snoozeEnabled
```

星期位定义：Monday bit 0 至 Sunday bit 6。

### 3.2 OccurrenceEntity

```text
id(UUID), alarmId, triggerAtMillis, kind, state,
parentOccurrenceId, createdAtMillis,
ringStartedAtMillis, autoStopAtMillis
```

状态：

```text
SCHEDULED -> QUEUED -> RINGING -> SNOOZED | DISMISSED | MISSED
```

状态持久化保证进程重建后可以恢复响铃或待处理队列。

### 3.3 RingtoneEntity

```text
id, displayName, privatePath, mimeType, durationMs, sizeBytes, importedAtMillis
```

文件路径固定位于 `filesDir/ringtones/`。导入时复制并验证，数据库只保存私有路径。

### 3.4 Settings

- `maxVolumePercent`
- 设置范围 10 至 80，默认 40。

## 4. 调度设计

### 4.1 精确触发

- 每个待触发 Occurrence 使用唯一 UUID 和对应唯一 `PendingIntent`。
- 精确权限可用时调用 `AlarmManager.setAlarmClock()`。
- 权限不可用时调用 `setAndAllowWhileIdle()`，产品界面明确标记为尽力调度。
- 广播接收器不执行长任务，立即启动前台服务。

### 4.2 恢复

以下事件触发全量重排：

- `BOOT_COMPLETED`
- `TIME_SET`
- `TIMEZONE_CHANGED`
- `MY_PACKAGE_REPLACED`
- `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`
- 应用进程启动

### 4.3 重复闹钟

- 服务收到触发后先标记当前 Occurrence，再注册下一次 Occurrence。
- 单次闹钟触发后自动禁用，但原设置保留。
- Snooze 使用独立 Occurrence，不修改父闹钟。
- 夏令时不存在时间顺延到当天第一个有效分钟；回拨重复时间取较早实例。

## 5. 音频路由设计

### 5.1 耳机识别

识别以下输出设备：

- 3.5mm 有线耳机/耳麦
- USB 耳机
- 蓝牙 A2DP
- 蓝牙 SCO 输出
- BLE 耳机（Android 12+）
- 助听器

Android 13+ 优先使用 `getAudioDevicesForAttributes()` 查询系统建议媒体输出，否则从当前输出设备中选择耳机。

### 5.2 失败关闭播放

1. 发现耳机后创建 `USAGE_MEDIA` 播放器。
2. 设置目标 `AudioDeviceInfo`，初始音量设为 0。
3. 开始播放后等待约 700ms，读取 `routedDevice`。
4. 只有实际设备 ID 与目标一致且设备类型属于耳机时才渐入。
5. 路由不匹配时立即释放播放器，显示失败状态并保持震动。
6. 播放期间监听 `AudioRouting` 和 `AudioDeviceCallback`，路由离开耳机立即停止。
7. 无耳机时使用 `USAGE_ALARM` 和系统默认闹铃音量。

播放器请求 `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`。永久或瞬时焦点丢失时停止音频，不自动改走扬声器。

### 5.3 已知竞态

系统在物理断开后可能先自行切换输出，再通知应用。应用能做的上限是保持启动阶段静音、监听路由变化并立即释放播放器，无法阻止操作系统在回调前改变路由。

## 6. 响铃服务状态机

### 6.1 启动

- 服务进入前台的时间目标小于 5 秒。
- 初始通知不播放声音，避免与受控播放器重复。
- 通知渠道声音为 null，所有声音由安全播放器控制。

### 6.2 队列

- DAO 查询当前 `RINGING`，存在时不启动第二个播放器。
- 新触发实例写为 `QUEUED`，并显示独立状态通知。
- 当前实例完成后通过 `claimNextQueued()` 提升最早排队实例。

### 6.3 操作

- `ACTION_SNOOZE`：当前实例写为 SNOOZED，创建 `now + 5min` 精确实例。
- `ACTION_DISMISS`：当前实例写为 DISMISSED。
- `ACTION_RETRY_AUDIO`：重新执行设备发现和失败关闭播放。
- 10 分钟超时：当前实例写为 MISSED，静音并写状态通知。
- `ringStartedAtMillis` 与 `autoStopAtMillis` 持久化首次响铃和绝对截止时间；服务恢复只等待剩余时间。
- 已过期的 `RINGING` 实例不启动播放器，直接进入 MISSED。
- `MediaPlayer` 和 Service watchdog 同时执行硬停止，避免主状态机异常时继续循环。

## 7. 通知与界面

- 响铃通知渠道：IMPORTANCE_HIGH、无通知声音、可见于锁屏、类别 ALARM。
- Android 14+ 检查 `canUseFullScreenIntent()`；不可用时退回高优先级通知。
- 响铃通知提供 Snooze、关闭和路由失败后的重试动作。
- UI 会话状态由进程内 `StateFlow` 提供，持久化状态仍以 Room 为准。

## 8. 文件导入

1. `ACTION_OPEN_DOCUMENT` 获取一次性读取授权。
2. 检查 MIME 或受支持扩展名。
3. 流式复制到私有目录。
4. 先使用 `MediaMetadataRetriever`，失败后再用 `MediaPlayer.prepare()` 验证。
5. 验证成功后写 RingtoneEntity；失败删除临时文件并展示原因。
6. 应用不需要 `READ_MEDIA_AUDIO` 或外部存储权限。

## 9. 隐私基线

- Manifest 不声明 `INTERNET`。
- `allowBackup=false`。
- `dataExtractionRules` 排除云备份和设备迁移。
- 不引入广告、统计、崩溃上报或远程配置 SDK。
- 不记录闹钟名称、铃声路径或用户文件内容。
- 不使用网络时间，全部时间来自 `ZoneId.systemDefault()` 和系统时钟。

## 10. 测试策略

### 自动化

- 触发时间算法、星期、单次、夏令时。
- Room v1→v2 migration、响铃绝对截止时间、终端状态清理。
- Room 队列串行和级联清理。
- Manifest 无网络权限、备份关闭。
- 内置 WAV 文件头和长度。
- Android Lint。

### 真机

- 蓝牙 A2DP、BLE Audio、3.5mm、USB-C。
- 后台、锁屏、进程重启、设备重启。
- 耳机中途断开、重连、蓝牙没电。
- 勿扰、静音、精确权限拒绝、电池限制。
- 多个同时触发、Snooze、10 分钟自动静音。
- 手动停止、后台服务重建、过期实例恢复、播放器硬停止。

### 模拟器

- `scripts/run-emulator-acceptance.ps1` 启动 API 35 Pixel 6 AVD。
- connected tests 覆盖 UI CRUD、后台/锁屏精确触发、队列、Snooze 和 WAV 导入。
- `VisualAcceptanceTest` 生成首页、编辑页和响铃页截图。
- 验收产物和未覆盖项写入 `docs/EMULATOR_TEST_REPORT.md`。

## 11. 后续可扩展项

- 铃声波形预览和响铃音量自动衰减。
- 设备级路由诊断记录器，仅保存在本地。
- 锁屏快捷关闭与 Wear OS 配套。
- 自定义 Snooze 时长和递增音量。

