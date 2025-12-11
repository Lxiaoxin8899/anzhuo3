package com.example.smartdosing.web.transfer

import com.example.smartdosing.data.transfer.IncomingTaskRequest
import com.example.smartdosing.data.transfer.MaterialPayload
import com.example.smartdosing.data.transfer.RecipePayload
import com.example.smartdosing.data.transfer.TaskPayload
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * 局域网任务 JSON 适配器，将发送端提案格式转换为内部的 IncomingTaskRequest
 */
class LanTransferProposalAdapter(
    private val gson: Gson,
    private val supportedVersions: Set<String> = setOf("1.0")
) {

    /**
     * 如果请求体符合 schemaVersion 格式，则转换为内部结构；否则返回 null 交给旧结构处理
     */
    fun tryConvert(rawBody: String): IncomingTaskRequest? {
        val jsonObject = runCatching { JsonParser.parseString(rawBody).asJsonObject }.getOrNull()
            ?: return null

        if (!jsonObject.has("schemaVersion") || !jsonObject.has("sender")) {
            return null
        }

        val schemaVersion = jsonObject.get("schemaVersion")?.asString ?: throw IllegalArgumentException("schemaVersion 字段不能为空")
        if (!supportedVersions.contains(schemaVersion)) {
            throw UnsupportedSchemaException(schemaVersion)
        }

        val envelope = gson.fromJson(jsonObject, LanTransferEnvelope::class.java)
        envelope.validate()
        return envelope.toIncomingTaskRequest()
    }

    companion object {
        /**
         * 辅助函数：提取 transferId，方便在解析失败时带回调用方
         */
        fun extractTransferId(rawBody: String): String? {
            return runCatching {
                val obj = JsonParser.parseString(rawBody).asJsonObject
                when {
                    obj.has("transferId") -> obj.get("transferId").asString
                    obj.has("transfer_id") -> obj.get("transfer_id").asString
                    else -> null
                }
            }.getOrNull()
        }
    }
}

/**
 * 发送端 schemaVersion 不受支持时抛出的异常
 */
class UnsupportedSchemaException(val version: String) : IllegalArgumentException("不支持的协议版本: $version")

private data class LanTransferEnvelope(
    val schemaVersion: String,
    val transferId: String,
    val timestamp: Long,
    val sender: LanSenderPayload,
    val task: LanTaskPayload,
    val recipe: LanRecipePayload?
) {
    fun validate() {
        require(transferId.isNotBlank()) { "transferId 不能为空" }
        require(timestamp > 0) { "timestamp 非法" }
        requireNotNull(recipe) { "recipe 字段不能为空" }
        require(task.quantity != null && task.quantity > 0) { "任务数量必须大于 0" }
        require(!recipe.materials.isNullOrEmpty()) { "材料列表不能为空" }
    }

    fun toIncomingTaskRequest(): IncomingTaskRequest {
        val recipePayload = recipe!!.toRecipePayload()
        val taskPayload = task.toTaskPayload(recipePayload)
        return IncomingTaskRequest(
            schemaVersion = schemaVersion,
            transferId = transferId,
            senderUID = sender.uid,
            senderName = sender.name,
            senderIP = sender.ip,
            senderAppVersion = sender.appVersion,
            timestamp = timestamp,
            task = taskPayload,
            recipe = recipePayload
        )
    }
}

private data class LanSenderPayload(
    val uid: String,
    val name: String,
    val ip: String? = null,
    val appVersion: String? = null
)

private data class LanTaskPayload(
    val title: String,
    val quantity: Double?,
    val unit: String? = null,
    val priority: String? = null,
    val deadline: String? = null,
    val customer: String? = null
) {
    fun toTaskPayload(recipe: RecipePayload): TaskPayload {
        val normalizedPriority = priority?.uppercase(Locale.getDefault()) ?: "NORMAL"
        require(listOf("LOW", "NORMAL", "HIGH", "URGENT").contains(normalizedPriority)) { "priority 取值非法: $priority" }
        val resolvedUnit = unit?.takeIf { it.isNotBlank() } ?: "g"
        return TaskPayload(
            title = title,
            recipeCode = recipe.code,
            recipeName = recipe.name,
            quantity = quantity ?: 0.0,
            unit = resolvedUnit,
            priority = normalizedPriority,
            deadline = deadline,
            customer = customer,
            perfumer = null,
            note = null,
            tags = emptyList()
        )
    }
}

private data class LanRecipePayload(
    val id: String? = null,
    val code: String? = null,
    val name: String,
    val category: String? = null,
    val subCategory: String? = null,
    val customer: String? = null,
    val perfumer: String? = null,
    val description: String? = null,
    val totalWeight: Double,
    val materials: List<LanMaterialPayload>?
) {
    fun toRecipePayload(): RecipePayload {
        val resolvedCode = (code ?: id).orEmpty()
        require(resolvedCode.isNotBlank()) { "配方必须包含 code 或 id" }
        val materialPayloads = materials.orEmpty().mapIndexed { index, material ->
            material.toMaterialPayload(index)
        }
        return RecipePayload(
            code = resolvedCode,
            name = name,
            category = category.orEmpty(),
            subCategory = subCategory.orEmpty(),
            customer = customer.orEmpty(),
            description = description.orEmpty(),
            totalWeight = totalWeight,
            materials = materialPayloads,
            tags = emptyList()
        )
    }
}

private data class LanMaterialPayload(
    val id: String? = null,
    val code: String? = null,
    val name: String,
    val weight: Double,
    val unit: String? = null,
    val sequence: Int? = null
) {
    fun toMaterialPayload(index: Int): MaterialPayload {
        val resolvedCode = code.orEmpty()
        android.util.Log.d("LanTransfer", "Material [$name] code映射: 原始='$code' -> 解析='$resolvedCode'")
        require(weight > 0) { "材料 $name 的 weight 必须大于 0" }
        val resolvedUnit = unit?.takeIf { it.isNotBlank() } ?: "g"
        return MaterialPayload(
            name = name,
            code = resolvedCode,
            weight = weight,
            unit = resolvedUnit,
            sequence = sequence ?: (index + 1),
            notes = ""
        )
    }
}
