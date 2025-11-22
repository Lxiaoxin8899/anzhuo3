package com.example.smartdosing.ui.screens.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTheme

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

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        // 页面标题
        Text(
            text = "投料记录",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 标签页
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = Color(0xFF1976D2)
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
        when (selectedTab) {
            0 -> RecordsContent(onNavigateToRecordDetail, onNavigateToDosingOperation)
            1 -> StatisticsContent()
        }
    }
}

/**
 * 记录列表内容
 */
@Composable
fun RecordsContent(
    onNavigateToRecordDetail: (String) -> Unit,
    onNavigateToDosingOperation: (String) -> Unit
) {
    // 模拟记录数据
    val sampleRecords = listOf(
        RecordItemData("1", "苹果香精配方", "2024-01-15 14:30", "完成", "98%", "5分钟"),
        RecordItemData("2", "柠檬酸配方", "2024-01-15 10:15", "完成", "100%", "3分钟"),
        RecordItemData("3", "甜蜜素配方", "2024-01-14 16:45", "完成", "96%", "4分钟"),
        RecordItemData("4", "综合调味配方", "2024-01-14 09:20", "异常", "85%", "8分钟"),
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sampleRecords.size) { index ->
            RecordCard(
                record = sampleRecords[index],
                onRecordClick = onNavigateToRecordDetail,
                onRepeatDosing = onNavigateToDosingOperation
            )
        }
    }
}

/**
 * 记录数据类
 */
data class RecordItemData(
    val id: String,
    val recipeName: String,
    val timestamp: String,
    val status: String,
    val accuracy: String,
    val duration: String
)

/**
 * 记录卡片组件
 */
@Composable
fun RecordCard(
    record: RecordItemData,
    onRecordClick: (String) -> Unit,
    onRepeatDosing: (String) -> Unit
) {
    Card(
        onClick = { onRecordClick(record.id) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 记录基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = record.recipeName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.timestamp,
                        fontSize = 14.sp,
                        color = Color(0xFF757575)
                    )
                }

                // 状态标签
                StatusChip(status = record.status)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 记录详细信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RecordInfoItem(
                    label = "精度",
                    value = record.accuracy,
                    icon = Icons.Default.CheckCircle
                )
                RecordInfoItem(
                    label = "用时",
                    value = record.duration,
                    icon = Icons.Default.Info
                )

                // 重复投料按钮
                Button(
                    onClick = { onRepeatDosing(record.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重复投料",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "重复",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 状态芯片组件
 */
@Composable
fun StatusChip(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "完成" -> Color(0xFFE8F5E8) to Color(0xFF4CAF50)
        "进行中" -> Color(0xFFE3F2FD) to Color(0xFF2196F3)
        "异常" -> Color(0xFFFFEBEE) to Color(0xFFF44336)
        else -> Color(0xFFF5F5F5) to Color(0xFF757575)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
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
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF757575),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF263238)
            )
        }
    }
}

/**
 * 统计内容
 */
@Composable
fun StatisticsContent() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 今日统计
            StatisticsCard(
                title = "今日统计",
                items = listOf(
                    "投料次数" to "12次",
                    "成功率" to "98%",
                    "平均用时" to "4.2分钟",
                    "总重量" to "450.6KG"
                )
            )
        }

        item {
            // 本周统计
            StatisticsCard(
                title = "本周统计",
                items = listOf(
                    "投料次数" to "78次",
                    "成功率" to "97%",
                    "平均用时" to "4.8分钟",
                    "总重量" to "2,856.3KG"
                )
            )
        }

        item {
            // 常用配方
            StatisticsCard(
                title = "常用配方 TOP5",
                items = listOf(
                    "苹果香精配方" to "23次",
                    "柠檬酸配方" to "18次",
                    "甜蜜素配方" to "15次",
                    "综合调味配方" to "12次",
                    "香草精配方" to "10次"
                )
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                color = Color(0xFF263238)
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
                        color = Color(0xFF757575)
                    )
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF263238)
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