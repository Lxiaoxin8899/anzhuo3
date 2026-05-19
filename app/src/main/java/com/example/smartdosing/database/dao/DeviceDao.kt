package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.example.smartdosing.database.entities.PendingTaskResultSyncEntity
import com.example.smartdosing.database.entities.ReceivedTaskEntity

/**
 * 设备通信相关数据访问对象 (DAO)
 * 管理授权发送端和接收任务记录
 */
@Dao
interface DeviceDao {

    // =================================
    // 授权发送端管理
    // =================================

    /**
     * 获取所有授权发送端（实时流）
     */
    @Query("SELECT * FROM authorized_senders ORDER BY authorized_at DESC")
    fun getAllSendersFlow(): Flow<List<AuthorizedSenderEntity>>

    /**
     * 获取所有活跃的授权发送端
     */
    @Query("SELECT * FROM authorized_senders WHERE is_active = 1 ORDER BY authorized_at DESC")
    suspend fun getActiveSenders(): List<AuthorizedSenderEntity>

    /**
     * 根据 UID 获取授权发送端
     */
    @Query("SELECT * FROM authorized_senders WHERE uid = :uid")
    suspend fun getSenderByUID(uid: String): AuthorizedSenderEntity?

    /**
     * 检查发送端是否已授权且活跃
     */
    @Query("SELECT EXISTS(SELECT 1 FROM authorized_senders WHERE uid = :uid AND is_active = 1)")
    suspend fun isSenderAuthorized(uid: String): Boolean

    /**
     * 插入或更新授权发送端
     */
    @Upsert
    suspend fun upsertSender(sender: AuthorizedSenderEntity)

    /**
     * 更新发送端的最后任务时间和任务计数
     */
    @Query("""
        UPDATE authorized_senders
        SET last_task_at = :timestamp, task_count = task_count + 1, ip_address = :ipAddress, app_version = :appVersion
        WHERE uid = :uid
    """)
    suspend fun updateSenderActivity(uid: String, timestamp: Long, ipAddress: String?, appVersion: String?)

    /**
     * 启用/禁用发送端
     */
    @Query("UPDATE authorized_senders SET is_active = :isActive WHERE uid = :uid")
    suspend fun setSenderActive(uid: String, isActive: Boolean)

    /**
     * 删除授权发送端
     */
    @Query("DELETE FROM authorized_senders WHERE uid = :uid")
    suspend fun deleteSender(uid: String)

    /**
     * 获取授权发送端数量
     */
    @Query("SELECT COUNT(*) FROM authorized_senders WHERE is_active = 1")
    suspend fun getActiveSenderCount(): Int

    // =================================
    // 接收任务管理
    // =================================

    /**
     * 获取所有接收的任务（实时流）
     */
    @Query("SELECT * FROM received_tasks ORDER BY received_at DESC")
    fun getAllReceivedTasksFlow(): Flow<List<ReceivedTaskEntity>>

    /**
     * 获取所有接收的任务（一次性查询，供任务同步使用）
     */
    @Query("SELECT * FROM received_tasks ORDER BY received_at DESC")
    suspend fun getAllReceivedTasks(): List<ReceivedTaskEntity>

    /**
     * 获取待处理的接收任务
     */
    @Query("SELECT * FROM received_tasks WHERE status = 'PENDING' ORDER BY received_at DESC")
    fun getPendingTasksFlow(): Flow<List<ReceivedTaskEntity>>

    /**
     * 根据 ID 获取接收任务
     */
    @Query("SELECT * FROM received_tasks WHERE id = :id")
    suspend fun getReceivedTaskById(id: String): ReceivedTaskEntity?

    /**
     * 检查传输 ID 是否已存在（防止重复接收）
     */
    @Query("SELECT EXISTS(SELECT 1 FROM received_tasks WHERE transfer_id = :transferId)")
    suspend fun isTransferIdExists(transferId: String): Boolean

    /**
     * 插入接收任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivedTask(task: ReceivedTaskEntity)

    /**
     * 更新任务状态
     */
    @Query("UPDATE received_tasks SET status = :status, accepted_at = :acceptedAt WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: String, acceptedAt: Long?)

    /**
     * 关联本地配方
     */
    @Query("UPDATE received_tasks SET local_recipe_id = :recipeId WHERE id = :id")
    suspend fun linkLocalRecipe(id: String, recipeId: String)

    /**
     * 删除接收任务
     */
    @Query("DELETE FROM received_tasks WHERE id = :id")
    suspend fun deleteReceivedTask(id: String)

    /**
     * 获取待处理任务数量
     */
    @Query("SELECT COUNT(*) FROM received_tasks WHERE status = 'PENDING'")
    suspend fun getPendingTaskCount(): Int

    /**
     * 获取来自特定发送端的任务数量
     */
    @Query("SELECT COUNT(*) FROM received_tasks WHERE sender_uid = :senderUID")
    suspend fun getTaskCountBySender(senderUID: String): Int

    /**
     * 分页查询接收任务
     */
    @Query("""
        SELECT * FROM received_tasks
        WHERE (:status IS NULL OR status = :status)
        AND (:senderUID IS NULL OR sender_uid = :senderUID)
        ORDER BY received_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getReceivedTasksPaged(
        status: String? = null,
        senderUID: String? = null,
        limit: Int,
        offset: Int
    ): List<ReceivedTaskEntity>

    // =================================
    // v6 新增：执行进度管理
    // =================================

    /**
     * 更新任务执行状态（状态流转）
     */
    @Query("""
        UPDATE received_tasks
        SET exec_status = :execStatus,
            status = :status,
            progress = :progress,
            exec_message = :message,
            exec_updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateTaskExecStatus(
        id: String,
        execStatus: String,
        status: String,
        progress: Int,
        message: String?,
        updatedAt: Long
    )

    /**
     * 更新任务执行进度（不改变状态，仅更新进度数据）
     */
    @Query("""
        UPDATE received_tasks
        SET progress = :progress,
            current_step = :currentStep,
            total_steps = :totalSteps,
            current_material = :currentMaterial,
            exec_message = :message,
            exec_updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateTaskProgress(
        id: String,
        progress: Int,
        currentStep: Int,
        totalSteps: Int,
        currentMaterial: String?,
        message: String?,
        updatedAt: Long
    )

    /**
     * 根据 transferId 查询任务（用于进度查询接口）
     */
    @Query("SELECT * FROM received_tasks WHERE transfer_id = :transferId")
    suspend fun getTaskByTransferId(transferId: String): ReceivedTaskEntity?

    /**
     * 批量根据 transferId 查询任务
     */
    @Query("SELECT * FROM received_tasks WHERE transfer_id IN (:transferIds)")
    suspend fun getTasksByTransferIds(transferIds: List<String>): List<ReceivedTaskEntity>

    /**
     * 获取当前正在执行的任务（exec_status = EXECUTING）
     */
    @Query("SELECT * FROM received_tasks WHERE exec_status = 'EXECUTING' LIMIT 1")
    suspend fun getExecutingTask(): ReceivedTaskEntity?

    /**
     * 标记局域网任务最终配置结果已同步到后端
     */
    @Query("""
        UPDATE received_tasks
        SET result_record_id = :resultRecordId,
            result_synced_at = :syncedAt
        WHERE id = :taskId
    """)
    suspend fun markTaskResultSynced(taskId: String, resultRecordId: String, syncedAt: Long)

    // =================================
    // 最终配置结果待同步队列
    // =================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingTaskResultSync(sync: PendingTaskResultSyncEntity)

    @Query("SELECT * FROM pending_task_result_syncs ORDER BY updated_at ASC LIMIT :limit")
    suspend fun getPendingTaskResultSyncs(limit: Int = 20): List<PendingTaskResultSyncEntity>

    @Query("DELETE FROM pending_task_result_syncs WHERE transfer_id = :transferId")
    suspend fun deletePendingTaskResultSync(transferId: String)

    @Query("""
        UPDATE pending_task_result_syncs
        SET attempts = attempts + 1,
            last_error = :lastError,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun incrementPendingTaskResultSyncAttempt(id: String, lastError: String?, updatedAt: Long)

    // =================================
    // v6 新增：配对 API Key 管理
    // =================================

    /**
     * 根据 API Key 查找已授权的发送端
     */
    @Query("SELECT * FROM authorized_senders WHERE sender_api_key = :apiKey AND is_active = 1")
    suspend fun getSenderByApiKey(apiKey: String): AuthorizedSenderEntity?

    /**
     * 获取所有已配对发送端的 API Key 列表
     */
    @Query("SELECT sender_api_key FROM authorized_senders WHERE sender_api_key IS NOT NULL AND is_active = 1")
    suspend fun getAllSenderApiKeys(): List<String>

    /**
     * 根据发送端 UID 获取回调地址
     */
    @Query("SELECT callback_base_url FROM authorized_senders WHERE uid = :uid AND is_active = 1")
    suspend fun getCallbackBaseUrl(uid: String): String?
}
