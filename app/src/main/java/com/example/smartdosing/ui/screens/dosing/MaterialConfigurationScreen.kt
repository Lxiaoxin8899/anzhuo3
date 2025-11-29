package com.example.smartdosing.ui.screens.dosing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
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
                },
                onMaterialEdit = { index ->
                    materialStates = materialStates.toMutableList().apply {
                        this[index] = this[index].copy(isConfirmed = false, hasError = false)
                    }
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

        Spacer(modifier = Modifier.height(12.dp))

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

        // 材料列表
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(materialStates) { index, materialState ->
                MaterialConfigCard(
                    index = index + 1,
                    state = materialState,
                    onWeightChanged = { weight ->
                        onMaterialWeightChanged(index, weight)
                    },
                    onConfirmed = {
                        onMaterialConfirmed(index)
                    },
                    onEdit = {
                        onMaterialEdit(index)
                    },
                    isCompact = useCompactLayout
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
 * 单个材料配置卡片
 */
@Composable
private fun MaterialConfigCard(
    index: Int,
    state: MaterialConfigState,
    onWeightChanged: (String) -> Unit,
    onConfirmed: () -> Unit,
    onEdit: () -> Unit,
    isCompact: Boolean
) {
    val cardColor = when {
        state.isConfirmed -> MaterialTheme.colorScheme.secondaryContainer
        state.hasError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val displayCode = state.material.code.takeIf { it.isNotBlank() } ?: "未设置"
    val targetWeightText = DecimalFormat("#.##").format(state.material.weight)
    val actualValue = state.actualWeight.toDoubleOrNull()
    val deviation = actualValue?.let { it - state.material.weight }
    val deviationPercent = if (deviation != null && state.material.weight != 0.0) {
        (deviation / state.material.weight) * 100
    } else null
    val deviationColor = when {
        deviationPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        kotlin.math.abs(deviationPercent) <= 5.0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    val targetLabel = buildAnnotatedString {
        append("目标 ")
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        ) {
            append("$targetWeightText g")
        }
    }
    val actualSummary = buildAnnotatedString {
        append("实际 ")
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        ) {
            append("${DecimalFormat("#.##").format(actualValue ?: 0.0)} g")
        }
        deviation?.let {
            append(" · Δ ")
            withStyle(
                SpanStyle(
                    color = deviationColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("${DecimalFormat("#.##").format(it)} g")
            }
            deviationPercent?.let { percent ->
                append(" (${DecimalFormat("#.##").format(percent)}%)")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        val containerModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
        if (isCompact) {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialIndexBadge(index = index, isConfirmed = state.isConfirmed)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = state.material.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "编码: $displayCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!state.isConfirmed) {
                    OutlinedTextField(
                        value = state.actualWeight,
                        onValueChange = onWeightChanged,
                        label = { Text("实际重量") },
                        singleLine = true,
                        suffix = { Text("g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleMedium,
                        isError = state.hasError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilledTonalButton(
                        onClick = onConfirmed,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text("确认")
                    }
                } else {
                    Text(
                        text = actualSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = deviationColor
                    )
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("调整")
                    }
                }
            }
        } else {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialIndexBadge(index = index, isConfirmed = state.isConfirmed)

                Column(
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(
                        text = state.material.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "编码: $displayCode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(min = 90.dp)
                )

                if (!state.isConfirmed) {
                    OutlinedTextField(
                        value = state.actualWeight,
                        onValueChange = onWeightChanged,
                        label = { Text("实际") },
                        singleLine = true,
                        suffix = { Text("g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleMedium,
                        isError = state.hasError,
                        modifier = Modifier.widthIn(min = 90.dp, max = 120.dp)
                    )

                    FilledTonalButton(
                        onClick = onConfirmed,
                        modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("确认")
                    }
                } else {
                    Text(
                        text = actualSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = deviationColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f)
                    )

                    TextButton(onClick = onEdit) {
                        Text("调整")
                    }
                }
            }
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
