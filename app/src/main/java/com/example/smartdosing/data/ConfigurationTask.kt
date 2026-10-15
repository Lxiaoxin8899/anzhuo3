package com.example.smartdosing.data

import com.google.gson.annotations.SerializedName

/**
 * 研发配置任务：记录配方、目标设备与执行状态
 */
data class ConfigurationTask(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("recipeId") val recipeId: String,
    @SerializedName("recipeName") val recipeName: String,
    @SerializedName("recipeCode") val recipeCode: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("unit") val unit: String = "g",
    @SerializedName("priority") val priority: TaskPriority = TaskPriority.NORMAL,
    @SerializedName("requestedBy") val requestedBy: String = "",
    @SerializedName("perfumer") val perfumer: String = "",
    @SerializedName("customer") val customer: String = "",
    @SerializedName("salesOwner") val salesOwner: String = "",
    @SerializedName("status") val status: TaskStatus = TaskStatus.DRAFT,
    @SerializedName("deadline") val deadline: String = "",
    @SerializedName("createdAt") val createdAt: String = "",
    @SerializedName("publishedAt") val publishedAt: String? = null,
    @SerializedName("statusUpdatedAt") val statusUpdatedAt: String = "",
    @SerializedName("targetDevices") val targetDevices: List<String> = emptyList(),
    @SerializedName("note") val note: String = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    // 接单相关字段
    @SerializedName("acceptedBy") val acceptedBy: String? = null,  // 接单人
    @SerializedName("acceptedAt") val acceptedAt: String? = null,  // 接单时间
    // 执行时间跟踪
    @SerializedName("startedAt") val startedAt: String? = null,   // 开始配置时间
    @SerializedName("completedAt") val completedAt: String? = null  // 完成时间
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
    fun dailyTasks(): List<ConfigurationTask> = emptyList()  // 已清空演示数据，使用真实后端数据
    
    // 以下是演示数据的备份，需要时可以恢复
    /*
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
        // ... 其他演示数据
    )
    */
}

/**
 * 任务中心中的设备信息
 */
data class TaskDeviceInfo(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: DeviceStatus,
    @SerializedName("currentTaskId") val currentTaskId: String? = null,
    @SerializedName("currentTaskName") val currentTaskName: String? = null,
    @SerializedName("lastHeartbeat") val lastHeartbeat: String? = null
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
    @SerializedName("id") val id: String,
    @SerializedName("time") val time: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("operator") val operator: String = "",
    @SerializedName("status") val status: PublishResultStatus = PublishResultStatus.SUCCEEDED
)

enum class PublishResultStatus {
    SUCCEEDED,
    FAILED,
    QUEUED
}

data class TaskOverviewSnapshot(
    @SerializedName("pendingCount") val pendingCount: Int,
    @SerializedName("runningCount") val runningCount: Int,
    @SerializedName("completedToday") val completedToday: Int,
    @SerializedName("heartbeatWindow") val heartbeatWindow: String,
    @SerializedName("tasks") val tasks: List<ConfigurationTask>,
    @SerializedName("devices") val devices: List<TaskDeviceInfo>,
    @SerializedName("publishLog") val publishLog: List<TaskPublishLogEntry>
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
