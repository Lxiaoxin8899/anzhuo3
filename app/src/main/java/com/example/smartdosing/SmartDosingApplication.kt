package com.example.smartdosing

import android.app.Application
import android.util.Log
import cn.wch.ch9140lib.CH9140BluetoothManager
import com.example.smartdosing.bluetooth.BluetoothPermissionHelper
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 全局蓝牙秤管理器（懒加载）
    val bluetoothScaleManager: BluetoothScaleManager by lazy {
        BluetoothScaleManager(this)
    }

    // 蓝牙偏好管理器
    val bluetoothPreferencesManager: BluetoothScalePreferencesManager by lazy {
        BluetoothScalePreferencesManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        initBluetoothSdk()
        scheduleAutoConnect()
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
}
