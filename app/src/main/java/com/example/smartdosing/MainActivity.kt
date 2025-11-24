package com.example.smartdosing

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.navigation.SmartDosingNavHost
import com.example.smartdosing.ui.components.SmartDosingBottomNavigationBar
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.web.WebService
import com.example.smartdosing.web.WebServiceResult
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var webService: WebService
    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Web Service
        webService = WebService.getInstance(this)

        // 测试TTS功能
        testTTSFunctionality()

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
     * 测试TTS功能
     */
    private fun testTTSFunctionality() {
        Log.d("TTSTest", "开始测试TTS功能")

        // 首先检查TTS引擎可用性
        checkTTSEngineAvailability()

        testTts = TextToSpeech(this) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    Log.d("TTSTest", "✅ TTS初始化成功")
                    configureTTSSettings()
                }
                TextToSpeech.ERROR -> {
                    Log.e("TTSTest", "❌ TTS初始化失败 - ERROR状态")
                    showToast("TTS初始化失败，请检查设备TTS设置")
                    tryAlternativeTTSApproach()
                }
                else -> {
                    Log.w("TTSTest", "⚠️ TTS状态未知: $status")
                    showToast("TTS状态异常: $status")
                }
            }
        }
    }

    /**
     * 检查TTS引擎可用性
     */
    private fun checkTTSEngineAvailability() {
        try {
            val packageManager = packageManager
            val ttsIntent = android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val ttsEngines = packageManager.queryIntentActivities(ttsIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)

            Log.d("TTSTest", "可用TTS引擎数量: ${ttsEngines.size}")
            for (engine in ttsEngines) {
                Log.d("TTSTest", "TTS引擎: ${engine.activityInfo.packageName}")
            }

            if (ttsEngines.isEmpty()) {
                Log.e("TTSTest", "❌ 没有可用的TTS引擎")
                showToast("设备没有安装TTS引擎，请到设置中安装")
            }
        } catch (e: Exception) {
            Log.e("TTSTest", "检查TTS引擎时出错", e)
        }
    }

    /**
     * 配置TTS设置
     */
    private fun configureTTSSettings() {
        testTts?.apply {
            // 尝试多种语言设置
            val languages = listOf(
                Locale.CHINA,
                Locale.CHINESE,
                Locale("zh", "CN"),
                Locale.getDefault(),
                Locale.ENGLISH
            )

            var languageSet = false
            for (locale in languages) {
                val result = setLanguage(locale)
                when (result) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                        Log.d("TTSTest", "✅ 语言设置成功: $locale")
                        languageSet = true
                        break
                    }
                    TextToSpeech.LANG_MISSING_DATA -> {
                        Log.w("TTSTest", "⚠️ 语言数据缺失: $locale")
                    }
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.w("TTSTest", "⚠️ 语言不支持: $locale")
                    }
                }
            }

            if (!languageSet) {
                Log.e("TTSTest", "❌ 所有语言设置都失败")
                showToast("TTS语言设置失败，可能需要下载语音数据包")
                return
            }

            // 设置语音参数 - 针对小米设备优化
            setSpeechRate(1.0f)  // 标准语速
            setPitch(1.0f)       // 标准音调

            // 尝试设置音频属性
            try {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(android.media.AudioManager.STREAM_MUSIC)
                    .build()
                setAudioAttributes(audioAttributes)
                Log.d("TTSTest", "✅ 音频属性设置成功")
            } catch (e: Exception) {
                Log.w("TTSTest", "⚠️ 音频属性设置失败", e)
            }

            // 测试播放
            testVoicePlayback()
        }
    }

    /**
     * 测试语音播放
     */
    private fun testVoicePlayback() {
        try {
            val testText = "语音测试，智能投料系统启动完成"
            val result = testTts?.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test")

            when (result) {
                TextToSpeech.SUCCESS -> {
                    Log.d("TTSTest", "✅ 语音播放命令发送成功")
                    showToast("TTS功能正常，可以进行语音播报")
                }
                TextToSpeech.ERROR -> {
                    Log.e("TTSTest", "❌ 语音播放命令失败")
                    showToast("TTS播放失败，请检查音量设置")
                }
                else -> {
                    Log.w("TTSTest", "⚠️ 语音播放结果未知: $result")
                    showToast("TTS播放状态异常")
                }
            }
        } catch (e: Exception) {
            Log.e("TTSTest", "语音测试异常", e)
            showToast("语音测试出错: ${e.message}")
        }
    }

    /**
     * 尝试替代TTS方案
     */
    private fun tryAlternativeTTSApproach() {
        Log.d("TTSTest", "尝试替代TTS方案")

        // 方案1: 指定特定的TTS引擎
        val miuiTTSPackages = listOf(
            "com.xiaomi.speech",
            "com.miui.speech",
            "com.google.android.tts",
            "com.android.speech.tts",
            "com.svox.pico"
        )

        for (enginePackage in miuiTTSPackages) {
            try {
                packageManager.getPackageInfo(enginePackage, 0)
                Log.d("TTSTest", "尝试使用TTS引擎: $enginePackage")
                testTts = TextToSpeech(this, { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        Log.d("TTSTest", "✅ 使用$enginePackage 初始化成功")
                        configureTTSSettings()
                    }
                }, enginePackage)
                return
            } catch (e: Exception) {
                Log.d("TTSTest", "TTS引擎 $enginePackage 不可用")
            }
        }

        showToast("所有TTS引擎都无法使用，请手动配置TTS设置")
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

    Scaffold(
        bottomBar = {
            SmartDosingBottomNavigationBar(
                navController = navController
            )
        }
    ) { innerPadding ->
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
