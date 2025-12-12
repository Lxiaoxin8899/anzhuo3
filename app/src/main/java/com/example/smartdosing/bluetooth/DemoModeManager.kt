package com.example.smartdosing.bluetooth

import android.util.Log
import com.example.smartdosing.bluetooth.model.WeightData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 演示模式管理器
 * 用于在没有实际蓝牙电子秤的情况下模拟投料过程
 */
class DemoModeManager {

    companion object {
        private const val TAG = "DemoModeManager"

        // 演示速度预设（毫秒）
        const val SPEED_FAST = 100L      // 快速
        const val SPEED_NORMAL = 200L    // 正常
        const val SPEED_SLOW = 400L      // 缓慢
    }

    /**
     * 演示场景类型
     */
    enum class DemoScenario {
        GRADUAL,        // 渐进式：逐步增加到目标重量
        OVERSHOOT,      // 超调式：先超过目标再回调
        UNSTABLE,       // 不稳定：重量波动后稳定
        INSTANT         // 瞬时：直接到达目标重量
    }

    /**
     * 演示状态
     */
    enum class DemoState {
        IDLE,           // 空闲
        RUNNING,        // 运行中
        PAUSED,         // 暂停
        COMPLETED       // 完成
    }

    private val _currentWeight = MutableStateFlow<WeightData?>(null)
    val currentWeight: StateFlow<WeightData?> = _currentWeight.asStateFlow()

    private val _demoState = MutableStateFlow(DemoState.IDLE)
    val demoState: StateFlow<DemoState> = _demoState.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var demoJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 演示配置
    var scenario: DemoScenario = DemoScenario.GRADUAL
    var speedMs: Long = SPEED_NORMAL
    var unit: String = "g"

    /**
     * 开始演示模式
     */
    fun startDemo() {
        Log.i(TAG, "启动演示模式")
        _isActive.value = true
        _demoState.value = DemoState.IDLE
        // 初始化为0重量
        _currentWeight.value = WeightData(
            value = 0.0,
            unit = unit,
            isStable = true,
            rawString = "DEMO: 0.000 $unit"
        )
    }

    /**
     * 停止演示模式
     */
    fun stopDemo() {
        Log.i(TAG, "停止演示模式")
        demoJob?.cancel()
        demoJob = null
        _isActive.value = false
        _demoState.value = DemoState.IDLE
        _currentWeight.value = null
    }

    /**
     * 模拟投料到目标重量
     * @param targetWeight 目标重量
     * @param onComplete 完成回调
     */
    fun simulateWeighing(
        targetWeight: Double,
        onComplete: (() -> Unit)? = null
    ) {
        if (!_isActive.value) {
            Log.w(TAG, "演示模式未激活")
            return
        }

        demoJob?.cancel()
        _demoState.value = DemoState.RUNNING

        demoJob = scope.launch {
            try {
                when (scenario) {
                    DemoScenario.GRADUAL -> simulateGradual(targetWeight)
                    DemoScenario.OVERSHOOT -> simulateOvershoot(targetWeight)
                    DemoScenario.UNSTABLE -> simulateUnstable(targetWeight)
                    DemoScenario.INSTANT -> simulateInstant(targetWeight)
                }
                _demoState.value = DemoState.COMPLETED
                onComplete?.invoke()
            } catch (e: CancellationException) {
                Log.d(TAG, "演示被取消")
                _demoState.value = DemoState.IDLE
            }
        }
    }

    /**
     * 渐进式模拟：逐步增加到目标重量
     */
    private suspend fun simulateGradual(targetWeight: Double) {
        val startWeight = _currentWeight.value?.value ?: 0.0
        val steps = 20
        val increment = (targetWeight - startWeight) / steps

        // 逐步增加（不稳定）
        for (i in 1..steps) {
            val currentValue = startWeight + increment * i
            val noise = if (i < steps) (Math.random() - 0.5) * 0.5 else 0.0
            emitWeight(currentValue + noise, isStable = false)
            delay(speedMs)
        }

        // 最终稳定
        delay(speedMs * 2)
        emitWeight(targetWeight, isStable = true)
        delay(speedMs * 3) // 保持稳定一段时间
    }

    /**
     * 超调式模拟：先超过目标再回调
     */
    private suspend fun simulateOvershoot(targetWeight: Double) {
        val startWeight = _currentWeight.value?.value ?: 0.0
        val overshootAmount = targetWeight * 0.1 // 超调10%

        // 快速增加到超调点
        val steps1 = 10
        val increment1 = (targetWeight + overshootAmount - startWeight) / steps1
        for (i in 1..steps1) {
            val currentValue = startWeight + increment1 * i
            emitWeight(currentValue, isStable = false)
            delay(speedMs)
        }

        // 回调到目标
        val steps2 = 5
        val decrement = overshootAmount / steps2
        for (i in 1..steps2) {
            val currentValue = targetWeight + overshootAmount - decrement * i
            emitWeight(currentValue, isStable = false)
            delay(speedMs)
        }

        // 最终稳定
        delay(speedMs * 2)
        emitWeight(targetWeight, isStable = true)
        delay(speedMs * 3)
    }

    /**
     * 不稳定模拟：重量波动后稳定
     */
    private suspend fun simulateUnstable(targetWeight: Double) {
        val startWeight = _currentWeight.value?.value ?: 0.0

        // 快速到达目标附近
        val steps1 = 8
        val increment = (targetWeight - startWeight) / steps1
        for (i in 1..steps1) {
            val currentValue = startWeight + increment * i
            emitWeight(currentValue, isStable = false)
            delay(speedMs)
        }

        // 波动阶段
        for (i in 1..10) {
            val noise = (Math.random() - 0.5) * targetWeight * 0.05 // ±2.5%波动
            emitWeight(targetWeight + noise, isStable = false)
            delay(speedMs)
        }

        // 最终稳定
        delay(speedMs * 2)
        emitWeight(targetWeight, isStable = true)
        delay(speedMs * 3)
    }

    /**
     * 瞬时模拟：直接到达目标重量
     */
    private suspend fun simulateInstant(targetWeight: Double) {
        emitWeight(targetWeight, isStable = false)
        delay(speedMs * 3)
        emitWeight(targetWeight, isStable = true)
        delay(speedMs * 3)
    }

    /**
     * 模拟去皮操作
     */
    fun simulateTare() {
        if (!_isActive.value) return
        Log.d(TAG, "模拟去皮")
        _currentWeight.value = WeightData(
            value = 0.0,
            unit = unit,
            isStable = true,
            rawString = "DEMO: 0.000 $unit (TARE)"
        )
    }

    /**
     * 模拟置零操作
     */
    fun simulateZero() {
        if (!_isActive.value) return
        Log.d(TAG, "模拟置零")
        _currentWeight.value = WeightData(
            value = 0.0,
            unit = unit,
            isStable = true,
            rawString = "DEMO: 0.000 $unit (ZERO)"
        )
    }

    /**
     * 暂停演示
     */
    fun pause() {
        if (_demoState.value == DemoState.RUNNING) {
            demoJob?.cancel()
            _demoState.value = DemoState.PAUSED
        }
    }

    /**
     * 发送重量数据
     */
    private fun emitWeight(value: Double, isStable: Boolean) {
        val formattedValue = String.format("%.3f", value).toDouble()
        _currentWeight.value = WeightData(
            value = formattedValue,
            unit = unit,
            isStable = isStable,
            rawString = "DEMO: $formattedValue $unit${if (isStable) "" else " ?"}"
        )
    }

    /**
     * 清理资源
     */
    fun destroy() {
        stopDemo()
        scope.cancel()
    }
}

/**
 * 演示场景配置
 */
data class DemoScenarioConfig(
    val name: String,
    val description: String,
    val scenario: DemoModeManager.DemoScenario,
    val speedMs: Long = DemoModeManager.SPEED_NORMAL
) {
    companion object {
        val PRESETS = listOf(
            DemoScenarioConfig(
                name = "标准投料",
                description = "逐步增加到目标重量，模拟正常投料过程",
                scenario = DemoModeManager.DemoScenario.GRADUAL,
                speedMs = DemoModeManager.SPEED_NORMAL
            ),
            DemoScenarioConfig(
                name = "快速投料",
                description = "快速完成投料，适合快速演示",
                scenario = DemoModeManager.DemoScenario.GRADUAL,
                speedMs = DemoModeManager.SPEED_FAST
            ),
            DemoScenarioConfig(
                name = "超调回调",
                description = "先超过目标重量再回调，模拟手抖场景",
                scenario = DemoModeManager.DemoScenario.OVERSHOOT,
                speedMs = DemoModeManager.SPEED_NORMAL
            ),
            DemoScenarioConfig(
                name = "不稳定称重",
                description = "重量波动后稳定，模拟振动环境",
                scenario = DemoModeManager.DemoScenario.UNSTABLE,
                speedMs = DemoModeManager.SPEED_NORMAL
            ),
            DemoScenarioConfig(
                name = "瞬时到位",
                description = "直接到达目标重量，最快演示",
                scenario = DemoModeManager.DemoScenario.INSTANT,
                speedMs = DemoModeManager.SPEED_FAST
            )
        )
    }
}
