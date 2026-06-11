# 悬浮窗通知合并与录音按钮防抖动 — 设计文档

> 版本：1.0
> 日期：2026-06-11
> 状态：设计待评审
> 关联：QS-0003 全局悬浮窗（本文件是对其的打磨/修订）

---

## 一、背景与问题

QS-0003 上线后真机使用发现两处体验问题：

1. **通知栏出现两条常驻通知。** 根因是有两个常驻前台服务：`ClipboardMonitorService`（通知 1001）与 `OverlayService`（通知 1002，悬浮窗开启时）。两者都是“QuickSave 运行中，点击打开”类通知，冗余。
2. **常驻通知文案过时。** `ClipboardMonitorService` 正文为“点击打开应用保存剪切板内容”，只提剪贴板；而应用现已具备手动输入、悬浮窗、录音等能力。
3. **录音时面板抖动。** 录音中【录音】按钮文字每秒变化（`录音中 01:09` → `01:10`），数字为比例字体、宽度变化，而按钮为 `WRAP_CONTENT`，导致展开面板每秒重排、视觉上抖动。

---

## 二、设计

### 2.1 OverlayService 降级为普通 started 服务（消除第二条通知）

**关键约束：** Android 规定前台服务必须显示一条通知。但 `OverlayService` 不必是前台服务——它当前用 `startForeground` 仅为保活进程，而 `ClipboardMonitorService` 已是常驻前台服务，整个进程已被其保活。同进程内，`OverlayService` 只要持有 WindowManager 叠加层 View，进程存活即悬浮窗存活。

**改动：**
- `OverlayService`：移除 `startForeground(...)`、`buildNotification()`/`ensureChannel()`、通知渠道常量 `quicksave_overlay_channel`、`NOTIFICATION_ID`。`onCreate` 不再进前台，仅初始化 WindowManager + 读取位置 + `addOverlay`。`onStartCommand` 保留 `ACTION_STOP → stopSelf` 与默认 `START_STICKY`。`onDestroy`/`removeOverlay`/拖拽/展开折叠/录音态可视化/位置持久化**全部不变**。
- 启动方：`SettingsScreen.onToggleOverlay` 与 `MainActivity.onCreate` 中的 `ContextCompat.startForegroundService(OverlayService)` → 改为 `context.startService(OverlayService)`（均在前台 Activity 上下文发起，允许）。
- `AndroidManifest.xml`：`OverlayService` 去掉 `android:foregroundServiceType="specialUse"` 及其 `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`，保留 `<service android:name=".overlay.OverlayService" android:exported="false" />`。

**依赖与前提（已成立）：** OverlayService 的存活依赖常驻的 `ClipboardMonitorService` 保活进程。后者由 `MainActivity` 每次启动无条件拉起、`START_STICKY`、前台保护，因此悬浮窗开启时它必然在跑。设备重启后两者均不自启（与现有设计一致），用户重开 App 即恢复。

### 2.2 ClipboardMonitorService 通知文案

- 正文 `"点击打开应用保存剪切板内容"` → `"点击打开应用"`；标题仍为 `"QuickSave"`。其余通知属性（渠道、ongoing、silent、PendingIntent 打开 MainActivity）不变。

### 2.3 录音按钮防抖动

- `OverlayService` 中【录音】按钮：
  - 在 LinearLayout 面板中给它**固定宽度**槽位（按最长态 `"录音中 00:00"` 用按钮自身 paint 实测宽度 + 内边距余量计算，避免写死 dp 在不同字号/密度下失准），替代当前的 `WRAP_CONTENT`。
  - 设 `fontFeatureSettings = "tnum"`（tabular 等宽数字），`maxLines = 1`，文字居中。
  - 效果：录音文本 `"录音中 mm:ss"` 字符数恒定 + 等宽数字 → 每秒更新宽度不变；固定宽度槽位锁定面板总宽 → 不再重排。
- 【文字输入】按钮保持不变。

---

## 三、不改动项

- 悬浮窗的权限流、贴边拖拽、展开/折叠、点面板外收起、位置持久化、文字输入（计划 B）保持原样。
- 录音逻辑（`RecorderService`、`RecordingController`、MediaStore 落盘、录音通知 1003）不变——录音通知是**临时**通知（仅录音期间出现、停止即消失），不属于“常驻两条”问题。
- `RecordingController` → `OverlayService` 的录音态订阅与红色把手/红点逻辑不变，仅按钮宽度/数字呈现方式调整。

---

## 四、测试策略

纯框架/服务/UI 改动，靠真机验证（无新单测）：
1. 开启悬浮窗后，通知栏**只剩一条**常驻通知，文案为“点击打开应用”。
2. 悬浮窗仍可贴边拖拽、展开/折叠、文字输入、录音；切 App / 锁屏后悬浮窗与服务仍在。
3. 录音时展开面板**不抖动**，计时正常递增；录音中临时出现录音通知，停止后消失。
4. 关闭悬浮窗总开关：悬浮窗消失；常驻通知仍在（剪贴板那条）。
5. 重开 App：悬浮窗按持久化状态恢复。

---

## 五、风险

- **OverlayService 非前台后被回收**：仅当常驻前台服务不在时才可能；该服务始终在跑，风险可忽略。若未来将剪贴板服务改为可停止，需重新评估悬浮窗保活方式。
- **固定按钮宽度在超长录音（>99 分钟）下可能不足**：极端场景，`maxLines=1` 下最多文字截断，不影响功能；可后续按需放宽。
