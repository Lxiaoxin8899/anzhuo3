package com.example.smartdosing.data

/**
 * 研发配置任务：记录配方与数量等关键信息
 */
data class ConfigurationTask(
    val id: String,
    val recipeId: String,
    val recipeName: String,
    val recipeCode: String,
    val quantity: Double,
    val unit: String = "kg",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val requestedBy: String = "",
    val customer: String = "",
    val salesOwner: String = "",
    val status: TaskStatus = TaskStatus.WAITING,
    val deadline: String = "",
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
    WAITING,
    IN_PROGRESS,
    COMPLETED
}

/**
 * 简易示例数据，方便界面预览
 */
object TaskSampleData {
    fun dailyTasks(): List<ConfigurationTask> = listOf(
        ConfigurationTask(
            id = "TASK-001",
            recipeId = "RCP-001",
            recipeName = "清雅草莓测试批",
            recipeCode = "ST-2401",
            quantity = 12.5,
            unit = "kg",
            priority = TaskPriority.URGENT,
            requestedBy = "王敏",
            customer = "测试客户A",
            salesOwner = "陈琪",
            status = TaskStatus.WAITING,
            deadline = "今天 15:30",
            note = "需验证新香精配比",
            tags = listOf("验证", "快速")
        ),
        ConfigurationTask(
            id = "TASK-002",
            recipeId = "RCP-002",
            recipeName = "冷萃柠檬迭代",
            recipeCode = "CL-2024B",
            quantity = 25.0,
            unit = "kg",
            priority = TaskPriority.HIGH,
            requestedBy = "张蕾",
            customer = "客户 B",
            salesOwner = "李晗",
            status = TaskStatus.IN_PROGRESS,
            deadline = "今天 18:00",
            note = "需要记录每个批次偏差"
        ),
        ConfigurationTask(
            id = "TASK-003",
            recipeId = "RCP-003",
            recipeName = "低糖可可雾化版",
            recipeCode = "LC-0907",
            quantity = 8.0,
            unit = "kg",
            priority = TaskPriority.NORMAL,
            requestedBy = "沈工",
            customer = "内部研发",
            salesOwner = "内部项目",
            status = TaskStatus.WAITING,
            deadline = "明天 10:00",
            note = "聚焦口感一致性"
        ),
        ConfigurationTask(
            id = "TASK-004",
            recipeId = "RCP-004",
            recipeName = "桂花青提试制",
            recipeCode = "GQ-0512",
            quantity = 15.0,
            unit = "kg",
            priority = TaskPriority.LOW,
            requestedBy = "陈路",
            customer = "体验店",
            salesOwner = "苏月",
            status = TaskStatus.COMPLETED,
            deadline = "昨天",
            note = "已完成验证，待归档"
        )
    )
}
