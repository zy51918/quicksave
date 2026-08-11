package com.ylib.quicksave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ylib.quicksave.ui.theme.Dim
import com.ylib.quicksave.ui.viewmodel.HomeViewModel
import com.ylib.quicksave.ui.viewmodel.SaveResult

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickSave") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dim.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dim.itemSpacing),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = Dim.screenVertical
            )
        ) {
            if (uiState.targetFileUri == null) {
                item { NoFileWarningCard { navController.navigate("settings") } }
            }

            item {
                CategoryChipRow(
                    categories = uiState.categories,
                    selectedCategory = uiState.selectedCategory,
                    onSelect = viewModel::selectCategory,
                    onAddClick = viewModel::showAddCategoryDialog
                )
            }

            if (uiState.clipText != null) {
                item {
                    ClipboardCard(
                        clipText = uiState.clipText!!,
                        isSaving = uiState.isClipSaving,
                        onSave = viewModel::saveClipboard
                    )
                }
            } else {
                item {
                    Text(
                        "剪切板为空，请先在其他应用复制文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dim.screenVertical),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                ManualInputCard(
                    text = uiState.manualInputText,
                    onTextChange = viewModel::updateManualInput,
                    isSaving = uiState.isManualSaving,
                    onSave = viewModel::saveManualInput
                )
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
internal fun CategoryChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag(TAG_CATEGORY_CHIP_ROW)) {
        Text(
            "分类（可选）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dim.labelToContent))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dim.chipSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        onSelect(if (selectedCategory == category) null else category)
                    },
                    label = { Text(category) }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onAddClick,
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
    }
}

@Composable
internal fun ClipboardCard(
    clipText: String,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(TAG_CLIPBOARD_CARD),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(Dim.cardPadding)) {
            Text(
                "当前剪切板",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dim.cardLabelToBody))
            Text(
                clipText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Dim.cardBodyToAction))
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (isSaving) "保存中…" else "保存到文件")
            }
        }
    }
}

@Composable
internal fun ManualInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSave = text.isNotBlank() && !isSaving
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_MANUAL_INPUT_CARD),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(Dim.cardPadding)) {
            Text(
                "手动输入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dim.cardLabelToBody))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("在此输入要保存的文字") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_MANUAL_INPUT_FIELD)
            )
            Spacer(Modifier.height(Dim.cardBodyToAction))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(TAG_MANUAL_SAVE_BUTTON)
            ) {
                Text(if (isSaving) "保存中…" else "保存到文件")
            }
        }
    }
}

internal const val TAG_MANUAL_INPUT_CARD = "manual_input_card"
internal const val TAG_MANUAL_INPUT_FIELD = "manual_input_field"
internal const val TAG_MANUAL_SAVE_BUTTON = "manual_save_button"
internal const val TAG_CATEGORY_CHIP_ROW = "category_chip_row"
internal const val TAG_CLIPBOARD_CARD = "clipboard_card"

@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val isDuplicate = existingNames.any { it == name.trim() }
    val isBlank = name.trim().isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("\n", "") },
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
            Column(modifier = Modifier.padding(Dim.cardPadding)) {
                Text(
                    "未设置保存文件",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(Dim.labelToContent))
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("前往设置")
                }
            }
        }
    }
}
