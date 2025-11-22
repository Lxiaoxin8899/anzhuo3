package com.example.smartdosing.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * SmartDosing 系统首页
 * 显示系统概览、快速操作和统计信息
 */
@Composable
fun HomeScreen(
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToDosingOperation: (String) -> Unit = {},
    onNavigateToRecords: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 系统标题
        item {
            SystemHeader()
        }

        // 今日概览卡片
        item {
            TodayOverviewCard()
        }

        // 快速开始区域
        item {
            QuickStartSection(
                onNavigateToDosingOperation = onNavigateToDosingOperation
            )
        }

        // 功能入口
        item {
            FunctionEntrySection(
                onNavigateToRecipes = onNavigateToRecipes,
                onNavigateToRecords = onNavigateToRecords,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        // 最近记录
        item {
            RecentRecordsSection()
        }
    }
}

/**
 * 系统标题区域
 */
@Composable
fun SystemHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "SmartDosing 智能投料系统",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "工业级精确投料解决方案",
            fontSize = 16.sp,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 今日概览卡片
 */
@Composable
fun TodayOverviewCard() {
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
                text = "📊 今日概览",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(160.dp)
            ) {
                item {
                    StatisticCard(
                        title = "投料次数",
                        value = "12",
                        icon = Icons.Default.PlayArrow,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    StatisticCard(
                        title = "活跃配方",
                        value = "5",
                        icon = Icons.Default.List,
                        color = Color(0xFF2196F3)
                    )
                }
                item {
                    StatisticCard(
                        title = "总重量",
                        value = "450KG",
                        icon = Icons.Default.Build,
                        color = Color(0xFFFF9800)
                    )
                }
                item {
                    StatisticCard(
                        title = "成功率",
                        value = "98%",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF9C27B0)
                    )
                }
            }
        }
    }
}

/**
 * 统计卡片组件
 */
@Composable
fun StatisticCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 快速开始区域
 */
@Composable
fun QuickStartSection(
    onNavigateToDosingOperation: (String) -> Unit
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
                text = "🚀 快速开始",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 最近使用的配方
            repeat(2) { index ->
                QuickStartItem(
                    recipeName = if (index == 0) "苹果香精配方" else "柠檬酸配方",
                    lastUsed = if (index == 0) "30分钟前使用" else "2小时前使用",
                    onClick = { onNavigateToDosingOperation("recipe_$index") }
                )
                if (index == 0) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 快速开始项目
 */
@Composable
fun QuickStartItem(
    recipeName: String,
    lastUsed: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📋 $recipeName",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF263238)
                )
                Text(
                    text = "⏰ $lastUsed",
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "开始投料",
                tint = Color(0xFF1976D2)
            )
        }
    }
}

/**
 * 功能入口区域
 */
@Composable
fun FunctionEntrySection(
    onNavigateToRecipes: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onNavigateToSettings: () -> Unit
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
                text = "📱 功能入口",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(160.dp)
            ) {
                item {
                    FunctionEntryCard(
                        title = "新建配方",
                        icon = Icons.Default.Add,
                        color = Color(0xFF4CAF50),
                        onClick = onNavigateToRecipes
                    )
                }
                item {
                    FunctionEntryCard(
                        title = "配方管理",
                        icon = Icons.Default.List,
                        color = Color(0xFF2196F3),
                        onClick = onNavigateToRecipes
                    )
                }
                item {
                    FunctionEntryCard(
                        title = "投料记录",
                        icon = Icons.Default.List,
                        color = Color(0xFFFF9800),
                        onClick = onNavigateToRecords
                    )
                }
                item {
                    FunctionEntryCard(
                        title = "系统设置",
                        icon = Icons.Default.Settings,
                        color = Color(0xFF9C27B0),
                        onClick = onNavigateToSettings
                    )
                }
            }
        }
    }
}

/**
 * 功能入口卡片
 */
@Composable
fun FunctionEntryCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF263238),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 最近记录区域
 */
@Composable
fun RecentRecordsSection() {
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
                text = "⏰ 最近记录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 示例记录
            repeat(3) { index ->
                RecentRecordItem(
                    recipeName = "配方 ${index + 1}",
                    time = "${2 + index}小时前",
                    status = if (index == 0) "完成" else "进行中"
                )
                if (index < 2) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 最近记录项目
 */
@Composable
fun RecentRecordItem(
    recipeName: String,
    time: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = recipeName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF263238)
            )
            Text(
                text = time,
                fontSize = 12.sp,
                color = Color(0xFF757575)
            )
        }

        Text(
            text = status,
            fontSize = 12.sp,
            color = if (status == "完成") Color(0xFF4CAF50) else Color(0xFFFF9800),
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun HomeScreenPreview() {
    SmartDosingTheme {
        HomeScreen()
    }
}