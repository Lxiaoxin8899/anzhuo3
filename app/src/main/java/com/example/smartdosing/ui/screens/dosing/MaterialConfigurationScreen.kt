package com.example.smartdosing.ui.screens.dosing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.Material as DataMaterial
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.SmartDosingApplication
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.bluetooth.model.WeightData
import com.example.smartdosing.ui.components.DosingBluetoothStatusBar
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import kotlinx.coroutines.delay
import java.text.DecimalFormat

/**
 * 材料配置界面 - 研发配置核心页面
 * 用于配置配方中各材料的实际重量和确认状态
 */
@Composable
fun MaterialConfigurationScreen(
    recipeId: String? = null,
    taskId: String = "",
    recordId: String = "",
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

    // 活动行状态（当前正在接收蓝牙重量的行）
    var activeRowIndex by remember { mutableStateOf<Int?>(null) }

    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var materialStates by remember { mutableStateOf<List<MaterialConfigState>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var customer by remember { mutableStateOf("") }
    var salesOwner by remember { mutableStateOf("") }
    var perfumer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var currentTaskId by remember { mutableStateOf(taskId) }
    var currentRecordId by remember { mutableStateOf(recordId) }
    var showTaskSelectionHint by remember { mutableStateOf(false) }

    fun resetMetaInfo() {
        customer = ""
        salesOwner = ""
        perfumer = ""
        notes = ""
    }

    // 统一封装配方与材料状态的赋值，避免多处重复代码
    fun updateRecipeState(targetRecipe: Recipe) {
        recipe = targetRecipe
        customer = targetRecipe.customer
        salesOwner = targetRecipe.salesOwner
        perfumer = targetRecipe.perfumer
        notes = targetRecipe.description
        materialStates = targetRecipe.materials.map { material ->
            android.util.Log.d("MatConfigScreen", "MaterialState: name=${material.name}, code='${material.code}'")
            MaterialConfigState(
                material = material,
                actualWeight = "",
                isConfirmed = false,
                hasError = false
            )
        }
    }

    // 加载配方数据
    LaunchedEffect(recipeId, taskId, recordId) {
        isLoading = true
        loadError = null
        showTaskSelectionHint = false
        try {
            val normalizedId = recipeId?.takeIf { it.isNotBlank() && it != "quick_start" }
            val cachedRecord: ConfigurationRecord? = if (recordId.isNotBlank()) {
                recordRepository.fetchRecord(recordId)?.also { record ->
                    currentRecordId = record.id
                }
            } else {
                null
            }
            val recipeFromRecord = cachedRecord?.let { record ->
                repository.getRecipeById(record.recipeId)
                    ?: repository.getRecipeByCode(record.recipeCode)
            }
            val loadedRecipe = when {
                normalizedId != null -> repository.getRecipeById(normalizedId) ?: recipeFromRecord
                else -> recipeFromRecord
            }

            if (loadedRecipe != null) {
                updateRecipeState(loadedRecipe)
                loadError = null
            } else {
                val shouldGuideTask = normalizedId == null && recordId.isBlank()
                loadError = if (shouldGuideTask) {
                    "当前没有可用配方，请先导入配方或前往任务中心选择任务后再进入快速配料。"
                } else {
                    val missingId = normalizedId ?: recipeId.orEmpty()
                    "未找到配方（ID: $missingId），请确认配方是否存在或重新导入。"
                }
                recipe = null
                materialStates = emptyList()
                resetMetaInfo()
                showTaskSelectionHint = shouldGuideTask
            }

            if (taskId.isNotBlank()) {
                taskRepository.fetchTask(taskId)?.let { task ->
                    currentTaskId = task.id
                    customer = task.customer
                    salesOwner = task.salesOwner
                    perfumer = task.requestedBy
                }
            }

            if (recordId.isNotBlank()) {
                (cachedRecord ?: recordRepository.fetchRecord(recordId))?.let { record ->
                    currentRecordId = record.id
                    customer = record.customer
                    salesOwner = record.salesOwner
                    perfumer = record.operator
                    notes = record.note
                }
            }
        } catch (e: Exception) {
            loadError = "加载配方失败：${e.message ?: "未知错误"}"
            recipe = null
            materialStates = emptyList()
            resetMetaInfo()
        } finally {
            isLoading = false
        }
    }

    // 当蓝牙连接且有活动行时，实时更新活动行的重量
    LaunchedEffect(currentWeight, activeRowIndex, isBluetoothConnected) {
        if (isBluetoothConnected && activeRowIndex != null && currentWeight != null) {
            val index = activeRowIndex!!
            if (index < materialStates.size && !materialStates[index].isConfirmed) {
                // 更新活动行的重量为蓝牙读取的重量
                materialStates = materialStates.toMutableList().apply {
                    this[index] = this[index].copy(
                        actualWeight = currentWeight!!.value.toString(),
                        hasError = false
                    )
                }
            }
        }
    }

    // 自动确认逻辑：稳定后等待指定时间自动确认（需在误差范围内）
    var stableStartTime by remember { mutableStateOf<Long?>(null) }
    val autoConfirmEnabled = bluetoothPreferences.autoConfirmOnStable
    val autoConfirmDelayMs = bluetoothPreferences.autoConfirmDelaySeconds * 1000L
    val autoTareOnConfirm = bluetoothPreferences.autoTareOnConfirm
    val autoConfirmTolerancePermille = bluetoothPreferences.autoConfirmTolerancePermille

    // 检查当前重量是否在目标重量的误差范围内（使用千分比‰）
    fun isWeightWithinTolerance(actualWeight: Double, targetWeight: Double, tolerancePermille: Int): Boolean {
        if (targetWeight <= 0) return actualWeight > 0 // 目标为0时，只要有重量就算合格
        val tolerance = targetWeight * tolerancePermille / 1000.0 // 千分比转换
        val lowerBound = targetWeight - tolerance
        val upperBound = targetWeight + tolerance
        return actualWeight in lowerBound..upperBound
    }

    LaunchedEffect(currentWeight?.isStable, currentWeight?.value, activeRowIndex, autoConfirmEnabled) {
        if (!autoConfirmEnabled || activeRowIndex == null || !isBluetoothConnected) {
            stableStartTime = null
            return@LaunchedEffect
        }

        val index = activeRowIndex!!
        if (index >= materialStates.size || materialStates[index].isConfirmed) {
            stableStartTime = null
            return@LaunchedEffect
        }

        val targetWeight = materialStates[index].material.weight
        val actualWeight = currentWeight?.value ?: 0.0
        val isWithinTolerance = isWeightWithinTolerance(actualWeight, targetWeight, autoConfirmTolerancePermille)

        if (currentWeight?.isStable == true && actualWeight > 0 && isWithinTolerance) {
            // 重量稳定、大于0、且在误差范围内，开始计时
            if (stableStartTime == null) {
                stableStartTime = System.currentTimeMillis()
            }

            // 等待指定时间后自动确认
            delay(autoConfirmDelayMs)

            // 再次检查条件（可能在等待期间状态已改变）
            val currentActualWeight = currentWeight?.value ?: 0.0
            val stillWithinTolerance = isWeightWithinTolerance(currentActualWeight, targetWeight, autoConfirmTolerancePermille)

            if (activeRowIndex == index &&
                currentWeight?.isStable == true &&
                stillWithinTolerance &&
                !materialStates[index].isConfirmed
            ) {
                // 自动确认
                materialStates = materialStates.toMutableList().apply {
                    val state = this[index]
                    val weight = state.actualWeight.toDoubleOrNull()
                    if (weight != null && weight > 0) {
                        this[index] = state.copy(isConfirmed = true, hasError = false)
                    }
                }

                // 自动去皮（如果启用）
                if (autoTareOnConfirm) {
                    if (isDemoMode) {
                        demoManager.simulateTare()
                    } else {
                        scaleManager.tare()
                    }
                }

                // 自动跳到下一个未确认的行
                val nextUnconfirmedIndex = materialStates.indexOfFirst { !it.isConfirmed }
                activeRowIndex = if (nextUnconfirmedIndex >= 0) nextUnconfirmedIndex else null

                stableStartTime = null
            }
        } else {
            // 重量不稳定、为0、或不在误差范围内，重置计时
            stableStartTime = null
        }
    }

    when {
        isLoading -> {
            MaterialConfigurationLoadingState(modifier = modifier)
        }
        recipe == null || materialStates.isEmpty() -> {
            MaterialConfigurationEmptyState(
                message = loadError ?: "当前配方没有配置材料，请返回后重新选择。",
                onNavigateBack = onNavigateBack,
                actionLabel = if (showTaskSelectionHint) "前往任务中心" else null,
                onAction = if (showTaskSelectionHint) onNavigateToTaskCenter else null,
                modifier = modifier
            )
        }
        else -> {
            MaterialConfigurationContent(
                recipe = recipe,
                materialStates = materialStates,
                customer = customer,
                salesOwner = salesOwner,
                perfumer = perfumer,
                notes = notes,
                taskId = currentTaskId,
                recordId = currentRecordId,
                // 蓝牙相关参数
                scaleManager = scaleManager,
                deviceAlias = bluetoothPreferences.deviceAlias,
                activeRowIndex = activeRowIndex,
                onActiveRowChange = { index ->
                    activeRowIndex = index
                    // 演示模式下，切换活动行时自动模拟投料
                    if (isDemoMode && demoActive && index != null && index < materialStates.size) {
                        val targetWeight = materialStates[index].material.weight
                        demoManager.simulateWeighing(targetWeight)
                    }
                },
                isDemoMode = isDemoMode,
                autoConfirmEnabled = autoConfirmEnabled,
                autoConfirmDelaySeconds = bluetoothPreferences.autoConfirmDelaySeconds,
                autoConfirmTolerancePermille = autoConfirmTolerancePermille,
                stableStartTime = stableStartTime,
                // 回调
                onCustomerChange = { customer = it },
                onSalesOwnerChange = { salesOwner = it },
                onPerfumerChange = { perfumer = it },
                onNotesChange = { notes = it },
                onMaterialWeightChanged = { index, weight ->
                    materialStates = materialStates.toMutableList().apply {
                        this[index] = this[index].copy(actualWeight = weight, hasError = false)
                    }
                },
                onMaterialConfirmed = { index ->
                    materialStates = materialStates.toMutableList().apply {
                        val state = this[index]
                        val weight = state.actualWeight.toDoubleOrNull()
                        if (weight != null && weight > 0) {
                            this[index] = state.copy(isConfirmed = true, hasError = false)
                        } else {
                            this[index] = state.copy(hasError = true)
                        }
                    }
                    // 手动确认后也执行自动去皮和跳转
                    if (autoTareOnConfirm && isBluetoothConnected) {
                        if (isDemoMode) {
                            demoManager.simulateTare()
                        } else {
                            scaleManager.tare()
                        }
                    }
                    // 自动跳到下一个未确认的行
                    val nextUnconfirmedIndex = materialStates.indexOfFirst { !it.isConfirmed }
                    activeRowIndex = if (nextUnconfirmedIndex >= 0) nextUnconfirmedIndex else null
                },
                onMaterialEdit = { index ->
                    materialStates = materialStates.toMutableList().apply {
                        this[index] = this[index].copy(isConfirmed = false, hasError = false)
                    }
                    // 编辑时设为活动行
                    activeRowIndex = index
                },
                onSaveConfiguration = { configData ->
                    onSaveConfiguration(
                        configData.copy(
                            taskId = currentTaskId,
                            recordId = currentRecordId
                        )
                    )
                },
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }
    }
}

/**
 * 材料配置状态
 */
data class MaterialConfigState(
    val material: DataMaterial,
    val actualWeight: String,
    val isConfirmed: Boolean,
    val hasError: Boolean
)

/**
 * 材料配置数据
 */
data class MaterialConfigurationData(
    val recipeId: String,
    val recipeCode: String,
    val recipeName: String,
    val taskId: String = "",
    val recordId: String = "",
    val customer: String = "",
    val salesOwner: String = "",
    val perfumer: String = "",
    val notes: String = "",
    val materials: List<MaterialConfigResult>
)

data class MaterialConfigResult(
    val materialName: String,
    val targetWeight: Double,
    val actualWeight: Double,
    val deviation: Double,
    val deviationPercentage: Double,
    val unit: String = "g",
    val materialCode: String = ""
)

/**
 * 材料配置加载占位，防止加载过程出现白屏
 */
@Composable
private fun MaterialConfigurationLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在加载配方，请稍候…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 没有配方或材料时的提示界面
 */
@Composable
private fun MaterialConfigurationEmptyState(
    message: String,
    onNavigateBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "可返回重新选择配方，或根据提示完成配方导入/任务选择后再次进入。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onNavigateBack) {
                Text("返回上一页")
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * 材料配置界面内容
 */
@Composable
private fun MaterialConfigurationContent(
    recipe: Recipe?,
    materialStates: List<MaterialConfigState>,
    customer: String,
    salesOwner: String,
    perfumer: String,
    notes: String,
    taskId: String,
    recordId: String,
    // 蓝牙相关参数（可选，用于 Preview）
    scaleManager: BluetoothScaleManager? = null,
    deviceAlias: String? = null,
    activeRowIndex: Int? = null,
    onActiveRowChange: (Int?) -> Unit = {},
    isDemoMode: Boolean = false,
    autoConfirmEnabled: Boolean = false,
    autoConfirmDelaySeconds: Int = 10,
    autoConfirmTolerancePermille: Int = 10, // 默认10‰（千分之十，即1%）
    stableStartTime: Long? = null,
    // 回调
    onCustomerChange: (String) -> Unit,
    onSalesOwnerChange: (String) -> Unit,
    onPerfumerChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onMaterialWeightChanged: (Int, String) -> Unit,
    onMaterialConfirmed: (Int) -> Unit,
    onMaterialEdit: (Int) -> Unit,
    onSaveConfiguration: (MaterialConfigurationData) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSize = LocalWindowSize.current
    val useCompactLayout = windowSize.widthClass == SmartDosingWindowWidthClass.Compact
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部标题和进度
        recipe?.let {
            RecipeHeader(
                recipe = it,
                completedCount = materialStates.count { it.isConfirmed },
                totalCount = materialStates.size
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 演示模式或蓝牙电子秤状态栏
        if (isDemoMode) {
            // 演示模式状态栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "演示模式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )
                            Text(
                                text = "点击物料行开始模拟投料",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1976D2)
                            )
                        }
                    }
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else if (scaleManager != null) {
            DosingBluetoothStatusBar(
                scaleManager = scaleManager,
                deviceAlias = deviceAlias,
                onConnectClick = {
                    // 触发自动连接（如果有绑定设备）
                    // 这里可以导航到蓝牙设置页面或显示连接对话框
                }
            )

            // 自动确认倒计时提示
            val connectionState by scaleManager.connectionState.collectAsState()
            val currentWeight by scaleManager.currentWeight.collectAsState()
            if (autoConfirmEnabled &&
                activeRowIndex != null &&
                connectionState == ConnectionState.CONNECTED &&
                currentWeight?.isStable == true
            ) {
                val targetWeight = materialStates.getOrNull(activeRowIndex)?.material?.weight ?: 0.0
                val actualWeight = currentWeight?.value ?: 0.0
                val tolerance = targetWeight * autoConfirmTolerancePermille / 1000.0 // 千分比转换
                val isWithinTolerance = if (targetWeight <= 0) actualWeight > 0 else actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)

                Spacer(modifier = Modifier.height(4.dp))
                if (isWithinTolerance && stableStartTime != null) {
                    val elapsedSeconds = ((System.currentTimeMillis() - stableStartTime) / 1000).toInt()
                    val remainingSeconds = (autoConfirmDelaySeconds - elapsedSeconds).coerceAtLeast(0)
                    if (remainingSeconds > 0) {
                        Text(
                            text = "误差范围内，${remainingSeconds}秒后自动确认...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (!isWithinTolerance && actualWeight > 0) {
                    // 显示当前偏差（千分比）
                    val deviation = actualWeight - targetWeight
                    val deviationPermille = if (targetWeight > 0) (deviation / targetWeight * 1000) else 0.0
                    Text(
                        text = "偏差 ${if (deviation >= 0) "+" else ""}${String.format("%.1f", deviationPermille)}‰，需在±${autoConfirmTolerancePermille}‰内",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StakeholderSection(
            customer = customer,
            salesOwner = salesOwner,
            perfumer = perfumer,
            notes = notes,
            onCustomerChange = onCustomerChange,
            onSalesOwnerChange = onSalesOwnerChange,
            onPerfumerChange = onPerfumerChange,
            onNotesChange = onNotesChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 材料列表 - 高密度列表模式
        // 蓝牙连接状态（用于材料列表）
        val isBluetoothConnected = if (scaleManager != null) {
            val connState by scaleManager.connectionState.collectAsState()
            connState == ConnectionState.CONNECTED
        } else {
            false
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(materialStates) { index, materialState ->
                // 计算当前行的倒计时状态
                val isThisRowCountingDown = autoConfirmEnabled &&
                        activeRowIndex == index &&
                        stableStartTime != null &&
                        !materialState.isConfirmed

                val countdownProgress = if (isThisRowCountingDown && stableStartTime != null) {
                    val elapsed = (System.currentTimeMillis() - stableStartTime).toFloat()
                    val total = autoConfirmDelaySeconds * 1000f
                    (elapsed / total).coerceIn(0f, 1f)
                } else 0f

                MaterialConfigCard(
                    index = index + 1,
                    state = materialState,
                    isActive = activeRowIndex == index,
                    isBluetoothConnected = isBluetoothConnected,
                    onRowClick = {
                        // 点击未确认的行时设为活动行
                        if (!materialState.isConfirmed) {
                            onActiveRowChange(index)
                        }
                    },
                    onWeightChanged = { weight ->
                        onMaterialWeightChanged(index, weight)
                    },
                    onConfirmed = {
                        onMaterialConfirmed(index)
                    },
                    onEdit = {
                        onMaterialEdit(index)
                    },
                    isCompact = true,
                    isAutoConfirmCountingDown = isThisRowCountingDown,
                    autoConfirmProgress = countdownProgress
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 底部操作按钮
        BottomActions(
            recipe = recipe,
            materialStates = materialStates,
            taskId = taskId,
            recordId = recordId,
            customer = customer,
            salesOwner = salesOwner,
            perfumer = perfumer,
            notes = notes,
            onSaveConfiguration = onSaveConfiguration,
            onNavigateBack = onNavigateBack,
            isCompactDevice = useCompactLayout
        )
    }
}

@Composable
private fun StakeholderSection(
    customer: String,
    salesOwner: String,
    perfumer: String,
    notes: String,
    onCustomerChange: (String) -> Unit,
    onSalesOwnerChange: (String) -> Unit,
    onPerfumerChange: (String) -> Unit,
    onNotesChange: (String) -> Unit
) {
    // 研发配置界面聚焦于材料执行，客户/业务等信息在执行
    // 前已经确认，这里仅保留段落占位，避免显示冗余输入框。
}

/**
 * 配方标题区域
 */
@Composable
private fun RecipeHeader(
    recipe: Recipe,
    completedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "配方编码: ${recipe.code}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Text(
                    text = "$completedCount / $totalCount 已完成",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条
            LinearProgressIndicator(
                progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f,
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
        }
    }
}

/**
 * 单个材料配置项 - 支持展开/收缩模式
 * 活动行展开显示大号重量和按钮，非活动行紧凑显示
 */
@Composable
private fun MaterialConfigCard(
    index: Int,
    state: MaterialConfigState,
    isActive: Boolean = false,
    isBluetoothConnected: Boolean = false,
    onRowClick: () -> Unit = {},
    onWeightChanged: (String) -> Unit,
    onConfirmed: () -> Unit,
    onEdit: () -> Unit,
    isCompact: Boolean,
    // 自动确认倒计时相关
    isAutoConfirmCountingDown: Boolean = false,
    autoConfirmProgress: Float = 0f // 0-1 进度
) {
    val isConfirmed = state.isConfirmed
    // 是否展开：活动行且蓝牙连接且未确认时展开
    val isExpanded = isActive && isBluetoothConnected && !isConfirmed

    // 背景色
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isConfirmed -> Color(0xFFE8F5E9) // Light Green
            isExpanded -> Color(0xFFE3F2FD) // Light Blue for expanded
            state.hasError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            index % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "backgroundColor"
    )

    // 边框色
    val borderColor = if (isExpanded) Color(0xFF1976D2) else Color.Transparent

    // 序号颜色池
    val indexColors = listOf(
        Color(0xFF42A5F5), Color(0xFFFFA726), Color(0xFFAB47BC),
        Color(0xFF26A69A), Color(0xFFEC407A), Color(0xFF5C6BC0)
    )
    val indexBadgeColor = if (isConfirmed) Color(0xFF66BB6A) else indexColors[(index - 1) % indexColors.size]

    // 使用 Card 包裹展开的行，普通行用简单容器
    if (isExpanded) {
        // 展开模式 - 大卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(2.dp, borderColor),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 顶部：序号 + 物料名称
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 大号序号徽章
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(indexBadgeColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.material.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 物料代码
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = state.material.code.ifBlank { "N/A" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "目标: ${DecimalFormat("0.000").format(state.material.weight)} g",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 中间：大号重量显示
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFBBDEFB),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(2.dp, Color(0xFF1976D2))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = state.actualWeight.ifBlank { "0.000" },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D47A1)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "g",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF1976D2)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部：确认按钮 + 倒计时指示器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 倒计时进度指示器（仅在倒计时时显示）
                    if (isAutoConfirmCountingDown) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { autoConfirmProgress },
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFF4CAF50),
                                trackColor = Color(0xFFE8F5E9),
                                strokeWidth = 4.dp
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 确认按钮
                    Button(
                        onClick = onConfirmed,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoConfirmCountingDown) Color(0xFF66BB6A) else Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAutoConfirmCountingDown) "自动确认中" else "确认投料",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        // 紧凑模式 - 单行
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(enabled = !isConfirmed) { onRowClick() }
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 序号徽章
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(indexBadgeColor, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConfirmed) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(index.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // 物料信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.material.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = state.material.code.ifBlank { "-" },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "→ ${DecimalFormat("0.000").format(state.material.weight)}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 右侧操作区
                if (isConfirmed) {
                    // 已确认：显示实际重量和编辑按钮
                    Text(
                        text = "${DecimalFormat("0.000").format(state.actualWeight.toDoubleOrNull() ?: 0.0)}g",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                } else {
                    // 未确认：显示输入框和确认按钮
                    OutlinedTextField(
                        value = state.actualWeight,
                        onValueChange = onWeightChanged,
                        placeholder = { Text("0.0", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(70.dp).height(36.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.hasError,
                        shape = RoundedCornerShape(6.dp)
                    )
                    FilledTonalButton(
                        onClick = onConfirmed,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("确认", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun MaterialIndexBadge(index: Int, isConfirmed: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (isConfirmed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isConfirmed) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = index.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 底部操作按钮
 */
@Composable
private fun BottomActions(
    recipe: Recipe?,
    materialStates: List<MaterialConfigState>,
    taskId: String,
    recordId: String,
    customer: String,
    salesOwner: String,
    perfumer: String,
    notes: String,
    onSaveConfiguration: (MaterialConfigurationData) -> Unit,
    onNavigateBack: () -> Unit,
    isCompactDevice: Boolean
) {
    val allConfirmed = materialStates.isNotEmpty() && materialStates.all { it.isConfirmed }
    val onSave: () -> Unit = {
        recipe?.let { r ->
            val configData = MaterialConfigurationData(
                recipeId = r.id,
                recipeCode = r.code,
                recipeName = r.name,
                taskId = taskId,
                recordId = recordId,
                customer = customer,
                salesOwner = salesOwner,
                perfumer = perfumer,
                notes = notes,
                materials = materialStates.mapIndexed { idx, state ->
                    val actualWeight = state.actualWeight.toDoubleOrNull() ?: 0.0
                    val deviation = actualWeight - state.material.weight
                    val deviationPercentage = if (state.material.weight == 0.0) {
                        0.0
                    } else {
                        (deviation / state.material.weight) * 100
                    }
                    val materialCode = state.material.code.takeIf { it.isNotBlank() } ?: "RD-${idx + 1}"

                    MaterialConfigResult(
                        materialName = state.material.name,
                        targetWeight = state.material.weight,
                        actualWeight = actualWeight,
                        deviation = deviation,
                        deviationPercentage = deviationPercentage,
                        unit = "g",
                        materialCode = materialCode
                    )
                }
            )
            onSaveConfiguration(configData)
        }
    }

    if (isCompactDevice) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("返回")
            }
            Button(
                onClick = onSave,
                enabled = allConfirmed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (allConfirmed) "保存配置" else "请完成所有材料配置")
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("返回")
            }

            Button(
                onClick = onSave,
                enabled = allConfirmed,
                modifier = Modifier.weight(2f)
            ) {
                Text(if (allConfirmed) "保存配置" else "请完成所有材料配置")
            }
        }
    }
}

// Preview函数
@Preview(showBackground = true)
@Composable
private fun MaterialConfigurationScreenPreview() {
    SmartDosingTheme {
        val sampleMaterials = listOf(
            DataMaterial(id = "1", name = "VG溶剂", code = "VG-DEMO", weight = 70.0, unit = "g"),
            DataMaterial(id = "2", name = "PG溶剂", code = "PG-DEMO", weight = 22.0, unit = "g"),
            DataMaterial(id = "3", name = "薄荷香精", code = "MINT-DEMO", weight = 6.0, unit = "g"),
            DataMaterial(id = "4", name = "尼古丁溶液", code = "NIC-DEMO", weight = 2.0, unit = "g")
        )

        val sampleStates = listOf(
            MaterialConfigState(
                material = sampleMaterials[0],
                actualWeight = "70.2",
                isConfirmed = true,
                hasError = false
            ),
            MaterialConfigState(
                material = sampleMaterials[1],
                actualWeight = "22.1",
                isConfirmed = false,
                hasError = false
            ),
            MaterialConfigState(
                material = sampleMaterials[2],
                actualWeight = "",
                isConfirmed = false,
                hasError = false
            ),
            MaterialConfigState(
                material = sampleMaterials[3],
                actualWeight = "",
                isConfirmed = false,
                hasError = false
            )
        )

        MaterialConfigurationContent(
            recipe = Recipe(
                id = "1",
                code = "R-2024-001",
                name = "薄荷清香配方",
                category = "研发演示",
                materials = sampleMaterials,
                totalWeight = 100.0,
                createTime = "2024-01-01 00:00:00",
                customer = "示例客户",
                salesOwner = "王业务",
                perfumer = "李调香",
                description = "保持低温混合"
            ),
            materialStates = sampleStates,
            customer = "示例客户",
            salesOwner = "王业务",
            perfumer = "李调香",
            notes = "保持低温混合",
            taskId = "",
            recordId = "",
            onCustomerChange = {},
            onSalesOwnerChange = {},
            onPerfumerChange = {},
            onNotesChange = {},
            onMaterialWeightChanged = { _, _ -> },
            onMaterialConfirmed = { },
            onMaterialEdit = { },
            onSaveConfiguration = { },
            onNavigateBack = { }
        )
    }
}
