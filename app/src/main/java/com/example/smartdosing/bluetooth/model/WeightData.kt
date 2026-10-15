package com.example.smartdosing.bluetooth.model

import java.util.Locale

data class WeightData(
    val value: Double,
    val unit: String,
    val isStable: Boolean,
    val rawString: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getDisplayValue(): String {
        return String.format(Locale.US, "%.3f", value)
    }

    fun getFullDisplay(): String {
        return "${getDisplayValue()} $unit"
    }
}
