# QuickSave — 架构设计文档（ARCH）

> 版本：1.3
> 日期：2026-06-11
> 作者：开发工程师

本文档描述 QuickSave Android 应用的项目级架构现状，反映已交付的 MVP（QS-0001）、分类标签（category-tag）、手动输入（QS-0002）与全局悬浮窗（QS-0003，含文字输入 + 录音）合并后的代码组织。Feature 级架构变更详见 [`docs/ARCH_changelist.md`](ARCH_changelist.md) 与 `docs/features/<feature_id>/arch-<feature_name>.md`。

---

## 一、技术栈

| 类别 | 选择 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 / Material You |
| 导航 | `androidx.navigation.compose` |
| 状态 | `StateFlow` + `MutableStateFlow`（UDF 单向数据流） |
| 持久化 | Jetpack DataStore (Preferences) |
| 文件 I/O | SAF (`ContentResolver` + 持久化 URI 权限)；录音经 MediaStore (`Music/QuickSave/`) |
| 后台 | `ForegroundService`（剪贴板常驻 `specialUse`；录音 `microphone`）；悬浮窗服务为普通 started 服务 |
| 悬浮窗 | `WindowManager` + `TYPE_APPLICATION_OVERLAY`（经典 Android View，非 Compose） |
| 录音 | `MediaRecorder`（AAC/MPEG-4） |
| 协程 | Kotlin Coroutines（Dispatchers.IO + Main） |
| 拖拽排序 | `sh.calvin.reorderable` |
| 测试 | JUnit 4 + Mockito-Kotlin + `kotlinx-coroutines-test` |
| 最低 SDK | 29；目标 SDK：36 |
| 依赖注入 | 手动单例（Application `by lazy`），不引入 Hilt |

---

## 二、模块分层

```
┌───────────────────────────── UI Layer ─────────────────────────────┐
│  MainActivity + AppNavigation (NavHost)                            │
│      ├── HomeScreen (Composable)                                   │
│      └── SettingsScreen (Composable，含悬浮窗总开关 + 权限流)        │
│  InputActivity（透明，复用主页手动输入，悬浮窗拉起）                 │
│  RecordPermissionActivity（透明，申请 RECORD_AUDIO）                │
│  ui.theme.{Color, Theme, Type}                                     │
├─────────────────────────── ViewModel Layer ────────────────────────┤
│  HomeViewModel : AndroidViewModel  └─ StateFlow<HomeUiState>       │
│  SettingsViewModel : AndroidViewModel                              │
│      └─ targetFileUri / categories / overlayEnabled               │
├─────────────────────────── Repository Layer ───────────────────────┤
│  ClipRepository (interface) ── ClipRepositoryImpl                  │
│  OverlayRepository (interface) ── OverlayRepositoryImpl            │
├──────────────────────────── Data Source Layer ─────────────────────┤
│  AppDataStore   (interface)  ── AppDataStoreImpl  (DataStore)      │
│  FileDataSource (interface)  ── SafFileDataSource (SAF)            │
├──────────────────────────── Service Layer ─────────────────────────┤
│  ClipboardMonitorService : Service  (前台 / 常驻通知，保活进程)     │
│  OverlayService : Service  (非前台；WindowManager 叠加层)          │
│  RecorderService : Service  (microphone 前台；MediaRecorder)       │
│  RecordingController (object)  StateFlow 桥接录音态（Recorder↔Overlay）│
├────────────────────────── Application Layer ───────────────────────┤
│  QuickSaveApplication : Application  (DI 容器，by lazy 单例)       │
├────────────────────────────── Util ────────────────────────────────┤
│  PermissionHelper（通知 / 悬浮窗 / 录音权限判断）                   │
└────────────────────────────────────────────────────────────────────┘
```

依赖方向严格自上而下：UI → ViewModel → Repository → DataSource。Service 与 UI 并列，不互相依赖；二者皆通过 `QuickSaveApplication` 取得 Repository。

**悬浮窗/录音保活与状态桥：** `OverlayService` 不再是前台服务——进程由常驻的 `ClipboardMonitorService`（前台）保活，故应用常驻通知只有一条。`RecorderService` 为 `microphone` 前台服务，仅录音期间存在（含计时通知）。`RecordingController` 是进程内单例 `StateFlow<RecordingUiState>`，由 `RecorderService` 写、`OverlayService` 订阅以更新把手/按钮录音态。

---

## 三、Package 结构

```
com.ylib.quicksave
├── app/                  # Application 容器
│   └── QuickSaveApplication.kt
├── data/
│   ├── repository/       # 业务规则 + Result 包装
│   │   ├── ClipRepository.kt / ClipRepositoryImpl.kt
│   │   └── OverlayRepository.kt / OverlayRepositoryImpl.kt   # QS-0003
│   └── source/           # 纯 I/O：DataStore + SAF
│       ├── AppDataStore.kt / AppDataStoreImpl.kt
│       └── FileDataSource.kt / SafFileDataSource.kt
├── overlay/              # QS-0003 悬浮窗
│   ├── OverlayEdge.kt / OverlayPosition.kt
│   ├── OverlayPositionCalculator.kt   # 纯逻辑，可单测
│   └── OverlayService.kt              # WindowManager 叠加层（非前台）
├── recorder/             # QS-0003 录音
│   ├── RecordingUiState.kt / RecordingController.kt
│   ├── RecordingFileNamer.kt          # 纯逻辑，可单测
│   └── RecorderService.kt             # microphone 前台 + MediaRecorder
├── service/
│   └── ClipboardMonitorService.kt
├── ui/
│   ├── InputActivity.kt               # QS-0003 透明输入窗
│   ├── RecordPermissionActivity.kt    # QS-0003 透明权限申请
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   └── SettingsScreen.kt
│   ├── theme/
│   │   ├── Color.kt / Theme.kt / Type.kt
│   └── viewmodel/
│       ├── HomeViewModel.kt
│       └── SettingsViewModel.kt
├── util/
│   └── PermissionHelper.kt
└── MainActivity.kt
```

> 接口与实现放在同 package：测试时通过 mock/fake 替换实现，无需开放跨 package 可见性。

---

## 四、跨模块协议

### 4.1 ClipRepository 接口

```kotlin
interface ClipRepository {
    suspend fun saveEntry(text: String, category: String? = null): Result<Unit>
    suspend fun setTargetFile(uri: Uri)
    fun getTargetFileUri(): Flow<Uri?>
    suspend fun clearSavedFile(): Result<Unit>

    // category-tag 扩展
    fun getCategories(): Flow<List<String>>
    suspend fun setCategories(categories: List<String>)
    fun getSelectedCategory(): Flow<String?>
    suspend fun setSelectedCategory(category: String?)
}
```

约定：
- 写操作返回 `Result<Unit>`，**永不抛异常**到 ViewModel
- 读操作返回 `Flow<T>`，由订阅方决定生命周期
- `saveEntry(text, category=null)` 与 MVP 行为完全等价（向后兼容）

### 4.2 文件保存格式

```
有分类：[分类名][yyyy-MM-dd HH:mm:ss] 文字内容\n
无分类：[yyyy-MM-dd HH:mm:ss] 文字内容\n
```

均以 `\n` 结尾、追加写入。无分类格式与 MVP 完全一致。

### 4.3 DataStore 字段

| Key | 类型 | 默认值 | 序列化 |
|-----|------|--------|--------|
| `target_file_uri` | `String?` | `null` | 原始 URI 字符串 |
| `categories` | `List<String>` | `emptyList()` | 换行符分隔；空时删除 key |
| `selected_category` | `String?` | `null` | 原始字符串；null 时删除 key |
| `overlay_enabled` | `Boolean` | `false` | 悬浮窗总开关（QS-0003） |
| `overlay_edge` | `String` | `"RIGHT"` | 贴边方向 LEFT/RIGHT（QS-0003） |
| `overlay_y_ratio` | `Float` | `0.4` | 把手纵向位置 = 屏高比例（QS-0003） |

`selected_category` 不在 `categories` 列表时视为 `null`（在 ViewModel `combine` 阶段过滤，存储侧不修改）。

### 4.4 OverlayRepository 接口（QS-0003）

```kotlin
interface OverlayRepository {
    fun isEnabled(): Flow<Boolean>
    suspend fun setEnabled(enabled: Boolean)
    fun getPosition(): Flow<OverlayPosition>          // OverlayPosition(edge: OverlayEdge, yRatio: Float)
    suspend fun setPosition(position: OverlayPosition)
}
```

是悬浮窗 UI/服务与 DataStore 之间的类型化边界：把存储层的 `Pair<String, Float>` 映射为领域 `OverlayPosition`（经 `OverlayEdge.fromStorage` 容错解析）。`AppDataStore` 不反向依赖 `overlay` 包。

### 4.5 RecordingController（跨服务录音态桥）

```kotlin
object RecordingController {                          // 进程内单例
    val state: StateFlow<RecordingUiState>            // RecordingUiState(isRecording, elapsedSeconds)
    fun update(isRecording: Boolean, elapsedSeconds: Int)
    fun reset()
}
```

`RecorderService` 每秒 `update`；`OverlayService` 在自身 `scope` 内 `collectLatest` 订阅，更新把手颜色/红点/录音按钮文本（固定宽 + tabular 数字，避免计时引起面板重排）。同进程共享，无跨进程开销。

### 4.6 录音文件输出

经 MediaStore 写入公共目录：`MediaStore.Audio` + `RELATIVE_PATH = "Music/QuickSave"`，`DISPLAY_NAME = QS_yyyyMMdd_HHmmss.m4a`，录制期间 `IS_PENDING=1`、停止后置 `0`。`MediaRecorder` 设 MIC / MPEG_4 / AAC / 128kbps / 44.1kHz，`ParcelFileDescriptor` 在录音期间保持打开、停止时关闭。

---

## 五、并发与线程模型

| 操作 | Dispatcher | 备注 |
|------|-----------|------|
| 文件 I/O（`appendLine` / `clearFile`） | `Dispatchers.IO` | `withContext` 在 `SafFileDataSource` 内部切换 |
| `ClipboardManager.primaryClip` 读取 | `Dispatchers.Main` | Android 要求主线程访问 |
| DataStore 读写 | DataStore 内部 IO | 调用方在 `viewModelScope` 即可 |
| StateFlow 更新 | Main（`viewModelScope` 默认） | 工作量极小 |

- 所有协程作用域限定为 `viewModelScope`，`onCleared()` 自动取消
- 不使用 `GlobalScope`、不手动管理 `Job`

---

## 六、错误处理

| 失败 | 抛出 | UI 反馈（Snackbar） |
|------|------|--------------------|
| 未配置目标文件 | `IllegalStateException("未设置目标文件")` | 「请先在设置中选择保存文件」 |
| SAF 权限丢失 | `SecurityException("目标文件无写入权限，请重新选择")` | 「文件无写入权限，请重新选择」 |
| IO 异常 | 通过 `runCatching` 捕获 | 「保存失败：{message}」 |

- Repository 层用 `runCatching { ... }` 统一包装 → `Result.failure`
- ViewModel 通过 `SaveResult` 密封类传递结果，UI 监听后调用 `clearLastSaveResult()` 复位

---

## 七、应用生命周期与 DI

```
Process Start
    │
    ▼
QuickSaveApplication.onCreate()
    │   by lazy: dataStore / fileDataSource / clipRepository
    ▼
MainActivity.onCreate()
    │   1. requestPermissions(POST_NOTIFICATIONS) on TIRAMISU+
    │   2. startForegroundService(ClipboardMonitorService)   // 常驻前台，保活进程
    │   3. lifecycleScope: 若 overlayEnabled && canDrawOverlays → startService(OverlayService)
    │   4. setContent { QuickSaveTheme { AppNavigation(homeViewModel) } }
    ▼
ClipboardMonitorService.onCreate()
    │   1. createNotificationChannel("quicksave_channel", LOW)
    │   2. startForeground(1001, buildNotification())   // 应用唯一常驻通知
    ▼
HomeScreen / SettingsScreen 通过 viewModels()
    └── ViewModel 通过 (app as QuickSaveApplication).{clipRepository, overlayRepository} 取依赖
```

设置页总开关开启 → 检查 `canDrawOverlays`，未授权跳系统授权页；授予后 `setEnabled(true)` + `startService(OverlayService)`。关闭 → `setEnabled(false)` + `startService(OverlayService, ACTION_STOP)`。录音由 `OverlayService.onRecordClicked` 经 `startForegroundService(RecorderService, ACTION_START)` 触发（进程因剪贴板前台服务处于 FGS/可见级别，允许启动 mic 前台服务）；无录音权限时拉起 `RecordPermissionActivity`。

DI 容器仅在 `QuickSaveApplication` 中：

```kotlin
val dataStore: AppDataStore by lazy { AppDataStoreImpl(this) }
val fileDataSource: FileDataSource by lazy { SafFileDataSource(contentResolver) }
val clipRepository: ClipRepository by lazy { ClipRepositoryImpl(dataStore, fileDataSource) }
val overlayRepository: OverlayRepository by lazy { OverlayRepositoryImpl(dataStore) }   // QS-0003
```

---

## 八、权限模型

| 权限 | 用途 | 申请时机 |
|------|------|---------|
| `FOREGROUND_SERVICE` | 启动前台服务（必需） | Manifest 静态声明 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 剪贴板常驻服务子类型 | Manifest，对应 `ClipboardMonitorService` 的 `persistentNotification` |
| `FOREGROUND_SERVICE_MICROPHONE` | 录音前台服务子类型（QS-0003） | Manifest，对应 `RecorderService` |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 | `MainActivity.onCreate()` 运行时申请，未授权时通知静默失败但不影响主功能 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗绘制（QS-0003） | 开总开关时按需申请（`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`） |
| `RECORD_AUDIO` | 录音（QS-0003） | 首次点【录音】时经 `RecordPermissionActivity` 运行时申请 |
| 文件读写 | 通过 SAF 持久化 URI 权限，无需声明 `READ/WRITE_EXTERNAL_STORAGE`；录音经 MediaStore 写公共目录，无需存储权限 | 选文件时 `takePersistableUriPermission()` |

`PermissionHelper` 提供 `hasNotificationPermission` / `canDrawOverlays` / `hasRecordAudioPermission` 与运行时 SDK 注入点 (`sdkIntProvider`)，便于单元测试不同 API 等级行为。

**注：** `OverlayService` 不持有前台服务类型——它是普通 started 服务，借常驻的 `ClipboardMonitorService` 保活进程，从而把应用常驻通知收成一条。持有 `SYSTEM_ALERT_WINDOW` 时可从其后台拉起透明 Activity（`InputActivity` / `RecordPermissionActivity`，`FLAG_ACTIVITY_NEW_TASK`）。

---

## 九、测试约定

| 测试类型 | 位置 | 框架 |
|---------|------|------|
| 单元测试 | `app/src/test/` | JUnit 4 + Mockito-Kotlin + `kotlinx-coroutines-test` |
| 集成 / 系统测试 | `app/src/androidTest/` | AndroidX Test + Compose Testing |

- Repository / ViewModel 单元测试通过 mock `AppDataStore` 与 `FileDataSource` 隔离
- DataSource 接口刻意保持瘦，便于 mock；不在接口中暴露 `Context` / `ContentResolver` 细节
- `PermissionHelper.sdkIntProvider` 是注入点，避免单元测试触碰 `Build.VERSION.SDK_INT`

---

## 十、Feature 架构文档索引

| feature_id | 范围 | 架构变更 | 文档 |
|------------|------|---------|------|
| `QS-0001` | MVP — 项目骨架（分层、Repository、SAF、前台 Service、Compose 导航） | 首次建立 | [features/QS-0001/arch-mvp.md](features/QS-0001/arch-mvp.md) |
| `QS-0002` | 手动输入保存（v1.2，已交付） | **无** — 仅 UI 层增量（HomeUiState 字段拆分、HomeScreen 抽出 3 个 Composable）；Repository / DataStore / Service / 跨模块协议全部不变 | — |
| `QS-0003` | 全局悬浮窗 + 文字输入 + 录音（v1.3，已交付） | **有** — 新增 `overlay/` 与 `recorder/` 包、`OverlayService`（非前台）/`RecorderService`（mic 前台）/`RecordingController`、`OverlayRepository`、`InputActivity`/`RecordPermissionActivity`；DataStore 增 3 键；新增 `SYSTEM_ALERT_WINDOW`/`RECORD_AUDIO`/`FOREGROUND_SERVICE_MICROPHONE` 权限；常驻通知由两条收成一条 | [features/QS-0003/design-floating-window.md](features/QS-0003/design-floating-window.md) |

> 后续 feature 若引入架构变更（新增模块、修改跨层协议），需在本表追加并新建 `arch-<feature_name>.md`（QS-0003 以 brainstorming 流程的 `design-floating-window.md` 承载架构说明）。仅做实现变更（不动接口）的 feature 在表中标注「无」即可，不需要单独的 arch 文档。
