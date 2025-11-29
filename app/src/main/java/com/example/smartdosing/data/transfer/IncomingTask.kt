package com.example.smartdosing.data.transfer

import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.Material
import com.example.smartdosing.data.RecipeImportRequest
import com.example.smartdosing.data.MaterialImport

/**
 * 接收到的任务请求 - 发送端推送的数据格式
 */
data class IncomingTaskRequest(
    val schemaVersion: String = "1.0", // 协议版本，默认沿用 1.0
    val transferId: String,            // 传输流水号（发送端生成，用于去重）
    val senderUID: String,             // 发送端 UID
    val senderName: String,            // 发送端名称
    val senderIP: String? = null,      // 发送端 IP
    val senderAppVersion: String? = null, // 发送端应用版本
    val timestamp: Long,               // 发送时间戳
    val task: TaskPayload,             // 任务内容
    val recipe: RecipePayload? = null  // 配方数据（可选）
)

/**
 * 任务内容载荷
 */
data class TaskPayload(
    val title: String,                // 任务标题
    val recipeCode: String,           // 配方编码
    val recipeName: String,           // 配方名称
    val quantity: Double,             // 数量
    val unit: String = "kg",          // 单位
    val priority: String = "NORMAL",  // 优先级: LOW, NORMAL, HIGH, URGENT
    val deadline: String? = null,     // 截止时间
    val customer: String? = null,     // 客户
    val perfumer: String? = null,     // 调香师
    val salesOwner: String? = null,   // 销售代表
    val note: String? = null,         // 备注
    val tags: List<String> = emptyList() // 标签
)

/**
 * 配方内容载荷 - 完整配方数据
 */
data class RecipePayload(
    val code: String,                 // 配方编码
    val name: String,                 // 配方名称
    val category: String = "",        // 分类
    val subCategory: String = "",     // 子分类
    val customer: String = "",        // 客户
    val description: String = "",     // 描述
    val totalWeight: Double,          // 总重量
    val materials: List<MaterialPayload>, // 材料列表
    val tags: List<String> = emptyList()  // 标签
)

/**
 * 材料内容载荷
 */
data class MaterialPayload(
    val name: String,                 // 材料名称
    val code: String = "",            // 材料编码
    val weight: Double,               // 重量
    val unit: String = "g",           // 单位
    val sequence: Int = 1,            // 投料顺序
    val notes: String = ""            // 备注
)

/**
 * 任务接收响应 - 返回给发送端
 */
data class TaskReceiveResponse(
    val success: Boolean,              // 是否成功
    val message: String,               // 响应消息
    val transferId: String,            // 传输流水号（回传确认）
    val receivedTaskId: String? = null,// 本地生成的任务 ID
    val receiverUID: String,           // 接收端 UID
    val receiverName: String,          // 接收端名称
    val timestamp: Long = System.currentTimeMillis(), // 响应时间戳
    val schemaVersion: String? = null, // 服务端处理所依据的协议版本
    val errorCode: String? = null,     // 失败时的错误码
    val warningCodes: List<String> = emptyList() // 业务预警代码
)

/**
 * UID 配方同步命令 - 通过局域网直接推送完整配方
 */
data class RecipeSyncCommand(
    val transferId: String,           // 传输流水号，方便幂等校验
    val targetUID: String,            // 目标接收端 UID
    val senderUID: String,            // 发送端 UID
    val senderName: String,           // 发送端名称
    val senderIP: String? = null,     // 发送端 IP
    val timestamp: Long,              // 发送时间
    val overwrite: Boolean = false,   // 是否需要覆盖已有配方
    val recipe: RecipePayload         // 配方主体数据
)

/**
 * UID 配方同步响应
 */
data class RecipeSyncResult(
    val transferId: String,           // 回传流水号
    val recipeId: String,             // 本地落库后的配方 ID
    val recipeCode: String,           // 配方编号
    val operation: RecipeSyncOperation, // 执行的操作类型
    val receiverUID: String,          // 接收端 UID
    val receiverName: String,         // 接收端名称
    val timestamp: Long = System.currentTimeMillis() // 处理时间戳
)

/**
 * UID 同步操作类型
 */
enum class RecipeSyncOperation {
    CREATED,    // 新增配方
    UPDATED     // 覆盖已存在的配方
}

/**
 * 任务接收状态
 */
enum class ReceivedTaskStatus {
    PENDING,    // 待处理
    ACCEPTED,   // 已接收
    REJECTED,   // 已拒绝
    COMPLETED   // 已完成
}

/**
 * 扩展函数：将 RecipePayload 转换为本地 Recipe
 */
fun RecipePayload.toLocalRecipe(recipeId: String): Recipe {
    val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    return Recipe(
        id = recipeId,
        code = this.code,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        customer = this.customer,
        description = this.description,
        totalWeight = this.totalWeight,
        materials = this.materials.mapIndexed { index, it -> it.toLocalMaterial("${recipeId}_mat_$index") },
        tags = this.tags,
        createTime = now
    )
}

/**
 * 扩展函数：将 MaterialPayload 转换为本地 Material
 */
fun MaterialPayload.toLocalMaterial(materialId: String): Material {
    return Material(
        id = materialId,
        name = this.name,
        code = this.code,
        weight = this.weight,
        unit = this.unit,
        sequence = this.sequence,
        notes = this.notes
    )
}

/**
 * 扩展函数：将本地 Recipe 转换为 RecipePayload（用于发送）
 */
fun Recipe.toPayload(): RecipePayload {
    return RecipePayload(
        code = this.code,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        customer = this.customer,
        description = this.description,
        totalWeight = this.totalWeight,
        materials = this.materials.map { it.toPayload() },
        tags = this.tags
    )
}

/**
 * 扩展函数：将本地 Material 转换为 MaterialPayload
 */
fun Material.toPayload(): MaterialPayload {
    return MaterialPayload(
        name = this.name,
        code = this.code,
        weight = this.weight,
        unit = this.unit,
        sequence = this.sequence,
        notes = this.notes
    )
}

/**
 * 扩展函数：将网络传输的 RecipePayload 转换为标准的导入请求
 */
fun RecipePayload.toImportRequest(): RecipeImportRequest {
    return RecipeImportRequest(
        code = this.code,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        customer = this.customer,
        description = this.description,
        materials = this.materials.map { it.toImportRequest() },
        tags = this.tags
    )
}

/**
 * 扩展函数：将 MaterialPayload 转换为 MaterialImport
 */
fun MaterialPayload.toImportRequest(): MaterialImport {
    return MaterialImport(
        name = this.name,
        code = this.code,
        weight = this.weight,
        unit = this.unit,
        sequence = this.sequence,
        notes = this.notes
    )
}
