# QuickSave QS-0001 (MVP) — 架构设计文档

> feature_id：`QS-0001`
> feature_name：`mvp`
> 版本：1.0
> 日期：2026-05-07
> 作者：开发工程师
> 状态：已交付（v1.0 首发）
> 关联文档：[`docs/features/QS-0001/prd-mvp.md`](prd-mvp.md) · [`docs/features/QS-0001/ui-mvp.md`](ui-mvp.md) · [`docs/ARCH.md`](../../ARCH.md)

本文档记录 MVP 阶段（QS-0001）确立的**项目骨架架构**。后续 feature（如 `category-tag`）在此骨架上做的接口扩展，已在项目级 `docs/ARCH.md` 中合并描述，本文档只关心 MVP 引入的初始决策与衔接点。

---

## 一、架构决策概览

| 决策 | 选择 | 触发的 PRD/UI 需求 |
|------|------|-------------------|
| 分层模式 | UI → ViewModel → Repository → DataSource 单向依赖 | 复用文件 I/O 与 DataStore，并支持 Compose 重组 |
| 依赖注入 | Application-scope 手动单例（`by lazy`），不引入 Hilt | 应用规模小，避免框架复杂度 |
| 持久化 — 配置 | Jetpack DataStore (Preferences) | 仅存「目标文件 URI」一项轻量配置 |
| 持久化 — 内容 | SAF (Storage Access Framework) `ContentResolver` 写入用户选定文件 | US-04（用户自选文件、重启后权限保留） |
| 后台进程 | `ForegroundService`（`specialUse / persistentNotification`） | US-01（常驻通知，应用运行期间持续展示） |
| Compose 导航 | `androidx.navigation.compose` `NavHost`，两个路由 | UI §七 主页 ↔ 设置页 |
| 协程调度 | 文件 I/O `Dispatchers.IO`，剪切板读取 `Dispatchers.Main` | 防止主线程阻塞；剪切板需主线程访问 |
| 错误传递 | `Result<Unit>` 在 Repository 层包装异常 | 让 ViewModel 拿到结构化失败原因驱动 Snackbar |

---

## 二、模块划分（MVP 范围）

```
┌──────────────────────────── UI Layer ────────────────────────────┐
│  MainActivity + NavHost                                          │
│      ├── HomeScreen (Composable)                                 │
│      └── SettingsScreen (Composable)                             │
├──────────────────────── ViewModel Layer ─────────────────────────┤
│  HomeViewModel : AndroidViewModel  ── StateFlow<HomeUiState>     │
│  SettingsViewModel : AndroidViewModel                            │
├──────────────────────── Repository Layer ────────────────────────┤
│  ClipRepository (interface)  ── ClipRepositoryImpl               │
├──────────────────────── Data Source Layer ───────────────────────┤
│  AppDataStore   (interface)  ── AppDataStoreImpl  (DataStore)    │
│  FileDataSource (interface)  ── SafFileDataSource (SAF)          │
├──────────────────────── Service Layer ───────────────────────────┤
│  ClipboardMonitorService : Service  (前台服务 / 常驻通知)         │
├────────────────────── Application Layer ─────────────────────────┤
│  QuickSaveApplication : Application  (DI 容器，by lazy 单例)     │
└──────────────────────────────────────────────────────────────────┘
```

### 2.1 各层职责

| 层 | 职责 | 关键类型 |
|----|------|---------|
| Application | 进程级单例容器，按 `by lazy` 装配依赖图 | `QuickSaveApplication` |
| Service | 启动前台服务、注册通知渠道、维持常驻通知 | `ClipboardMonitorService` |
| UI | Compose 渲染 + 用户输入 | `HomeScreen`、`SettingsScreen`、`ui.theme.*` |
| ViewModel | 持有 `StateFlow<UiState>`，订阅 Repository Flow，编排用户动作 | `HomeViewModel`、`SettingsViewModel`、`HomeUiState`、`SaveResult` |
| Repository | 业务规则（拼接时间戳、校验权限）、Result 包装 | `ClipRepository`、`ClipRepositoryImpl` |
| Data Source | 纯 I/O：DataStore 键值对、SAF 文件读写 | `AppDataStore`、`FileDataSource` 及实现 |

### 2.2 依赖方向

- 严格自上而下：UI → ViewModel → Repository → DataSource，无反向依赖
- Service 层与 UI 层并列，均通过 Application 单例间接获取 Repository（MVP 中 Service 仅维护通知，未直接调用 Repository）
- 接口（`ClipRepository`、`AppDataStore`、`FileDataSource`）放在与实现同 package，便于测试时替换

---

## 三、核心数据流

### 3.1 保存剪切板（流程 A，US-02 + US-03）

```
User tap "保存到文件 ▶"
        │
        ▼
HomeScreen → HomeViewModel.saveClipboard()
        │   _uiState.update { isSaving = true }
        ▼
viewModelScope.launch (Dispatchers.Main.immediate by default)
        │
        ▼
ClipRepositoryImpl.saveEntry(text, category=null)
        │   1. dataStore.getTargetFileUri().first()  → URI? (null 抛 IllegalStateException)
        │   2. fileDataSource.isWritable(uri)        → false 抛 SecurityException
        │   3. format "[yyyy-MM-dd HH:mm:ss] $text"
        ▼
SafFileDataSource.appendLine(uri, line)  on Dispatchers.IO
        │   contentResolver.openOutputStream(uri, "wa") write line + "\n"
        ▼
返回 Result.success(Unit) → ViewModel 更新 lastSaveResult = Success
        │
        ▼
HomeScreen 监听 lastSaveResult → 显示 Snackbar「已保存」并 clearLastSaveResult()
```

### 3.2 配置目标文件（流程 B，US-04）

```
SettingsScreen 调起 ActivityResultContracts.OpenDocument / CreateDocument
        │
        ▼
回调拿到 Uri → ContentResolver.takePersistableUriPermission(uri, READ|WRITE)
        │
        ▼
SettingsViewModel.setTargetFile(uri)
        │
        ▼
ClipRepositoryImpl.setTargetFile(uri) → AppDataStoreImpl.saveTargetFileUri(uri.toString())
        │
        ▼
DataStore 写入 → SettingsViewModel.targetFileUri (StateFlow) 自动更新 → UI 重组
```

### 3.3 清空文件（流程 C，US-05）

```
User tap "清空保存文件内容" → HomeViewModel.showClearDialog()
        │
        ▼
AlertDialog 确认 → HomeViewModel.clearSavedFile()
        │
        ▼
ClipRepositoryImpl.clearSavedFile()
        │   1. 取 URI（同上）
        │   2. 校验写权限
        ▼
SafFileDataSource.clearFile(uri)  on Dispatchers.IO
        │   contentResolver.openOutputStream(uri, "wt") write empty
        ▼
Result.success → SaveResult.ClearSuccess → Snackbar「文件内容已清空」
```

---

## 四、衔接点（供后续 feature 扩展）

| 扩展类型 | MVP 留出的扩展点 | 后续 feature 的接入方式 |
|---------|----------------|----------------------|
| 保存格式 | `ClipRepository.saveEntry(text, category: String? = null)` 的 `category` 参数（MVP 始终传 null） | `category-tag` 直接传分类名，Repository 内拼接 `[$category]` 前缀 |
| 持久化字段 | `AppDataStore` 接口可扩展新方法 | `category-tag` 增加 `saveCategories` / `getCategories` / `saveSelectedCategory` / `getSelectedCategory` |
| UI 状态 | `HomeUiState` 是 data class，新字段默认值不破坏旧调用 | `category-tag` 增加 `categories` / `selectedCategory` / `showAddCategoryDialog` 字段 |
| Repository 实现 | 单一 `ClipRepositoryImpl` 持有所有 DataSource | 直接在同一实现内增加新方法即可，无需新建仓库 |

> 这些扩展点是**意识形态约定**，MVP 阶段并未提前为分类标签预留代码。`category-tag` 上线时同时修改了接口签名与实现，但保持向后兼容（`category=null` 行为与 MVP 完全一致）。

---

## 五、并发与线程模型

| 调用 | 线程 | 原因 |
|------|------|------|
| `ClipboardManager.primaryClip` 读取 | `Dispatchers.Main`（`viewModelScope` 默认） | Android 要求在主线程访问剪切板 |
| `SafFileDataSource.appendLine` / `clearFile` | `Dispatchers.IO`（`withContext` 切换） | 文件 I/O 阻塞，避免卡 UI |
| DataStore `Flow` 订阅 | DataStore 内部使用 `Dispatchers.IO` | 已封装，调用方无需关心 |
| `HomeViewModel.init` 中 `combine(...)` | `viewModelScope`（Main） | 仅做 StateFlow 更新，工作量极小 |

- `viewModelScope` 在 ViewModel `onCleared()` 自动取消，无内存泄漏风险
- 不使用 `GlobalScope`、不手动管理 Job

---

## 六、错误处理策略

| 失败类型 | 抛出位置 | 包装方式 | UI 反馈 |
|---------|--------|---------|--------|
| 未配置目标文件 | `ClipRepositoryImpl.saveEntry/clearSavedFile` 抛 `IllegalStateException("未设置目标文件")` | `runCatching { ... }` 转 `Result.failure` | Snackbar「请先在设置中选择保存文件」 |
| SAF 权限丢失 | 同上抛 `SecurityException("目标文件无写入权限，请重新选择")` | 同上 | Snackbar「文件无写入权限，请重新选择」 |
| 文件 I/O 异常（IOException 等） | `SafFileDataSource` 内 `openOutputStream` 失败由 `runCatching` 在 Repository 层捕获 | 同上 | Snackbar「保存失败：{异常信息}」 |

- 所有面向 ViewModel 的 Repository 方法返回 `Result<T>`，**永不抛异常**
- 所有面向 UI 的 ViewModel 状态更新通过 `_uiState.update { copy(...) }`，单一可观测来源

---

## 七、备选方案与取舍

| 决策点 | 备选方案 | 选定方案 | 取舍理由 |
|-------|---------|---------|---------|
| DI | Hilt / Koin | Application 手动 `by lazy` | MVP 仅 3 个单例（DataStore / FileDataSource / Repository），框架开销远大于收益 |
| 持久化 | Room / SharedPreferences | DataStore Preferences | 仅一项配置，DataStore 提供 Flow 且零样板 |
| 文件存储 | App-private storage / MediaStore | SAF（用户自选） | US-04 明确要求用户决定保存位置，并在重启后保留权限 |
| 后台进程 | WorkManager 周期任务 / `BroadcastReceiver` | 前台 Service + 常驻通知 | US-01 要求"运行期间始终展示"，需要持续运行的进程 |
| 时间格式化 | `java.time.LocalDateTime` (API 26+) | `SimpleDateFormat`（每实例非静态） | minSdk=29 可用 java.time，但当前实现已稳定；后续可平滑替换 |
| 错误模型 | sealed `AppError` + `Either` | Kotlin `Result<T>` | 标准库内置，避免引入 Arrow 等额外依赖 |

---

## 八、不在 MVP 架构范围

下列 MVP 阶段刻意未做的设计，留作后续 feature 处理：

- **剪切板自动监听**：当前 Service 仅维护通知，不读取剪切板（避免 Android 12+ 隐私横幅与轮询耗电）。如需自动保存需引入「无障碍服务 + 800ms 轮询」方案
- **数据库 / 历史检索**：所有保存记录直接写入用户选定的纯文本文件，无应用内查询能力
- **同步与备份**：完全本地化
- **多文件目标**：DataStore 仅存单一 `target_file_uri`
