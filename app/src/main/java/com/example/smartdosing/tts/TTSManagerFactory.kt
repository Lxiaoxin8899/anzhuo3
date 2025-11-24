package com.example.smartdosing.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS管理器工厂类
 * 统一管理TTS实例，提供全局访问点
 */
object TTSManagerFactory {
    
    private const val TAG = "TTSManagerFactory"
    
    private var xiaomiTTSManager: XiaomiTTSManager? = null
    private var fallbackTTS: TextToSpeech? = null
    private var isInitialized = false
    private var currentTTSType = TTSType.NONE
    
    enum class TTSType {
        NONE,
        XIAOMI_TTS,
        FALLBACK_TTS
    }
    
    /**
     * 初始化TTS管理器
     */
    fun initialize(context: Context): Boolean {
        Log.d(TAG, "开始初始化TTS管理器工厂")
        
        return try {
            // 优先尝试小米TTS
            xiaomiTTSManager = XiaomiTTSManager(context)
            if (xiaomiTTSManager?.initialize() == true) {
                currentTTSType = TTSType.XIAOMI_TTS
                isInitialized = true
                Log.d(TAG, "✅ 小米TTS管理器初始化成功")
                return true
            }
            
            // 降级到标准TTS
            Log.w(TAG, "小米TTS初始化失败，尝试标准TTS")
            if (initializeFallbackTTS(context)) {
                currentTTSType = TTSType.FALLBACK_TTS
                isInitialized = true
                Log.d(TAG, "✅ 标准TTS初始化成功")
                return true
            }
            
            Log.e(TAG, "❌ 所有TTS初始化失败")
            false
        } catch (e: Exception) {
            Log.e(TAG, "TTS管理器工厂初始化异常", e)
            false
        }
    }
    
    /**
     * 初始化备用TTS
     */
    private fun initializeFallbackTTS(context: Context): Boolean {
        return try {
            var initResult = false
            
            fallbackTTS = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val langResult = fallbackTTS?.setLanguage(Locale.CHINA)
                    if (langResult != null && langResult >= TextToSpeech.LANG_AVAILABLE) {
                        initResult = true
                        Log.d(TAG, "备用TTS语言设置成功")
                    } else {
                        Log.w(TAG, "备用TTS不支持中文")
                    }
                } else {
                    Log.e(TAG, "备用TTS初始化失败: $status")
                }
            }
            
            // 等待初始化完成
            Thread.sleep(1000)
            initResult
        } catch (e: Exception) {
            Log.e(TAG, "备用TTS初始化异常", e)
            false
        }
    }
    
    /**
     * 获取当前TTS类型
     */
    fun getCurrentTTSType(): TTSType {
        return currentTTSType
    }
    
    /**
     * 播放语音
     */
    fun speak(text: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS未初始化，无法播放: $text")
            return
        }
        
        when (currentTTSType) {
            TTSType.XIAOMI_TTS -> {
                xiaomiTTSManager?.speak(text)
            }
            TTSType.FALLBACK_TTS -> {
                try {
                    fallbackTTS?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "factory_fallback")
                } catch (e: Exception) {
                    Log.e(TAG, "备用TTS播放失败", e)
                }
            }
            TTSType.NONE -> {
                Log.w(TAG, "没有可用的TTS引擎")
            }
        }
    }
    
    /**
     * 停止语音
     */
    fun stopSpeaking() {
        when (currentTTSType) {
            TTSType.XIAOMI_TTS -> {
                xiaomiTTSManager?.stopSpeaking()
            }
            TTSType.FALLBACK_TTS -> {
                fallbackTTS?.stop()
            }
            TTSType.NONE -> {
                // 无操作
            }
        }
    }
    
    /**
     * 获取小米TTS管理器
     */
    fun getXiaomiTTSManager(): XiaomiTTSManager? {
        return xiaomiTTSManager
    }
    
    /**
     * 获取备用TTS实例
     */
    fun getFallbackTTS(): TextToSpeech? {
        return fallbackTTS
    }
    
    /**
     * 检查TTS是否可用
     */
    fun isTTSAvailable(): Boolean {
        return isInitialized && currentTTSType != TTSType.NONE
    }
    
    /**
     * 获取TTS状态信息
     */
    fun getTTSStatus(): Map<String, Any> {
        return mapOf(
            "isInitialized" to isInitialized,
            "currentType" to currentTTSType.name,
            "xiaomiTTSStatus" to (xiaomiTTSManager?.getTTSStatus() ?: emptyMap<String, Any>()),
            "fallbackTTSAvailable" to (fallbackTTS != null)
        )
    }
    
    /**
     * 释放所有TTS资源
     */
    fun release() {
        Log.d(TAG, "释放TTS管理器工厂资源")
        
        try {
            xiaomiTTSManager?.release()
            xiaomiTTSManager = null
            
            fallbackTTS?.stop()
            fallbackTTS?.shutdown()
            fallbackTTS = null
            
            isInitialized = false
            currentTTSType = TTSType.NONE
            
            Log.d(TAG, "TTS管理器工厂资源释放完成")
        } catch (e: Exception) {
            Log.e(TAG, "释放TTS资源失败", e)
        }
    }
    
    /**
     * 重新初始化TTS
     */
    fun reinitialize(context: Context): Boolean {
        Log.d(TAG, "重新初始化TTS管理器工厂")
        
        release()
        return initialize(context)
    }
    
    /**
     * 测试TTS功能
     */
    fun testTTS(context: Context): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "TTS未初始化，无法测试")
            return false
        }
        
        return try {
            val testText = when (currentTTSType) {
                TTSType.XIAOMI_TTS -> "小米TTS测试成功"
                TTSType.FALLBACK_TTS -> "标准TTS测试成功"
                TTSType.NONE -> "无TTS可用"
            }
            
            speak(testText)
            Log.d(TAG, "TTS测试完成: $testText")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TTS测试失败", e)
            false
        }
    }
}