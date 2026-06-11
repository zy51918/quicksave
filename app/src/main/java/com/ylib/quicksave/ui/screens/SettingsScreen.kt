package com.ylib.quicksave.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ylib.quicksave.overlay.OverlayService
import com.ylib.quicksave.ui.viewmodel.SettingsViewModel
import com.ylib.quicksave.util.PermissionHelper
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = viewModel()) {
    val targetUri by viewModel.targetFileUri.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    val overlayEnabled by viewModel.overlayEnabled.collectAsState()

    // 对账：若开关为开但悬浮窗权限已被系统撤销，则回退持久化状态，避免开关与现实不符
    LaunchedEffect(overlayEnabled) {
        if (overlayEnabled && !PermissionHelper.canDrawOverlays(context)) {
            viewModel.setOverlayEnabled(false)
        }
    }

    // 申请悬浮窗权限后回到本页：若已授权则开启并启动服务
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionHelper.canDrawOverlays(context)) {
            viewModel.setOverlayEnabled(true)
            context.startService(Intent(context, OverlayService::class.java))
        }
    }

    val onToggleOverlay: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (PermissionHelper.canDrawOverlays(context)) {
                viewModel.setOverlayEnabled(true)
                context.startService(Intent(context, OverlayService::class.java))
            } else {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        } else {
            viewModel.setOverlayEnabled(false)
            context.startService(
                Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP
                }
            )
        }
    }

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

            // 全局悬浮窗区块
            Spacer(Modifier.height(24.dp))
            Text(
                "全局悬浮窗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "启用全局悬浮窗",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { onToggleOverlay(it) }
                )
            }
            Text(
                "开启后将在所有应用之上显示一个贴边悬浮窗，点击展开快捷操作。需要授予\"显示在其他应用上层\"权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
