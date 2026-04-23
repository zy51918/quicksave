package com.ylib.quicksave.ui.screens

import androidx.compose.foundation.background
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
