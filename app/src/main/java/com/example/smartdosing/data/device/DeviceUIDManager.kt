package com.example.smartdosing.data.device

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

/**
 * 设备 UID 管理器 - 负责生成和管理本机唯一标识
 */
object DeviceUIDManager {

    private const val PREFS_NAME = "device_identity_prefs"
    private const val KEY_DEVICE_UID = "device_uid"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_CREATED_AT = "created_at"

    private var cachedIdentity: DeviceIdentity? = null

    /**
     * 获取或创建本机 UID
     */
    fun getOrCreateUID(context: Context): String {
        val prefs = getPrefs(context)
        var uid = prefs.getString(KEY_DEVICE_UID, null)

        if (uid.isNullOrEmpty()) {
            uid = generateUID()
            prefs.edit()
                .putString(KEY_DEVICE_UID, uid)
                .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                .apply()
        }

        return uid
    }

    /**
     * 获取完整设备标识信息
     */
    fun getDeviceIdentity(context: Context): DeviceIdentity {
        cachedIdentity?.let {
            // 更新 IP 地址（可能变化）
            return it.copy(ipAddress = getLocalIPAddress(context))
        }

        val prefs = getPrefs(context)
        val uid = getOrCreateUID(context)
        val deviceName = prefs.getString(KEY_DEVICE_NAME, null) ?: getDefaultDeviceName()
        val createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())

        val identity = DeviceIdentity(
            uid = uid,
            deviceName = deviceName,
            ipAddress = getLocalIPAddress(context),
            port = 8080,
            appVersion = getAppVersion(context),
            status = ReceiverStatus.IDLE,
            createdAt = createdAt
        )

        cachedIdentity = identity
        return identity
    }

    /**
     * 更新设备名称
     */
    fun updateDeviceName(context: Context, newName: String) {
        getPrefs(context).edit()
            .putString(KEY_DEVICE_NAME, newName)
            .apply()
        cachedIdentity = cachedIdentity?.copy(deviceName = newName)
    }

    /**
     * 获取设备名称
     */
    fun getDeviceName(context: Context): String {
        return getPrefs(context).getString(KEY_DEVICE_NAME, null) ?: getDefaultDeviceName()
    }

    /**
     * 重置 UID（慎用，会导致发送端需要重新绑定）
     */
    fun resetUID(context: Context): String {
        val newUID = generateUID()
        getPrefs(context).edit()
            .putString(KEY_DEVICE_UID, newUID)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        cachedIdentity = null
        return newUID
    }

    /**
     * 生成新的 UID
     */
    private fun generateUID(): String {
        // 格式: SD-XXXXXXXX (SD = SmartDosing 前缀 + 8位随机字符)
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase()
        return "SD-${uuid.take(8)}"
    }

    /**
     * 获取默认设备名称
     */
    private fun getDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * 获取本机 IP 地址
     */
    @Suppress("DEPRECATION")
    private fun getLocalIPAddress(context: Context): String? {
        try {
            // 优先尝试 WiFi
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
                if (ipInt != 0) {
                    val ip = String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            // 备用：遍历网络接口
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    networkInterface.inetAddresses?.toList()?.forEach { address ->
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
