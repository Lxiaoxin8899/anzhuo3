package com.example.smartdosing.ui.screens.dosing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.RecipeRepository
import com.example.smartdosing.ui.theme.SmartDosingTheme
import java.util.Locale

/**
 * 投料作业检查清单界面
 * 检查配方名称/编码/投料重量三项后才能进入投料界面
 */
@Composable
fun DosingScreen(
    recipeId: String? = null,
    onNavigateToDosingOperation: (String) -> Unit = {},
    onNavigateToRecipeList: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember { RecipeRepository.getInstance() }
    val checklistInfo = remember(recipeId) {
        recipeId?.takeIf { it.isNotBlank() && it != "quick_start" }?.let { id ->
            repository.getRecipeById(id)?.toChecklistInfo()
        }
    }

    if (checklistInfo == null) {
        EmptyChecklistState(
            onNavigateToRecipeList = onNavigateToRecipeList,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    } else {
        DosingChecklistContent(
            recipe = checklistInfo,
            onNavigateToDosingOperation = onNavigateToDosingOperation,
            modifier = modifier
        )
    }
}

/**
 * 检查清单主体内容
 */
@Composable
private fun DosingChecklistContent(
    recipe: RecipeChecklistInfo,
    onNavigateToDosingOperation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val checklistItems = remember(recipe.id) {
        listOf(
            ChecklistSummaryItem(
                id = "name",
                label = "配方名称",
                value = recipe.name,
                hint = "确认名称与生产任务一致"
            ),
            ChecklistSummaryItem(
                id = "code",
                label = "配方编码",
                value = recipe.code,
                hint = "确认编码与系统记录一致"
            ),
            ChecklistSummaryItem(
                id = "weight",
                label = "投料重量",
                value = formatWeight(recipe.totalWeight, recipe.weightUnit),
                hint = "确认称量重量与工艺要求一致"
            )
        )
    }
    val confirmations = remember(recipe.id) {
        mutableStateMapOf<String, Boolean>().apply {
            checklistItems.forEach { this[it.id] = false }
        }
    }
    val confirmedCount = confirmations.values.count { it }
    val allConfirmed = checklistItems.isNotEmpty() && confirmedCount == checklistItems.size
    val progress = if (checklistItems.isEmpty()) 0f else confirmedCount / checklistItems.size.toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "投料作业检查清单",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "请依次确认配方名称、配方编码和投料重量，全部确认后即可进入投料界面。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        RecipeInfoCard(recipe = recipe)

        ChecklistSummarySection(
            items = checklistItems,
            confirmations = confirmations,
            progress = progress,
            onItemChecked = { itemId, checked ->
                confirmations[itemId] = checked
            }
        )

        ChecklistActionBar(
            totalCount = checklistItems.size,
            confirmedCount = confirmedCount,
            allConfirmed = allConfirmed,
            onConfirmAll = {
                confirmations.keys.forEach { confirmations[it] = true }
            },
            onStartDosing = {
                onNavigateToDosingOperation(recipe.id)
            }
        )
    }
}

/**
 * 无配方时的占位态
 */
@Composable
private fun EmptyChecklistState(
    onNavigateToRecipeList: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "尚未选择配方",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请先在配方管理中选择需要投料的配方，然后进入检查清单进行确认。",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNavigateToRecipeList,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("前往配方管理", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("返回上一页", fontSize = 16.sp)
        }
    }
}

/**
 * 配方信息卡片
 */
@Composable
private fun RecipeInfoCard(
    recipe: RecipeChecklistInfo
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(recipe.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("配方编码：${recipe.code}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()
            InfoRow(label = "投料重量", value = formatWeight(recipe.totalWeight, recipe.weightUnit))
            InfoRow(label = "检查项数量", value = "3 项")
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 检查项列表
 */
@Composable
private fun ChecklistSummarySection(
    items: List<ChecklistSummaryItem>,
    confirmations: Map<String, Boolean>,
    progress: Float,
    onItemChecked: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("确认内容", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已确认 ${confirmations.values.count { it }} / ${items.size}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Divider()
            items.forEachIndexed { index, item ->
                ChecklistSummaryItemRow(
                    item = item,
                    checked = confirmations[item.id] == true,
                    onCheckedChange = { onItemChecked(item.id, it) }
                )
                if (index < items.lastIndex) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/**
 * 单行检查项
 */
@Composable
private fun ChecklistSummaryItemRow(
    item: ChecklistSummaryItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(item.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = item.value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.hint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 操作区
 */
@Composable
private fun ChecklistActionBar(
    totalCount: Int,
    confirmedCount: Int,
    allConfirmed: Boolean,
    onConfirmAll: () -> Unit,
    onStartDosing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "需完成全部 $totalCount 项确认后才能开始投料。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onConfirmAll,
                enabled = confirmedCount < totalCount,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Default.DoneAll, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("全部标记完成")
            }

            Button(
                onClick = onStartDosing,
                enabled = allConfirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("全部确认，开始投料")
            }
        }
    }
}

/**
 * 工具函数：格式化重量
 */
private fun formatWeight(weight: Double, unit: String): String {
    val normalizedUnit = unit.uppercase(Locale.getDefault())
    return if (weight % 1.0 == 0.0) {
        "${weight.toInt()} $normalizedUnit"
    } else {
        String.format(Locale.getDefault(), "%.2f %s", weight, normalizedUnit)
    }
}

/**
 * 将完整配方转换为检查清单所需信息
 */
private fun Recipe.toChecklistInfo(): RecipeChecklistInfo {
    val unit = materials.firstOrNull()?.unit ?: "kg"
    return RecipeChecklistInfo(
        id = id,
        name = name,
        code = code,
        totalWeight = totalWeight,
        weightUnit = unit
    )
}

data class RecipeChecklistInfo(
    val id: String,
    val name: String,
    val code: String,
    val totalWeight: Double,
    val weightUnit: String
)

data class ChecklistSummaryItem(
    val id: String,
    val label: String,
    val value: String,
    val hint: String
)

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DosingScreenPreview() {
    SmartDosingTheme {
        DosingChecklistContent(
            recipe = RecipeChecklistInfo(
                id = "preview",
                name = "工业配方 A1",
                code = "REC-2024-001",
                totalWeight = 125.5,
                weightUnit = "kg"
            ),
            onNavigateToDosingOperation = {}
        )
    }
}
