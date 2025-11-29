package com.example.smartdosing.data.transfer

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.example.smartdosing.database.entities.ReceivedTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.abs

/**
 * 任务接收器 - 处理来自发送端的任务
 */
class TaskReceiver(private val context: Context) {

    companion object {
        private const val TAG = "TaskReceiver"

        @Volatile
        private var INSTANCE: TaskReceiver? = null

        fun getInstance(context: Context): TaskReceiver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaskReceiver(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val database = SmartDosingDatabase.getDatabase(context)
    private val deviceDao = database.deviceDao()
    private val recipeRepository = DatabaseRecipeRepository.getInstance(context)
    private val supportedSchemaVersions = setOf("1.0")

    // 新任务通知
    private val _newTaskReceived = MutableStateFlow<ReceivedTaskEntity?>(null)
    val newTaskReceived: StateFlow<ReceivedTaskEntity?> = _newTaskReceived.asStateFlow()

    // 待处理任务数量
    private val _pendingTaskCount = MutableStateFlow(0)
    val pendingTaskCount: StateFlow<Int> = _pendingTaskCount.asStateFlow()

    /**
     * 处理接收到的任务请求
     */
    suspend fun receiveTask(request: IncomingTaskRequest): TaskReceiveResponse {
        val deviceIdentity = DeviceUIDManager.getDeviceIdentity(context)

        try {
            if (!supportedSchemaVersions.contains(request.schemaVersion)) {
                Log.w(TAG, "不支持的协议版本: ${request.schemaVersion}")
                return TaskReceiveResponse(
                    success = false,
                    message = "协议版本不受支持",
                    transferId = request.transferId,
                    receiverUID = deviceIdentity.uid,
                    receiverName = deviceIdentity.deviceName,
                    schemaVersion = request.schemaVersion,
                    errorCode = "UNSUPPORTED_VERSION"
                )
            }

            if (request.task.quantity <= 0) {
                Log.w(TAG, "任务数量非法: ${request.task.quantity}")
                return TaskReceiveResponse(
                    success = false,
                    message = "任务数量必须大于 0",
                    transferId = request.transferId,
                    receiverUID = deviceIdentity.uid,
                    receiverName = deviceIdentity.deviceName,
                    schemaVersion = request.schemaVersion,
                    errorCode = "FIELD_INVALID"
                )
            }

            val recipeMaterials = request.recipe?.materials.orEmpty()
            if (request.recipe != null && recipeMaterials.isEmpty()) {
                return TaskReceiveResponse(
                    success = false,
                    message = "配方材料不能为空",
                    transferId = request.transferId,
                    receiverUID = deviceIdentity.uid,
                    receiverName = deviceIdentity.deviceName,
                    schemaVersion = request.schemaVersion,
                    errorCode = "FIELD_MISSING"
                )
            }

            val warningCodes = mutableListOf<String>()
            request.recipe?.let { recipe ->
                if (recipe.totalWeight > 0) {
                    val diff = abs(recipe.totalWeight - request.task.quantity)
                    val ratio = diff / recipe.totalWeight
                    if (ratio > 0.05) {
                        warningCodes += "QUANTITY_MISMATCH"
                    }
                }
            }
            // 1. 检查是否重复传输
            if (deviceDao.isTransferIdExists(request.transferId)) {
                Log.w(TAG, "重复的传输请求: ${request.transferId}")
                return TaskReceiveResponse(
                    success = false,
                    message = "任务已接收过，请勿重复发送",
                    transferId = request.transferId,
                    receiverUID = deviceIdentity.uid,
                    receiverName = deviceIdentity.deviceName,
                    schemaVersion = request.schemaVersion,
                    errorCode = "DUPLICATE_TRANSFER"
                )
            }

            val now = System.currentTimeMillis()

            // 2. 检查发送端是否已授权
            val isAuthorized = deviceDao.isSenderAuthorized(request.senderUID)
            if (!isAuthorized) {
                Log.w(TAG, "未授权的发送端: ${request.senderUID}")
                // 自动授权新设备（可以根据需求改为需要手动确认）
                val newSender = AuthorizedSenderEntity(
                    uid = request.senderUID,
                    name = request.senderName,
                    ipAddress = request.senderIP,
                    authorizedAt = now,
                    lastTaskAt = now,
                    isActive = true,
                    appVersion = request.senderAppVersion
                )
                deviceDao.upsertSender(newSender)
                Log.i(TAG, "已自动授权新设备: ${request.senderName}")
            }

            // 3. 生成本地任务 ID
            val localTaskId = "RT-${UUID.randomUUID().toString().take(8).uppercase()}"

            // 4. 若包含配方则先落库
            var linkedRecipeId: String? = null
            request.recipe?.let { recipePayload ->
                val importRequest = recipePayload.toImportRequest()
                if (importRequest.code.isBlank()) {
                    return TaskReceiveResponse(
                        success = false,
                        message = "配方编码不能为空",
                        transferId = request.transferId,
                        receiverUID = deviceIdentity.uid,
                        receiverName = deviceIdentity.deviceName,
                        schemaVersion = request.schemaVersion,
                        errorCode = "FIELD_MISSING"
                    )
                }
                val existingRecipe = recipeRepository.getRecipeByCode(importRequest.code)
                if (existingRecipe != null) {
                    return TaskReceiveResponse(
                        success = false,
                        message = "配方编码已存在，如需覆盖请人工确认",
                        transferId = request.transferId,
                        receiverUID = deviceIdentity.uid,
                        receiverName = deviceIdentity.deviceName,
                        schemaVersion = request.schemaVersion,
                        errorCode = "RECIPE_EXISTS"
                    )
                }
                val createdRecipe = recipeRepository.addRecipe(importRequest)
                linkedRecipeId = createdRecipe.id
            }

            // 4. 保存接收任务
            val receivedTask = ReceivedTaskEntity(
                id = localTaskId,
                transferId = request.transferId,
                senderUID = request.senderUID,
                senderName = request.senderName,
                senderIP = request.senderIP,
                senderAppVersion = request.senderAppVersion,
                schemaVersion = request.schemaVersion,
                title = request.task.title,
                recipeCode = request.task.recipeCode,
                recipeName = request.task.recipeName,
                quantity = request.task.quantity,
                unit = request.task.unit,
                priority = request.task.priority,
                deadline = request.task.deadline,
                customer = request.task.customer,
                note = request.task.note,
                localRecipeId = linkedRecipeId,
                receivedAt = now,
                status = "PENDING"
            )

            deviceDao.insertReceivedTask(receivedTask)

            // 6. 更新发送端活动记录
            deviceDao.updateSenderActivity(
                uid = request.senderUID,
                timestamp = now,
                ipAddress = request.senderIP,
                appVersion = request.senderAppVersion
            )

            // 7. 通知新任务到达
            _newTaskReceived.value = receivedTask
            updatePendingCount()

            Log.i(TAG, "成功接收任务: $localTaskId from ${request.senderName}")

            return TaskReceiveResponse(
                success = true,
                message = "任务接收成功",
                transferId = request.transferId,
                receivedTaskId = localTaskId,
                receiverUID = deviceIdentity.uid,
                receiverName = deviceIdentity.deviceName,
                schemaVersion = request.schemaVersion,
                warningCodes = warningCodes
            )

        } catch (e: Exception) {
            Log.e(TAG, "接收任务失败", e)
            return TaskReceiveResponse(
                success = false,
                message = "接收任务失败: ${e.message}",
                transferId = request.transferId,
                receiverUID = deviceIdentity.uid,
                receiverName = deviceIdentity.deviceName,
                schemaVersion = request.schemaVersion,
                errorCode = "UNKNOWN_ERROR"
            )
        }
    }

    /**
     * 接受任务
     */
    suspend fun acceptTask(taskId: String): Boolean {
        return try {
            deviceDao.updateTaskStatus(taskId, "ACCEPTED", System.currentTimeMillis())
            updatePendingCount()
            true
        } catch (e: Exception) {
            Log.e(TAG, "接受任务失败", e)
            false
        }
    }

    /**
     * 拒绝任务
     */
    suspend fun rejectTask(taskId: String): Boolean {
        return try {
            deviceDao.updateTaskStatus(taskId, "REJECTED", System.currentTimeMillis())
            updatePendingCount()
            true
        } catch (e: Exception) {
            Log.e(TAG, "拒绝任务失败", e)
            false
        }
    }

    /**
     * 获取待处理任务列表（实时流）
     */
    fun getPendingTasksFlow(): Flow<List<ReceivedTaskEntity>> {
        return deviceDao.getPendingTasksFlow()
    }

    /**
     * 获取所有接收任务列表（实时流）
     */
    fun getAllReceivedTasksFlow(): Flow<List<ReceivedTaskEntity>> {
        return deviceDao.getAllReceivedTasksFlow()
    }

    /**
     * 获取所有授权发送端（实时流）
     */
    fun getAuthorizedSendersFlow(): Flow<List<AuthorizedSenderEntity>> {
        return deviceDao.getAllSendersFlow()
    }

    /**
     * 添加授权发送端
     */
    suspend fun authorizeSender(uid: String, name: String, ipAddress: String? = null): Boolean {
        return try {
            val sender = AuthorizedSenderEntity(
                uid = uid,
                name = name,
                ipAddress = ipAddress,
                authorizedAt = System.currentTimeMillis(),
                isActive = true
            )
            deviceDao.upsertSender(sender)
            true
        } catch (e: Exception) {
            Log.e(TAG, "授权发送端失败", e)
            false
        }
    }

    /**
     * 移除授权发送端
     */
    suspend fun revokeSender(uid: String): Boolean {
        return try {
            deviceDao.deleteSender(uid)
            true
        } catch (e: Exception) {
            Log.e(TAG, "移除发送端失败", e)
            false
        }
    }

    /**
     * 启用/禁用发送端
     */
    suspend fun setSenderActive(uid: String, active: Boolean): Boolean {
        return try {
            deviceDao.setSenderActive(uid, active)
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新发送端状态失败", e)
            false
        }
    }

    /**
     * 更新待处理任务计数
     */
    private suspend fun updatePendingCount() {
        try {
            _pendingTaskCount.value = deviceDao.getPendingTaskCount()
        } catch (e: Exception) {
            Log.e(TAG, "更新待处理计数失败", e)
        }
    }

    /**
     * 清除新任务通知
     */
    fun clearNewTaskNotification() {
        _newTaskReceived.value = null
    }
}
