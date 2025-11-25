package com.example.smartdosing.ui.screens.tasks

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.TaskPriority
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import kotlinx.coroutines.launch
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * 任务中心界面：展示研发阶段快速下发的配置任务
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TaskCenterScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onAcceptTask: (ConfigurationTask) -> Unit = {},
    onStartTask: (ConfigurationTask) -> Unit = {},
    onConfigureTask: (ConfigurationTask) -> Unit = {},
    refreshSignal: Int = 0
) {
    val repository = remember { ConfigurationRepositoryProvider.taskRepository }
    var tasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<TaskStatus?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshTasks() {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                tasks = repository.fetchTasks()
            } catch (e: Exception) {
                loadError = "加载任务失败：${e.message ?: "未知错误"}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshTasks()
    }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal >= 0) {
            refreshTasks()
        }
    }

    val displayedTasks = remember(selectedStatus, tasks) {
        selectedStatus?.let { status -> tasks.filter { it.status == status } } ?: tasks
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("任务中心") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
            TaskSummarySection(tasks = tasks)
            TaskStatusFilterRow(
                selected = selectedStatus,
                onSelected = { selectedStatus = it }
            )
            when {
                isLoading -> TaskLoadingState()
                loadError != null -> TaskErrorState(message = loadError!!, onRetry = { refreshTasks() })
                displayedTasks.isEmpty() -> TaskEmptyState()
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedTasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onAccept = {
                                    scope.launch {
                                        repository.updateTaskStatus(task.id, TaskStatus.IN_PROGRESS)
                                        refreshTasks()
                                    }
                                    onAcceptTask(task)
                                },
                                onStart = {
                                    scope.launch {
                                        repository.updateTaskStatus(task.id, TaskStatus.IN_PROGRESS)
                                        refreshTasks()
                                    }
                                    onStartTask(task)
                                },
                                onConfigure = {
                                    onConfigureTask(task)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部概览，展示待接单、进行中等数量
 */
@Composable
private fun TaskSummarySection(tasks: List<ConfigurationTask>) {
    val waitingStatuses = setOf(TaskStatus.DRAFT, TaskStatus.READY)
    val runningStatuses = setOf(TaskStatus.PUBLISHED, TaskStatus.IN_PROGRESS)
    val waitingCount = tasks.count { it.status in waitingStatuses }
    val progressingCount = tasks.count { it.status in runningStatuses }
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            title = "待接单",
            value = waitingCount.toString(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "进行中",
            value = progressingCount.toString(),
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "已完成",
            value = completedCount.toString(),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TaskLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Text("正在加载任务，请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TaskErrorState(message: String, onRetry: () -> Unit) {
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
private fun TaskEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("暂无符合条件的任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("可尝试调整筛选条件或稍后再试", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 状态筛选：快速切换不同任务阶段
 */
@Composable
private fun TaskStatusFilterRow(
    selected: TaskStatus?,
    onSelected: (TaskStatus?) -> Unit
) {
    val options = listOf<TaskStatus?>(null) + TaskStatus.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { status ->
            val isSelected = selected == status
            AssistChip(
                onClick = { onSelected(if (isSelected) null else status) },
                label = {
                    val label = when (status) {
                        null -> "全部"
                        TaskStatus.DRAFT -> "草稿"
                        TaskStatus.READY -> "待发布"
                        TaskStatus.PUBLISHED -> "已下发"
                        TaskStatus.IN_PROGRESS -> "执行中"
                        TaskStatus.COMPLETED -> "已完成"
                        TaskStatus.CANCELLED -> "已取消"
                    }
                    Text(label)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 单个任务卡片
 */
@Composable
private fun TaskCard(
    task: ConfigurationTask,
    onAccept: () -> Unit,
    onStart: () -> Unit,
    onConfigure: () -> Unit
) {
    var detailExpanded by remember(task.id) { mutableStateOf(false) }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PriorityTag(priority = task.priority)
                Text(
                    text = task.title.ifBlank { task.recipeName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusTag(status = task.status)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow(
                    title = "编码",
                    value = task.recipeCode,
                    secondary = "数量 ${task.quantity} ${task.unit}"
                )
                InfoRow(
                    title = "调香师",
                    value = task.perfumer.ifBlank { task.requestedBy.ifBlank { "未指定" } },
                    secondary = "客户 ${task.customer.ifBlank { "未指定" }}"
                )
                InfoRow(
                    title = "业务员",
                    value = task.salesOwner.ifBlank { "未指定" },
                    secondary = "截止 ${task.deadline.ifBlank { "待安排" }}"
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (task.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        task.tags.take(3).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = task.deadline.ifBlank { "待安排" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.note.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "备注",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { detailExpanded = !detailExpanded }) {
                            Text(if (detailExpanded) "收起" else "展开")
                        }
                    }
                    if (detailExpanded) {
                        Text(
                            text = task.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = task.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == TaskStatus.DRAFT || task.status == TaskStatus.READY) {
                    TextButton(onClick = onAccept) {
                        Text("接单")
                    }
                }
                val canConfigure = task.status != TaskStatus.COMPLETED && task.status != TaskStatus.CANCELLED
                TextButton(onClick = onConfigure, enabled = canConfigure) {
                    Text("去配置")
                }
                FilledTonalButton(
                    onClick = onStart,
                    enabled = canConfigure
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val startLabel = when (task.status) {
                        TaskStatus.IN_PROGRESS -> "继续配置"
                        TaskStatus.PUBLISHED -> "进入设备"
                        else -> "开始配置"
                    }
                    Text(startLabel)
                }
            }
        }
    }
}

@Composable
private fun PriorityTag(priority: TaskPriority) {
    val color = when (priority) {
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
        TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        TaskPriority.URGENT -> MaterialTheme.colorScheme.error
    }
    Text(
        text = when (priority) {
            TaskPriority.LOW -> "低"
            TaskPriority.NORMAL -> "普通"
            TaskPriority.HIGH -> "高"
            TaskPriority.URGENT -> "加急"
        },
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatusTag(status: TaskStatus) {
    val (label, color) = when (status) {
        TaskStatus.DRAFT -> "草稿" to MaterialTheme.colorScheme.surfaceVariant
        TaskStatus.READY -> "待发布" to MaterialTheme.colorScheme.primary
        TaskStatus.PUBLISHED -> "已下发" to MaterialTheme.colorScheme.tertiary
        TaskStatus.IN_PROGRESS -> "执行中" to MaterialTheme.colorScheme.tertiary
        TaskStatus.COMPLETED -> "已完成" to MaterialTheme.colorScheme.secondary
        TaskStatus.CANCELLED -> "已取消" to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun InfoBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    secondary: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        secondary?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskCenterScreenPreview() {
    SmartDosingTheme {
        TaskCenterScreen()
    }
}
