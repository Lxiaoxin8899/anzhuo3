package com.example.smartdosing.ui.screens.home

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
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
    onContinueRecoveryTask: (ConfigurationTask) -> Unit = {},
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
    val deviationTrend by viewModel.deviationTrend.collectAsState()
    val perfumerEfficiency by viewModel.perfumerEfficiency.collectAsState()
    val recoveryTask by viewModel.recoveryTask.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scaleConnectionState by viewModel.scaleConnectionState.collectAsState()

    val actionCards = listOf(
        HomeAction(
            title = "实验中心",
            description = "任务领取、配方调阅与实验配置",
            icon = Icons.Outlined.Science,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            emphasize = true,
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
                    deviationTrend = deviationTrend,
                    perfumerEfficiency = perfumerEfficiency,
                    runtimeStatus = runtimeStatus,
                    recoveryTask = recoveryTask,
                    scaleConnectionState = scaleConnectionState,
                    onContinueTask = { task -> onContinueRecoveryTask(task) }
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
                    deviationTrend = deviationTrend,
                    perfumerEfficiency = perfumerEfficiency,
                    runtimeStatus = runtimeStatus,
                    recoveryTask = recoveryTask,
                    scaleConnectionState = scaleConnectionState,
                    onContinueTask = { task -> onContinueRecoveryTask(task) }
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
    deviationTrend: List<DeviationPoint>,
    perfumerEfficiency: List<PerfumerEfficiency>,
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

            // 图形分析看板
            AnalyticalDashboardPanel(
                deviationTrend = deviationTrend,
                perfumerEfficiency = perfumerEfficiency,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 右侧侧边栏：系统状态与设备管理
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "系统监控",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
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
    deviationTrend: List<DeviationPoint>,
    perfumerEfficiency: List<PerfumerEfficiency>,
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

        // 图形分析看板
        AnalyticalDashboardPanel(
            deviationTrend = deviationTrend,
            perfumerEfficiency = perfumerEfficiency,
            modifier = Modifier.fillMaxWidth()
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
        
        // 中文注释：状态指示保持静态，避免离线/在线状态被动画误读。
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPulseIndicator(
                color = LabGreen,
                modifier = Modifier.size(width = 28.dp, height = 10.dp)
            )
            Text("系统在线", style = MaterialTheme.typography.labelSmall, color = LabGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPulseIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    isFlat: Boolean = false
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        if (isFlat) {
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            drawCircle(
                color = color.copy(alpha = 0.18f),
                radius = height * 0.48f,
                center = Offset(height * 0.5f, centerY)
            )
            drawCircle(color = color, radius = height * 0.3f, center = Offset(height * 0.5f, centerY))
            drawLine(
                color = color.copy(alpha = 0.45f),
                start = Offset(height, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            
            StatusItem(
                title = "数据同步服务",
                status = if (runtimeStatus.isWirelessRunning) "运行中" else "未启动",
                color = if (runtimeStatus.isWirelessRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                icon = Icons.Outlined.WifiTethering,
                subtitle = runtimeStatus.wirelessAddress
            )
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = color.copy(alpha = 0.07f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.12f))
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            val isOffline = color == Color(0xFF757575) ||
                color == Color(0xFFF44336) ||
                color == LabRed ||
                status.contains("离线") ||
                status.contains("未启动") ||
                status.contains("下线")

            StatusPulseIndicator(
                color = color,
                modifier = Modifier.size(width = 32.dp, height = 12.dp),
                isFlat = isOffline
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

    Card(
        onClick = action.onClick,
        modifier = modifier
            .heightIn(min = if (isPrimary) 118.dp else 96.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(if (isPrimary) 20.dp else 16.dp),
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 4.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
private fun RecentOperationShimmerRow() {
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
                .clip(CircleShape)
                .shimmer()
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { index ->
                        RecentOperationShimmerRow()
                        if (index < 3) {
                            Divider(modifier = Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
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
                "${com.example.smartdosing.utils.FormatUtils.formatWeight(record.actualQuantity)} ${record.unit}",
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

@Composable
fun AnalyticalDashboardPanel(
    deviationTrend: List<DeviationPoint>,
    perfumerEfficiency: List<PerfumerEfficiency>,
    modifier: Modifier = Modifier
) {
    val windowSize = LocalWindowSize.current
    val isLargeScreen = windowSize.widthClass == SmartDosingWindowWidthClass.Expanded

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("研发效能分析看板 (高精度投料)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            if (isLargeScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    DeviationTrendChart(
                        trendPoints = deviationTrend,
                        modifier = Modifier.weight(1.2f).height(240.dp)
                    )
                    PerfumerEfficiencyChart(
                        efficiencies = perfumerEfficiency,
                        modifier = Modifier.weight(0.8f).height(240.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    DeviationTrendChart(
                        trendPoints = deviationTrend,
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                    PerfumerEfficiencyChart(
                        efficiencies = perfumerEfficiency,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun DeviationTrendChart(
    trendPoints: List<DeviationPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "配料误差分布 (最近已完成)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (trendPoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无充足的实验记录以描绘误差分布", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // 中文注释：这里展示近期误差分布，不宣称六西格玛能力。
                val deviations = trendPoints.map { pt -> pt.deviationPercent }
                val mean = if (deviations.isNotEmpty()) deviations.average() else 0.0
                val variance = if (deviations.size > 1) {
                    deviations.map { (it - mean) * (it - mean) }.sum() / (deviations.size - 1)
                } else 0.0
                val stdDev = if (variance > 0.0) kotlin.math.sqrt(variance) else 0.3 // 默认 0.3%

                // 确定 X 轴区间：覆盖样本离散程度，并至少保留 +/-1.5% 的阅读范围。
                val maxAbsDev = trendPoints.maxOfOrNull { kotlin.math.abs(it.deviationPercent) } ?: 0.0
                val rangeX = kotlin.math.max(3.0 * stdDev, kotlin.math.max(maxAbsDev * 1.2, 1.5))
                val minX = -rangeX
                val maxX = rangeX

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    val paddingLeft = 45.dp.toPx()
                    val paddingRight = 15.dp.toPx()
                    val paddingTop = 15.dp.toPx()
                    val paddingBottom = 25.dp.toPx()

                    val chartWidth = width - paddingLeft - paddingRight
                    val chartHeight = height - paddingTop - paddingBottom

                    // Draw vertical dashed lines for standard deviation references.
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // X-axis coordinates mapping
                    fun mapX(value: Double): Float {
                        val ratio = (value - minX) / (maxX - minX)
                        return (paddingLeft + ratio * chartWidth).toFloat()
                    }

                    // Y-axis coordinates mapping
                    // Gaussian bell curve peak normalized to chartHeight * 0.85
                    fun gaussianHeight(value: Double): Float {
                        val z = (value - mean) / stdDev
                        val exponent = -0.5 * z * z
                        val pdf = kotlin.math.exp(exponent) // peak is 1.0 when value = mean
                        return (paddingTop + chartHeight - pdf * chartHeight * 0.85f).toFloat()
                    }

                    // 1. Draw grid / standard deviation references.
                    val sigmaOffsets = listOf(-2.0 * stdDev, -1.0 * stdDev, 0.0, 1.0 * stdDev, 2.0 * stdDev)
                    val sigmaLabels = listOf("-2σ", "-1σ", "目标", "+1σ", "+2σ")

                    val paint = Paint().apply {
                        color = textColor
                        textSize = 9.sp.toPx()
                        typeface = Typeface.MONOSPACE
                        textAlign = Paint.Align.CENTER
                    }

                    sigmaOffsets.forEachIndexed { index, offsetVal ->
                        val x = mapX(offsetVal)
                        drawLine(
                            color = if (offsetVal == 0.0) primaryColor.copy(alpha = 0.5f) else gridColor,
                            start = Offset(x, paddingTop),
                            end = Offset(x, paddingTop + chartHeight),
                            strokeWidth = if (offsetVal == 0.0) 2f else 1f,
                            pathEffect = pathEffect
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                sigmaLabels[index],
                                x,
                                paddingTop + chartHeight + 15.dp.toPx(),
                                paint
                            )
                        }
                    }

                    // 2. Draw Gaussian Bell Curve
                    val curvePath = Path()
                    val resolution = 100
                    for (i in 0..resolution) {
                        val t = i.toFloat() / resolution
                        val devVal = minX + t * (maxX - minX)
                        val x = mapX(devVal)
                        val y = gaussianHeight(devVal)
                        if (i == 0) {
                            curvePath.moveTo(x, y)
                        } else {
                            curvePath.lineTo(x, y)
                        }
                    }

                    // Draw filled gradient under the curve
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(mapX(maxX), paddingTop + chartHeight)
                        lineTo(mapX(minX), paddingTop + chartHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                            startY = paddingTop,
                            endY = paddingTop + chartHeight
                        )
                    )

                    // Draw curve stroke
                    drawPath(
                        path = curvePath,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 3. Draw sample points.
                    trendPoints.forEach { pt ->
                        val x = mapX(pt.deviationPercent)
                        val y = gaussianHeight(pt.deviationPercent)

                        drawCircle(
                            color = primaryColor.copy(alpha = 0.18f),
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                        // White core
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                        // Thin outer border
                        drawCircle(
                            color = primaryColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerfumerEfficiencyChart(
    efficiencies: List<PerfumerEfficiency>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "调香师研发投料效能 (已归档记录)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (efficiencies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无充足的调香师配置统计", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val maxCount = efficiencies.maxOf { it.completedCount }.toFloat().coerceAtLeast(1f)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    efficiencies.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = item.perfumer,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(72.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val progress = item.completedCount / maxCount
                            Box(
                                modifier = Modifier.weight(1f).height(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.primary
                                                )
                                            ),
                                            RoundedCornerShape(6.dp)
                                        )
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${item.completedCount}次",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val devColor = when {
                                    item.averageDeviationPercent < 0.3 -> Color(0xFF4CAF50)
                                    item.averageDeviationPercent < 1.0 -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }

                                Surface(
                                    color = devColor.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, devColor.copy(alpha = 0.16f))
                                ) {
                                    Text(
                                        text = String.format("±%.2f%%", item.averageDeviationPercent),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        color = devColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun HomeScreenLargePreview() {
    SmartDosingTheme { HomeScreen() }
}
