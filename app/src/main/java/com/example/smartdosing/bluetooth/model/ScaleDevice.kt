package com.example.smartdosing.bluetooth.model

/**
 * 蓝牙电子秤设备信息
 */
data class ScaleDevice(
    val name: String,
    val mac: String,
    val rssi: Int = 0,
    val lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * 判断是否可能是 CH9140/CH9141 蓝牙串口模块
     */
    fun isLikelyCH9140(): Boolean {
        val upperName = name.uppercase()
        return upperName.contains("CH9140") ||
                upperName.contains("CH9141") ||
                upperName.contains("WCH") ||
                upperName.contains("BLE2U") ||
                upperName.contains("SERIAL") ||
                upperName.contains("RS232") ||
                upperName.contains("SCALE") ||
                upperName.contains("秤")
    }

    /**
     * 获取显示名称（包含 MAC 后四位以区分同名设备）
     */
    fun getDisplayName(): String {
        val macSuffix = mac.takeLast(5).replace(":", "") // 取 MAC 后四位
        return if (name == "Unknown" || name.isBlank()) {
            "未知设备 ($macSuffix)"
        } else {
            "$name ($macSuffix)"
        }
    }

    /**
     * 获取信号强度描述
     */
    fun getSignalStrength(): String {
        return when {
            rssi > -50 -> "极强"
            rssi > -60 -> "强"
            rssi > -70 -> "中"
            rssi > -80 -> "弱"
            else -> "极弱"
        }
    }
}
