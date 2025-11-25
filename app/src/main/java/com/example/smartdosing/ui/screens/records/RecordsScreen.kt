package com.example.smartdosing.ui.screens.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log
import com.example.smartdosing.data.DosingRecord
import com.example.smartdosing.data.DosingRecordDetail
import com.example.smartdosing.data.DosingRecordRepository
import com.example.smartdosing.data.DosingRecordStatus
import com.example.smartdosing.ui.theme.*

/**
 * 投料记录页面
 * 显示历史投料记录和统计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    onNavigateToRecordDetail: (String) -> Unit = {},
    onNavigateToDosingOperation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("投料记录", "统计报表")
    val context = LocalContext.current

    // 详情页面状态管理
    var showDetailScreen by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<DosingRecord?>(null) }

    // 添加导航日志
    Log.d("RecordsScreen", "RecordsScreen 组件初始化")

    // 状态管理
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<DosingRecord>>(emptyList()) }

    val recordRepository = remember {
        try {
            DosingRecordRepository.getInstance(context)
        } catch (e: Exception) {
            errorMessage = "数据库初始化失败: ${e.message}"
            null
        }
    }

    val safeonNavigateToRecordDetail: (String) -> Unit = { recordId ->
        Log.d("RecordsScreen", "收到导航到详情的请求，记录ID: $recordId")
        try {
            // 不再使用外部导航，改为内部状态管理
            // onNavigateToRecordDetail(recordId)
            // Log.d("RecordsScreen", "导航到详情页面成功")

            // 内部导航：查找对应记录并显示详情页面
            records.find { it.id == recordId }?.let { record ->
                selectedRecord = record
                showDetailScreen = true
                Log.d("RecordsScreen", "内部导航到详情页面成功，记录: ${record.recipeName}")
            } ?: run {
                Log.w("RecordsScreen", "未找到记录ID: $recordId")
            }
        } catch (e: Exception) {
            Log.e("RecordsScreen", "导航到详情页面失败", e)
        }
    }

    val safeOnNavigateToDosingOperation: (String) -> Unit = { recipeId ->
        Log.d("RecordsScreen", "收到导航到投料操作的请求，配方ID: $recipeId")
        try {
            onNavigateToDosingOperation(recipeId)
            Log.d("RecordsScreen", "导航到投料操作页面成功")
        } catch (e: Exception) {
            Log.e("RecordsScreen", "导航到投料操作页面失败", e)
        }
    }

    // 安全的数据观察
    LaunchedEffect(recordRepository) {
        recordRepository?.let { repo ->
            try {
                repo.observeRecords().collect { recordList ->
                    records = recordList
                    isLoading = false
                    errorMessage = null
                }
            } catch (e: Exception) {
                errorMessage = "数据加载失败: ${e.message}"
                isLoading = false
            }
        } ?: run {
            isLoading = false
        }
    }

    val recordStats = remember(records) {
        try {
            calculateRecordStats(records)
        } catch (e: Exception) {
            errorMessage = "统计计算失败: ${e.message}"
            RecordStats() // 返回空的统计
        }
    }

    // 条件渲染：显示详情页面或列表页面
    if (showDetailScreen && selectedRecord != null) {
        RecordDetailScreen(
            record = selectedRecord!!,
            onNavigateBack = {
                showDetailScreen = false
                selectedRecord = null
                Log.d("RecordsScreen", "从详情页面返回到列表页面")
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp) // 减少页面边距
    ) {
        // 页面标题
        Text(
            text = "投料记录",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 错误状态显示
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "错误",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "加载出错",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            // 重新触发数据加载
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text("重试")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 加载状态
        if (isLoading && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "正在加载投料记录...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        // 标签页
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 内容区域
        if (errorMessage == null) {
            when (selectedTab) {
                0 -> RecordsContent(
                    records = records,
                    onNavigateToRecordDetail = safeonNavigateToRecordDetail,
                    onNavigateToDosingOperation = safeOnNavigateToDosingOperation
                )
                1 -> StatisticsContent(stats = recordStats)
            }
        }
    }
}

/**
 * 记录列表内容
 */
@Composable
fun RecordsContent(
    records: List<DosingRecord>,
    onNavigateToRecordDetail: (String) -> Unit,
    onNavigateToDosingOperation: (String) -> Unit
) {
    var contentError by remember { mutableStateOf<String?>(null) }

    if (contentError != null) {
        ErrorContent(
            error = contentError!!,
            onRetry = { contentError = null }
        )
        return
    }

    if (records.isEmpty()) {
        EmptyStateContent()
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp) // 减少卡片间距，提高密度
    ) {
        items(records.size) { index ->
            val record = records.getOrNull(index)
            if (record != null) {
                RecordCard(
                    record = record,
                    onRecordClick = onNavigateToRecordDetail,
                    onRepeatDosing = onNavigateToDosingOperation
                )
            } else {
                ErrorCard(
                    error = "记录数据异常：索引 $index 超出范围",
                    recordIndex = index
                )
            }
        }
    }
}

/**
 * 空状态内容
 */
@Composable
fun EmptyStateContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "无记录",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "暂无投料记录",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "完成投料操作后，记录将显示在这里",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 错误内容组件
 */
@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "错误",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = "加载失败",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onErrorContainer,
                        contentColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重试")
                }
            }
        }
    }
}

/**
 * 错误记录卡片
 */
@Composable
fun ErrorCard(
    error: String,
    recordIndex: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "错误",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "记录 #${recordIndex + 1} 显示异常",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 记录卡片组件 - 紧凑版本，提高信息密度
 */
@Composable
fun RecordCard(
    record: DosingRecord,
    onRecordClick: (String) -> Unit,
    onRepeatDosing: (String) -> Unit
) {
    var cardError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (cardError != null) {
        ErrorCard(
            error = cardError!!,
            recordIndex = 0
        )
        return
    }

    val hasOverLimit = record.overLimitCount > 0

    Card(
        // 暂时禁用点击导航，避免白屏问题
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasOverLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(8.dp),
        border = if (hasOverLimit) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：配方名称 + 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.recipeName.takeIf { it.isNotEmpty() } ?: "未知配方",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // 超标警告图标
                if (hasOverLimit) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "有超标情况",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // 状态标签
                CompactStatusChip(status = record.status)
            }

            // 第二行：操作员和时间信息（紧凑显示）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "操作员: ${record.operatorName.ifBlank { "未填写" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = record.startTime.ifEmpty { "未知时间" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 第三行：关键统计信息（水平排列）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 完成率
                CompactStatItem(
                    label = "完成",
                    value = "${record.completedMaterials}/${record.totalMaterials}",
                    modifier = Modifier.weight(1f)
                )

                // 平均偏差
                CompactStatItem(
                    label = "偏差",
                    value = formatPercent(record.averageDeviationPercent),
                    valueColor = when {
                        record.averageDeviationPercent > 5.0 -> MaterialTheme.colorScheme.error
                        record.averageDeviationPercent > 2.0 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    },
                    modifier = Modifier.weight(1f)
                )

                // 超标情况
                CompactStatItem(
                    label = "超标",
                    value = if (hasOverLimit) "${record.overLimitCount}" else "0",
                    valueColor = if (hasOverLimit) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )

                // 操作按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 查看详情按钮（增大尺寸）
                    Button(
                        onClick = {
                            Log.e("RecordCard", "===== 详情按钮被点击了！记录ID: ${record.id} =====")
                            // 直接调用导航函数
                            onRecordClick(record.id)
                        },
                        modifier = Modifier.size(width = 70.dp, height = 32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            text = "详情",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 重复投料按钮（增大尺寸）
                    Button(
                        onClick = {
                            Log.e("RecordCard", "===== 重复投料按钮被点击了！ =====")
                            runCatching {
                                record.recipeId?.let { recipeId ->
                                    Log.d("RecordCard", "开始重复投料，配方ID: $recipeId")
                                    onRepeatDosing(recipeId)
                                }
                            }.onFailure { e ->
                                Log.e("RecordCard", "重复投料失败", e)
                                cardError = "重复投料失败: ${e.message}"
                            }
                        },
                        enabled = record.recipeId != null,
                        modifier = Modifier.size(width = 70.dp, height = 32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (record.recipeId != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = Color.White,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重复投料",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑状态芯片组件
 */
@Composable
fun CompactStatusChip(status: DosingRecordStatus) {
    val (label, backgroundColor, textColor) = when (status) {
        DosingRecordStatus.COMPLETED -> Triple("完成", Color(0xFF4CAF50).copy(alpha = 0.1f), Color(0xFF4CAF50))
        DosingRecordStatus.ABORTED -> Triple("异常", MaterialTheme.colorScheme.error.copy(alpha = 0.1f), MaterialTheme.colorScheme.error)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * 紧凑统计项组件
 */
@Composable
fun CompactStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
@Composable
fun StatusChip(status: DosingRecordStatus) {
    val (label, backgroundColor, textColor) = when (status) {
        DosingRecordStatus.COMPLETED -> Triple("完成", IndustrialGreen.copy(alpha = 0.1f), IndustrialGreen)
        DosingRecordStatus.ABORTED -> Triple("异常", IndustrialRed.copy(alpha = 0.1f), IndustrialRed)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * 快速统计项组件
 */
@Composable
fun QuickStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    progress: Float? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )

        // 可选的进度条
        progress?.let { prog ->
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { prog },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 投料详情区域组件
 */
@Composable
fun DosingDetailsSection(record: DosingRecord) {
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 详情标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "详情",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "投料详情 (${record.details.size} 种材料)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(1f))

            // 展开/收起材料详情按钮
            IconButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (detailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (detailsExpanded) "收起材料详情" else "展开材料详情",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 投料汇总信息（始终显示）
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailSummaryItem(
                    label = "总重量",
                    value = formatWeight(record.totalActualWeight),
                    modifier = Modifier.weight(1f)
                )
                DetailSummaryItem(
                    label = "平均偏差",
                    value = formatPercent(record.averageDeviationPercent),
                    valueColor = when {
                        record.averageDeviationPercent > 5.0 -> MaterialTheme.colorScheme.error
                        record.averageDeviationPercent > 2.0 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    },
                    modifier = Modifier.weight(1f)
                )
                DetailSummaryItem(
                    label = "耗时",
                    value = calculateDuration(record.startTime, record.endTime),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 材料详情列表（可展开/收起）
        if (detailsExpanded) {
            val sortedDetails = remember(record.details) {
                record.details.sortedBy { it.materialSequence }
            }

            // 使用Column而不是LazyColumn来避免嵌套问题
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 如果材料太多，只显示前几个并提供查看更多选项
                val maxDisplayItems = 5
                val itemsToShow = if (sortedDetails.size <= maxDisplayItems) {
                    sortedDetails
                } else {
                    sortedDetails.take(maxDisplayItems)
                }

                itemsToShow.forEach { detail ->
                    DosingDetailItem(detail = detail)
                }

                // 如果有更多材料，显示提示
                if (sortedDetails.size > maxDisplayItems) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "还有 ${sortedDetails.size - maxDisplayItems} 种材料...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 投料详情单项组件
 */
@Composable
fun DosingDetailItem(detail: DosingRecordDetail) {
    var itemError by remember { mutableStateOf<String?>(null) }

    if (itemError != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "错误",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "材料详情显示异常",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = itemError!!,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
        return
    }

    val deviationPercent = runCatching {
        if (detail.targetWeight > 0) {
            ((detail.actualWeight - detail.targetWeight) / detail.targetWeight) * 100
        } else 0.0
    }.getOrElse {
        itemError = "偏差计算失败"
        0.0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (detail.isOverLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (detail.isOverLimit) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        } else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${detail.materialSequence}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 材料信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = detail.materialName.takeIf { it.isNotBlank() } ?: "未知材料",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail.materialCode.takeIf { it.isNotBlank() } ?: "无代码",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 重量信息
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${formatWeight(detail.actualWeight)} / ${formatWeight(detail.targetWeight)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (detail.isOverLimit) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "超标",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "${if (deviationPercent > 0) "+" else ""}${formatPercent(deviationPercent)}",
                        fontSize = 11.sp,
                        color = when {
                            detail.isOverLimit -> MaterialTheme.colorScheme.error
                            deviationPercent < 0 -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (detail.isOverLimit) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 详情汇总项组件
 */
@Composable
fun DetailSummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

/**
 * 记录信息项组件
 */
@Composable
fun RecordInfoItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 统计内容
 */
@Composable
fun StatisticsContent(stats: RecordStats) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatisticsCard(
                title = "整体统计",
                items = listOf(
                    "记录总数" to "${stats.totalCount} 次",
                    "完成记录" to "${stats.completedCount} 次",
                    "平均偏差" to formatPercent(stats.averageDeviation),
                    "累计投料" to "${formatWeight(stats.totalActualWeight)}"
                )
            )
        }

        item {
            StatisticsCard(
                title = "异常/超标提醒",
                items = listOf(
                    "超标次数" to "${stats.overLimitCount} 次",
                    "异常率" to if (stats.totalCount == 0) "0%" else formatPercent(
                        (stats.totalCount - stats.completedCount).toDouble() / stats.totalCount * 100.0
                    )
                )
            )
        }

        item {
            val operatorItems = if (stats.topOperators.isEmpty()) {
                listOf("暂无操作员数据" to "-")
            } else {
                stats.topOperators.map { (name, count) -> name to "${count} 次" }
            }
            StatisticsCard(
                title = "主操作者 TOP5",
                items = operatorItems
            )
        }
    }
}

/**
 * 统计卡片组件
 */
@Composable
fun StatisticsCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun RecordsScreenPreview() {
    SmartDosingTheme {
        RecordsScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun RecordCardPreview() {
    val sampleDetail = DosingRecordDetail(
        id = "detail1",
        recordId = "record1",
        materialSequence = 1,
        materialCode = "M-001",
        materialName = "苹果香精",
        targetWeight = 10.0,
        actualWeight = 10.2,
        unit = "KG",
        isOverLimit = false,
        overLimitPercent = 2.0
    )
    val sampleRecord = DosingRecord(
        id = "record1",
        recipeId = "recipe1",
        recipeCode = "XJ241101001",
        recipeName = "苹果香精配方",
        operatorName = "张工",
        checklist = listOf("设备校准", "批次确认"),
        startTime = "2024-01-15 14:30",
        endTime = "2024-01-15 14:36",
        totalMaterials = 3,
        completedMaterials = 3,
        totalActualWeight = 150.5,
        tolerancePercent = 3f,
        overLimitCount = 1,
        averageDeviationPercent = 1.8,
        status = DosingRecordStatus.COMPLETED,
        createdAt = "2024-01-15 14:36",
        details = listOf(sampleDetail)
    )
    SmartDosingTheme {
        RecordCard(record = sampleRecord, onRecordClick = {}, onRepeatDosing = {})
    }
}

data class RecordStats(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val averageDeviation: Double = 0.0,
    val totalActualWeight: Double = 0.0,
    val overLimitCount: Int = 0,
    val topOperators: List<Pair<String, Int>> = emptyList()
)

private fun calculateRecordStats(records: List<DosingRecord>): RecordStats {
    if (records.isEmpty()) return RecordStats()
    val totalCount = records.size
    val completedCount = records.count { it.status == DosingRecordStatus.COMPLETED }
    val averageDeviation = records.sumOf { it.averageDeviationPercent } / totalCount
    val totalActualWeight = records.sumOf { it.totalActualWeight }
    val overLimitCount = records.sumOf { it.overLimitCount }
    val operatorRanking = records
        .groupBy { it.operatorName.ifBlank { "未填写" } }
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }

    return RecordStats(
        totalCount = totalCount,
        completedCount = completedCount,
        averageDeviation = averageDeviation,
        totalActualWeight = totalActualWeight,
        overLimitCount = overLimitCount,
        topOperators = operatorRanking
    )
}

private fun formatPercent(value: Double): String {
    return String.format(Locale.getDefault(), "%.1f%%", value)
}

private fun formatWeight(value: Double): String {
    return String.format(Locale.getDefault(), "%.2f", value)
}

private fun calculateDuration(startTime: String, endTime: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val start = format.parse(startTime)
        val end = format.parse(endTime)
        if (start != null && end != null) {
            val durationMs = end.time - start.time
            val minutes = (durationMs / 1000 / 60).toInt()
            val seconds = ((durationMs / 1000) % 60).toInt()
            "${minutes}分${seconds}秒"
        } else {
            "未知"
        }
    } catch (e: Exception) {
        "未知"
    }
}

/**
 * 简化版投料详情区域 - 用于排查白屏问题
 * 只显示基本信息，不包含复杂的展开/收起逻辑
 */
@Composable
fun SimpleDosingDetailsSection(record: DosingRecord) {
    Log.d("SimpleDosingDetails", "开始渲染简化详情，材料数量: ${record.details.size}")

    var renderError by remember { mutableStateOf<String?>(null) }

    if (renderError != null) {
        // 最简化的错误显示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "详情显示异常: $renderError",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(
                text = "投料详情 (${record.details.size} 种材料)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 汇总信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "总重量",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatWeight(record.totalActualWeight),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "平均偏差",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPercent(record.averageDeviationPercent),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            record.averageDeviationPercent > 5.0 -> MaterialTheme.colorScheme.error
                            record.averageDeviationPercent > 2.0 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "超标次数",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (record.overLimitCount > 0) "${record.overLimitCount} 次" else "无",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (record.overLimitCount > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 简化的材料列表 - 只显示前3个材料
            Text(
                text = "材料列表（显示前3项）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Log.d("SimpleDosingDetails", "开始渲染材料列表")

            val materialsToShow = record.details.take(3)
            materialsToShow.forEachIndexed { index, detail ->
                Log.d("SimpleDosingDetails", "渲染材料 $index: ${detail.materialName}")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (detail.isOverLimit) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.materialName.ifBlank { "未知材料" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = detail.materialCode.ifBlank { "无代码" },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${formatWeight(detail.actualWeight)} / ${formatWeight(detail.targetWeight)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (detail.isOverLimit) {
                                Text(
                                    text = "超标",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (record.details.size > 3) {
                Text(
                    text = "还有 ${record.details.size - 3} 种材料...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Log.d("SimpleDosingDetails", "简化详情渲染完成")
        }
    }
}

/**
 * 投料记录详情页面
 * 全屏显示单条投料记录的完整信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    record: DosingRecord,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailError by remember { mutableStateOf<String?>(null) }

    if (detailError != null) {
        ErrorContent(
            error = detailError!!,
            onRetry = { detailError = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "投料记录详情",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = record.recipeName.takeIf { it.isNotEmpty() } ?: "未知配方",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 基本信息卡片
            item {
                RecordDetailInfoCard(record = record)
            }

            // 统计信息卡片
            item {
                RecordDetailStatsCard(record = record)
            }

            // 材料详情卡片
            item {
                RecordDetailMaterialsCard(record = record)
            }
        }
    }
}

/**
 * 记录基本信息卡片
 */
@Composable
fun RecordDetailInfoCard(record: DosingRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "基本信息",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                CompactStatusChip(status = record.status)
            }

            DetailInfoRow(
                label = "配方名称",
                value = record.recipeName.takeIf { it.isNotEmpty() } ?: "未知配方",
                icon = Icons.Default.List
            )

            record.recipeCode?.takeIf { it.isNotEmpty() }?.let { code ->
                DetailInfoRow(
                    label = "配方编码",
                    value = code,
                    icon = Icons.Default.Code
                )
            }

            DetailInfoRow(
                label = "操作人员",
                value = record.operatorName.takeIf { it.isNotEmpty() } ?: "未填写",
                icon = Icons.Default.Person
            )

            DetailInfoRow(
                label = "开始时间",
                value = record.startTime.takeIf { it.isNotEmpty() } ?: "未知时间",
                icon = Icons.Default.Schedule
            )

            DetailInfoRow(
                label = "结束时间",
                value = record.endTime.takeIf { it.isNotEmpty() } ?: "未知时间",
                icon = Icons.Default.Schedule
            )

            DetailInfoRow(
                label = "操作耗时",
                value = calculateDuration(record.startTime, record.endTime),
                icon = Icons.Default.Timer
            )
        }
    }
}

/**
 * 统计信息卡片
 */
@Composable
fun RecordDetailStatsCard(record: DosingRecord) {
    val hasOverLimit = record.overLimitCount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasOverLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (hasOverLimit) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投料统计",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (hasOverLimit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "有超标情况",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "存在超标",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 统计数据网格 - 2行2列
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailStatItem(
                        label = "材料总数",
                        value = "${record.totalMaterials} 种",
                        modifier = Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    DetailStatItem(
                        label = "完成材料",
                        value = "${record.completedMaterials} 种",
                        modifier = Modifier.weight(1f),
                        valueColor = Color(0xFF4CAF50)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailStatItem(
                        label = "总重量",
                        value = formatWeight(record.totalActualWeight),
                        modifier = Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    DetailStatItem(
                        label = "平均偏差",
                        value = formatPercent(record.averageDeviationPercent),
                        modifier = Modifier.weight(1f),
                        valueColor = when {
                            record.averageDeviationPercent > 5.0 -> MaterialTheme.colorScheme.error
                            record.averageDeviationPercent > 2.0 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailStatItem(
                        label = "超标次数",
                        value = if (hasOverLimit) "${record.overLimitCount} 次" else "无",
                        modifier = Modifier.weight(1f),
                        valueColor = if (hasOverLimit) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                    DetailStatItem(
                        label = "容差标准",
                        value = "${record.tolerancePercent}%",
                        modifier = Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 材料详情卡片
 */
@Composable
fun RecordDetailMaterialsCard(record: DosingRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "材料列表",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "投料详情 (${record.details.size} 种材料)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val sortedDetails = remember(record.details) {
                record.details.sortedBy { it.materialSequence }
            }

            // 使用Column而不是LazyColumn来避免嵌套问题
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedDetails.forEach { detail ->
                    DetailMaterialItem(detail = detail)
                }
            }
        }
    }
}

/**
 * 详情信息行组件
 */
@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )

        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 详情统计项组件
 */
@Composable
fun DetailStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * 详情材料项组件
 */
@Composable
fun DetailMaterialItem(detail: DosingRecordDetail) {
    var itemError by remember { mutableStateOf<String?>(null) }

    if (itemError != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "错误",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "材料详情显示异常: $itemError",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    val deviationPercent = runCatching {
        if (detail.targetWeight > 0) {
            ((detail.actualWeight - detail.targetWeight) / detail.targetWeight) * 100
        } else 0.0
    }.getOrElse {
        itemError = "偏差计算失败"
        0.0
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (detail.isOverLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (detail.isOverLimit) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        } else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 序号标识
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${detail.materialSequence}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 材料信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = detail.materialName.takeIf { it.isNotBlank() } ?: "未知材料",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail.materialCode.takeIf { it.isNotBlank() } ?: "无代码",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 重量和偏差信息
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${formatWeight(detail.actualWeight)} / ${formatWeight(detail.targetWeight)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (detail.isOverLimit) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "超标",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "${if (deviationPercent > 0) "+" else ""}${formatPercent(deviationPercent)}",
                        fontSize = 13.sp,
                        color = when {
                            detail.isOverLimit -> MaterialTheme.colorScheme.error
                            deviationPercent < 0 -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (detail.isOverLimit) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
