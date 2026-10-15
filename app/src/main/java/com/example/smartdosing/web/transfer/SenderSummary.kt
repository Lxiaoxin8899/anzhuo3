package com.example.smartdosing.web.transfer

import com.example.smartdosing.database.entities.AuthorizedSenderEntity

/**
 * 对外暴露的发送端摘要，不包含 API Key、回调地址等敏感字段
 */
data class SenderSummary(
    val uid: String,
    val name: String,
    val ipAddress: String?,
    val authorizedAt: Long,
    val lastTaskAt: Long?,
    val taskCount: Int,
    val appVersion: String?,
    val senderPort: Int?
)

fun AuthorizedSenderEntity.toSummary(): SenderSummary = SenderSummary(
    uid = uid,
    name = name,
    ipAddress = ipAddress,
    authorizedAt = authorizedAt,
    lastTaskAt = lastTaskAt,
    taskCount = taskCount,
    appVersion = appVersion,
    senderPort = senderPort
)
