package com.example.smartdosing.ui.screens.tasks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.TaskPriority
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import kotlinx.coroutines.launch
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass

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
    val windowSize = LocalWindowSize.current
    val isCompactWidth = windowSize.widthClass == SmartDosingWindowWidthClass.Compact
    var tasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<TaskStatus?>(null) }
    val scope = rememberCoroutineScope()
    
    // 接单确认对话框状态
    var showAcceptDialog by remember { mutableStateOf(false) }
    var taskToAccept by remember { mutableStateOf<ConfigurationTask?>(null) }
    
    // Snackbar状态
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showSnackbar by remember { mutableStateOf(false) }
    
    // 获取当前用户（这里暂时硬编码，实际应该从用户session获取）
    val currentUser = "操作员" // TODO: 从实际的用户session或preferences获取

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
                title = {
                    Text(
                        "任务中心",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            if (showSnackbar) {
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(snackbarMessage ?: "")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TaskSummarySection(tasks = tasks, isCompact = isCompactWidth)
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
                        itemsIndexed(displayedTasks, key = { _, task -> task.id }) { index, task ->
                            AnimatedTaskCard(
                                task = task,
                                index = index,
                                onAccept = {
                                    // 显示确认对话框
                                    taskToAccept = task
                                    showAcceptDialog = true
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
        
        // 接单确认对话框
        if (showAcceptDialog && taskToAccept != null) {
            AcceptTaskConfirmDialog(
                task = taskToAccept!!,
                onConfirm = {
                    scope.launch {
                        // 1. 尝试调用接口接单
                        var acceptedTask = repository.acceptTask(taskToAccept!!.id, currentUser)
                        
                        // 2. 如果接口失败（返回null），则进行本地逻辑接单（离线/伪接单）
                        if (acceptedTask == null) {
                            // 构造一个本地更新后的任务对象
                            acceptedTask = taskToAccept!!.copy(
                                status = TaskStatus.IN_PROGRESS,
                                acceptedBy = currentUser,
                                acceptedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            // 提示用户（可选：提示是离线接单）
                            snackbarMessage = "已接单 (本地模式)"
                        } else {
                            snackbarMessage = "已成功接单：${acceptedTask.recipeName}"
                        }

                        // 3. 更新UI状态
                        showSnackbar = true
                        
                        // 手动更新当前列表中的该任务状态，实现“即时响应”
                        tasks = tasks.map { 
                            if (it.id == acceptedTask!!.id) acceptedTask!! else it 
                        }
                        
                        // 触发回调
                        onAcceptTask(acceptedTask!!)

                        // 4. 关闭对话框
                        showAcceptDialog = false
                        taskToAccept = null
                        
                        // 5. 尝试刷新（如果是真联网成功，刷新会获取最新状态；如果失败，刷新可能会重置状态，所以最好仅在成功时刷新，或者不仅赖刷新）
                        // 为防止刷新后状态回退（如果后端没存上），暂不强制立即刷新整个列表，而是信赖上面的本地修改。
                        // refreshTasks() 
                    }
                },
                onDismiss = {
                    showAcceptDialog = false
                    taskToAccept = null
                }
            )
        }
        
        // Snackbar自动隐藏
        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                kotlinx.coroutines.delay(3000)
                showSnackbar = false
            }
        }
    }
}

/**
 * 带动画的任务卡片
 */
@Composable
private fun AnimatedTaskCard(
    task: ConfigurationTask,
    index: Int,
    onAccept: () -> Unit,
    onStart: () -> Unit,
    onConfigure: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(300)
                )
    ) {
        TaskCard(
            task = task,
            onAccept = onAccept,
            onStart = onStart,
            onConfigure = onConfigure
        )
    }
}

/**
 * 顶部概览，展示待接单、进行中等数量
 */
@Composable
private fun TaskSummarySection(tasks: List<ConfigurationTask>, isCompact: Boolean) {
    val waitingStatuses = setOf(TaskStatus.DRAFT, TaskStatus.READY)
    val runningStatuses = setOf(TaskStatus.PUBLISHED, TaskStatus.IN_PROGRESS)
    val waitingCount = tasks.count { it.status in waitingStatuses }
    val progressingCount = tasks.count { it.status in runningStatuses }
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val summaryData = listOf(
        SummaryCardData(
            title = "待接单",
            value = waitingCount,
            icon = Icons.Default.HourglassEmpty,
            color = MaterialTheme.colorScheme.primary
        ),
        SummaryCardData(
            title = "进行中",
            value = progressingCount,
            icon = Icons.Default.TrendingUp,
            color = MaterialTheme.colorScheme.tertiary
        ),
        SummaryCardData(
            title = "已完成",
            value = completedCount,
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.secondary
        )
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + expandVertically()
    ) {
        if (isCompact) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(summaryData) { data ->
                    SummaryCard(
                        title = data.title,
                        value = data.value,
                        icon = data.icon,
                        color = data.color,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                summaryData.forEach { data ->
                    SummaryCard(
                        title = data.title,
                        value = data.value,
                        icon = data.icon,
                        color = data.color,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class SummaryCardData(
    val title: String,
    val value: Int,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
private fun SummaryCard(
    title: String,
    value: Int,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = animatedValue.value.toInt().toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TaskLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "正在加载任务，请稍候…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun TaskErrorState(message: String, onRetry: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            FilledTonalButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun TaskEmptyState() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "暂无符合条件的任务",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "可尝试调整筛选条件或稍后再试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(options) { index, status ->
            val isSelected = selected == status
            var visible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 40L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.8f)
            ) {
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
            val isAccepted = task.status != TaskStatus.DRAFT && task.status != TaskStatus.READY
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PriorityTag(priority = task.priority)
                Text(
                    text = if (isAccepted) task.title.ifBlank { task.recipeName } else "待接单任务",
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
                    value = if (isAccepted) task.recipeCode else "***",
                    secondary = if (isAccepted) "数量 ${task.quantity} ${task.unit}" else "数量 ***"
                )
                InfoRow(
                    title = "调香师",
                    value = if (isAccepted) (task.perfumer.ifBlank { task.requestedBy.ifBlank { "未指定" } }) else "***",
                    secondary = if (isAccepted) "客户 ${task.customer.ifBlank { "未指定" }}" else "客户 ***"
                )
                InfoRow(
                    title = "业务员",
                    value = if (isAccepted) task.salesOwner.ifBlank { "未指定" } else "***",
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

/**
 * 接单确认对话框
 */
@Composable
private fun AcceptTaskConfirmDialog(
    task: ConfigurationTask,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "确认接单",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "您确定要接这个任务吗？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "任务名称",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                task.recipeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "配方编码",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                task.recipeCode,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "配置数量",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${task.quantity} ${task.unit}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "截止时间",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                task.deadline.ifBlank { "待安排" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.deadline.contains("今天")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm
            ) {
                Text("确认接单")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
