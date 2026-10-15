package com.example.smartdosing.data

import com.google.gson.annotations.SerializedName

/**
 * 配方数据模型
 */
data class Recipe(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String, // 配方编码，唯一标识
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String, // 一级分类（香精、酸类、甜味剂等）
    @SerializedName("subCategory") val subCategory: String = "", // 二级分类（客户、时间等）
    @SerializedName("customer") val customer: String = "", // 客户名称
    @SerializedName("salesOwner") val salesOwner: String = "", // 业务员
    @SerializedName("perfumer") val perfumer: String = "", // 调香师
    @SerializedName("batchNo") val batchNo: String = "", // 批次号
    @SerializedName("version") val version: String = "1.0", // 版本号
    @SerializedName("description") val description: String = "",
    @SerializedName("materials") val materials: List<Material>,
    @SerializedName("totalWeight") val totalWeight: Double,
    @SerializedName("createTime") val createTime: String, // 创建时间
    @SerializedName("updateTime") val updateTime: String = "", // 更新时间
    @SerializedName("lastUsed") val lastUsed: String? = null, // 最后使用时间
    @SerializedName("usageCount") val usageCount: Int = 0, // 使用次数
    @SerializedName("status") val status: RecipeStatus = RecipeStatus.ACTIVE, // 配方状态
    @SerializedName("priority") val priority: RecipePriority = RecipePriority.NORMAL, // 优先级
    @SerializedName("tags") val tags: List<String> = emptyList(), // 标签
    @SerializedName("creator") val creator: String = "", // 创建者
    @SerializedName("reviewer") val reviewer: String = "" // 审核者
)

/**
 * 配方材料
 */
data class Material(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("weight") val weight: Double, // 克
    @SerializedName("unit") val unit: String = "g",
    @SerializedName("sequence") val sequence: Int = 1, // 投料顺序
    @SerializedName("notes") val notes: String = "",
    @SerializedName("code") val code: String = "" // 材料编码 - 用于库存管理和追溯
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
    @SerializedName("code") val code: String = "", // 配方编码
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("subCategory") val subCategory: String = "",
    @SerializedName("customer") val customer: String = "",
    @SerializedName("salesOwner") val salesOwner: String = "",
    @SerializedName("perfumer") val perfumer: String = "",
    @SerializedName("batchNo") val batchNo: String = "",
    @SerializedName("version") val version: String = "1.0",
    @SerializedName("description") val description: String = "",
    @SerializedName("materials") val materials: List<MaterialImport>,
    @SerializedName("status") val status: RecipeStatus = RecipeStatus.ACTIVE,
    @SerializedName("priority") val priority: RecipePriority = RecipePriority.NORMAL,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("creator") val creator: String = "",
    @SerializedName("reviewer") val reviewer: String = ""
)

data class MaterialImport(
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String = "", // 材料编码
    @SerializedName("weight") val weight: Double,
    @SerializedName("unit") val unit: String = "g",
    @SerializedName("sequence") val sequence: Int = 1,
    @SerializedName("notes") val notes: String = ""
)

/**
 * API响应包装
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String = "",
    @SerializedName("data") val data: T? = null
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
    @SerializedName("type") val type: CategoryType,
    @SerializedName("name") val name: String,
    @SerializedName("count") val count: Int,
    @SerializedName("icon") val icon: String = "",
    @SerializedName("color") val color: String = "",
    @SerializedName("children") val children: List<CategoryInfo> = emptyList()
)

/**
 * 配方统计信息
 */
data class RecipeStats(
    @SerializedName("totalRecipes") val totalRecipes: Int,
    @SerializedName("categoryCounts") val categoryCounts: Map<String, Int>,
    @SerializedName("customerCounts") val customerCounts: Map<String, Int>,
    @SerializedName("statusCounts") val statusCounts: Map<RecipeStatus, Int>,
    @SerializedName("priorityCounts") val priorityCounts: Map<RecipePriority, Int>,
    @SerializedName("recentlyUsed") val recentlyUsed: List<Recipe>,
    @SerializedName("mostUsed") val mostUsed: List<Recipe>,
    @SerializedName("recentlyCreated") val recentlyCreated: List<Recipe>,
    @SerializedName("categoryTree") val categoryTree: List<CategoryInfo>
)
