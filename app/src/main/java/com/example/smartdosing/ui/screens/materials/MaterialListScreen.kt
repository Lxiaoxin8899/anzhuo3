package com.example.smartdosing.ui.screens.materials

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.Material
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.data.RecipeRepository
import kotlinx.coroutines.launch

/**
 * 物料清单界面
 * 核心功能：选择任务 -> 查看物料 -> 打勾确认找到的物料
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialListScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val taskRepository = remember { ConfigurationRepositoryProvider.taskRepository }
    val recipeRepository = remember { RecipeRepository.getInstance() }
    val scope = rememberCoroutineScope()
    
    // 状态
    var tasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var selectedTask by remember { mutableStateOf<ConfigurationTask?>(null) }
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingRecipe by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // 物料确认状态 - 使用物料ID作为key
    var confirmedMaterials by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // 任务下拉菜单状态
    var showTaskDropdown by remember { mutableStateOf(false) }
    
    // 加载任务列表
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            error = null
            tasks = taskRepository.fetchTasks()
                .filter { it.status != TaskStatus.CANCELLED }
        } catch (e: Exception) {
            error = "加载任务失败：${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    // 当选择任务时，加载对应配方
    LaunchedEffect(selectedTask) {
        selectedTask?.let { task ->
            try {
                isLoadingRecipe = true
                recipe = recipeRepository.getRecipeById(task.recipeId)
                // 重置确认状态
                confirmedMaterials = emptySet()
            } catch (e: Exception) {
                error = "加载配方失败：${e.message}"
            } finally {
                isLoadingRecipe = false
            }
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "物料清单",
                        fontWeight = FontWeight.Bold
                    )
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 任务选择器和进度
            TaskSelectorWithProgress(
                tasks = tasks,
                selectedTask = selectedTask,
                showDropdown = showTaskDropdown,
                onDropdownToggle = { showTaskDropdown = !showTaskDropdown },
                onTaskSelected = { task ->
                    selectedTask = task
                    showTaskDropdown = false
                },
                confirmedCount = confirmedMaterials.size,
                totalCount = recipe?.materials?.size ?: 0
            )
            
            when {
                isLoading -> {
                    LoadingState(message = "正在加载任务...")
                }
                error != null -> {
                    ErrorState(
                        message = error!!,
                        onRetry = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    error = null
                                    tasks = taskRepository.fetchTasks()
                                } catch (e: Exception) {
                                    error = "加载失败：${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    )
                }
                selectedTask == null -> {
                    EmptyState(message = "请选择一个任务查看物料清单")
                }
                isLoadingRecipe -> {
                    LoadingState(message = "正在加载配方...")
                }
                recipe == null -> {
                    EmptyState(message = "未找到该任务关联的配方")
                }
                else -> {
                    // 批量操作按钮
                    BatchOperationRow(
                        onSelectAll = {
                            recipe?.materials?.let { materials ->
                                confirmedMaterials = materials.map { it.id }.toSet()
                            }
                        },
                        onReset = {
                            confirmedMaterials = emptySet()
                        },
                        allConfirmed = recipe?.materials?.all { it.id in confirmedMaterials } == true
                    )
                    
                    // 物料列表
                    MaterialListContent(
                        materials = recipe!!.materials,
                        recipeName = recipe!!.name,
                        confirmedMaterials = confirmedMaterials,
                        onToggleConfirm = { materialId ->
                            confirmedMaterials = if (materialId in confirmedMaterials) {
                                confirmedMaterials - materialId
                            } else {
                                confirmedMaterials + materialId
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 任务选择器带进度显示
 */
@Composable
private fun TaskSelectorWithProgress(
    tasks: List<ConfigurationTask>,
    selectedTask: ConfigurationTask?,
    showDropdown: Boolean,
    onDropdownToggle: () -> Unit,
    onTaskSelected: (ConfigurationTask) -> Unit,
    confirmedCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 任务选择下拉
        Box(modifier = Modifier.weight(1f)) {
            OutlinedCard(
                onClick = onDropdownToggle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTask?.recipeName ?: "选择任务",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedTask != null) {
                            Text(
                                text = "编码: ${selectedTask.recipeCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }
            
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { onDropdownToggle() },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                if (tasks.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("暂无可用任务") },
                        onClick = { },
                        enabled = false
                    )
                } else {
                    tasks.forEach { task ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = task.recipeName,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "编码: ${task.recipeCode} | ${task.quantity}${task.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { onTaskSelected(task) },
                            leadingIcon = {
                                if (task.id == selectedTask?.id) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 进度显示
        if (totalCount > 0) {
            val progress = confirmedCount.toFloat() / totalCount
            val progressPercent = (progress * 100).toInt()
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (confirmedCount == totalCount) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (confirmedCount == totalCount) Icons.Default.CheckCircle else Icons.Default.Checklist,
                        contentDescription = null,
                        tint = if (confirmedCount == totalCount) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "$confirmedCount/$totalCount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "($progressPercent%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 批量操作按钮
 */
@Composable
private fun BatchOperationRow(
    onSelectAll: () -> Unit,
    onReset: () -> Unit,
    allConfirmed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onSelectAll,
            enabled = !allConfirmed
        ) {
            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("全选")
        }
        OutlinedButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("重置")
        }
    }
}

/**
 * 物料列表内容
 */
@Composable
private fun MaterialListContent(
    materials: List<Material>,
    recipeName: String,
    confirmedMaterials: Set<String>,
    onToggleConfirm: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "配方：$recipeName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        itemsIndexed(materials, key = { _, m -> m.id }) { index, material ->
            val isConfirmed = material.id in confirmedMaterials
            
            MaterialItemCard(
                index = index + 1,
                material = material,
                isConfirmed = isConfirmed,
                onToggle = { onToggleConfirm(material.id) }
            )
        }
    }
}

/**
 * 单个物料卡片
 */
@Composable
private fun MaterialItemCard(
    index: Int,
    material: Material,
    isConfirmed: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = if (isConfirmed) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 确认复选框
            Checkbox(
                checked = isConfirmed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )
            
            // 序号
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConfirmed) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConfirmed) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 物料信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isConfirmed) 
                        androidx.compose.ui.text.style.TextDecoration.LineThrough 
                    else 
                        null
                )
                if (material.code.isNotBlank()) {
                    Text(
                        text = "编码: ${material.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 重量
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${material.weight}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = material.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 确认状态图标
            if (isConfirmed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已确认",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 加载状态
 */
@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * 错误状态
 */
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        FilledTonalButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
