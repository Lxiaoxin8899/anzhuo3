package com.example.smartdosing.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * 配方数据仓库
 * 负责管理配方数据的增删改查
 */
class RecipeRepository {

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    init {
        // 初始化一些示例数据
        loadSampleRecipes()
    }

    /**
     * 获取所有配方
     */
    fun getAllRecipes(): List<Recipe> {
        return _recipes.value
    }

    /**
     * 根据ID获取配方
     */
    fun getRecipeById(id: String): Recipe? {
        return _recipes.value.find { it.id == id }
    }

    /**
     * 根据分类获取配方
     */
    fun getRecipesByCategory(category: String): List<Recipe> {
        return if (category == "全部") {
            _recipes.value
        } else {
            _recipes.value.filter { it.category == category || it.subCategory == category }
        }
    }

    /**
     * 根据客户获取配方
     */
    fun getRecipesByCustomer(customer: String): List<Recipe> {
        return _recipes.value.filter { it.customer == customer }
    }

    /**
     * 根据筛选条件获取配方
     */
    fun getFilteredRecipes(filter: RecipeFilter): List<Recipe> {
        var filteredRecipes = _recipes.value

        // 一级分类过滤
        if (filter.category.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { it.category == filter.category }
        }

        // 二级分类过滤
        if (filter.subCategory.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { it.subCategory == filter.subCategory }
        }

        // 客户过滤
        if (filter.customer.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { it.customer == filter.customer }
        }

        // 状态过滤
        filter.status?.let { status ->
            filteredRecipes = filteredRecipes.filter { it.status == status }
        }

        // 优先级过滤
        filter.priority?.let { priority ->
            filteredRecipes = filteredRecipes.filter { it.priority == priority }
        }

        // 时间范围过滤
        filter.timeRange?.let { timeRange ->
            filteredRecipes = filteredRecipes.filter { recipe ->
                recipe.createTime >= timeRange.startTime && recipe.createTime <= timeRange.endTime
            }
        }

        // 搜索文本过滤
        if (filter.searchText.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { recipe ->
                recipe.code.contains(filter.searchText, ignoreCase = true) ||
                recipe.name.contains(filter.searchText, ignoreCase = true) ||
                recipe.description.contains(filter.searchText, ignoreCase = true) ||
                recipe.customer.contains(filter.searchText, ignoreCase = true) ||
                recipe.materials.any { it.name.contains(filter.searchText, ignoreCase = true) }
            }
        }

        // 标签过滤
        if (filter.tags.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { recipe ->
                filter.tags.all { tag -> recipe.tags.contains(tag) }
            }
        }

        // 创建者过滤
        if (filter.creator.isNotEmpty()) {
            filteredRecipes = filteredRecipes.filter { it.creator == filter.creator }
        }

        // 排序
        filteredRecipes = when (filter.sortBy) {
            SortType.CREATE_TIME -> filteredRecipes.sortedBy { it.createTime }
            SortType.UPDATE_TIME -> filteredRecipes.sortedBy { it.updateTime }
            SortType.LAST_USED -> filteredRecipes.sortedBy { it.lastUsed ?: "" }
            SortType.USAGE_COUNT -> filteredRecipes.sortedBy { it.usageCount }
            SortType.NAME -> filteredRecipes.sortedBy { it.name }
            SortType.CODE -> filteredRecipes.sortedBy { it.code }
            SortType.CUSTOMER -> filteredRecipes.sortedBy { it.customer }
        }

        // 排序顺序
        if (filter.sortOrder == SortOrder.DESC) {
            filteredRecipes = filteredRecipes.reversed()
        }

        return filteredRecipes
    }

    /**
     * 搜索配方
     */
    fun searchRecipes(query: String): List<Recipe> {
        if (query.isBlank()) return _recipes.value

        return _recipes.value.filter { recipe ->
            recipe.name.contains(query, ignoreCase = true) ||
            recipe.description.contains(query, ignoreCase = true) ||
            recipe.materials.any { it.name.contains(query, ignoreCase = true) }
        }
    }

    /**
     * 添加新配方
     */
    fun addRecipe(request: RecipeImportRequest): Recipe {
        val newId = "recipe_${System.currentTimeMillis()}"
        val currentTime = dateFormat.format(Date())

        // 计算材料信息并写入导入模板提供的编码，确保多端数据一致
        val materials = request.materials.mapIndexed { index, materialImport ->
            Material(
                id = "material_${System.currentTimeMillis()}_$index",
                name = materialImport.name,
                weight = materialImport.weight,
                unit = materialImport.unit,
                sequence = materialImport.sequence,
                notes = materialImport.notes,
                code = materialImport.code
            )
        }

        // 计算总重量
        val totalWeight = materials.sumOf { it.weight }

        // 生成配方编码（如果没有提供）
        val code = if (request.code.isNotEmpty()) {
            // 检查编码是否重复
            if (_recipes.value.any { it.code == request.code }) {
                throw IllegalArgumentException("配方编码 ${request.code} 已存在，请使用其他编码")
            }
            request.code
        } else {
            generateUniqueCode(request.category, totalWeight, request.name)
        }

        val recipe = Recipe(
            id = newId,
            code = code,
            name = request.name,
            category = request.category,
            subCategory = request.subCategory,
            customer = request.customer,
            batchNo = request.batchNo,
            version = request.version,
            description = request.description,
            materials = materials,
            totalWeight = totalWeight,
            createTime = currentTime,
            updateTime = currentTime,
            lastUsed = null,
            usageCount = 0,
            status = request.status,
            priority = request.priority,
            tags = request.tags,
            creator = request.creator,
            reviewer = request.reviewer
        )

        val updatedList = _recipes.value.toMutableList()
        updatedList.add(recipe)
        _recipes.value = updatedList

        return recipe
    }

    /**
     * 生成唯一配方编码 - 支持相同料号不同配置量
     */
    private fun generateUniqueCode(category: String, totalWeight: Double = 0.0, recipeName: String = ""): String {
        val prefix = when (category) {
            "香精" -> "XJ"
            "酸类" -> "SL"
            "甜味剂" -> "TWJ"
            "色素" -> "SS"
            "防腐剂" -> "FFS"
            "增稠剂" -> "ZCJ"
            else -> "QT"
        }

        val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        // 将总重量转换为编码的一部分（去掉小数点，取整数部分）
        val weightCode = if (totalWeight > 0) {
            "${totalWeight.toInt().toString().padStart(3, '0')}"
        } else {
            "000"
        }

        var counter = 1
        var code: String

        do {
            // 新的编码格式：前缀+日期+重量+序号
            code = "${prefix}${dateStr}${weightCode}${counter.toString().padStart(2, '0')}"
            counter++
        } while (_recipes.value.any { it.code == code })

        return code
    }

    /**
     * 更新配方
     */
    fun updateRecipe(id: String, request: RecipeImportRequest): Recipe? {
        val existingRecipe = getRecipeById(id) ?: return null
        val currentTime = dateFormat.format(Date())

        // 检查配方编码是否重复（排除当前配方）
        if (request.code.isNotEmpty() && request.code != existingRecipe.code) {
            if (_recipes.value.any { it.code == request.code && it.id != id }) {
                throw IllegalArgumentException("配方编码 ${request.code} 已存在，请使用其他编码")
            }
        }

        // 更新时同样带上材料编码，避免导入后的数据丢失关键字段
        val materials = request.materials.mapIndexed { index, materialImport ->
            Material(
                id = "material_${System.currentTimeMillis()}_$index",
                name = materialImport.name,
                weight = materialImport.weight,
                unit = materialImport.unit,
                sequence = materialImport.sequence,
                notes = materialImport.notes,
                code = materialImport.code
            )
        }

        val totalWeight = materials.sumOf { it.weight }

        val updatedRecipe = existingRecipe.copy(
            code = if (request.code.isNotEmpty()) request.code else existingRecipe.code,
            name = request.name,
            category = request.category,
            subCategory = request.subCategory,
            customer = request.customer,
            batchNo = request.batchNo,
            version = request.version,
            description = request.description,
            materials = materials,
            totalWeight = totalWeight,
            updateTime = currentTime,
            status = request.status,
            priority = request.priority,
            tags = request.tags,
            reviewer = request.reviewer
        )

        val updatedList = _recipes.value.toMutableList()
        val index = updatedList.indexOfFirst { it.id == id }
        if (index != -1) {
            updatedList[index] = updatedRecipe
            _recipes.value = updatedList
        }

        return updatedRecipe
    }

    /**
     * 删除配方
     */
    fun deleteRecipe(id: String): Boolean {
        val updatedList = _recipes.value.toMutableList()
        val removed = updatedList.removeIf { it.id == id }
        if (removed) {
            _recipes.value = updatedList
        }
        return removed
    }

    /**
     * 标记配方被使用
     */
    fun markRecipeUsed(id: String): Recipe? {
        val recipe = getRecipeById(id) ?: return null
        val currentTime = dateFormat.format(Date())

        val updatedRecipe = recipe.copy(
            lastUsed = currentTime,
            usageCount = recipe.usageCount + 1
        )

        val updatedList = _recipes.value.toMutableList()
        val index = updatedList.indexOfFirst { it.id == id }
        if (index != -1) {
            updatedList[index] = updatedRecipe
            _recipes.value = updatedList
        }

        return updatedRecipe
    }

    /**
     * 获取配方统计信息
     */
    fun getRecipeStats(): RecipeStats {
        val allRecipes = _recipes.value

        // 按分类统计
        val categoryCounts = allRecipes.groupBy { it.category }
            .mapValues { it.value.size }

        // 按客户统计
        val customerCounts = allRecipes
            .filter { it.customer.isNotEmpty() }
            .groupBy { it.customer }
            .mapValues { it.value.size }

        // 按状态统计
        val statusCounts = allRecipes.groupBy { it.status }
            .mapValues { it.value.size }

        // 按优先级统计
        val priorityCounts = allRecipes.groupBy { it.priority }
            .mapValues { it.value.size }

        // 最近使用的配方
        val recentlyUsed = allRecipes
            .filter { it.lastUsed != null }
            .sortedByDescending { it.lastUsed }
            .take(10)

        // 最常用的配方
        val mostUsed = allRecipes
            .filter { it.usageCount > 0 }
            .sortedByDescending { it.usageCount }
            .take(10)

        // 最近创建的配方
        val recentlyCreated = allRecipes
            .sortedByDescending { it.createTime }
            .take(10)

        // 构建分类树
        val categoryTree = buildCategoryTree(allRecipes)

        return RecipeStats(
            totalRecipes = allRecipes.size,
            categoryCounts = categoryCounts,
            customerCounts = customerCounts,
            statusCounts = statusCounts,
            priorityCounts = priorityCounts,
            recentlyUsed = recentlyUsed,
            mostUsed = mostUsed,
            recentlyCreated = recentlyCreated,
            categoryTree = categoryTree
        )
    }

    /**
     * 构建分类树结构
     */
    private fun buildCategoryTree(recipes: List<Recipe>): List<CategoryInfo> {
        val categoryTree = mutableListOf<CategoryInfo>()

        // 按一级分类分组
        val primaryCategories = recipes.groupBy { it.category }

        primaryCategories.forEach { (category, categoryRecipes) ->
            // 按二级分类（客户）分组
            val customerGroups = categoryRecipes
                .filter { it.customer.isNotEmpty() }
                .groupBy { it.customer }

            val children = customerGroups.map { (customer, customerRecipes) ->
                CategoryInfo(
                    type = CategoryType.CUSTOMER,
                    name = customer,
                    count = customerRecipes.size,
                    color = getCustomerColor(customer)
                )
            }

            categoryTree.add(
                CategoryInfo(
                    type = CategoryType.MATERIAL,
                    name = category,
                    count = categoryRecipes.size,
                    icon = getCategoryIcon(category),
                    color = getCategoryColor(category),
                    children = children
                )
            )
        }

        return categoryTree.sortedByDescending { it.count }
    }

    /**
     * 获取分类图标
     */
    fun getCategoryIcon(category: String): String {
        return when (category) {
            "香精" -> "🌸"
            "酸类" -> "🍋"
            "甜味剂" -> "🍯"
            "色素" -> "🎨"
            "防腐剂" -> "🛡️"
            "增稠剂" -> "🥄"
            else -> "📦"
        }
    }

    /**
     * 获取分类颜色
     */
    private fun getCategoryColor(category: String): String {
        return when (category) {
            "香精" -> "#FF6B9D"
            "酸类" -> "#4ECDC4"
            "甜味剂" -> "#45B7D1"
            "色素" -> "#96CEB4"
            "防腐剂" -> "#FECA57"
            "增稠剂" -> "#FF9FF3"
            else -> "#DDA0DD"
        }
    }

    /**
     * 获取客户颜色
     */
    private fun getCustomerColor(customer: String): String {
        val colors = listOf(
            "#667eea", "#f093fb", "#4facfe", "#fa709a", "#a8edea",
            "#ffecd2", "#fcb69f", "#667eea", "#764ba2", "#fad0c4"
        )
        return colors[customer.hashCode().mod(colors.size)]
    }

    /**
     * 获取所有客户列表
     */
    fun getAllCustomers(): List<String> {
        return _recipes.value
            .mapNotNull { it.customer.takeIf { customer -> customer.isNotEmpty() } }
            .distinct()
            .sorted()
    }

    /**
     * 获取时间分类列表（按创建时间分组）
     */
    fun getTimeRanges(): List<String> {
        val timeRanges = mutableListOf<String>()
        val recipes = _recipes.value

        // 今天
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (recipes.any { it.createTime.startsWith(today) }) {
            timeRanges.add("今天")
        }

        // 本周
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        if (recipes.any { it.createTime >= weekAgo }) {
            timeRanges.add("本周")
        }

        // 本月
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val currentMonth = monthFormat.format(Date())
        if (recipes.any { it.createTime.startsWith(currentMonth) }) {
            timeRanges.add("本月")
        }

        // 更早
        if (recipes.any { !it.createTime.startsWith(currentMonth) }) {
            timeRanges.add("更早")
        }

        return timeRanges
    }

    /**
     * 根据时间范围筛选配方
     */
    fun getRecipesByTimeRange(timeRange: String): List<Recipe> {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        return when (timeRange) {
            "今天" -> {
                val today = dateFormat.format(Date())
                _recipes.value.filter { it.createTime.startsWith(today) }
            }
            "本周" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = dateFormat.format(calendar.time)
                _recipes.value.filter { it.createTime >= weekAgo }
            }
            "本月" -> {
                val currentMonth = monthFormat.format(Date())
                _recipes.value.filter { it.createTime.startsWith(currentMonth) }
            }
            "更早" -> {
                val currentMonth = monthFormat.format(Date())
                _recipes.value.filter { !it.createTime.startsWith(currentMonth) }
            }
            else -> _recipes.value
        }
    }

    /**
     * 获取所有分类列表
     */
    fun getAllCategories(): List<String> {
        return _recipes.value
            .map { it.category }
            .distinct()
            .sorted()
    }

    /**
     * 根据配方编码获取配方
     */
    fun getRecipeByCode(code: String): Recipe? {
        return _recipes.value.find { it.code == code }
    }

    /**
     * 加载示例数据
     */
    private fun loadSampleRecipes() {
        val sampleRecipes = listOf(
            Recipe(
                id = "1",
                code = "XJ241101001",
                name = "苹果香精配方",
                category = "香精",
                subCategory = "水果类",
                customer = "康师傅",
                batchNo = "KSF2024001",
                version = "2.1",
                description = "经典苹果香味配方，适用于饮料和糖果制作",
                materials = listOf(
                    Material("m1", "苹果香精", 50.0, "g", 1, "主香料"),
                    Material("m2", "乙基麦芽酚", 10.0, "g", 2, "增香剂"),
                    Material("m3", "柠檬酸", 5.0, "g", 3, "调酸"),
                    Material("m4", "山梨醇", 100.0, "g", 4, "甜味剂"),
                    Material("m5", "食用酒精", 35.0, "ml", 5, "溶剂")
                ),
                totalWeight = 200.0,
                createTime = "2024-11-01 10:30:00",
                updateTime = "2024-11-15 14:20:00",
                lastUsed = "2024-11-20 14:20:00",
                usageCount = 8,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.HIGH,
                tags = listOf("水果", "饮料", "糖果"),
                creator = "张工程师",
                reviewer = "李主管"
            ),
            Recipe(
                id = "2",
                code = "SL241102001",
                name = "柠檬酸配方",
                category = "酸类",
                subCategory = "有机酸",
                customer = "统一",
                batchNo = "TY2024002",
                version = "1.5",
                description = "标准柠檬酸调味配方",
                materials = listOf(
                    Material("m6", "柠檬酸", 80.0, "g", 1),
                    Material("m7", "柠檬香精", 15.0, "g", 2),
                    Material("m8", "蔗糖", 120.0, "g", 3)
                ),
                totalWeight = 215.0,
                createTime = "2024-11-02 09:15:00",
                updateTime = "2024-11-10 16:30:00",
                lastUsed = "2024-11-19 11:45:00",
                usageCount = 5,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = listOf("酸味", "调料"),
                creator = "王技师",
                reviewer = "张主管"
            ),
            Recipe(
                id = "3",
                code = "TWJ241103001",
                name = "甜蜜素配方",
                category = "甜味剂",
                subCategory = "人工甜味剂",
                customer = "娃哈哈",
                batchNo = "WHH2024001",
                version = "1.8",
                description = "低热量甜味剂配方",
                materials = listOf(
                    Material("m9", "甜蜜素", 60.0, "g", 1),
                    Material("m10", "安赛蜜", 20.0, "g", 2),
                    Material("m11", "糖精钠", 5.0, "g", 3),
                    Material("m12", "麦芽糊精", 80.0, "g", 4)
                ),
                totalWeight = 165.0,
                createTime = "2024-11-03 16:20:00",
                updateTime = "2024-11-12 10:15:00",
                lastUsed = "2024-11-18 08:30:00",
                usageCount = 3,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = listOf("甜味", "低卡"),
                creator = "李技师",
                reviewer = "王主管"
            ),
            Recipe(
                id = "4",
                code = "QT241104001",
                name = "综合调味配方",
                category = "其他",
                subCategory = "复合调料",
                customer = "海天",
                batchNo = "HT2024003",
                version = "3.2",
                description = "多功能调味配方，可用于多种食品",
                materials = listOf(
                    Material("m13", "食用盐", 30.0, "g", 1),
                    Material("m14", "味精", 25.0, "g", 2),
                    Material("m15", "I+G", 10.0, "g", 3),
                    Material("m16", "酵母提取物", 40.0, "g", 4),
                    Material("m17", "胡椒粉", 15.0, "g", 5),
                    Material("m18", "大蒜粉", 20.0, "g", 6),
                    Material("m19", "洋葱粉", 18.0, "g", 7),
                    Material("m20", "香芹籽", 12.0, "g", 8)
                ),
                totalWeight = 170.0,
                createTime = "2024-11-04 13:45:00",
                updateTime = "2024-11-08 09:20:00",
                lastUsed = "2024-11-21 16:10:00",
                usageCount = 12,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.HIGH,
                tags = listOf("咸味", "复合", "调料"),
                creator = "刘工程师",
                reviewer = "陈主管"
            ),
            Recipe(
                id = "5",
                code = "SS241105001",
                name = "胭脂红配方",
                category = "色素",
                subCategory = "红色系",
                customer = "好丽友",
                batchNo = "HLY2024001",
                version = "1.0",
                description = "食品用胭脂红色素配方",
                materials = listOf(
                    Material("m21", "胭脂红", 25.0, "g", 1),
                    Material("m22", "柠檬黄", 5.0, "g", 2),
                    Material("m23", "载体淀粉", 70.0, "g", 3)
                ),
                totalWeight = 100.0,
                createTime = "2024-11-05 11:20:00",
                updateTime = "2024-11-05 11:20:00",
                lastUsed = "2024-11-17 15:30:00",
                usageCount = 6,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = listOf("红色", "着色"),
                creator = "赵技师",
                reviewer = "孙主管"
            ),
            Recipe(
                id = "6",
                code = "XJ241106001",
                name = "草莓香精配方",
                category = "香精",
                subCategory = "水果类",
                customer = "康师傅",
                batchNo = "KSF2024002",
                version = "1.3",
                description = "天然草莓香味配方",
                materials = listOf(
                    Material("m24", "草莓香精", 45.0, "g", 1),
                    Material("m25", "香兰素", 8.0, "g", 2),
                    Material("m26", "乙基香兰素", 12.0, "g", 3),
                    Material("m27", "丙三醇", 30.0, "ml", 4)
                ),
                totalWeight = 95.0,
                createTime = "2024-11-06 14:15:00",
                updateTime = "2024-11-14 16:45:00",
                lastUsed = "2024-11-16 10:25:00",
                usageCount = 4,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = listOf("草莓", "水果", "天然"),
                creator = "钱工程师",
                reviewer = "李主管"
            ),
            Recipe(
                id = "7",
                code = "FFS241107001",
                name = "山梨酸钾配方",
                category = "防腐剂",
                subCategory = "有机防腐",
                customer = "统一",
                batchNo = "TY2024003",
                version = "2.0",
                description = "天然防腐剂配方",
                materials = listOf(
                    Material("m28", "山梨酸钾", 80.0, "g", 1),
                    Material("m29", "苯甲酸钠", 20.0, "g", 2)
                ),
                totalWeight = 100.0,
                createTime = "2024-11-07 09:30:00",
                updateTime = "2024-11-13 11:40:00",
                lastUsed = "2024-11-15 13:20:00",
                usageCount = 7,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.URGENT,
                tags = listOf("防腐", "天然"),
                creator = "周技师",
                reviewer = "张主管"
            ),
            Recipe(
                id = "8",
                code = "ZCJ241108001",
                name = "黄原胶配方",
                category = "增稠剂",
                subCategory = "天然增稠",
                customer = "娃哈哈",
                batchNo = "WHH2024002",
                version = "1.1",
                description = "天然增稠剂配方",
                materials = listOf(
                    Material("m30", "黄原胶", 60.0, "g", 1),
                    Material("m31", "瓜尔胶", 30.0, "g", 2),
                    Material("m32", "卡拉胶", 10.0, "g", 3)
                ),
                totalWeight = 100.0,
                createTime = "2024-11-08 15:45:00",
                updateTime = "2024-11-11 14:25:00",
                lastUsed = null,
                usageCount = 0,
                status = RecipeStatus.DRAFT,
                priority = RecipePriority.LOW,
                tags = listOf("增稠", "天然"),
                creator = "吴技师",
                reviewer = ""
            ),
            // 添加相同名称但不同配置量的配方演示新编码逻辑
            Recipe(
                id = "9",
                code = "XJ24110915001", // 苹果香精配方 - 150g配置
                name = "苹果香精配方",
                category = "香精",
                subCategory = "水果类",
                customer = "康师傅",
                batchNo = "KSF2024004",
                version = "2.2",
                description = "苹果香味配方 - 小批量生产配置",
                materials = listOf(
                    Material("m33", "苹果香精", 40.0, "g", 1, "主香料"),
                    Material("m34", "乙基麦芽酚", 8.0, "g", 2, "增香剂"),
                    Material("m35", "柠檬酸", 4.0, "g", 3, "调酸"),
                    Material("m36", "山梨醇", 80.0, "g", 4, "甜味剂"),
                    Material("m37", "食用酒精", 18.0, "ml", 5, "溶剂")
                ),
                totalWeight = 150.0,
                createTime = "2024-11-09 09:15:00",
                updateTime = "2024-11-09 09:15:00",
                lastUsed = "2024-11-22 08:30:00",
                usageCount = 2,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.NORMAL,
                tags = listOf("水果", "小批量"),
                creator = "张工程师",
                reviewer = "李主管"
            ),
            Recipe(
                id = "10",
                code = "XJ24110950001", // 苹果香精配方 - 500g配置
                name = "苹果香精配方",
                category = "香精",
                subCategory = "水果类",
                customer = "康师傅",
                batchNo = "KSF2024005",
                version = "2.3",
                description = "苹果香味配方 - 大批量生产配置",
                materials = listOf(
                    Material("m38", "苹果香精", 125.0, "g", 1, "主香料"),
                    Material("m39", "乙基麦芽酚", 25.0, "g", 2, "增香剂"),
                    Material("m40", "柠檬酸", 12.0, "g", 3, "调酸"),
                    Material("m41", "山梨醇", 250.0, "g", 4, "甜味剂"),
                    Material("m42", "食用酒精", 88.0, "ml", 5, "溶剂")
                ),
                totalWeight = 500.0,
                createTime = "2024-11-10 14:20:00",
                updateTime = "2024-11-10 14:20:00",
                lastUsed = "2024-11-21 16:45:00",
                usageCount = 6,
                status = RecipeStatus.ACTIVE,
                priority = RecipePriority.HIGH,
                tags = listOf("水果", "大批量"),
                creator = "张工程师",
                reviewer = "李主管"
            )
        )

        _recipes.value = sampleRecipes
    }

    companion object {
        @Volatile
        private var INSTANCE: RecipeRepository? = null

        fun getInstance(): RecipeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RecipeRepository().also { INSTANCE = it }
            }
        }
    }
}
