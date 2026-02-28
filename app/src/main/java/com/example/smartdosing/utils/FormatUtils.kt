package com.example.smartdosing.utils

import java.util.Locale

/**
 * 格式化工具类
 */
object FormatUtils {
    /**
     * 格式化重量显示，保留三位小数（精度到 0.001g）
     * @param weight 重量值
     * @return 格式化后的字符串，例如 "12.500"
     */
    fun formatWeight(weight: Double): String {
        return String.format(Locale.getDefault(), "%.3f", weight)
    }

    /**
     * 格式化重量显示（带单位）
     * @param weight 重量值
     * @param unit 单位，默认 "g"
     * @return 格式化后的字符串，例如 "12.500 g"
     */
    fun formatWeightWithUnit(weight: Double, unit: String = "g"): String {
        return "${formatWeight(weight)} $unit"
    }

    /**
     * 格式化百分比显示，保留一位小数
     * @param value 百分比值
     * @return 格式化后的字符串，例如 "12.5%"
     */
    fun formatPercent(value: Double): String {
        return String.format(Locale.getDefault(), "%.1f%%", value)
    }
}
