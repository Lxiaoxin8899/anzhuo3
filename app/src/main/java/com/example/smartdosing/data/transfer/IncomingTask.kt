package com.example.smartdosing.data.transfer

import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.Material
import com.example.smartdosing.data.RecipeImportRequest
import com.example.smartdosing.data.MaterialImport
import com.google.gson.annotations.SerializedName

/**
 * 接收到的任务请求 - 发送端推送的数据格式
 */
data class IncomingTaskRequest(
    @SerializedName("schemaVersion") val schemaVersion: String = "1.0", // 协议版本，默认沿用 1.0
    @SerializedName("transferId") val transferId: String,            // 传输流水号（发送端生成，用于去重）
    @SerializedName("senderUID") val senderUID: String,             // 发送端 UID
    @SerializedName("senderName") val senderName: String,            // 发送端名称
    @SerializedName("senderIP") val senderIP: String? = null,      // 发送端 IP
    @SerializedName("senderAppVersion") val senderAppVersion: String? = null, // 发送端应用版本
    @SerializedName("timestamp") val timestamp: Long,               // 发送时间戳
    @SerializedName("task") val task: TaskPayload,             // 任务内容
    @SerializedName("recipe") val recipe: RecipePayload? = null  // 配方数据（可选）
)

/**
 * 任务内容载荷
 */
data class TaskPayload(
    @SerializedName("title") val title: String,                // 任务标题
    @SerializedName("recipeCode") val recipeCode: String,           // 配方编码
    @SerializedName("recipeName") val recipeName: String,           // 配方名称
    @SerializedName("quantity") val quantity: Double,             // 数量
    @SerializedName("unit") val unit: String = "g",           // 单位
    @SerializedName("priority") val priority: String = "NORMAL",  // 优先级: LOW, NORMAL, HIGH, URGENT
    @SerializedName("deadline") val deadline: String? = null,     // 截止时间
    @SerializedName("customer") val customer: String? = null,     // 客户
    @SerializedName("perfumer") val perfumer: String? = null,     // 调香师
    @SerializedName("salesOwner") val salesOwner: String? = null,   // 销售代表
    @SerializedName("note") val note: String? = null,         // 备注
    @SerializedName("tags") val tags: List<String> = emptyList() // 标签
)

/**
 * 配方内容载荷 - 完整配方数据
 */
data class RecipePayload(
    @SerializedName("code") val code: String,                 // 配方编码
    @SerializedName("name") val name: String,                 // 配方名称
    @SerializedName("category") val category: String = "",        // 分类
    @SerializedName("subCategory") val subCategory: String = "",     // 子分类
    @SerializedName("customer") val customer: String = "",        // 客户
    @SerializedName("description") val description: String = "",     // 描述
    @SerializedName("totalWeight") val totalWeight: Double,          // 总重量
    @SerializedName("materials") val materials: List<MaterialPayload>, // 材料列表
    @SerializedName("tags") val tags: List<String> = emptyList()  // 标签
)

/**
 * 材料内容载荷
 */
data class MaterialPayload(
    @SerializedName("name") val name: String,                 // 材料名称
    @SerializedName("code") val code: String = "",            // 材料编码
    @SerializedName("weight") val weight: Double,               // 重量
    @SerializedName("unit") val unit: String = "g",           // 单位
    @SerializedName("sequence") val sequence: Int = 1,            // 投料顺序
    @SerializedName("notes") val notes: String = ""            // 备注
)

/**
 * 任务接收响应 - 返回给发送端
 */
data class TaskReceiveResponse(
    @SerializedName("success") val success: Boolean,              // 是否成功
    @SerializedName("message") val message: String,               // 响应消息
    @SerializedName("transferId") val transferId: String,            // 传输流水号（回传确认）
    @SerializedName("receivedTaskId") val receivedTaskId: String? = null,// 本地生成的任务 ID
    @SerializedName("receiverUID") val receiverUID: String,           // 接收端 UID
    @SerializedName("receiverName") val receiverName: String,          // 接收端名称
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(), // 响应时间戳
    @SerializedName("schemaVersion") val schemaVersion: String? = null, // 服务端处理所依据的协议版本
    @SerializedName("errorCode") val errorCode: String? = null,     // 失败时的错误码
    @SerializedName("warningCodes") val warningCodes: List<String> = emptyList() // 业务预警代码
)

/**
 * UID 配方同步命令 - 通过局域网直接推送完整配方
 */
data class RecipeSyncCommand(
    @SerializedName("transferId") val transferId: String,           // 传输流水号，方便幂等校验
    @SerializedName("targetUID") val targetUID: String,            // 目标接收端 UID
    @SerializedName("senderUID") val senderUID: String,            // 发送端 UID
    @SerializedName("senderName") val senderName: String,           // 发送端名称
    @SerializedName("senderIP") val senderIP: String? = null,     // 发送端 IP
    @SerializedName("timestamp") val timestamp: Long,              // 发送时间
    @SerializedName("overwrite") val overwrite: Boolean = false,   // 是否需要覆盖已有配方
    @SerializedName("recipe") val recipe: RecipePayload         // 配方主体数据
)

/**
 * UID 配方同步响应
 */
data class RecipeSyncResult(
    @SerializedName("transferId") val transferId: String,           // 回传流水号
    @SerializedName("recipeId") val recipeId: String,             // 本地落库后的配方 ID
    @SerializedName("recipeCode") val recipeCode: String,           // 配方编号
    @SerializedName("operation") val operation: RecipeSyncOperation, // 执行的操作类型
    @SerializedName("receiverUID") val receiverUID: String,          // 接收端 UID
    @SerializedName("receiverName") val receiverName: String,         // 接收端名称
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis() // 处理时间戳
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
    android.util.Log.d("MaterialPayload", "toImportRequest: name=$name, code='$code'")
    return MaterialImport(
        name = this.name,
        code = this.code,
        weight = this.weight,
        unit = this.unit,
        sequence = this.sequence,
        notes = this.notes
    )
}

