package com.example.smartdosing.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.*

/**
 * 配料提示音管理器
 * 根据重量变化播放提示音，支持渐进模式和阈值模式
 */
class DosingBeepManager {

    companion object {
        private const val TAG = "DosingBeepManager"

        // 渐进模式参数
        const val PROGRESSIVE_MAX_INTERVAL_MS = 1500L  // 最慢间隔（距离目标远）
        const val PROGRESSIVE_MIN_INTERVAL_MS = 100L   // 最快间隔（接近目标）
        const val PROGRESSIVE_START_RATIO = 0.5        // 开始提示的比例（达到目标50%时开始）

        // 阈值模式参数
        const val DEFAULT_THRESHOLD_PERCENT = 90        // 默认阈值：目标的90%

        // 音调
        const val TONE_DURATION_MS = 80                 // 单次提示音时长
        const val TONE_TYPE = ToneGenerator.TONE_PROP_BEEP // 提示音类型
        const val TONE_TYPE_ARRIVED = ToneGenerator.TONE_PROP_ACK // 到达目标音
    }

    private var toneGenerator: ToneGenerator? = null
    private var beepJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isPlaying = false

    // 阈值模式：是否已经触发过（用于"仅提示一次"）
    @Volatile
    private var thresholdTriggered = false

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
     * 当 ratio < PROGRESSIVE_START_RATIO 时不发声
     * 当 ratio >= PROGRESSIVE_START_RATIO 时：
     *   normalizedProgress = (ratio - startRatio) / (1.0 - startRatio)  → 0.0~1.0
     *   interval = MAX_INTERVAL - (MAX_INTERVAL - MIN_INTERVAL) * normalizedProgress^2
     *   使用平方曲线让接近目标时加速更明显
     * 当 ratio >= 1.0（到达目标）时播放到达音并停止
     */
    fun updateProgressive(
        actualWeight: Double,
        targetWeight: Double,
        tolerancePermille: Int = 10
    ) {
        if (targetWeight <= 0) return

        val ratio = actualWeight / targetWeight
        val tolerance = targetWeight * tolerancePermille / 1000.0
        val inTolerance = actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)

        if (inTolerance) {
            // 到达目标范围，播放到达音并停止循环
            stopBeeping()
            playTone(TONE_TYPE_ARRIVED, 200)
            return
        }

        if (ratio < PROGRESSIVE_START_RATIO || ratio > 1.0 + tolerancePermille / 1000.0) {
            // 还没到开始比例，或已超标，停止
            stopBeeping()
            return
        }

        // 计算间隔
        val normalizedProgress = ((ratio - PROGRESSIVE_START_RATIO) / (1.0 - PROGRESSIVE_START_RATIO))
            .coerceIn(0.0, 1.0)
        val intervalMs = PROGRESSIVE_MAX_INTERVAL_MS -
            ((PROGRESSIVE_MAX_INTERVAL_MS - PROGRESSIVE_MIN_INTERVAL_MS) * normalizedProgress * normalizedProgress).toLong()

        startBeepLoop(intervalMs.coerceIn(PROGRESSIVE_MIN_INTERVAL_MS, PROGRESSIVE_MAX_INTERVAL_MS))
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
        tolerancePermille: Int = 10
    ) {
        if (targetWeight <= 0) return

        val ratio = actualWeight / targetWeight
        val thresholdRatio = thresholdPercent / 100.0
        val tolerance = targetWeight * tolerancePermille / 1000.0
        val inTolerance = actualWeight in (targetWeight - tolerance)..(targetWeight + tolerance)

        if (inTolerance) {
            // 到达目标，播放到达音
            stopBeeping()
            playTone(TONE_TYPE_ARRIVED, 200)
            thresholdTriggered = false // 重置，为下一个物料准备
            return
        }

        if (ratio >= thresholdRatio && ratio < 1.0 + tolerancePermille / 1000.0) {
            if (continuous) {
                // 连续提示：固定间隔 beep
                startBeepLoop(300L)
            } else {
                // 仅提示一次
                if (!thresholdTriggered) {
                    thresholdTriggered = true
                    playTone(TONE_TYPE, TONE_DURATION_MS)
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
     * 重置阈值触发状态（切换物料时调用）
     */
    fun resetThresholdState() {
        thresholdTriggered = false
        stopBeeping()
    }

    /**
     * 启动循环 beep
     */
    private fun startBeepLoop(intervalMs: Long) {
        // 如果已经在以相同间隔播放，不重启
        if (isPlaying) {
            // 更新间隔：取消旧的，启动新的
            beepJob?.cancel()
        }
        isPlaying = true
        beepJob = scope.launch {
            while (isActive) {
                playTone(TONE_TYPE, TONE_DURATION_MS)
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
