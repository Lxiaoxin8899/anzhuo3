package com.example.smartdosing.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Web服务管理类
 * 提供Web服务器的生命周期管理和网络配置
 */
class WebService(private val context: Context) {

    private val webServerManager = WebServerManager(context)
    private var serviceScope: CoroutineScope? = null

    companion object {
        private const val TAG = "WebService"
        private const val DEFAULT_PORT = 8080
        private const val LOCALHOST_FALLBACK = "127.0.0.1" // 回环地址，无法获取局域网IP时用于本机访问

        @Volatile
        private var INSTANCE: WebService? = null

        fun getInstance(context: Context): WebService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 启动Web服务
     */
    fun startWebService(port: Int = DEFAULT_PORT): WebServiceResult {
        try {
            if (webServerManager.isServerRunning()) {
                val serverUrl = getServerUrl(port)
                return if (serverUrl != null) {
                    WebServiceResult.AlreadyRunning(serverUrl)
                } else {
                    WebServiceResult.NetworkError("无法获取服务器地址")
                }
            }

            // 启动服务器
            val success = webServerManager.startServer(port)
            if (!success) {
                return WebServiceResult.StartFailed("Web服务器启动失败")
            }

            // 创建协程作用域用于后台任务
            serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            val ipAddress = getLocalIPAddress()
            if (ipAddress == null) {
                Log.w(TAG, "无法获取局域网IP，采用回环地址，仅支持本机访问")
            }
            val serverIp = ipAddress ?: LOCALHOST_FALLBACK
            val serverUrl = "http://$serverIp:$port"
            Log.i(TAG, "Web服务启动成功: $serverUrl")

            return WebServiceResult.Success(serverUrl, serverIp, port)

        } catch (e: Exception) {
            Log.e(TAG, "启动Web服务失败", e)
            return WebServiceResult.StartFailed("启动失败: ${e.localizedMessage}")
        }
    }

    /**
     * 停止Web服务
     */
    fun stopWebService(): Boolean {
        return try {
            webServerManager.stopServer()
            serviceScope?.cancel()
            serviceScope = null
            Log.i(TAG, "Web服务已停止")
            true
        } catch (e: Exception) {
            Log.e(TAG, "停止Web服务失败", e)
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
     * 获取服务器URL
     */
    fun getServerUrl(port: Int = DEFAULT_PORT): String? {
        if (!isServiceRunning()) {
            return null
        }
        val ipAddress = getLocalIPAddress() ?: LOCALHOST_FALLBACK
        return "http://$ipAddress:$port"
    }

    /**
     * 获取设备本地IP地址
     */
    private fun getLocalIPAddress(): String? {
        try {
            // 优先尝试WiFi管理器获取IP
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

            // 如果WiFi方式失败，尝试网络接口方式
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
            "http://$displayIp:${DEFAULT_PORT}"
        } else {
            null
        }

        return DeviceInfo(
            ipAddress = displayIp,
            isServerRunning = isRunning,
            serverUrl = serverUrl,
            port = DEFAULT_PORT
        )
    }

    /**
     * 重启Web服务
     */
    fun restartWebService(port: Int = DEFAULT_PORT): WebServiceResult {
        stopWebService()
        Thread.sleep(1000) // 等待服务完全停止
        return startWebService(port)
    }
}

/**
 * Web服务结果
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
