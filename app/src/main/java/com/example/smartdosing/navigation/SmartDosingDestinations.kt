package com.example.smartdosing.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Science
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
    DASHBOARD(
        route = SmartDosingRoutes.HOME,
        title = "首页",
        icon = Icons.Default.Dashboard,
        description = "研发看板、状态监控与任务恢复"
    ),
    LAB_CENTER(
        route = SmartDosingRoutes.LAB_CENTER,
        title = "实验中心",
        icon = Icons.Default.Science,
        description = "任务领取、配方调阅与实验配置"
    ),
    ARCHIVE(
        route = SmartDosingRoutes.CONFIGURATION_RECORDS,
        title = "档案库",
        icon = Icons.Default.Archive,
        description = "实验记录、统计报表与数据追溯"
    ),
    SETTINGS(
        route = SmartDosingRoutes.SETTINGS,
        title = "系统设置",
        icon = Icons.Default.Settings,
        description = "网络、语音、天平连接等系统配置"
    )
}

/**
 * 底部导航展示顺序
 */
val bottomNavigationDestinations = listOf(
    SmartDosingDestination.DASHBOARD,
    SmartDosingDestination.LAB_CENTER,
    SmartDosingDestination.ARCHIVE,
    SmartDosingDestination.SETTINGS
)

/**
 * 全局路由常量
 */
object SmartDosingRoutes {
    const val HOME = "home"
    const val LAB_CENTER = "lab_center"
    const val RECIPES = "recipes"
    const val MATERIAL_CONFIGURATION = "material_configuration/{recipeId}?taskId={taskId}&recordId={recordId}&viewOnly={viewOnly}&targetTotalWeight={targetTotalWeight}"
    const val TASK_CENTER = "task_center"
    const val CONFIGURATION_RECORDS = "configuration_records"
    const val CONFIGURATION_RECORD_DETAIL = "configuration_record_detail/{recordId}"
    const val SETTINGS = "settings"
    const val DEVICE_INFO = "device_info"
    const val BLUETOOTH_SCALE_SETTINGS = "bluetooth_scale_settings"
    const val WEB_SERVICE_SETTINGS = "web_service_settings"
    const val MATERIAL_LIST = "material_list"

    fun materialConfiguration(
        recipeId: String,
        taskId: String? = null,
        recordId: String? = null,
        viewOnly: Boolean = false,
        targetTotalWeight: Double? = null
    ): String {
        val normalizedId = recipeId.ifBlank { "quick_start" }
        val params = buildList {
            taskId?.takeIf { it.isNotBlank() }?.let { add("taskId=$it") }
            recordId?.takeIf { it.isNotBlank() }?.let { add("recordId=$it") }
            if (viewOnly) add("viewOnly=true")
            targetTotalWeight?.let { add("targetTotalWeight=$it") }
        }
        val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "material_configuration/$normalizedId$query"
    }
    fun configurationRecordDetail(recordId: String) = "configuration_record_detail/$recordId"
}
