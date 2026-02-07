package com.example.smartdosing.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 无线传输服务管理类
 * 提供无线传输服务器的生命周期管理和网络配置
 */
class WebService(private val context: Context) {

    private val webServerManager = WebServerManager(context)
    private var serviceScope: CoroutineScope? = null
    private val servicePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private var currentPort: Int = DEFAULT_PORT

    init {
        currentPort = servicePrefs.getInt(KEY_PREFERRED_PORT, DEFAULT_PORT)
    }

    companion object {
        private const val TAG = "WebService"
        private const val DEFAULT_PORT = 8080
        private const val LOCALHOST_FALLBACK = "127.0.0.1" // 回环地址，无法获取局域网IP时用于本机访问
        private const val PREFS_NAME = "wireless_transfer_prefs"
        private const val KEY_PREFERRED_PORT = "preferred_port"
        private const val KEY_AUTO_START = "auto_start_enabled"

        @Volatile
        private var INSTANCE: WebService? = null

        fun getInstance(context: Context): WebService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 启动无线传输服务
     */
    fun startWebService(port: Int = DEFAULT_PORT): WebServiceResult {
        try {
            val targetPort = normalizePort(port)
            if (webServerManager.isServerRunning()) {
                val serverUrl = getServerUrl(currentPort)
                return if (serverUrl != null) {
                    WebServiceResult.AlreadyRunning(serverUrl)
                } else {
                    WebServiceResult.NetworkError("无法获取无线传输服务地址")
                }
            }

            val success = webServerManager.startServer(targetPort)
            if (!success) {
                return WebServiceResult.StartFailed("无线传输服务器启动失败")
            }

            serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            val ipAddress = getLocalIPAddress()
            if (ipAddress == null) {
                Log.w(TAG, "无法获取局域网IP，采用回环地址，仅支持本机访问")
            }
            val serverIp = ipAddress ?: LOCALHOST_FALLBACK
            val serverUrl = "http://$serverIp:$targetPort"
            setPreferredPort(targetPort)
            Log.i(TAG, "无线传输服务启动成功: $serverUrl")

            return WebServiceResult.Success(serverUrl, serverIp, targetPort)

        } catch (e: Exception) {
            Log.e(TAG, "启动无线传输服务失败", e)
            return WebServiceResult.StartFailed("启动失败: ${e.localizedMessage}")
        }
    }

    /**
     * 停止无线传输服务
     */
    fun stopWebService(): Boolean {
        return try {
            webServerManager.stopServer()
            serviceScope?.cancel()
            serviceScope = null
            Log.i(TAG, "无线传输服务已停止")
            true
        } catch (e: Exception) {
            Log.e(TAG, "停止无线传输服务失败", e)
            false
        }
    }

    /**
     * 检查服务状态
     */
    fun isServiceRunning(): Boolean {
        return webServerManager.isServerRunning()
    }

    /**
     * 获取无线传输 API Key，用于接口鉴权。
     */
    fun getApiKey(): String {
        return webServerManager.apiKey
    }

    /**
     * 获取服务器URL
     */
    fun getServerUrl(port: Int = currentPort): String? {
        if (!isServiceRunning()) {
            return null
        }
        val ipAddress = getLocalIPAddress() ?: LOCALHOST_FALLBACK
        val targetPort = normalizePort(port)
        return "http://$ipAddress:$targetPort"
    }

    /**
     * 获取偏好端口
     */
    fun getPreferredPort(): Int = currentPort

    /**
     * 设置偏好端口
     */
    fun setPreferredPort(port: Int) {
        if (port !in 1024..65535) return
        currentPort = port
        servicePrefs.edit().putInt(KEY_PREFERRED_PORT, port).apply()
    }

    /**
     * 是否自动启动无线传输服务
     */
    fun isAutoStartEnabled(): Boolean = servicePrefs.getBoolean(KEY_AUTO_START, true)

    /**
     * 设置是否自动启动无线传输服务
     */
    fun setAutoStartEnabled(enabled: Boolean) {
        servicePrefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    /**
     * 获取设备本地IP地址
     */
    private fun getLocalIPAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress

            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in networkInterfaces) {
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    for (address in addresses) {
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取IP地址失败", e)
        }

        return null
    }

    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): DeviceInfo {
        val isRunning = isServiceRunning()
        val ipAddress = getLocalIPAddress()
        val displayIp = ipAddress ?: if (isRunning) LOCALHOST_FALLBACK else null
        val serverUrl = if (isRunning && displayIp != null) {
            "http://$displayIp:$currentPort"
        } else {
            null
        }

        return DeviceInfo(
            ipAddress = displayIp,
            isServerRunning = isRunning,
            serverUrl = serverUrl,
            port = currentPort
        )
    }

    /**
     * 重启无线传输服务
     */
    fun restartWebService(port: Int = currentPort): WebServiceResult {
        stopWebService()
        return startWebService(port)
    }

    private fun normalizePort(port: Int): Int {
        return if (port in 1024..65535) port else currentPort
    }
}

/**
 * 无线传输服务结果
 */
sealed class WebServiceResult {
    data class Success(
        val serverUrl: String,
        val ipAddress: String,
        val port: Int
    ) : WebServiceResult()

    data class AlreadyRunning(val serverUrl: String) : WebServiceResult()
    data class NetworkError(val message: String) : WebServiceResult()
    data class StartFailed(val message: String) : WebServiceResult()
}

/**
 * 设备信息
 */
data class DeviceInfo(
    val ipAddress: String?,
    val isServerRunning: Boolean,
    val serverUrl: String?,
    val port: Int
)
