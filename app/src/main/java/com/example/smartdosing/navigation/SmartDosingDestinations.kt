package com.example.smartdosing.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SmartDosing 顶层导航配置（UTF-8 中文）
 */
enum class SmartDosingDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    HOME(
        route = SmartDosingRoutes.HOME,
        title = "首页",
        icon = Icons.Default.Home,
        description = "任务、配置、导入等总览入口"
    ),
    TASK_CENTER(
        route = SmartDosingRoutes.TASK_CENTER,
        title = "任务中心",
        icon = Icons.Default.Assignment,
        description = "接单、开始配置、更新状态"
    ),
    RECIPES(
        route = SmartDosingRoutes.RECIPES,
        title = "配方管理",
        icon = Icons.Default.List,
        description = "导入、筛选、维护配方字段"
    ),
    RECORDS(
        route = SmartDosingRoutes.CONFIGURATION_RECORDS,
        title = "配置记录",
        icon = Icons.Default.Assignment,
        description = "查看历史配置记录与明细"
    ),
    SETTINGS(
        route = SmartDosingRoutes.SETTINGS,
        title = "系统设置",
        icon = Icons.Default.Settings,
        description = "网络、语音、Web 服务等设置"
    )
}

/**
 * 底部导航展示顺序
 */
val bottomNavigationDestinations = listOf(
    SmartDosingDestination.HOME,
    SmartDosingDestination.TASK_CENTER,
    SmartDosingDestination.RECIPES,
    SmartDosingDestination.RECORDS,
    SmartDosingDestination.SETTINGS
)

/**
 * 全局路由常量
 */
object SmartDosingRoutes {
    const val HOME = "home"
    const val RECIPES = "recipes"
    const val RECIPE_DETAIL = "recipe_detail/{recipeId}"
    const val RECIPE_CREATE = "recipe_create"
    const val RECIPE_EDIT = "recipe_edit/{recipeId}"
    const val MATERIAL_CONFIGURATION = "material_configuration/{recipeId}?taskId={taskId}&recordId={recordId}"
    const val TASK_CENTER = "task_center"
    const val CONFIGURATION_RECORDS = "configuration_records"
    const val CONFIGURATION_RECORD_DETAIL = "configuration_record_detail/{recordId}"
    const val SETTINGS = "settings"
    const val DEVICE_INFO = "device_info"
    const val BLUETOOTH_SCALE_SETTINGS = "bluetooth_scale_settings"

    fun recipeDetail(recipeId: String) = "recipe_detail/$recipeId"
    fun recipeEdit(recipeId: String) = "recipe_edit/$recipeId"
    fun materialConfiguration(
        recipeId: String,
        taskId: String? = null,
        recordId: String? = null
    ): String {
        val normalizedId = recipeId.ifBlank { "quick_start" }
        val params = buildList {
            taskId?.takeIf { it.isNotBlank() }?.let { add("taskId=$it") }
            recordId?.takeIf { it.isNotBlank() }?.let { add("recordId=$it") }
        }
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "material_configuration/$normalizedId$query"
    }
    fun configurationRecordDetail(recordId: String) = "configuration_record_detail/$recordId"
}
