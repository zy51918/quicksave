# QuickSave — 架构设计文档（ARCH）

> 版本：1.2
> 日期：2026-05-08
> 作者：开发工程师

本文档描述 QuickSave Android 应用的项目级架构现状，反映已交付的 MVP（QS-0001）与分类标签（category-tag）合并后的代码组织。Feature 级架构变更详见 [`docs/ARCH_changelist.md`](ARCH_changelist.md) 与 `docs/features/<feature_id>/arch-<feature_name>.md`。

---

## 一、技术栈

| 类别 | 选择 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 / Material You |
| 导航 | `androidx.navigation.compose` |
| 状态 | `StateFlow` + `MutableStateFlow`（UDF 单向数据流） |
| 持久化 | Jetpack DataStore (Preferences) |
| 文件 I/O | SAF (`ContentResolver` + 持久化 URI 权限) |
| 后台 | `ForegroundService`（`specialUse / persistentNotification`） |
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
│      └── SettingsScreen (Composable)                               │
│  ui.theme.{Color, Theme, Type}                                     │
├─────────────────────────── ViewModel Layer ────────────────────────┤
│  HomeViewModel : AndroidViewModel                                  │
│      └─ StateFlow<HomeUiState>                                     │
│  SettingsViewModel : AndroidViewModel                              │
│      └─ StateFlow<Uri?> targetFileUri                              │
│      └─ StateFlow<List<String>> categories                         │
├─────────────────────────── Repository Layer ───────────────────────┤
│  ClipRepository (interface)                                        │
│      └─ ClipRepositoryImpl                                         │
├──────────────────────────── Data Source Layer ─────────────────────┤
│  AppDataStore   (interface)  ── AppDataStoreImpl  (DataStore)      │
│  FileDataSource (interface)  ── SafFileDataSource (SAF)            │
├──────────────────────────── Service Layer ─────────────────────────┤
│  ClipboardMonitorService : Service  (前台服务 / 常驻通知)           │
├────────────────────────── Application Layer ───────────────────────┤
│  QuickSaveApplication : Application  (DI 容器，by lazy 单例)       │
├────────────────────────────── Util ────────────────────────────────┤
│  PermissionHelper（运行时 SDK 注入点，便于单测）                    │
└────────────────────────────────────────────────────────────────────┘
```

依赖方向严格自上而下：UI → ViewModel → Repository → DataSource。Service 与 UI 并列，不互相依赖；二者皆通过 `QuickSaveApplication` 取得 Repository。

---

## 三、Package 结构

```
com.ylib.quicksave
├── app/                  # Application 容器
│   └── QuickSaveApplication.kt
├── data/
│   ├── repository/       # 业务规则 + Result 包装
│   │   ├── ClipRepository.kt
│   │   └── ClipRepositoryImpl.kt
│   └── source/           # 纯 I/O：DataStore + SAF
│       ├── AppDataStore.kt
│       ├── AppDataStoreImpl.kt
│       ├── FileDataSource.kt
│       └── SafFileDataSource.kt
├── service/
│   └── ClipboardMonitorService.kt
├── ui/
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

`selected_category` 不在 `categories` 列表时视为 `null`（在 ViewModel `combine` 阶段过滤，存储侧不修改）。

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
    │   2. startForegroundService(ClipboardMonitorService)
    │   3. setContent { QuickSaveTheme { AppNavigation(homeViewModel) } }
    ▼
ClipboardMonitorService.onCreate()
    │   1. createNotificationChannel("quicksave_channel", LOW)
    │   2. startForeground(1001, buildNotification())
    ▼
HomeScreen / SettingsScreen 通过 viewModels()
    └── ViewModel 通过 (app as QuickSaveApplication).clipRepository 取依赖
```

DI 容器仅在 `QuickSaveApplication` 中：

```kotlin
val dataStore: AppDataStore by lazy { AppDataStoreImpl(this) }
val fileDataSource: FileDataSource by lazy { SafFileDataSource(contentResolver) }
val clipRepository: ClipRepository by lazy { ClipRepositoryImpl(dataStore, fileDataSource) }
```

---

## 八、权限模型

| 权限 | 用途 | 申请时机 |
|------|------|---------|
| `FOREGROUND_SERVICE` | 启动前台服务（必需） | Manifest 静态声明 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 必需的子类型 | Manifest，对应 `<service> property persistentNotification` |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 | `MainActivity.onCreate()` 运行时申请，未授权时通知静默失败但不影响主功能 |
| 文件读写 | 通过 SAF 持久化 URI 权限，无需声明 `READ/WRITE_EXTERNAL_STORAGE` | 选文件时 `takePersistableUriPermission()` |

`PermissionHelper` 提供运行时 SDK 注入点 (`sdkIntProvider`)，便于单元测试不同 API 等级行为。

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

> 后续 feature 若引入架构变更（新增模块、修改跨层协议），需在本表追加并新建 `arch-<feature_name>.md`。仅做实现变更（不动接口）的 feature 在表中标注「无」即可，不需要单独的 arch 文档。
