package com.example.smartdosing.ui.screens.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.repository.ConfigurationRecordFilter
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * 配置记录界面：研发阶段的配置成果，按品类分组展示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationRecordsScreen(
    refreshSignal: Int = 0,
    onNavigateBack: () -> Unit = {},
    onRecordSelected: (String) -> Unit = {}
) {
    val repository = remember { ConfigurationRepositoryProvider.recordRepository }
    var records by remember { mutableStateOf<List<ConfigurationRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var selectedCustomer by remember { mutableStateOf<String?>(null) }
    var selectedSales by remember { mutableStateOf<String?>(null) }
    var selectedPerfumer by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<ConfigurationRecordStatus?>(null) }
    var sortAscending by remember { mutableStateOf(false) }

    fun refreshRecords() {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                val filter = ConfigurationRecordFilter(
                    customer = selectedCustomer,
                    salesOwner = selectedSales,
                    operator = selectedPerfumer,
                    status = selectedStatus,
                    sortAscending = sortAscending
                )
                // 在 IO 线程拉取记录，避免阻塞主线程
                val result = withContext(Dispatchers.IO) {
                    repository.fetchRecords(filter)
                }
                records = result
            } catch (e: Exception) {
                loadError = "加载配置记录失败：${e.message ?: "未知错误"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshRecords() }
    LaunchedEffect(refreshSignal) { refreshRecords() }

    val customers = remember(records) { records.map { it.customer }.distinct() }
    val salesOwners = remember(records) { records.map { it.salesOwner }.distinct() }
    val perfumers = remember(records) { records.map { it.operator }.distinct() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置记录") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RecordSummaryRow(records = records)
            MultiFilterRow(
                label = "客户",
                options = customers,
                selected = selectedCustomer,
                onSelected = {
                    selectedCustomer = it
                    refreshRecords()
                }
            )
            MultiFilterRow(
                label = "业务员",
                options = salesOwners,
                selected = selectedSales,
                onSelected = {
                    selectedSales = it
                    refreshRecords()
                }
            )
            MultiFilterRow(
                label = "调香师",
                options = perfumers,
                selected = selectedPerfumer,
                onSelected = {
                    selectedPerfumer = it
                    refreshRecords()
                }
            )
            StatusFilterRow(
                selected = selectedStatus,
                onSelected = {
                    selectedStatus = it
                    refreshRecords()
                }
            )
            SortBar(
                ascending = sortAscending,
                onToggle = {
                    sortAscending = !sortAscending
                    refreshRecords()
                }
            )

            when {
                isLoading -> RecordLoadingState()
                loadError != null -> RecordErrorState(message = loadError!!, onRetry = { refreshRecords() })
                records.isEmpty() -> RecordEmptyState()
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            ConfigurationRecordCard(
                                record = record,
                                onClick = { onRecordSelected(record.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordSummaryRow(records: List<ConfigurationRecord>) {
    val completed = records.count { it.resultStatus == ConfigurationRecordStatus.COMPLETED }
    val running = records.count { it.resultStatus == ConfigurationRecordStatus.RUNNING }
    val review = records.count { it.resultStatus == ConfigurationRecordStatus.IN_REVIEW }
    val summaryItems = listOf(
        Triple("全部", records.size.toString(), MaterialTheme.colorScheme.primary),
        Triple("进行中", running.toString(), MaterialTheme.colorScheme.tertiary),
        Triple("待评估", review.toString(), MaterialTheme.colorScheme.secondary),
        Triple("已完成", completed.toString(), MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        summaryItems.forEach { (title, value, color) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, color = color, style = MaterialTheme.typography.labelSmall)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MultiFilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit
) {
    if (options.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AssistChip(
            onClick = { onSelected(null) },
            label = { Text("全部") },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                labelColor = if (selected == null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        options.forEach { option ->
            val isSelected = selected == option
            AssistChip(
                onClick = { onSelected(if (isSelected) null else option) },
                label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: ConfigurationRecordStatus?,
    onSelected: (ConfigurationRecordStatus?) -> Unit
) {
    val options = listOf<Pair<ConfigurationRecordStatus?, String>>(null to "全部") +
        ConfigurationRecordStatus.entries.map { status ->
            status to when (status) {
                ConfigurationRecordStatus.IN_REVIEW -> "待评估"
                ConfigurationRecordStatus.RUNNING -> "进行中"
                ConfigurationRecordStatus.COMPLETED -> "已完成"
                ConfigurationRecordStatus.ARCHIVED -> "已归档"
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEach { (status, label) ->
            val isSelected = selected == status
            AssistChip(
                onClick = { onSelected(if (isSelected) null else status) },
                label = { Text(label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SortBar(
    ascending: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ascending) "时间顺序" else "时间倒序",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onToggle) {
            Text(if (ascending) "改为倒序" else "改为顺序")
        }
    }
}

@Composable
private fun RecordLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text("正在加载配置记录…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun RecordEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("没有符合条件的配置记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("可以调整筛选或稍后再试", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(text = "$count 条记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ConfigurationRecordCard(
    record: ConfigurationRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = record.recipeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "任务 ${record.taskId} · ${record.updatedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                StatusChip(status = record.resultStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.recipeCode} | ${record.quantity} ${record.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "调香: ${record.operator}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "客户: ${record.customer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "业务员: ${record.salesOwner}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        record.tags.take(3).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (record.note.isNotBlank()) {
                Text(
                    text = "备注: ${record.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "实际产出: ${record.actualQuantity} ${record.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusChip(status: ConfigurationRecordStatus) {
    val (label, color) = when (status) {
        ConfigurationRecordStatus.IN_REVIEW -> "待评估" to MaterialTheme.colorScheme.secondary
        ConfigurationRecordStatus.RUNNING -> "进行中" to MaterialTheme.colorScheme.tertiary
        ConfigurationRecordStatus.COMPLETED -> "已完成" to MaterialTheme.colorScheme.primary
        ConfigurationRecordStatus.ARCHIVED -> "已归档" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfigurationRecordsScreenPreview() {
    SmartDosingTheme {
        ConfigurationRecordsScreen()
    }
}

