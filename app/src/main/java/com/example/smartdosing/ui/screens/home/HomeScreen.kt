package com.example.smartdosing.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.DosingRecord
import com.example.smartdosing.data.DosingRecordRepository
import com.example.smartdosing.data.DosingRecordStatus
import com.example.smartdosing.data.RecipeStats
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.tts.TTSManagerFactory
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import com.example.smartdosing.web.WebService

/**
 * SmartDosing 首页，包含自适应的大屏/小屏布局
 */
@Composable
fun HomeScreen(
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToMaterialConfiguration: (String) -> Unit = {},
    onNavigateToTaskCenter: () -> Unit = {},
    onNavigateToConfigurationRecords: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDeviceInfo: () -> Unit = {},
    onImportRecipe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowSize = LocalWindowSize.current
    val isLargeScreen = windowSize.widthClass == SmartDosingWindowWidthClass.Expanded
    val isCompactDevice = windowSize.widthClass == SmartDosingWindowWidthClass.Compact

    // 加载实际数据
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }
    val taskRepository = remember { ConfigurationRepositoryProvider.taskRepository }
    val dosingRepository = remember { DosingRecordRepository.getInstance(context) }
    var recipeStats by remember { mutableStateOf<RecipeStats?>(null) }
    var pendingTaskCount by remember { mutableIntStateOf(0) }
    var inProgressTaskCount by remember { mutableIntStateOf(0) }
    var completedTodayCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var recentOperations by remember { mutableStateOf<List<RecentOperation>>(emptyList()) }
    var isRecentLoading by remember { mutableStateOf(true) }

    val webService = remember { WebService.getInstance(context) }
    var runtimeStatus by remember {
        mutableStateOf(
            HomeRuntimeStatus(
                isWirelessRunning = webService.isServiceRunning(),
                wirelessAddress = webService.getDeviceInfo().serverUrl ?: "未连接网络",
                ttsStatus = "语音服务检测中",
                ttsHint = "将根据设备能力自动选择语音引擎"
            )
        )
    }

    // 加载统计数据
    LaunchedEffect(Unit) {
            try {
                recipeStats = repository.getRecipeStats()
                val tasks = taskRepository.fetchTasks()
                pendingTaskCount = tasks.count {
                    it.status == TaskStatus.DRAFT || it.status == TaskStatus.READY || it.status == TaskStatus.PUBLISHED
                }
                inProgressTaskCount = tasks.count { it.status == TaskStatus.IN_PROGRESS }
                completedTodayCount = tasks.count { it.status == TaskStatus.COMPLETED }
                runCatching {
                    dosingRepository.getRecentRecords(limit = 3)
                }.onSuccess { records ->
                    recentOperations = records.map { it.toRecentOperation() }
                }.onFailure {
                    recentOperations = emptyList()
                }
            } catch (_: Exception) {
                // 忽略加载错误，使用默认值
            } finally {
                isLoading = false
                isRecentLoading = false
            }
    }

    // 轮询无线传输与语音状态，保证首页显示实时运行情况
    LaunchedEffect(Unit) {
        while (true) {
            val deviceInfo = webService.getDeviceInfo()
            val ttsAvailable = TTSManagerFactory.isTTSAvailable()
            val ttsTypeLabel = when (TTSManagerFactory.getCurrentTTSType()) {
                TTSManagerFactory.TTSType.XIAOMI_TTS -> "小米 TTS"
                TTSManagerFactory.TTSType.FALLBACK_TTS -> "系统 TTS"
                TTSManagerFactory.TTSType.NONE -> "未启用 TTS"
            }
            runtimeStatus = HomeRuntimeStatus(
                isWirelessRunning = deviceInfo.isServerRunning,
                wirelessAddress = deviceInfo.serverUrl ?: "未连接网络",
                ttsStatus = if (ttsAvailable) "$ttsTypeLabel · 可用" else "语音服务不可用",
                ttsHint = if (ttsAvailable) "可在设置页执行语音自检" else "请检查语音组件安装与权限"
            )
            kotlinx.coroutines.delay(3000)
        }
    }

    val actionCards = listOf(
        HomeAction(
            title = "快速配料",
            description = "选择配方，立即开始配料操作",
            icon = Icons.Default.Science,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            emphasize = true,
            onClick = { onNavigateToMaterialConfiguration("quick_start") }
        ),
        HomeAction(
            title = "任务中心",
            description = "查看和管理配置任务",
            icon = Icons.Default.Assignment,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            badge = if (pendingTaskCount > 0) pendingTaskCount.toString() else null,
            onClick = onNavigateToTaskCenter
        ),
        HomeAction(
            title = "配置记录",
            description = "查看历史配置记录",
            icon = Icons.Default.CheckCircle,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onNavigateToConfigurationRecords
        ),
        HomeAction(
            title = "导入配方",
            description = "CSV / Excel 格式配方导入",
            icon = Icons.Default.FileUpload,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = onImportRecipe
        ),
        HomeAction(
            title = "配方管理",
            description = "查看 / 编辑 / 管理配方",
            icon = Icons.Default.ViewList,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            badge = recipeStats?.totalRecipes?.takeIf { it > 0 }?.toString(),
            onClick = onNavigateToRecipes
        )
    )

    // 统计数据
    val statsData = DashboardStats(
        totalRecipes = recipeStats?.totalRecipes ?: 0,
        pendingTasks = pendingTaskCount,
        inProgressTasks = inProgressTaskCount,
        completedToday = completedTodayCount
    )

    if (isLargeScreen) {
        LargeScreenHomeLayout(
            modifier = modifier,
            actionCards = actionCards,
            stats = statsData,
            isLoading = isLoading,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToConfigurationRecords = onNavigateToConfigurationRecords,
            onNavigateToDeviceInfo = onNavigateToDeviceInfo,
            recentOperations = recentOperations,
            isRecentLoading = isRecentLoading,
            runtimeStatus = runtimeStatus
        )
    } else {
        CompactHomeLayout(
            modifier = modifier,
            actionCards = actionCards,
            stats = statsData,
            isLoading = isLoading,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToConfigurationRecords = onNavigateToConfigurationRecords,
            onNavigateToDeviceInfo = onNavigateToDeviceInfo,
            isCompactDevice = isCompactDevice,
            recentOperations = recentOperations,
            isRecentLoading = isRecentLoading,
            runtimeStatus = runtimeStatus
        )
    }
}

/**
 * 仪表盘统计数据
 */
private data class DashboardStats(
    val totalRecipes: Int = 0,
    val pendingTasks: Int = 0,
    val inProgressTasks: Int = 0,
    val completedToday: Int = 0
)

private data class DashboardCardInfo(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

private data class HomeRuntimeStatus(
    val isWirelessRunning: Boolean = false,
    val wirelessAddress: String = "未连接网络",
    val ttsStatus: String = "语音服务检测中",
    val ttsHint: String = "将根据设备能力自动选择语音引擎"
)

/**
 * ≥900dp 宽度的布局，内容区域占满屏幕，不再嵌套额外的 NavigationRail
 */
@Composable
private fun LargeScreenHomeLayout(
    modifier: Modifier,
    actionCards: List<HomeAction>,
    stats: DashboardStats,
    isLoading: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfigurationRecords: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    recentOperations: List<RecentOperation>,
    isRecentLoading: Boolean,
    runtimeStatus: HomeRuntimeStatus
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        HomeHeader()

        // 数据概览卡片
        DashboardOverview(stats = stats, isLoading = isLoading)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SystemStatusCard(
                modifier = Modifier.weight(2f),
                runtimeStatus = runtimeStatus
            )
            QuickInfoColumn(
                modifier = Modifier.weight(1f),
                runtimeStatus = runtimeStatus
            )
        }
        HomeActionGrid(actions = actionCards)
        RecentOperationsPanel(
            operations = recentOperations,
            isLoading = isRecentLoading,
            onViewMore = onNavigateToConfigurationRecords
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            AssistChip(
                onClick = onNavigateToDeviceInfo,
                label = { Text("设备管理") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = "设备管理",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
            AssistChip(
                onClick = onNavigateToSettings,
                label = { Text("系统设置") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "系统设置",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/**
 * 小屏布局维持纵向滚动结构
 */
@Composable
private fun CompactHomeLayout(
    modifier: Modifier,
    actionCards: List<HomeAction>,
    stats: DashboardStats,
    isLoading: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfigurationRecords: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    isCompactDevice: Boolean,
    recentOperations: List<RecentOperation>,
    isRecentLoading: Boolean,
    runtimeStatus: HomeRuntimeStatus
) {
    val scrollState = rememberScrollState()
    val horizontalPadding = if (isCompactDevice) 16.dp else 24.dp
    val verticalPadding = if (isCompactDevice) 12.dp else 16.dp
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HomeHeader()

        // 数据概览卡片
        DashboardOverview(stats = stats, isLoading = isLoading)

        SystemStatusCard(isCompact = isCompactDevice, runtimeStatus = runtimeStatus)
        QuickInfoColumn(isCompact = isCompactDevice, runtimeStatus = runtimeStatus)
        HomeActionGrid(actions = actionCards)
        RecentOperationsPanel(
            operations = recentOperations,
            isLoading = isRecentLoading,
            onViewMore = onNavigateToConfigurationRecords
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            AssistChip(
                onClick = onNavigateToDeviceInfo,
                label = { Text("设备管理") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = "设备管理",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
            AssistChip(
                onClick = onNavigateToSettings,
                label = { Text("系统设置") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "系统设置",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

/**
 * 首页抬头
 */
@Composable
private fun HomeHeader() {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
            initialOffsetY = { -20 },
            animationSpec = tween(500)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "快速配置系统",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "系统健康，今日可以直接开始投料作业",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 数据概览仪表盘
 */
@Composable
private fun DashboardOverview(
    stats: DashboardStats,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val windowSize = LocalWindowSize.current
    val isCompactWidth = windowSize.widthClass == SmartDosingWindowWidthClass.Compact
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        visible = true
    }

    val cardConfigs = listOf(
        DashboardCardInfo(
            title = "配方总数",
            value = if (isLoading) "-" else stats.totalRecipes.toString(),
            icon = Icons.Outlined.Inventory2,
            color = MaterialTheme.colorScheme.primary
        ),
        DashboardCardInfo(
            title = "待处理任务",
            value = if (isLoading) "-" else stats.pendingTasks.toString(),
            icon = Icons.Default.Schedule,
            color = MaterialTheme.colorScheme.tertiary
        ),
        DashboardCardInfo(
            title = "进行中",
            value = if (isLoading) "-" else stats.inProgressTasks.toString(),
            icon = Icons.Default.TrendingUp,
            color = MaterialTheme.colorScheme.secondary
        ),
        DashboardCardInfo(
            title = "今日完成",
            value = if (isLoading) "-" else stats.completedToday.toString(),
            icon = Icons.Default.CheckCircle,
            color = MaterialTheme.colorScheme.primary
        )
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + expandVertically()
    ) {
        if (isCompactWidth) {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                cardConfigs.chunked(2).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCards.forEach { info ->
                            DashboardStatCard(
                                title = info.title,
                                value = info.value,
                                icon = info.icon,
                                color = info.color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCards.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                cardConfigs.forEach { info ->
                    DashboardStatCard(
                        title = info.title,
                        value = info.value,
                        icon = info.icon,
                        color = info.color,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 仪表盘统计卡片
 */
@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    // 数值动画
    val animatedValue = remember { Animatable(0f) }
    val targetValue = value.toFloatOrNull() ?: 0f

    LaunchedEffect(value) {
        if (value != "-") {
            animatedValue.animateTo(
                targetValue = targetValue,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
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
            }
            Text(
                text = if (value == "-") value else animatedValue.value.toInt().toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

/**
 * 系统状态卡片
 */
@Composable
private fun SystemStatusCard(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    runtimeStatus: HomeRuntimeStatus
) {
    val cardPadding = if (isCompact) 20.dp else 24.dp
    val statusTitle = if (runtimeStatus.isWirelessRunning) {
        "无线传输服务运行中"
    } else {
        "无线传输服务未启动"
    }
    val statusDescription = if (runtimeStatus.isWirelessRunning) {
        "可通过局域网进行无线数据传输"
    } else {
        "请在设置中开启自动启动或手动启动服务"
    }
    val runningChipText = if (runtimeStatus.isWirelessRunning) "传输状态：已启动" else "传输状态：未启动"
    val runningChipColor = if (runtimeStatus.isWirelessRunning) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val wirelessAddressText = runtimeStatus.wirelessAddress.ifBlank { "未连接网络" }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "系统状态",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text = statusTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = statusDescription,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(text = runningChipText, color = runningChipColor)
                StatusChip(text = "地址：$wirelessAddressText", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/**
 * 无线传输状态、语音播报状态
 */
@Composable
private fun QuickInfoColumn(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    runtimeStatus: HomeRuntimeStatus
) {
    val spacing = if (isCompact) 8.dp else 12.dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        InfoCard(
            title = "无线传输服务",
            content = runtimeStatus.wirelessAddress,
            icon = Icons.Default.Cloud,
            hint = if (runtimeStatus.isWirelessRunning) "已启动 · 支持局域网无线传输" else "未启动 · 请在设置页开启服务"
        )
        InfoCard(
            title = "语音播报",
            content = runtimeStatus.ttsStatus,
            icon = Icons.Default.SpeakerPhone,
            hint = runtimeStatus.ttsHint
        )
    }
}

/**
 * 快捷操作区域
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeActionGrid(
    actions: List<HomeAction>
) {
    val windowSize = LocalWindowSize.current
    val columns = when (windowSize.widthClass) {
        SmartDosingWindowWidthClass.Compact -> 1
        SmartDosingWindowWidthClass.Medium -> 2
        SmartDosingWindowWidthClass.Expanded -> 3
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = columns
    ) {
        actions.forEachIndexed { index, action ->
            HomeActionCard(action = action, index = index)
        }
    }
}

/**
 * 单个操作卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeActionCard(
    action: HomeAction,
    index: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(300))
    ) {
        Card(
            onClick = action.onClick,
            modifier = Modifier.scale(scale),
            colors = CardDefaults.cardColors(
                containerColor = action.containerColor,
                contentColor = action.contentColor
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = if (action.emphasize) 8.dp else 2.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.title,
                            modifier = Modifier.size(32.dp)
                        )

                        // 显示 Badge
                        action.badge?.let { badgeText ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (action.emphasize)
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (action.emphasize)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Text(
                        text = action.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = action.description,
                        fontSize = 14.sp,
                        color = action.contentColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * 最近操作面板
 */
@Composable
private fun RecentOperationsPanel(
    operations: List<RecentOperation>,
    isLoading: Boolean,
    onViewMore: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(400)
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "最近操作",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onViewMore) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "查看更多配置记录"
                        )
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                operations.isEmpty() -> {
                    Text(
                        text = "暂无最近配置记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    operations.forEachIndexed { index, operation ->
                        RecentOperationRow(operation = operation)
                        if (index != operations.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentOperationRow(operation: RecentOperation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = operation.title,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = operation.desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(text = operation.status, color = MaterialTheme.colorScheme.secondary)
    }
}

private data class RecentOperation(
    val title: String,
    val desc: String,
    val status: String
)

private fun DosingRecord.toRecentOperation(): RecentOperation {
    val operatorDisplay = operatorName.ifBlank { "未设置操作人" }
    val timeDisplay = endTime.takeIf { it.isNotBlank() } ?: createdAt
    val statusLabel = when (status) {
        DosingRecordStatus.COMPLETED -> "已完成"
        DosingRecordStatus.ABORTED -> "已中止"
    }
    val progress = "$completedMaterials/$totalMaterials 步"
    return RecentOperation(
        title = recipeName.ifBlank { recipeCode ?: "未命名配方" },
        desc = "$timeDisplay · $operatorDisplay",
        status = "$statusLabel · $progress"
    )
}

@Composable
private fun InfoCard(
    title: String,
    content: String,
    icon: ImageVector,
    hint: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = content,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = hint,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class HomeAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val emphasize: Boolean = false,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun HomeScreenLargePreview() {
    SmartDosingTheme {
        HomeScreen()
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Composable
private fun HomeScreenPhonePreview() {
    SmartDosingTheme {
        HomeScreen()
    }
}
