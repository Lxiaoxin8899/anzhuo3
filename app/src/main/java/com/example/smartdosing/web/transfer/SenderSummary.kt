package com.example.smartdosing.web.transfer

import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.google.gson.annotations.SerializedName

/**
 * 对外暴露的发送端摘要，不包含 API Key、回调地址等敏感字段
 */
data class SenderSummary(
    @SerializedName("uid") val uid: String,
    @SerializedName("name") val name: String,
    @SerializedName("ipAddress") val ipAddress: String?,
    @SerializedName("authorizedAt") val authorizedAt: Long,
    @SerializedName("lastTaskAt") val lastTaskAt: Long?,
    @SerializedName("taskCount") val taskCount: Int,
    @SerializedName("appVersion") val appVersion: String?,
    @SerializedName("senderPort") val senderPort: Int?
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
