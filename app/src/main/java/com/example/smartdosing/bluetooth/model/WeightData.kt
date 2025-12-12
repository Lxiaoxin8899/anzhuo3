package com.example.smartdosing.bluetooth.model

data class WeightData(
    val value: Double,
    val unit: String,
    val isStable: Boolean,
    val rawString: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getDisplayValue(): String {
        return String.format("%.3f", value)
    }

    fun getFullDisplay(): String {
        return "${getDisplayValue()} $unit"
    }
}
