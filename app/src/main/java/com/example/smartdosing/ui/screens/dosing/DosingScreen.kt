package com.example.smartdosing.ui.screens.dosing

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
 * 投料操作入口页面
 * 提供配方选择和快速开始投料的入口
 */
@Composable
fun DosingScreen(
    onNavigateToDosingOperation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // 页面标题
            Text(
                text = "投料操作",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item {
            // 快速开始区域
            QuickDosingSection(
                onNavigateToDosingOperation = onNavigateToDosingOperation
            )
        }

        item {
            // 配方选择区域
            RecipeSelectionSection(
                onNavigateToDosingOperation = onNavigateToDosingOperation
            )
        }

        item {
            // 最近操作记录
            RecentOperationsSection()
        }
    }
}

/**
 * 快速投料区域
 */
@Composable
fun QuickDosingSection(
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
                text = "⚡ 快速开始",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 大的开始投料按钮
            Button(
                onClick = { onNavigateToDosingOperation("import_csv") },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "开始投料",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "导入配方文件开始投料",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "支持导入CSV格式的配方文件进行投料操作",
                fontSize = 14.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 配方选择区域
 */
@Composable
fun RecipeSelectionSection(
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
                text = "📋 选择已保存配方",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 示例配方列表
            val sampleRecipes = listOf(
                Pair("苹果香精配方", "recipe_001"),
                Pair("柠檬酸配方", "recipe_002"),
                Pair("甜蜜素配方", "recipe_003")
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(sampleRecipes.size) { index ->
                    val recipe = sampleRecipes[index]
                    RecipeQuickCard(
                        recipeName = recipe.first,
                        recipeId = recipe.second,
                        onClick = { onNavigateToDosingOperation(recipe.second) }
                    )
                }
            }
        }
    }
}

/**
 * 配方快选卡片
 */
@Composable
fun RecipeQuickCard(
    recipeName: String,
    recipeId: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = recipeName,
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recipeName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF263238),
                textAlign = TextAlign.Center
            )
            Text(
                text = recipeId,
                fontSize = 12.sp,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 最近操作记录区域
 */
@Composable
fun RecentOperationsSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏰ 最近操作",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263238)
                )

                TextButton(
                    onClick = { /* 查看全部 */ }
                ) {
                    Text(
                        text = "查看全部",
                        fontSize = 14.sp,
                        color = Color(0xFF1976D2)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 示例最近操作记录
            repeat(3) { index ->
                RecentOperationItem(
                    recipeName = "配方 ${index + 1}",
                    operationTime = "${index + 1}小时前",
                    status = if (index == 0) "已完成" else "进行中"
                )
                if (index < 2) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * 最近操作项目
 */
@Composable
fun RecentOperationItem(
    recipeName: String,
    operationTime: String,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "历史记录",
                tint = Color(0xFF757575),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = recipeName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF263238)
                )
                Text(
                    text = operationTime,
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }
        }

        Surface(
            color = if (status == "已完成") Color(0xFF4CAF50) else Color(0xFFFF9800),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = status,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DosingScreenPreview() {
    SmartDosingTheme {
        DosingScreen()
    }
}