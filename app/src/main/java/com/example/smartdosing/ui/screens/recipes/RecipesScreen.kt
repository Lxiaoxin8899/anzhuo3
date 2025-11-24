package com.example.smartdosing.ui.screens.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.RecipePriority
import com.example.smartdosing.data.RecipeRepository
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.RecipeStats
import com.example.smartdosing.data.RecipeStatus
import com.example.smartdosing.ui.theme.SmartDosingTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * 配方管理界面 - 新版投料看板
 * 通过左侧过滤面板 + 右侧多视图区组合，在有限空间内也能快速定位配方
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onNavigateToRecipeDetail: (String) -> Unit = {},
    onNavigateToDosingOperation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }

    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var filteredRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf("") }
    var selectedTimeRange by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedSubCategory by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<RecipeStatus?>(null) }
    var selectedPriority by remember { mutableStateOf<RecipePriority?>(null) }
    var selectedQuickCollection by remember { mutableStateOf(QuickCollectionType.ALL) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(RecipeViewMode.CARD) }
    var isFilterExpanded by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val customFolders = remember { mutableStateListOf<RecipeFolder>() }
    fun addRecipeToFolder(recipeId: String, folderId: String) {
        val index = customFolders.indexOfFirst { it.id == folderId }
        if (index != -1) {
            val folder = customFolders[index]
            if (!folder.recipeIds.contains(recipeId)) {
                customFolders[index] = folder.copy(recipeIds = folder.recipeIds + recipeId)
            }
        }
    }

    // 预加载数据，后续可替换成 Flow 收集
    LaunchedEffect(Unit) {
        runCatching {
            val allRecipes = repository.getAllRecipes()
            recipes = allRecipes
            filteredRecipes = allRecipes
            if (customFolders.isEmpty()) {
                customFolders.addAll(
                    listOf(
                        RecipeFolder(id = UUID.randomUUID().toString(), name = "快速交付", recipeIds = emptySet()),
                        RecipeFolder(id = UUID.randomUUID().toString(), name = "出口客户", recipeIds = emptySet())
                    )
                )
            }
        }.onSuccess {
            loadError = null
        }.onFailure { throwable ->
            loadError = throwable.localizedMessage ?: "加载配方失败，请稍后重试"
        }
    }

    var stats by remember {
        mutableStateOf(
            RecipeStats(
                totalRecipes = 0,
                categoryCounts = emptyMap(),
                customerCounts = emptyMap(),
                statusCounts = emptyMap(),
                priorityCounts = emptyMap(),
                recentlyUsed = emptyList(),
                mostUsed = emptyList(),
                recentlyCreated = emptyList(),
                categoryTree = emptyList()
            )
        )
    }
    var timeRanges by remember { mutableStateOf<List<String>>(emptyList()) }
    var statsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recipes) {
        if (recipes.isEmpty()) {
            statsError = null
            return@LaunchedEffect
        }
        runCatching {
            stats = repository.getRecipeStats()
            timeRanges = repository.getTimeRanges()
        }.onSuccess {
            statsError = null
        }.onFailure { throwable ->
            statsError = throwable.localizedMessage ?: "统计信息加载失败"
        }
    }

    val categories = remember(recipes) { recipes.map { it.category }.distinct() }
    val subCategories = remember(recipes, selectedCategory) {
        if (selectedCategory.isEmpty()) emptyList()
        else recipes.filter { it.category == selectedCategory && it.subCategory.isNotEmpty() }
            .map { it.subCategory }
            .distinct()
    }
    val customers = remember(recipes) {
        recipes.mapNotNull { it.customer.takeIf(String::isNotEmpty) }.distinct()
    }
    val folderSnapshot = customFolders.toList()
    // 综合过滤逻辑：支持搜索/分类/状态/自定义组/快速集合
    LaunchedEffect(
        recipes,
        searchText,
        selectedCustomer,
        selectedTimeRange,
        selectedCategory,
        selectedSubCategory,
        selectedStatus,
        selectedPriority,
        selectedQuickCollection,
        selectedFolderId,
        folderSnapshot
    ) {
        var filtered = recipes

        if (selectedFolderId != null) {
            val targetFolder = folderSnapshot.firstOrNull { it.id == selectedFolderId }
            filtered = filtered.filter { recipe -> targetFolder?.recipeIds?.contains(recipe.id) == true }
        }

        if (selectedQuickCollection != QuickCollectionType.ALL) {
            filtered = applyQuickCollectionFilter(filtered, selectedQuickCollection)
        }

        if (searchText.isNotEmpty()) {
            filtered = filtered.filter { recipe ->
                recipe.code.contains(searchText, ignoreCase = true) ||
                    recipe.name.contains(searchText, ignoreCase = true) ||
                    recipe.customer.contains(searchText, ignoreCase = true) ||
                    recipe.description.contains(searchText, ignoreCase = true)
            }
        }

        if (selectedCategory.isNotEmpty()) {
            filtered = filtered.filter { it.category == selectedCategory }
        }

        if (selectedSubCategory.isNotEmpty()) {
            filtered = filtered.filter { it.subCategory == selectedSubCategory }
        }

        if (selectedCustomer.isNotEmpty()) {
            filtered = filtered.filter { it.customer == selectedCustomer }
        }

        if (selectedTimeRange.isNotEmpty()) {
            val validIds = repository.getRecipesByTimeRange(selectedTimeRange).map { it.id }.toSet()
            filtered = filtered.filter { it.id in validIds }
        }

        selectedStatus?.let { status ->
            filtered = filtered.filter { it.status == status }
        }

        selectedPriority?.let { priority ->
            filtered = filtered.filter { it.priority == priority }
        }

        filteredRecipes = filtered
    }
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isFilterExpanded) {
            RecipeFilterPanel(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .fillMaxHeight(),
                categories = categories,
                subCategories = subCategories,
                selectedCategory = selectedCategory,
                selectedSubCategory = selectedSubCategory,
                onCategorySelected = {
                    selectedCategory = it
                    selectedSubCategory = ""
                },
                onSubCategorySelected = { selectedSubCategory = it },
                timeRanges = timeRanges,
                selectedTimeRange = selectedTimeRange,
                onTimeRangeSelected = { selectedTimeRange = it },
                customers = customers,
                selectedCustomer = selectedCustomer,
                onCustomerSelected = { selectedCustomer = it },
                selectedStatus = selectedStatus,
                onStatusSelected = { selectedStatus = it },
                selectedPriority = selectedPriority,
                onPrioritySelected = { selectedPriority = it },
                folders = folderSnapshot,
                selectedFolderId = selectedFolderId,
                onFolderSelected = {
                    selectedFolderId = it
                    if (it != null) {
                        selectedQuickCollection = QuickCollectionType.ALL
                    }
                },
                onAddFolder = { name ->
                    if (name.isNotBlank() && folderSnapshot.none { it.name == name }) {
                        customFolders.add(RecipeFolder(id = UUID.randomUUID().toString(), name = name, recipeIds = emptySet()))
                    }
                },
                onCollectCurrent = { folderId ->
                    val index = customFolders.indexOfFirst { it.id == folderId }
                    if (index != -1) {
                        val folder = customFolders[index]
                        val newSet = folder.recipeIds + filteredRecipes.map { it.id }
                        customFolders[index] = folder.copy(recipeIds = newSet)
                    }
                },
                onCollapse = { isFilterExpanded = false },
                filteredSize = filteredRecipes.size
            )
        } else {
            FilterCollapsedCard(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
                filteredSize = filteredRecipes.size,
                onExpand = { isFilterExpanded = true }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            loadError?.let { message ->
                Text(
                    text = "配方加载失败：$message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            RecipeWorkspaceHeader(
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                resultCount = filteredRecipes.size
            )

            QuickCollectionSection(
                selectedQuickCollection = selectedQuickCollection,
                onCollectionSelected = {
                    selectedQuickCollection = it
                    selectedFolderId = null
                }
            )

            RecipeStatsOverview(
                stats = stats,
                filteredCount = filteredRecipes.size
            )
            statsError?.let { message ->
                Text(
                    text = "统计信息加载失败：$message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                if (filteredRecipes.isEmpty()) {
                    EmptyState()
                } else {
                    if (viewMode == RecipeViewMode.CARD) {
                        RecipeCardList(
                            recipes = filteredRecipes,
                            onRecipeClick = onNavigateToRecipeDetail,
                            onStartDosing = onNavigateToDosingOperation,
                            folders = folderSnapshot,
                            onAddToFolder = { recipeId, folderId ->
                                addRecipeToFolder(recipeId, folderId)
                            }
                        )
                    } else {
                        RecipeTableView(
                            recipes = filteredRecipes,
                            onRecipeClick = onNavigateToRecipeDetail,
                            onStartDosing = onNavigateToDosingOperation,
                            folders = folderSnapshot,
                            onAddToFolder = { recipeId, folderId ->
                                addRecipeToFolder(recipeId, folderId)
                            }
                        )
                    }
                }
            }
        }
    }
}
/**
 * 左侧过滤面板：容纳分类/时间/客户/状态/标签/自定义组
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeFilterPanel(
    modifier: Modifier = Modifier,
    categories: List<String>,
    subCategories: List<String>,
    selectedCategory: String,
    selectedSubCategory: String,
    onCategorySelected: (String) -> Unit,
    onSubCategorySelected: (String) -> Unit,
    timeRanges: List<String>,
    selectedTimeRange: String,
    onTimeRangeSelected: (String) -> Unit,
    customers: List<String>,
    selectedCustomer: String,
    onCustomerSelected: (String) -> Unit,
    selectedStatus: RecipeStatus?,
    onStatusSelected: (RecipeStatus?) -> Unit,
    selectedPriority: RecipePriority?,
    onPrioritySelected: (RecipePriority?) -> Unit,
    folders: List<RecipeFolder>,
    selectedFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    onAddFolder: (String) -> Unit,
    onCollectCurrent: (String) -> Unit,
    onCollapse: () -> Unit,
    filteredSize: Int
) {
    var newFolderName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("过滤方案", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    text = "当前结果：$filteredSize 条",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "收起筛选")
            }
        }
        Text(
            text = "通过折叠筛选区可腾出更多空间，保留常用组合后一键展开即可继续使用。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        SectionTitle(icon = Icons.Default.Category, text = "一级分类")
        FilterChipGroup(
            options = listOf("全部") + categories,
            selected = selectedCategory.ifEmpty { "全部" },
            onSelected = { choice -> onCategorySelected(choice.takeIf { it != "全部" } ?: "") }
        )

        if (selectedCategory.isNotEmpty() && subCategories.isNotEmpty()) {
            SectionTitle(icon = Icons.Default.List, text = "子分类")
            FilterChipGroup(
                options = listOf("全部") + subCategories,
                selected = selectedSubCategory.ifEmpty { "全部" },
                onSelected = { choice -> onSubCategorySelected(choice.takeIf { it != "全部" } ?: "") }
            )
        }

        SectionTitle(icon = Icons.Default.Schedule, text = "时间范围")
        FilterChipGroup(
            options = listOf("全部") + timeRanges,
            selected = selectedTimeRange.ifEmpty { "全部" },
            onSelected = { choice -> onTimeRangeSelected(choice.takeIf { it != "全部" } ?: "") }
        )

        SectionTitle(icon = Icons.Default.Groups, text = "客户/项目")
        FilterChipGroup(
            options = listOf("全部") + customers,
            selected = selectedCustomer.ifEmpty { "全部" },
            onSelected = { choice -> onCustomerSelected(choice.takeIf { it != "全部" } ?: "") },
            wrap = true
        )

        SectionTitle(icon = Icons.Default.Speed, text = "状态 / 优先级")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedStatus == null,
                onClick = { onStatusSelected(null) },
                label = { Text("全部状态") }
            )
            RecipeStatus.values().forEach { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onStatusSelected(status) },
                    label = { Text(status.label) }
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPriority == null,
                onClick = { onPrioritySelected(null) },
                label = { Text("全部优先级") }
            )
            RecipePriority.values().forEach { priority ->
                FilterChip(
                    selected = selectedPriority == priority,
                    onClick = { onPrioritySelected(priority) },
                    label = { Text(priority.label) }
                )
            }
        }
        SectionTitle(icon = Icons.Default.Folder, text = "自定义分组")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "提示：点击分组可切换查看，“收录当前”可把当前筛选结果一次性添加；也可在配方卡片上的“加入分组”按钮逐条添加。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { onFolderSelected(null) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selectedFolderId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("全部自定义组")
            }

            folders.forEach { folder ->
                val selected = folder.id == selectedFolderId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onFolderSelected(folder.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(folder.name, fontWeight = FontWeight.Medium)
                        Text(
                            text = "已含 ${folder.recipeIds.size} 条配方",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onCollectCurrent(folder.id) }) {
                        Icon(
                            imageVector = Icons.Default.LibraryAdd,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("收录当前")
                    }
                }
            }

            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                label = { Text("新建分组") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onAddFolder(newFolderName.trim())
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "确认新建")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FilterCollapsedCard(
    modifier: Modifier = Modifier,
    filteredSize: Int,
    onExpand: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onExpand() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Tune, contentDescription = "展开筛选")
        Text("筛选", fontWeight = FontWeight.SemiBold)
        Text(
            text = "$filteredSize 条",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null)
    }
}
/**
 * 右侧顶部操作栏：搜索 + 视图切换 + 结果计数
 */
@Composable
fun RecipeWorkspaceHeader(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    viewMode: RecipeViewMode,
    onViewModeChange: (RecipeViewMode) -> Unit,
    resultCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("工业配方投料看板", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "结果数：$resultCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconToggleChip(
                    label = "卡片",
                    icon = Icons.Default.ViewModule,
                    selected = viewMode == RecipeViewMode.CARD,
                    onClick = { onViewModeChange(RecipeViewMode.CARD) }
                )
                IconToggleChip(
                    label = "表格",
                    icon = Icons.Default.ViewList,
                    selected = viewMode == RecipeViewMode.TABLE,
                    onClick = { onViewModeChange(RecipeViewMode.TABLE) }
                )
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索配方编码、名称、客户...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
/**
 * 快捷集合：最近使用 / 高频生产 / 草稿等
 */
@Composable
fun QuickCollectionSection(
    selectedQuickCollection: QuickCollectionType,
    onCollectionSelected: (QuickCollectionType) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(QuickCollectionType.values()) { type ->
            FilterChip(
                selected = selectedQuickCollection == type,
                onClick = { onCollectionSelected(type) },
                label = { Text(type.label) },
                leadingIcon = {
                    Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * 顶部统计卡片：展示总数 / 当前结果 / 高优先级等
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeStatsOverview(
    stats: RecipeStats,
    filteredCount: Int
) {
    val urgentCount = stats.priorityCounts[RecipePriority.URGENT] ?: 0
    val draftCount = stats.statusCounts[RecipeStatus.DRAFT] ?: 0

    val items = listOf(
        Triple("总配方", stats.totalRecipes.toString(), Icons.Default.AllInbox),
        Triple("当前结果", filteredCount.toString(), Icons.Default.FilterAlt),
        Triple("紧急", urgentCount.toString(), Icons.Default.Bolt),
        Triple("草稿/待审", draftCount.toString(), Icons.Default.Article)
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, value, icon) ->
            CompactStatChip(label = label, value = value, icon = icon)
        }
    }
}

@Composable
fun CompactStatChip(
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
/**
 * 卡片视图：适合触摸屏操作
 */
@Composable
fun RecipeCardList(
    recipes: List<Recipe>,
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit,
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recipes) { recipe ->
                DosingRecipeCard(
                    recipe = recipe,
                    onRecipeClick = onRecipeClick,
                    onStartDosing = onStartDosing,
                    folders = folders,
                    onAddToFolder = onAddToFolder
                )
            }
        }
}

/**
 * 表格视图：高信息密度模式
 */
@Composable
fun RecipeTableView(
    recipes: List<Recipe>,
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit,
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("配方信息", fontWeight = FontWeight.Medium)
            Text("操作", fontWeight = FontWeight.Medium)
        }
        Divider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(recipes) { recipe ->
                RecipeTableRow(
                    recipe = recipe,
                    onRecipeClick = onRecipeClick,
                    onStartDosing = onStartDosing,
                    folders = folders,
                    onAddToFolder = onAddToFolder
                )
                Divider()
            }
        }
    }
}

@Composable
fun RecipeTableRow(
    recipe: Recipe,
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit,
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRecipeClick(recipe.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(recipe.name, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("编码：${recipe.code}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipe.customer.isNotEmpty()) {
                    Text("客户：${recipe.customer}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("用量：${recipe.totalWeight}g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = recipe.status.label,
                    color = when (recipe.status) {
                        RecipeStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        RecipeStatus.DRAFT -> MaterialTheme.colorScheme.tertiary
                        RecipeStatus.INACTIVE -> MaterialTheme.colorScheme.error
                        RecipeStatus.ARCHIVED -> MaterialTheme.colorScheme.outline
                    },
                    icon = Icons.Default.TrackChanges
                )
                StatusPill(
                    text = recipe.priority.label,
                    color = when (recipe.priority) {
                        RecipePriority.URGENT -> MaterialTheme.colorScheme.error
                        RecipePriority.HIGH -> MaterialTheme.colorScheme.primary
                        RecipePriority.NORMAL -> MaterialTheme.colorScheme.secondary
                        RecipePriority.LOW -> MaterialTheme.colorScheme.outline
                    },
                    icon = Icons.Default.Flag
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderMenuButton(
                folders = folders,
                onFolderSelected = { folderId -> onAddToFolder(recipe.id, folderId) }
            )
            Button(
                onClick = { onStartDosing(recipe.id) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("投料", fontSize = 12.sp)
            }
        }
    }
}
/**
 * 卡片样式 - 增加状态/优先级/标签展示
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DosingRecipeCard(
    recipe: Recipe,
    onRecipeClick: (String) -> Unit,
    onStartDosing: (String) -> Unit,
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onRecipeClick(recipe.id) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recipe.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("编码：${recipe.code}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (recipe.customer.isNotEmpty()) {
                            Text("客户：${recipe.customer}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("总重：${recipe.totalWeight} g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FolderMenuButton(
                        folders = folders,
                        onFolderSelected = { folderId -> onAddToFolder(recipe.id, folderId) }
                    )
                    Button(
                        onClick = { onStartDosing(recipe.id) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("投料", fontSize = 12.sp)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = recipe.status.label,
                    color = when (recipe.status) {
                        RecipeStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        RecipeStatus.DRAFT -> MaterialTheme.colorScheme.tertiary
                        RecipeStatus.INACTIVE -> MaterialTheme.colorScheme.error
                        RecipeStatus.ARCHIVED -> MaterialTheme.colorScheme.outline
                    },
                    icon = Icons.Default.Verified
                )
                StatusPill(
                    text = recipe.priority.label,
                    color = when (recipe.priority) {
                        RecipePriority.URGENT -> MaterialTheme.colorScheme.error
                        RecipePriority.HIGH -> MaterialTheme.colorScheme.primary
                        RecipePriority.NORMAL -> MaterialTheme.colorScheme.secondary
                        RecipePriority.LOW -> MaterialTheme.colorScheme.outline
                    },
                    icon = Icons.Default.Flag
                )
                Text(
                    text = "材料：${recipe.materials.size} 项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
fun FolderMenuButton(
    folders: List<RecipeFolder>,
    onFolderSelected: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val enabled = folders.isNotEmpty()
    IconButton(
        onClick = { if (enabled) menuExpanded = true },
        enabled = enabled
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = if (enabled) "加入分组" else "暂无分组",
            tint = MaterialTheme.colorScheme.primary
        )
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        folders.forEach { folder ->
            DropdownMenuItem(
                text = { Text(folder.name) },
                onClick = {
                    menuExpanded = false
                    onFolderSelected(folder.id)
                }
            )
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("暂无符合条件的配方", fontWeight = FontWeight.Medium)
        Text("尝试调整搜索或过滤条件", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
/**
 * 自定义图标切换按钮
 */
@Composable
fun IconToggleChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

/**
 * 通用的 FilterChip 组装方法
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChipGroup(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    wrap: Boolean = false
) {
    if (wrap) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) }
                )
            }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) }
                )
            }
        }
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
/**
 * 快速集合类型
 */
enum class QuickCollectionType(val label: String, val icon: ImageVector) {
    ALL("全部配方", Icons.Outlined.AllInbox),
    RECENT("最近使用", Icons.Outlined.Schedule),
    FREQUENT("高频生产", Icons.Outlined.TrendingUp),
    DRAFT("草稿/未审核", Icons.Outlined.Article),
    URGENT("紧急任务", Icons.Outlined.Bolt)
}

/**
 * 视图模式
 */
enum class RecipeViewMode {
    CARD,
    TABLE
}

/**
 * 自定义文件夹实体
 */
data class RecipeFolder(
    val id: String,
    val name: String,
    val recipeIds: Set<String>
)

/**
 * 快捷集合过滤规则
 */
fun applyQuickCollectionFilter(
    source: List<Recipe>,
    type: QuickCollectionType
): List<Recipe> {
    return when (type) {
        QuickCollectionType.ALL -> source
        QuickCollectionType.RECENT -> source.filter { isWithinDays(it.lastUsed, 7) }
        QuickCollectionType.FREQUENT -> source.filter { it.usageCount >= 5 }
        QuickCollectionType.DRAFT -> source.filter { it.status == RecipeStatus.DRAFT }
        QuickCollectionType.URGENT -> source.filter { it.priority == RecipePriority.URGENT }
    }
}

/**
 * 判断是否在指定天数内使用过
 */
fun isWithinDays(dateString: String?, days: Int): Boolean {
    if (dateString.isNullOrBlank()) return false
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return runCatching {
        val targetDate = formatter.parse(dateString) ?: return false
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        targetDate.after(calendar.time)
    }.getOrDefault(false)
}

private val RecipeStatus.label: String
    get() = when (this) {
        RecipeStatus.ACTIVE -> "已启用"
        RecipeStatus.INACTIVE -> "禁用"
        RecipeStatus.DRAFT -> "草稿"
        RecipeStatus.ARCHIVED -> "归档"
    }

private val RecipePriority.label: String
    get() = when (this) {
        RecipePriority.URGENT -> "紧急"
        RecipePriority.HIGH -> "高"
        RecipePriority.NORMAL -> "标准"
        RecipePriority.LOW -> "低"
    }

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun RecipeScreenPreview() {
    SmartDosingTheme {
        RecipesScreen()
    }
}
