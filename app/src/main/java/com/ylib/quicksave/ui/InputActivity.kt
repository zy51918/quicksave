package com.ylib.quicksave.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ylib.quicksave.ui.screens.CategoryChipRow
import com.ylib.quicksave.ui.screens.CategoryNameDialog
import com.ylib.quicksave.ui.screens.ManualInputCard
import com.ylib.quicksave.ui.theme.QuickSaveTheme
import com.ylib.quicksave.ui.viewmodel.HomeViewModel
import com.ylib.quicksave.ui.viewmodel.SaveResult

/**
 * 透明输入窗：从悬浮窗【文字输入】按钮拉起。
 * 复用主页手动输入的分类 Chip 行 + 输入卡片 + 保存逻辑（QS-0002）。
 * 保存成功后 Toast「已保存」并关闭；点窗口外区域或返回键取消。
 */
class InputActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickSaveTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(uiState.lastSaveResult) {
                    when (val result = uiState.lastSaveResult) {
                        is SaveResult.Success -> {
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            viewModel.clearLastSaveResult()
                            finish()
                        }
                        is SaveResult.Failure -> {
                            Toast.makeText(context, "保存失败：${result.message}", Toast.LENGTH_SHORT).show()
                            viewModel.clearLastSaveResult()
                        }
                        else -> {}
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            CategoryChipRow(
                                categories = uiState.categories,
                                selectedCategory = uiState.selectedCategory,
                                onSelect = viewModel::selectCategory,
                                onAddClick = viewModel::showAddCategoryDialog
                            )
                            Spacer(Modifier.height(12.dp))
                            ManualInputCard(
                                text = uiState.manualInputText,
                                onTextChange = viewModel::updateManualInput,
                                isSaving = uiState.isManualSaving,
                                onSave = viewModel::saveManualInput
                            )
                        }
                    }
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
    }
}
