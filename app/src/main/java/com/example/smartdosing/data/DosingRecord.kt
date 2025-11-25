package com.example.smartdosing.data

/**
 * 投料记录领域模型
 */
data class DosingRecord(
    val id: String,
    val recipeId: String?,
    val recipeCode: String?,
    val recipeName: String,
    val operatorName: String,
    val checklist: List<String>,
    val startTime: String,
    val endTime: String,
    val totalMaterials: Int,
    val completedMaterials: Int,
    val totalActualWeight: Double,
    val tolerancePercent: Float,
    val overLimitCount: Int,
    val averageDeviationPercent: Double,
    val status: DosingRecordStatus,
    val createdAt: String,
    val details: List<DosingRecordDetail>
)

/**
 * 投料记录明细
 */
data class DosingRecordDetail(
    val id: String,
    val recordId: String,
    val materialSequence: Int,
    val materialCode: String,
    val materialName: String,
    val targetWeight: Double,
    val actualWeight: Double,
    val unit: String,
    val isOverLimit: Boolean,
    val overLimitPercent: Double
)

/**
 * 投料记录状态
 */
enum class DosingRecordStatus {
    COMPLETED,
    ABORTED
}
