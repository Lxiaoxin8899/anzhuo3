package com.example.smartdosing.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 智能投料系统 - 导航目标枚举
 */
enum class SmartDosingDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    HOME(
        route = "home",
        title = "首页",
        icon = Icons.Default.Home,
        description = "系统主页和快捷操作"
    ),
    RECIPES(
        route = "recipes",
        title = "配方管理",
        icon = Icons.Default.List,
        description = "配方创建、编辑和管理"
    ),
    DOSING(
        route = "dosing",
        title = "投料作业",
        icon = Icons.Default.PlayArrow,
        description = "执行投料操作"
    ),
    RECORDS(
        route = "records",
        title = "投料记录",
        icon = Icons.Default.List,
        description = "查看投料历史和统计"
    ),
    SETTINGS(
        route = "settings",
        title = "系统设置",
        icon = Icons.Default.Settings,
        description = "应用设置和个性化配置"
    )
}

/**
 * 底部导航栏显示的主要页面
 */
val bottomNavigationDestinations = listOf(
    SmartDosingDestination.HOME,
    SmartDosingDestination.RECIPES,
    SmartDosingDestination.DOSING,
    SmartDosingDestination.RECORDS,
    SmartDosingDestination.SETTINGS
)

/**
 * 导航路由常量
 */
object SmartDosingRoutes {
    const val HOME = "home"
    const val RECIPES = "recipes"
    const val RECIPE_DETAIL = "recipe_detail/{recipeId}"
    const val RECIPE_CREATE = "recipe_create"
    const val RECIPE_EDIT = "recipe_edit/{recipeId}"
    const val DOSING = "dosing"
    const val DOSING_CHECKLIST = "dosing_checklist/{recipeId}"
    const val DOSING_OPERATION = "dosing_operation/{recipeId}"
    const val RECORDS = "records"
    const val RECORD_DETAIL = "record_detail/{recordId}"
    const val SETTINGS = "settings"

    fun recipeDetail(recipeId: String) = "recipe_detail/$recipeId"
    fun recipeEdit(recipeId: String) = "recipe_edit/$recipeId"
    fun dosingChecklist(recipeId: String) = "dosing_checklist/$recipeId"
    fun dosingOperation(recipeId: String) = "dosing_operation/$recipeId"
    fun recordDetail(recordId: String) = "record_detail/$recordId"
}
