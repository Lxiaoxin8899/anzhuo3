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

    /**
     * 任务状态常量（对应 received_tasks.status）
     */
    object TaskStatus {
        const val PENDING = "PENDING"
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
        const val COMPLETED = "COMPLETED"
    }

    /**
     * 执行状态常量（对应 received_tasks.exec_status）
     */
    object ExecStatus {
        const val PENDING = "PENDING"
        const val ACCEPTED = "ACCEPTED"
        const val REJECTED = "REJECTED"
        const val EXECUTING = "EXECUTING"
        const val COMPLETED = "COMPLETED"
        const val EXEC_FAILED = "EXEC_FAILED"
    }

    private val database = SmartDosingDatabase.getDatabase(context)
    private val deviceDao = database.deviceDao()
    private val recipeRepository = DatabaseRecipeRepository.getInstance(context)
    private val callbackManager = TaskProgressCallbackManager.getInstance(context)
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

            // 2. 检查发送端是否已授权（必须通过配对流程）
            val isAuthorized = deviceDao.isSenderAuthorized(request.senderUID)
            if (!isAuthorized) {
                Log.w(TAG, "未授权的发送端: ${request.senderUID}，请先完成配对")
                return TaskReceiveResponse(
                    success = false,
                    message = "发送端未授权，请先通过 /api/device/pair 完成配对",
                    transferId = request.transferId,
                    receiverUID = deviceIdentity.uid,
                    receiverName = deviceIdentity.deviceName,
                    schemaVersion = request.schemaVersion,
                    errorCode = "UNAUTHORIZED_SENDER"
                )
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
                status = TaskStatus.PENDING
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
     * 接受任务（触发 ACCEPTED 回调）
     */
    suspend fun acceptTask(taskId: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            deviceDao.updateTaskStatus(taskId, TaskStatus.ACCEPTED, now)
            deviceDao.updateTaskExecStatus(taskId, ExecStatus.ACCEPTED, TaskStatus.ACCEPTED, 0, "任务已接收，等待执行", now)
            updatePendingCount()

            // 触发回调
            val task = deviceDao.getReceivedTaskById(taskId)
            task?.let {
                callbackManager.sendCallback(
                    senderUID = it.senderUID,
                    transferId = it.transferId,
                    status = ExecStatus.ACCEPTED,
                    message = "任务已接收，等待执行"
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "接受任务失败", e)
            false
        }
    }

    /**
     * 拒绝任务（触发 REJECTED 回调）
     */
    suspend fun rejectTask(taskId: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            deviceDao.updateTaskStatus(taskId, TaskStatus.REJECTED, now)
            deviceDao.updateTaskExecStatus(taskId, ExecStatus.REJECTED, TaskStatus.REJECTED, 0, "任务已拒绝", now)
            updatePendingCount()

            // 触发回调
            val task = deviceDao.getReceivedTaskById(taskId)
            task?.let {
                callbackManager.sendCallback(
                    senderUID = it.senderUID,
                    transferId = it.transferId,
                    status = ExecStatus.REJECTED,
                    message = "任务已拒绝"
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "拒绝任务失败", e)
            false
        }
    }

    /**
     * 开始执行任务（触发 EXECUTING 回调）
     */
    suspend fun startExecuting(taskId: String, totalSteps: Int = 0, message: String? = null): Boolean {
        return try {
            val now = System.currentTimeMillis()
            deviceDao.updateTaskExecStatus(taskId, ExecStatus.EXECUTING, TaskStatus.ACCEPTED, 0, message ?: "开始执行配方任务", now)
            if (totalSteps > 0) {
                deviceDao.updateTaskProgress(taskId, 0, 1, totalSteps, null, message, now)
            }

            val task = deviceDao.getReceivedTaskById(taskId)
            task?.let {
                callbackManager.sendCallback(
                    senderUID = it.senderUID,
                    transferId = it.transferId,
                    status = ExecStatus.EXECUTING,
                    progress = 0,
                    currentStep = if (totalSteps > 0) 1 else 0,
                    totalSteps = totalSteps,
                    message = message ?: "开始执行配方任务"
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "开始执行任务失败", e)
            false
        }
    }

    /**
     * 更新执行进度（不触发回调，仅更新本地数据供轮询）
     */
    suspend fun updateProgress(
        taskId: String,
        progress: Int,
        currentStep: Int,
        totalSteps: Int,
        currentMaterial: String? = null,
        message: String? = null
    ): Boolean {
        return try {
            deviceDao.updateTaskProgress(
                taskId, progress, currentStep, totalSteps,
                currentMaterial, message, System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新任务进度失败", e)
            false
        }
    }

    /**
     * 任务完成（触发 COMPLETED 回调）
     */
    suspend fun completeTask(taskId: String, message: String? = null): Boolean {
        return try {
            val now = System.currentTimeMillis()
            deviceDao.updateTaskExecStatus(taskId, ExecStatus.COMPLETED, TaskStatus.COMPLETED, 100, message ?: "配方执行完成", now)

            val task = deviceDao.getReceivedTaskById(taskId)
            task?.let {
                callbackManager.sendCallback(
                    senderUID = it.senderUID,
                    transferId = it.transferId,
                    status = ExecStatus.COMPLETED,
                    progress = 100,
                    currentStep = it.totalSteps,
                    totalSteps = it.totalSteps,
                    message = message ?: "配方执行完成"
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "完成任务失败", e)
            false
        }
    }

    /**
     * 任务执行失败（触发 EXEC_FAILED 回调）
     */
    suspend fun failTask(taskId: String, errorMessage: String): Boolean {
        return try {
            val now = System.currentTimeMillis()
            // 先查询当前进度，失败时保留进度信息而非重置为 0
            val task = deviceDao.getReceivedTaskById(taskId)
            val currentProgress = task?.progress ?: 0
            deviceDao.updateTaskExecStatus(taskId, ExecStatus.EXEC_FAILED, TaskStatus.COMPLETED, currentProgress, errorMessage, now)

            task?.let {
                callbackManager.sendCallback(
                    senderUID = it.senderUID,
                    transferId = it.transferId,
                    status = ExecStatus.EXEC_FAILED,
                    progress = it.progress,
                    currentStep = it.currentStep,
                    totalSteps = it.totalSteps,
                    currentMaterial = it.currentMaterial,
                    message = errorMessage
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "标记任务失败失败", e)
            false
        }
    }

    /**
     * 获取当前正在执行的任务
     */
    suspend fun getExecutingTask(): ReceivedTaskEntity? {
        return try {
            deviceDao.getExecutingTask()
        } catch (e: Exception) {
            Log.e(TAG, "获取执行中任务失败", e)
            null
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
