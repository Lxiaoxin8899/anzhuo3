package com.example.smartdosing.data

import com.google.gson.annotations.SerializedName

/**
 * 批量导入统计
 */
data class ImportSummary(
    @SerializedName("total") val total: Int,
    @SerializedName("success") val success: Int,
    @SerializedName("failed") val failed: Int,
    @SerializedName("errors") val errors: List<String>
)
