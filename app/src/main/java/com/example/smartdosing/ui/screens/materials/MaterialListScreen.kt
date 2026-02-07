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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.Material
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import kotlinx.coroutines.launch

/**
 * 物料清单：备料模式重构
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialListScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val taskRepository = remember { ConfigurationRepositoryProvider.taskRepository }
    val recipeRepository = remember { DatabaseRecipeRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var tasks by remember { mutableStateOf<List<ConfigurationTask>>(emptyList()) }
    var selectedTask by remember { mutableStateOf<ConfigurationTask?>(null) }
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingRecipe by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmedMaterials by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTaskDropdown by remember { mutableStateOf(false) }
    var taskSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            error = null
            tasks = taskRepository.fetchTasks().filter { it.status != TaskStatus.CANCELLED }
        } catch (e: Exception) {
            error = "加载任务失败：${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    LaunchedEffect(selectedTask) {
        selectedTask?.let { task ->
            try {
                isLoadingRecipe = true
                recipe = recipeRepository.getRecipeById(task.recipeId)
                confirmedMaterials = emptySet()
            } catch (e: Exception) {
                error = "加载配方失败：${e.message}"
            } finally {
                isLoadingRecipe = false
            }
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize().drawBehind {
            val gridSize = 40.dp.toPx()
            val gridColor = Color.LightGray.copy(alpha = 0.05f)
            for (x in 0..size.width.toInt() step gridSize.toInt()) {
                drawLine(gridColor, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), strokeWidth = 1f)
            }
            for (y in 0..size.height.toInt() step gridSize.toInt()) {
                drawLine(gridColor, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f)
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("物料备料清单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("MATERIAL PREPARATION LIST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), letterSpacing = 2.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 任务选择器
            TaskSelectorCard(
                tasks = tasks,
                selectedTask = selectedTask,
                showDropdown = showTaskDropdown,
                onDropdownToggle = { showTaskDropdown = !showTaskDropdown },
                onTaskSelected = { task -> selectedTask = task; showTaskDropdown = false; taskSearchQuery = "" },
                searchQuery = taskSearchQuery,
                onSearchQueryChange = { taskSearchQuery = it }
            )
            
            if (selectedTask != null && recipe != null) {
                PrepProgressSection(
                    confirmedCount = confirmedMaterials.size,
                    totalCount = recipe!!.materials.size
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> LoadingState("正在同步任务...")
                    error != null -> ErrorState(error!!) { /* Retry */ }
                    selectedTask == null -> EmptyState("请选择一个研发任务以开始备料")
                    isLoadingRecipe -> LoadingState("正在加载配方物料...")
                    recipe == null -> EmptyState("未找到配方数据")
                    else -> {
                        MaterialListContent(
                            materials = recipe!!.materials,
                            recipeTotal = recipe!!.totalWeight,
                            taskQuantity = selectedTask!!.quantity,
                            taskUnit = selectedTask!!.unit,
                            confirmedMaterials = confirmedMaterials,
                            onToggleConfirm = { id ->
                                confirmedMaterials = if (id in confirmedMaterials) confirmedMaterials - id else confirmedMaterials + id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSelectorCard(
    tasks: List<ConfigurationTask>,
    selectedTask: ConfigurationTask?,
    showDropdown: Boolean,
    onDropdownToggle: () -> Unit,
    onTaskSelected: (ConfigurationTask) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val filteredTasks = tasks.filter { it.recipeName.contains(searchQuery, true) || it.recipeCode.contains(searchQuery, true) }
    
    Box {
        Card(
            onClick = onDropdownToggle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedTask?.recipeName ?: "选择研发任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selectedTask?.let { "编码: ${it.recipeCode} | 目标: ${it.quantity}${it.unit}" } ?: "点击选择需要备料的任务", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (showDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = onDropdownToggle,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 400.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("搜索配方...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )
            filteredTasks.forEach { task ->
                DropdownMenuItem(
                    text = { Text(task.recipeName) },
                    onClick = { onTaskSelected(task) }
                )
            }
        }
    }
}

@Composable
private fun PrepProgressSection(confirmedCount: Int, totalCount: Int) {
    val progress = if (totalCount > 0) confirmedCount.toFloat() / totalCount else 0f
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("备料进度", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("$confirmedCount / $totalCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun MaterialListContent(
    materials: List<Material>,
    recipeTotal: Double,
    taskQuantity: Double,
    taskUnit: String,
    confirmedMaterials: Set<String>,
    onToggleConfirm: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(materials) { index, material ->
            val (actualWeight, displayUnit) = calculateActualWeight(material.weight, recipeTotal, taskQuantity, taskUnit)
            val isConfirmed = material.id in confirmedMaterials
            
            MaterialPrepCard(
                index = index + 1,
                material = material,
                weight = actualWeight,
                unit = displayUnit,
                isConfirmed = isConfirmed,
                onToggle = { onToggleConfirm(material.id) }
            )
        }
    }
}

@Composable
private fun MaterialPrepCard(
    index: Int,
    material: Material,
    weight: Double,
    unit: String,
    isConfirmed: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isConfirmed) MaterialTheme.colorScheme.primary.copy(0.05f) else MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isConfirmed) MaterialTheme.colorScheme.primary.copy(0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 序号与勾选状态
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isConfirmed) Icon(Icons.Default.Check, null, tint = Color.White)
                else Text("$index", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(material.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, textDecoration = if (isConfirmed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                Text("编码: ${material.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                // 模拟的库存/效期状态
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusTag("库存充足", Color(0xFF4CAF50))
                    StatusTag("有效期内", MaterialTheme.colorScheme.primary)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(formatWeight(weight), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { /* Scan Simulation */ }, modifier = Modifier.size(32.dp).padding(top = 4.dp)) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
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
    totalCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    // 根据搜索词过滤任务
    val filteredTasks = remember(tasks, searchQuery) {
        if (searchQuery.isEmpty()) {
            tasks
        } else {
            tasks.filter { task ->
                task.recipeName.contains(searchQuery, ignoreCase = true) ||
                task.recipeCode.contains(searchQuery, ignoreCase = true) ||
                task.customer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                                text = "编码: ${selectedTask.recipeCode} | ${selectedTask.quantity}${selectedTask.unit}",
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
                onDismissRequest = {
                    onDropdownToggle()
                    onSearchQueryChange("")
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 400.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("搜索配方名称、编码...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (filteredTasks.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (searchQuery.isEmpty()) "暂无可用任务"
                                else "未找到匹配的任务"
                            )
                        },
                        onClick = { },
                        enabled = false
                    )
                } else {
                    filteredTasks.forEach { task ->
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
    recipeTotal: Double,
    taskQuantity: Double,
    taskUnit: String,
    confirmedMaterials: Set<String>,
    onToggleConfirm: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配方：$recipeName",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "任务量：$taskQuantity $taskUnit",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        itemsIndexed(materials, key = { _, m -> m.id }) { index, material ->
            val isConfirmed = material.id in confirmedMaterials
            val (actualWeight, displayUnit) = calculateActualWeight(
                materialWeight = material.weight,
                recipeTotal = recipeTotal,
                taskQuantity = taskQuantity,
                taskUnit = taskUnit
            )

            MaterialItemCard(
                index = index + 1,
                material = material,
                actualWeight = actualWeight,
                displayUnit = displayUnit,
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
    actualWeight: Double,
    displayUnit: String,
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

            // 重量显示
            Column(horizontalAlignment = Alignment.End) {
                // 实际需要量（主要显示）
                Text(
                    text = formatWeight(actualWeight),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = displayUnit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 配方原始重量（次要显示）
                Text(
                    text = "(配方: ${material.weight}${material.unit})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
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

/**
 * 计算物料的实际需要重量
 * @param materialWeight 物料在配方中的重量（克）
 * @param recipeTotal 配方总重量（克）
 * @param taskQuantity 任务总量
 * @param taskUnit 任务单位（kg/g）
 * @return Pair<Double, String> 实际重量和显示单位
 */
private fun calculateActualWeight(
    materialWeight: Double,
    recipeTotal: Double,
    taskQuantity: Double,
    taskUnit: String
): Pair<Double, String> {
    if (recipeTotal <= 0) return Pair(materialWeight, "g")

    // 将任务总量转换为克
    val taskQuantityInGrams = when (taskUnit.lowercase()) {
        "kg" -> taskQuantity * 1000
        "g" -> taskQuantity
        else -> taskQuantity * 1000  // 默认按 kg 处理
    }

    // 计算实际重量（克）
    val actualWeightInGrams = materialWeight * (taskQuantityInGrams / recipeTotal)

    // 智能选择显示单位
    return if (actualWeightInGrams >= 1000) {
        Pair(actualWeightInGrams / 1000, "kg")
    } else {
        Pair(actualWeightInGrams, "g")
    }
}

/**
 * 格式化重量显示
 */
private fun formatWeight(weight: Double): String {
    return if (weight >= 100) {
        "%.1f".format(weight)
    } else if (weight >= 10) {
        "%.2f".format(weight)
    } else {
        "%.3f".format(weight)
    }
}
