package com.example.smartdosing.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.*
import com.example.smartdosing.database.dao.*
import com.example.smartdosing.database.DataMapper
import com.example.smartdosing.database.DataMapper.toDomainModel
import com.example.smartdosing.database.DataMapper.toEntity
import com.example.smartdosing.database.DataMapper.toDomainModels

/**
 * 基于数据库的配方数据仓库
 * 替换内存存储，使用SQLite持久化存储
 */
class DatabaseRecipeRepository(private val context: Context) {

    private val database = SmartDosingDatabase.getDatabase(context)
    private val recipeDao = database.recipeDao()
    private val materialDao = database.materialDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // =================================
    // Flow数据订阅
    // =================================

    /**
     * 获取所有配方的Flow订阅
     */
    val recipes: Flow<List<Recipe>> = recipeDao.getAllRecipesWithMaterialsFlow()
        .map { recipeWithMaterials ->
            recipeWithMaterials.toDomainModels()
        }

    // =================================
    // 基础查询操作
    // =================================

    /**
     * 获取所有配方
     */
    suspend fun getAllRecipes(): List<Recipe> {
        return recipeDao.getRecipesWithMaterials().toDomainModels()
    }

    /**
     * 根据ID获取配方
     */
    suspend fun getRecipeById(id: String): Recipe? {
        return recipeDao.getRecipeWithMaterials(id)?.toDomainModel()
    }

    /**
     * 根据配方编码获取配方
     */
    suspend fun getRecipeByCode(code: String): Recipe? {
        val recipe = recipeDao.getRecipeByCode(code) ?: return null
        return recipeDao.getRecipeWithMaterials(recipe.id)?.toDomainModel()
    }

    /**
     * 根据分类获取配方
     */
    suspend fun getRecipesByCategory(category: String): List<Recipe> {
        return if (category == "全部") {
            getAllRecipes()
        } else {
            // 基础实现：获取所有配方然后过滤
            getAllRecipes().filter { it.category == category }
        }
    }

    /**
     * 根据客户获取配方
     */
    suspend fun getRecipesByCustomer(customer: String): List<Recipe> {
        // 基础实现：获取所有配方然后过滤
        return getAllRecipes().filter { it.customer == customer }
    }

    /**
     * 搜索配方
     */
    suspend fun searchRecipes(query: String): List<Recipe> {
        if (query.isBlank()) return getAllRecipes()

        val searchedRecipes = recipeDao.searchRecipes("%$query%")
        return searchedRecipes.map { recipe ->
            recipeDao.getRecipeWithMaterials(recipe.id)?.toDomainModel()
        }.filterNotNull()
    }

    // =================================
    // 复杂查询和筛选
    // =================================

    /**
     * 根据筛选条件获取配方
     */
    suspend fun getFilteredRecipes(filter: RecipeFilter): List<Recipe> {
        // 获取基础数据
        var recipes = getAllRecipes()

        // 应用筛选条件
        if (filter.category.isNotEmpty()) {
            recipes = recipes.filter { it.category == filter.category }
        }

        if (filter.subCategory.isNotEmpty()) {
            recipes = recipes.filter { it.subCategory == filter.subCategory }
        }

        if (filter.customer.isNotEmpty()) {
            recipes = recipes.filter { it.customer == filter.customer }
        }

        filter.status?.let { status ->
            recipes = recipes.filter { it.status == status }
        }

        filter.priority?.let { priority ->
            recipes = recipes.filter { it.priority == priority }
        }

        filter.timeRange?.let { timeRange ->
            recipes = recipes.filter { recipe ->
                recipe.createTime >= timeRange.startTime && recipe.createTime <= timeRange.endTime
            }
        }

        if (filter.searchText.isNotEmpty()) {
            recipes = recipes.filter { recipe ->
                recipe.code.contains(filter.searchText, ignoreCase = true) ||
                recipe.name.contains(filter.searchText, ignoreCase = true) ||
                recipe.description.contains(filter.searchText, ignoreCase = true) ||
                recipe.customer.contains(filter.searchText, ignoreCase = true) ||
                recipe.materials.any { it.name.contains(filter.searchText, ignoreCase = true) }
            }
        }

        if (filter.tags.isNotEmpty()) {
            recipes = recipes.filter { recipe ->
                filter.tags.all { tag -> recipe.tags.contains(tag) }
            }
        }

        if (filter.creator.isNotEmpty()) {
            recipes = recipes.filter { it.creator == filter.creator }
        }

        // 排序
        recipes = when (filter.sortBy) {
            SortType.CREATE_TIME -> recipes.sortedBy { it.createTime }
            SortType.UPDATE_TIME -> recipes.sortedBy { it.updateTime }
            SortType.LAST_USED -> recipes.sortedBy { it.lastUsed ?: "" }
            SortType.USAGE_COUNT -> recipes.sortedBy { it.usageCount }
            SortType.NAME -> recipes.sortedBy { it.name }
            SortType.CODE -> recipes.sortedBy { it.code }
            SortType.CUSTOMER -> recipes.sortedBy { it.customer }
        }

        // 排序顺序
        if (filter.sortOrder == SortOrder.DESC) {
            recipes = recipes.reversed()
        }

        return recipes
    }

    // =================================
    // 增删改操作
    // =================================

    /**
     * 添加新配方
     */
    suspend fun addRecipe(request: RecipeImportRequest): Recipe {
        val currentTime = dateFormat.format(Date())

        // 检查配方编码是否重复
        if (request.code.isNotEmpty() && recipeDao.getRecipeByCode(request.code) != null) {
            throw IllegalArgumentException("配方编码 ${request.code} 已存在，请使用其他编码")
        }

        // 使用DataMapper转换为Domain Model
        val recipe = DataMapper.run {
            request.toDomainModel(
                code = if (request.code.isNotEmpty()) request.code else generateUniqueCode(request.category, request.materials.sumOf { it.weight }),
                currentTime = currentTime
            )
        }

        // 转换为数据库实体并插入
        val recipeEntity = recipe.toEntity()
        val materialEntities = recipe.materials.map { it.toEntity(recipe.id) }

        recipeDao.insertRecipeWithMaterials(recipeEntity, materialEntities, recipe.tags)

        return recipe
    }

    /**
     * 更新配方
     */
    suspend fun updateRecipe(id: String, request: RecipeImportRequest): Recipe? {
        val existingRecipe = getRecipeById(id) ?: return null
        val currentTime = dateFormat.format(Date())

        // 检查配方编码是否重复（排除当前配方）
        if (request.code.isNotEmpty() && request.code != existingRecipe.code) {
            val existingByCode = recipeDao.getRecipeByCode(request.code)
            if (existingByCode != null && existingByCode.id != id) {
                throw IllegalArgumentException("配方编码 ${request.code} 已存在，请使用其他编码")
            }
        }

        // 构建更新后的配方，带上导入模板中的材料编码
        val materials = request.materials.mapIndexed { index, materialImport ->
            Material(
                id = "material_${UUID.randomUUID()}_$index",
                name = materialImport.name,
                weight = materialImport.weight,
                unit = materialImport.unit,
                sequence = materialImport.sequence,
                notes = materialImport.notes,
                code = materialImport.code
            )
        }

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
            totalWeight = materials.sumOf { it.weight },
            updateTime = currentTime,
            status = request.status,
            priority = request.priority,
            tags = request.tags,
            reviewer = request.reviewer
        )

        // 事务更新配方、材料和标签
        val recipeEntity = updatedRecipe.toEntity()
        val materialEntities = updatedRecipe.materials.map { it.toEntity(updatedRecipe.id) }
        recipeDao.updateRecipeWithMaterials(recipeEntity, materialEntities, updatedRecipe.tags)

        return updatedRecipe
    }

    /**
     * 删除配方
     */
    suspend fun deleteRecipe(id: String): Boolean {
        val recipe = recipeDao.getRecipeById(id) ?: return false
        recipeDao.deleteRecipeById(id)
        return true
    }

    /**
     * 标记配方被使用
     */
    suspend fun markRecipeUsed(id: String): Recipe? {
        val currentTime = dateFormat.format(Date())
        recipeDao.markRecipeUsed(id, currentTime)
        return getRecipeById(id)
    }

    // =================================
    // 统计和分析功能
    // =================================

    /**
     * 获取配方统计信息
     */
    suspend fun getRecipeStats(): RecipeStats {
        val totalRecipes = recipeDao.getRecipeCount()
        val allRecipes = getAllRecipes()

        // 使用DAO的统计查询
        val categoryCounts = recipeDao.getCategoryStats().associate { it.category to it.count }
        val customerCounts = recipeDao.getCustomerStats().associate { it.customer to it.count }

        // 状态统计 - 通过内存过滤计算
        val statusCounts = allRecipes.groupBy { it.status }.mapValues { it.value.size }

        // 优先级统计 - 通过内存过滤计算
        val priorityCounts = allRecipes.groupBy { it.priority }.mapValues { it.value.size }

        // 最近使用的配方
        val recentlyUsedEntities = recipeDao.getRecentlyUsedRecipes(10)
        val recentlyUsed = recentlyUsedEntities.map { entity ->
            recipeDao.getRecipeWithMaterials(entity.id)?.toDomainModel()
        }.filterNotNull()

        // 最常用的配方
        val mostUsedEntities = recipeDao.getMostUsedRecipes(10)
        val mostUsed = mostUsedEntities.map { entity ->
            recipeDao.getRecipeWithMaterials(entity.id)?.toDomainModel()
        }.filterNotNull()

        // 最近创建的配方
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val recentlyCreatedEntities = recipeDao.getRecentlyCreatedRecipes(currentDate, 10)
        val recentlyCreated = recentlyCreatedEntities.map { entity ->
            recipeDao.getRecipeWithMaterials(entity.id)?.toDomainModel()
        }.filterNotNull()

        // 构建分类树
        val categoryTree = buildCategoryTree(allRecipes)

        return RecipeStats(
            totalRecipes = totalRecipes,
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

    // =================================
    // 辅助功能
    // =================================

    /**
     * 获取所有客户列表
     */
    suspend fun getAllCustomers(): List<String> {
        return recipeDao.getAllCustomers()
    }

    /**
     * 获取所有分类列表
     */
    suspend fun getAllCategories(): List<String> {
        return recipeDao.getAllCategories()
    }

    /**
     * 根据时间范围筛选配方
     */
    suspend fun getRecipesByTimeRange(timeRange: String): List<Recipe> {
        // 基础实现：获取所有配方然后过滤
        val allRecipes = getAllRecipes()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        return when (timeRange) {
            "今天" -> {
                val today = dateFormat.format(Date())
                allRecipes.filter { it.createTime.startsWith(today) }
            }
            "本周" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = dateFormat.format(calendar.time)
                allRecipes.filter { it.createTime >= weekAgo }
            }
            "本月" -> {
                val currentMonth = monthFormat.format(Date())
                allRecipes.filter { it.createTime.startsWith(currentMonth) }
            }
            "更早" -> {
                val currentMonth = monthFormat.format(Date())
                allRecipes.filter { !it.createTime.startsWith(currentMonth) }
            }
            else -> allRecipes
        }
    }

    /**
     * 获取时间分类列表（按创建时间分组）
     */
    suspend fun getTimeRanges(): List<String> {
        val timeRanges = mutableListOf<String>()
        val allRecipes = getAllRecipes()

        // 今天
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (allRecipes.any { it.createTime.startsWith(today) }) {
            timeRanges.add("今天")
        }

        // 本周
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        if (allRecipes.any { it.createTime >= weekAgo }) {
            timeRanges.add("本周")
        }

        // 本月
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val currentMonth = monthFormat.format(Date())
        if (allRecipes.any { it.createTime.startsWith(currentMonth) }) {
            timeRanges.add("本月")
        }

        // 更早
        if (allRecipes.any { !it.createTime.startsWith(currentMonth) }) {
            timeRanges.add("更早")
        }

        return timeRanges
    }

    // =================================
    // 私有辅助方法
    // =================================

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
     * 生成唯一配方编码
     */
    private suspend fun generateUniqueCode(category: String, totalWeight: Double = 0.0): String {
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

        val weightCode = if (totalWeight > 0) {
            "${totalWeight.toInt().toString().padStart(3, '0')}"
        } else {
            "000"
        }

        var counter = 1
        var code: String

        do {
            code = "${prefix}${dateStr}${weightCode}${counter.toString().padStart(2, '0')}"
            counter++
        } while (recipeDao.getRecipeByCode(code) != null)

        return code
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

    companion object {
        @Volatile
        private var INSTANCE: DatabaseRecipeRepository? = null

        fun getInstance(context: Context): DatabaseRecipeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseRecipeRepository(context).also { INSTANCE = it }
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}
