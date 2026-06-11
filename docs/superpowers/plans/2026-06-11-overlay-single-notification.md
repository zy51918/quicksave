# 悬浮窗通知合并 + 文案 + 录音按钮防抖动 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 常驻通知收成一条（OverlayService 降级为非前台服务）、常驻通知文案改通用、录音按钮固定宽+等宽数字消除面板抖动。

**Architecture:** OverlayService 不再 `startForeground`，靠常驻的 `ClipboardMonitorService`（前台）保活进程；启动方改用 `startService`；Manifest 去掉其前台类型。`ClipboardMonitorService` 通知正文改文案。`OverlayService` 录音按钮在面板里改为固定宽度槽位 + tabular 数字。

**Tech Stack:** Kotlin、Android Service、WindowManager、经典 View。无新依赖、无新单测（纯框架/UI 改动，真机验证）。

**关联：** spec `docs/superpowers/specs/2026-06-11-overlay-single-notification-design.md`；修订 QS-0003。

---

## 文件结构（仅修改）
- `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt` — 去前台/通知（Task 1）；录音按钮防抖（Task 3）
- `app/src/main/AndroidManifest.xml` — OverlayService 去前台类型（Task 1）
- `app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt` — 启动方改 startService（Task 1）
- `app/src/main/java/com/ylib/quicksave/MainActivity.kt` — 启动方改 startService（Task 1）
- `app/src/main/java/com/ylib/quicksave/service/ClipboardMonitorService.kt` — 通知文案（Task 2）

---

## Task 1：OverlayService 降级为非前台服务

**Files:**
- Modify: `OverlayService.kt`、`AndroidManifest.xml`、`SettingsScreen.kt`、`MainActivity.kt`

> 先 Read `OverlayService.kt` 确认当前结构：`onCreate` 中调用 `createChannelAndForeground()`（创建通知渠道并 `startForeground(NOTIFICATION_ID, ...)`）；companion 含 `CHANNEL_ID = "quicksave_overlay_channel"`、`NOTIFICATION_ID = 1002`、`ACTION_STOP`、`HANDLE_W_DP`、`HANDLE_H_DP`。

- [ ] **Step 1：OverlayService 去掉前台/通知**

在 `OverlayService.kt` 中：
1. `onCreate`：删除对 `createChannelAndForeground()` 的调用。`onCreate` 保留：`super.onCreate()`、`windowManager = getSystemService(...) as WindowManager`、以及读取位置并 `addOverlay(pos)` 的 `scope.launch { ... }`。
2. 删除整个 `createChannelAndForeground()` 方法。
3. 删除 companion 中的 `CHANNEL_ID` 和 `NOTIFICATION_ID` 常量（保留 `ACTION_STOP`、`HANDLE_W_DP`、`HANDLE_H_DP`）。
4. `onStartCommand` 保持不变（`ACTION_STOP → stopSelf(); return START_NOT_STICKY`，否则 `return START_STICKY`）。`onDestroy`、`removeOverlay`、拖拽、展开/折叠、`observeRecordingState`、`applyRecordingVisual` 等全部不动。
5. 删除因上述删除而不再使用的 import：`android.app.NotificationChannel`、`android.app.NotificationManager`、`android.app.PendingIntent`、`androidx.core.app.NotificationCompat`、`com.ylib.quicksave.MainActivity`。**逐一确认**这些符号在文件中确实不再被引用后再删（用搜索确认）。保留 `android.app.Service`、`android.content.Context`（若 `Context` 仍被其他处使用）等仍在用的 import。

- [ ] **Step 2：Manifest 去掉 OverlayService 前台类型**

在 `AndroidManifest.xml` 中，将：
```xml
        <service
            android:name=".overlay.OverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="floatingWindow" />
        </service>
```
替换为：
```xml
        <service
            android:name=".overlay.OverlayService"
            android:exported="false" />
```
（`RecorderService`、`ClipboardMonitorService` 的声明保持不变。）

- [ ] **Step 3：SettingsScreen 启动方改 startService**

在 `SettingsScreen.kt` 中，悬浮窗启动有**两处**用 `ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))`（一处在 `overlayPermissionLauncher` 回调内，一处在 `onToggleOverlay` 的已授权分支）。把这两处都改为：
```kotlin
                context.startService(Intent(context, OverlayService::class.java))
```
注意：`onToggleOverlay` 的“关闭”分支仍用 `context.startService(Intent(...).apply { action = OverlayService.ACTION_STOP })`，不变。
改完后检查 `androidx.core.content.ContextCompat` 是否在 `SettingsScreen.kt` 中还有其他用处；若不再被使用，删除该 import。

- [ ] **Step 4：MainActivity 启动方改 startService**

在 `MainActivity.kt` 的 `lifecycleScope.launch { ... }` 块内，将启动 OverlayService 的：
```kotlin
                ContextCompat.startForegroundService(
                    this@MainActivity, Intent(this@MainActivity, OverlayService::class.java)
                )
```
改为：
```kotlin
                this@MainActivity.startService(
                    Intent(this@MainActivity, OverlayService::class.java)
                )
```
注意：`MainActivity` 中启动 `ClipboardMonitorService` 仍用 `ContextCompat.startForegroundService(...)`（它仍是前台服务），**保留**，因此 `ContextCompat` import 不要删。

- [ ] **Step 5：编译 + 构建**

Run: `./gradlew compileDebugKotlin assembleDebug`
Expected: BUILD SUCCESSFUL（manifest 合并通过；无未用 import 报错）。

- [ ] **Step 6：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt app/src/main/AndroidManifest.xml app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt app/src/main/java/com/ylib/quicksave/MainActivity.kt
git commit -m "refactor(overlay): demote OverlayService to non-foreground (single persistent notification)"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 2：ClipboardMonitorService 通知文案

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/service/ClipboardMonitorService.kt`

- [ ] **Step 1：改正文文案**

将 `buildNotification()` 中：
```kotlin
            .setContentText("点击打开应用保存剪切板内容")
```
改为：
```kotlin
            .setContentText("点击打开应用")
```
（标题 `setContentTitle("QuickSave")` 及其余属性不变。）

- [ ] **Step 2：编译**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/service/ClipboardMonitorService.kt
git commit -m "feat(overlay): generalize persistent notification text"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 3：录音按钮固定宽 + 等宽数字（防抖动）

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt`

> 先 Read `OverlayService.kt`。当前 `buildPanelView()` 里录音按钮为：
> ```kotlin
>         val recBtn = buildPanelButton("录音") {
>             collapse()
>             onRecordClicked()
>         }
>         recordButton = recBtn
>         addView(recBtn)
> ```
> `buildPanelButton(label, onClick)` 返回一个 `Button`，其 `layoutParams` 是带 marginStart/marginEnd 的 `LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)`。`dp(v: Int)` 已存在。

- [ ] **Step 1：录音按钮设固定宽 + tabular 数字**

把上面那段替换为（在 `addView(recBtn)` 之前，对 `recBtn` 追加防抖配置）：
```kotlin
        val recBtn = buildPanelButton("录音") {
            collapse()
            onRecordClicked()
        }
        recBtn.apply {
            maxLines = 1
            fontFeatureSettings = "tnum" // 等宽数字，计时更新不改变字宽
            // 固定宽度槽位：按最长态实测，避免计时引起面板重排（抖动）
            val longest = "录音中 00:00"
            val contentWidth = paint.measureText(longest).toInt()
            val lp = (layoutParams as LinearLayout.LayoutParams)
            lp.width = contentWidth + dp(28)
            layoutParams = lp
        }
        recordButton = recBtn
        addView(recBtn)
```
说明：`paint.measureText` 用按钮自身字号实测，`dp(28)` 为左右内边距余量；`fontFeatureSettings = "tnum"` 是 `TextView` 属性（`Button` 继承）。

- [ ] **Step 2：编译 + 构建**

Run: `./gradlew compileDebugKotlin assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3：提交**

```bash
git add app/src/main/java/com/ylib/quicksave/overlay/OverlayService.kt
git commit -m "fix(overlay): fixed-width record button with tabular digits to stop panel jitter"
```
追加（空行后）：
```
Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## Task 4：设备手动验证

需真机/模拟器，悬浮窗权限已授予。

- [ ] **Step 1：安装**

Run: `./gradlew installDebug`，启动 App。

- [ ] **Step 2：单条通知 + 文案**

操作：开启悬浮窗（若未开），下拉通知栏。
Expected：**只有一条** QuickSave 常驻通知，正文为“点击打开应用”；无第二条“悬浮窗”通知。

- [ ] **Step 3：悬浮窗功能回归**

操作：贴边拖拽、点击展开、点【文字输入】保存一条、切到其他 App 再回来。
Expected：拖拽吸附正常；文字输入保存成功；切 App 后悬浮窗仍在（进程由剪贴板前台服务保活）。

- [ ] **Step 4：录音防抖动**

操作：展开面板点【录音】→ 授权→ 盯着面板看计时递增数秒 → 再次点停止。
Expected：录音中面板**不抖动**，按钮宽度恒定、计时正常递增（等宽数字）；录音时临时出现录音通知；停止后录音通知消失、文件落盘 `Music/QuickSave/`。录音期间常驻通知仍只有剪贴板那一条 + 临时录音条。

- [ ] **Step 5：关开关**

操作：设置里关闭悬浮窗总开关。
Expected：悬浮窗消失；常驻通知仍在（剪贴板那条）。

---

## 自检备注（计划作者完成）

- **Spec 覆盖**：§2.1（OverlayService 降级 + manifest + 两处启动方 + MainActivity）、§2.2（文案）、§2.3（录音按钮固定宽+tabular）。
- **不破坏**：onStartCommand 的 ACTION_STOP、关闭分支的 startService(ACTION_STOP)、ClipboardMonitorService 的 startForegroundService、录音逻辑均保留。
- **import 卫生**：Task 1 Step 1/3 明确要求删前确认无引用；MainActivity 保留 ContextCompat（剪贴板仍用）。
- **占位符**：无 TBD/TODO。
