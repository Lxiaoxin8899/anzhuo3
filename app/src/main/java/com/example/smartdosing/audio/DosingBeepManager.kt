package com.example.smartdosing.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.example.smartdosing.dosing.WeightEvaluation
import com.example.smartdosing.dosing.WeightWarningLevel
import kotlinx.coroutines.*
import kotlin.math.pow

/**
 * 配料提示音管理器
 * 根据重量变化播放提示音，支持渐进模式和阈值模式
 */
class DosingBeepManager {

    companion object {
        private const val TAG = "DosingBeepManager"
    }

    private var toneGenerator: ToneGenerator? = null
    private var beepJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isPlaying = false

    // 阈值模式：是否已经触发过（用于"仅提示一次"）
    @Volatile
    private var thresholdTriggered = false

    @Volatile
    private var lastWarningLevel: WeightWarningLevel? = null

    @Volatile
    private var hardOverLimitAlertCount = 0

    /**
     * 初始化 ToneGenerator
     */
    fun initialize() {
        if (toneGenerator == null) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                Log.d(TAG, "ToneGenerator 初始化成功")
            } catch (e: Exception) {
                Log.e(TAG, "ToneGenerator 初始化失败", e)
            }
        }
    }

    /**
     * 渐进模式：根据当前重量与目标重量的比例计算 beep 间隔
     *
     * 算法：
     * ratio = actual / target (0.0 ~ 1.0+)
     * 当 ratio < startRatio 时不发声
     * 当 ratio >= startRatio 时：
     *   normalizedProgress = (ratio - startRatio) / (1.0 - startRatio)  → 0.0~1.0
     *   interval = maxInterval - (maxInterval - minInterval) * normalizedProgress^curveExponent
     * 当 ratio >= 1.0（到达目标）时播放到达音并停止
     */
    fun updateProgressive(
        actualWeight: Double,
        targetWeight: Double,
        tolerancePermille: Int = 10,
        startPercent: Int = 50,
        maxIntervalMs: Long = 1500L,
        minIntervalMs: Long = 100L,
        curveExponent: Float = 2.0f,
        toneDurationMs: Int = 80,
        toneType: Int = ToneGenerator.TONE_PROP_BEEP,
        arrivedToneType: Int = ToneGenerator.TONE_PROP_ACK,
        arrivedDurationMs: Int = 200
    ) {
        if (targetWeight <= 0) return

        val ratio = actualWeight / targetWeight
        val tolerance = targetWeight * tolerancePermille / 1000.0
        val inTolerance = actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)
        val startRatio = startPercent / 100.0

        if (inTolerance) {
            // 到达目标范围，播放到达音并停止循环
            stopBeeping()
            playTone(arrivedToneType, arrivedDurationMs)
            return
        }

        if (ratio < startRatio || ratio > 1.0 + tolerancePermille / 1000.0) {
            // 还没到开始比例，或已超标，停止
            stopBeeping()
            return
        }

        // 计算间隔
        val normalizedProgress = ((ratio - startRatio) / (1.0 - startRatio))
            .coerceIn(0.0, 1.0)
        val intervalMs = maxIntervalMs -
            ((maxIntervalMs - minIntervalMs) * normalizedProgress.pow(curveExponent.toDouble())).toLong()

        startBeepLoop(
            intervalMs.coerceIn(minIntervalMs, maxIntervalMs),
            toneType = toneType,
            toneDurationMs = toneDurationMs
        )
    }

    /**
     * 阈值模式：到达阈值后触发提示
     * @param thresholdPercent 阈值百分比（如 90 表示达到目标的90%时触发）
     * @param continuous true=连续提示，false=仅提示一次
     */
    fun updateThreshold(
        actualWeight: Double,
        targetWeight: Double,
        thresholdPercent: Int,
        continuous: Boolean,
        tolerancePermille: Int = 10,
        intervalMs: Long = 300L,
        toneDurationMs: Int = 80,
        toneType: Int = ToneGenerator.TONE_PROP_BEEP,
        arrivedToneType: Int = ToneGenerator.TONE_PROP_ACK,
        arrivedDurationMs: Int = 200
    ) {
        if (targetWeight <= 0) return

        val ratio = actualWeight / targetWeight
        val thresholdRatio = thresholdPercent / 100.0
        val tolerance = targetWeight * tolerancePermille / 1000.0
        val inTolerance = actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)

        if (inTolerance) {
            // 到达目标，播放到达音
            stopBeeping()
            playTone(arrivedToneType, arrivedDurationMs)
            thresholdTriggered = false // 重置，为下一个物料准备
            return
        }

        if (ratio >= thresholdRatio && ratio < 1.0 + tolerancePermille / 1000.0) {
            if (continuous) {
                // 连续提示：固定间隔 beep
                startBeepLoop(intervalMs, toneType = toneType, toneDurationMs = toneDurationMs)
            } else {
                // 仅提示一次
                if (!thresholdTriggered) {
                    thresholdTriggered = true
                    playTone(toneType, toneDurationMs)
                }
            }
        } else {
            stopBeeping()
            if (ratio < thresholdRatio) {
                thresholdTriggered = false // 重量回落到阈值以下，重置
            }
        }
    }

    /**
     * 基于统一重量评估结果播放提示音。
     * 合格、普通超标和严重超标只做有限次提示，避免反复刷屏式报警。
     */
    fun updateWarning(
        evaluation: WeightEvaluation,
        mode: BeepMode,
        thresholdPercent: Int,
        thresholdContinuous: Boolean = true,
        progressiveMaxIntervalMs: Long = 1500L,
        progressiveMinIntervalMs: Long = 100L,
        thresholdIntervalMs: Long = 300L,
        toneDurationMs: Int = 80,
        toneType: Int = ToneGenerator.TONE_PROP_BEEP,
        arrivedToneType: Int = ToneGenerator.TONE_PROP_ACK,
        arrivedDurationMs: Int = 200,
        overLimitToneType: Int = ToneGenerator.TONE_PROP_NACK,
        overLimitDurationMs: Int = 180
    ) {
        if (mode == BeepMode.OFF) {
            resetWarningState()
            return
        }

        if (lastWarningLevel != evaluation.level) {
            lastWarningLevel = evaluation.level
            thresholdTriggered = false
            hardOverLimitAlertCount = 0
        }

        when (evaluation.level) {
            WeightWarningLevel.IDLE,
            WeightWarningLevel.FILLING -> {
                stopBeeping()
                thresholdTriggered = false
            }

            WeightWarningLevel.APPROACHING,
            WeightWarningLevel.SLOW_DOWN,
            WeightWarningLevel.FINE_DOSING -> {
                if (mode == BeepMode.THRESHOLD && evaluation.progressRatio < thresholdPercent / 100.0) {
                    stopBeeping()
                    thresholdTriggered = false
                    return
                }
                val intervalMs = when (evaluation.level) {
                    WeightWarningLevel.APPROACHING -> progressiveMaxIntervalMs
                    WeightWarningLevel.SLOW_DOWN -> ((progressiveMaxIntervalMs + thresholdIntervalMs) / 2)
                    WeightWarningLevel.FINE_DOSING -> progressiveMinIntervalMs.coerceAtLeast(80L)
                    else -> thresholdIntervalMs
                }.coerceAtLeast(80L)

                if (mode == BeepMode.PROGRESSIVE || (mode == BeepMode.THRESHOLD && thresholdContinuous)) {
                    startBeepLoop(intervalMs, toneType = toneType, toneDurationMs = toneDurationMs)
                } else if (!thresholdTriggered) {
                    thresholdTriggered = true
                    playTone(toneType, toneDurationMs)
                }
            }

            WeightWarningLevel.IN_TOLERANCE -> {
                stopBeeping()
                if (!thresholdTriggered) {
                    thresholdTriggered = true
                    playTone(arrivedToneType, arrivedDurationMs)
                }
            }

            WeightWarningLevel.OVER_LIMIT -> {
                stopBeeping()
                if (!thresholdTriggered) {
                    thresholdTriggered = true
                    playTone(overLimitToneType, overLimitDurationMs)
                }
            }

            WeightWarningLevel.HARD_OVER_LIMIT -> {
                stopBeeping()
                if (hardOverLimitAlertCount < 3) {
                    hardOverLimitAlertCount += 1
                    playTone(overLimitToneType, overLimitDurationMs)
                }
            }
        }
    }

    /**
     * 重置阈值触发状态（切换物料时调用）
     */
    fun resetThresholdState() {
        thresholdTriggered = false
        resetWarningState()
    }

    /**
     * 重置统一预警提示状态。
     */
    fun resetWarningState() {
        thresholdTriggered = false
        lastWarningLevel = null
        hardOverLimitAlertCount = 0
        stopBeeping()
    }

    /**
     * 播放自动确认倒计时提示音（单次）
     */
    fun playAutoConfirmCountdownBeep(toneType: Int, durationMs: Int) {
        playTone(toneType, durationMs)
    }

    /**
     * 播放自动确认完成音（单次）
     */
    fun playAutoConfirmCompleteBeep(toneType: Int, durationMs: Int) {
        playTone(toneType, durationMs)
    }

    /**
     * 启动循环 beep
     */
    private fun startBeepLoop(
        intervalMs: Long,
        toneType: Int = ToneGenerator.TONE_PROP_BEEP,
        toneDurationMs: Int = 80
    ) {
        // 如果已经在播放，取消旧的，启动新的
        if (isPlaying) {
            beepJob?.cancel()
        }
        isPlaying = true
        beepJob = scope.launch {
            while (isActive) {
                playTone(toneType, toneDurationMs)
                delay(intervalMs)
            }
        }
    }

    /**
     * 停止循环 beep
     */
    fun stopBeeping() {
        isPlaying = false
        beepJob?.cancel()
        beepJob = null
    }

    /**
     * 播放单次音调
     */
    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "播放提示音失败", e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopBeeping()
        scope.cancel()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 ToneGenerator 失败", e)
        }
    }
}
