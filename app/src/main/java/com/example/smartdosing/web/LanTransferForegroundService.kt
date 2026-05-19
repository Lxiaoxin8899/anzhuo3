package com.example.smartdosing.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smartdosing.MainActivity
import com.example.smartdosing.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 局域网传输前台服务。
 * 用于在应用切后台、息屏或网络切换后继续托管无线传输服务，降低设备“假离线”和被系统回收的概率。
 */
class LanTransferForegroundService : Service() {

    companion object {
        private const val TAG = "LanTransferKeepAlive"
        private const val CHANNEL_ID = "lan_transfer_keep_alive"
        private const val CHANNEL_NAME = "无线传输保活"
        private const val NOTIFICATION_ID = 20031
        private const val ACTION_START = "com.example.smartdosing.web.action.START_KEEP_ALIVE"
        private const val ACTION_STOP = "com.example.smartdosing.web.action.STOP_KEEP_ALIVE"
        private const val EXTRA_PORT = "extra_port"
        private const val HEALTH_CHECK_INTERVAL_MS = 20_000L
        private const val NETWORK_RECOVERY_DELAY_MS = 1_500L
        private const val WAKE_LOCK_TAG = "smartdosing:lan-transfer-wake-lock"
        private const val WIFI_LOCK_TAG = "smartdosing:lan-transfer-wifi-lock"

        fun start(context: Context, port: Int) {
            val intent = Intent(context, LanTransferForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LanTransferForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isStopping = false
    private var networkCallbackRegistered = false

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleSelfHeal("网络已恢复")
        }

        override fun onLost(network: Network) {
            updateForegroundNotification("网络暂时不可用，等待自动恢复")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: android.net.NetworkCapabilities
        ) {
            scheduleSelfHeal("网络能力发生变化")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            isStopping = true
            Log.i(TAG, "收到停止保活服务指令")
            WebService.getInstance(this).stopServerOnly()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        isStopping = false
        val preferredPort = intent?.getIntExtra(EXTRA_PORT, WebService.getInstance(this).getPreferredPort())
            ?: WebService.getInstance(this).getPreferredPort()

        startForeground(
            NOTIFICATION_ID,
            buildNotification("正在初始化无线传输服务")
        )

        serviceScope.launch {
            ensureServerRunning(preferredPort, "前台保活服务启动")
        }
        startHealthMonitor()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        releaseWifiLock()
        releaseWakeLock()
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()

        if (!isStopping) {
            Log.w(TAG, "前台保活服务被系统回收，尝试重新拉起")
            start(applicationContext, WebService.getInstance(applicationContext).getPreferredPort())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHealthMonitor() {
        if (monitorJob?.isActive == true) {
            return
        }
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                ensureServerRunning(
                    port = WebService.getInstance(this@LanTransferForegroundService).getPreferredPort(),
                    reason = "周期巡检"
                )
            }
        }
    }

    private fun scheduleSelfHeal(reason: String) {
        serviceScope.launch {
            delay(NETWORK_RECOVERY_DELAY_MS)
            ensureServerRunning(
                port = WebService.getInstance(this@LanTransferForegroundService).getPreferredPort(),
                reason = reason
            )
        }
    }

    private suspend fun ensureServerRunning(port: Int, reason: String) {
        when (val result = WebService.getInstance(this).ensureServerRunning(port)) {
            is WebServiceResult.Success -> {
                Log.i(TAG, "无线传输服务已恢复，启动成功，原因: $reason, url=${result.serverUrl}")
                updateForegroundNotification("服务在线: ${result.serverUrl}")
            }

            is WebServiceResult.AlreadyRunning -> {
                updateForegroundNotification("服务运行中: ${result.serverUrl}")
            }

            is WebServiceResult.NetworkError -> {
                Log.w(TAG, "服务已启动，但当前无法获取局域网地址，原因: $reason")
                updateForegroundNotification("服务已启动，等待局域网地址恢复")
            }

            is WebServiceResult.StartFailed -> {
                Log.e(TAG, "无线传输服务启动失败，原因: $reason, message=${result.message}")
                updateForegroundNotification("服务异常，正在自动重试")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持设备端局域网传输服务在线"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LanTransferForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1002,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("无线传输服务保活中")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(launchPendingIntent)
            .addAction(0, "停止", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            if (!isHeld) {
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w(TAG, "释放 WakeLock 失败: ${it.message}")
        }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
            setReferenceCounted(false)
            if (!isHeld) {
                acquire()
            }
        }
    }

    private fun releaseWifiLock() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w(TAG, "释放 WifiLock 失败: ${it.message}")
        }
        wifiLock = null
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure {
            Log.w(TAG, "注册网络回调失败: ${it.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) {
            return
        }
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }.onFailure {
            Log.w(TAG, "注销网络回调失败: ${it.message}")
        }
        networkCallbackRegistered = false
    }
}
