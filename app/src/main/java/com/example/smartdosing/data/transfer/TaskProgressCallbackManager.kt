package com.example.smartdosing.data.transfer

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 任务进度回调管理器
 * 负责在任务状态变更时主动推送通知给发送端
 */
class TaskProgressCallbackManager(private val context: Context) {

    companion object {
        private const val TAG = "TaskProgressCallback"
        private const val CALLBACK_PATH = "/api/lan/devices/task-progress"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // 重试策略
        private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)

        @Volatile
        private var INSTANCE: TaskProgressCallbackManager? = null

        fun getInstance(context: Context): TaskProgressCallbackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskProgressCallbackManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 发送状态变更回调（异步，不阻塞调用方）
     */
    fun sendCallback(
        senderUID: String,
        transferId: String,
        status: String,
        progress: Int = 0,
        currentStep: Int = 0,
        totalSteps: Int = 0,
        currentMaterial: String? = null,
        message: String? = null
    ) {
        scope.launch {
            try {
                val database = SmartDosingDatabase.getDatabase(context)
                val deviceDao = database.deviceDao()

                // 获取发送端的回调地址
                val callbackBaseUrl = deviceDao.getCallbackBaseUrl(senderUID)
                if (callbackBaseUrl.isNullOrBlank()) {
                    Log.d(TAG, "发送端 $senderUID 未配置回调地址，跳过回调")
                    return@launch
                }

                val deviceIdentity = DeviceUIDManager.getDeviceIdentity(context)

                val payload = TaskProgressCallback(
                    transferId = transferId,
                    deviceUID = deviceIdentity.uid,
                    deviceName = deviceIdentity.deviceName,
                    status = status,
                    progress = progress,
                    currentStep = currentStep,
                    totalSteps = totalSteps,
                    currentMaterial = currentMaterial,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )

                // 中文注释：优先使用设备主动连接通道；未连接时保留原 HTTP 回调，兼容旧后端/旧配对。
                if (BackendConnectionManager.getInstance(context).sendTaskProgress(payload)) {
                    Log.i(TAG, "已通过 WebSocket 上报任务进度: transferId=$transferId, status=$status")
                    return@launch
                }

                val url = callbackBaseUrl.trimEnd('/') + CALLBACK_PATH
                val jsonBody = gson.toJson(payload)

                executeWithRetry(url, jsonBody)
            } catch (e: Exception) {
                Log.e(TAG, "发送回调失败: transferId=$transferId, senderUID=$senderUID", e)
            }
        }
    }

    /**
     * 带重试的 HTTP POST
     */
    private suspend fun executeWithRetry(url: String, jsonBody: String) {
        for (attempt in 0..RETRY_DELAYS_MS.size) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                response.use {
                    if (it.isSuccessful) {
                        Log.i(TAG, "回调成功: $url (attempt=${attempt + 1})")
                        return
                    } else {
                        Log.w(TAG, "回调返回非 2xx: ${it.code} (attempt=${attempt + 1})")
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "回调网络异常 (attempt=${attempt + 1}): ${e.message}")
            }

            // 最后一次不再等待
            if (attempt < RETRY_DELAYS_MS.size) {
                val delayMs = RETRY_DELAYS_MS[attempt]
                Log.d(TAG, "等待 ${delayMs}ms 后重试...")
                delay(delayMs)
            }
        }
        Log.e(TAG, "回调最终失败，共尝试 ${RETRY_DELAYS_MS.size + 1} 次: $url")
    }

    /**
     * 清理资源
     */
    fun destroy() {
        scope.cancel()
    }
}

/**
 * 回调请求体数据结构
 */
data class TaskProgressCallback(
    val transferId: String,
    val deviceUID: String,
    val deviceName: String,
    val status: String,
    val progress: Int = 0,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentMaterial: String? = null,
    val message: String? = null,
    val timestamp: Long
)
