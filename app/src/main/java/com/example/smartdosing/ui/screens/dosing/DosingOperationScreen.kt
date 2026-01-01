package com.example.smartdosing.ui.screens.dosing

import androidx.compose.ui.text.font.FontFamily
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.DosingRecordDetailInput
import com.example.smartdosing.data.DosingRecordRepository
import com.example.smartdosing.data.DosingRecordSaveRequest
import com.example.smartdosing.data.RecipeRepository
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.settings.DosingPreferencesManager
import com.example.smartdosing.data.settings.DosingPreferencesState
import com.example.smartdosing.data.Material as RecipeMaterial
import com.example.smartdosing.ui.theme.SmartDosingTokens
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import com.example.smartdosing.ui.components.*
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Schedule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Material(
    val id: String,
    val name: String,
    val targetWeight: Float,
    val unit: String = "KG",
    val sequence: Int = 1
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
                        android.util.Log.i("DosingTTS", "小爱语音引擎已就绪")
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
                    android.util.Log.i("DosingTTS", "Google语音引擎已就绪")
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
                ttsInstance.speak("投料系统已经就绪", TextToSpeech.QUEUE_FLUSH, null, null)
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
    fun announceMaterial(material: Material, repeatCount: Int = 1) {
        if (!isInitialized) return

        val announcement = buildString {
            append("请添加材料：")
            append("${material.name}，")
            append("编号：${material.id}，")
            append("重量：${formatWeight(material.targetWeight, material.unit)}")
        }

        val safeRepeat = repeatCount.coerceAtLeast(1)
        repeat(safeRepeat) { index ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            speak(announcement, queueMode)
        }
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
    fun repeatCurrentAnnouncement(material: Material, repeatCount: Int = 1) {
        announceMaterial(material, repeatCount)
    }

    private fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
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
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            ttsInstance.speak(text, queueMode, null, utteranceId)
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
/**
 * 投料操作页面 (实验室版)
 * 集成CSV文件导入和完整的投料流程 - 针对研发场景优化
 */
@Composable
fun DosingOperationScreen(
    recipeId: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToRecipeList: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }
    val dosingRecordRepository = remember { DosingRecordRepository.getInstance(context) }
    val preferencesManager = remember { DosingPreferencesManager(context) }
    val preferencesState by preferencesManager.preferencesFlow.collectAsState(initial = DosingPreferencesState())
    val coroutineScope = rememberCoroutineScope()
    // Lab: Default items might differ, but keep logic same for now
    val checklistItems = remember {
        mutableStateListOf(
            ChecklistItemState("称量设备已校准"),
            ChecklistItemState("原料批次已核对"),
            ChecklistItemState("安全防护已到位")
        )
    }
    var operatorName by remember { mutableStateOf("") }
    val detailInputs = remember { mutableStateListOf<DosingRecordDetailInput>() }
    var overLimitWarning by remember { mutableStateOf<OverLimitWarning?>(null) }
    var isPreCheckCompleted by remember { mutableStateOf(false) }
    var showPreCheckDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    var operationStartTime by remember { mutableStateOf(dateFormat.format(Date())) }
    var recipeName by remember { mutableStateOf("临时配方") }
    var recipeCode by remember { mutableStateOf<String?>(null) }
    var isRecordSaved by remember { mutableStateOf(false) }
    val normalizedRecipeId = recipeId?.trim().orEmpty()
    val isCsvMode = normalizedRecipeId.isEmpty() || normalizedRecipeId == "import_csv" || normalizedRecipeId == "quick_start"
    var recipe by remember(normalizedRecipeId) { mutableStateOf<List<Material>?>(null) }
    var loadError by remember(normalizedRecipeId) { mutableStateOf<String?>(null) }
    var isLoading by remember(normalizedRecipeId) { mutableStateOf(!isCsvMode) }

    // Lab: Cleaner import Logic
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
                            if (tokens.size >= 3) { // Slightly more robust check
                                val material = Material(
                                    id = tokens[0].trim(),
                                    name = tokens[1].trim(),
                                    targetWeight = tokens[2].trim().toFloatOrNull() ?: 0f,
                                    unit = "KG",
                                    sequence = parsedRecipe.size + 1
                                )
                                parsedRecipe.add(material)
                            }
                        }
                    }
                }
                if (parsedRecipe.isNotEmpty()) {
                    recipeName = "CSV导入(${parsedRecipe.size}项)"
                    recipeCode = null
                    operationStartTime = dateFormat.format(Date())
                    detailInputs.clear()
                    isRecordSaved = false
                    recipe = parsedRecipe
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(normalizedRecipeId) {
        loadError = null
        detailInputs.clear()
        overLimitWarning = null
        isRecordSaved = false
        isPreCheckCompleted = false
        showPreCheckDialog = false
        operationStartTime = dateFormat.format(Date())
        recipeCode = null
        if (isCsvMode) {
            recipeName = "CSV临时配方"
        }
        checklistItems.forEach { it.checked = false }
        operatorName = ""
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
                    recipeName = targetRecipe.name
                    recipeCode = targetRecipe.code
                    recipe = materials
                }
            }
            isLoading = false
        } else {
            loadError = null
            isLoading = false
        }
    }

    // Main Container Styling
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background // Scientific Grey/White
    ) {
        when {
            loadError != null -> {
                DosingErrorState(
                    message = loadError!!,
                    onNavigateBack = onNavigateBack
                )
            }
            !isCsvMode && (isLoading || recipe == null) -> {
                DosingLoadingState()
            }
            recipe == null -> {
                CsvImportState(
                    onImportFromFile = { launcher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*")) }, // Enhanced MIME types
                    onNavigateBack = onNavigateBack
                )
            }
            else -> {
                val selectNewRecipeLabel = if (isCsvMode) "选择新配方" else "返回配方管理"
                val onSelectNewRecipeAction: () -> Unit = if (isCsvMode) {
                    { recipe = null }
                } else {
                    { onNavigateToRecipeList() }
                }
                overLimitWarning?.let { warning ->
                    OverLimitDialog(warning = warning, onDismiss = { overLimitWarning = null })
                }

                // PreCheck Dialog
                PreCheckDialog(
                    operatorName = operatorName,
                    onOperatorNameChange = { operatorName = it },
                    checklistItems = checklistItems,
                    isVisible = showPreCheckDialog,
                    onConfirm = {
                        isPreCheckCompleted = true
                        showPreCheckDialog = false
                        operationStartTime = dateFormat.format(Date())
                    },
                    onCancel = { showPreCheckDialog = false }
                )

                if (!isPreCheckCompleted) {
                    // Prep Screen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LabCard(
                            useResponsiveWidth = true
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.lg)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Science, // Lab Icon
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "实验准备就绪",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                    DataValueDisplay(label = "实验配方", value = recipeName)
                                    Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.sm))
                                    Text(
                                        text = "共 ${recipe!!.size} 种组分",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
                                ) {
                                    LabOutlinedButton(
                                        onClick = onNavigateBack,
                                        text = "取消",
                                        modifier = Modifier.weight(1f)
                                    )
                                    LabButton(
                                        onClick = { showPreCheckDialog = true },
                                        text = "开始投料",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Main Dosing Flow
                    DosingScreen(
                        recipe = recipe!!,
                        onSelectNewRecipe = onSelectNewRecipeAction,
                        selectNewRecipeLabel = selectNewRecipeLabel,
                        onNavigateBack = onNavigateBack,
                        preferencesState = preferencesState,
                        operatorName = operatorName,
                        checklistItems = checklistItems,
                        detailInputs = detailInputs,
                        onOverLimitWarningChange = { overLimitWarning = it },
                        isRecordSaved = isRecordSaved,
                        onRecordSavedChange = { isRecordSaved = it },
                        isCsvMode = isCsvMode,
                        normalizedRecipeId = normalizedRecipeId,
                        recipeCode = recipeCode,
                        recipeName = recipeName,
                        operationStartTime = operationStartTime,
                        dateFormat = dateFormat,
                        dosingRecordRepository = dosingRecordRepository,
                        coroutineScope = coroutineScope,
                        modifier = modifier
                    )
                }
            }
        }
    }
}

/**
 * CSV 导入模式界面 (实验室版)
 */
@Composable
private fun CsvImportState(
    modifier: Modifier = Modifier,
    onImportFromFile: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabCard(useResponsiveWidth = true) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.xl)
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "导入实验数据",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "支持 CSV 格式配方文件导入\n格式: 编号, 名称, 重量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
                ) {
                    LabButton(
                        onClick = onImportFromFile,
                        text = "选择文件",
                        icon = Icons.Default.FolderOpen,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    LabOutlinedButton(
                        onClick = onNavigateBack,
                        text = "返回首页",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 配方载入错误提示 (实验室版)
 */
@Composable
private fun DosingErrorState(
    message: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LabCard(useResponsiveWidth = true) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.lg)
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                LabButton(onClick = onNavigateBack, text = "返回", containerColor = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 配方载入过渡态 (实验室版)
 */
@Composable
private fun DosingLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.md))
            Text("正在同步实验数据...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PreCheckDialog(
    operatorName: String,
    onOperatorNameChange: (String) -> Unit,
    checklistItems: List<ChecklistItemState>,
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = {
                LabSectionHeader("实验前检查")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
                ) {
                    // 操作员姓名输入
                    LabTextField(
                        value = operatorName,
                        onValueChange = onOperatorNameChange,
                        label = "操作人员",
                        placeholder = "输入姓名或工号",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 检查清单
                    checklistItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { item.checked = !item.checked }
                        ) {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { checked -> item.checked = checked }
                            )
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    val isAllReady = operatorName.isNotBlank() && checklistItems.all { it.checked }

                    if (!isAllReady) {
                        Text(
                            text = "需完成所有检查项方可开始",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                val isAllReady = operatorName.isNotBlank() && checklistItems.all { it.checked }
                LabButton(
                    onClick = onConfirm,
                    enabled = isAllReady,
                    text = "开始投料"
                )
            },
            dismissButton = {
                LabOutlinedButton(onClick = onCancel, text = "取消")
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(SmartDosingTokens.radius.lg)
        )
    }
}

@Composable
fun DosingScreen(
    recipe: List<Material>,
    onSelectNewRecipe: () -> Unit,
    selectNewRecipeLabel: String = "选择新配方",
    onNavigateBack: () -> Unit = {},
    preferencesState: DosingPreferencesState,
    operatorName: String,
    checklistItems: List<ChecklistItemState>,
    detailInputs: MutableList<DosingRecordDetailInput>,
    onOverLimitWarningChange: (OverLimitWarning?) -> Unit,
    isRecordSaved: Boolean,
    onRecordSavedChange: (Boolean) -> Unit,
    isCsvMode: Boolean,
    normalizedRecipeId: String,
    recipeCode: String?,
    recipeName: String,
    operationStartTime: String,
    dateFormat: SimpleDateFormat,
    dosingRecordRepository: DosingRecordRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier
) {
    // 获取窗口尺寸，决定布局模式
    val windowSize = LocalWindowSize.current
    val isCompactLayout = windowSize.widthClass == SmartDosingWindowWidthClass.Compact
    
    var currentStep by remember { mutableStateOf(0) }
    var actualWeight by remember { mutableStateOf("") }
    val context = LocalContext.current
    val voiceManager = remember { VoiceAnnouncementManager(context) }
    val listState = rememberLazyListState()
    
    // Bluetooth Manager
    val bluetoothScaleManager = remember { com.example.smartdosing.bluetooth.BluetoothScaleManager(context) }
    val bluetoothConnectionState by bluetoothScaleManager.connectionState.collectAsState()
    val bluetoothWeight by bluetoothScaleManager.currentWeight.collectAsState()
    val isBluetoothConnected = bluetoothConnectionState == com.example.smartdosing.bluetooth.model.ConnectionState.CONNECTED
    
    // Dosing Mode from passed preferences
    val isManualMode = preferencesState.dosingMode == com.example.smartdosing.data.settings.DosingMode.MANUAL
    
    // Debug: Log mode value
    LaunchedEffect(preferencesState.dosingMode) {
        android.util.Log.d("DosingScreen", "🔧 当前投料模式: ${preferencesState.dosingMode.name}, isManualMode=$isManualMode")
    }
    
    // Sync Bluetooth weight to actualWeight when connected and stable (optional auto-fill)
    // Or just pass the bluetooth weight object to ActiveStation to decide.
    // For now, let's keep them separate flows but use Bluetooth reading if available.

    DisposableEffect(context) {
        voiceManager.initialize()
        onDispose { 
            voiceManager.shutdown() 
            bluetoothScaleManager.destroy()
        }
    }

    val currentMaterial = if (currentStep < recipe.size) recipe[currentStep] else null

    LaunchedEffect(currentStep) {
        if (currentStep < recipe.size) {
            listState.animateScrollToItem(currentStep)
        }
    }

    LaunchedEffect(currentMaterial, preferencesState.repeatCountForPlayback) {
        if (currentMaterial != null) {
            voiceManager.announceStep(currentStep, recipe.size)
            delay(800)
            voiceManager.announceMaterial(currentMaterial, preferencesState.repeatCountForPlayback)
        } else {
            voiceManager.announceCompletion()
        }
    }

    LaunchedEffect(currentStep, recipe) {
        val materials = recipe
        if (materials != null && currentStep >= materials.size && detailInputs.isNotEmpty() && !isRecordSaved) {
            val recordRecipeId = if (isCsvMode) null else normalizedRecipeId.ifBlank { null }
            val request = DosingRecordSaveRequest(
                recipeId = recordRecipeId,
                recipeCode = recipeCode,
                recipeName = recipeName,
                operatorName = operatorName.ifBlank { "未填写" },
                checklistItems = checklistItems.filter { it.checked }.map { it.label },
                startTime = operationStartTime,
                endTime = dateFormat.format(Date()),
                totalMaterials = materials.size,
                tolerancePercent = preferencesState.overLimitTolerancePercent,
                details = detailInputs.map { it.copy() }
            )
            coroutineScope.launch {
                try {
                    dosingRecordRepository.saveRecord(request)
                    onRecordSavedChange(true)
                    Toast.makeText(context, "投料记录已保存", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "保存投料记录失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (currentMaterial != null) {
        if (isCompactLayout) {
            // === 紧凑布局（手机）：垂直堆叠，隐藏材料列表 ===
            CompactDosingLayout(
                modifier = modifier.fillMaxSize(),
                material = currentMaterial,
                currentStep = currentStep,
                totalSteps = recipe.size,
                actualWeight = actualWeight,
                onWeightChange = { actualWeight = it },
                onClearWeight = { actualWeight = "" },
                onConfirmNext = {
                    val normalizedInput = actualWeight.replace(',', '.')
                    val actualValue = normalizedInput.toDoubleOrNull()
                    if (actualValue == null) {
                        Toast.makeText(context, "请输入有效的投料重量", Toast.LENGTH_SHORT).show()
                        return@CompactDosingLayout
                    }
                    val target = currentMaterial.targetWeight.toDouble()
                    val tolerance = preferencesState.overLimitTolerancePercent.toDouble()
                    val limit = target * (1 + tolerance / 100.0)
                    val isOverLimit = target > 0 && actualValue > limit
                    val overPercent = if (target > 0) ((actualValue - target) / target) * 100.0 else 0.0
                    detailInputs.add(
                        DosingRecordDetailInput(
                            sequence = currentMaterial.sequence,
                            materialCode = currentMaterial.id,
                            materialName = currentMaterial.name,
                            targetWeight = target,
                            actualWeight = actualValue,
                            unit = currentMaterial.unit,
                            isOverLimit = isOverLimit,
                            overLimitPercent = overPercent
                        )
                    )
                    if (isOverLimit) {
                        onOverLimitWarningChange(OverLimitWarning(currentMaterial.name, overPercent))
                    }
                    currentStep++
                    actualWeight = ""
                },
                onRepeatAnnouncement = {
                    voiceManager.repeatCurrentAnnouncement(currentMaterial, preferencesState.repeatCountForPlayback)
                },
                tolerancePercent = preferencesState.overLimitTolerancePercent
            )
        } else {
            // === 常规布局（平板）：水平分割 ===
            Column(
                modifier = modifier.fillMaxSize().padding(SmartDosingTokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
            ) {
                // Zone A: Material Context List (40%)
                MaterialContextList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f),
                    materials = recipe,
                    currentStep = currentStep,
                    listState = listState,
                    onItemClick = { index -> 
                        currentStep = index
                        actualWeight = "" 
                    }
                )

            // Zone B: Active Material Station (60%) - Mode-Specific
            if (isManualMode) {
                ManualModeActiveMaterialStation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f),
                    material = currentMaterial,
                    currentWeight = actualWeight,
                    onWeightChange = { actualWeight = it },
                    onClearWeight = { actualWeight = "" },
                    onConfirmNext = {
                        val materialForLog = currentMaterial
                        val normalizedInput = actualWeight.replace(',', '.')
                        val actualValue = normalizedInput.toDoubleOrNull()
                        if (actualValue == null) {
                            Toast.makeText(context, "请输入有效的投料重量", Toast.LENGTH_SHORT).show()
                            return@ManualModeActiveMaterialStation
                        }
                        val target = materialForLog.targetWeight.toDouble()
                        val tolerance = preferencesState.overLimitTolerancePercent.toDouble()
                        val limit = target * (1 + tolerance / 100.0)
                        val isOverLimit = target > 0 && actualValue > limit
                        val overPercent = if (target > 0) ((actualValue - target) / target) * 100.0 else 0.0
                        detailInputs.add(
                            DosingRecordDetailInput(
                                sequence = materialForLog.sequence,
                                materialCode = materialForLog.id,
                                materialName = materialForLog.name,
                                targetWeight = target,
                                actualWeight = actualValue,
                                unit = materialForLog.unit,
                                isOverLimit = isOverLimit,
                                overLimitPercent = overPercent
                            )
                        )
                        if (isOverLimit) {
                            onOverLimitWarningChange(OverLimitWarning(materialForLog.name, overPercent))
                        }
                        currentStep++
                        actualWeight = ""
                    },
                    onRepeatAnnouncement = {
                        voiceManager.repeatCurrentAnnouncement(currentMaterial, preferencesState.repeatCountForPlayback)
                    },
                    tolerancePercent = preferencesState.overLimitTolerancePercent
                )
            } else {
                ActiveMaterialStation(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f),
                    material = currentMaterial,
                    currentWeight = actualWeight,
                    onWeightChange = { actualWeight = it },
                    onClearWeight = { actualWeight = "" },
                    onConfirmNext = {
                        val materialForLog = currentMaterial
                        val normalizedInput = actualWeight.replace(',', '.')
                        val actualValue = normalizedInput.toDoubleOrNull()
                        if (actualValue == null) {
                            Toast.makeText(context, "请输入有效的投料重量", Toast.LENGTH_SHORT).show()
                            return@ActiveMaterialStation
                        }
                        val target = materialForLog.targetWeight.toDouble()
                        val tolerance = preferencesState.overLimitTolerancePercent.toDouble()
                        val limit = target * (1 + tolerance / 100.0)
                        val isOverLimit = target > 0 && actualValue > limit
                        val overPercent = if (target > 0) ((actualValue - target) / target) * 100.0 else 0.0
                        detailInputs.add(
                            DosingRecordDetailInput(
                                sequence = materialForLog.sequence,
                                materialCode = materialForLog.id,
                                materialName = materialForLog.name,
                                targetWeight = target,
                                actualWeight = actualValue,
                                unit = materialForLog.unit,
                                isOverLimit = isOverLimit,
                                overLimitPercent = overPercent
                            )
                        )
                        if (isOverLimit) {
                            onOverLimitWarningChange(OverLimitWarning(materialForLog.name, overPercent))
                        }
                        currentStep++
                        actualWeight = ""
                    },
                    onRepeatAnnouncement = {
                        voiceManager.repeatCurrentAnnouncement(currentMaterial, preferencesState.repeatCountForPlayback)
                    },
                    onNavigateBack = onNavigateBack,
                    tolerancePercent = preferencesState.overLimitTolerancePercent,
                    bluetoothScaleManager = bluetoothScaleManager,
                    isBluetoothConnected = isBluetoothConnected,
                    bluetoothWeight = bluetoothWeight
                )
            }
        }
        } // 关闭 else 分支
    } else {
        // Completion Screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LabCard(useResponsiveWidth = true) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.xl)
                ) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        null, 
                        modifier = Modifier.size(80.dp), 
                        tint = SmartDosingTokens.colors.success
                    )
                    Text("配方投料完成", style = MaterialTheme.typography.headlineMedium)
                    
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)) {
                         LabButton(
                            onClick = {
                                currentStep = 0
                                detailInputs.clear()
                                onRecordSavedChange(false)
                                onOverLimitWarningChange(null)
                            },
                            text = "重新开始此配方",
                            modifier = Modifier.fillMaxWidth()
                        )
                        LabOutlinedButton(
                            onClick = onSelectNewRecipe,
                            text = selectNewRecipeLabel,
                            modifier = Modifier.fillMaxWidth()
                        )
                         LabOutlinedButton(
                            onClick = onNavigateBack,
                            text = "返回首页",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Zone A: Context List
 * Compact list to show all materials and status
 */
// Zone A: Context List
@Composable
fun MaterialContextList(
    modifier: Modifier = Modifier,
    materials: List<Material>,
    currentStep: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (Int) -> Unit
) {
    LabCard(modifier = modifier, backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)) {
        Column {
            LabSectionHeader("配方清单 (${currentStep + 1}/${materials.size})")
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(materials) { index, material ->
                    val isCurrent = index == currentStep
                    val isCompleted = index < currentStep
                    
                    val backgroundColor = when {
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer
                        isCompleted -> SmartDosingTokens.colors.success.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    
                    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent
                    
                    Surface(
                        color = backgroundColor,
                        shape = RoundedCornerShape(SmartDosingTokens.radius.sm),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp) // Compact fixed height
                            .clickable { onItemClick(index) } // Enable Click
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = SmartDosingTokens.spacing.md)
                        ) {
                            // Status Icon
                            Icon(
                                imageVector = if (isCompleted) Icons.Default.CheckCircle else if (isCurrent) Icons.Default.ArrowForward else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isCompleted) SmartDosingTokens.colors.success else if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(SmartDosingTokens.spacing.md))
                            
                            // Name
                            Text(
                                text = material.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Weight
                            Text(
                                text = "${material.targetWeight} ${material.unit}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Zone B: Active Station
 * Large controls for the current operation
 */
@Composable
fun ActiveMaterialStation(
    modifier: Modifier = Modifier,
    material: Material,
    currentWeight: String,
    onWeightChange: (String) -> Unit,
    onClearWeight: () -> Unit,
    onConfirmNext: () -> Unit,
    onRepeatAnnouncement: () -> Unit,
    onNavigateBack: () -> Unit,
    tolerancePercent: Float,
    bluetoothScaleManager: com.example.smartdosing.bluetooth.BluetoothScaleManager,
    isBluetoothConnected: Boolean,
    bluetoothWeight: com.example.smartdosing.bluetooth.model.WeightData?
) {
    LabCard(modifier = modifier, backgroundColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Current Material Info & Bluetooth Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "当前正在投料",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Bluetooth Status Bar (Compact)
                DosingBluetoothStatusBar(
                    scaleManager = bluetoothScaleManager,
                    modifier = Modifier.width(280.dp), // Fixed width for status bar
                    onConnectClick = { 
                         // Check permissions and scan (Quick logic, usually need Activity launcher)
                         bluetoothScaleManager.startScan()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.md))
            
            // Main Operation Area : Split Left (Display) and Right (Keypad)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.lg)
            ) {
                // Left: Weight Display & Actions
                Column(modifier = Modifier.weight(0.45f)) {
                    // Optimized Dual-Mode Display
                    // If Bluetooth connected -> Show Bluetooth Style
                    // If Manual -> Show Input Field Style
                    
                    val displayWeight = if (isBluetoothConnected && bluetoothWeight != null) {
                        bluetoothWeight.getDisplayValue()
                    } else {
                        currentWeight
                    }
                    
                    val isStable = if (isBluetoothConnected) bluetoothWeight?.isStable == true else false
                    
                    LabWeightDisplay(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        weight = displayWeight,
                        targetWeight = material.targetWeight,
                        tolerancePercent = tolerancePercent,
                        isManualMode = !isBluetoothConnected, // New Flag
                        isStable = isStable
                    )
                    
                    Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.md))
                    
                    Row(
                       horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm) 
                    ) {
                        LabOutlinedButton(onClick = onRepeatAnnouncement, text = "重播", modifier = Modifier.weight(1f))
                        LabOutlinedButton(onClick = onNavigateBack, text = "暂离", modifier = Modifier.weight(1f))
                    }
                }
                
                // Right: Big Keypad
                Column(modifier = Modifier.weight(0.55f)) {
                     Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
                    ) {
                        // Keypad occupies most space
                        LabNumericKeypad(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onKeyPress = { key ->
                                when (key) {
                                    "⌫" -> if (currentWeight.isNotEmpty()) onWeightChange(currentWeight.dropLast(1))
                                    "." -> if (!currentWeight.contains(".") && currentWeight.isNotEmpty()) onWeightChange(currentWeight + key)
                                    else -> onWeightChange(currentWeight + key)
                                }
                            }
                        )
                        
                        // Control Column
                        Column(
                            verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md),
                            modifier = Modifier.width(80.dp).fillMaxHeight()
                        ) {
                             LabButton(
                                onClick = onClearWeight,
                                text = "清",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            LabButton(
                                onClick = onConfirmNext,
                                text = "OK",
                                containerColor = MaterialTheme.colorScheme.primary, // Green or Primary
                                modifier = Modifier.weight(2f).fillMaxWidth(), // Double height for ease of access
                                enabled = currentWeight.isNotBlank()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Manual Mode: Calculator-Style Active Station
 * Optimized for manual weight input with large keypad and input box.
 * No Bluetooth UI elements.
 */
@Composable
fun ManualModeActiveMaterialStation(
    modifier: Modifier = Modifier,
    material: Material,
    currentWeight: String,
    onWeightChange: (String) -> Unit,
    onClearWeight: () -> Unit,
    onConfirmNext: () -> Unit,
    onRepeatAnnouncement: () -> Unit,
    tolerancePercent: Float
) {
    LabCard(modifier = modifier, backgroundColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Header: Material Name + Target Weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LabStatusBadge(
                    text = "目标: ${material.targetWeight} ${material.unit}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.sm))
            
            // Main Area: Large Input + Keypad Side by Side
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
            ) {
                // Left: Large Input Display + Progress
                Column(
                    modifier = Modifier.weight(0.4f),
                    verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm)
                ) {
                    // Large Weight Input Display (Manual Style)
                    ManualInputDisplay(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        weight = currentWeight,
                        targetWeight = material.targetWeight,
                        tolerancePercent = tolerancePercent
                    )
                    
                    // Small utility buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm)) {
                        LabOutlinedButton(
                            onClick = onRepeatAnnouncement,
                            text = "重播",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Right: Big Keypad + Control Buttons
                Column(
                    modifier = Modifier.weight(0.6f),
                    verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
                    ) {
                        // Large Keypad
                        LabNumericKeypad(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onKeyPress = { key ->
                                when (key) {
                                    "⌫" -> if (currentWeight.isNotEmpty()) onWeightChange(currentWeight.dropLast(1))
                                    "." -> if (!currentWeight.contains(".") && currentWeight.isNotEmpty()) onWeightChange(currentWeight + key)
                                    else -> onWeightChange(currentWeight + key)
                                }
                            }
                        )
                        
                        // Control Column: Clear + OK
                        Column(
                            verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md),
                            modifier = Modifier.width(100.dp).fillMaxHeight()
                        ) {
                            LabButton(
                                onClick = onClearWeight,
                                text = "清空",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            LabButton(
                                onClick = onConfirmNext,
                                text = "确认",
                                containerColor = SmartDosingTokens.colors.success,
                                contentColor = Color.White,
                                modifier = Modifier.weight(2f).fillMaxWidth(),
                                enabled = currentWeight.isNotBlank()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Large Manual Input Display - Calculator Style
 */
@Composable
fun ManualInputDisplay(
    modifier: Modifier = Modifier,
    weight: String,
    targetWeight: Float = 0f,
    tolerancePercent: Float = 0f
) {
    val currentWeightVal = weight.replace(",", ".").toFloatOrNull() ?: 0f
    
    val isOver = targetWeight > 0 && currentWeightVal > targetWeight * (1 + tolerancePercent / 100f)
    val isNear = targetWeight > 0 && currentWeightVal >= targetWeight * (1 - tolerancePercent / 100f)
    
    val displayColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isNear -> SmartDosingTokens.colors.success
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val statusColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isNear -> SmartDosingTokens.colors.success
        else -> MaterialTheme.colorScheme.primary
    }

    LabCard(
        modifier = modifier,
        backgroundColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SmartDosingTokens.spacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            // Input Mode Label
            Text(
                "手动输入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.sm))
            
            // Large Weight Display with Cursor
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (weight.isBlank()) "0" else weight,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = displayColor
                    )
                    // Blinking Cursor
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(4.dp)
                            .height(70.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            
            // Progress Bar
            if (targetWeight > 0) {
                val progress = (currentWeightVal / targetWeight).coerceIn(0f, 1.2f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("目标: $targetWeight", style = MaterialTheme.typography.bodySmall)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { progress / 1.2f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun RecipeMaterial.toOperationMaterial(): Material {
    val normalizedId = when {
        code.isNotBlank() -> code
        id.isNotBlank() -> id
        else -> "MAT-$sequence"
    }
    val normalizedUnit = unit.ifBlank { "KG" }.uppercase(Locale.getDefault())
    return Material(
        id = normalizedId,
        name = name,
        targetWeight = weight.toFloat(),
        unit = normalizedUnit,
        sequence = sequence
    )
}

private fun formatWeightDisplay(weight: Float, unit: String): String {
    val normalizedUnit = unit.uppercase(Locale.getDefault())
    return String.format(Locale.getDefault(), "%.3f %s", weight, normalizedUnit)
}

// Re-using LabWeightDisplay and LabNumericKeypad from previous (or keeping same implementations but ensuring they adapt)
// We need to ensure LabNumericKeypad fills height properly.

@Composable
fun LabWeightDisplay(
    modifier: Modifier = Modifier,
    weight: String,
    targetWeight: Float = 0f,
    tolerancePercent: Float = 0f,
    isManualMode: Boolean = true,
    isStable: Boolean = false
) {
    val currentWeightVal = weight.replace(",", ".").toFloatOrNull() ?: 0f
    
    // Status Logic
    val isOver = targetWeight > 0 && currentWeightVal > targetWeight * (1 + tolerancePercent / 100f)
    val isNear = targetWeight > 0 && currentWeightVal >= targetWeight * (1 - tolerancePercent / 100f)
    
    val statusColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isNear -> SmartDosingTokens.colors.success
        else -> MaterialTheme.colorScheme.primary
    }

    val backgroundColor = if (isManualMode) {
        MaterialTheme.colorScheme.surface
    } else {
        // Bluetooth LCD background
        Color(0xFFF0F5EE) 
    }
    
    val borderColor = if (isManualMode) {
        // Manual mode looks like an active input field
        MaterialTheme.colorScheme.primary
    } else {
        // Bluetooth mode border depends on stability or just subtle
        if (isStable) SmartDosingTokens.colors.success else MaterialTheme.colorScheme.outlineVariant
    }

    val displayColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isNear -> SmartDosingTokens.colors.success
        else -> MaterialTheme.colorScheme.onSurface
    }

    LabCard(
        modifier = modifier, 
        backgroundColor = backgroundColor, 
        borderColor = borderColor
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
             // Input Mode Indicator / Stability
             Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                 if (isManualMode) {
                     Text("手动输入中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                 } else {
                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                         Icon(
                             if (isStable) Icons.Default.CheckCircle else Icons.Default.Schedule,
                             contentDescription = null,
                             modifier = Modifier.size(12.dp),
                             tint = if (isStable) SmartDosingTokens.colors.success else MaterialTheme.colorScheme.outline
                         )
                         Text(
                             if (isStable) "稳定" else "读取中...", 
                             style = MaterialTheme.typography.labelSmall, 
                             color = if (isStable) SmartDosingTokens.colors.success else MaterialTheme.colorScheme.outline
                         )
                     }
                 }
             }
        
             Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterEnd) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Text(
                        text = if (weight.isBlank()) "0.0" else weight,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontFamily = FontFamily.Monospace), // Even Bigger
                        fontWeight = FontWeight.Bold,
                        color = displayColor
                     )
                     // Cursor simulation for manual mode
                     if (isManualMode) {
                         Box(
                             modifier = Modifier
                                 .padding(start = 4.dp)
                                 .width(3.dp)
                                 .height(64.dp)
                                 .background(MaterialTheme.colorScheme.primary)
                         )
                     }
                 }
             }
             
             if (targetWeight > 0) {
                 val progress = (currentWeightVal / targetWeight).coerceIn(0f, 1.2f)
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                     Text("目标: $targetWeight", style = MaterialTheme.typography.bodySmall)
                     Text("${(progress*100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                 }
                 LinearProgressIndicator(
                     progress = { progress / 1.2f },
                     modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                     color = statusColor,
                     trackColor = MaterialTheme.colorScheme.surfaceVariant
                 )
             }
        }
    }
}

@Composable
fun LabNumericKeypad(
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit
) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "⌫")
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Button(
                        onClick = { onKeyPress(key) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(SmartDosingTokens.radius.xs),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface // Dark text on light keys
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(key, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

class ChecklistItemState(val label: String, checked: Boolean = false) {
    var checked by mutableStateOf(checked)
}

data class OverLimitWarning(
    val materialName: String,
    val percent: Double
)

@Composable
fun OverLimitDialog(warning: OverLimitWarning, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            LabButton(onClick = onDismiss, text = "确认偏差并继续")
        },
        title = { LabSectionHeader("⚠️ 称量偏差警告") },
        text = {
            Text(
                text = "材料「${warning.materialName}」超出目标值 ${"%.2f".format(warning.percent)}%。\n请确认是否通过此偏差？",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        textContentColor = MaterialTheme.colorScheme.onErrorContainer,
        titleContentColor = MaterialTheme.colorScheme.onErrorContainer
    )
}

/**
 * 紧凑投料布局 - 专为手机小屏幕设计
 * 垂直堆叠布局：材料头部 -> 重量显示 -> 数字键盘 -> 操作按钮
 */
@Composable
fun CompactDosingLayout(
    modifier: Modifier = Modifier,
    material: Material,
    currentStep: Int,
    totalSteps: Int,
    actualWeight: String,
    onWeightChange: (String) -> Unit,
    onClearWeight: () -> Unit,
    onConfirmNext: () -> Unit,
    onRepeatAnnouncement: () -> Unit,
    tolerancePercent: Float
) {
    Column(
        modifier = modifier
            .padding(SmartDosingTokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm)
    ) {
        // 1. 紧凑材料信息头部 (~12%)
        LabCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "步骤 ${currentStep + 1}/$totalSteps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = material.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                LabStatusBadge(
                    text = "${material.targetWeight} ${material.unit}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 2. 大号重量显示区 (~20%)
        CompactWeightDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f),
            weight = actualWeight,
            targetWeight = material.targetWeight,
            tolerancePercent = tolerancePercent
        )
        
        // 3. 数字键盘 (~55%)
        LabNumericKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
            onKeyPress = { key ->
                when (key) {
                    "⌫" -> if (actualWeight.isNotEmpty()) onWeightChange(actualWeight.dropLast(1))
                    "." -> if (!actualWeight.contains(".") && actualWeight.isNotEmpty()) onWeightChange(actualWeight + key)
                    else -> onWeightChange(actualWeight + key)
                }
            }
        )
        
        // 4. 底部操作按钮 (~13%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.sm)
        ) {
            LabOutlinedButton(
                onClick = onClearWeight,
                text = "清空",
                modifier = Modifier.weight(1f)
            )
            LabOutlinedButton(
                onClick = onRepeatAnnouncement,
                text = "重播",
                modifier = Modifier.weight(1f)
            )
            LabButton(
                onClick = onConfirmNext,
                text = "下一步",
                modifier = Modifier.weight(2f),
                enabled = actualWeight.isNotBlank()
            )
        }
    }
}

/**
 * 紧凑重量显示组件 - 用于小屏幕
 */
@Composable
fun CompactWeightDisplay(
    modifier: Modifier = Modifier,
    weight: String,
    targetWeight: Float = 0f,
    tolerancePercent: Float = 0f
) {
    val currentWeightVal = weight.replace(",", ".").toFloatOrNull() ?: 0f
    
    val isOver = targetWeight > 0 && currentWeightVal > targetWeight * (1 + tolerancePercent / 100f)
    val isNear = targetWeight > 0 && currentWeightVal >= targetWeight * (1 - tolerancePercent / 100f)
    
    val displayColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isNear -> SmartDosingTokens.colors.success
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val progressPercent = if (targetWeight > 0) (currentWeightVal / targetWeight * 100).coerceIn(0f, 120f) else 0f
    
    LabCard(
        modifier = modifier,
        borderColor = displayColor.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 大号重量数字
            Text(
                text = if (weight.isBlank()) "0" else weight,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = displayColor
            )
            
            // 进度条
            LinearProgressIndicator(
                progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .padding(top = SmartDosingTokens.spacing.sm),
                color = displayColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // 状态文字
            Text(
                text = when {
                    isOver -> "超出目标 ⚠️"
                    isNear -> "已达标 ✓"
                    else -> "剩余 ${String.format("%.1f", targetWeight - currentWeightVal)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = displayColor,
                modifier = Modifier.padding(top = SmartDosingTokens.spacing.xs)
            )
        }
    }
}
