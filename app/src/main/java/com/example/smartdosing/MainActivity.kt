package com.example.smartdosing

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.navigation.SmartDosingNavHost
import com.example.smartdosing.tts.TTSManagerFactory
import com.example.smartdosing.tts.XiaomiTTSSettingsHelper
import com.example.smartdosing.ui.components.SmartDosingBottomNavigationBar
import com.example.smartdosing.ui.components.SmartDosingNavigationRail
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingTokens
import com.example.smartdosing.web.WebService
import com.example.smartdosing.web.WebServiceResult
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var webService: WebService
    private var testTts: TextToSpeech? = null
    private lateinit var ttsSettingsHelper: XiaomiTTSSettingsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Web Service
        webService = WebService.getInstance(this)

        // 初始化小米TTS管理器
        initializeXiaomiTTS()

        // 验证数据库初始化
        testDatabaseInitialization()

        enableEdgeToEdge()
        setContent {
            SmartDosingTheme {
                SmartDosingApp()
            }
        }

        // Start Web Service
        startWebService()
    }

    /**
     * 初始化TTS管理器
     */
    private fun initializeXiaomiTTS() {
        Log.d("TTS", "=== 开始初始化TTS管理器工厂 ===")
        
        try {
            // 初始化TTS设置助手
            ttsSettingsHelper = XiaomiTTSSettingsHelper(this)
            
            // 检查TTS可用性
            val ttsStatus = ttsSettingsHelper.checkTTSAvailability()
            Log.d("TTS", "TTS状态检查结果: $ttsStatus")
            
            // 如果TTS不可用，显示设置对话框
            if (!ttsStatus.canUseTTS) {
                Log.w("TTS", "TTS不可用，显示设置对话框")
                showTTSSettingsDialog()
            }
            
            // 使用TTS管理器工厂初始化
            if (TTSManagerFactory.initialize(this)) {
                val ttsType = TTSManagerFactory.getCurrentTTSType()
                Log.d("TTS", "✅ TTS管理器工厂初始化成功，类型: $ttsType")
                showToast("TTS语音功能已就绪 ($ttsType)")
                
                // 测试TTS功能
                TTSManagerFactory.testTTS(this)
            } else {
                Log.w("TTS", "⚠️ TTS管理器工厂初始化失败")
                showToast("TTS功能初始化失败，请检查设置")
            }
            
        } catch (e: Exception) {
            Log.e("TTS", "❌ TTS管理器工厂初始化异常", e)
            showToast("TTS初始化异常: ${e.message}")
        }
    }
    
    /**
     * 显示TTS设置对话框
     */
    private fun showTTSSettingsDialog() {
        try {
            ttsSettingsHelper.showTTSSettingsOptions(this)
        } catch (e: Exception) {
            Log.e("TTS", "显示TTS设置对话框失败", e)
            showToast("请手动检查TTS设置")
        }
    }
    
    /**
     * 备用TTS初始化
     */
    private fun initializeFallbackTTS() {
        Log.d("TTS", "使用备用TTS初始化方案")
        
        testTts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e("TTS", "❌ 备用TTS初始化失败: $status")
                showToast("TTS功能不可用，请检查设备设置")
                return@TextToSpeech
            }
            
            Log.d("TTS", "✅ 备用TTS初始化成功")
            
            // 尝试设置中文语言
            val langResult = testTts?.setLanguage(Locale.CHINA)
            if (langResult != null && langResult >= TextToSpeech.LANG_AVAILABLE) {
                testTts?.speak("智能投料系统启动完成", TextToSpeech.QUEUE_FLUSH, null, "fallback_test")
                showToast("TTS功能已启用（备用方案）")
            } else {
                Log.w("TTS", "备用TTS不支持中文")
                showToast("TTS不支持中文语言")
            }
        }
    }
    
    /**
     * 获取TTS设置助手实例（供其他组件使用）
     */
    fun getTTSSettingsHelper(): XiaomiTTSSettingsHelper? {
        return if (::ttsSettingsHelper.isInitialized) ttsSettingsHelper else null
    }
    
    /**
     * 播放TTS语音（供其他组件使用）
     */
    fun speakTTS(text: String) {
        TTSManagerFactory.speak(text)
    }
    
    /**
     * 停止TTS语音（供其他组件使用）
     */
    fun stopTTS() {
        TTSManagerFactory.stopSpeaking()
    }

    /**
     * 检查设备上可用的TTS引擎 - 诊断用
     */
    private fun checkAvailableTTSEngines() {
        try {
            Log.d("TTSTest", "=== 开始检查设备上可用的TTS引擎 ===")

            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val activities = packageManager.queryIntentActivities(intent, 0)

            Log.d("TTSTest", "找到 ${activities.size} 个TTS引擎:")
            activities.forEach { activity ->
                val packageName = activity.activityInfo.packageName
                val appName = activity.loadLabel(packageManager).toString()
                Log.d("TTSTest", "  - $appName ($packageName)")
            }

            // 特别检查小米相关的包
            val xiaomiPackages = listOf(
                "com.xiaomi.mibrain.speech",
                "com.miui.tts",
                "com.xiaomi.speech",
                "com.miui.speech.tts"
            )

            Log.d("TTSTest", "\n=== 检查小米TTS包是否已安装 ===")
            xiaomiPackages.forEach { packageName ->
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    Log.d("TTSTest", "✅ 已安装: $packageName")
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    Log.w("TTSTest", "❌ 未安装: $packageName")
                }
            }

            Log.d("TTSTest", "=== 引擎检查完成 ===\n")
        } catch (e: Exception) {
            Log.e("TTSTest", "检查TTS引擎时出错", e)
        }
    }

    /**
     * 尝试小米自带小爱TTS测试 - 增强版
     * 支持多个小米TTS引擎包名
     */
    private fun tryXiaoAiTTSTest(): Boolean {
        // 小米设备可能的TTS引擎包名（按优先级排列）
        val xiaomiEngines = listOf(
            "com.xiaomi.mibrain.speech",          // XiaoAi TTS (主要)
            "com.miui.tts",                       // MIUI TTS (备用1)
            "com.xiaomi.speech",                  // Xiaomi Speech (备用2)
            "com.miui.speech.tts"                 // MIUI Speech TTS (备用3)
        )

        Log.d("TTSTest", "=== 开始尝试小米自带TTS引擎 ===")

        xiaomiEngines.forEach { enginePackage ->
            try {
                Log.d("TTSTest", "尝试引擎: $enginePackage")

                val result = testTts?.setEngineByPackageName(enginePackage)
                Log.d("TTSTest", "引擎绑定结果: $result")

                if (result == TextToSpeech.SUCCESS) {
                    Log.d("TTSTest", "✅ 成功绑定小米TTS引擎: $enginePackage")

                    testTts?.apply {
                        // 延迟一下，确保引擎完全切换
                        try {
                            Thread.sleep(300) // 300ms延迟
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        // 设置中文语言
                        val langResult = setLanguage(Locale.CHINA)
                        Log.d("TTSTest", "语言设置结果: $langResult")

                        if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                            setSpeechRate(1.0f)
                            speak("小爱语音已就绪，智能投料系统启动完成", TextToSpeech.QUEUE_FLUSH, null, null)

                            showToast("小米TTS功能正常 - 引擎: $enginePackage")
                            Log.d("TTSTest", "✅ 小爱TTS测试完成 - 引擎: $enginePackage")
                            return true
                        } else {
                            Log.w("TTSTest", "⚠️ 引擎 $enginePackage 不支持中文，继续尝试下一个")
                            return@forEach
                        }
                    }
                } else {
                    Log.w("TTSTest", "⚠️ 引擎绑定失败: $enginePackage (结果: $result)")
                    return@forEach
                }
            } catch (e: Exception) {
                Log.e("TTSTest", "❌ 引擎 $enginePackage 配置异常", e)
                return@forEach
            }
        }

        Log.e("TTSTest", "❌ 所有小米TTS引擎都无法使用")
        return false
    }

    /**
     * 尝试Google TTS（HyperOS优化）测试
     */
    private fun tryGoogleTTSHyperOSTest(): Boolean {
        try {
            Log.d("TTSTest", "尝试Google TTS（HyperOS优化）")

            // 这行是小米 HyperOS 的"开挂神句"，必须加！
            val result = testTts?.setEngineByPackageName("com.google.android.tts")
            if (result == TextToSpeech.SUCCESS) {
                Log.d("TTSTest", "✅ 成功强制使用 Google 原生 TTS")

                testTts?.apply {
                    setLanguage(Locale.CHINA)           // 中文
                    setSpeechRate(1.0f)                 // 语速正常
                    speak("Google语音已就绪，智能投料系统启动完成", TextToSpeech.QUEUE_FLUSH, null, null)

                    showToast("Google TTS（HyperOS优化）功能正常")
                    Log.d("TTSTest", "✅ Google TTS HyperOS优化测试完成")
                }
                return true
            } else {
                Log.e("TTSTest", "❌ Google TTS HyperOS优化失败: $result")
                return false
            }
        } catch (e: Exception) {
            Log.e("TTSTest", "❌ Google TTS HyperOS优化异常", e)
            return false
        }
    }

    /**
     * 标准TTS初始化方式（备用）
     */
    private fun fallbackToStandardTTS() {
        val ttsInstance = testTts ?: return

        try {
            Log.d("TTSTest", "尝试标准TTS配置方式")
            val langResult = ttsInstance.setLanguage(Locale.CHINA)
            if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                Log.d("TTSTest", "✅ 标准TTS配置成功")
                ttsInstance.setSpeechRate(1.0f)
                ttsInstance.speak("智能投料系统启动完成，使用标准TTS", TextToSpeech.QUEUE_FLUSH, null, null)
                showToast("标准TTS功能正常")
            } else {
                Log.e("TTSTest", "❌ 标准TTS语言设置失败")
                showToast("TTS语言设置失败")
            }
        } catch (e: Exception) {
            Log.e("TTSTest", "❌ 标准TTS配置异常", e)
            showToast("TTS配置异常: ${e.message}")
        }
    }

    /**
     * 测试数据库初始化
     */
    private fun testDatabaseInitialization() {
        lifecycleScope.launch {
            try {
                Log.d("DBTest", "=== 开始数据库初始化测试 ===")

                val repository = DatabaseRecipeRepository.getInstance(this@MainActivity)

                // 测试1: 获取所有配方
                val recipes = repository.getAllRecipes()
                Log.d("DBTest", "✅ 数据库已初始化，配方总数: ${recipes.size}")

                // 测试2: 显示配方详情
                recipes.forEachIndexed { index, recipe ->
                    Log.d("DBTest", "配方${index + 1}: ${recipe.name}")
                    Log.d("DBTest", "  - 编码: ${recipe.code}")
                    Log.d("DBTest", "  - 分类: ${recipe.category}")
                    Log.d("DBTest", "  - 客户: ${recipe.customer}")
                    Log.d("DBTest", "  - 材料数: ${recipe.materials.size}")
                    Log.d("DBTest", "  - 总重量: ${recipe.totalWeight}g")
                }

                // 测试3: 获取统计数据
                val stats = repository.getRecipeStats()
                Log.d("DBTest", "\n✅ 统计数据:")
                Log.d("DBTest", "  - 总配方数: ${stats.totalRecipes}")
                Log.d("DBTest", "  - 分类统计: ${stats.categoryCounts}")
                Log.d("DBTest", "  - 客户统计: ${stats.customerCounts}")

                // 测试4: 测试搜索功能
                val searchResults = repository.searchRecipes("苹果")
                Log.d("DBTest", "\n✅ 搜索'苹果'结果: ${searchResults.size}条")

                Log.d("DBTest", "=== 数据库初始化测试完成 ===\n")

                // 运行CRUD测试
                testCRUDOperations(repository)

                showToast("数据库初始化成功！配方数: ${recipes.size}")
            } catch (e: Exception) {
                Log.e("DBTest", "❌ 数据库初始化测试失败", e)
                showToast("数据库初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 测试CRUD操作
     */
    private suspend fun testCRUDOperations(repository: DatabaseRecipeRepository) {
        try {
            Log.d("DBTest", "\n=== 开始CRUD操作测试 ===")

            // CREATE测试
            Log.d("DBTest", "\n1. CREATE测试 - 创建新配方")
            val testRecipe = com.example.smartdosing.data.RecipeImportRequest(
                code = "TEST001",
                name = "测试配方-自动创建",
                category = "测试分类",
                subCategory = "测试子分类",
                customer = "测试客户",
                batchNo = "TEST-BATCH-001",
                version = "1.0",
                description = "这是一个自动创建的测试配方",
                materials = listOf(
                    com.example.smartdosing.data.MaterialImport(
                        name = "测试材料A",
                        weight = 100.0,
                        unit = "g",
                        sequence = 1,
                        notes = "主要材料"
                    ),
                    com.example.smartdosing.data.MaterialImport(
                        name = "测试材料B",
                        weight = 50.0,
                        unit = "g",
                        sequence = 2,
                        notes = "辅助材料"
                    )
                ),
                status = com.example.smartdosing.data.RecipeStatus.ACTIVE,
                priority = com.example.smartdosing.data.RecipePriority.NORMAL,
                tags = listOf("测试", "自动化"),
                creator = "系统测试",
                reviewer = ""
            )

            val created = repository.addRecipe(testRecipe)
            Log.d("DBTest", "✅ CREATE成功:")
            Log.d("DBTest", "  - ID: ${created.id}")
            Log.d("DBTest", "  - 名称: ${created.name}")
            Log.d("DBTest", "  - 材料数: ${created.materials.size}")

            // READ测试
            Log.d("DBTest", "\n2. READ测试 - 读取刚创建的配方")
            val found = repository.getRecipeById(created.id)
            if (found != null) {
                Log.d("DBTest", "✅ READ成功:")
                Log.d("DBTest", "  - 找到配方: ${found.name}")
                Log.d("DBTest", "  - 编码: ${found.code}")
                Log.d("DBTest", "  - 材料: ${found.materials.joinToString { it.name }}")
            } else {
                Log.e("DBTest", "❌ READ失败: 配方未找到")
            }

            // UPDATE测试
            Log.d("DBTest", "\n3. UPDATE测试 - 更新配方信息")
            val updateRequest = testRecipe.copy(
                name = "测试配方-已更新",
                description = "这个配方已经被更新过了",
                materials = listOf(
                    com.example.smartdosing.data.MaterialImport(
                        name = "更新后材料A",
                        weight = 120.0,
                        unit = "g",
                        sequence = 1
                    ),
                    com.example.smartdosing.data.MaterialImport(
                        name = "更新后材料B",
                        weight = 60.0,
                        unit = "g",
                        sequence = 2
                    ),
                    com.example.smartdosing.data.MaterialImport(
                        name = "新增材料C",
                        weight = 30.0,
                        unit = "g",
                        sequence = 3
                    )
                )
            )

            val updated = repository.updateRecipe(created.id, updateRequest)
            if (updated != null) {
                Log.d("DBTest", "✅ UPDATE成功:")
                Log.d("DBTest", "  - 新名称: ${updated.name}")
                Log.d("DBTest", "  - 新描述: ${updated.description}")
                Log.d("DBTest", "  - 材料数: ${updated.materials.size}")
                Log.d("DBTest", "  - 总重量: ${updated.totalWeight}g")
            } else {
                Log.e("DBTest", "❌ UPDATE失败")
            }

            // 搜索测试
            Log.d("DBTest", "\n4. SEARCH测试 - 搜索测试配方")
            val searchResult = repository.searchRecipes("测试配方")
            Log.d("DBTest", "✅ SEARCH成功: 找到 ${searchResult.size} 条结果")
            searchResult.forEach {
                Log.d("DBTest", "  - ${it.name}")
            }

            // 按分类查询测试
            Log.d("DBTest", "\n5. QUERY测试 - 按分类查询")
            val categoryResult = repository.getRecipesByCategory("测试分类")
            Log.d("DBTest", "✅ QUERY成功: '测试分类'有 ${categoryResult.size} 条配方")

            // 统计测试
            Log.d("DBTest", "\n6. STATS测试 - 获取最新统计")
            val newStats = repository.getRecipeStats()
            Log.d("DBTest", "✅ STATS成功:")
            Log.d("DBTest", "  - 总配方数: ${newStats.totalRecipes}")
            Log.d("DBTest", "  - 分类数: ${newStats.categoryCounts.size}")

            // DELETE测试
            Log.d("DBTest", "\n7. DELETE测试 - 删除测试配方")
            val deleted = repository.deleteRecipe(created.id)
            Log.d("DBTest", "✅ DELETE成功: $deleted")

            // 验证删除
            val shouldBeNull = repository.getRecipeById(created.id)
            if (shouldBeNull == null) {
                Log.d("DBTest", "✅ DELETE验证成功: 配方已被删除")
            } else {
                Log.e("DBTest", "❌ DELETE验证失败: 配方仍然存在")
            }

            Log.d("DBTest", "\n=== CRUD操作测试全部通过 ===\n")

        } catch (e: Exception) {
            Log.e("DBTest", "❌ CRUD测试失败", e)
        }
    }

    private fun startWebService() {
        lifecycleScope.launch {
            when (val result = webService.startWebService()) {
                is WebServiceResult.Success -> {
                    showToast("Web管理后台已启动: ${result.serverUrl}")
                }
                is WebServiceResult.AlreadyRunning -> {
                    // Optionally show a toast, or just ignore
                }
                is WebServiceResult.NetworkError -> {
                    showToast("网络错误: ${result.message}")
                }
                is WebServiceResult.StartFailed -> {
                    showToast("Web服务启动失败: ${result.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webService.stopWebService()
        // 清理TTS资源
        testTts?.stop()
        testTts?.shutdown()
        testTts = null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SmartDosingApp() {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val useNavigationRail = screenWidthDp >= 720

    if (useNavigationRail) {
        SmartDosingLargeScreenLayout(navController)
    } else {
        SmartDosingBottomBarLayout(navController)
    }
}

@Composable
private fun SmartDosingBottomBarLayout(navController: NavHostController) {
    Scaffold(
        bottomBar = {
            SmartDosingBottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun SmartDosingLargeScreenLayout(navController: NavHostController) {
    val railWidth = 96.dp
    val dividerWidth = 1.dp
    val outerPadding = SmartDosingTokens.spacing.lg

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail
        SmartDosingNavigationRail(
            navController = navController,
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
        )

        // Divider
        VerticalDivider(
            modifier = Modifier
                .width(dividerWidth)
                .fillMaxHeight()
        )

        // Main content
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(outerPadding)
        )
    }
}
