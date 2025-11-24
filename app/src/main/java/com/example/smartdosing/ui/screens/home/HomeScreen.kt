package com.example.smartdosing.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTheme

/**
 * SmartDosing 系统首页
 * 傻瓜式操作界面，适合一线操作人员使用
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
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 简化的系统标题
        SimplifiedSystemHeader()

        // 当前系统状态
        SystemStatusIndicator()

        // 主要操作按钮区域（傻瓜式大按钮）
        MainActionButtons(
            onNavigateToDosingOperation = onNavigateToDosingOperation,
            onNavigateToRecipes = onNavigateToRecipes,
            onNavigateToRecords = onNavigateToRecords,
            onImportRecipe = onImportRecipe
        )

        Spacer(modifier = Modifier.weight(1f))

        // 简化的最近操作记录（仅显示最重要的）
        SimplifiedRecentSection()

        // 设置入口（底部小按钮）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("设置", fontSize = 14.sp)
            }
        }
    }
}

/**
 * 简化的系统标题区域
 */
@Composable
fun SimplifiedSystemHeader() {
    Text(
        text = "SmartDosing 投料系统",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 系统状态指示器
 */
@Composable
fun SystemStatusIndicator() {
    val statusColor = MaterialTheme.colorScheme.secondary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "系统状态",
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "系统正常 • 可以开始投料",
                fontSize = 16.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 主要操作按钮区域 - 傻瓜式大按钮设计
 */
@Composable
fun MainActionButtons(
    onNavigateToDosingOperation: (String) -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onImportRecipe: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 主要操作：开始投料（超大按钮）
        Button(
            onClick = { onNavigateToDosingOperation("quick_start") },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "开始投料",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "开始投料",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onImportRecipe,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = "导入配方",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text("导入配方文件", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "支持 CSV / Excel 文件，一键生成投料清单",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 次要操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 配方管理
            Button(
                onClick = onNavigateToRecipes,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "配方管理",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "配方管理",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 查看记录
            Button(
                onClick = onNavigateToRecords,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "投料记录",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "投料记录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 简化的最近操作区域
 */
@Composable
fun SimplifiedRecentSection() {
    Column {
        Text(
            text = "最近操作",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                SimplifiedRecentItem(
                    recipeName = "苹果香精配方",
                    time = "30分钟前",
                    status = "已完成"
                )
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                SimplifiedRecentItem(
                    recipeName = "柠檬酸配方",
                    time = "2小时前",
                    status = "已完成"
                )
            }
        }
    }
}

/**
 * 简化的最近操作项目
 */
@Composable
fun SimplifiedRecentItem(
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = time,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = status,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
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
