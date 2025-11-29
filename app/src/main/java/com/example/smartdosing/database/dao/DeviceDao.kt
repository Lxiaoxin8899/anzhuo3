package com.example.smartdosing.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
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
     * 根据传输 ID 获取接收任务
     */
    @Query("SELECT * FROM received_tasks WHERE transfer_id = :transferId")
    suspend fun getReceivedTaskByTransferId(transferId: String): ReceivedTaskEntity?

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
}
