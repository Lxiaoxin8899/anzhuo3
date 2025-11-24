package com.example.smartdosing.tts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 小米TTS设置辅助类
 * 提供用户友好的TTS设置引导和权限申请
 */
class XiaomiTTSSettingsHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "XiaomiTTSSettings"
        private const val REQUEST_TTS_PERMISSIONS = 1002
        private const val REQUEST_INSTALL_TTS = 1003
        
        // 小米应用商店相关
        const val XIAOMI_APP_STORE_PACKAGE = "com.xiaomi.market"
        const val XIAOMI_TTS_APP_ID = "com.xiaomi.mibrain.speech"
        
        // 第三方TTS应用
        val THIRD_PARTY_TTS_APPS = mapOf(
            "Google TTS" to "com.google.android.tts",
            "讯飞语音+" to "com.iflytek.speechsuite",
            "百度语音" to "com.baidu.speechsynthesizer",
            "腾讯云语音" to "com.tencent.tts"
        )
    }
    
    /**
     * 检查并请求TTS权限
     */
    fun checkAndRequestTTSPermissions(activity: Activity): Boolean {
        if (!XiaomiTTSManager.XIAOMI_TTS_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }) {
            Log.d(TAG, "所有TTS权限已授予")
            return true
        }
        
        Log.d(TAG, "需要请求TTS权限")
        ActivityCompat.requestPermissions(
            activity,
            XiaomiTTSManager.XIAOMI_TTS_PERMISSIONS.toTypedArray(),
            REQUEST_TTS_PERMISSIONS
        )
        return false
    }
    
    /**
     * 显示TTS设置选项（通过Toast提示）
     */
    fun showTTSSettingsOptions(activity: Activity) {
        Log.d(TAG, "显示TTS设置选项")
        
        // 显示设置选项提示
        showToast(activity, "TTS设置选项：\n1. 小米TTS设置\n2. 系统TTS设置\n3. 安装第三方TTS\n4. TTS使用教程")
        
        // 直接打开小米TTS设置，若失败自动降级
        openXiaomiTTSSettings()
        
        // 如果语音数据缺失则主动引导
        val availability = checkTTSAvailability()
        if (!availability.xiaomiTTSDataReady) {
            startXiaomiTTSDataDownload(activity)
        }
    }
    
    /**
     * 打开小米TTS设置
     */
    fun openXiaomiTTSSettings() {
        try {
            val deviceType = getDeviceType()
            val packages = XiaomiTTSManager.XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()
            val actions = XiaomiTTSManager.XIAOMI_TTS_ACTIONS[deviceType]
            
            if (actions != null && packages.isNotEmpty()) {
                val intent = Intent(actions["settings"]).apply {
                    setPackage(packages.first())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "打开小米TTS设置")
                    return
                } else {
                    Log.w(TAG, "小米TTS设置入口在当前系统不可用")
                }
            } else {
                Log.w(TAG, "无法找到小米TTS设置Action或包名，降级到系统设置")
            }
            openSystemTTSSettings()
        } catch (e: Exception) {
            Log.e(TAG, "打开小米TTS设置失败", e)
            openSystemTTSSettings()
        }
    }
    
    /**
     * 打开系统TTS设置
     */
    fun openSystemTTSSettings() {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "打开系统TTS设置")
        } catch (e: Exception) {
            Log.e(TAG, "打开系统TTS设置失败", e)
            openAppSettings()
        }
    }
    
    /**
     * 打开应用设置
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "打开应用设置")
        } catch (e: Exception) {
            Log.e(TAG, "打开应用设置失败", e)
        }
    }
    
    /**
     * 显示TTS选择选项
     */
    fun showTTSSelectionOptions(activity: Activity) {
        Log.d(TAG, "显示TTS选择选项")
        
        val options = THIRD_PARTY_TTS_APPS.keys.joinToString("\n") { "${it}: ${THIRD_PARTY_TTS_APPS[it]}" }
        showToast(activity, "推荐TTS引擎：\n$options")
        
        // 默认安装Google TTS
        installTTSApp(activity, "Google TTS", "com.google.android.tts")
    }
    
    /**
     * 安装TTS应用
     */
    fun installTTSApp(activity: Activity, appName: String, packageName: String) {
        try {
            // 首先检查是否已安装
            val packageManager = context.packageManager
            try {
                packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "$appName 已安装，打开设置")
                openTTSAppSettings(packageName)
                showToast(activity, "$appName 已安装，请在TTS设置中启用")
                return
            } catch (e: PackageManager.NameNotFoundException) {
                Log.d(TAG, "$appName 未安装，开始安装")
            }
            
            // 打开应用商店安装
            val storeIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                setPackage(XIAOMI_APP_STORE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(storeIntent)
                Log.d(TAG, "打开小米应用商店安装: $appName")
                showToast(activity, "正在打开小米应用商店安装: $appName")
            } catch (e: Exception) {
                // 降级到浏览器
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://app.mi.com/details?id=$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Log.d(TAG, "打开浏览器安装: $appName")
                showToast(activity, "正在打开浏览器安装: $appName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "安装TTS应用失败: $appName", e)
            showToast(activity, "安装失败，请手动搜索安装: $appName")
        }
    }
    
    /**
     * 打开TTS应用设置
     */
    private fun openTTSAppSettings(packageName: String) {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开TTS应用设置失败", e)
        }
    }
    
    /**
     * 显示TTS使用教程
     */
    fun showTTSTutorial(activity: Activity) {
        val tutorial = """
            TTS语音设置教程：
            
            1. 小米TTS设置：
            - 进入设置 → 小爱同学 → 语音合成
            - 下载中文语音包
            - 选择默认语音引擎
            
            2. 系统TTS设置：
            - 进入设置 → 系统 → 语言和输入 → 文字转语音(TTS)输出
            - 选择TTS引擎
            - 安装语音数据
            
            3. 权限设置：
            - 确保应用有麦克风权限
            - 确保应用有存储权限
            
            4. 测试方法：
            - 在TTS设置中点击"测试"
            - 确保能听到语音播放
        """.trimIndent()
        
        showToast(activity, tutorial)
        Log.d(TAG, "显示TTS使用教程")
    }
    
    /**
     * 检查TTS可用性
     */
    fun checkTTSAvailability(): TTSAvailabilityResult {
        return try {
            val deviceType = getDeviceType()
            val packages = XiaomiTTSManager.XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()
            
            // 检查小米TTS包是否安装
            val xiaomiTTSInstalled = packages.any { packageName ->
                try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
            
            // 检查权限
            val permissionsGranted = XiaomiTTSManager.XIAOMI_TTS_PERMISSIONS.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            
            // 检查第三方TTS
            val thirdPartyTTSInstalled = THIRD_PARTY_TTS_APPS.values.any { packageName ->
                try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }

            val xiaomiTTSDataReady = hasXiaomiTTSData(deviceType)
            val result = TTSAvailabilityResult(
                isXiaomiDevice = isXiaomiDevice(),
                deviceType = deviceType,
                xiaomiTTSInstalled = xiaomiTTSInstalled,
                xiaomiTTSDataReady = xiaomiTTSDataReady,
                permissionsGranted = permissionsGranted,
                thirdPartyTTSInstalled = thirdPartyTTSInstalled,
                canUseTTS = (xiaomiTTSInstalled && permissionsGranted && xiaomiTTSDataReady) || thirdPartyTTSInstalled
            )
            
            Log.d(TAG, "TTS可用性检查结果: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "检查TTS可用性失败", e)
            TTSAvailabilityResult(
                isXiaomiDevice = false,
                deviceType = "unknown",
                xiaomiTTSInstalled = false,
                xiaomiTTSDataReady = false,
                permissionsGranted = false,
                thirdPartyTTSInstalled = false,
                canUseTTS = false
            )
        }
    }

    /**
     * 检查是否有可用的语音数据安装或下载渠道
     */
    private fun hasXiaomiTTSData(deviceType: String): Boolean {
        val packages = XiaomiTTSManager.XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()
        val pm = context.packageManager
        val checkerAvailable = packages.any { packageName ->
            val checkIntent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).apply {
                setPackage(packageName)
            }
            checkIntent.resolveActivity(pm) != null
        }
        if (checkerAvailable) {
            return true
        }
        val downloadAction = XiaomiTTSManager.XIAOMI_TTS_ACTIONS[deviceType]?.get("download")
        return downloadAction != null && packages.firstOrNull()?.let { pkg ->
            val downloadIntent = Intent(downloadAction).apply { setPackage(pkg) }
            downloadIntent.resolveActivity(pm) != null
        } == true
    }
    
    /**
     * 获取设备类型
     */
    private fun getDeviceType(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val display = Settings.Global.getString(
                    context.contentResolver,
                    "xiaomi.device.display"
                )
                when {
                    display.contains("HyperOS") -> "hyperos"
                    display.contains("MIUI") -> "xiaomi"
                    else -> "xiaomi"
                }
            } catch (e: Exception) {
                "xiaomi"
            }
        } else {
            "xiaomi"
        }
    }
    
    /**
     * 检查是否为小米设备
     */
    private fun isXiaomiDevice(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            manufacturer.contains("xiaomi") || brand.contains("xiaomi")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 显示TTS状态信息
     */
    fun showTTSStatus(activity: Activity) {
        val status = checkTTSAvailability()
        
        val statusText = """
            设备类型: ${if (status.isXiaomiDevice) "小米设备" else "其他设备"}
            系统版本: ${status.deviceType}
            
            小米TTS: ${if (status.xiaomiTTSInstalled) "已安装" else "未安装"}
            语音数据: ${if (status.xiaomiTTSDataReady) "已就绪" else "缺失"}
            权限状态: ${if (status.permissionsGranted) "已授权" else "未授权"}
            第三方TTS: ${if (status.thirdPartyTTSInstalled) "已安装" else "未安装"}
            
            TTS可用性: ${if (status.canUseTTS) "✅ 可用" else "❌ 不可用"}
        """.trimIndent()
        
        showToast(activity, statusText)
        Log.d(TAG, "显示TTS状态: $statusText")
    }
    
    /**
     * 显示Toast消息
     */
    private fun showToast(activity: Activity, message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    /**
     * TTS可用性检查结果
     */
    data class TTSAvailabilityResult(
        val isXiaomiDevice: Boolean,
        val deviceType: String,
        val xiaomiTTSInstalled: Boolean,
        val xiaomiTTSDataReady: Boolean,
        val permissionsGranted: Boolean,
        val thirdPartyTTSInstalled: Boolean,
        val canUseTTS: Boolean
    )

    /**
     * 引导用户下载或安装小米语音数据
     */
    fun startXiaomiTTSDataDownload(activity: Activity? = null) {
        val deviceType = getDeviceType()
        val packages = XiaomiTTSManager.XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()
        val downloadAction = XiaomiTTSManager.XIAOMI_TTS_ACTIONS[deviceType]?.get("download")
        
        if (downloadAction != null && packages.isNotEmpty()) {
            val intent = Intent(downloadAction).apply {
                setPackage(packages.first())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    activity?.let { showToast(it, "正在跳转至小米语音数据下载页面") }
                    Log.d(TAG, "打开小米语音数据下载: $downloadAction")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "打开小米语音数据下载失败", e)
                }
            } else {
                Log.w(TAG, "小米语音数据下载入口不可用，尝试系统入口")
            }
        }
        
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                activity?.let { showToast(it, "请按照系统提示安装语音数据") }
                Log.d(TAG, "打开系统语音数据安装入口")
            } else {
                Log.w(TAG, "系统未提供语音数据安装界面")
                activity?.let { showToast(it, "请在系统设置中搜索\"文字转语音\"手动安装语音数据") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法打开语音数据安装入口", e)
            activity?.let { showToast(it, "无法自动打开语音数据安装，请手动前往设置") }
        }
    }
}
