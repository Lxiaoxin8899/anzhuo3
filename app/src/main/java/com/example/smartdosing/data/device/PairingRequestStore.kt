package com.example.smartdosing.data.device

import android.util.Log
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 配对请求内存管理器
 * 管理发送端发起的配对请求，支持屏幕确认/拒绝流程
 */
object PairingRequestStore {

    private const val TAG = "PairingRequestStore"
    private const val EXPIRY_MS = 60_000L // 60 秒过期
    private const val MAX_REQUESTS_PER_IP = 5 // 同一 IP 每分钟最多 5 次

    private val sessions = ConcurrentHashMap<String, PairingSession>()
    private val ipRequestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * 创建配对请求，返回 pairingId
     * @return pairingId 或 null（如果被限流）
     */
    fun createRequest(
        senderUID: String,
        senderName: String,
        senderIP: String,
        senderPort: Int,
        callbackBaseUrl: String
    ): String? {
        // 限流检查
        if (isRateLimited(senderIP)) {
            Log.w(TAG, "配对请求被限流: $senderIP")
            return null
        }

        // 清理过期请求
        cleanExpired()

        // 同一 senderUID 只允许一个活跃请求，取消旧的
        sessions.entries.removeIf { it.value.senderUID == senderUID && it.value.status == PairingStatus.WAITING }

        val pairingId = "PAIR-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}"
        val now = System.currentTimeMillis()

        val session = PairingSession(
            pairingId = pairingId,
            senderUID = senderUID,
            senderName = senderName,
            senderIP = senderIP,
            senderPort = senderPort,
            callbackBaseUrl = callbackBaseUrl,
            status = PairingStatus.WAITING,
            generatedApiKey = null,
            createdAt = now,
            expiresAt = now + EXPIRY_MS
        )

        sessions[pairingId] = session
        Log.i(TAG, "创建配对请求: $pairingId from $senderName ($senderIP)")
        return pairingId
    }

    /**
     * 获取配对会话
     */
    fun getSession(pairingId: String): PairingSession? {
        cleanExpired()
        return sessions[pairingId]
    }

    /**
     * 获取所有等待中的配对请求（供 UI 展示弹窗）
     */
    fun getWaitingRequests(): List<PairingSession> {
        cleanExpired()
        return sessions.values.filter { it.status == PairingStatus.WAITING }
    }

    /**
     * 操作员确认配对
     * @return 生成的 API Key，或 null（如果会话不存在/已过期）
     */
    fun approve(pairingId: String): String? {
        val session = sessions[pairingId] ?: return null
        if (session.status != PairingStatus.WAITING) return null
        if (System.currentTimeMillis() > session.expiresAt) {
            sessions[pairingId] = session.copy(status = PairingStatus.EXPIRED)
            return null
        }

        val apiKey = UUID.randomUUID().toString()
        sessions[pairingId] = session.copy(
            status = PairingStatus.APPROVED,
            generatedApiKey = apiKey
        )
        Log.i(TAG, "配对已确认: $pairingId, 生成 API Key")
        return apiKey
    }

    /**
     * 操作员拒绝配对
     */
    fun reject(pairingId: String): Boolean {
        val session = sessions[pairingId] ?: return false
        if (session.status != PairingStatus.WAITING) return false

        sessions[pairingId] = session.copy(status = PairingStatus.REJECTED)
        Log.i(TAG, "配对已拒绝: $pairingId")
        return true
    }

    /**
     * 清理过期的配对请求
     */
    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { (_, session) ->
            session.status == PairingStatus.WAITING && now > session.expiresAt
        }
        // 同时清理已完成的旧会话（保留 5 分钟供轮询）
        sessions.entries.removeIf { (_, session) ->
            session.status != PairingStatus.WAITING && now - session.createdAt > 5 * 60_000L
        }
        // 清理空的 IP 限流记录，防止内存泄漏
        ipRequestTimestamps.entries.removeIf { (_, timestamps) ->
            synchronized(timestamps) { timestamps.isEmpty() }
        }
    }

    /**
     * IP 限流检查（线程安全）
     */
    private fun isRateLimited(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = ipRequestTimestamps.getOrPut(ip) {
            Collections.synchronizedList(mutableListOf())
        }
        synchronized(timestamps) {
            // 清理 1 分钟前的记录
            timestamps.removeAll { now - it > 60_000L }
            if (timestamps.size >= MAX_REQUESTS_PER_IP) {
                return true
            }
            timestamps.add(now)
        }
        return false
    }
}

/**
 * 配对会话
 */
data class PairingSession(
    val pairingId: String,
    val senderUID: String,
    val senderName: String,
    val senderIP: String,
    val senderPort: Int,
    val callbackBaseUrl: String,
    val status: PairingStatus,
    val generatedApiKey: String?,
    val createdAt: Long,
    val expiresAt: Long
)

/**
 * 配对状态
 */
enum class PairingStatus {
    WAITING,    // 等待设备确认
    APPROVED,   // 已确认
    REJECTED,   // 已拒绝
    EXPIRED     // 已过期
}
