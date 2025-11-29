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

    @ColumnInfo(name = "sales_owner")
    val salesOwner: String = "",                    // 业务员

    val perfumer: String = "",                      // 调香师

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
        Index(value = ["recipe_id", "sequence"]),
        Index(value = ["code"])
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
    val notes: String = "",                         // 备注
    val code: String = ""                           // 材料编码 - 用于库存管理和追溯
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


/**
 * 鎶曟枡璁板綍瀹炰綋
 */
@Entity(
    tableName = "dosing_records",
    indices = [
        Index(value = ["recipe_id"]),
        Index(value = ["start_time"]),
        Index(value = ["operator_name"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class DosingRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String?,
    @ColumnInfo(name = "recipe_code")
    val recipeCode: String?,
    @ColumnInfo(name = "recipe_name")
    val recipeName: String,
    @ColumnInfo(name = "operator_name")
    val operatorName: String,
    @ColumnInfo(name = "checklist_items")
    val checklistItems: List<String> = emptyList(),
    @ColumnInfo(name = "start_time")
    val startTime: String,
    @ColumnInfo(name = "end_time")
    val endTime: String,
    @ColumnInfo(name = "total_materials")
    val totalMaterials: Int,
    @ColumnInfo(name = "completed_materials")
    val completedMaterials: Int,
    @ColumnInfo(name = "total_actual_weight")
    val totalActualWeight: Double,
    @ColumnInfo(name = "tolerance_percent")
    val tolerancePercent: Float,
    @ColumnInfo(name = "over_limit_count")
    val overLimitCount: Int,
    @ColumnInfo(name = "avg_deviation_percent")
    val avgDeviationPercent: Double,
    @ColumnInfo(name = "status")
    val status: String = "COMPLETED",
    @ColumnInfo(name = "created_at")
    val createdAt: String
)

/**
 * 鎶曟枡璁板綍鏄庣粏瀹炰綋
 */
@Entity(
    tableName = "dosing_record_details",
    indices = [
        Index(value = ["record_id"]),
        Index(value = ["material_code"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = DosingRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DosingRecordDetailEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Int,
    @ColumnInfo(name = "material_code")
    val materialCode: String,
    @ColumnInfo(name = "material_name")
    val materialName: String,
    @ColumnInfo(name = "target_weight")
    val targetWeight: Double,
    @ColumnInfo(name = "actual_weight")
    val actualWeight: Double,
    val unit: String,
    @ColumnInfo(name = "is_over_limit")
    val isOverLimit: Boolean,
    @ColumnInfo(name = "over_limit_percent")
    val overLimitPercent: Double
)

/**
 * 鎶曟枡璁板綍鍜屾壒寰佹暟鎹叧鑱?
 */
data class DosingRecordWithDetails(
    @Embedded
    val record: DosingRecordEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "record_id"
    )
    val details: List<DosingRecordDetailEntity>
)

/**
 * 授权发送端实体 - 记录允许向本机发送任务的设备
 */
@Entity(
    tableName = "authorized_senders",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["is_active"]),
        Index(value = ["authorized_at"])
    ]
)
data class AuthorizedSenderEntity(
    @PrimaryKey
    val uid: String,                                // 发送端 UID

    val name: String,                               // 发送端名称

    @ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,                  // 最后已知 IP

    @ColumnInfo(name = "authorized_at")
    val authorizedAt: Long,                         // 授权时间戳

    @ColumnInfo(name = "last_task_at")
    val lastTaskAt: Long? = null,                   // 最后一次发送任务时间

    @ColumnInfo(name = "task_count")
    val taskCount: Int = 0,                         // 累计发送任务数

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,                   // 是否启用

    @ColumnInfo(name = "app_version")
    val appVersion: String? = null                  // 发送端应用版本
)

/**
 * 接收任务记录实体 - 记录从其他设备接收的任务
 */
@Entity(
    tableName = "received_tasks",
    indices = [
        Index(value = ["transfer_id"], unique = true),
        Index(value = ["sender_uid"]),
        Index(value = ["received_at"]),
        Index(value = ["status"])
    ]
)
data class ReceivedTaskEntity(
    @PrimaryKey
    val id: String,                                 // 本地任务 ID

    @ColumnInfo(name = "transfer_id")
    val transferId: String,                         // 传输流水号（发送端生成）

    @ColumnInfo(name = "sender_uid")
    val senderUID: String,                          // 发送端 UID

    @ColumnInfo(name = "sender_name")
    val senderName: String,                         // 发送端名称

    @ColumnInfo(name = "sender_ip")
    val senderIP: String? = null,                   // 发送端 IP

    @ColumnInfo(name = "sender_app_version")
    val senderAppVersion: String? = null,           // 发送端应用版本

    @ColumnInfo(name = "schema_version")
    val schemaVersion: String = "1.0",              // 协议版本

    // 任务内容
    val title: String,                              // 任务标题
    @ColumnInfo(name = "recipe_code")
    val recipeCode: String,                         // 配方编码
    @ColumnInfo(name = "recipe_name")
    val recipeName: String,                         // 配方名称
    val quantity: Double,                           // 数量
    val unit: String = "kg",                        // 单位
    val priority: String = "NORMAL",                // 优先级
    val deadline: String? = null,                   // 截止时间
    val customer: String? = null,                   // 客户
    val note: String? = null,                       // 备注

    // 关联配方（如果同时传输了配方）
    @ColumnInfo(name = "local_recipe_id")
    val localRecipeId: String? = null,              // 本地保存的配方 ID

    // 时间和状态
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,                           // 接收时间戳

    @ColumnInfo(name = "accepted_at")
    val acceptedAt: Long? = null,                   // 确认接收时间

    val status: String = "PENDING"                  // 状态：PENDING, ACCEPTED, REJECTED, COMPLETED
)
