package com.example.smartdosing.ui.screens.recipes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Date

/**
 * 配方管理界面 - 新版投料看板
 * 通过左侧过滤面板 + 右侧多视图区组合，在有限空间内也能快速定位配方
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onNavigateToRecipeDetail: (String) -> Unit = {},
    onNavigateToMaterialConfiguration: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isCompactScreen = screenWidthDp < 900

    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var filteredRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf("") }
    var selectedTimeRange by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedSubCategory by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<RecipeStatus?>(null) }
    var selectedQuickCollection by remember { mutableStateOf(QuickCollectionType.ALL) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(RecipeViewMode.CARD) }
    var isFilterExpanded by remember { mutableStateOf(!isCompactScreen) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    // 删除配方相关状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }

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

    // 删除配方的回调函数
    val coroutineScope = rememberCoroutineScope()
    val onDeleteRecipe: (Recipe) -> Unit = { recipe ->
        recipeToDelete = recipe
        showDeleteDialog = true
    }
    
    // 确认删除配方
    val confirmDelete: () -> Unit = {
        recipeToDelete?.let { recipe ->
            coroutineScope.launch {
                runCatching {
                    repository.deleteRecipe(recipe.id)
                    // 刷新数据
                    val updatedRecipes = repository.getAllRecipes()
                    recipes = updatedRecipes
                }.onFailure { error ->
                    loadError = "删除失败: ${error.localizedMessage}"
                }
            }
        }
        showDeleteDialog = false
        recipeToDelete = null
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

    val categories = remember { listOf("烟油", "辅料") }
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

        filteredRecipes = filtered
    }
    val activeFilters = remember(
        selectedCategory,
        selectedSubCategory,
        selectedCustomer,
        selectedStatus,
        selectedTimeRange,
        selectedQuickCollection,
        selectedFolderId,
        folderSnapshot
    ) {
        buildList {
            if (selectedQuickCollection != QuickCollectionType.ALL) add(selectedQuickCollection.label)
            if (selectedFolderId != null) {
                folderSnapshot.firstOrNull { it.id == selectedFolderId }?.name?.let { add(it) }
            }
            if (selectedCategory.isNotEmpty()) add(selectedCategory)
            if (selectedSubCategory.isNotEmpty()) add(selectedSubCategory)
            if (selectedCustomer.isNotEmpty()) add("客户：$selectedCustomer")
            if (selectedTimeRange.isNotEmpty()) add("时间：$selectedTimeRange")
            selectedStatus?.label?.let { add(it) }
        }
    }

    fun clearFilters() {
        selectedCategory = ""
        selectedSubCategory = ""
        selectedCustomer = ""
        selectedStatus = null
        selectedTimeRange = ""
        selectedQuickCollection = QuickCollectionType.ALL
        selectedFolderId = null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(isCompactScreen) {
        if (isCompactScreen) {
            isFilterExpanded = false
        } else {
            isFilterSheetOpen = false
        }
    }

    if (isCompactScreen) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
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
                resultCount = filteredRecipes.size,
                isCompact = true,
                onFilterClick = { isFilterSheetOpen = true }
            )
            ActiveFilterSummary(
                activeFilters = activeFilters,
                onClear = { clearFilters() }
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
                            onMaterialConfiguration = onNavigateToMaterialConfiguration,
                            folders = folderSnapshot,
                            onAddToFolder = { recipeId, folderId ->
                                addRecipeToFolder(recipeId, folderId)
                            },
                            onDeleteRecipe = onDeleteRecipe,
                            compactCards = true
                        )
                    } else {
                        RecipeTableView(
                            recipes = filteredRecipes,
                            onRecipeClick = onNavigateToRecipeDetail,
                            onMaterialConfiguration = onNavigateToMaterialConfiguration,
                            folders = folderSnapshot,
                            onAddToFolder = { recipeId, folderId ->
                                addRecipeToFolder(recipeId, folderId)
                            },
                            onDeleteRecipe = onDeleteRecipe
                        )
                    }
                }
            }
        }
        if (isFilterSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isFilterSheetOpen = false },
                sheetState = sheetState
            ) {
                RecipeFilterPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                    onCollapse = { isFilterSheetOpen = false },
                    filteredSize = filteredRecipes.size,
                    showCollapseHint = false
                )
            }
        }
        
        // 删除确认对话框
        if (showDeleteDialog && recipeToDelete != null) {
            DeleteRecipeDialog(
                recipe = recipeToDelete!!,
                onConfirm = confirmDelete,
                onDismiss = {
                    showDeleteDialog = false
                    recipeToDelete = null
                }
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFilterExpanded) {
                RecipeFilterPanel(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 280.dp)
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
                        .width(44.dp)
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
                    resultCount = filteredRecipes.size,
                    isCompact = false,
                    onFilterClick = null
                )
                ActiveFilterSummary(
                    activeFilters = activeFilters,
                    onClear = { clearFilters() }
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
                                onMaterialConfiguration = onNavigateToMaterialConfiguration,
                                folders = folderSnapshot,
                                onAddToFolder = { recipeId, folderId ->
                                    addRecipeToFolder(recipeId, folderId)
                                },
                                onDeleteRecipe = onDeleteRecipe
                            )
                        } else {
                            RecipeTableView(
                                recipes = filteredRecipes,
                                onRecipeClick = onNavigateToRecipeDetail,
                                onMaterialConfiguration = onNavigateToMaterialConfiguration,
                                folders = folderSnapshot,
                                onAddToFolder = { recipeId, folderId ->
                                    addRecipeToFolder(recipeId, folderId)
                                },
                                onDeleteRecipe = onDeleteRecipe
                            )
                        }
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
    folders: List<RecipeFolder>,
    selectedFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    onAddFolder: (String) -> Unit,
    onCollectCurrent: (String) -> Unit,
    onCollapse: () -> Unit,
    filteredSize: Int,
    showCollapseHint: Boolean = true
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
        if (showCollapseHint) {
            Text(
                text = "通过折叠筛选区可腾出更多空间，保留常用组合后一键展开即可继续使用。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

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

        SectionTitle(icon = Icons.Default.Speed, text = "状态")
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
    resultCount: Int,
    isCompact: Boolean,
    onFilterClick: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "配方管理中心",
                    fontSize = if (isCompact) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "结果：$resultCount",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isCompact) {
                    IconButton(onClick = { onFilterClick?.invoke() }) {
                        Icon(Icons.Default.Tune, contentDescription = "筛选")
                    }
                }
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
            placeholder = {
                Text(
                    "搜索配方编码、名称、客户...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "清空搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )
    }
}

/**
 * 当前激活的筛选标签，方便在折叠筛选区时也能一目了然
 */
@Composable
fun ActiveFilterSummary(
    activeFilters: List<String>,
    onClear: () -> Unit
) {
    if (activeFilters.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(activeFilters) { tag ->
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        TextButton(
            onClick = onClear,
            enabled = activeFilters.isNotEmpty(),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text("清除筛选")
        }
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
    val animatedValue = remember { Animatable(0f) }
    val targetValue = value.toFloatOrNull() ?: 0f

    LaunchedEffect(value) {
        animatedValue.animateTo(
            targetValue = targetValue,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                animatedValue.value.toInt().toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onMaterialConfiguration: (String) -> Unit = {},
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit = {},
    compactCards: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
            AnimatedRecipeCard(
                recipe = recipe,
                index = index,
                onRecipeClick = onRecipeClick,
                onMaterialConfiguration = onMaterialConfiguration,
                folders = folders,
                onAddToFolder = onAddToFolder,
                onDeleteRecipe = onDeleteRecipe,
                compact = compactCards
            )
        }
    }
}

/**
 * 带动画的配方卡片
 */
@Composable
private fun AnimatedRecipeCard(
    recipe: Recipe,
    index: Int,
    onRecipeClick: (String) -> Unit,
    onMaterialConfiguration: (String) -> Unit,
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit,
    compact: Boolean
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(300)
                )
    ) {
        DosingRecipeCard(
            recipe = recipe,
            onRecipeClick = onRecipeClick,
            onMaterialConfiguration = onMaterialConfiguration,
            folders = folders,
            onAddToFolder = onAddToFolder,
            onDeleteRecipe = onDeleteRecipe,
            compact = compact
        )
    }
}

/**
 * 表格视图：高信息密度模式
 */
@Composable
fun RecipeTableView(
    recipes: List<Recipe>,
    onRecipeClick: (String) -> Unit,
    onMaterialConfiguration: (String) -> Unit = {},
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "配方信息",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "操作",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
                var visible by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 30L)
                    visible = true
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(200)) + expandVertically()
                ) {
                    Column {
                        RecipeTableRow(
                            recipe = recipe,
                            onRecipeClick = onRecipeClick,
                            onMaterialConfiguration = onMaterialConfiguration,
                            folders = folders,
                            onAddToFolder = onAddToFolder,
                            onDeleteRecipe = onDeleteRecipe
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeTableRow(
    recipe: Recipe,
    onRecipeClick: (String) -> Unit,
    onMaterialConfiguration: (String) -> Unit = {},
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit = {}
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
            IconButton(
                onClick = { onDeleteRecipe(recipe) },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除配方",
                    modifier = Modifier.size(20.dp)
                )
            }
            FolderMenuButton(
                folders = folders,
                onFolderSelected = { folderId -> onAddToFolder(recipe.id, folderId) }
            )
            Button(
                onClick = { onMaterialConfiguration(recipe.id) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("研发配置", fontSize = 12.sp)
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
    onMaterialConfiguration: (String) -> Unit = {},
    folders: List<RecipeFolder>,
    onAddToFolder: (String, String) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        onClick = { onRecipeClick(recipe.id) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        recipe.name,
                        fontSize = if (compact) 15.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    IconButton(
                        onClick = { onDeleteRecipe(recipe) },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除配方",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    FolderMenuButton(
                        folders = folders,
                        onFolderSelected = { folderId -> onAddToFolder(recipe.id, folderId) }
                    )
                    Button(
                        onClick = { onMaterialConfiguration(recipe.id) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (compact) 8.dp else 10.dp,
                            vertical = if (compact) 2.dp else 4.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (compact) "配置" else "研发配置", fontSize = 12.sp)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                Text(
                    text = "最近：${recipe.lastUsed?.takeIf { it.isNotBlank() } ?: "未记录"}",
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
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "暂无符合条件的配方",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "尝试调整搜索或过滤条件",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
    return try {
        val date = formatter.parse(dateString) ?: return false
        val diff = Date().time - date.time
        val daysDiff = diff / (1000 * 60 * 60 * 24)
        daysDiff <= days
    } catch (e: Exception) {
        false
    }
}

/**
 * 删除配方确认对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteRecipeDialog(
    recipe: Recipe,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "确认删除配方",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "您确定要删除以下配方吗?此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "配方名称: ${recipe.name}",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "配方编码: ${recipe.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (recipe.customer.isNotEmpty()) {
                            Text(
                                text = "客户: ${recipe.customer}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
