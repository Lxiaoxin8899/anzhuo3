package com.example.smartdosing.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TTS测试运行器
 * 用于测试和验证小米TTS解决方案
 */
class TTSTestRunner(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSTestRunner"
        
        // 测试用例
        val TEST_CASES = listOf(
            "小米TTS测试开始",
            "智能投料系统启动完成",
            "配方加载成功",
            "开始投料操作",
            "投料完成，请检查结果",
            "系统运行正常",
            "测试结束"
        )
    }
    
    /**
     * 运行完整的TTS测试套件
     */
    fun runCompleteTestSuite(): TestResult {
        Log.d(TAG, "=== 开始运行完整TTS测试套件 ===")
        
        val results = mutableListOf<SingleTestResult>()
        
        try {
            // 1. 初始化测试
            val initResult = testInitialization()
            results.add(initResult)
            
            if (!initResult.success) {
                Log.e(TAG, "初始化失败，停止测试")
                return TestResult(false, results, "初始化失败")
            }
            
            // 2. TTS类型检测测试
            val typeResult = testTTSTypeDetection()
            results.add(typeResult)
            
            // 3. 基础语音播放测试
            val basicResult = testBasicSpeech()
            results.add(basicResult)
            
            // 4. 连续语音播放测试
            val continuousResult = testContinuousSpeech()
            results.add(continuousResult)
            
            // 5. 停止和恢复测试
            val stopResumeResult = testStopAndResume()
            results.add(stopResumeResult)
            
            // 6. 错误处理测试
            val errorResult = testErrorHandling()
            results.add(errorResult)
            
            // 7. 资源清理测试
            val cleanupResult = testResourceCleanup()
            results.add(cleanupResult)
            
            // 计算总体结果
            val allPassed = results.all { it.success }
            val message = if (allPassed) "所有测试通过" else "部分测试失败"
            
            Log.d(TAG, "=== TTS测试套件完成 ===")
            Log.d(TAG, "总体结果: $message")
            results.forEach { result ->
                Log.d(TAG, "- ${result.name}: ${if (result.success) "✅ 通过" else "❌ 失败"} - ${result.message}")
            }
            
            return TestResult(allPassed, results, message)
            
        } catch (e: Exception) {
            Log.e(TAG, "TTS测试套件执行异常", e)
            return TestResult(false, results, "测试执行异常: ${e.message}")
        }
    }
    
    /**
     * 测试TTS初始化
     */
    private fun testInitialization(): SingleTestResult {
        Log.d(TAG, "测试TTS初始化")
        
        return try {
            val success = TTSManagerFactory.initialize(context)
            val message = if (success) {
                val type = TTSManagerFactory.getCurrentTTSType()
                "初始化成功，TTS类型: $type"
            } else {
                "初始化失败"
            }
            
            SingleTestResult("TTS初始化", success, message)
        } catch (e: Exception) {
            Log.e(TAG, "初始化测试异常", e)
            SingleTestResult("TTS初始化", false, "初始化异常: ${e.message}")
        }
    }
    
    /**
     * 测试TTS类型检测
     */
    private fun testTTSTypeDetection(): SingleTestResult {
        Log.d(TAG, "测试TTS类型检测")
        
        return try {
            val type = TTSManagerFactory.getCurrentTTSType()
            val status = TTSManagerFactory.getTTSStatus()
            val success = type != TTSManagerFactory.TTSType.NONE
            
            val message = "检测到TTS类型: $type, 状态: $status"
            SingleTestResult("TTS类型检测", success, message)
        } catch (e: Exception) {
            Log.e(TAG, "类型检测测试异常", e)
            SingleTestResult("TTS类型检测", false, "类型检测异常: ${e.message}")
        }
    }
    
    /**
     * 测试基础语音播放
     */
    private fun testBasicSpeech(): SingleTestResult {
        Log.d(TAG, "测试基础语音播放")
        
        return try {
            if (!TTSManagerFactory.isTTSAvailable()) {
                return SingleTestResult("基础语音播放", false, "TTS不可用")
            }
            
            val testText = "基础语音播放测试"
            TTSManagerFactory.speak(testText)
            
            // 等待播放完成
            Thread.sleep(2000)
            
            SingleTestResult("基础语音播放", true, "播放测试文本: $testText")
        } catch (e: Exception) {
            Log.e(TAG, "基础语音播放测试异常", e)
            SingleTestResult("基础语音播放", false, "播放异常: ${e.message}")
        }
    }
    
    /**
     * 测试连续语音播放
     */
    private fun testContinuousSpeech(): SingleTestResult {
        Log.d(TAG, "测试连续语音播放")
        
        return try {
            if (!TTSManagerFactory.isTTSAvailable()) {
                return SingleTestResult("连续语音播放", false, "TTS不可用")
            }
            
            var successCount = 0
            val totalCount = TEST_CASES.size
            
            TEST_CASES.forEach { text ->
                try {
                    TTSManagerFactory.speak(text)
                    Thread.sleep(1500) // 等待播放
                    successCount++
                } catch (e: Exception) {
                    Log.w(TAG, "播放失败: $text", e)
                }
            }
            
            val success = successCount == totalCount
            val message = "成功播放 $successCount/$totalCount 个测试用例"
            
            SingleTestResult("连续语音播放", success, message)
        } catch (e: Exception) {
            Log.e(TAG, "连续语音播放测试异常", e)
            SingleTestResult("连续语音播放", false, "连续播放异常: ${e.message}")
        }
    }
    
    /**
     * 测试停止和恢复
     */
    private fun testStopAndResume(): SingleTestResult {
        Log.d(TAG, "测试停止和恢复")
        
        return try {
            if (!TTSManagerFactory.isTTSAvailable()) {
                return SingleTestResult("停止和恢复", false, "TTS不可用")
            }
            
            // 开始播放
            TTSManagerFactory.speak("停止和恢复测试开始")
            Thread.sleep(500)
            
            // 停止播放
            TTSManagerFactory.stopSpeaking()
            Thread.sleep(500)
            
            // 恢复播放
            TTSManagerFactory.speak("停止和恢复测试完成")
            Thread.sleep(1500)
            
            SingleTestResult("停止和恢复", true, "停止和恢复功能正常")
        } catch (e: Exception) {
            Log.e(TAG, "停止和恢复测试异常", e)
            SingleTestResult("停止和恢复", false, "停止恢复异常: ${e.message}")
        }
    }
    
    /**
     * 测试错误处理
     */
    private fun testErrorHandling(): SingleTestResult {
        Log.d(TAG, "测试错误处理")
        
        return try {
            // 测试空文本
            TTSManagerFactory.speak("")
            Thread.sleep(500)
            
            // 测试超长文本
            val longText = "这是一个非常长的测试文本".repeat(10)
            TTSManagerFactory.speak(longText)
            Thread.sleep(1000)
            
            // 测试特殊字符
            TTSManagerFactory.speak("特殊字符测试: @#$%^&*()")
            Thread.sleep(1000)
            
            SingleTestResult("错误处理", true, "错误处理测试完成")
        } catch (e: Exception) {
            Log.e(TAG, "错误处理测试异常", e)
            SingleTestResult("错误处理", false, "错误处理异常: ${e.message}")
        }
    }
    
    /**
     * 测试资源清理
     */
    private fun testResourceCleanup(): SingleTestResult {
        Log.d(TAG, "测试资源清理")
        
        return try {
            // 重新初始化
            TTSManagerFactory.reinitialize(context)
            
            // 播放测试
            TTSManagerFactory.speak("资源清理测试")
            Thread.sleep(1000)
            
            // 清理资源
            TTSManagerFactory.release()
            
            // 验证清理结果
            val isAvailable = TTSManagerFactory.isTTSAvailable()
            val success = !isAvailable // 清理后应该不可用
            
            val message = if (success) "资源清理成功" else "资源清理失败"
            SingleTestResult("资源清理", success, message)
        } catch (e: Exception) {
            Log.e(TAG, "资源清理测试异常", e)
            SingleTestResult("资源清理", false, "资源清理异常: ${e.message}")
        }
    }
    
    /**
     * 运行快速测试
     */
    fun runQuickTest(): SingleTestResult {
        Log.d(TAG, "运行快速TTS测试")
        
        return try {
            // 初始化
            if (!TTSManagerFactory.initialize(context)) {
                return SingleTestResult("快速测试", false, "初始化失败")
            }
            
            // 播放测试
            TTSManagerFactory.testTTS(context)
            
            SingleTestResult("快速测试", true, "快速测试完成")
        } catch (e: Exception) {
            Log.e(TAG, "快速测试异常", e)
            SingleTestResult("快速测试", false, "快速测试异常: ${e.message}")
        }
    }
    
    /**
     * 异步运行测试
     */
    fun runTestAsync(callback: (TestResult) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCompleteTestSuite()
            callback(result)
        }
    }
    
    /**
     * 单个测试结果
     */
    data class SingleTestResult(
        val name: String,
        val success: Boolean,
        val message: String
    )
    
    /**
     * 完整测试结果
     */
    data class TestResult(
        val success: Boolean,
        val results: List<SingleTestResult>,
        val message: String
    )
}