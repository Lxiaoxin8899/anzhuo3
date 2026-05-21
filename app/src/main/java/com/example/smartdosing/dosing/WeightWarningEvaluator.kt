package com.example.smartdosing.dosing

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 重量预警等级，统一供界面、提示音和自动确认逻辑使用。
 */
enum class WeightWarningLevel {
    IDLE,
    FILLING,
    APPROACHING,
    SLOW_DOWN,
    FINE_DOSING,
    IN_TOLERANCE,
    OVER_LIMIT,
    HARD_OVER_LIMIT
}

/**
 * 超标后的软件锁定策略。
 */
enum class OverLimitLockMode {
    OFF,
    SOFT,
    HARD_ON_SEVERE
}

/**
 * 重量预警配置。
 *
 * tolerancePermille 使用千分比，例如 10 表示 ±10‰。
 */
data class WeightWarningConfig(
    val tolerancePermille: Int,
    val approachPercent: Int = 80,
    val slowDownPercent: Int = 90,
    val fineDosingPercent: Int = 95,
    val hardOverToleranceMultiplier: Double = 2.0,
    val hardOverMinAbsoluteGram: Double = 0.05,
    val overLimitLockMode: OverLimitLockMode = OverLimitLockMode.SOFT
)

/**
 * 单次重量评估结果。
 */
data class WeightEvaluation(
    val level: WeightWarningLevel,
    val targetWeight: Double,
    val actualWeight: Double,
    val remainingWeight: Double,
    val deviationWeight: Double,
    val deviationPercent: Double,
    val progressRatio: Double,
    val toleranceWeight: Double,
    val allowManualConfirm: Boolean,
    val allowAutoConfirm: Boolean,
    val requireAdminUnlock: Boolean,
    val title: String,
    val message: String
)

/**
 * 统一重量评估器，避免界面、声音和自动确认各自重复计算阈值。
 */
object WeightWarningEvaluator {

    fun evaluate(
        actualWeight: Double,
        targetWeight: Double,
        config: WeightWarningConfig
    ): WeightEvaluation {
        if (targetWeight <= 0 || actualWeight <= 0) {
            return result(
                level = WeightWarningLevel.IDLE,
                actualWeight = actualWeight,
                targetWeight = targetWeight,
                toleranceWeight = 0.0,
                title = "等待称重",
                message = "请放置物料并开始称重"
            )
        }

        val safeTolerancePermille = config.tolerancePermille.coerceAtLeast(0)
        val toleranceWeight = targetWeight * safeTolerancePermille / 1000.0
        val lowerBound = targetWeight - toleranceWeight
        val upperBound = targetWeight + toleranceWeight
        val hardOverDelta = max(
            toleranceWeight * config.hardOverToleranceMultiplier.coerceAtLeast(1.0),
            config.hardOverMinAbsoluteGram.coerceAtLeast(0.0)
        )
        val hardOverBound = targetWeight + hardOverDelta
        val progressRatio = actualWeight / targetWeight

        val level = when {
            actualWeight > hardOverBound -> WeightWarningLevel.HARD_OVER_LIMIT
            actualWeight > upperBound -> WeightWarningLevel.OVER_LIMIT
            actualWeight in lowerBound..upperBound -> WeightWarningLevel.IN_TOLERANCE
            progressRatio >= config.fineDosingPercent / 100.0 -> WeightWarningLevel.FINE_DOSING
            progressRatio >= config.slowDownPercent / 100.0 -> WeightWarningLevel.SLOW_DOWN
            progressRatio >= config.approachPercent / 100.0 -> WeightWarningLevel.APPROACHING
            else -> WeightWarningLevel.FILLING
        }

        val requireAdminUnlock = level == WeightWarningLevel.HARD_OVER_LIMIT &&
            config.overLimitLockMode == OverLimitLockMode.HARD_ON_SEVERE

        val (title, message) = messageFor(level, actualWeight, targetWeight, toleranceWeight)
        return result(
            level = level,
            actualWeight = actualWeight,
            targetWeight = targetWeight,
            toleranceWeight = toleranceWeight,
            requireAdminUnlock = requireAdminUnlock,
            title = title,
            message = message
        )
    }

    private fun result(
        level: WeightWarningLevel,
        actualWeight: Double,
        targetWeight: Double,
        toleranceWeight: Double,
        requireAdminUnlock: Boolean = false,
        title: String,
        message: String
    ): WeightEvaluation {
        val deviationWeight = actualWeight - targetWeight
        val deviationPercent = if (targetWeight > 0) deviationWeight / targetWeight * 100.0 else 0.0
        return WeightEvaluation(
            level = level,
            targetWeight = targetWeight,
            actualWeight = actualWeight,
            remainingWeight = targetWeight - actualWeight,
            deviationWeight = deviationWeight,
            deviationPercent = deviationPercent,
            progressRatio = if (targetWeight > 0) actualWeight / targetWeight else 0.0,
            toleranceWeight = toleranceWeight,
            allowManualConfirm = level == WeightWarningLevel.IN_TOLERANCE,
            allowAutoConfirm = level == WeightWarningLevel.IN_TOLERANCE,
            requireAdminUnlock = requireAdminUnlock,
            title = title,
            message = message
        )
    }

    private fun messageFor(
        level: WeightWarningLevel,
        actualWeight: Double,
        targetWeight: Double,
        toleranceWeight: Double
    ): Pair<String, String> {
        val remaining = formatWeight(abs(targetWeight - actualWeight))
        val over = formatWeight((actualWeight - targetWeight).coerceAtLeast(0.0))
        val tolerance = formatWeight(toleranceWeight)
        return when (level) {
            WeightWarningLevel.IDLE -> "等待称重" to "请放置物料并开始称重"
            WeightWarningLevel.FILLING -> "加注中" to "继续加注，距离目标还差 ${remaining} g"
            WeightWarningLevel.APPROACHING -> "接近目标" to "接近目标，请注意控制速度"
            WeightWarningLevel.SLOW_DOWN -> "请减速" to "接近目标，请减速"
            WeightWarningLevel.FINE_DOSING -> "少量加入" to "少量加入，还差 ${remaining} g"
            WeightWarningLevel.IN_TOLERANCE -> "已进入合格范围" to "当前重量在允许误差 ±${tolerance} g 内"
            WeightWarningLevel.OVER_LIMIT -> "重量超出允许范围" to "已超出 ${over} g，请处理后继续"
            WeightWarningLevel.HARD_OVER_LIMIT -> "严重超标" to "已严重超出目标重量，需要管理员处理"
        }
    }

    private fun formatWeight(value: Double): String {
        return String.format(Locale.getDefault(), "%.3f", value)
    }
}
