# 内容分类标签 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为每条剪切板保存记录添加可选分类标签，用户可在主页 Chip 选择器中切换分类，在设置页进行增删改排序管理。

**Architecture:** 分类列表和当前选中分类以换行符分隔字符串存入 DataStore。`ClipRepository` 接口新增 4 个分类方法委托给 `AppDataStore`。`HomeViewModel` 将分类状态合入已有 `HomeUiState`；`SettingsViewModel` 新增增删改排序 actions。`saveEntry` 新增可选 `category` 参数，有分类时在时间戳前插入 `[分类名]` 前缀。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, DataStore Preferences, `sh.calvin.reorderable:reorderable:2.4.0`

---

## 文件变更索引

| 文件 | 操作 |
|------|------|
| `gradle/libs.versions.toml` | 修改 — 新增 reorderable 版本和库条目 |
| `app/build.gradle.kts` | 修改 — 新增 reorderable 依赖 |
| `app/src/main/java/com/ylib/quicksave/data/source/AppDataStore.kt` | 修改 — 新增 categories/selectedCategory 接口方法 |
| `app/src/main/java/com/ylib/quicksave/data/source/AppDataStoreImpl.kt` | 修改 — 实现上述方法 |
| `app/src/main/java/com/ylib/quicksave/data/repository/ClipRepository.kt` | 修改 — `saveEntry` 新增 `category` 参数，新增 4 个分类方法 |
| `app/src/main/java/com/ylib/quicksave/data/repository/ClipRepositoryImpl.kt` | 修改 — 实现上述变更 |
| `app/src/test/java/com/ylib/quicksave/data/repository/ClipRepositoryImplTest.kt` | 新建 — 单元测试 |
| `app/src/main/java/com/ylib/quicksave/ui/viewmodel/HomeViewModel.kt` | 修改 — 新增分类状态与 actions |
| `app/src/main/java/com/ylib/quicksave/ui/screens/HomeScreen.kt` | 修改 — 剪切板卡片内新增 Chip 行与新增分类对话框 |
| `app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt` | 修改 — 新增分类管理 actions |
| `app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt` | 修改 — 新增分类管理区块（含拖拽排序） |

---

### Task 1: 添加 reorderable 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 在 libs.versions.toml 中新增版本和库条目**

在 `[versions]` 末尾追加：
```toml
reorderable = "2.4.0"
```

在 `[libraries]` 末尾追加：
```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

- [ ] **Step 2: 在 app/build.gradle.kts 中声明依赖**

在 `dependencies { }` 块的 `implementation(libs.androidx.lifecycle.viewmodel.compose)` 行之后添加：
```kotlin
implementation(libs.reorderable)
```

- [ ] **Step 3: 同步验证**

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep reorderable
```

期望输出包含：`sh.calvin.reorderable:reorderable:2.4.0`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add sh.calvin.reorderable dependency"
```

---

### Task 2: 扩展 AppDataStore 接口与实现

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/data/source/AppDataStore.kt`
- Modify: `app/src/main/java/com/ylib/quicksave/data/source/AppDataStoreImpl.kt`

- [ ] **Step 1: 更新 AppDataStore 接口**

完整替换 `AppDataStore.kt` 内容：

```kotlin
package com.ylib.quicksave.data.source

import kotlinx.coroutines.flow.Flow

interface AppDataStore {
    suspend fun saveTargetFileUri(uri: String)
    fun getTargetFileUri(): Flow<String?>
    suspend fun saveCategories(categories: List<String>)
    fun getCategories(): Flow<List<String>>
    suspend fun saveSelectedCategory(category: String?)
    fun getSelectedCategory(): Flow<String?>
}
```

- [ ] **Step 2: 更新 AppDataStoreImpl 实现**

完整替换 `AppDataStoreImpl.kt` 内容：

```kotlin
package com.ylib.quicksave.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quicksave_prefs")

class AppDataStoreImpl(private val context: Context) : AppDataStore {

    companion object {
        private val TARGET_FILE_URI = stringPreferencesKey("target_file_uri")
        private val CATEGORIES = stringPreferencesKey("categories")
        private val SELECTED_CATEGORY = stringPreferencesKey("selected_category")
    }

    override suspend fun saveTargetFileUri(uri: String) {
        context.dataStore.edit { it[TARGET_FILE_URI] = uri }
    }

    override fun getTargetFileUri(): Flow<String?> =
        context.dataStore.data.map { it[TARGET_FILE_URI] }

    override suspend fun saveCategories(categories: List<String>) {
        context.dataStore.edit { it[CATEGORIES] = categories.joinToString("\n") }
    }

    override fun getCategories(): Flow<List<String>> =
        context.dataStore.data.map {
            it[CATEGORIES]?.split("\n")?.filter { c -> c.isNotBlank() } ?: emptyList()
        }

    override suspend fun saveSelectedCategory(category: String?) {
        context.dataStore.edit { prefs ->
            if (category != null) prefs[SELECTED_CATEGORY] = category
            else prefs.remove(SELECTED_CATEGORY)
        }
    }

    override fun getSelectedCategory(): Flow<String?> =
        context.dataStore.data.map { it[SELECTED_CATEGORY] }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

期望：BUILD SUCCESSFUL，无编译错误

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/data/source/
git commit -m "feat: extend AppDataStore with categories and selectedCategory"
```

---

### Task 3: 更新 ClipRepository 接口与实现（TDD）

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/data/repository/ClipRepository.kt`
- Modify: `app/src/main/java/com/ylib/quicksave/data/repository/ClipRepositoryImpl.kt`
- Create: `app/src/test/java/com/ylib/quicksave/data/repository/ClipRepositoryImplTest.kt`

- [ ] **Step 1: 创建失败测试**

新建 `app/src/test/java/com/ylib/quicksave/data/repository/ClipRepositoryImplTest.kt`：

```kotlin
package com.ylib.quicksave.data.repository

import android.net.Uri
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.FileDataSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClipRepositoryImplTest {

    private val dataStore = mock<AppDataStore>()
    private val fileDataSource = mock<FileDataSource>()
    private val repo = ClipRepositoryImpl(dataStore, fileDataSource)

    @Test
    fun `saveEntry with category prepends category tag before timestamp`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)

        repo.saveEntry("hello world", category = "工作")

        verify(fileDataSource).appendLine(any(), argThat { startsWith("[工作][") })
    }

    @Test
    fun `saveEntry without category writes timestamp-only prefix`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)

        repo.saveEntry("hello world", category = null)

        verify(fileDataSource).appendLine(any(), argThat { startsWith("[20") && !contains("][20") })
    }

    @Test
    fun `saveEntry returns failure when no target URI set`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf(null))

        val result = repo.saveEntry("hello", category = null)

        assertTrue(result.isFailure)
        assertEquals("未设置目标文件", result.exceptionOrNull()?.message)
    }

    @Test
    fun `saveEntry returns failure when file not writable`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(false)

        val result = repo.saveEntry("hello", category = null)

        assertTrue(result.isFailure)
        assertEquals("目标文件无写入权限，请重新选择", result.exceptionOrNull()?.message)
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

```bash
./gradlew test --tests com.ylib.quicksave.data.repository.ClipRepositoryImplTest
```

期望：编译失败，`saveEntry` 签名不匹配

- [ ] **Step 3: 更新 ClipRepository 接口**

完整替换 `ClipRepository.kt` 内容：

```kotlin
package com.ylib.quicksave.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ClipRepository {
    suspend fun saveEntry(text: String, category: String? = null): Result<Unit>
    suspend fun setTargetFile(uri: Uri)
    fun getTargetFileUri(): Flow<Uri?>
    suspend fun clearSavedFile(): Result<Unit>
    fun getCategories(): Flow<List<String>>
    suspend fun setCategories(categories: List<String>)
    fun getSelectedCategory(): Flow<String?>
    suspend fun setSelectedCategory(category: String?)
}
```

- [ ] **Step 4: 更新 ClipRepositoryImpl 实现**

完整替换 `ClipRepositoryImpl.kt` 内容：

```kotlin
package com.ylib.quicksave.data.repository

import android.net.Uri
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.FileDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipRepositoryImpl(
    private val dataStore: AppDataStore,
    private val fileDataSource: FileDataSource
) : ClipRepository {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override suspend fun saveEntry(text: String, category: String?): Result<Unit> = runCatching {
        val uriString = dataStore.getTargetFileUri().first()
            ?: throw IllegalStateException("未设置目标文件")
        val uri = Uri.parse(uriString)
        if (!fileDataSource.isWritable(uri)) throw SecurityException("目标文件无写入权限，请重新选择")
        val prefix = if (category != null) "[$category]" else ""
        fileDataSource.appendLine(uri, "$prefix[${dateFormatter.format(Date())}] $text")
    }

    override suspend fun setTargetFile(uri: Uri) {
        dataStore.saveTargetFileUri(uri.toString())
    }

    override fun getTargetFileUri(): Flow<Uri?> =
        dataStore.getTargetFileUri().map { it?.let(Uri::parse) }

    override suspend fun clearSavedFile(): Result<Unit> = runCatching {
        val uriString = dataStore.getTargetFileUri().first()
            ?: throw IllegalStateException("未设置目标文件")
        val uri = Uri.parse(uriString)
        if (!fileDataSource.isWritable(uri)) throw SecurityException("目标文件无写入权限，请重新选择")
        fileDataSource.clearFile(uri)
    }

    override fun getCategories(): Flow<List<String>> = dataStore.getCategories()

    override suspend fun setCategories(categories: List<String>) =
        dataStore.saveCategories(categories)

    override fun getSelectedCategory(): Flow<String?> = dataStore.getSelectedCategory()

    override suspend fun setSelectedCategory(category: String?) =
        dataStore.saveSelectedCategory(category)
}
```

- [ ] **Step 5: 运行测试，确认全部通过**

```bash
./gradlew test --tests com.ylib.quicksave.data.repository.ClipRepositoryImplTest
```

期望：4 tests, 0 failures

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/data/repository/ \
        app/src/test/java/com/ylib/quicksave/data/repository/
git commit -m "feat: add category parameter to saveEntry and category CRUD to ClipRepository"
```

---

### Task 4: 更新 HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/ui/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: 完整替换 HomeViewModel.kt**

```kotlin
package com.ylib.quicksave.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val targetFileUri: String? = null,
    val clipText: String? = null,
    val isSaving: Boolean = false,
    val showClearDialog: Boolean = false,
    val lastSaveResult: SaveResult? = null,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val showAddCategoryDialog: Boolean = false
)

sealed class SaveResult {
    data object Success : SaveResult()
    data object ClearSuccess : SaveResult()
    data class Failure(val message: String) : SaveResult()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as QuickSaveApplication).clipRepository
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getTargetFileUri(),
                repo.getCategories(),
                repo.getSelectedCategory()
            ) { uri, categories, selected ->
                Triple(uri, categories, selected?.takeIf { it in categories })
            }.collect { (uri, categories, selected) ->
                _uiState.update {
                    it.copy(
                        targetFileUri = uri?.toString(),
                        categories = categories,
                        selectedCategory = selected
                    )
                }
            }
        }
    }

    fun readClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        _uiState.update { it.copy(clipText = text?.takeIf { t -> t.isNotBlank() }) }
    }

    fun saveClipboard() {
        val text = _uiState.value.clipText ?: return
        val category = _uiState.value.selectedCategory
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repo.saveEntry(text, category)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    lastSaveResult = if (result.isSuccess) SaveResult.Success
                    else SaveResult.Failure(result.exceptionOrNull()?.message ?: "保存失败")
                )
            }
        }
    }

    fun selectCategory(category: String?) {
        viewModelScope.launch { repo.setSelectedCategory(category) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _uiState.value.categories.any { it == trimmed }) return
        viewModelScope.launch {
            repo.setCategories(_uiState.value.categories + trimmed)
            repo.setSelectedCategory(trimmed)
        }
    }

    fun showAddCategoryDialog() = _uiState.update { it.copy(showAddCategoryDialog = true) }
    fun dismissAddCategoryDialog() = _uiState.update { it.copy(showAddCategoryDialog = false) }
    fun showClearDialog() = _uiState.update { it.copy(showClearDialog = true) }
    fun dismissClearDialog() = _uiState.update { it.copy(showClearDialog = false) }
    fun clearLastSaveResult() = _uiState.update { it.copy(lastSaveResult = null) }

    fun clearSavedFile() {
        viewModelScope.launch {
            val result = repo.clearSavedFile()
            _uiState.update {
                it.copy(
                    showClearDialog = false,
                    lastSaveResult = if (result.isSuccess) SaveResult.ClearSuccess
                    else SaveResult.Failure(result.exceptionOrNull()?.message ?: "清空失败")
                )
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

期望：BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/viewmodel/HomeViewModel.kt
git commit -m "feat: add category state and actions to HomeViewModel"
```

---

### Task 5: 更新 HomeScreen（Chip 选择器 + 新增对话框）

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/ui/screens/HomeScreen.kt`

- [ ] **Step 1: 完整替换 HomeScreen.kt**

```kotlin
package com.ylib.quicksave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ylib.quicksave.ui.viewmodel.HomeViewModel
import com.ylib.quicksave.ui.viewmodel.SaveResult

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val view = LocalView.current
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) viewModel.readClipboard(context)
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }

    LaunchedEffect(uiState.lastSaveResult) {
        uiState.lastSaveResult?.let { result ->
            snackbarHostState.showSnackbar(
                when (result) {
                    is SaveResult.Success -> "已保存"
                    is SaveResult.ClearSuccess -> "文件内容已清空"
                    is SaveResult.Failure -> "保存失败：${result.message}"
                }
            )
            viewModel.clearLastSaveResult()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("QuickSave", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
            }

            if (uiState.targetFileUri == null) {
                item { NoFileWarningCard { navController.navigate("settings") } }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { navController.navigate("settings") }) {
                        Text("设置")
                    }
                }
            }

            if (uiState.clipText != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "当前剪切板",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                uiState.clipText!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(uiState.categories) { category ->
                                    FilterChip(
                                        selected = uiState.selectedCategory == category,
                                        onClick = {
                                            viewModel.selectCategory(
                                                if (uiState.selectedCategory == category) null else category
                                            )
                                        },
                                        label = { Text(category) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.showAddCategoryDialog() },
                                        label = { Text("＋ 新增") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            labelColor = MaterialTheme.colorScheme.outline
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = false,
                                            borderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.saveClipboard() },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(if (uiState.isSaving) "保存中…" else "保存到文件 ▶")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "剪切板为空，请先在其他应用复制文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState.targetFileUri != null) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.showClearDialog() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("清空保存文件内容") }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        if (uiState.showClearDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissClearDialog() },
                title = { Text("清空保存文件") },
                text = { Text("确认清空文件内全部内容？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSavedFile() }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissClearDialog() }) { Text("取消") }
                }
            )
        }

        if (uiState.showAddCategoryDialog) {
            CategoryNameDialog(
                title = "新增分类",
                initialName = "",
                existingNames = uiState.categories,
                onConfirm = { name ->
                    viewModel.addCategory(name)
                    viewModel.dismissAddCategoryDialog()
                },
                onDismiss = { viewModel.dismissAddCategoryDialog() }
            )
        }
    }
}

@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val isDuplicate = existingNames.any { it == name.trim() }
    val isBlank = name.trim().isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("分类名称") },
                isError = isDuplicate,
                supportingText = if (isDuplicate) {
                    { Text("分类名已存在", color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = !isBlank && !isDuplicate
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NoFileWarningCard(onNavigateToSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "未设置保存文件",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("前往设置")
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

期望：BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/screens/HomeScreen.kt
git commit -m "feat: add category chip selector to clipboard card in HomeScreen"
```

---

### Task 6: 更新 SettingsViewModel

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt`

- [ ] **Step 1: 完整替换 SettingsViewModel.kt**

```kotlin
package com.ylib.quicksave.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as QuickSaveApplication).clipRepository

    val targetFileUri: StateFlow<Uri?> = repo.getTargetFileUri()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<String>> = repo.getCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTargetFile(uri: Uri) {
        viewModelScope.launch { repo.setTargetFile(uri) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || categories.value.any { it == trimmed }) return
        viewModelScope.launch { repo.setCategories(categories.value + trimmed) }
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || categories.value.any { it == trimmed }) return
        viewModelScope.launch {
            repo.setCategories(categories.value.map { if (it == oldName) trimmed else it })
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            repo.setCategories(categories.value.filter { it != name })
        }
    }

    fun reorderCategories(reordered: List<String>) {
        viewModelScope.launch { repo.setCategories(reordered) }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugKotlin
```

期望：BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/viewmodel/SettingsViewModel.kt
git commit -m "feat: add category management actions to SettingsViewModel"
```

---

### Task 7: 更新 SettingsScreen（分类管理区块 + 拖拽排序）

**Files:**
- Modify: `app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: 完整替换 SettingsScreen.kt**

```kotlin
package com.ylib.quicksave.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ylib.quicksave.ui.viewmodel.SettingsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = viewModel()) {
    val targetUri by viewModel.targetFileUri.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<String?>(null) }

    val categoryListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(categoryListState) { from, to ->
        val mutableList = categories.toMutableList()
        mutableList.add(to.index, mutableList.removeAt(from.index))
        viewModel.reorderCategories(mutableList)
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setTargetFile(uri)
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setTargetFile(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "保存目标文件",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (targetUri != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "当前文件：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        val displayPath = targetUri!!.lastPathSegment
                            ?.substringAfter(':')
                            ?.ifEmpty { targetUri.toString() }
                            ?: targetUri.toString()
                        Text(displayPath, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        openFileLauncher.launch(
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "text/plain"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新选择文件") }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "尚未设置，请选择保存文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    createFileLauncher.launch(
                                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TITLE, "quicksave.txt")
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("选择保存文件  ▶") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "保存的文字将追加到文件末尾，每条记录包含时间戳。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "示例：[2026-04-20 14:23:05] 这是保存的内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // 分类管理区块
            Spacer(Modifier.height(24.dp))
            Text(
                "分类管理",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (categories.isEmpty()) {
                Text(
                    "暂无分类，点击下方按钮添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    state = categoryListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    userScrollEnabled = false
                ) {
                    items(categories, key = { it }) { category ->
                        ReorderableItem(reorderableState, key = category) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 4.dp else 0.dp,
                                label = "elevation"
                            )
                            Surface(
                                shadowElevation = elevation,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "⠿",
                                        modifier = Modifier
                                            .draggableHandle()
                                            .padding(horizontal = 12.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        category,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(onClick = { renamingCategory = category }) {
                                        Text("重命名")
                                    }
                                    TextButton(onClick = { viewModel.deleteCategory(category) }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAddCategoryDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("＋ 新增分类") }

            Spacer(Modifier.height(8.dp))
            Text(
                "重命名分类不会修改已保存的记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddCategoryDialog) {
        CategoryNameDialog(
            title = "新增分类",
            initialName = "",
            existingNames = categories,
            onConfirm = { name ->
                viewModel.addCategory(name)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    renamingCategory?.let { oldName ->
        CategoryNameDialog(
            title = "重命名分类",
            initialName = oldName,
            existingNames = categories.filter { it != oldName },
            onConfirm = { newName ->
                viewModel.renameCategory(oldName, newName)
                renamingCategory = null
            },
            onDismiss = { renamingCategory = null }
        )
    }
}
```

> 注意：`CategoryNameDialog` 已在 `HomeScreen.kt` 中定义为 `public`，此处直接使用，无需重复定义。

- [ ] **Step 2: 编译并运行所有单元测试**

```bash
./gradlew :app:compileDebugKotlin && ./gradlew test
```

期望：BUILD SUCCESSFUL，所有测试通过

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ylib/quicksave/ui/screens/SettingsScreen.kt
git commit -m "feat: add category management section with drag-to-reorder in SettingsScreen"
```

---

## 完成后验证清单

- [ ] `./gradlew assembleDebug` — Debug APK 构建成功
- [ ] `./gradlew test` — 所有单元测试通过
- [ ] 手动验证：主页选择分类后点击保存，检查文件内容包含 `[分类名][时间戳]` 格式
- [ ] 手动验证：设置页可新增/重命名/删除/拖拽排序分类
- [ ] 手动验证：删除当前选中分类后，主页无分类选中（无标签格式）
- [ ] 手动验证：重启应用后当前选中分类保持不变
