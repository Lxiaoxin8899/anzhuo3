package com.example.smartdosing.data

import com.google.gson.annotations.SerializedName

/**
 * 配方数据模型
 */
data class Recipe(
    val id: String,
    val code: String, // 配方编码，唯一标识
    val name: String,
    val category: String, // 一级分类（香精、酸类、甜味剂等）
    val subCategory: String = "", // 二级分类（客户、时间等）
    val customer: String = "", // 客户名称
    val batchNo: String = "", // 批次号
    val version: String = "1.0", // 版本号
    val description: String = "",
    val materials: List<Material>,
    val totalWeight: Double,
    val createTime: String, // 创建时间
    val updateTime: String = "", // 更新时间
    val lastUsed: String? = null, // 最后使用时间
    val usageCount: Int = 0, // 使用次数
    val status: RecipeStatus = RecipeStatus.ACTIVE, // 配方状态
    val priority: RecipePriority = RecipePriority.NORMAL, // 优先级
    val tags: List<String> = emptyList(), // 标签
    val creator: String = "", // 创建者
    val reviewer: String = "" // 审核者
)

/**
 * 配方材料
 */
data class Material(
    val id: String,
    val name: String,
    val weight: Double, // 克
    val unit: String = "g",
    val sequence: Int = 1, // 投料顺序
    val notes: String = "",
    val code: String = "" // 材料编码 - 用于库存管理和追溯
)

/**
 * 配方状态
 */
enum class RecipeStatus {
    ACTIVE, // 启用
    INACTIVE, // 禁用
    DRAFT, // 草稿
    ARCHIVED // 归档
}

/**
 * 配方优先级
 */
enum class RecipePriority {
    LOW, // 低
    NORMAL, // 普通
    HIGH, // 高
    URGENT // 紧急
}

/**
 * 分类类型
 */
enum class CategoryType {
    MATERIAL, // 按材料分类
    CUSTOMER, // 按客户分类
    TIME, // 按时间分类
    STATUS, // 按状态分类
    PRIORITY // 按优先级分类
}

/**
 * 配方导入请求
 */
data class RecipeImportRequest(
    val code: String = "", // 配方编码
    val name: String,
    val category: String,
    val subCategory: String = "",
    val customer: String = "",
    val batchNo: String = "",
    val version: String = "1.0",
    val description: String = "",
    val materials: List<MaterialImport>,
    val status: RecipeStatus = RecipeStatus.ACTIVE,
    val priority: RecipePriority = RecipePriority.NORMAL,
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val reviewer: String = ""
)

data class MaterialImport(
    val name: String,
    val code: String = "", // 材料编码
    val weight: Double,
    val unit: String = "g",
    val sequence: Int = 1,
    val notes: String = ""
)

/**
 * API响应包装
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String = "",
    val data: T? = null
)

/**
 * 配方筛选条件
 */
data class RecipeFilter(
    val category: String = "", // 一级分类
    val subCategory: String = "", // 二级分类
    val customer: String = "", // 客户
    val status: RecipeStatus? = null, // 状态
    val priority: RecipePriority? = null, // 优先级
    val timeRange: TimeRange? = null, // 时间范围
    val searchText: String = "", // 搜索文本（支持编码、名称、材料）
    val tags: List<String> = emptyList(), // 标签过滤
    val creator: String = "", // 创建者
    val sortBy: SortType = SortType.CREATE_TIME, // 排序方式
    val sortOrder: SortOrder = SortOrder.DESC // 排序顺序
)

/**
 * 时间范围
 */
data class TimeRange(
    val startTime: String,
    val endTime: String
)

/**
 * 排序类型
 */
enum class SortType {
    CREATE_TIME, // 按创建时间
    UPDATE_TIME, // 按更新时间
    LAST_USED, // 按最后使用时间
    USAGE_COUNT, // 按使用次数
    NAME, // 按名称
    CODE, // 按编码
    CUSTOMER // 按客户
}

/**
 * 排序顺序
 */
enum class SortOrder {
    ASC, // 升序
    DESC // 降序
}

/**
 * 分类信息
 */
data class CategoryInfo(
    val type: CategoryType,
    val name: String,
    val count: Int,
    val icon: String = "",
    val color: String = "",
    val children: List<CategoryInfo> = emptyList()
)

/**
 * 配方统计信息
 */
data class RecipeStats(
    val totalRecipes: Int,
    val categoryCounts: Map<String, Int>,
    val customerCounts: Map<String, Int>,
    val statusCounts: Map<RecipeStatus, Int>,
    val priorityCounts: Map<RecipePriority, Int>,
    val recentlyUsed: List<Recipe>,
    val mostUsed: List<Recipe>,
    val recentlyCreated: List<Recipe>,
    val categoryTree: List<CategoryInfo>
)
