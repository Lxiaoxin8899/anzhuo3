package com.example.smartdosing.ui.screens.dosing

import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.RecipeRepository
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.Material as RecipeMaterial
import com.example.smartdosing.ui.theme.SmartDosingTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlinx.coroutines.delay

data class Material(
    val id: String,
    val name: String,
    val targetWeight: Float,
    val unit: String = "KG"
)

/**
 * 语音播报管理器 - 专门处理工业投料的语音播报
 * 优化支持小米定制版百度TTS引擎
 */
class VoiceAnnouncementManager(private val context: android.content.Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    fun initialize(onReady: () -> Unit = {}) {
        android.util.Log.d("VoiceManager", "开始初始化TTS服务（优先小米小爱TTS）")

        try {
            // 优先使用小米自带小爱TTS的初始化方式
            tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    android.util.Log.e("VoiceManager", "❌ TTS初始化失败: $status")
                    isInitialized = false
                    return@TextToSpeech
                }

                android.util.Log.d("VoiceManager", "✅ TTS基础初始化成功，开始配置引擎")

                // 尝试小米自带小爱TTS
                if (tryXiaoAiTTS(onReady)) return@TextToSpeech

                // 备用：尝试Google TTS（HyperOS优化）
                if (tryGoogleTTSHyperOS(onReady)) return@TextToSpeech

                // 最后备用：标准TTS
                fallbackToStandardTTS(onReady)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "❌ TTS初始化异常", e)
            isInitialized = false
        }
    }

    /**
     * 尝试小米自带小爱TTS
     * 增强版 - 支持多个小米TTS引擎包名
     */
    private fun tryXiaoAiTTS(onReady: () -> Unit): Boolean {
        // 小米设备可能的TTS引擎包名（按优先级排列）
        val xiaomiEngines = listOf(
            "com.xiaomi.mibrain.speech",          // XiaoAi TTS (主要)
            "com.miui.tts",                       // MIUI TTS (备用1)
            "com.xiaomi.speech",                  // Xiaomi Speech (备用2)
            "com.miui.speech.tts"                 // MIUI Speech TTS (备用3)
        )

        android.util.Log.d("VoiceManager", "=== 开始尝试小米自带TTS引擎 ===")

        xiaomiEngines.forEach { enginePackage ->
            try {
                android.util.Log.d("VoiceManager", "尝试引擎: $enginePackage")

                val result = tts?.setEngineByPackageName(enginePackage)
                android.util.Log.d("VoiceManager", "引擎绑定结果: $result")

                if (result == TextToSpeech.SUCCESS) {
                    android.util.Log.d("VoiceManager", "✅ 成功绑定小米TTS引擎: $enginePackage")

                    tts?.apply {
                        // 延迟一下，确保引擎完全切换
                        try {
                            Thread.sleep(300) // 300ms延迟
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        // 设置中文语言
                        val langResult = setLanguage(java.util.Locale.CHINA)
                        android.util.Log.d("VoiceManager", "语言设置结果: $langResult")

                        if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                            setSpeechRate(1.0f)
                            setPitch(1.0f)

                            isInitialized = true
                            android.util.Log.d("VoiceManager", "✅ 小爱TTS配置完成 - 引擎: $enginePackage")

                            // 测试播放
                            speak("小爱语音已就绪，智能投料系统准备完成", TextToSpeech.QUEUE_FLUSH, null, null)
                            onReady()
                            return true
                        } else {
                            android.util.Log.w("VoiceManager", "⚠️ 引擎 $enginePackage 不支持中文，继续尝试下一个")
                            return@forEach
                        }
                    }
                } else {
                    android.util.Log.w("VoiceManager", "⚠️ 引擎绑定失败: $enginePackage (结果: $result)")
                    return@forEach
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceManager", "❌ 引擎 $enginePackage 配置异常", e)
                return@forEach
            }
        }

        android.util.Log.e("VoiceManager", "❌ 所有小米TTS引擎都无法使用")
        return false
    }

    /**
     * 尝试Google TTS（HyperOS优化）
     */
    private fun tryGoogleTTSHyperOS(onReady: () -> Unit): Boolean {
        try {
            android.util.Log.d("VoiceManager", "尝试Google TTS（HyperOS优化）")

            // 这行是小米 HyperOS 的"开挂神句"，必须加！
            val result = tts?.setEngineByPackageName("com.google.android.tts")
            if (result == TextToSpeech.SUCCESS) {
                android.util.Log.d("VoiceManager", "✅ 成功强制使用 Google 原生 TTS")

                tts?.apply {
                    setLanguage(java.util.Locale.CHINA)
                    setSpeechRate(1.0f)
                    setPitch(1.0f)

                    isInitialized = true
                    android.util.Log.d("VoiceManager", "✅ Google TTS（HyperOS优化）配置完成")

                    // 测试播放
                    speak("Google语音已就绪，智能投料系统准备完成", TextToSpeech.QUEUE_FLUSH, null, null)
                    onReady()
                }
                return true
            } else {
                android.util.Log.e("VoiceManager", "❌ Google TTS HyperOS优化失败: $result")
                return false
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "❌ Google TTS HyperOS优化异常", e)
            return false
        }
    }

    /**
     * 标准TTS初始化方式（备用）
     */
    private fun fallbackToStandardTTS(onReady: () -> Unit) {
        android.util.Log.d("VoiceManager", "尝试标准TTS初始化方式")

        val ttsInstance = tts ?: return

        try {
            val langResult = ttsInstance.setLanguage(java.util.Locale.CHINA)
            if (langResult >= TextToSpeech.LANG_AVAILABLE) {
                android.util.Log.d("VoiceManager", "✅ 标准TTS配置成功")
                ttsInstance.setSpeechRate(1.0f)
                ttsInstance.setPitch(1.0f)
                isInitialized = true
                ttsInstance.speak("智能投料系统语音播报已就绪", TextToSpeech.QUEUE_FLUSH, null, null)
                onReady()
            } else {
                android.util.Log.e("VoiceManager", "❌ 标准TTS语言设置失败")
                isInitialized = false
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "❌ 标准TTS配置异常", e)
            isInitialized = false
        }
    }

    /**
     * 播报材料信息 - 材料名称、编号、重量
     */
    fun announceMaterial(material: Material) {
        if (!isInitialized) return

        val announcement = buildString {
            append("请添加材料：")
            append("${material.name}，")
            append("编号：${material.id}，")
            append("重量：${formatWeight(material.targetWeight, material.unit)}")
        }

        speak(announcement)
    }

    /**
     * 播报当前步骤
     */
    fun announceStep(currentStep: Int, totalSteps: Int) {
        if (!isInitialized) return
        speak("第${currentStep + 1}步，共${totalSteps}步")
    }

    /**
     * 播报配方完成
     */
    fun announceCompletion() {
        if (!isInitialized) return
        speak("配方投料完成，请确认所有材料已添加")
    }

    /**
     * 播报错误信息
     */
    fun announceError(message: String) {
        if (!isInitialized) return
        speak("注意：$message")
    }

    /**
     * 重复播报当前材料信息
     */
    fun repeatCurrentAnnouncement(material: Material) {
        announceMaterial(material)
    }

    private fun speak(text: String) {
        android.util.Log.d("VoiceManager", "尝试播放语音: $text")

        if (!isInitialized) {
            android.util.Log.w("VoiceManager", "⚠️ TTS未初始化，跳过播放")
            return
        }

        val ttsInstance = tts
        if (ttsInstance == null) {
            android.util.Log.e("VoiceManager", "❌ TTS实例为空")
            return
        }

        try {
            // 使用社区推荐的简化播放方式
            ttsInstance.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            android.util.Log.d("VoiceManager", "✅ 语音播放命令已发送")
        } catch (e: Exception) {
            android.util.Log.e("VoiceManager", "❌ 语音播放异常", e)
        }
    }

    private fun formatWeight(weight: Float, unit: String): String {
        val normalizedUnit = unit.uppercase(Locale.getDefault())
        val value = if (weight == weight.toInt().toFloat()) {
            weight.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", weight)
        }
        return "$value $normalizedUnit"
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

/**
 * 投料操作页面
 * 集成CSV文件导入和完整的投料流程
 */
@Composable
fun DosingOperationScreen(
    recipeId: String? = null,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }
    val normalizedRecipeId = recipeId?.trim().orEmpty()
    val isCsvMode = normalizedRecipeId.isEmpty() || normalizedRecipeId == "import_csv" || normalizedRecipeId == "quick_start"
    var recipe by remember(normalizedRecipeId) { mutableStateOf<List<Material>?>(null) }
    var loadError by remember(normalizedRecipeId) { mutableStateOf<String?>(null) }
    var isLoading by remember(normalizedRecipeId) { mutableStateOf(!isCsvMode) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                val parsedRecipe = mutableListOf<Material>()
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val tokens = line!!.split(',')
                            if (tokens.size == 3) {
                                val material = Material(
                                    id = tokens[0].trim(),
                                    name = tokens[1].trim(),
                                    targetWeight = tokens[2].trim().toFloat(),
                                    unit = "KG"
                                )
                                parsedRecipe.add(material)
                            }
                        }
                    }
                }
                if (parsedRecipe.isNotEmpty()) {
                    recipe = parsedRecipe
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(normalizedRecipeId) {
        loadError = null
        if (!isCsvMode) {
            isLoading = true
            recipe = null
            val targetRecipe = repository.getRecipeById(normalizedRecipeId)
            if (targetRecipe == null) {
                loadError = "未找到该配方，请返回重新选择。"
            } else {
                val materials = targetRecipe.materials
                    .sortedBy { it.sequence }
                    .map { it.toOperationMaterial() }
                if (materials.isEmpty()) {
                    loadError = "该配方没有材料，请返回重新选择。"
                } else {
                    recipe = materials
                }
            }
            isLoading = false
        } else {
            loadError = null
            isLoading = false
        }
    }

    when {
        loadError != null -> {
            DosingErrorState(
                message = loadError!!,
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
        !isCsvMode && (isLoading || recipe == null) -> {
            DosingLoadingState(modifier = modifier)
        }
        recipe == null -> {
            CsvImportState(
                modifier = modifier,
                onImportFromFile = { launcher.launch(arrayOf("*/*")) },
                onNavigateBack = onNavigateBack
            )
        }
        else -> {
            val onSelectNewRecipeAction: () -> Unit = if (isCsvMode) {
                { recipe = null }
            } else {
                { onNavigateBack() }
            }
            DosingScreen(
                recipe = recipe!!,
                onSelectNewRecipe = onSelectNewRecipeAction,
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
    }
}

/**
 * CSV 导入模式界面
 */
@Composable
private fun CsvImportState(
    modifier: Modifier = Modifier,
    onImportFromFile: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "选择投料配方",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onImportFromFile,
            modifier = Modifier.width(300.dp).height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1976D2)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "导入CSV配方文件",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "请选择一个CSV格式的配方文件\n格式: 材料编号,材料名称,重量",
            fontSize = 16.sp,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.width(200.dp).height(60.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF757575)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "返回",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 配方载入错误提示
 */
@Composable
private fun DosingErrorState(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, fontSize = 20.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack, modifier = Modifier.width(200.dp).height(56.dp)) {
            Text(text = "返回", fontSize = 18.sp)
        }
    }
}

/**
 * 配方载入过渡态
 */
@Composable
private fun DosingLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "正在载入配方信息...", fontSize = 16.sp)
    }
}

@Composable
fun InfoCard(title: String, content: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = content, style = MaterialTheme.typography.displayMedium, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DosingScreen(
    recipe: List<Material>,
    onSelectNewRecipe: () -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(0) }
    var actualWeight by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 使用新的语音播报管理器
    val voiceManager = remember { VoiceAnnouncementManager(context) }

    // 初始化语音播报
    DisposableEffect(context) {
        voiceManager.initialize()
        onDispose {
            voiceManager.shutdown()
        }
    }

    val currentMaterial = if (currentStep < recipe.size) recipe[currentStep] else null

    // 当材料切换时进行语音播报
    LaunchedEffect(currentMaterial) {
        if (currentMaterial != null) {
            // 先播报步骤，稍等片刻再播报材料信息
            voiceManager.announceStep(currentStep, recipe.size)
            delay(800) // 等待步骤播报完成
            voiceManager.announceMaterial(currentMaterial)
        } else {
            voiceManager.announceCompletion()
        }
    }

    if (currentMaterial != null) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF757575)
                    )
                ) {
                    Text("← 返回", fontSize = 16.sp)
                }

                Text(
                    text = "配方投料操作",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(80.dp)) // 平衡布局
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Middle Info Row
            Row(
                modifier = Modifier.fillMaxWidth().weight(0.5f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                InfoCard(title = "材料名称", content = currentMaterial.name, modifier = Modifier.weight(1f))
                InfoCard(title = "材料编码", content = currentMaterial.id, modifier = Modifier.weight(1f))
                InfoCard(
                    title = "投料重量",
                    content = formatWeightDisplay(currentMaterial.targetWeight, currentMaterial.unit),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // 底部控制区域 - 重构为左右两栏布局 (2:1 比例)
            BottomControlArea(
                modifier = Modifier.fillMaxWidth().weight(1f),
                currentWeight = actualWeight,
                onWeightChange = { newWeight -> actualWeight = newWeight },
                onClearWeight = { actualWeight = "" },
                onConfirmNext = {
                    if (actualWeight.isNotBlank()) {
                        currentStep++
                        actualWeight = ""
                    }
                },
                onRepeatAnnouncement = {
                    // 手动重复播报当前材料信息
                    currentMaterial?.let { material ->
                        voiceManager.repeatCurrentAnnouncement(material)
                    }
                }
            )
        }
    } else {
        // "配方完成" screen
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "配方完成!", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { currentStep = 0 },
                    modifier = Modifier.width(200.dp).height(60.dp)
                ) {
                    Text("重新开始", fontSize = 24.sp)
                }
                Button(
                    onClick = onSelectNewRecipe,
                    modifier = Modifier.width(200.dp).height(60.dp)
                ) {
                    Text("选择新配方", fontSize = 24.sp)
                }
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.width(200.dp).height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF757575)
                    )
                ) {
                    Text("返回首页", fontSize = 24.sp)
                }
            }
        }
    }
}

/**
 * 将配方材料转换为投料操作材料
 */
private fun RecipeMaterial.toOperationMaterial(): Material {
    val normalizedId = if (id.isBlank()) {
        "MAT-$sequence"
    } else {
        id
    }
    val normalizedUnit = unit.ifBlank { "KG" }.uppercase(Locale.getDefault())
    return Material(
        id = normalizedId,
        name = name,
        targetWeight = weight.toFloat(),
        unit = normalizedUnit
    )
}

/**
 * 显示用重量格式化
 */
private fun formatWeightDisplay(weight: Float, unit: String): String {
    val normalizedUnit = unit.uppercase(Locale.getDefault())
    return if (weight == weight.toInt().toFloat()) {
        "${weight.toInt()} $normalizedUnit"
    } else {
        String.format(Locale.getDefault(), "%.2f %s", weight, normalizedUnit)
    }
}

/**
 * 底部控制区域 - 左右两栏布局（2:1 比例）
 * 左侧：数字键盘区域 (66% 宽度)
 * 右侧：功能控制区域 (33% 宽度)
 */
@Composable
fun BottomControlArea(
    modifier: Modifier = Modifier,
    currentWeight: String,
    onWeightChange: (String) -> Unit,
    onClearWeight: () -> Unit,
    onConfirmNext: () -> Unit,
    onRepeatAnnouncement: () -> Unit = {}
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 左侧 - 数字键盘区域 (65% 宽度)
        Column(
            modifier = Modifier.weight(2f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 输入显示框 - 放在键盘上方
            WeightDisplayBox(
                modifier = Modifier.fillMaxWidth().weight(0.25f),
                weight = currentWeight
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 数字键盘 - 标准3x4布局
            IndustrialNumericKeypad(
                modifier = Modifier.weight(0.75f),
                onKeyPress = { key ->
                    when (key) {
                        "⌫" -> {
                            // 回退删除最后一位
                            if (currentWeight.isNotEmpty()) {
                                onWeightChange(currentWeight.dropLast(1))
                            }
                        }
                        "." -> {
                            // 小数点逻辑 - 只允许一个小数点且不能是第一位
                            if (!currentWeight.contains(".") && currentWeight.isNotEmpty()) {
                                onWeightChange(currentWeight + key)
                            }
                        }
                        else -> {
                            // 数字输入
                            onWeightChange(currentWeight + key)
                        }
                    }
                }
            )
        }

        // 右侧 - 功能控制区域 (33% 宽度)
        FunctionControlPanel(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClearWeight = onClearWeight,
            onConfirmNext = onConfirmNext,
            onRepeatAnnouncement = onRepeatAnnouncement,
            isNextEnabled = currentWeight.isNotBlank()
        )
    }
}

/**
 * 重量显示框 - 输入显示区域
 */
@Composable
fun WeightDisplayBox(
    modifier: Modifier = Modifier,
    weight: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFB0BEC5)),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = if (weight.isBlank()) "0.0" else weight,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF263238),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * 工业级数字键盘 - 3列 x 4行标准布局，适配10寸平板
 * 布局：7 8 9
 *      4 5 6
 *      1 2 3
 *      . 0 ⌫
 * 优化：适中尺寸，适合10寸平板操作
 */
@Composable
fun IndustrialNumericKeypad(
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit
) {
    // 按键布局 - 标准计算器布局
    val keyLayout = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp) // 适中的垂直间距
    ) {
        keyLayout.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // 适中的水平间距
            ) {
                row.forEach { key ->
                    IndustrialKeyButton(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        text = key,
                        onClick = { onKeyPress(key) }
                    )
                }
            }
        }
    }
}

/**
 * 工业级按键按钮 - 适配10寸平板，便于操作
 */
@Composable
fun IndustrialKeyButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp), // 恢复圆润设计
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF263238)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,  // 适中的阴影
            pressedElevation = 8.dp
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)) // 适中的边框
    ) {
        Text(
            text = text,
            fontSize = 28.sp, // 适合10寸平板的字体大小
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 功能控制面板 - 右侧操作按钮区域
 * 工业级设计：大按钮，方形设计，间距充足
 */
@Composable
fun FunctionControlPanel(
    modifier: Modifier = Modifier,
    onClearWeight: () -> Unit,
    onConfirmNext: () -> Unit,
    onRepeatAnnouncement: () -> Unit,
    isNextEnabled: Boolean
) {
    Column(
        modifier = modifier.padding(8.dp), // 面板内边距
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), // 增加按钮间距
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "语音重播"按钮 - 工业绿色，超大设计
        Button(
            onClick = onRepeatAnnouncement,
            modifier = Modifier.fillMaxWidth().height(90.dp), // 增加高度
            shape = RoundedCornerShape(8.dp), // 方形设计
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,  // 增加阴影
                pressedElevation = 16.dp
            ),
            border = BorderStroke(2.dp, Color(0xFF388E3C)), // 添加边框
            contentPadding = PaddingValues(20.dp) // 增加内边距
        ) {
            Text(
                text = "🔊 重播",
                fontSize = 24.sp, // 增大字体
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // "清空"按钮 - 警示色，超大设计
        Button(
            onClick = onClearWeight,
            modifier = Modifier.fillMaxWidth().height(90.dp), // 增加高度
            shape = RoundedCornerShape(8.dp), // 方形设计
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF9A9A),
                contentColor = Color(0xFFB71C1C)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,  // 增加阴影
                pressedElevation = 16.dp
            ),
            border = BorderStroke(2.dp, Color(0xFFE57373)), // 添加边框
            contentPadding = PaddingValues(20.dp) // 增加内边距
        ) {
            Text(
                text = "清空",
                fontSize = 24.sp, // 增大字体
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // "下一步"按钮 - 工业蓝，超大设计
        Button(
            onClick = onConfirmNext,
            modifier = Modifier.fillMaxWidth().height(90.dp), // 增加高度
            shape = RoundedCornerShape(8.dp), // 方形设计
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1976D2),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFB0BEC5),
                disabledContentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,  // 增加阴影
                pressedElevation = 16.dp
            ),
            border = BorderStroke(
                width = 2.dp,
                color = if (isNextEnabled) Color(0xFF1565C0) else Color(0xFF90A4AE)
            ), // 动态边框颜色
            enabled = isNextEnabled,
            contentPadding = PaddingValues(20.dp) // 增加内边距
        ) {
            Text(
                text = "下一步",
                fontSize = 24.sp, // 增大字体
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DosingOperationScreenPreview() {
    SmartDosingTheme {
        DosingOperationScreen()
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun DosingOperationScreenDetailPreview() {
    val previewRecipe = listOf(
        Material("abc-001", "苹果香精", 10.5f, "KG"),
        Material("abc-002", "柠檬酸", 22.0f, "KG"),
        Material("def-003", "甜蜜素", 5.2f, "KG")
    )
    SmartDosingTheme {
        DosingScreen(recipe = previewRecipe, onSelectNewRecipe = {})
    }
}
