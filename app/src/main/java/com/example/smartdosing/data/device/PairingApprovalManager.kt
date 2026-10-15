package com.example.smartdosing.data.device

import android.content.Context
import android.util.Log
import com.example.smartdosing.data.transfer.BackendConnectionManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import java.util.UUID

/**
 * 设备端配对确认管理器。
 *
 * 中文注释：配对批准/拒绝只能由 App 内部 UI 调用，避免局域网客户端绕过屏幕确认直接访问 HTTP approve/reject。
 */
class PairingApprovalManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PairingApprovalManager"

        @Volatile
        private var INSTANCE: PairingApprovalManager? = null

        fun getInstance(context: Context): PairingApprovalManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PairingApprovalManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun approve(pairingId: String): PairingActionResult {
        val session = PairingRequestStore.getSession(pairingId)
            ?: return PairingActionResult(false, "配对请求不存在或已过期")
        if (session.status != PairingStatus.WAITING || System.currentTimeMillis() > session.expiresAt) {
            return PairingActionResult(false, "配对请求不存在或已处理")
        }
        val generatedApiKey = UUID.randomUUID().toString()

        return try {
            val now = System.currentTimeMillis()
            val sender = AuthorizedSenderEntity(
                uid = session.senderUID,
                name = session.senderName,
                ipAddress = session.senderIP,
                authorizedAt = now,
                isActive = true,
                senderPort = session.senderPort,
                callbackBaseUrl = session.callbackBaseUrl,
                senderApiKey = generatedApiKey
            )
            val deviceDao = SmartDosingDatabase.getDatabase(context).deviceDao()
            val previousSender = deviceDao.getSenderByUID(session.senderUID)
            deviceDao.upsertSender(sender)
            if (!PairingRequestStore.approveWithApiKey(pairingId, generatedApiKey)) {
                if (previousSender != null) {
                    deviceDao.upsertSender(previousSender)
                } else {
                    deviceDao.deleteSender(session.senderUID)
                }
                return PairingActionResult(false, "配对请求不存在或已处理")
            }
            BackendConnectionManager.getInstance(context).reconnect("pairing approved")
            PairingActionResult(true, "配对已确认，正在连接后端")
        } catch (e: Exception) {
            Log.e(TAG, "确认配对失败", e)
            PairingActionResult(false, e.localizedMessage ?: "确认配对失败")
        }
    }

    fun reject(pairingId: String): PairingActionResult {
        return if (PairingRequestStore.reject(pairingId)) {
            PairingActionResult(true, "已拒绝配对请求")
        } else {
            PairingActionResult(false, "配对请求不存在或已处理")
        }
    }
}

data class PairingActionResult(
    val success: Boolean,
    val message: String
)
