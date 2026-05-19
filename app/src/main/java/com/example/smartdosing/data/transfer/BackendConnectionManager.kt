package com.example.smartdosing.data.transfer

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.web.transfer.LanTransferProposalAdapter
import com.example.smartdosing.web.transfer.UnsupportedSchemaException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * 后端主动连接管理器。
 *
 * 中文注释：该连接只负责“设备主动连后端”的控制通道，设备身份仍使用持久化 deviceUID；
 * 原本的 Ktor HTTP 服务继续保留，用于配对、调试和旧链路兼容。
 */
class BackendConnectionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BackendConnection"
        private const val CONNECT_PATH = "/api/lan/devices/connect"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val BASE_RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        @Volatile
        private var INSTANCE: BackendConnectionManager? = null

        fun getInstance(context: Context): BackendConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackendConnectionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val lanAdapter = LanTransferProposalAdapter(gson)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var connected = false

    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempts = 0
    private var manualStop = false

    fun start() {
        manualStop = false
        if (webSocket != null || reconnectJob?.isActive == true) {
            return
        }
        scope.launch {
            connect()
        }
    }

    fun stop() {
        manualStop = true
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        connected = false
        webSocket?.close(1000, "device stopped")
        webSocket = null
    }

    fun reconnect(reason: String) {
        Log.i(TAG, "请求重新连接后端: $reason")
        webSocket?.close(1001, reason)
        webSocket = null
        connected = false
        start()
    }

    fun sendTaskProgress(payload: TaskProgressCallback): Boolean {
        return sendJsonMessage("TASK_PROGRESS", gson.toJsonTree(payload).asJsonObject)
    }

    fun sendTaskResultJson(payloadJson: String): Boolean {
        val payload = runCatching { JsonParser.parseString(payloadJson) }.getOrNull() ?: return false
        return sendJsonMessage("TASK_RESULT", payload)
    }

    private suspend fun connect() {
        val target = findConnectionTarget()
        if (target == null) {
            Log.d(TAG, "暂无可用后端连接配置，等待配对后再连接")
            return
        }

        val wsUrl = buildWebSocketUrl(target.callbackBaseUrl)
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.i(TAG, "连接后端 WebSocket: $wsUrl")
        webSocket = httpClient.newWebSocket(request, DeviceSocketListener(target.apiKey))
    }

    private suspend fun findConnectionTarget(): ConnectionTarget? = withContext(Dispatchers.IO) {
        val senders = SmartDosingDatabase.getDatabase(context).deviceDao().getActiveSenders()
        val sender = senders.firstOrNull {
            !it.callbackBaseUrl.isNullOrBlank() && !it.senderApiKey.isNullOrBlank()
        }
        sender?.let {
            ConnectionTarget(
                callbackBaseUrl = it.callbackBaseUrl!!.trimEnd('/'),
                apiKey = it.senderApiKey!!
            )
        }
    }

    private fun buildWebSocketUrl(callbackBaseUrl: String): String {
        val base = when {
            callbackBaseUrl.startsWith("https://", ignoreCase = true) ->
                "wss://" + callbackBaseUrl.substringAfter("://")
            callbackBaseUrl.startsWith("http://", ignoreCase = true) ->
                "ws://" + callbackBaseUrl.substringAfter("://")
            else -> "ws://$callbackBaseUrl"
        }
        return base.trimEnd('/') + CONNECT_PATH
    }

    private fun sendHello(apiKey: String) {
        val identity = DeviceUIDManager.getDeviceIdentity(context)
        val payload = mapOf(
            "deviceUID" to identity.uid,
            "deviceName" to identity.deviceName,
            "ipAddress" to identity.ipAddress,
            "port" to identity.port,
            "appVersion" to identity.appVersion,
            "protocolVersion" to "1.0",
            "capabilities" to listOf("TASK_ASSIGN", "TASK_PROGRESS", "TASK_RESULT"),
            "apiKey" to apiKey
        )
        sendJsonMessage("HELLO", gson.toJsonTree(payload))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeat()
            }
        }
    }

    private suspend fun sendHeartbeat() {
        val identity = DeviceUIDManager.getDeviceIdentity(context)
        val executingTask = TaskReceiver.getInstance(context).getExecutingTask()
        val payload = mutableMapOf<String, Any?>(
            "deviceUID" to identity.uid,
            "deviceName" to identity.deviceName,
            "ipAddress" to identity.ipAddress,
            "port" to identity.port,
            "appVersion" to identity.appVersion,
            "status" to if (executingTask != null) "BUSY" else "IDLE"
        )
        if (executingTask != null) {
            payload["currentTask"] = mapOf(
                "transferId" to executingTask.transferId,
                "status" to executingTask.execStatus,
                "progress" to executingTask.progress
            )
        }
        sendJsonMessage("HEARTBEAT", gson.toJsonTree(payload))
    }

    private fun sendJsonMessage(type: String, data: Any): Boolean {
        val socket = webSocket ?: return false
        if (!connected && type != "HELLO") {
            return false
        }
        val message = JsonObject().apply {
            addProperty("type", type)
            add("data", gson.toJsonTree(data))
        }
        return socket.send(message.toString())
    }

    private fun sendTaskAck(messageId: String?, response: TaskReceiveResponse) {
        val message = JsonObject().apply {
            addProperty("type", "TASK_ACK")
            if (!messageId.isNullOrBlank()) {
                addProperty("messageId", messageId)
            }
            add("data", JsonObject().apply {
                if (!messageId.isNullOrBlank()) {
                    addProperty("messageId", messageId)
                }
                addProperty("transferId", response.transferId)
                addProperty("success", response.success)
                addProperty("message", response.message)
                add("data", gson.toJsonTree(response))
            })
        }
        webSocket?.send(message.toString())
    }

    private fun scheduleReconnect() {
        if (manualStop || reconnectJob?.isActive == true) {
            return
        }
        val delayMs = min(
            MAX_RECONNECT_DELAY_MS,
            BASE_RECONNECT_DELAY_MS * (1L shl min(reconnectAttempts, 4))
        )
        reconnectJob = scope.launch {
            delay(delayMs)
            reconnectAttempts++
            connect()
        }
    }

    private inner class DeviceSocketListener(private val apiKey: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "后端 WebSocket 已打开")
            this@BackendConnectionManager.webSocket = webSocket
            sendHello(apiKey)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleServerMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "后端 WebSocket 已关闭: code=$code, reason=$reason")
            connected = false
            heartbeatJob?.cancel()
            this@BackendConnectionManager.webSocket = null
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "后端 WebSocket 连接失败: ${t.message}")
            connected = false
            heartbeatJob?.cancel()
            this@BackendConnectionManager.webSocket = null
            scheduleReconnect()
        }
    }

    private fun handleServerMessage(text: String) {
        val message = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
        if (message == null) {
            Log.w(TAG, "后端 WebSocket 消息无法解析: $text")
            return
        }

        when (message.get("type")?.asString) {
            "CONNECTED" -> {
                connected = true
                reconnectAttempts = 0
                startHeartbeat()
                scope.launch {
                    TaskResultCallbackManager.getInstance(context).retryPendingResults()
                }
                Log.i(TAG, "设备主动连接后端成功")
            }
            "TASK_ASSIGN" -> handleTaskAssign(message)
            "HEARTBEAT_ACK" -> Unit
            "ERROR" -> Log.w(TAG, "后端返回错误: ${message.get("data")}")
            else -> Log.d(TAG, "忽略未知后端消息: ${message.get("type")}")
        }
    }

    private fun handleTaskAssign(message: JsonObject) {
        scope.launch {
            val messageId = message.get("messageId")?.asString
            val rawData = message.get("data")?.toString().orEmpty()
            val taskReceiver = TaskReceiver.getInstance(context)
            val identity = DeviceUIDManager.getDeviceIdentity(context)

            val response = try {
                val request = lanAdapter.tryConvert(rawData) ?: gson.fromJson(rawData, IncomingTaskRequest::class.java)
                taskReceiver.receiveTask(request)
            } catch (e: UnsupportedSchemaException) {
                TaskReceiveResponse(
                    success = false,
                    message = e.message ?: "协议版本不受支持",
                    transferId = LanTransferProposalAdapter.extractTransferId(rawData).orEmpty(),
                    receiverUID = identity.uid,
                    receiverName = identity.deviceName,
                    schemaVersion = e.version,
                    errorCode = "UNSUPPORTED_VERSION"
                )
            } catch (e: Exception) {
                Log.e(TAG, "处理后端下发任务失败", e)
                TaskReceiveResponse(
                    success = false,
                    message = e.message ?: "任务接收失败",
                    transferId = LanTransferProposalAdapter.extractTransferId(rawData).orEmpty(),
                    receiverUID = identity.uid,
                    receiverName = identity.deviceName,
                    errorCode = "UNKNOWN_ERROR"
                )
            }

            sendTaskAck(messageId, response)
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}

private data class ConnectionTarget(
    val callbackBaseUrl: String,
    val apiKey: String
)
