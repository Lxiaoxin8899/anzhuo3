
package com.example.smartdosing.data

/**
 * ???????????????????????
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
            recipeName = "???????",
            recipeCode = "ST-2401",
            category = "????",
            operator = "??",
            quantity = 12.5,
            unit = "kg",
            actualQuantity = 12.1,
            customer = "????A",
            salesOwner = "??",
            resultStatus = ConfigurationRecordStatus.COMPLETED,
            updatedAt = "?? 14:10",
            tags = listOf("??", "??"),
            note = "????<2%?????"
        ),
        ConfigurationRecord(
            id = "CR-202401-002",
            taskId = "TASK-002",
            recipeId = "RCP-002",
            recipeName = "??????",
            recipeCode = "CL-2024B",
            category = "????",
            operator = "??",
            quantity = 25.0,
            unit = "kg",
            actualQuantity = 24.7,
            customer = "??B",
            salesOwner = "??",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "?? 12:40",
            tags = listOf("???"),
            note = "???????????"
        ),
        ConfigurationRecord(
            id = "CR-202401-003",
            taskId = "TASK-003",
            recipeId = "RCP-003",
            recipeName = "??????",
            recipeCode = "LC-0907",
            category = "????",
            operator = "??",
            quantity = 8.0,
            unit = "kg",
            actualQuantity = 0.0,
            customer = "????",
            salesOwner = "???",
            resultStatus = ConfigurationRecordStatus.IN_REVIEW,
            updatedAt = "?? 19:30",
            tags = listOf("??", "??"),
            note = "????????"
        ),
        ConfigurationRecord(
            id = "CR-202401-004",
            taskId = "TASK-004",
            recipeId = "RCP-004",
            recipeName = "??????",
            recipeCode = "GQ-0512",
            category = "????",
            operator = "??",
            quantity = 15.0,
            unit = "kg",
            actualQuantity = 15.2,
            customer = "???",
            salesOwner = "??",
            resultStatus = ConfigurationRecordStatus.ARCHIVED,
            updatedAt = "???",
            tags = listOf("??"),
            note = "????"
        ),
        ConfigurationRecord(
            id = "CR-202401-005",
            taskId = "TASK-005",
            recipeId = "RCP-005",
            recipeName = "??????",
            recipeCode = "HX-7721",
            category = "????",
            operator = "??",
            quantity = 18.0,
            unit = "kg",
            actualQuantity = 17.6,
            customer = "??C",
            salesOwner = "??",
            resultStatus = ConfigurationRecordStatus.RUNNING,
            updatedAt = "?? 09:50",
            tags = listOf("??", "???"),
            note = "????????"
        )
    )
}
