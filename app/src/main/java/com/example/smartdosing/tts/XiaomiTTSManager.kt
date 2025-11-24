package com.example.smartdosing.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 小米设备TTS专用管理器
 * 解决小米MIUI系统TTS引擎检测和初始化问题
 */
class XiaomiTTSManager(private val context: Context) {
    
    companion object {
        private const val TAG = "XiaomiTTS"
        private const val REQUEST_TTS_PERMISSIONS = 1001

        // 小米TTS相关权限
        val XIAOMI_TTS_PERMISSIONS = listOf(
            "com.xiaomi.permission.TTS",
            "com.miui.permission.TTS",
            "android.permission.BIND_TTS_SERVICE"
        )
        
        // 小米TTS引擎包名（按优先级排序）
        val XIAOMI_TTS_PACKAGES = mapOf(
            "xiaomi" to listOf(
                "com.xiaomi.mibrain.speech",    // 小爱同学TTS (主要)
                "com.miui.tts",             // MIUI TTS (备用1)
                "com.xiaomi.speech",           // Xiaomi Speech (备用2)
                "com.miui.speech.tts"         // MIUI Speech TTS (备用3)
            ),
            "hyperos" to listOf(
                "com.xiaomi.mibrain.speech",    // HyperOS上的小爱同学TTS
                "com.miui.tts"              // HyperOS上的MIUI TTS
            )
        )
        
        // 小米TTS服务Action
        val XIAOMI_TTS_ACTIONS = mapOf(
            "xiaomi" to mapOf(
                "settings" to "com.xiaomi.speech.action.TTS_SETTINGS",
                "service" to "com.xiaomi.speech.action.TTS_SERVICE",
                "engine" to "com.xiaomi.speech.action.TTS_ENGINE",
                "download" to "com.xiaomi.speech.action.DOWNLOAD_TTS_DATA"
            ),
            "hyperos" to mapOf(
                "settings" to "com.xiaomi.speech.action.TTS_SETTINGS",
                "service" to "com.xiaomi.speech.action.TTS_SERVICE",
                "engine" to "com.xiaomi.speech.action.TTS_ENGINE",
                "download" to "com.xiaomi.speech.action.DOWNLOAD_TTS_DATA"
            )
        )
    }

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var currentEnginePackage: String? = null

    /**
     * 初始化小米TTS管理器
     */
    fun initialize(): Boolean {
        Log.d(TAG, "开始初始化小米TTS管理器")
        
        if (!isXiaomiDevice()) {
            Log.d(TAG, "非小米设备，使用标准TTS初始化")
            return initializeStandardTTS()
        }
        
        Log.d(TAG, "检测到小米设备，使用小米TTS专用流程")
        
        // 1. 检查小米TTS权限
        if (!checkXiaomiTTSPermissions()) {
            Log.w(TAG, "小米TTS权限不足，请求权限")
            requestXiaomiTTSPermissions()
            return false
        }
        
        // 2. 初始化TextToSpeech对象
        initializeXiaomiTTSEngine()
        return isInitialized
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
            Log.e(TAG, "检测设备类型失败", e)
            false
        }
    }
    
    /**
     * 获取小米设备类型
     */
    private fun getXiaomiDeviceType(): String {
        return try {
            val display = Settings.Global.getString(
                context.contentResolver,
                "xiaomi.device.display"
            ) ?: ""
            val normalizedDisplay = display.lowercase()

            when {
                normalizedDisplay.contains("hyperos") -> "hyperos"
                normalizedDisplay.contains("miui") -> "xiaomi"
                getSystemProperty("ro.mi.os.version.name")?.lowercase()?.contains("hyperos") == true -> "hyperos"
                Build.DISPLAY?.lowercase()?.contains("hyperos") == true -> "hyperos"
                else -> "xiaomi"
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取设备类型失败", e)
            if (Build.DISPLAY?.lowercase()?.contains("hyperos") == true) {
                "hyperos"
            } else {
                "xiaomi"
            }
        }
    }

    /**
     * 读取系统属性，兼容HyperOS版本识别
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as? String
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * 检查小米TTS权限
     */
    private fun checkXiaomiTTSPermissions(): Boolean {
        return XIAOMI_TTS_PERMISSIONS.all { permission ->
            when {
                isRuntimePermission(permission) -> {
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
                else -> true // 签名级权限无法动态授予，直接视为已具备
            }
        }
    }

    /**
     * 判断权限是否为运行时权限（HyperOS 2.0对签名权限会直接拒绝）
     */
    private fun isRuntimePermission(permission: String): Boolean {
        return try {
            val permissionInfo = context.packageManager.getPermissionInfo(permission, 0)
            val baseProtection = permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            baseProtection == PermissionInfo.PROTECTION_DANGEROUS
        } catch (e: Exception) {
            Log.d(TAG, "权限未在当前系统公开，视为非运行时权限: $permission")
            false
        }
    }

    /**
     * 请求小米TTS权限
     */
    private fun requestXiaomiTTSPermissions() {
        val runtimePermissions = XIAOMI_TTS_PERMISSIONS.filter { isRuntimePermission(it) }
        if (runtimePermissions.isEmpty()) {
            Log.w(TAG, "小米TTS缺少签名级权限，需由系统预装授予，无法运行时申请")
            return
        }
        // 这里需要上层Activity结合ActivityResultLauncher触发权限申请
        Log.w(TAG, "需要在Activity中请求TTS运行时权限: ${runtimePermissions.joinToString()}")
    }

    /**
     * 初始化小米TTS引擎
     */
    private fun initializeXiaomiTTSEngine(): Boolean {
        val deviceType = getXiaomiDeviceType()
        val packages = XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()

        Log.d(TAG, "设备类型: $deviceType, 可用TTS包: ${packages.joinToString()}")

        if (packages.isEmpty()) {
            Log.e(TAG, "未找到可用的小米TTS引擎包")
            return false
        }

        // 先尝试确保对应语音服务处于运行状态
        packages.firstOrNull()?.let { packageName ->
            if (!startXiaomiTTSService(deviceType, packageName)) {
                Log.w(TAG, "无法显式唤起小米TTS服务，继续尝试绑定")
            }
        }

        tryInitializeNextEngine(deviceType, packages.iterator())
        return false
    }

    /**
     * 递归尝试不同的TTS引擎包，直到成功或全部失败
     */
    private fun tryInitializeNextEngine(deviceType: String, iterator: Iterator<String>) {
        if (!iterator.hasNext()) {
            Log.e(TAG, "所有小米TTS引擎均无法初始化")
            isInitialized = false
            currentEnginePackage = null
            return
        }

        val packageName = iterator.next()
        if (!isPackageInstalled(packageName)) {
            Log.w(TAG, "TTS引擎包未安装: $packageName，尝试下一个")
            tryInitializeNextEngine(deviceType, iterator)
            return
        }

        currentEnginePackage = packageName
        releaseInternalTTS()

        textToSpeech = TextToSpeech(context, { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    Log.d(TAG, "TextToSpeech初始化成功，引擎: $packageName")
                    if (configureXiaomiTTSEngine(packageName)) {
                        isInitialized = true
                        Log.d(TAG, "✅ 小米TTS初始化成功: $packageName")
                    } else {
                        Log.w(TAG, "配置失败，准备尝试下一个引擎")
                        tryInitializeNextEngine(deviceType, iterator)
                    }
                }
                else -> {
                    Log.e(TAG, "TextToSpeech初始化失败，status=$status，引擎: $packageName")
                    tryInitializeNextEngine(deviceType, iterator)
                }
            }
        }, packageName)
    }

    /**
     * 检查目标包是否已安装
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 启动小米TTS服务
     */
    private fun startXiaomiTTSService(deviceType: String, packageName: String): Boolean {
        return try {
            val serviceAction = XIAOMI_TTS_ACTIONS[deviceType]?.get("service")

            if (serviceAction != null) {
                val intent = Intent(serviceAction).apply {
                    setPackage(packageName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "尝试唤起小米TTS服务: $serviceAction ($packageName)")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动小米TTS服务失败", e)
            false
        }
    }
    
    /**
     * 尝试绑定小米TTS引擎
     */
    private fun configureXiaomiTTSEngine(packageName: String): Boolean {
        return try {
            textToSpeech?.apply {
                // 设置中文语言
                val langResult = setLanguage(Locale.CHINA)
                Log.d(TAG, "语言设置结果: $langResult")
                
                if (langResult < TextToSpeech.LANG_AVAILABLE) {
                    Log.w(TAG, "TTS引擎不支持中文: $packageName")
                    return false
                }

                // 设置小米TTS特定参数
                configureXiaomiTTSParameters(packageName)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "配置TTS引擎失败: $packageName", e)
            false
        }
    }
    
    /**
     * 配置小米TTS特定参数
     */
    private fun configureXiaomiTTSParameters(packageName: String) {
        when (packageName) {
            "com.xiaomi.mibrain.speech" -> {
                // 小爱同学TTS特定配置
                setSpeechRate(0.9f)  // 稍微调慢语速
                setPitch(1.1f)     // 稍微提高音调
                Log.d(TAG, "配置小爱同学TTS参数")
            }
            "com.miui.tts", "com.xiaomi.speech", "com.miui.speech.tts" -> {
                // MIUI TTS配置
                setSpeechRate(1.0f)  // 正常语速
                setPitch(1.0f)     // 正常音调
                Log.d(TAG, "配置MIUI TTS参数")
            }
        }
    }
    
    /**
     * 标准TTS初始化（非小米设备）
     */
    private fun initializeStandardTTS(): Boolean {
        Log.d(TAG, "使用标准TTS初始化流程")
        
        var initResult = false
        
        textToSpeech = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    Log.d(TAG, "标准TTS初始化成功")
                    val langResult = textToSpeech?.setLanguage(Locale.CHINA)
                    if (langResult != null && langResult >= TextToSpeech.LANG_AVAILABLE) {
                        isInitialized = true
                        initResult = true
                        Log.d(TAG, "✅ 标准TTS配置完成")
                        testStandardTTS()
                    } else {
                        Log.w(TAG, "标准TTS不支持中文")
                    }
                }
                TextToSpeech.ERROR -> {
                    Log.e(TAG, "标准TTS初始化失败")
                }
            }
        }
        
        // 等待初始化完成
        Thread.sleep(1000)
        return initResult
    }
    
    /**
     * 测试标准TTS
     */
    private fun testStandardTTS() {
        try {
            textToSpeech?.speak("标准语音测试成功", TextToSpeech.QUEUE_FLUSH, null, "standard_test")
            Log.d(TAG, "标准TTS测试播放完成")
        } catch (e: Exception) {
            Log.e(TAG, "标准TTS测试失败", e)
        }
    }
    
    /**
     * 设置语速
     */
    private fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }
    
    /**
     * 设置音调
     */
    private fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
    
    /**
     * 播放语音
     */
    fun speak(text: String) {
        if (isInitialized && textToSpeech != null) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smartdosing")
                Log.d(TAG, "TTS播放: $text")
            } catch (e: Exception) {
                Log.e(TAG, "TTS播放失败", e)
            }
        } else {
            Log.w(TAG, "TTS未初始化，无法播放: $text")
        }
    }
    
    /**
     * 停止语音
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        releaseInternalTTS()
        isInitialized = false
        currentEnginePackage = null
        Log.d(TAG, "小米TTS管理器已释放")
    }

    /**
     * 内部释放TTS实例，供重新初始化时复用
     */
    private fun releaseInternalTTS() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "释放TTS内部实例失败", e)
        } finally {
            textToSpeech = null
        }
    }

    /**
     * 打开小米TTS设置
     */
    fun openXiaomiTTSSettings() {
        try {
            val deviceType = getXiaomiDeviceType()
            val settingsAction = XIAOMI_TTS_ACTIONS[deviceType]?.get("settings")
            
            if (settingsAction != null) {
                val intent = Intent(settingsAction).apply {
                    setPackage(XIAOMI_TTS_PACKAGES[deviceType]?.first())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "打开小米TTS设置")
            } else {
                // 降级到系统TTS设置
                val systemIntent = Intent("com.android.settings.TTS_SETTINGS")
                systemIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(systemIntent)
                Log.d(TAG, "打开系统TTS设置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开TTS设置失败", e)
        }
    }
    
    /**
     * 检查小米TTS数据是否已安装
     */
    fun checkXiaomiTTSDataInstalled(): Boolean {
        return try {
            val deviceType = getXiaomiDeviceType()
            val packages = XIAOMI_TTS_PACKAGES[deviceType] ?: emptyList()
            
            packages.forEach { packageName ->
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                if (packageInfo != null) {
                    Log.d(TAG, "找到小米TTS包: $packageName")
                    return true
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "检查TTS数据失败", e)
            false
        }
    }
    
    /**
     * 获取TTS状态信息
     */
    fun getTTSStatus(): Map<String, Any> {
        return mapOf(
            "isXiaomiDevice" to isXiaomiDevice(),
            "isInitialized" to isInitialized,
            "deviceType" to getXiaomiDeviceType(),
            "enginePackage" to (currentEnginePackage ?: "unknown"),
            "permissionsGranted" to if (isXiaomiDevice()) checkXiaomiTTSPermissions() else true,
            "dataInstalled" to if (isXiaomiDevice()) checkXiaomiTTSDataInstalled() else true
        )
    }
}
