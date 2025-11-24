package com.example.smartdosing.database.entities

import androidx.room.*

/**
 * 配方实体类 - Room数据库映射
 * 对应 data/Recipe.kt 中的业务模型
 */
@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["category"]),
        Index(value = ["customer"]),
        Index(value = ["status"]),
        Index(value = ["last_used"]),
        Index(value = ["create_time"])
    ]
)
data class RecipeEntity(
    @PrimaryKey
    val id: String,

    // 基本信息
    val code: String,                               // 配方编码（业务唯一标识）
    val name: String,                               // 配方名称
    val category: String,                           // 一级分类（香精、酸类等）

    @ColumnInfo(name = "sub_category")
    val subCategory: String = "",                   // 二级分类

    val customer: String = "",                      // 客户名称

    @ColumnInfo(name = "batch_no")
    val batchNo: String = "",                       // 批次号

    val version: String = "1.0",                    // 版本号
    val description: String = "",                   // 配方描述

    // 重量信息
    @ColumnInfo(name = "total_weight")
    val totalWeight: Double,                        // 总重量

    // 时间信息
    @ColumnInfo(name = "create_time")
    val createTime: String,                         // 创建时间 (ISO 8601格式)

    @ColumnInfo(name = "update_time")
    val updateTime: String,                         // 更新时间

    @ColumnInfo(name = "last_used")
    val lastUsed: String? = null,                   // 最后使用时间

    @ColumnInfo(name = "usage_count")
    val usageCount: Int = 0,                        // 使用次数

    // 状态信息
    val status: String = "ACTIVE",                  // 状态：ACTIVE/INACTIVE/DRAFT/ARCHIVED
    val priority: String = "NORMAL",                // 优先级：LOW/NORMAL/HIGH/URGENT

    // 人员信息
    val creator: String = "",                       // 创建者
    val reviewer: String = ""                       // 审核者
)

/**
 * 材料实体类
 */
@Entity(
    tableName = "materials",
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["recipe_id", "sequence"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MaterialEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "recipe_id")
    val recipeId: String,                           // 关联配方ID

    val name: String,                               // 材料名称
    val weight: Double,                             // 重量
    val unit: String = "g",                         // 单位
    val sequence: Int = 1,                          // 投料顺序
    val notes: String = ""                          // 备注
)

/**
 * 配方标签关系实体类
 */
@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RecipeTagEntity(
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,                           // 配方ID

    val tag: String                                 // 标签名称
)

/**
 * 模板实体类
 */
@Entity(
    tableName = "templates",
    indices = [Index(value = ["name"])]
)
data class TemplateEntity(
    @PrimaryKey
    val id: String,

    val name: String,                               // 模板名称
    val description: String = "",                   // 模板描述
    val version: Int = 1,                          // 版本号

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,                         // 更新时间

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,                // 是否为默认模板

    @ColumnInfo(name = "created_by")
    val createdBy: String = "SYSTEM"               // 创建者（SYSTEM/USER）
)

/**
 * 模板字段实体类
 */
@Entity(
    tableName = "template_fields",
    indices = [
        Index(value = ["template_id"]),
        Index(value = ["template_id", "field_order"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemplateFieldEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "template_id")
    val templateId: String,                         // 关联模板ID

    @ColumnInfo(name = "field_key")
    val fieldKey: String,                           // 字段键名

    val label: String,                              // 显示标签
    val description: String = "",                   // 字段描述
    val required: Boolean = true,                   // 是否必填
    val example: String = "",                       // 示例值

    @ColumnInfo(name = "field_order")
    val fieldOrder: Int = 0                        // 字段顺序
)

/**
 * 导入日志实体类
 */
@Entity(
    tableName = "import_logs",
    indices = [
        Index(value = ["import_time"]),
        Index(value = ["file_type"])
    ]
)
data class ImportLogEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,                           // 原始文件名

    @ColumnInfo(name = "file_size")
    val fileSize: Long,                            // 文件大小(字节)

    @ColumnInfo(name = "file_type")
    val fileType: String,                          // 文件类型(CSV/EXCEL)

    @ColumnInfo(name = "success_count")
    val successCount: Int = 0,                     // 成功导入数量

    @ColumnInfo(name = "failed_count")
    val failedCount: Int = 0,                      // 失败数量

    @ColumnInfo(name = "error_details")
    val errorDetails: String? = null,              // 错误详情(JSON格式)

    @ColumnInfo(name = "import_time")
    val importTime: String,                        // 导入时间

    @ColumnInfo(name = "import_duration")
    val importDuration: Long = 0,                  // 导入耗时(毫秒)

    @ColumnInfo(name = "imported_by")
    val importedBy: String = "WEB"                 // 导入来源(WEB/APP)
)

/**
 * 关系映射类 - 配方及其关联数据
 */
data class RecipeWithMaterials(
    @Embedded
    val recipe: RecipeEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "recipe_id"
    )
    val materials: List<MaterialEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "recipe_id",
        entity = RecipeTagEntity::class,
        projection = ["tag"]
    )
    val tags: List<String>
)