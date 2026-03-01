package com.example.smartdosing.ui.screens.dosing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.smartdosing.SmartDosingApplication
import com.example.smartdosing.audio.BeepMode
import com.example.smartdosing.audio.DosingBeepManager
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.bluetooth.model.WeightData
import com.example.smartdosing.data.*
import com.example.smartdosing.data.Material as DataMaterial
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.data.settings.DosingPreferencesManager
import com.example.smartdosing.data.settings.DosingPreferencesState
import com.example.smartdosing.ui.components.DosingBluetoothStatusBar
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import kotlinx.coroutines.delay
import com.example.smartdosing.utils.FormatUtils
import java.text.DecimalFormat

/**
 * 实验配置界面 - 实验室版核心作业页面
 * 支持大屏自适应布局与高密度物料清单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialConfigurationScreen(
    recipeId: String? = null,
    taskId: String = "",
    recordId: String = "",
    isViewOnly: Boolean = false,
    targetTotalWeight: Double? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToTaskCenter: () -> Unit = {},
    onSaveConfiguration: (MaterialConfigurationData) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseRecipeRepository.getInstance(context) }
    val taskRepository = remember { ConfigurationRepositoryProvider.taskRepository }
    val recordRepository = remember { ConfigurationRepositoryProvider.recordRepository }

    // 蓝牙电子秤相关
    val application = context.applicationContext as SmartDosingApplication
    val scaleManager = application.bluetoothScaleManager
    val bluetoothPreferencesManager = application.bluetoothPreferencesManager
    val demoManager = application.demoModeManager
    val bluetoothPreferences by bluetoothPreferencesManager.preferencesFlow.collectAsState(
        initial = BluetoothScalePreferencesManager.BluetoothScalePreferencesState()
    )

    // 演示模式状态
    val isDemoMode = bluetoothPreferences.demoModeEnabled
    val demoActive by demoManager.isActive.collectAsState()
    val demoWeight by demoManager.currentWeight.collectAsState()

    // 根据模式选择数据源
    val connectionState by scaleManager.connectionState.collectAsState()
    val bluetoothWeight by scaleManager.currentWeight.collectAsState()
    val currentWeight = if (isDemoMode && demoActive) demoWeight else bluetoothWeight
    val isBluetoothConnected = if (isDemoMode) demoActive else connectionState == ConnectionState.CONNECTED

    // 活动行状态
    var activeRowIndex by rememberSaveable { mutableIntStateOf(0) }
    
    // 放大显示状态
    var isMagnified by rememberSaveable { mutableStateOf(false) }

    // 完成确认对话框状态
    var showCompletionDialog by rememberSaveable { mutableStateOf(false) }

    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var materialStates by rememberSaveable(stateSaver = MaterialConfigStateListSaver) { mutableStateOf<List<MaterialConfigState>>(emptyList()) }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    
    // 语音播报逻辑（TTS 已软下线）
    fun announceMaterial(index: Int) {
        // TTS 已软下线，暂不播报
    }

    // 提示音管理器
    val dosingPreferencesManager = remember { DosingPreferencesManager(context) }
    val dosingPreferences by dosingPreferencesManager.preferencesFlow.collectAsState(
        initial = DosingPreferencesState()
    )
    val beepManager = remember {
        DosingBeepManager().also { it.initialize() }
    }

    // 页面退出时释放提示音资源
    DisposableEffect(Unit) {
        onDispose {
            beepManager.release()
        }
    }

    // 构建保存数据的辅助函数
    fun buildConfigurationData(): MaterialConfigurationData? {
        val r = recipe ?: return null
        return MaterialConfigurationData(
            recipeId = r.id,
            recipeCode = r.code,
            recipeName = r.name,
            taskId = taskId,
            recordId = recordId,
            materials = materialStates.map { state ->
                val actual = state.actualWeight.toDoubleOrNull() ?: 0.0
                MaterialConfigResult(
                    materialName = state.material.name,
                    materialCode = state.material.code,
                    targetWeight = state.material.weight,
                    actualWeight = actual,
                    unit = state.material.unit,
                    deviation = actual - state.material.weight,
                    deviationPercentage = if (state.material.weight > 0)
                        (actual - state.material.weight) / state.material.weight * 100 else 0.0
                )
            }
        )
    }

    // 加载数据
    LaunchedEffect(recipeId, taskId, recordId) {
        if (materialStates.isNotEmpty()) {
            if (recipe == null) {
                recipeId?.takeIf { it.isNotBlank() && it != "quick_start" }?.let {
                    recipe = repository.getRecipeById(it) ?: repository.getRecipeByCode(it)
                }
            }
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val normalizedId = recipeId?.takeIf { it.isNotBlank() && it != "quick_start" }
            var loadedRecipe = normalizedId?.let { id ->
                repository.getRecipeById(id) ?: repository.getRecipeByCode(id)
            }
            if (loadedRecipe == null && taskId.isNotBlank()) {
                val task = taskRepository.fetchTask(taskId)
                if (task != null) {
                    loadedRecipe = repository.getRecipeById(task.recipeId)
                        ?: repository.getRecipeByCode(task.recipeId)
                        ?: repository.getRecipeByCode(task.recipeCode)
                }
            }

            if (loadedRecipe != null) {
                recipe = loadedRecipe
                // 根据目标总重量计算缩放比例
                // 配方库模式：targetTotalWeight 由用户输入；任务模式：由 task.quantity 传入
                val scaleFactor = if (targetTotalWeight != null && loadedRecipe.totalWeight > 0) {
                    targetTotalWeight / loadedRecipe.totalWeight
                } else {
                    1.0 // 无缩放（只读查看或未指定总重量）
                }
                materialStates = loadedRecipe.materials.map { material ->
                    val scaledMaterial = material.copy(weight = material.weight * scaleFactor)
                    MaterialConfigState(scaledMaterial, "", isConfirmed = false, hasError = false)
                }
                // 首次进入播报第一行
                announceMaterial(0)
            } else {
                loadError = "未找到有效配方"
            }
        } catch (e: Exception) {
            loadError = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // 蓝牙连接状态变化监听：自动恢复自动获取模式
    LaunchedEffect(isBluetoothConnected) {
        if (isBluetoothConnected) {
            // 当天平连接成功时，自动清除所有待处理物料的手动覆盖标记，优先使用天平数据
            materialStates = materialStates.map { 
                if (!it.isConfirmed) it.copy(isManualOverride = false) else it 
            }
        }
    }

    // 蓝牙实时更新
    LaunchedEffect(currentWeight, activeRowIndex, isBluetoothConnected) {
        if (isBluetoothConnected && activeRowIndex < materialStates.size && 
            !materialStates[activeRowIndex].isConfirmed && 
            !materialStates[activeRowIndex].isManualOverride) {
            currentWeight?.let { weight ->
                materialStates = materialStates.toMutableList().apply {
                    this[activeRowIndex] = this[activeRowIndex].copy(
                        actualWeight = FormatUtils.formatWeight(weight.value),
                        hasError = false
                    )
                }
            }
        }
    }

    // 提示音触发逻辑
    LaunchedEffect(currentWeight?.value, activeRowIndex, dosingPreferences.beepMode) {
        if (dosingPreferences.beepMode == BeepMode.OFF) {
            beepManager.stopBeeping()
            return@LaunchedEffect
        }

        if (!isBluetoothConnected || activeRowIndex >= materialStates.size || materialStates[activeRowIndex].isConfirmed) {
            beepManager.stopBeeping()
            return@LaunchedEffect
        }

        val target = materialStates[activeRowIndex].material.weight
        val actual = currentWeight?.value ?: 0.0

        when (dosingPreferences.beepMode) {
            BeepMode.PROGRESSIVE -> {
                beepManager.updateProgressive(
                    actualWeight = actual,
                    targetWeight = target,
                    tolerancePermille = bluetoothPreferences.autoConfirmTolerancePermille
                )
            }
            BeepMode.THRESHOLD -> {
                beepManager.updateThreshold(
                    actualWeight = actual,
                    targetWeight = target,
                    thresholdPercent = dosingPreferences.beepThresholdPercent,
                    continuous = dosingPreferences.beepThresholdContinuous,
                    tolerancePermille = bluetoothPreferences.autoConfirmTolerancePermille
                )
            }
            BeepMode.OFF -> { /* 不会到这里 */ }
        }
    }

    // 切换物料时重置提示音状态
    LaunchedEffect(activeRowIndex) {
        beepManager.resetThresholdState()
    }

    // 自动确认逻辑 (保持原有核心逻辑，仅作微调)
    var stableStartTime by remember { mutableStateOf<Long?>(null) }
    var autoConfirmCountdown by remember { mutableIntStateOf(-1) } // 倒计时剩余秒数，-1表示未计时
    val autoConfirmEnabled = bluetoothPreferences.autoConfirmOnStable
    val autoConfirmDelayMs = bluetoothPreferences.autoConfirmDelaySeconds * 1000L
    val autoConfirmTolerancePermille = bluetoothPreferences.autoConfirmTolerancePermille

    LaunchedEffect(currentWeight?.isStable, currentWeight?.value, activeRowIndex, autoConfirmEnabled, isBluetoothConnected) {
        if (!autoConfirmEnabled || !isBluetoothConnected || activeRowIndex >= materialStates.size || materialStates[activeRowIndex].isConfirmed) {
            stableStartTime = null
            autoConfirmCountdown = -1
            return@LaunchedEffect
        }

        val target = materialStates[activeRowIndex].material.weight
        val actual = currentWeight?.value ?: 0.0
        val tolerance = target * autoConfirmTolerancePermille / 1000.0
        val inTolerance = if (target <= 0) actual > 0 else actual in (target - tolerance)..(target + tolerance)

        if (currentWeight?.isStable == true && actual > 0 && inTolerance) {
            if (stableStartTime == null) stableStartTime = System.currentTimeMillis()
        } else {
            stableStartTime = null
            autoConfirmCountdown = -1
        }
    }

    LaunchedEffect(stableStartTime) {
        if (stableStartTime != null) {
            val totalSeconds = (autoConfirmDelayMs / 1000).toInt()
            autoConfirmCountdown = totalSeconds
            while (autoConfirmCountdown > 0) {
                delay(1000)
                if (stableStartTime == null) {
                    autoConfirmCountdown = -1
                    return@LaunchedEffect
                }
                autoConfirmCountdown--
            }
            
            // 执行确认
            val index = activeRowIndex
            materialStates = materialStates.toMutableList().apply {
                val state = this[index]
                this[index] = state.copy(isConfirmed = true)
            }

            // TTS 已软下线
            // TTSManagerFactory.speak("完成 ${materialStates[index].material.name}")

            // 确认后自动去皮
            if (bluetoothPreferences.autoTareOnConfirm) {
                scaleManager.tare()
            }

            // 跳到下一行
            val next = materialStates.indexOfFirst { !it.isConfirmed }
            if (next >= 0) {
                activeRowIndex = next
                announceMaterial(next)
            } else {
                showCompletionDialog = true
            }
            stableStartTime = null
            autoConfirmCountdown = -1
        }
    }

    Scaffold(
        topBar = {
            LabConfigurationTopBar(
                recipeName = recipe?.name ?: if (isViewOnly) "配方详情" else "实验配置",
                progress = if (materialStates.isNotEmpty()) materialStates.count { it.isConfirmed }.toFloat() / materialStates.size else 0f,
                connectionState = connectionState,
                onBack = onNavigateBack,
                isViewOnly = isViewOnly
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                isLoading -> MaterialConfigurationLoadingState()
                recipe == null || materialStates.isEmpty() -> MaterialConfigurationEmptyState(loadError ?: "暂无配方数据", onNavigateBack)
                else -> {
                    val windowSize = LocalWindowSize.current
                    if (windowSize.widthClass == SmartDosingWindowWidthClass.Expanded) {
                        LargeScreenLayout(
                            recipe = recipe!!,
                            materialStates = materialStates,
                            activeRowIndex = activeRowIndex,
                            scaleManager = scaleManager,
                            currentWeight = currentWeight,
                            isBluetoothConnected = isBluetoothConnected,
                            isViewOnly = isViewOnly,
                            onRowClick = {
                                activeRowIndex = it
                                announceMaterial(it)
                            },
                            onConfirm = {
                                stableStartTime = null
                                autoConfirmCountdown = -1
                                materialStates = materialStates.toMutableList().apply {
                                    this[activeRowIndex] = this[activeRowIndex].copy(isConfirmed = true)
                                }
                                // 确认后自动去皮
                                if (bluetoothPreferences.autoTareOnConfirm) {
                                    scaleManager.tare()
                                }
                                val next = materialStates.indexOfFirst { !it.isConfirmed }
                                if (next >= 0) {
                                    activeRowIndex = next
                                    announceMaterial(next)
                                } else {
                                    showCompletionDialog = true
                                }
                            },
                            onSave = {
                                buildConfigurationData()?.let { onSaveConfiguration(it) }
                            },
                            onToggleMagnify = { isMagnified = true },
                            onWeightChange = { index, newWeight ->
                                materialStates = materialStates.toMutableList().apply {
                                    if (newWeight == "RESET_AUTO") {
                                        this[index] = this[index].copy(isManualOverride = false)
                                    } else {
                                        this[index] = this[index].copy(
                                            actualWeight = newWeight,
                                            isManualOverride = true
                                        )
                                    }
                                }
                            },
                            autoConfirmCountdown = autoConfirmCountdown
                        )
                    } else {
                        CompactScreenLayout(
                            recipe = recipe!!,
                            materialStates = materialStates,
                            activeRowIndex = activeRowIndex,
                            scaleManager = scaleManager,
                            currentWeight = currentWeight,
                            isBluetoothConnected = isBluetoothConnected,
                            isViewOnly = isViewOnly,
                            onRowClick = {
                                activeRowIndex = it
                                announceMaterial(it)
                            },
                            onConfirm = {
                                stableStartTime = null
                                autoConfirmCountdown = -1
                                materialStates = materialStates.toMutableList().apply {
                                    this[activeRowIndex] = this[activeRowIndex].copy(isConfirmed = true)
                                }
                                // 确认后自动去皮
                                if (bluetoothPreferences.autoTareOnConfirm) {
                                    scaleManager.tare()
                                }
                                val next = materialStates.indexOfFirst { !it.isConfirmed }
                                if (next >= 0) {
                                    activeRowIndex = next
                                    announceMaterial(next)
                                } else {
                                    showCompletionDialog = true
                                }
                            },
                            onSave = {
                                buildConfigurationData()?.let { onSaveConfiguration(it) }
                            },
                            onToggleMagnify = { isMagnified = true },
                            onWeightChange = { index, newWeight ->
                                materialStates = materialStates.toMutableList().apply {
                                    if (newWeight == "RESET_AUTO") {
                                        this[index] = this[index].copy(isManualOverride = false)
                                    } else {
                                        this[index] = this[index].copy(
                                            actualWeight = newWeight,
                                            isManualOverride = true
                                        )
                                    }
                                }
                            },
                            autoConfirmCountdown = autoConfirmCountdown
                        )
                    }
                }
            }

            // 放大显示层
            if (isMagnified && activeRowIndex < materialStates.size) {
                val activeState = materialStates[activeRowIndex]
                MagnifiedWeightOverlay(
                    materialName = activeState.material.name,
                    targetWeight = activeState.material.weight,
                    currentWeight = currentWeight,
                    isStable = currentWeight?.isStable == true,
                    onDismiss = { isMagnified = false },
                    onTare = { scaleManager.tare() },
                    onConfirm = {
                        stableStartTime = null
                        autoConfirmCountdown = -1
                        materialStates = materialStates.toMutableList().apply {
                            this[activeRowIndex] = this[activeRowIndex].copy(isConfirmed = true)
                        }
                        // 确认后自动去皮
                        if (bluetoothPreferences.autoTareOnConfirm) {
                            scaleManager.tare()
                        }
                        val next = materialStates.indexOfFirst { !it.isConfirmed }
                        if (next >= 0) {
                            activeRowIndex = next
                            announceMaterial(next)
                        } else {
                            // 全部完成，自动退出放大模式并弹出完成对话框
                            isMagnified = false
                            showCompletionDialog = true
                        }
                    },
                    isConfirmed = activeState.isConfirmed,
                    autoConfirmCountdown = autoConfirmCountdown
                )
            }

            // 完成确认对话框
            if (showCompletionDialog && !isViewOnly) {
                val confirmedCount = materialStates.count { it.isConfirmed }
                val totalCount = materialStates.size

                AlertDialog(
                    onDismissRequest = { showCompletionDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            "配置完成",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "所有物料已确认完成，是否保存记录？",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "配方: ${recipe?.name ?: ""}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "物料数量: $confirmedCount / $totalCount",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCompletionDialog = false
                                buildConfigurationData()?.let { onSaveConfiguration(it) }
                            }
                        ) {
                            Text("保存记录")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCompletionDialog = false }) {
                            Text("继续修改")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 实验室专用常驻标题栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabConfigurationTopBar(
    recipeName: String,
    progress: Float,
    connectionState: ConnectionState,
    onBack: () -> Unit,
    isViewOnly: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            TopAppBar(
                title = {
                    Column {
                        Text(recipeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isViewOnly) "配方详情查看" else "实验配置作业中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 只读模式不显示天平状态
                    if (!isViewOnly) {
                        // 天平状态微型徽章
                        Surface(
                            color = when(connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(0.1f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, when(connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50).copy(0.5f)
                                else -> MaterialTheme.colorScheme.outline.copy(0.3f)
                            })
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(if (connectionState == ConnectionState.CONNECTED) Color(0xFF4CAF50) else Color.Gray, CircleShape))
                                Text(if (connectionState == ConnectionState.CONNECTED) "天平就绪" else "天平未连接", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                }
            )
            // 只读模式不显示进度条
            if (!isViewOnly) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

/**
 * 大屏双栏布局
 */
@Composable
private fun LargeScreenLayout(
    recipe: Recipe,
    materialStates: List<MaterialConfigState>,
    activeRowIndex: Int,
    scaleManager: BluetoothScaleManager,
    currentWeight: WeightData?,
    isBluetoothConnected: Boolean,
    isViewOnly: Boolean = false,
    onRowClick: (Int) -> Unit,
    onConfirm: () -> Unit,
    onSave: () -> Unit,
    onToggleMagnify: () -> Unit,
    onWeightChange: (Int, String) -> Unit,
    autoConfirmCountdown: Int = -1
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：物料清单 (40% 宽度)
        Box(modifier = Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("物料清单", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                itemsIndexed(materialStates) { index, state ->
                    MaterialCompactItem(
                        index = index + 1,
                        state = state,
                        isActive = activeRowIndex == index,
                        onClick = { onRowClick(index) }
                    )
                }
            }
        }

        // 右侧：配置工作台 (60% 宽度)
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val activeState = materialStates.getOrNull(activeRowIndex) ?: return@Column
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("当前物料配置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggleMagnify) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "放大显示")
                }
            }
            
            // 物料详情卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(activeState.material.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                            Text("第 ${activeRowIndex + 1} / ${materialStates.size} 步", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        if (activeState.material.code.isNotBlank()) {
                            Text("编码: ${activeState.material.code}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("目标 ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatWeight(activeState.material.weight), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Text(" g", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            // 重量显示与手动输入
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 重量显示仪
                Surface(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, if (currentWeight?.isStable == true) Color(0xFF4CAF50) else Color(0xFF666666))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RollingWeightDisplay(
                                weight = currentWeight?.getDisplayValue() ?: "0.000",
                                style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 72.sp),
                                color = if (currentWeight?.isStable == true) Color(0xFF4CAF50) else Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("g", color = Color.Gray, style = MaterialTheme.typography.titleLarge)
                                if (currentWeight?.isStable == true) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                                    Text("稳定", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                // 手动输入入口
                OutlinedTextField(
                    value = activeState.actualWeight,
                    onValueChange = { onWeightChange(activeRowIndex, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (activeState.isManualOverride) "手动模式 (已校准)" else "天平实时同步 (点击可手动校准)") },
                    placeholder = { Text("请输入实际称重数值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        if (activeState.isManualOverride) {
                            IconButton(onClick = { 
                                // 通过传递空字符串或特定信号来重置手动覆盖
                                onWeightChange(activeRowIndex, "RESET_AUTO") 
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "恢复自动同步", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            // 操作区
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { scaleManager.tare() },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Balance, null)
                    Spacer(Modifier.width(8.dp))
                    Text("去皮")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(2f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !activeState.isConfirmed,
                    colors = if (autoConfirmCountdown > 0) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
                ) {
                    if (autoConfirmCountdown > 0) {
                        Icon(Icons.Default.Timer, null)
                    } else {
                        Icon(Icons.Default.Check, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (autoConfirmCountdown > 0) {
                        Text("${autoConfirmCountdown}s 后自动确认")
                    } else {
                        Text("确认完成")
                    }
                }
            }

            PrecisionMeter(
                targetWeight = activeState.material.weight,
                actualWeight = currentWeight?.value ?: activeState.actualWeight.toDoubleOrNull() ?: 0.0,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.weight(1f))

            // 只读模式不显示保存按钮
            if (!isViewOnly) {
                val allDone = materialStates.all { it.isConfirmed }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    enabled = allDone,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("完成实验并保存记录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 紧凑型小屏布局 (原有逻辑优化)
 */
@Composable
private fun CompactScreenLayout(
    recipe: Recipe,
    materialStates: List<MaterialConfigState>,
    activeRowIndex: Int,
    scaleManager: BluetoothScaleManager,
    currentWeight: WeightData?,
    isBluetoothConnected: Boolean,
    isViewOnly: Boolean = false,
    onRowClick: (Int) -> Unit,
    onConfirm: () -> Unit,
    onSave: () -> Unit,
    onToggleMagnify: () -> Unit,
    onWeightChange: (Int, String) -> Unit,
    autoConfirmCountdown: Int = -1
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 当前活动项卡片
        val activeState = materialStates.getOrNull(activeRowIndex)
        activeState?.let { state ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("当前物料: ${state.material.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (state.material.code.isNotBlank()) {
                                Text("物料编码: ${state.material.code}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                        }
                        IconButton(onClick = onToggleMagnify) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "放大显示")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // 当前重量 (左侧)
                        Text(
                            text = currentWeight?.getDisplayValue() ?: "0.000",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (currentWeight?.isStable == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )

                        // 目标重量 (右侧利用空余空间)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("目标重量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = FormatUtils.formatWeight(state.material.weight),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(" g", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                    }

                    // 只读模式不显示手动输入框
                    if (!isViewOnly) {
                        // 紧凑版手动输入
                        OutlinedTextField(
                            value = state.actualWeight,
                            onValueChange = { onWeightChange(activeRowIndex, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("手动输入实际重量 (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.5f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    PrecisionMeter(
                        targetWeight = state.material.weight,
                        actualWeight = currentWeight?.value ?: state.actualWeight.toDoubleOrNull() ?: 0.0
                    )

                    // 只读模式不显示确认按钮
                    if (!isViewOnly) {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isConfirmed,
                            colors = if (autoConfirmCountdown > 0) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
                        ) {
                            if (autoConfirmCountdown > 0) {
                                Icon(Icons.Default.Timer, null)
                                Spacer(Modifier.width(4.dp))
                                Text("${autoConfirmCountdown}s 后自动确认")
                            } else {
                                Text("确认当前物料")
                            }
                        }
                    }
                }
            }
        }

        // 物料列表
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(materialStates) { index, state ->
                MaterialCompactItem(index + 1, state, index == activeRowIndex) { onRowClick(index) }
            }
        }
    }
}

@Composable
private fun MaterialCompactItem(
    index: Int,
    state: MaterialConfigState,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(0.1f) else if (state.isConfirmed) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(28.dp).background(if (state.isConfirmed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                if (state.isConfirmed) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                else Text(index.toString(), style = MaterialTheme.typography.labelMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(state.material.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                if (state.material.code.isNotBlank()) {
                    Text("编码: ${state.material.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Text("目标: ${FormatUtils.formatWeight(state.material.weight)} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isConfirmed) {
                Text("${state.actualWeight} g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
private fun MaterialConfigurationLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MaterialConfigurationEmptyState(msg: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Warning, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(msg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}

/**
 * 精密进度条 (Precision Meter)
 */
@Composable
private fun PrecisionMeter(
    targetWeight: Double,
    actualWeight: Double,
    tolerancePermille: Int = 10,
    modifier: Modifier = Modifier
) {
    if (targetWeight <= 0) return

    val progress = (actualWeight / targetWeight).coerceIn(0.0, 1.2).toFloat()
    val tolerance = targetWeight * tolerancePermille / 1000.0
    val isInTolerance = actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)
    val isOver = actualWeight > (targetWeight + tolerance)
    val isNear = actualWeight > (targetWeight * 0.9) && !isInTolerance && !isOver

    val meterColor by animateColorAsState(
        targetValue = when {
            isOver -> MaterialTheme.colorScheme.error
            isInTolerance -> Color(0xFF4CAF50)
            isNear -> Color(0xFFFFC107)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "color"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // 背景刻度
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val tickCount = 10
                val tickWidth = 2.dp.toPx()
                for (i in 1 until tickCount) {
                    val x = this.size.width * (i.toFloat() / tickCount)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.2f),
                        start = Offset(x, 0f),
                        end = Offset(x, this.size.height),
                        strokeWidth = tickWidth
                    )
                }
            }

            // 进度条
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(meterColor.copy(alpha = 0.7f), meterColor)
                        )
                    )
            )

            // 目标指示器 (100% 处)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(2.dp)
                    .graphicsLayer { translationX = size.width * 0.833f } // 1.0 / 1.2 = 0.833
                    .background(Color.White)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isOver) "注意：已过量" else if (isInTolerance) "完美：进入公差范围" else if (isNear) "减速：接近目标值" else "加注中...",
                style = MaterialTheme.typography.labelSmall,
                color = meterColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 数字滚动动画显示
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun RollingWeightDisplay(
    weight: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color
) {
    AnimatedContent(
        targetState = weight,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { height -> height } + fadeIn() with
                        slideOutVertically { height -> -height } + fadeOut()
            } else {
                slideInVertically { height -> -height } + fadeIn() with
                        slideOutVertically { height -> height } + fadeOut()
            }.using(SizeTransform(clip = false))
        },
        label = "weight_rolling"
    ) { targetWeight ->
        Text(
            text = targetWeight,
            style = style,
            color = color,
            softWrap = false
        )
    }
}
data class MaterialConfigState(
    val material: DataMaterial, 
    val actualWeight: String, 
    val isConfirmed: Boolean, 
    val hasError: Boolean,
    val isManualOverride: Boolean = false
)

val MaterialConfigStateListSaver = Saver<List<MaterialConfigState>, ArrayList<ArrayList<Any>>>(
    save = { states -> ArrayList(states.map { arrayListOf(it.material.id, it.material.name, it.material.weight, it.material.unit, it.material.sequence, it.material.notes, it.material.code, it.actualWeight, it.isConfirmed, it.hasError, it.isManualOverride) }) },
    restore = { lists -> lists.map { MaterialConfigState(DataMaterial(it[0] as String, it[1] as String, it[2] as Double, it[3] as String, it[4] as Int, it[5] as String, it[6] as String), it[7] as String, it[8] as Boolean, it[9] as Boolean, if (it.size > 10) it[10] as Boolean else false) } }
)

data class MaterialConfigurationData(
    val recipeId: String,
    val recipeCode: String,
    val recipeName: String,
    val taskId: String = "",
    val recordId: String = "",
    val materials: List<MaterialConfigResult>,
    val perfumer: String = "",
    val customer: String = "",
    val salesOwner: String = "",
    val notes: String = ""
)

data class MaterialConfigResult(
    val materialName: String,
    val materialCode: String,
    val targetWeight: Double,
    val actualWeight: Double,
    val unit: String,
    val deviation: Double,
    val deviationPercentage: Double
)

/**
 * 放大重量显示全屏遮罩
 */
@Composable
private fun MagnifiedWeightOverlay(
    materialName: String,
    targetWeight: Double,
    currentWeight: WeightData?,
    isStable: Boolean,
    onDismiss: () -> Unit,
    onTare: () -> Unit,
    onConfirm: () -> Unit,
    isConfirmed: Boolean,
    autoConfirmCountdown: Int = -1
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = materialName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "目标: $targetWeight g",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "退出放大",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // 中间巨大数字
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingWeightDisplay(
                    weight = currentWeight?.getDisplayValue() ?: "0.000",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 160.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = if (isStable) Color(0xFF4CAF50) else Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("g", color = Color.Gray, style = MaterialTheme.typography.displaySmall)
                    if (isStable) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                        Text("稳定", color = Color(0xFF4CAF50), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            PrecisionMeter(
                targetWeight = targetWeight,
                actualWeight = currentWeight?.value ?: 0.0,
                modifier = Modifier.padding(horizontal = 64.dp)
            )

            // 底部操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Button(
                    onClick = onTare,
                    modifier = Modifier.weight(1f).height(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Balance, null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("去皮", fontSize = 24.sp)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(2f).height(100.dp),
                    enabled = !isConfirmed,
                    colors = if (autoConfirmCountdown > 0) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    if (autoConfirmCountdown > 0) {
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    if (autoConfirmCountdown > 0) {
                        Text("${autoConfirmCountdown}s 后自动确认", fontSize = 24.sp)
                    } else {
                        Text("确认当前物料", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
