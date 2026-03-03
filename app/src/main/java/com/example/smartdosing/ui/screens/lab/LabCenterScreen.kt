package com.example.smartdosing.ui.screens.lab

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.ui.components.RecipeWeightInputDialog
import com.example.smartdosing.ui.screens.recipes.RecipesScreen
import com.example.smartdosing.ui.screens.tasks.TaskCenterScreen
import com.example.smartdosing.ui.theme.*

/**
 * 实验中心：整合任务中心与配方管理
 * 提供双标签页切换，采用实验室控制台风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabCenterScreen(
    onNavigateBack: () -> Unit = {},
    onStartTask: (ConfigurationTask) -> Unit = {},
    onNavigateToMaterialConfiguration: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToMaterialConfigurationWithWeight: (String, Double) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        LabTabItem("研发任务", Icons.Default.Assignment),
        LabTabItem("配方库", Icons.Default.List)
    )

    // 配方库启动实验：重量输入对话框状态
    var showWeightInputDialog by remember { mutableStateOf(false) }
    var selectedRecipeIdForExperiment by remember { mutableStateOf("") }

    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible.value = true }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = "实验中心",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "LABORATORY CENTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 2.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // 实验室风格网格背景 (与首页保持一致)
                val gridSize = 40.dp.toPx()
                val gridColor = Color.LightGray.copy(alpha = 0.05f)
                val dotColor = Color.LightGray.copy(alpha = 0.1f)
                
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
                }
                for (y in 0..size.height.toInt() step gridSize.toInt()) {
                    drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
                }
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawCircle(dotColor, radius = 2f, center = Offset(x.toFloat(), y.toFloat()))
                    }
                }
            }
    ) { innerPadding ->
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(animationSpec = tween(600)) + slideInVertically(initialOffsetY = { 40 }),
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标签页切换：更现代的实验室风格
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) },
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Icon(
                                        tab.icon, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp),
                                        tint = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        tab.title,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            },
                            modifier = Modifier.height(56.dp)
                        )
                    }
                }

                // 内容区域
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            TaskCenterScreen(
                                onNavigateBack = onNavigateBack,
                                onStartTask = onStartTask,
                                onConfigureTask = { task ->
                                    // 查看配方：只读模式
                                    onNavigateToMaterialConfiguration(task.recipeId.ifBlank { "quick_start" }, true)
                                }
                            )
                        }
                        1 -> {
                            RecipesScreen(
                                onNavigateToMaterialConfiguration = { recipeId ->
                                    // 配方库启动实验：弹出重量输入对话框
                                    selectedRecipeIdForExperiment = recipeId
                                    showWeightInputDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 配方库启动实验：重量输入对话框
    if (showWeightInputDialog) {
        RecipeWeightInputDialog(
            onConfirm = { totalWeight ->
                showWeightInputDialog = false
                onNavigateToMaterialConfigurationWithWeight(selectedRecipeIdForExperiment, totalWeight)
            },
            onDismiss = { showWeightInputDialog = false },
            onViewOnly = {
                showWeightInputDialog = false
                onNavigateToMaterialConfiguration(selectedRecipeIdForExperiment, true)
            }
        )
    }
}

private data class LabTabItem(
    val title: String,
    val icon: ImageVector
)
