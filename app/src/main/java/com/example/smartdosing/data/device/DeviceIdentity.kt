package com.example.smartdosing.data.device

import com.google.gson.annotations.SerializedName

/**
 * 设备标识信息 - 用于局域网设备识别
 */
data class DeviceIdentity(
    @SerializedName("uid") val uid: String,              // 本机唯一标识 (UUID)
    @SerializedName("deviceName") val deviceName: String,       // 设备名称（可自定义）
    @SerializedName("ipAddress") val ipAddress: String?,       // 当前 IP 地址
    @SerializedName("port") val port: Int = 8080,         // 无线传输服务端口
    @SerializedName("appVersion") val appVersion: String = "",  // 应用版本
    @SerializedName("status") val status: ReceiverStatus = ReceiverStatus.IDLE,  // 接收端状态
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis()   // UID 创建时间
)

/**
 * 接收端状态
 */
enum class ReceiverStatus {
    IDLE,       // 空闲，可接收任务
    BUSY,       // 忙碌中（正在执行任务）
    OFFLINE     // 离线
}

/**
 * 已授权的发送端设备
 */
data class AuthorizedSender(
    @SerializedName("uid") val uid: String,              // 发送端 UID
    @SerializedName("name") val name: String,             // 发送端名称
    @SerializedName("ipAddress") val ipAddress: String?,       // 最后已知 IP
    @SerializedName("authorizedAt") val authorizedAt: Long,       // 授权时间
    @SerializedName("lastTaskAt") val lastTaskAt: Long? = null, // 最后一次发送任务时间
    @SerializedName("taskCount") val taskCount: Int = 0,       // 累计发送任务数
    @SerializedName("isActive") val isActive: Boolean = true  // 是否启用
)

/**
 * 设备配对请求
 */
data class PairingRequest(
    @SerializedName("senderUID") val senderUID: String,        // 发送端 UID
    @SerializedName("senderName") val senderName: String,       // 发送端名称
    @SerializedName("senderIP") val senderIP: String,         // 发送端 IP
    @SerializedName("timestamp") val timestamp: Long,          // 请求时间
    @SerializedName("pairingCode") val pairingCode: String? = null  // 可选的配对码
)

/**
 * 设备配对响应
 */
data class PairingResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("receiverUID") val receiverUID: String,      // 接收端 UID
    @SerializedName("receiverName") val receiverName: String      // 接收端名称
)
