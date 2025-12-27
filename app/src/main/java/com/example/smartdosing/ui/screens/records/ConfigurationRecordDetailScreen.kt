package com.example.smartdosing.ui.screens.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationMaterialRecord
import java.text.DecimalFormat
import androidx.compose.material.icons.filled.ArrowForward
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordSampleData
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.theme.SmartDosingTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationRecordDetailScreen(
    recordId: String,
    onNavigateBack: () -> Unit = {},
    onReconfigure: (ConfigurationRecord) -> Unit = {},
    onFixError: (ConfigurationRecord) -> Unit = {}
) {
    val repository = remember { ConfigurationRepositoryProvider.recordRepository }
    var record by remember(recordId) { mutableStateOf<ConfigurationRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshDetail() {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                record = repository.fetchRecord(recordId)
                if (record == null) {
                    loadError = "未找到该配置记录"
                }
            } catch (e: Exception) {
                loadError = e.message ?: "加载失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun handleFix(recordToFix: ConfigurationRecord) {
        scope.launch {
            try {
                val updated = repository.updateRecordStatus(recordToFix.id, ConfigurationRecordStatus.IN_REVIEW)
                if (updated != null) {
                    record = updated
                    actionMessage = "已标记为待评估"
                    onFixError(updated)
                } else {
                    actionMessage = "无法更新记录状态"
                }
            } catch (e: Exception) {
                actionMessage = e.message ?: "纠错失败"
            }
        }
    }

    LaunchedEffect(recordId) {
        refreshDetail()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            loadError != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                }
            }
            record == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("未找到配置记录", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    record?.let { current ->
                        DetailCard(record = current)
                        if (current.materialDetails.isNotEmpty()) {
                            MaterialDetailCard(materials = current.materialDetails)
                        } else {
                            EmptyMaterialHint()
                        }
                        ActionCard(
                            record = current,
                            actionMessage = actionMessage,
                            onReconfigure = onReconfigure,
                            onFixError = { handleFix(current) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCard(record: ConfigurationRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 第一行：标题与状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.recipeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${record.recipeCode} · ${record.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 状态标签
                Surface(
                    color = when(record.resultStatus) {
                        ConfigurationRecordStatus.COMPLETED -> Color(0xFFE8F5E9)
                        ConfigurationRecordStatus.IN_REVIEW -> Color(0xFFFFF3E0)
                        ConfigurationRecordStatus.RUNNING -> Color(0xFFE3F2FD)
                        else -> Color.Gray.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when(record.resultStatus) {
                            ConfigurationRecordStatus.COMPLETED -> "已完成"
                            ConfigurationRecordStatus.IN_REVIEW -> "待评估"
                            ConfigurationRecordStatus.RUNNING -> "进行中"
                            ConfigurationRecordStatus.ARCHIVED -> "已归档"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = when(record.resultStatus) {
                            ConfigurationRecordStatus.COMPLETED -> Color(0xFF2E7D32)
                            ConfigurationRecordStatus.IN_REVIEW -> Color(0xFFE65100)
                            ConfigurationRecordStatus.RUNNING -> Color(0xFF1565C0)
                            else -> Color.Gray
                        }
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 第二行：关键人员与时间（紧凑排版）
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    CompactInfoItem("客户", record.customer)
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactInfoItem("任务ID", record.taskId)
                }
                Column(modifier = Modifier.weight(1f)) {
                    CompactInfoItem("业务员", record.salesOwner)
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactInfoItem("更新时间", record.updatedAt)
                }
                Column(modifier = Modifier.weight(1f)) {
                    CompactInfoItem("调香师", record.operator)
                }
            }

            // 第三行：产量统计
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "目标产量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${DecimalFormat("#.##").format(record.quantity)} ${record.unit}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "实际产量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val totalDeviation = record.actualQuantity - record.quantity
                        val isDeviated = kotlin.math.abs(totalDeviation) > (record.quantity * 0.01)
                        Text(
                            text = "${DecimalFormat("#.##").format(record.actualQuantity)} ${record.unit}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeviated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (record.note.isNotBlank()) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "备注：${record.note}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = Color(0xFFF57C00)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInfoItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionCard(
    record: ConfigurationRecord,
    actionMessage: String?,
    onReconfigure: (ConfigurationRecord) -> Unit,
    onFixError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "您可以重新配置此记录，或者尝试修复可能存在的问题。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onReconfigure(record) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重新配置")
                }
                OutlinedButton(
                    onClick = onFixError,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("修复问题")
                }
            }
            actionMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun MaterialDetailCard(materials: List<ConfigurationMaterialRecord>) {
    var expanded by remember { mutableStateOf(true) } // 默认展开
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "材料明细",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "共 ${materials.size} 项记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp) // 增加最大高度
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "#", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(text = "材料", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall)
                Text(text = "目标", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(text = "实际", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(text = "偏差", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            
            materials.sortedBy { it.sequence }.forEachIndexed { index, detail ->
                MaterialDetailRow(detail)
                if (index < materials.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
        }
    }
}

@Composable
private fun MaterialDetailRow(detail: ConfigurationMaterialRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (detail.isOutOfTolerance) Color(0xFFFFEBEE) else Color.Transparent) // 超标行浅红背景
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号
        Text(
            text = detail.sequence.toString(),
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 材料名称与编码
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail.code.isNotBlank()) {
                Text(
                    text = detail.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        val df = DecimalFormat("#.###")
        
        // 目标值
        Text(
            text = df.format(detail.targetWeight),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 实际值 (超标红色粗体)
        Text(
            text = df.format(detail.actualWeight),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            fontWeight = if (detail.isOutOfTolerance) FontWeight.Bold else FontWeight.Normal,
            color = if (detail.isOutOfTolerance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        
        // 偏差值 (超标红色粗体)
        val sign = if (detail.deviation > 0) "+" else ""
        Text(
            text = "${sign}${df.format(detail.deviation)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            fontWeight = if (detail.isOutOfTolerance) FontWeight.Bold else FontWeight.Normal,
            color = if (detail.isOutOfTolerance) MaterialTheme.colorScheme.error else Color(0xFF2E7D32) // 正常显示绿色/黑色
        )
    }
}

@Composable
private fun EmptyMaterialHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "尚未添加材料明细",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "提交配置记录时可附带材料及重量，方便追溯",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationRecordDetailScreenPreview() {
    SmartDosingTheme {
        val record = ConfigurationRecordSampleData.records().first()
        // 模拟一个超标数据用于预览
        val modifiedMaterial = record.materialDetails[0].copy(
            isOutOfTolerance = true,
            deviation = -0.5,
            actualWeight = 4.5
        )
        val modifiedRecord = record.copy(materialDetails = listOf(modifiedMaterial) + record.materialDetails.drop(1))
        
        ConfigurationRecordDetailScreen(
            recordId = record.id
        )
    }
}
