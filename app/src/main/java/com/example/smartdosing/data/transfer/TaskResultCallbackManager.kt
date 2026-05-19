package com.example.smartdosing.data.transfer

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.ConfigurationMaterialRecord
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.PendingTaskResultSyncEntity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 任务最终配置结果同步器
 * 负责把设备端保存的完整材料配置结果回传给后端，失败时进入本地待同步队列。
 */
class TaskResultCallbackManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TaskResultCallback"
        private const val CALLBACK_PATH = "/api/lan/devices/task-result"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var INSTANCE: TaskResultCallbackManager? = null

        fun getInstance(context: Context): TaskResultCallbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskResultCallbackManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch {
            retryPendingResults()
        }
    }

    suspend fun syncResultForTask(
        taskId: String,
        record: ConfigurationRecord,
        tolerancePercent: Double
    ): Boolean = withContext(Dispatchers.IO) {
        val database = SmartDosingDatabase.getDatabase(context)
        val deviceDao = database.deviceDao()
        val task = deviceDao.getReceivedTaskById(taskId)
        if (task == null) {
            Log.w(TAG, "未找到局域网任务，跳过结果同步: taskId=$taskId")
            return@withContext false
        }

        val payload = buildPayload(record, task.transferId, tolerancePercent)
        val payloadJson = gson.toJson(payload)
        val sent = sendPayload(task.senderUID, payloadJson)

        if (sent) {
            val now = System.currentTimeMillis()
            deviceDao.markTaskResultSynced(taskId, record.id, now)
            deviceDao.deletePendingTaskResultSync(task.transferId)
            Log.i(TAG, "任务结果同步成功: transferId=${task.transferId}")
            true
        } else {
            queuePendingSync(
                taskId = taskId,
                transferId = task.transferId,
                senderUID = task.senderUID,
                recordId = record.id,
                payloadJson = payloadJson,
                error = "发送端回调失败"
            )
            false
        }
    }

    suspend fun retryPendingResults(limit: Int = 20) = withContext(Dispatchers.IO) {
        val database = SmartDosingDatabase.getDatabase(context)
        val deviceDao = database.deviceDao()
        val pending = deviceDao.getPendingTaskResultSyncs(limit)

        pending.forEach { item ->
            val sent = sendPayload(item.senderUID, item.payloadJson)
            if (sent) {
                val now = System.currentTimeMillis()
                deviceDao.markTaskResultSynced(item.taskId, item.recordId, now)
                deviceDao.deletePendingTaskResultSync(item.transferId)
                Log.i(TAG, "待同步任务结果补传成功: transferId=${item.transferId}")
            } else {
                deviceDao.incrementPendingTaskResultSyncAttempt(
                    id = item.id,
                    lastError = "补传失败",
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun queuePendingSync(
        taskId: String,
        transferId: String,
        senderUID: String,
        recordId: String,
        payloadJson: String,
        error: String
    ) {
        val now = System.currentTimeMillis()
        val database = SmartDosingDatabase.getDatabase(context)
        database.deviceDao().upsertPendingTaskResultSync(
            PendingTaskResultSyncEntity(
                id = "TRS-$transferId",
                taskId = taskId,
                transferId = transferId,
                senderUID = senderUID,
                recordId = recordId,
                payloadJson = payloadJson,
                attempts = 0,
                lastError = error,
                createdAt = now,
                updatedAt = now
            )
        )
        Log.w(TAG, "任务结果进入待同步队列: transferId=$transferId")
    }

    private suspend fun sendPayload(senderUID: String, payloadJson: String): Boolean {
        val database = SmartDosingDatabase.getDatabase(context)
        val callbackBaseUrl = database.deviceDao().getCallbackBaseUrl(senderUID)
        if (callbackBaseUrl.isNullOrBlank()) {
            Log.w(TAG, "发送端未配置回调地址，无法同步任务结果: senderUID=$senderUID")
            return false
        }

        val url = callbackBaseUrl.trimEnd('/') + CALLBACK_PATH
        return try {
            val request = Request.Builder()
                .url(url)
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    Log.w(TAG, "任务结果回调非 2xx: code=${it.code}, url=$url")
                }
                it.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "任务结果回调异常: ${e.message}")
            false
        }
    }

    private fun buildPayload(
        record: ConfigurationRecord,
        transferId: String,
        tolerancePercent: Double
    ): TaskResultPayload {
        val deviceIdentity = DeviceUIDManager.getDeviceIdentity(context)
        val materials = record.materialDetails.map { it.toPayloadMaterial() }
        val averageDeviationPercent = if (materials.isEmpty()) {
            0.0
        } else {
            materials.sumOf { kotlin.math.abs(it.deviationPercent) } / materials.size
        }

        return TaskResultPayload(
            transferId = transferId,
            deviceUID = deviceIdentity.uid,
            status = "COMPLETED",
            operator = record.operator,
            recipeCode = record.recipeCode,
            recipeName = record.recipeName,
            completedAt = nowIsoString(),
            totalMaterials = record.materialDetails.size,
            completedMaterials = record.materialDetails.size,
            plannedTotalWeight = record.quantity,
            actualTotalWeight = record.actualQuantity,
            averageDeviationPercent = averageDeviationPercent,
            overLimitCount = record.materialDetails.count { it.isOutOfTolerance },
            tolerancePercent = tolerancePercent,
            materials = materials
        )
    }

    private fun ConfigurationMaterialRecord.toPayloadMaterial(): TaskResultMaterialPayload {
        val deviationPercent = if (targetWeight > 0) deviation / targetWeight * 100.0 else 0.0
        return TaskResultMaterialPayload(
            sequence = sequence,
            materialCode = code,
            materialName = name,
            targetWeight = targetWeight,
            actualWeight = actualWeight,
            unit = unit,
            deviation = deviation,
            deviationPercent = deviationPercent,
            isOutOfTolerance = isOutOfTolerance
        )
    }

    private fun nowIsoString(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())
}

private data class TaskResultPayload(
    val transferId: String,
    val deviceUID: String,
    val status: String,
    val operator: String,
    val recipeCode: String,
    val recipeName: String,
    val startedAt: String? = null,
    val completedAt: String,
    val totalMaterials: Int,
    val completedMaterials: Int,
    val plannedTotalWeight: Double,
    val actualTotalWeight: Double,
    val averageDeviationPercent: Double,
    val overLimitCount: Int,
    val tolerancePercent: Double,
    val materials: List<TaskResultMaterialPayload>
)

private data class TaskResultMaterialPayload(
    val sequence: Int,
    val materialCode: String,
    val materialName: String,
    val targetWeight: Double,
    val actualWeight: Double,
    val unit: String,
    val deviation: Double,
    val deviationPercent: Double,
    val isOutOfTolerance: Boolean
)
