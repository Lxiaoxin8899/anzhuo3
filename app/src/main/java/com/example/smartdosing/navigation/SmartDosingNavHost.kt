package com.example.smartdosing.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.TaskStatus
import com.example.smartdosing.ui.screens.dosing.DosingOperationScreen
import com.example.smartdosing.ui.screens.dosing.DosingScreen
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationScreen
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationData
import com.example.smartdosing.data.repository.ConfigurationRepositoryProvider
import com.example.smartdosing.ui.screens.home.HomeScreen
import com.example.smartdosing.ui.screens.records.ConfigurationRecordDetailScreen
import com.example.smartdosing.ui.screens.records.ConfigurationRecordsScreen
import com.example.smartdosing.ui.screens.records.RecordsScreen
import com.example.smartdosing.ui.screens.recipes.RecipesScreen
import com.example.smartdosing.ui.screens.settings.SettingsScreen
import com.example.smartdosing.ui.screens.tasks.TaskCenterScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SmartDosing 应用导航主机
 */
@Composable
fun SmartDosingNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    var taskRefreshTrigger by remember { mutableIntStateOf(0) }
    var recordRefreshTrigger by remember { mutableIntStateOf(0) }

    fun handleDosingNavigation(recipeId: String) {
        val normalizedId = recipeId.trim().ifBlank { "quick_start" }
        val directOperation = normalizedId == "quick_start" || normalizedId == "import_csv"
        val targetRoute = if (directOperation) {
            SmartDosingRoutes.dosingOperation(normalizedId)
        } else {
            SmartDosingRoutes.dosingChecklist(normalizedId)
        }
        navController.navigate(targetRoute)
    }

    NavHost(
        navController = navController,
        startDestination = SmartDosingRoutes.HOME,
        modifier = modifier
    ) {
        // 首页
        composable(SmartDosingRoutes.HOME) {
            HomeScreen(
                onNavigateToRecipes = {
                    navController.navigate(SmartDosingRoutes.RECIPES)
                },
                onNavigateToDosingOperation = { recipeId ->
                    handleDosingNavigation(recipeId)
                },
                onNavigateToMaterialConfiguration = { recipeId ->
                    navController.navigate(SmartDosingRoutes.materialConfiguration(recipeId))
                },
                onNavigateToTaskCenter = {
                    navController.navigate(SmartDosingRoutes.TASK_CENTER)
                },
                onNavigateToConfigurationRecords = {
                    navController.navigate(SmartDosingRoutes.CONFIGURATION_RECORDS)
                },
                onNavigateToRecords = {
                    navController.navigate(SmartDosingRoutes.RECORDS)
                },
                onNavigateToSettings = {
                    navController.navigate(SmartDosingRoutes.SETTINGS)
                },
                onImportRecipe = {
                    navController.navigate(SmartDosingRoutes.dosingOperation("import_csv"))
                }
            )
        }

        // 配方管理
        composable(SmartDosingRoutes.RECIPES) {
            RecipesScreen(
                onNavigateToRecipeDetail = { recipeId ->
                    navController.navigate(SmartDosingRoutes.recipeDetail(recipeId))
                },
                onNavigateToDosingOperation = { recipeId ->
                    handleDosingNavigation(recipeId)
                },
                onNavigateToMaterialConfiguration = { recipeId ->
                    navController.navigate(SmartDosingRoutes.materialConfiguration(recipeId))
                }
            )
        }

        // 投料作业
        composable(SmartDosingRoutes.DOSING) {
            DosingScreen(
                recipeId = null,
                onNavigateToDosingOperation = { recipeId ->
                    handleDosingNavigation(recipeId)
                },
                onNavigateToRecipeList = {
                    navController.navigate(SmartDosingRoutes.RECIPES)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 投料作业检查清单
        composable(SmartDosingRoutes.DOSING_CHECKLIST) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId")
            DosingScreen(
                recipeId = recipeId,
                onNavigateToDosingOperation = { targetRecipeId ->
                    navController.navigate(SmartDosingRoutes.dosingOperation(targetRecipeId))
                },
                onNavigateToRecipeList = {
                    navController.navigate(SmartDosingRoutes.RECIPES)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 投料记录
        composable(SmartDosingRoutes.RECORDS) {
            RecordsScreen(
                onNavigateToRecordDetail = { recordId ->
                    navController.navigate(SmartDosingRoutes.recordDetail(recordId))
                },
                onNavigateToDosingOperation = { recipeId ->
                    handleDosingNavigation(recipeId)
                }
            )
        }

        // 系统设置
        composable(SmartDosingRoutes.SETTINGS) {
            SettingsScreen()
        }

        // 配方详情页面
        composable(SmartDosingRoutes.RECIPE_DETAIL) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
            // TODO: 实现配方详情页面
            // RecipeDetailScreen(recipeId = recipeId, onNavigateBack = { navController.popBackStack() })
        }

        // 投料操作页面
        composable(SmartDosingRoutes.DOSING_OPERATION) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
            DosingOperationScreen(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecipeList = {
                    // 优先弹出到配方管理，确保投料完成后直接回到列表
                    val popped = navController.popBackStack(SmartDosingRoutes.RECIPES, inclusive = false)
                    if (!popped) {
                        navController.navigate(SmartDosingRoutes.RECIPES) {
                            popUpTo(SmartDosingRoutes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        // 材料配置页面 (研发环境)
        composable(
            route = SmartDosingRoutes.MATERIAL_CONFIGURATION,
            arguments = listOf(
                navArgument("recipeId") { type = NavType.StringType; defaultValue = "quick_start" },
                navArgument("taskId") { type = NavType.StringType; defaultValue = "" },
                navArgument("recordId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: "quick_start"
            val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
            val recordId = backStackEntry.arguments?.getString("recordId").orEmpty()
            val context = LocalContext.current
            MaterialConfigurationScreen(
                recipeId = recipeId,
                taskId = taskId,
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() },
                onSaveConfiguration = { configData ->
                    saveMaterialConfiguration(
                        context = context,
                        configData = configData,
                        onSuccess = {
                            taskRefreshTrigger++
                            recordRefreshTrigger++
                            navController.popBackStack()
                        }
                    )
                }
            )
        }

        // 任务中心页面
        composable(SmartDosingRoutes.TASK_CENTER) {
            TaskCenterScreen(
                refreshSignal = taskRefreshTrigger,
                onNavigateBack = { navController.popBackStack() },
                onConfigureTask = { task ->
                    navController.navigate(
                        SmartDosingRoutes.materialConfiguration(
                            recipeId = task.recipeId,
                            taskId = task.id
                        )
                    )
                }
            )
        }

        // 配置记录页面
        composable(SmartDosingRoutes.CONFIGURATION_RECORDS) {
            ConfigurationRecordsScreen(
                refreshSignal = recordRefreshTrigger,
                onNavigateBack = { navController.popBackStack() },
                onRecordSelected = { recordId ->
                    navController.navigate(SmartDosingRoutes.configurationRecordDetail(recordId))
                }
            )
        }

        composable(SmartDosingRoutes.CONFIGURATION_RECORD_DETAIL) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId") ?: return@composable
            ConfigurationRecordDetailScreen(
                recordId = recordId,
                onNavigateBack = { navController.popBackStack() },
                onReconfigure = { record ->
                    navController.navigate(
                        SmartDosingRoutes.materialConfiguration(
                            recipeId = record.recipeId.ifBlank { record.recipeCode },
                            recordId = record.id
                        )
                    )
                },
                onFixError = { _ ->
                    recordRefreshTrigger++
                }
            )
        }

        // 记录详情页面
        composable(SmartDosingRoutes.RECORD_DETAIL) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId") ?: ""
            // TODO: 实现记录详情页面
            // RecordDetailScreen(recordId = recordId, onNavigateBack = { navController.popBackStack() })
        }

        // 新建配方页面
        composable(SmartDosingRoutes.RECIPE_CREATE) {
            // TODO: 实现配方创建页面
            // RecipeCreateScreen(onNavigateBack = { navController.popBackStack() })
        }

        // 编辑配方页面
        composable(SmartDosingRoutes.RECIPE_EDIT) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
            // TODO: 实现配方编辑页面
            // RecipeEditScreen(recipeId = recipeId, onNavigateBack = { navController.popBackStack() })
        }
    }
}

/**
 * 保存材料配置数据
 * 将研发环境的材料配置写入配置记录仓库，便于 Task Center 与配置记录联动
 */
fun saveMaterialConfiguration(
    context: Context,
    configData: MaterialConfigurationData,
    onSuccess: () -> Unit = {}
) {
    val recordRepository = ConfigurationRepositoryProvider.recordRepository
    val taskRepository = ConfigurationRepositoryProvider.taskRepository
    CoroutineScope(Dispatchers.IO).launch {
        try {
            recordRepository.createRecord(configData.toConfigurationRecord())
            if (configData.taskId.isNotBlank()) {
                taskRepository.updateTaskStatus(configData.taskId, TaskStatus.COMPLETED)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "研发配置已保存", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存配置失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * 将界面层数据映射为配置记录，默认以研发配置为分类
 */
private fun MaterialConfigurationData.toConfigurationRecord(): ConfigurationRecord {
    val targetTotal = materials.sumOf { it.targetWeight }
    val actualTotal = materials.sumOf { it.actualWeight }
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val highlight = materials.take(3).joinToString(" / ") { material ->
        val actualText = String.format(Locale.getDefault(), "%.2f", material.actualWeight)
        "${material.materialName}:$actualText g"
    }
    val extra = if (materials.size > 3) " · 等${materials.size}种材料" else ""
    val summaryNote = "目标${String.format(Locale.getDefault(), "%.2f", targetTotal)}g，" +
        "实际${String.format(Locale.getDefault(), "%.2f", actualTotal)}g。$highlight$extra"
    val resolvedCustomer = customer.ifBlank { "未指定" }
    val resolvedSalesOwner = salesOwner.ifBlank { "未指定" }
    val resolvedPerfumer = perfumer.ifBlank { "研发助理" }
    val combinedNote = listOf(notes.trim().takeIf { it.isNotEmpty() }, summaryNote)
        .filterNotNull()
        .joinToString(" / ")

    return ConfigurationRecord(
        id = recordId.takeIf { it.isNotBlank() } ?: "CR-${System.currentTimeMillis()}",
        taskId = taskId.ifBlank { "TASK-${System.currentTimeMillis()}" },
        recipeId = recipeId.ifBlank { recipeCode.ifBlank { "R&D-${System.currentTimeMillis()}" } },
        recipeName = recipeName.ifBlank { "研发配置" },
        recipeCode = recipeCode.ifBlank { "R&D-${System.currentTimeMillis()}" },
        category = "研发配置",
        operator = resolvedPerfumer,
        quantity = targetTotal,
        unit = "g",
        actualQuantity = actualTotal,
        customer = resolvedCustomer,
        salesOwner = resolvedSalesOwner,
        resultStatus = ConfigurationRecordStatus.COMPLETED,
        updatedAt = timestamp,
        tags = listOf("快速录入"),
        note = combinedNote
    )
}
