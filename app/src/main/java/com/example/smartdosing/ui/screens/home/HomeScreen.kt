package com.example.smartdosing.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.ui.theme.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SmartDosing 实验室研发看板 (Advanced Dashboard)
 * 采用高密度、高精度、极简实验室风格
 */
@Composable
fun HomeScreen(
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToMaterialConfiguration: (String) -> Unit = {},
    onNavigateToTaskCenter: () -> Unit = {},
    onNavigateToConfigurationRecords: () -> Unit = {},
    onNavigateToMaterialList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {},
    onImportRecipe: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val windowSize = LocalWindowSize.current
    val isLargeScreen = windowSize.widthClass == SmartDosingWindowWidthClass.Expanded
    val isCompactDevice = windowSize.widthClass == SmartDosingWindowWidthClass.Compact

    val stats by viewModel.stats.collectAsState()
    val runtimeStatus by viewModel.runtimeStatus.collectAsState()
    val recentOperations by viewModel.recentOperations.collectAsState()
    val recoveryTask by viewModel.recoveryTask.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scaleConnectionState by viewModel.scaleConnectionState.collectAsState()

    val actionCards = listOf(
        HomeAction(
            title = "实验配置",
            description = "选择配方，启动高精度配置作业",
            icon = Icons.Outlined.Science,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            emphasize = true,
            onClick = { onNavigateToMaterialConfiguration("quick_start") }
        ),
        HomeAction(
            title = "任务领取",
            description = "查看研发计划并申领配置任务",
            icon = Icons.Outlined.Assignment,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            badge = if (stats.pendingTasks > 0) stats.pendingTasks.toString() else null,
            onClick = onNavigateToTaskCenter
        ),
        HomeAction(
            title = "实验档案",
            description = "溯源历史实验记录与精度分析",
            icon = Icons.Outlined.History,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onNavigateToConfigurationRecords
        ),
        HomeAction(
            title = "物料清单",
            description = "查看当前任务所需的详细物料列表",
            icon = Icons.Outlined.FormatListBulleted,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onNavigateToMaterialList
        ),
        HomeAction(
            title = "配方管理",
            description = "维护研发配方库与物料参数",
            icon = Icons.Outlined.Analytics,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            onClick = onNavigateToRecipes
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // 实验室风格网格背景
                val gridSize = 40.dp.toPx()
                val gridColor = Color.LightGray.copy(alpha = 0.05f)
                val dotColor = Color.LightGray.copy(alpha = 0.1f)
                
                // 画线
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                }
                for (y in 0..size.height.toInt() step gridSize.toInt()) {
                    drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                }
                
                // 画交点小圆点，增加技术感
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawCircle(dotColor, radius = 2f, center = Offset(x.toFloat(), y.toFloat()))
                    }
                }
            }
    ) {
        val visible = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible.value = true }

        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(animationSpec = tween(800)) + expandVertically(animationSpec = tween(800)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLargeScreen) {
                LargeScreenHomeLayout(
                    actionCards = actionCards,
                    stats = stats,
                    isLoading = isLoading,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToConfigurationRecords = onNavigateToConfigurationRecords,
                    onNavigateToDeviceInfo = onNavigateToDeviceInfo,
                    recentOperations = recentOperations,
                    runtimeStatus = runtimeStatus,
                    recoveryTask = recoveryTask,
                    scaleConnectionState = scaleConnectionState,
                    onContinueTask = { task -> onNavigateToMaterialConfiguration(task.id) }
                )
            } else {
                CompactHomeLayout(
                    actionCards = actionCards,
                    stats = stats,
                    isLoading = isLoading,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToConfigurationRecords = onNavigateToConfigurationRecords,
                    onNavigateToDeviceInfo = onNavigateToDeviceInfo,
                    isCompactDevice = isCompactDevice,
                    recentOperations = recentOperations,
                    runtimeStatus = runtimeStatus,
                    recoveryTask = recoveryTask,
                    scaleConnectionState = scaleConnectionState,
                    onContinueTask = { task -> onNavigateToMaterialConfiguration(task.id) }
                )
            }
        }
    }
}

@Composable
private fun LargeScreenHomeLayout(
    actionCards: List<HomeAction>,
    stats: DashboardStats,
    isLoading: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfigurationRecords: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    recentOperations: List<ConfigurationRecord>,
    runtimeStatus: HomeRuntimeStatus,
    recoveryTask: ConfigurationTask?,
    scaleConnectionState: ConnectionState,
    onContinueTask: (ConfigurationTask) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧主要内容区
        Column(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HomeHeader()

            // 任务恢复 (如果有)
            recoveryTask?.let {
                TaskRecoveryCard(task = it, onContinue = { onContinueTask(it) })
            }

            DashboardOverview(stats = stats, isLoading = isLoading)

            Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HomeActionGrid(actions = actionCards)

            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecentOperationsPanel(
                    modifier = Modifier.weight(1f),
                    operations = recentOperations,
                    isLoading = isLoading,
                    onViewMore = onNavigateToConfigurationRecords
                )
            }
        }

        // 右侧侧边栏：系统状态与设备管理
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("系统监控", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            SystemStatusPanel(runtimeStatus = runtimeStatus, scaleConnectionState = scaleConnectionState)
            
            Spacer(Modifier.weight(1f))
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsActionItem(
                    title = "设备管理",
                    icon = Icons.Outlined.DeveloperBoard,
                    onClick = onNavigateToDeviceInfo
                )
                SettingsActionItem(
                    title = "系统偏好设置",
                    icon = Icons.Outlined.Settings,
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
private fun CompactHomeLayout(
    actionCards: List<HomeAction>,
    stats: DashboardStats,
    isLoading: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfigurationRecords: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    isCompactDevice: Boolean,
    recentOperations: List<ConfigurationRecord>,
    runtimeStatus: HomeRuntimeStatus,
    recoveryTask: ConfigurationTask?,
    scaleConnectionState: ConnectionState,
    onContinueTask: (ConfigurationTask) -> Unit
) {
    val scrollState = rememberScrollState()
    val padding = if (isCompactDevice) 16.dp else 24.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HomeHeader()

        recoveryTask?.let {
            TaskRecoveryCard(task = it, onContinue = { onContinueTask(it) })
        }

        DashboardOverview(stats = stats, isLoading = isLoading)
        
        SystemStatusPanel(runtimeStatus = runtimeStatus, scaleConnectionState = scaleConnectionState)
        
        Text("核心作业", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        HomeActionGrid(actions = actionCards)
        
        RecentOperationsPanel(
            operations = recentOperations,
            isLoading = isLoading,
            onViewMore = onNavigateToConfigurationRecords
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            AssistChip(
                onClick = onNavigateToDeviceInfo,
                label = { Text("设备") },
                leadingIcon = { Icon(Icons.Outlined.Memory, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onNavigateToSettings,
                label = { Text("设置") },
                leadingIcon = { Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    val currentTime = remember { LocalDateTime.now() }
    val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "实验室控制台",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currentTime.format(formatter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 实验室在线状态呼吸灯
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPulseIndicator(color = Color(0xFF4CAF50))
            Text("系统在线", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPulseIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(scale)
                .background(color.copy(alpha = alpha * 0.4f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun DashboardOverview(stats: DashboardStats, isLoading: Boolean) {
    val windowSize = LocalWindowSize.current
    val isCompact = windowSize.widthClass == SmartDosingWindowWidthClass.Compact

    val cardConfigs = listOf(
        DashboardCardInfo("研发配方库", if (isLoading) "-" else stats.totalRecipes.toString(), Icons.Outlined.Inventory2, MaterialTheme.colorScheme.primary, "件"),
        DashboardCardInfo("待处理任务", if (isLoading) "-" else stats.pendingTasks.toString(), Icons.Outlined.Assignment, MaterialTheme.colorScheme.secondary, "项"),
        DashboardCardInfo("今日已完成", if (isLoading) "-" else stats.completedToday.toString(), Icons.Outlined.CheckCircle, Color(0xFF4CAF50), "次")
    )

    if (isCompact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cardConfigs.forEach { DashboardStatCard(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            cardConfigs.forEach { DashboardStatCard(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DashboardStatCard(info: DashboardCardInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 背景装饰：模拟数据曲线 (Sparkline)
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
            ) {
                val path = Path().apply {
                    val points = listOf(0.2f, 0.4f, 0.3f, 0.7f, 0.5f, 0.8f, 0.6f)
                    val stepX = size.width / (points.size - 1)
                    moveTo(0f, size.height)
                    points.forEachIndexed { index, y ->
                        lineTo(index * stepX, size.height * (1f - y * 0.3f))
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(info.color.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
            }

            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(info.color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(info.icon, null, tint = info.color, modifier = Modifier.size(24.dp))
                }
                
                Column {
                    Text(text = info.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = info.value,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = info.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemStatusPanel(runtimeStatus: HomeRuntimeStatus, scaleConnectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            StatusItem(
                title = "高精度电子天平",
                status = when(scaleConnectionState) {
                    ConnectionState.CONNECTED -> "已连接"
                    ConnectionState.CONNECTING -> "正在握手..."
                    else -> "离线"
                },
                color = when(scaleConnectionState) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> Color(0xFFFF9800)
                    else -> Color(0xFF757575)
                },
                icon = Icons.Outlined.Balance
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            StatusItem(
                title = "数据同步服务",
                status = if (runtimeStatus.isWirelessRunning) "运行中" else "未启动",
                color = if (runtimeStatus.isWirelessRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                icon = Icons.Outlined.WifiTethering,
                subtitle = runtimeStatus.wirelessAddress
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            StatusItem(
                title = "语音引导引擎",
                status = runtimeStatus.ttsStatus,
                color = if (runtimeStatus.ttsStatus.contains("可用")) Color(0xFF4CAF50) else Color(0xFFFF9800),
                icon = Icons.Outlined.RecordVoiceOver,
                subtitle = runtimeStatus.ttsHint
            )
        }
    }
}

@Composable
private fun StatusItem(title: String, status: String, color: Color, icon: ImageVector, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TaskRecoveryCard(task: ConfigurationTask, onContinue: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("检测到进行中的实验项目", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "项目: ${task.recipeName} · 已于 ${task.acceptedAt?.substringAfter(" ") ?: "不久前"} 开始",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f)
                )
            }
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("继续实验")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeActionGrid(actions: List<HomeAction>) {
    val windowSize = LocalWindowSize.current
    val columns = when (windowSize.widthClass) {
        SmartDosingWindowWidthClass.Compact -> 1
        SmartDosingWindowWidthClass.Medium -> 2
        else -> 2
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = columns
    ) {
        actions.forEach { action ->
            HomeActionCard(action, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeActionCard(action: HomeAction, modifier: Modifier = Modifier) {
    val isPrimary = action.emphasize
    
    // 强调卡片的呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "primary_pulse")
    val pulseScale by if (isPrimary) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Card(
        onClick = action.onClick,
        modifier = modifier
            .heightIn(min = 100.dp)
            .scale(pulseScale),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(24.dp),
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 6.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isPrimary) {
                // 装饰性背景：高科技纹理
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nativePath = android.graphics.Path().apply {
                        moveTo(size.width * 0.7f, 0f)
                        quadTo(size.width * 0.85f, size.height * 0.4f, size.width, size.height * 0.2f)
                        lineTo(size.width, 0f)
                        close()
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(nativePath, android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            alpha = (255 * 0.12f).toInt()
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        })
                    }
                    
                    // 额外的装饰线
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, size.height * 0.8f),
                        end = Offset(size.width * 0.2f, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Icon(
                        action.icon, 
                        null, 
                        modifier = Modifier.size(32.dp), 
                        tint = if (isPrimary) Color.White else MaterialTheme.colorScheme.primary
                    )
                    action.badge?.let {
                        Surface(
                            color = if (isPrimary) Color.White.copy(0.2f) else MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrimary) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(action.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        action.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentOperationsPanel(modifier: Modifier = Modifier, operations: List<ConfigurationRecord>, isLoading: Boolean, onViewMore: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("最近实验成果", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onViewMore) { Text("查看完整报告", style = MaterialTheme.typography.labelMedium) }
            }
            
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (operations.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("暂无归档记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    operations.take(4).forEachIndexed { index, record ->
                        RecentOperationRow(record)
                        if (index < operations.take(4).size - 1) {
                            Divider(modifier = Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentOperationRow(record: ConfigurationRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (record.resultStatus == com.example.smartdosing.data.ConfigurationRecordStatus.COMPLETED) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (record.resultStatus == com.example.smartdosing.data.ConfigurationRecordStatus.COMPLETED) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(record.recipeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${record.updatedAt} · ${record.operator}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.actualQuantity} ${record.unit}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                if (record.resultStatus == com.example.smartdosing.data.ConfigurationRecordStatus.COMPLETED) "配置成功" else "已中止",
                style = MaterialTheme.typography.labelSmall,
                color = if (record.resultStatus == com.example.smartdosing.data.ConfigurationRecordStatus.COMPLETED) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun SettingsActionItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

private data class DashboardCardInfo(val title: String, val value: String, val icon: ImageVector, val color: Color, val unit: String)
private data class HomeAction(val title: String, val description: String, val icon: ImageVector, val containerColor: Color, val contentColor: Color, val emphasize: Boolean = false, val badge: String? = null, val onClick: () -> Unit)

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun HomeScreenLargePreview() {
    SmartDosingTheme { HomeScreen() }
}

