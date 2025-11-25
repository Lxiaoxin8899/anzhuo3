package com.example.smartdosing.data

/**
 * 研发配置任务：记录配方、目标设备与执行状态
 */
data class ConfigurationTask(
    val id: String,
    val title: String = "",
    val recipeId: String,
    val recipeName: String,
    val recipeCode: String,
    val quantity: Double,
    val unit: String = "kg",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val requestedBy: String = "",
    val perfumer: String = "",
    val customer: String = "",
    val salesOwner: String = "",
    val status: TaskStatus = TaskStatus.DRAFT,
    val deadline: String = "",
    val createdAt: String = "",
    val publishedAt: String? = null,
    val statusUpdatedAt: String = "",
    val targetDevices: List<String> = emptyList(),
    val note: String = "",
    val tags: List<String> = emptyList()
)

/**
 * 任务优先级
 */
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * 任务状态
 */
enum class TaskStatus {
    DRAFT,
    READY,
    PUBLISHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/**
 * 简易示例数据，方便界面预览
 */
object TaskSampleData {
    fun dailyTasks(): List<ConfigurationTask> = listOf(
        ConfigurationTask(
            id = "TASK-001",
            title = "薄荷雾化调试",
            recipeId = "RCP-001",
            recipeName = "清雅草莓测试批",
            recipeCode = "ST-2401",
            quantity = 12.5,
            unit = "kg",
            priority = TaskPriority.URGENT,
            requestedBy = "王敏",
            perfumer = "陈路",
            customer = "测试客户A",
            salesOwner = "陈琪",
            status = TaskStatus.READY,
            deadline = "今天 15:30",
            createdAt = "今天 09:20",
            targetDevices = listOf("本机投料设备"),
            note = "需验证新香精配比",
            tags = listOf("验证", "快速")
        ),
        ConfigurationTask(
            id = "TASK-002",
            title = "冷萃柠檬迭代",
            recipeId = "RCP-002",
            recipeName = "冷萃柠檬迭代",
            recipeCode = "CL-2024B",
            quantity = 25.0,
            unit = "kg",
            priority = TaskPriority.HIGH,
            requestedBy = "张蕾",
            perfumer = "张蕾",
            customer = "客户 B",
            salesOwner = "李晗",
            status = TaskStatus.IN_PROGRESS,
            deadline = "今天 18:00",
            createdAt = "今天 08:10",
            publishedAt = "今天 08:40",
            targetDevices = listOf("本机投料设备"),
            note = "需要记录每个批次偏差"
        ),
        ConfigurationTask(
            id = "TASK-003",
            title = "低糖可可雾化版",
            recipeId = "RCP-003",
            recipeName = "低糖可可雾化版",
            recipeCode = "LC-0907",
            quantity = 8.0,
            unit = "kg",
            priority = TaskPriority.NORMAL,
            requestedBy = "沈工",
            perfumer = "沈工",
            customer = "内部研发",
            salesOwner = "内部项目",
            status = TaskStatus.DRAFT,
            deadline = "明天 10:00",
            createdAt = "今天 10:00",
            note = "聚焦口感一致性"
        ),
        ConfigurationTask(
            id = "TASK-004",
            title = "桂花青提试制",
            recipeId = "RCP-004",
            recipeName = "桂花青提试制",
            recipeCode = "GQ-0512",
            quantity = 15.0,
            unit = "kg",
            priority = TaskPriority.LOW,
            requestedBy = "陈路",
            perfumer = "陈路",
            customer = "体验店",
            salesOwner = "苏月",
            status = TaskStatus.COMPLETED,
            deadline = "昨天",
            createdAt = "昨天 14:00",
            publishedAt = "昨天 14:20",
            targetDevices = listOf("本机投料设备"),
            note = "已完成验证，待归档"
        )
    )
}

/**
 * 任务中心中的设备信息
 */
data class TaskDeviceInfo(
    val id: String,
    val name: String,
    val status: DeviceStatus,
    val currentTaskId: String? = null,
    val currentTaskName: String? = null,
    val lastHeartbeat: String? = null
)

enum class DeviceStatus {
    ONLINE,
    BUSY,
    OFFLINE,
    ERROR
}

/**
 * 发布记录
 */
data class TaskPublishLogEntry(
    val id: String,
    val time: String,
    val title: String,
    val description: String,
    val operator: String = "",
    val status: PublishResultStatus = PublishResultStatus.SUCCEEDED
)

enum class PublishResultStatus {
    SUCCEEDED,
    FAILED,
    QUEUED
}

data class TaskOverviewSnapshot(
    val pendingCount: Int,
    val runningCount: Int,
    val completedToday: Int,
    val heartbeatWindow: String,
    val tasks: List<ConfigurationTask>,
    val devices: List<TaskDeviceInfo>,
    val publishLog: List<TaskPublishLogEntry>
)

object TaskDeviceSampleData {
    fun devices(): List<TaskDeviceInfo> = listOf(
        TaskDeviceInfo(
            id = "LOCAL-DEVICE",
            name = "本机投料设备",
            status = DeviceStatus.ONLINE,
            currentTaskId = null,
            currentTaskName = null,
            lastHeartbeat = "09:42"
        )
    )
}

object TaskPublishLogSampleData {
    fun recent(): List<TaskPublishLogEntry> = listOf(
        TaskPublishLogEntry(
            id = "LOG-001",
            time = "09:35",
            title = "薄荷雾化调试",
            description = "推送至 研发线 #1、实验设备 #X",
            operator = "系统",
            status = PublishResultStatus.SUCCEEDED
        ),
        TaskPublishLogEntry(
            id = "LOG-002",
            time = "09:05",
            title = "冷萃柠檬迭代",
            description = "已排队等待设备空闲",
            operator = "王敏",
            status = PublishResultStatus.QUEUED
        ),
        TaskPublishLogEntry(
            id = "LOG-003",
            time = "08:40",
            title = "低糖可可雾化版",
            description = "提交草稿，待确认设备",
            operator = "沈工",
            status = PublishResultStatus.FAILED
        )
    )
}
