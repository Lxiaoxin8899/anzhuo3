
package com.example.smartdosing.data

/**
 * 配置记录数据类，表示一次完整的配料操作记录
 */
data class ConfigurationRecord(
    val id: String,
    val taskId: String,
    val recipeId: String,
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
            recipeId = "RCP-001",
            recipeName = "草莓香精配方",
            recipeCode = "ST-2401",
            category = "香精类",
            operator = "张三",
            quantity = 12.5,
            unit = "kg",
            actualQuantity = 12.1,
            customer = "客户公司A",
            salesOwner = "李四",
            resultStatus = ConfigurationRecordStatus.COMPLETED,
            updatedAt = "今天 14:10",
            tags = listOf("加急", "复核"),
            note = "误差控制<2%，已通过"
        ),
        ConfigurationRecord(
            id = "CR-202401-002",
            taskId = "TASK-002",
            recipeId = "RCP-002",
            recipeName = "柠檬酸配方",
            recipeCode = "CL-2024B",
            category = "酸味剂",
            operator = "王五",
            quantity = 25.0,
            unit = "kg",
            actualQuantity = 24.7,
            customer = "客户B",
            salesOwner = "赵六",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "今天 12:40",
            tags = listOf("常规单"),
            note = "正在进行中，预计下午完成"
        ),
        ConfigurationRecord(
            id = "CR-202401-003",
            taskId = "TASK-003",
            recipeId = "RCP-003",
            recipeName = "苹果香精配方",
            recipeCode = "LC-0907",
            category = "香精类",
            operator = "张三",
            quantity = 8.0,
            unit = "kg",
            actualQuantity = 0.0,
            customer = "新客户",
            salesOwner = "孙七娘",
            resultStatus = ConfigurationRecordStatus.IN_REVIEW,
            updatedAt = "昨天 19:30",
            tags = listOf("新单", "试样"),
            note = "等待主管审核确认"
        ),
        ConfigurationRecord(
            id = "CR-202401-004",
            taskId = "TASK-004",
            recipeId = "RCP-004",
            recipeName = "葡萄香精配方",
            recipeCode = "GQ-0512",
            category = "香精类",
            operator = "王五",
            quantity = 15.0,
            unit = "kg",
            actualQuantity = 15.2,
            customer = "老客户",
            salesOwner = "李四",
            resultStatus = ConfigurationRecordStatus.ARCHIVED,
            updatedAt = "上周五",
            tags = listOf("归档"),
            note = "已归档"
        ),
        ConfigurationRecord(
            id = "CR-202401-005",
            taskId = "TASK-005",
            recipeId = "RCP-005",
            recipeName = "蜜桃香精配方",
            recipeCode = "HX-7721",
            category = "香精类",
            operator = "张三",
            quantity = 18.0,
            unit = "kg",
            actualQuantity = 17.6,
            customer = "客户C",
            salesOwner = "赵六",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "今天 09:50",
            tags = listOf("加急", "大客户"),
            note = "配料进行中，注意精度"
        )
    )
}
