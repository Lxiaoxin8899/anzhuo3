package com.example.smartdosing.data

/**
 * 批量导入统计
 */
data class ImportSummary(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<String>
)
