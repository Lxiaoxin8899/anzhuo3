package com.example.smartdosing.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * SmartDosing 首页，包含自适应的大屏/小屏布局
 */
@Composable
fun HomeScreen(
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToDosingOperation: (String) -> Unit = {},
    onNavigateToRecords: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onImportRecipe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLargeScreen = screenWidthDp >= 900

    val actionCards = listOf(
        HomeAction(
            title = "开始投料",
            description = "快速进入当前批次作业",
            icon = Icons.Default.PlayArrow,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            emphasize = true,
            onClick = { onNavigateToDosingOperation("quick_start") }
        ),
        HomeAction(
            title = "导入配方",
            description = "CSV / Excel 一键生成投料清单",
            icon = Icons.Default.FileUpload,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = onImportRecipe
        ),
        HomeAction(
            title = "配方管理",
            description = "创建 / 审核 / 归档生产配方",
            icon = Icons.Default.ViewList,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onNavigateToRecipes
        ),
        HomeAction(
            title = "投料记录",
            description = "实时查看执行记录与日志",
            icon = Icons.Default.BarChart,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onNavigateToRecords
        )
    )

    if (isLargeScreen) {
        LargeScreenHomeLayout(
            modifier = modifier,
            actionCards = actionCards,
            onNavigateToSettings = onNavigateToSettings
        )
    } else {
        CompactHomeLayout(
            modifier = modifier,
            actionCards = actionCards,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

/**
 * ≥900dp 宽度的布局，内容区域占满屏幕，不再嵌套额外的 NavigationRail
 */
@Composable
private fun LargeScreenHomeLayout(
    modifier: Modifier,
    actionCards: List<HomeAction>,
    onNavigateToSettings: () -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SystemStatusCard(
                modifier = Modifier.weight(2f)
            )
            QuickInfoColumn(
                modifier = Modifier.weight(1f)
            )
        }
        HomeActionGrid(actions = actionCards)
        RecentOperationsPanel()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
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
    onNavigateToSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HomeHeader()
        SystemStatusCard()
        QuickInfoColumn()
        HomeActionGrid(actions = actionCards)
        RecentOperationsPanel()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
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
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "SmartDosing 投料系统",
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

/**
 * 系统状态卡片
 */
@Composable
private fun SystemStatusCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
                        text = "系统正常 · 可以开始投料",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "最近一次自检：09:30，所有服务在线",
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(text = "批次：S001-上午班", color = MaterialTheme.colorScheme.primary)
                StatusChip(text = "Web 服务运行中", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/**
 * Web 后台、语音播报状态
 */
@Composable
private fun QuickInfoColumn(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InfoCard(
            title = "Web 管理后台",
            content = "http://192.168.5.6:8080",
            icon = Icons.Default.Cloud,
            hint = "已启动 · 支持局域网访问"
        )
        InfoCard(
            title = "语音播报",
            content = "Xiaomi TTS · 在线",
            icon = Icons.Default.SpeakerPhone,
            hint = "播报速率：1.0 · 中文女声"
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
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 2
    ) {
        actions.forEach { action ->
            HomeActionCard(action = action)
        }
    }
}

/**
 * 单个操作卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeActionCard(
    action: HomeAction
) {
    Card(
        onClick = action.onClick,
        colors = CardDefaults.cardColors(
            containerColor = action.containerColor,
            contentColor = action.contentColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (action.emphasize) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                modifier = Modifier.size(32.dp)
            )
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

/**
 * 最近操作面板
 */
@Composable
private fun RecentOperationsPanel() {
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
                IconButton(onClick = { /* TODO 查看更多记录 */ }) {
                    Icon(
                        imageVector = Icons.Default.ViewList,
                        contentDescription = "更多记录"
                    )
                }
            }
            RecentOperationRow(
                title = "苹果香精配方",
                desc = "30 分钟前 · 已完成",
                status = "完成"
            )
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            RecentOperationRow(
                title = "柠檬酸配方",
                desc = "2 小时前 · 已完成",
                status = "完成"
            )
        }
    }
}

@Composable
private fun RecentOperationRow(
    title: String,
    desc: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(text = status, color = MaterialTheme.colorScheme.secondary)
    }
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
