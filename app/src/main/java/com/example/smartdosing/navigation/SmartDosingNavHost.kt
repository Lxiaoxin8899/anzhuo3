package com.example.smartdosing.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.smartdosing.data.ConfigurationMaterialRecord
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.screens.device.DeviceInfoScreen
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationData
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationScreen
import com.example.smartdosing.ui.screens.home.HomeScreen
import com.example.smartdosing.ui.screens.materials.MaterialListScreen
import com.example.smartdosing.ui.screens.records.ConfigurationRecordDetailScreen
import com.example.smartdosing.ui.screens.records.ConfigurationRecordsScreen
import com.example.smartdosing.ui.screens.recipes.RecipesScreen
import com.example.smartdosing.ui.screens.settings.SettingsScreen
import com.example.smartdosing.ui.screens.settings.BluetoothScaleSettingsScreen
import com.example.smartdosing.ui.screens.tasks.TaskCenterScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val RECIPE_ID_ARG = "recipeId"
private const val TASK_ID_ARG = "taskId"
private const val RECORD_ID_ARG = "recordId"

/**
 * SmartDosing 导航主机，统一维护研发配置闭环的所有入口。
 *
 * 1. 只暴露“首页/任务中心/配方管理/记录/设置”五大底部入口，彻底移除旧的投料流程。
 * 2. 通过 navigateToMaterialConfiguration() 扩展方法约束所有“开始配置”类跳转，避免误入 legacy route。
 * 3. 保存材料配置时调用后台仓库并自动刷新任务中心、配置记录。
 */
@Composable
fun SmartDosingNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
    ) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var taskRefreshSignal by rememberSaveable { mutableStateOf(0) }
    var configurationRefreshSignal by rememberSaveable { mutableStateOf(0) }

    NavHost(
        navController = navController,
        startDestination = SmartDosingRoutes.HOME,
        modifier = modifier
    ) {
        composable(SmartDosingRoutes.HOME) {
            HomeScreen(
                onNavigateToRecipes = {
                    navController.navigate(SmartDosingRoutes.RECIPES)
                },
                onNavigateToMaterialConfiguration = { recipeId ->
                    navController.navigateToMaterialConfiguration(recipeId)
                },
                onNavigateToTaskCenter = {
                    navController.navigate(SmartDosingRoutes.TASK_CENTER)
                },
                onNavigateToConfigurationRecords = {
                    navController.navigate(SmartDosingRoutes.CONFIGURATION_RECORDS)
                },
                onNavigateToSettings = {
                    navController.navigate(SmartDosingRoutes.SETTINGS)
                },
                onNavigateToDeviceInfo = {
                    navController.navigate(SmartDosingRoutes.DEVICE_INFO)
                },
                onImportRecipe = {
                    navController.navigateToMaterialConfiguration("import_csv")
                }
            )
        }

        composable(SmartDosingRoutes.TASK_CENTER) {
            TaskCenterScreen(
                refreshSignal = taskRefreshSignal,
                onNavigateBack = { navController.popBackStack() },
                onStartTask = { task ->
                    navController.navigateToMaterialConfiguration(
                        recipeId = task.recipeId.ifBlank { "quick_start" },
                        taskId = task.id
                    )
                },
                onConfigureTask = { task ->
                    navController.navigateToMaterialConfiguration(
                        recipeId = task.recipeId.ifBlank { "quick_start" },
                        taskId = task.id
                    )
                }
            )
        }

        composable(SmartDosingRoutes.RECIPES) {
            RecipesScreen(
                onNavigateToRecipeDetail = { /* 详情页暂由列表内部处理 */ },
                onNavigateToMaterialConfiguration = { recipeId ->
                    navController.navigateToMaterialConfiguration(recipeId)
                }
            )
        }

        composable(SmartDosingRoutes.CONFIGURATION_RECORDS) {
            ConfigurationRecordsScreen(
                refreshSignal = configurationRefreshSignal,
                onNavigateBack = { navController.popBackStack() },
                onRecordSelected = { recordId ->
                    navController.navigate(
                        SmartDosingRoutes.configurationRecordDetail(recordId)
                    )
                }
            )
        }

        composable(
            route = SmartDosingRoutes.CONFIGURATION_RECORD_DETAIL,
            arguments = listOf(
                navArgument(RECORD_ID_ARG) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString(RECORD_ID_ARG).orEmpty()
            ConfigurationRecordDetailScreen(
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() },
                onReconfigure = { record ->
                    navController.navigateToMaterialConfiguration(
                        recipeId = record.recipeId.ifBlank { "quick_start" },
                        recordId = record.id
                    )
                },
                onFixError = { record ->
                    navController.navigateToMaterialConfiguration(
                        recipeId = record.recipeId.ifBlank { "quick_start" },
                        recordId = record.id
                    )
                }
            )
        }

        composable(
            route = SmartDosingRoutes.MATERIAL_CONFIGURATION,
            arguments = listOf(
                navArgument(RECIPE_ID_ARG) {
                    type = NavType.StringType
                    defaultValue = "quick_start"
                },
                navArgument(TASK_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument(RECORD_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString(RECIPE_ID_ARG).orEmpty()
            val taskId = backStackEntry.arguments?.getString(TASK_ID_ARG).orEmpty()
            val recordId = backStackEntry.arguments?.getString(RECORD_ID_ARG).orEmpty()

            MaterialConfigurationScreen(
                recipeId = recipeId,
                taskId = taskId,
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskCenter = {
                    navController.navigate(SmartDosingRoutes.TASK_CENTER)
                },
                onSaveConfiguration = { configData ->
                    scope.launch {
                        saveMaterialConfiguration(
                            context = context,
                            configData = configData
                        ) {
                            configurationRefreshSignal++
                            taskRefreshSignal++
                            navController.popBackStack()
                            navController.navigate(SmartDosingRoutes.CONFIGURATION_RECORDS) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }

        composable(SmartDosingRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateToBluetoothSettings = {
                    navController.navigate(SmartDosingRoutes.BLUETOOTH_SCALE_SETTINGS)
                }
            )
        }

        composable(SmartDosingRoutes.BLUETOOTH_SCALE_SETTINGS) {
            BluetoothScaleSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(SmartDosingRoutes.DEVICE_INFO) {
            DeviceInfoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(SmartDosingRoutes.MATERIAL_LIST) {
            MaterialListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * 统一的材料配置导航扩展，强制拼接任务/记录上下文，避免遗漏参数。
 */
fun NavController.navigateToMaterialConfiguration(
    recipeId: String,
    taskId: String? = null,
    recordId: String? = null
) {
    val route = SmartDosingRoutes.materialConfiguration(
        recipeId = recipeId,
        taskId = taskId,
        recordId = recordId
    )
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * 保存研发配置结果并刷新配置记录仓库。
 */
private suspend fun saveMaterialConfiguration(
    context: Context,
    configData: MaterialConfigurationData,
    onSuccess: () -> Unit
) {
    val recordRepository = ConfigurationRepositoryProvider.recordRepository
    val taskRepository = ConfigurationRepositoryProvider.taskRepository
    
    // 获取应用实例和偏好管理器
    val application = context.applicationContext as? com.example.smartdosing.SmartDosingApplication
    val preferencesManager = application?.bluetoothPreferencesManager
    
    // 获取公差设置（默认为10‰，即1%）
    val tolerancePermille = if (preferencesManager != null) {
        val prefs = preferencesManager.preferencesFlow.first()
        prefs.autoConfirmTolerancePermille
    } else {
        10
    }

    runCatching {
        recordRepository.createRecord(configData.toConfigurationRecord(tolerancePermille)).also {
            if (configData.taskId.isNotBlank()) {
                taskRepository.updateTaskStatus(configData.taskId, TaskStatus.COMPLETED)
            }
        }
    }.onSuccess {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "研发配置已入库", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }.onFailure { throwable ->
        withContext(Dispatchers.Main) {
            val message = throwable.message?.takeIf { it.isNotBlank() } ?: "保存失败，请稍后再试"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 将材料配置数据映射为配置记录实体，写入任务闭环。
 * @param tolerancePermille 允许的误差范围（千分比）
 */
private fun MaterialConfigurationData.toConfigurationRecord(tolerancePermille: Int = 10): ConfigurationRecord {
    val now = System.currentTimeMillis()
    val defaultId = if (recordId.isNotBlank()) recordId else "CR-${UUID.randomUUID()}"
    val defaultTaskId = if (taskId.isNotBlank()) taskId else "TASK-${UUID.randomUUID()}"
    val totalTarget = materials.sumOf { it.targetWeight }
    val totalActual = materials.sumOf { if (it.actualWeight > 0) it.actualWeight else it.targetWeight }
    val unit = materials.firstOrNull()?.unit ?: "g"
    val timestamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(now))
    // 将材料配置数据转换成有序的材料明细，便于详情页和后端还原最终结果
    val materialDetails = materials.mapIndexed { index, item ->
        val actual = if (item.actualWeight > 0) item.actualWeight else item.targetWeight
        val deviation = actual - item.targetWeight
        // 判定是否超标：误差超过目标值的 X‰ (默认10‰即1%)
        // tolerancePermille / 1000.0 转为小数
        val isOutOfTolerance = if (item.targetWeight > 0) {
            kotlin.math.abs(deviation) > (item.targetWeight * (tolerancePermille / 1000.0))
        } else {
            false
        }

        ConfigurationMaterialRecord(
            sequence = index + 1,
            name = item.materialName,
            code = item.materialCode.takeIf { it.isNotBlank() }
                ?: "${recipeCode.ifBlank { "RND" }}-${index + 1}",
            targetWeight = item.targetWeight,
            actualWeight = actual,
            unit = item.unit.ifBlank { unit },
            deviation = deviation,
            isOutOfTolerance = isOutOfTolerance
        )
    }

    return ConfigurationRecord(
        id = defaultId,
        taskId = defaultTaskId,
        recipeId = recipeId.ifBlank { "RND-${UUID.randomUUID()}" },
        recipeName = recipeName.ifBlank { recipeCode.ifBlank { "研发配置" } },
        recipeCode = recipeCode.ifBlank { "RND-$now" },
        category = "研发配置",
        operator = perfumer.ifBlank { "研发配置工位" },
        quantity = if (totalTarget > 0) totalTarget else totalActual,
        unit = unit,
        actualQuantity = totalActual,
        customer = customer.ifBlank { "内部研发" },
        salesOwner = salesOwner.ifBlank { "研发团队" },
        resultStatus = ConfigurationRecordStatus.COMPLETED,
        updatedAt = timestamp,
        tags = materials.take(3).map { it.materialName },
        note = notes,
        materialDetails = materialDetails
    )
}
