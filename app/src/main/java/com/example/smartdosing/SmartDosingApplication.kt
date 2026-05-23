package com.example.smartdosing

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cn.wch.ch9140lib.CH9140BluetoothManager
import com.example.smartdosing.bluetooth.BluetoothPermissionHelper
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager
import com.example.smartdosing.bluetooth.DemoModeManager
import com.example.smartdosing.data.settings.AdminPreferencesManager
import com.example.smartdosing.data.transfer.TaskResultCallbackManager
import com.example.smartdosing.web.WebService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SmartDosing 应用程序类
 * 负责初始化全局组件，包括蓝牙 SDK 和自动连接
 */
class SmartDosingApplication : Application() {

    companion object {
        private const val TAG = "SmartDosingApp"
        private const val AUTO_CONNECT_DELAY_MS = 2000L // 启动后延迟连接

        @Volatile
        private var instance: SmartDosingApplication? = null

        fun getInstance(): SmartDosingApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 全局蓝牙秤管理器（懒加载）
    val bluetoothScaleManager: BluetoothScaleManager by lazy {
        BluetoothScaleManager(this)
    }

    // 蓝牙偏好管理器
    val bluetoothPreferencesManager: BluetoothScalePreferencesManager by lazy {
        BluetoothScalePreferencesManager(this)
    }

    // 演示模式管理器
    val demoModeManager: DemoModeManager by lazy {
        DemoModeManager()
    }

    // 管理员偏好管理器
    val adminPreferencesManager: AdminPreferencesManager by lazy {
        AdminPreferencesManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        initBluetoothSdk()
        scheduleAutoConnect()
        initAppLifecycleAndWireless()
    }

    /**
     * 初始化 CH9140 蓝牙串口 SDK
     */
    private fun initBluetoothSdk() {
        try {
            CH9140BluetoothManager.getInstance().init(this)
            Log.i(TAG, "CH9140 蓝牙 SDK 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "CH9140 蓝牙 SDK 初始化失败: ${e.message}", e)
        }
    }

    /**
     * 安排自动连接任务
     * 在应用启动后延迟执行，给 UI 初始化留出时间
     */
    private fun scheduleAutoConnect() {
        applicationScope.launch {
            delay(AUTO_CONNECT_DELAY_MS)
            tryAutoConnectBoundDevice()
        }
    }

    /**
     * 尝试自动连接绑定的设备
     */
    private suspend fun tryAutoConnectBoundDevice() {
        try {
            val preferences = bluetoothPreferencesManager.preferencesFlow.first()

            // 检查是否启用自动连接
            if (!preferences.autoConnect) {
                Log.d(TAG, "自动连接已禁用")
                return
            }

            // 检查是否有绑定的设备
            if (!preferences.hasBoundDevice()) {
                Log.d(TAG, "没有绑定的设备，跳过自动连接")
                return
            }

            val boundMac = preferences.lastDeviceMac
            val deviceName = preferences.getDeviceDisplayName() ?: "未知设备"

            if (boundMac == null) {
                Log.w(TAG, "绑定设备 MAC 为空")
                return
            }

            // 检查蓝牙权限
            if (!BluetoothPermissionHelper.hasAllPermissions(this)) {
                Log.w(TAG, "缺少蓝牙权限，无法自动连接")
                return
            }

            // 检查是否已经连接
            if (bluetoothScaleManager.isConnected()) {
                Log.d(TAG, "设备已连接，跳过自动连接")
                return
            }

            Log.i(TAG, "自动连接绑定设备: $deviceName ($boundMac)")
            bluetoothScaleManager.connect(boundMac)

        } catch (e: Exception) {
            Log.e(TAG, "自动连接失败: ${e.message}", e)
        }
    }

    /**
     * 手动触发自动连接（供外部调用）
     */
    fun triggerAutoConnect() {
        applicationScope.launch {
            tryAutoConnectBoundDevice()
        }
    }

    private fun initAppLifecycleAndWireless() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(this))
            Log.i(TAG, "ProcessLifecycleOwner 监听注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "ProcessLifecycleOwner 监听注册失败: ${e.message}", e)
        }

        startWirelessService()
    }

    private fun startWirelessService() {
        val webService = WebService.getInstance(this)
        if (webService.isAutoStartEnabled()) {
            applicationScope.launch(Dispatchers.IO) {
                try {
                    val preferredPort = webService.getPreferredPort()
                    Log.i(TAG, "自启无线传输服务中... 端口: $preferredPort")
                    webService.startWebService(preferredPort)
                } catch (e: Exception) {
                    Log.e(TAG, "自启动无线服务失败: ${e.message}", e)
                }
            }
        }
    }
}

class AppLifecycleObserver(private val context: Context) : DefaultLifecycleObserver {
    companion object {
        private const val TAG = "AppLifecycleObserver"
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.i(TAG, "检测到应用进入前台，自动执行配置结果补传自愈流程")
        val app = context.applicationContext as SmartDosingApplication
        app.applicationScope.launch(Dispatchers.IO) {
            try {
                TaskResultCallbackManager.getInstance(context).retryPendingResults()
            } catch (e: Exception) {
                Log.e(TAG, "执行配置结果补传自愈失败: ${e.message}", e)
            }
        }
    }
}
