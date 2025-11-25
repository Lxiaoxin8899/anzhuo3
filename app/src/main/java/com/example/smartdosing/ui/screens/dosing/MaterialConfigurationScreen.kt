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
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.Material as DataMaterial
import com.example.smartdosing.data.Recipe
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.theme.SmartDosingTheme
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // 快速生成示例配方，确保在没有真实配方时也能展示界面
    val loadDemoRecipe: () -> Unit = {
        val demoRecipe = createQuickStartRecipe()
        updateRecipeState(demoRecipe)
        loadError = null
    }

    // 加载配方数据
    LaunchedEffect(recipeId, taskId, recordId) {
        isLoading = true
        loadError = null
        try {
            val normalizedId = recipeId?.takeIf { it.isNotBlank() } ?: "quick_start"
            if (normalizedId == "quick_start") {
                loadDemoRecipe()
            } else {
                val loadedRecipe = repository.getRecipeById(normalizedId)
                    ?: recordRepository.fetchRecord(recordId)?.let { record ->
                        currentRecordId = record.id
                        repository.getRecipeById(record.recipeId)
                            ?: repository.getRecipeByCode(record.recipeCode)
                    }
                if (loadedRecipe != null) {
                    updateRecipeState(loadedRecipe)
                } else {
                    loadError = "未找到配方（ID: $normalizedId），可以加载示例数据体验流程"
                    recipe = null
                    materialStates = emptyList()
                    resetMetaInfo()
                }
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
                recordRepository.fetchRecord(recordId)?.let { record ->
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
                message = loadError ?: "当前配方没有配置材料，请返回或加载示例数据",
                onNavigateBack = onNavigateBack,
                onLoadDemo = loadDemoRecipe,
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
 * 没有配方或材料时的提示界面，支持直接注入示例数据
 */
@Composable
private fun MaterialConfigurationEmptyState(
    message: String,
    onNavigateBack: () -> Unit,
    onLoadDemo: (() -> Unit)?,
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
                text = "可返回重新选择配方，或直接载入示例材料开始体验。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onNavigateBack) {
                Text("返回上一页")
            }
            onLoadDemo?.let { loadDemo ->
                OutlinedButton(onClick = loadDemo) {
                    Text("加载示例数据")
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
                    }
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
            onNavigateBack = onNavigateBack
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
    onEdit: () -> Unit
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (state.isConfirmed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.isConfirmed) {
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
                text = buildAnnotatedString {
                    append("目标 ")
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("$targetWeightText g")
                    }
                },
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
                    text = buildAnnotatedString {
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
                    },
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
    onNavigateBack: () -> Unit
) {
    val allConfirmed = materialStates.isNotEmpty() && materialStates.all { it.isConfirmed }

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
            onClick = {
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
                            // 避免除数为 0 导致的 NaN，空重量时默认 0%
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
            },
            enabled = allConfirmed,
            modifier = Modifier.weight(2f)
        ) {
            Text(if (allConfirmed) "保存配置" else "请完成所有材料配置")
        }
    }
}

/**
 * 构造一个默认的示例配方，保证研发助理在没有真实数据时也能体验流程
 */
private fun createQuickStartRecipe(): Recipe {
    val defaultMaterials = listOf(
        DataMaterial(id = "rd-1", name = "基底溶剂 VG", code = "VG-BASE", weight = 70.0, unit = "g"),
        DataMaterial(id = "rd-2", name = "基底溶剂 PG", code = "PG-BASE", weight = 20.0, unit = "g"),
        DataMaterial(id = "rd-3", name = "薄荷香精", code = "FLV-MINT", weight = 6.0, unit = "g"),
        DataMaterial(id = "rd-4", name = "甜味调节剂", code = "SW-CTRL", weight = 3.0, unit = "g"),
        DataMaterial(id = "rd-5", name = "稳定剂", code = "STB-01", weight = 1.0, unit = "g"),
        DataMaterial(id = "rd-6", name = "冷却剂 WS-23", code = "WS23", weight = 0.5, unit = "g"),
        DataMaterial(id = "rd-7", name = "润喉添加剂", code = "THROAT", weight = 0.3, unit = "g"),
        DataMaterial(id = "rd-8", name = "天然提味剂", code = "BOOST", weight = 0.8, unit = "g"),
        DataMaterial(id = "rd-9", name = "抗氧化剂", code = "AOX", weight = 0.5, unit = "g"),
        DataMaterial(id = "rd-10", name = "工艺验证对照", code = "CTRL", weight = 0.2, unit = "g")
    ).mapIndexed { index, material ->
        material.copy(sequence = index + 1)
    }

    return Recipe(
        id = "quick_start_demo",
        code = "RD-DEMO-001",
        name = "基础研发示例配方",
        category = "研发配置",
        materials = defaultMaterials,
        totalWeight = defaultMaterials.sumOf { it.weight },
        createTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        description = "示例配方，帮助研发助理快速演练材料配置流程"
    )
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
