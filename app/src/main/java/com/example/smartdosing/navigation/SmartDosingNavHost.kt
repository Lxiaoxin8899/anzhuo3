package com.example.smartdosing.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.smartdosing.data.DosingRecordDetailInput
import com.example.smartdosing.data.DosingRecordRepository
import com.example.smartdosing.data.DosingRecordSaveRequest
import com.example.smartdosing.ui.screens.dosing.DosingOperationScreen
import com.example.smartdosing.ui.screens.dosing.DosingScreen
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationScreen
import com.example.smartdosing.ui.screens.dosing.MaterialConfigurationData
import com.example.smartdosing.ui.screens.home.HomeScreen
import com.example.smartdosing.ui.screens.records.RecordsScreen
import com.example.smartdosing.ui.screens.recipes.RecipesScreen
import com.example.smartdosing.ui.screens.settings.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * SmartDosing 应用导航主机
 */
@Composable
fun SmartDosingNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
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
        composable(SmartDosingRoutes.MATERIAL_CONFIGURATION) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
            val context = LocalContext.current
            MaterialConfigurationScreen(
                recipeId = recipeId,
                onNavigateBack = { navController.popBackStack() },
                onSaveConfiguration = { configData ->
                    // 保存配置数据到本地存储或数据库
                    saveMaterialConfiguration(context, configData)
                    navController.popBackStack()
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
 * 将研发环境的材料配置保存为投料记录
 */
fun saveMaterialConfiguration(context: Context, configData: MaterialConfigurationData) {
    val tolerancePercent = 5f
    val recordRepository = DosingRecordRepository.getInstance(context)
    // 生成检查项概览文本，方便在记录列表中快速预览
    val checklistItems = configData.materials.mapIndexed { index, material ->
        val targetText = String.format(Locale.getDefault(), "%.2f", material.targetWeight)
        val actualText = String.format(Locale.getDefault(), "%.2f", material.actualWeight)
        "材料${index + 1}：${material.materialName}（目标${targetText}g，实际${actualText}g）"
    }
    // 构建投料明细数据，与数据库字段保持一致
    val detailInputs = configData.materials.mapIndexed { index, material ->
        DosingRecordDetailInput(
            sequence = index + 1,
            materialCode = material.materialCode.ifBlank { material.materialName },
            materialName = material.materialName,
            targetWeight = material.targetWeight,
            actualWeight = material.actualWeight,
            unit = "g",
            isOverLimit = abs(material.deviationPercentage) > tolerancePercent,
            overLimitPercent = material.deviationPercentage
        )
    }

    // 启动后台协程执行保存逻辑，完成后回到主线程提示结果
    CoroutineScope(Dispatchers.IO).launch {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val request = DosingRecordSaveRequest(
            recipeId = configData.recipeId,
            recipeCode = configData.recipeCode.ifBlank { "R&D-${System.currentTimeMillis()}" },
            recipeName = configData.recipeName,
            operatorName = "研发人员",
            checklistItems = checklistItems,
            startTime = timestamp,
            endTime = timestamp,
            totalMaterials = detailInputs.size,
            tolerancePercent = tolerancePercent,
            details = detailInputs
        )
        try {
            recordRepository.saveRecord(request)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "材料配置已保存", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存配置失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
