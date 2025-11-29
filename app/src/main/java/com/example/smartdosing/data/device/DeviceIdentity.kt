package com.example.smartdosing.data.device

/**
 * 设备标识信息 - 用于局域网设备识别
 */
data class DeviceIdentity(
    val uid: String,              // 本机唯一标识 (UUID)
    val deviceName: String,       // 设备名称（可自定义）
    val ipAddress: String?,       // 当前 IP 地址
    val port: Int = 8080,         // Web 服务端口
    val appVersion: String = "",  // 应用版本
    val status: ReceiverStatus = ReceiverStatus.IDLE,  // 接收端状态
    val createdAt: Long = System.currentTimeMillis()   // UID 创建时间
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
    val uid: String,              // 发送端 UID
    val name: String,             // 发送端名称
    val ipAddress: String?,       // 最后已知 IP
    val authorizedAt: Long,       // 授权时间
    val lastTaskAt: Long? = null, // 最后一次发送任务时间
    val taskCount: Int = 0,       // 累计发送任务数
    val isActive: Boolean = true  // 是否启用
)

/**
 * 设备配对请求
 */
data class PairingRequest(
    val senderUID: String,        // 发送端 UID
    val senderName: String,       // 发送端名称
    val senderIP: String,         // 发送端 IP
    val timestamp: Long,          // 请求时间
    val pairingCode: String? = null  // 可选的配对码
)

/**
 * 设备配对响应
 */
data class PairingResponse(
    val success: Boolean,
    val message: String,
    val receiverUID: String,      // 接收端 UID
    val receiverName: String      // 接收端名称
)
