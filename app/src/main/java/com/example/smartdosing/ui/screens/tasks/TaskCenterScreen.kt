package com.example.smartdosing.ui.screens.tasks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.TaskPriority
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.components.ErrorState
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import kotlinx.coroutines.launch

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
    onWaitingTaskCountChanged: (Int) -> Unit = {},
    onViewRecipeLibrary: (() -> Unit)? = null,
    showTopBar: Boolean = false,
    refreshSignal: Int = 0
) {
    val context = LocalContext.current
    val repository = remember { ConfigurationRepositoryProvider.taskRepository }
    val windowSize = LocalWindowSize.current
    val isCompactWidth = windowSize.widthClass == SmartDosingWindowWidthClass.Compact
    var allTasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<TaskLoadError?>(null) }
    var selectedStatus by remember { mutableStateOf<TaskStatus?>(null) }
    val scope = rememberCoroutineScope()
    
    // 接单确认对话框状态
    var showAcceptDialog by remember { mutableStateOf(false) }
    var taskToAccept by remember { mutableStateOf<ConfigurationTask?>(null) }
    
    // Snackbar状态
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showSnackbar by remember { mutableStateOf(false) }

    // 优先使用设备名作为操作员标识
    val currentUser = remember {
        DeviceUIDManager.getDeviceIdentity(context).deviceName.ifBlank { "本机操作员" }
    }

    fun refreshTasks() {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                allTasks = repository.fetchTasks()
                onWaitingTaskCountChanged(countWaitingTasks(allTasks))
                // 默认只显示活跃任务（排除已完成、已取消）
                tasks = allTasks.filter {
                    it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED
                }
            } catch (e: Exception) {
                loadError = e.toTaskLoadError()
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

    val displayedTasks = remember(selectedStatus, tasks, allTasks) {
        selectedStatus?.let { status ->
            allTasks.filter { it.status == status }  // 用户主动筛选时从全量数据中过滤
        } ?: tasks  // 默认显示活跃任务
    }

    val content = @Composable { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isCompactWidth) 16.dp else 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TaskSummarySection(tasks = allTasks, isCompact = isCompactWidth)
            
            TaskStatusFilterRow(
                selected = selectedStatus,
                onSelected = { selectedStatus = it }
            )

            when {
                isLoading -> TaskLoadingState()
                loadError != null -> TaskErrorState(
                    error = loadError!!,
                    onRetry = { refreshTasks() },
                    onViewRecipeLibrary = onViewRecipeLibrary
                )
                displayedTasks.isEmpty() -> TaskEmptyState()
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(displayedTasks, key = { _, task -> task.id }) { index, task ->
                            AnimatedTaskCard(
                                task = task,
                                index = index,
                                onAccept = {
                                    taskToAccept = task
                                    showAcceptDialog = true
                                },
                                onStart = {
                                    scope.launch {
                                        val newStatus = TaskStatus.IN_PROGRESS
                                        repository.updateTaskStatus(task.id, newStatus)
                                        allTasks = allTasks.map {
                                            if (it.id == task.id) it.copy(status = newStatus) else it
                                        }
                                        onWaitingTaskCountChanged(countWaitingTasks(allTasks))
                                        tasks = tasks.map {
                                            if (it.id == task.id) it.copy(status = newStatus) else it
                                        }
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = { Text("任务中心", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        snackbarHost = {
            if (showSnackbar) {
                Snackbar(modifier = Modifier.padding(16.dp)) {
                    Text(snackbarMessage ?: "")
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
        
        // 接单确认对话框
        if (showAcceptDialog && taskToAccept != null) {
            AcceptTaskConfirmDialog(
                task = taskToAccept!!,
                onConfirm = {
                    scope.launch {
                        val selectedTask = taskToAccept ?: return@launch
                        var acceptedTask = repository.acceptTask(selectedTask.id, currentUser)
                        if (acceptedTask == null) {
                            acceptedTask = selectedTask.copy(
                                status = TaskStatus.IN_PROGRESS,
                                acceptedBy = currentUser,
                                acceptedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            )
                            snackbarMessage = "已接单 (本地模式)"
                        } else {
                            snackbarMessage = "已成功接单：${acceptedTask.recipeName}"
                        }
                        showSnackbar = true
                        allTasks = allTasks.map { if (it.id == acceptedTask.id) acceptedTask else it }
                        onWaitingTaskCountChanged(countWaitingTasks(allTasks))
                        tasks = tasks.map { if (it.id == acceptedTask.id) acceptedTask else it }
                        onAcceptTask(acceptedTask)
                        // 领用后自动跳转到配置界面
                        onStartTask(acceptedTask)
                        showAcceptDialog = false
                        taskToAccept = null
                    }
                },
                onDismiss = {
                    showAcceptDialog = false
                    taskToAccept = null
                }
            )
        }
        
        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                kotlinx.coroutines.delay(3000)
                showSnackbar = false
            }
        }
    }
}

/**
 * 统计需要在实验中心重点提醒的待领用任务数量。
 */
private fun countWaitingTasks(tasks: List<ConfigurationTask>): Int {
    return tasks.count { it.status == TaskStatus.DRAFT || it.status == TaskStatus.READY }
}

/**
 * 顶部概览：更具技术感的实验室卡片
 */
@Composable
private fun TaskSummarySection(tasks: List<ConfigurationTask>, isCompact: Boolean) {
    val waitingCount = tasks.count { it.status == TaskStatus.DRAFT || it.status == TaskStatus.READY }
    val progressingCount = tasks.count { it.status == TaskStatus.PUBLISHED || it.status == TaskStatus.IN_PROGRESS }
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }

    val summaryData = listOf(
        SummaryCardData("待处理", waitingCount, Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.primary),
        SummaryCardData("进行中", progressingCount, Icons.Default.TrendingUp, MaterialTheme.colorScheme.tertiary),
        SummaryCardData("已归档", completedCount, Icons.Default.CheckCircle, Color(0xFF4CAF50))
    )

    if (isCompact) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(summaryData) { data ->
                SummaryCard(data, Modifier.width(160.dp))
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            summaryData.forEach { data ->
                SummaryCard(data, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryCard(data: SummaryCardData, modifier: Modifier = Modifier) {
    val animatedValue = remember { Animatable(0f) }
    LaunchedEffect(data.value) {
        animatedValue.animateTo(data.value.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 背景 Sparkline 装饰
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)) {
                val path = Path().apply {
                    val points = listOf(0.1f, 0.3f, 0.2f, 0.5f, 0.4f, 0.6f)
                    val stepX = size.width / (points.size - 1)
                    moveTo(0f, size.height)
                    points.forEachIndexed { index, y ->
                        lineTo(index * stepX, size.height * (1f - y * 0.2f))
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, Brush.verticalGradient(listOf(data.color.copy(alpha = 0.08f), Color.Transparent)))
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(28.dp).background(data.color.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(data.icon, null, tint = data.color, modifier = Modifier.size(16.dp))
                    }
                    Text(data.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = animatedValue.value.toInt().toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

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
        enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { 20 })
    ) {
        TaskCard(task, onAccept, onStart, onConfigure)
    }
}

@Composable
private fun TaskCard(
    task: ConfigurationTask,
    onAccept: () -> Unit,
    onStart: () -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriorityTag(task.priority)
                Text(
                    text = task.title.ifBlank { task.recipeName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusTag(task.status)
            }

            // 技术信息面板
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoItem(Modifier.weight(1f), "配方编码", task.recipeCode)
                    InfoItem(Modifier.weight(1f), "配置数量", "${com.example.smartdosing.utils.FormatUtils.formatWeight(task.quantity)} ${task.unit}")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoItem(Modifier.weight(1f), "调香师", task.perfumer.ifBlank { "未指定" })
                    InfoItem(Modifier.weight(1f), "截止日期", task.deadline.ifBlank { "待定" })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 所有状态都显示"查看配方"
                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("查看配方")
                }

                Spacer(Modifier.width(8.dp))

                when (task.status) {
                    // 未领用：只显示"确认领用"
                    TaskStatus.DRAFT, TaskStatus.READY -> {
                        Button(
                            onClick = onAccept,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AssignmentInd, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("确认领用")
                        }
                    }
                    // 已领用/进行中：显示"开始实验/继续实验"
                    TaskStatus.PUBLISHED, TaskStatus.IN_PROGRESS -> {
                        Button(
                            onClick = onStart,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (task.status == TaskStatus.IN_PROGRESS) "继续实验" else "开始实验")
                        }
                    }
                    // COMPLETED / CANCELLED：不显示操作按钮
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun InfoItem(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PriorityTag(priority: TaskPriority) {
    val (label, color) = when (priority) {
        TaskPriority.LOW -> "低" to MaterialTheme.colorScheme.outline
        TaskPriority.NORMAL -> "普通" to MaterialTheme.colorScheme.primary
        TaskPriority.HIGH -> "高" to MaterialTheme.colorScheme.tertiary
        TaskPriority.URGENT -> "加急" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusTag(status: TaskStatus) {
    val (label, color) = when (status) {
        TaskStatus.DRAFT -> "草稿" to MaterialTheme.colorScheme.outline
        TaskStatus.READY -> "待领用" to MaterialTheme.colorScheme.primary
        TaskStatus.PUBLISHED -> "待执行" to MaterialTheme.colorScheme.tertiary
        TaskStatus.IN_PROGRESS -> "进行中" to MaterialTheme.colorScheme.tertiary
        TaskStatus.COMPLETED -> "已完成" to Color(0xFF4CAF50)
        TaskStatus.CANCELLED -> "已取消" to MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TaskStatusFilterRow(selected: TaskStatus?, onSelected: (TaskStatus?) -> Unit) {
    val options = listOf<TaskStatus?>(null) + TaskStatus.entries
    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { status ->
            val isSelected = selected == status
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(status) },
                label = { Text(when(status) {
                    null -> "全部任务"
                    TaskStatus.DRAFT -> "草稿"
                    TaskStatus.READY -> "待领用"
                    TaskStatus.PUBLISHED -> "待执行"
                    TaskStatus.IN_PROGRESS -> "进行中"
                    TaskStatus.COMPLETED -> "已完成"
                    TaskStatus.CANCELLED -> "已取消"
                }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun TaskLoadingState() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun TaskErrorState(
    error: TaskLoadError,
    onRetry: () -> Unit,
    onViewRecipeLibrary: (() -> Unit)?
) {
    ErrorState(
        title = error.title,
        message = error.message,
        onRetry = onRetry,
        secondaryActionLabel = if (onViewRecipeLibrary != null) "查看配方库" else null,
        onSecondaryAction = onViewRecipeLibrary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
    )
}

@Composable
private fun TaskEmptyState() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Text("暂无任务记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class TaskLoadError(
    val title: String,
    val message: String
)

private fun Throwable.toTaskLoadError(): TaskLoadError {
    val rawMessage = message.orEmpty()
    // 对用户隐藏 Retrofit/HTTP 原始异常，只保留可行动的中文原因。
    return if (rawMessage.contains("500") || rawMessage.contains("Internal Server Error", ignoreCase = true)) {
        TaskLoadError(
            title = "任务服务暂时不可用",
            message = "任务服务返回异常，当前无法加载研发任务。请检查无线传输或后台服务状态，稍后重试。"
        )
    } else {
        TaskLoadError(
            title = "任务列表加载失败",
            message = "当前无法获取研发任务。请确认网络和后台服务可用后重试。"
        )
    }
}

private data class SummaryCardData(val title: String, val value: Int, val icon: ImageVector, val color: Color)

@Composable
private fun AcceptTaskConfirmDialog(task: ConfigurationTask, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认领用实验任务", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("您确定要领用 [${task.recipeName}] 的配置任务吗？领用后该任务将分配至您的名下。")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("配方编码: ${task.recipeCode}", style = MaterialTheme.typography.labelMedium)
                        Text("预期数量: ${com.example.smartdosing.utils.FormatUtils.formatWeight(task.quantity)} ${task.unit}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认领用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
