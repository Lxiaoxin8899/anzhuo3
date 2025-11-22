package com.example.smartdosing.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.smartdosing.ui.screens.home.HomeScreen
import com.example.smartdosing.ui.screens.recipes.RecipesScreen
import com.example.smartdosing.ui.screens.dosing.DosingScreen
import com.example.smartdosing.ui.screens.dosing.DosingOperationScreen
import com.example.smartdosing.ui.screens.records.RecordsScreen
import com.example.smartdosing.ui.screens.settings.SettingsScreen

/**
 * SmartDosing 应用导航主机
 */
@Composable
fun SmartDosingNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
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
                    navController.navigate(SmartDosingRoutes.dosingOperation(recipeId))
                },
                onNavigateToRecords = {
                    navController.navigate(SmartDosingRoutes.RECORDS)
                },
                onNavigateToSettings = {
                    navController.navigate(SmartDosingRoutes.SETTINGS)
                }
            )
        }

        // 配方管理
        composable(SmartDosingRoutes.RECIPES) {
            RecipesScreen(
                onNavigateToRecipeDetail = { recipeId ->
                    navController.navigate(SmartDosingRoutes.recipeDetail(recipeId))
                },
                onNavigateToCreateRecipe = {
                    navController.navigate(SmartDosingRoutes.RECIPE_CREATE)
                },
                onNavigateToDosingOperation = { recipeId ->
                    navController.navigate(SmartDosingRoutes.dosingOperation(recipeId))
                }
            )
        }

        // 投料作业
        composable(SmartDosingRoutes.DOSING) {
            DosingScreen(
                onNavigateToDosingOperation = { recipeId ->
                    navController.navigate(SmartDosingRoutes.dosingOperation(recipeId))
                }
            )
        }

        // 投料记录
        composable(SmartDosingRoutes.RECORDS) {
            RecordsScreen(
                onNavigateToRecordDetail = { recordId ->
                    navController.navigate(SmartDosingRoutes.recordDetail(recordId))
                },
                onNavigateToDosingOperation = { recipeId ->
                    navController.navigate(SmartDosingRoutes.dosingOperation(recipeId))
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
                onNavigateBack = { navController.popBackStack() }
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