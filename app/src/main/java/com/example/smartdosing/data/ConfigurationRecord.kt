package com.example.smartdosing.data

/**
 * 配置记录：呈现研发阶段已经完成或进行中的配置作业
 */
data class ConfigurationRecord(
    val id: String,
    val taskId: String,
    val recipeName: String,
    val recipeCode: String,
    val category: String,
    val operator: String,
    val quantity: Double,
    val unit: String,
    val actualQuantity: Double = quantity,
    val customer: String,
    val salesOwner: String,
    val resultStatus: ConfigurationRecordStatus,
    val updatedAt: String,
    val tags: List<String> = emptyList(),
    val note: String = ""
)

enum class ConfigurationRecordStatus {
    IN_REVIEW,
    RUNNING,
    COMPLETED,
    ARCHIVED
}

object ConfigurationRecordSampleData {
    fun records(): List<ConfigurationRecord> = listOf(
        ConfigurationRecord(
            id = "CR-202401-001",
            taskId = "TASK-001",
            recipeName = "清雅草莓测试批",
            recipeCode = "ST-2401",
            category = "水果茶基底",
            operator = "李然",
            quantity = 12.5,
            unit = "kg",
            actualQuantity = 12.1,
            customer = "测试客户A",
            salesOwner = "陈琪",
            resultStatus = ConfigurationRecordStatus.COMPLETED,
            updatedAt = "今天 14:10",
            tags = listOf("加急", "验证"),
            note = "配比通过，记录偏差 < 2%"
        ),
        ConfigurationRecord(
            id = "CR-202401-002",
            taskId = "TASK-002",
            recipeName = "冷萃柠檬迭代",
            recipeCode = "CL-2024B",
            category = "气泡饮品",
            operator = "周衡",
            quantity = 25.0,
            unit = "kg",
            actualQuantity = 24.7,
            customer = "客户B",
            salesOwner = "李晗",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "今天 12:40",
            tags = listOf("第二批次")
        ),
        ConfigurationRecord(
            id = "CR-202401-003",
            taskId = "TASK-003",
            recipeName = "低糖可可雾化版",
            recipeCode = "LC-0907",
            category = "雾化烟油",
            operator = "沈工",
            quantity = 8.0,
            unit = "kg",
            actualQuantity = 0.0,
            customer = "内部研发",
            salesOwner = "内部项目",
            resultStatus = ConfigurationRecordStatus.IN_REVIEW,
            updatedAt = "昨天 19:30",
            tags = listOf("口感", "低糖"),
            note = "等待品控反馈"
        ),
        ConfigurationRecord(
            id = "CR-202401-004",
            taskId = "TASK-004",
            recipeName = "桂花青提试制",
            recipeCode = "GQ-0512",
            category = "功能饮品",
            operator = "陈路",
            quantity = 15.0,
            unit = "kg",
            actualQuantity = 15.2,
            customer = "体验店",
            salesOwner = "苏月",
            resultStatus = ConfigurationRecordStatus.ARCHIVED,
            updatedAt = "2 天前",
            tags = listOf("归档")
        ),
        ConfigurationRecord(
            id = "CR-202401-005",
            taskId = "TASK-005",
            recipeName = "海盐西柚轻发酵",
            recipeCode = "HX-7721",
            category = "水果茶基底",
            operator = "秦雪",
            quantity = 18.0,
            unit = "kg",
            actualQuantity = 17.6,
            customer = "客户C",
            salesOwner = "王帆",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "今天 09:50",
            tags = listOf("发酵", "试饮")
        )
    )
}
