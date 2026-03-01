package com.example.smartdosing.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smartdosing.audio.BeepMode
import com.example.smartdosing.audio.BeepToneType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingPreferencesDataStore by preferencesDataStore(name = "dosing_preferences")

/**
 * 投料模式枚举
 */
enum class DosingMode(val displayName: String) {
    MANUAL("手动输入"),
    BLUETOOTH("蓝牙电子秤")
}

/**
 * 重量单位枚举
 */
enum class WeightUnit(val symbol: String, val displayName: String) {
    GRAM("g", "克 (g)"),
    KILOGRAM("kg", "千克 (kg)")
}

/**
 * 主题模式枚举
 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

data class DosingPreferencesState(
    val voiceRepeatEnabled: Boolean = false,
    val voiceRepeatCount: Int = 1,
    val dosingMode: DosingMode = DosingMode.MANUAL,
    val weightUnit: WeightUnit = WeightUnit.GRAM,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val beepMode: BeepMode = BeepMode.OFF,
    val beepThresholdPercent: Int = 90,
    val beepThresholdContinuous: Boolean = true,
    // 渐进模式细化
    val progressiveStartPercent: Int = 50,
    val progressiveMaxIntervalMs: Int = 1500,
    val progressiveMinIntervalMs: Int = 100,
    val progressiveCurveExponent: Float = 2.0f,
    // 通用声音设置
    val beepToneDurationMs: Int = 80,
    val beepToneType: BeepToneType = BeepToneType.BEEP,
    val beepArrivedToneType: BeepToneType = BeepToneType.ACK,
    val beepArrivedDurationMs: Int = 200,
    // 阈值模式补充
    val thresholdIntervalMs: Int = 300,
    // 自动确认提示音（独立于 beepMode）
    val autoConfirmBeepEnabled: Boolean = false,
    val autoConfirmBeepToneType: BeepToneType = BeepToneType.BEEP2,
    val autoConfirmCompleteToneType: BeepToneType = BeepToneType.ACK,
    val autoConfirmCompleteDurationMs: Int = 300
) {
    val repeatCountForPlayback: Int
        get() = if (voiceRepeatEnabled) {
            voiceRepeatCount.coerceIn(
                DosingPreferencesManager.MIN_REPEAT_COUNT,
                DosingPreferencesManager.MAX_REPEAT_COUNT
            )
        } else {
            1
        }
}

/**
 * 投料相关偏好设置管理器
 */
class DosingPreferencesManager(context: Context) {

    private val dataStore = context.applicationContext.dosingPreferencesDataStore

    val preferencesFlow: Flow<DosingPreferencesState> = dataStore.data.map { prefs ->
        DosingPreferencesState(
            voiceRepeatEnabled = prefs[VOICE_REPEAT_ENABLED] ?: true,
            voiceRepeatCount = prefs[VOICE_REPEAT_COUNT] ?: DEFAULT_REPEAT_COUNT,
            dosingMode = DosingMode.entries.find { it.name == prefs[DOSING_MODE] } ?: DosingMode.MANUAL,
            weightUnit = WeightUnit.entries.find { it.name == prefs[WEIGHT_UNIT] } ?: WeightUnit.GRAM,
            themeMode = ThemeMode.entries.find { it.name == prefs[THEME_MODE] } ?: ThemeMode.LIGHT,
            beepMode = BeepMode.entries.find { it.name == prefs[BEEP_MODE] } ?: BeepMode.OFF,
            beepThresholdPercent = prefs[BEEP_THRESHOLD_PERCENT] ?: 90,
            beepThresholdContinuous = prefs[BEEP_THRESHOLD_CONTINUOUS] ?: true,
            progressiveStartPercent = prefs[PROGRESSIVE_START_PERCENT] ?: 50,
            progressiveMaxIntervalMs = prefs[PROGRESSIVE_MAX_INTERVAL] ?: 1500,
            progressiveMinIntervalMs = prefs[PROGRESSIVE_MIN_INTERVAL] ?: 100,
            progressiveCurveExponent = prefs[PROGRESSIVE_CURVE_EXPONENT] ?: 2.0f,
            beepToneDurationMs = prefs[BEEP_TONE_DURATION] ?: 80,
            beepToneType = BeepToneType.entries.find { it.name == prefs[BEEP_TONE_TYPE] } ?: BeepToneType.BEEP,
            beepArrivedToneType = BeepToneType.entries.find { it.name == prefs[BEEP_ARRIVED_TONE_TYPE] } ?: BeepToneType.ACK,
            beepArrivedDurationMs = prefs[BEEP_ARRIVED_DURATION] ?: 200,
            thresholdIntervalMs = prefs[THRESHOLD_INTERVAL] ?: 300,
            autoConfirmBeepEnabled = prefs[AUTO_CONFIRM_BEEP_ENABLED] ?: false,
            autoConfirmBeepToneType = BeepToneType.entries.find { it.name == prefs[AUTO_CONFIRM_BEEP_TONE_TYPE] } ?: BeepToneType.BEEP2,
            autoConfirmCompleteToneType = BeepToneType.entries.find { it.name == prefs[AUTO_CONFIRM_COMPLETE_TONE_TYPE] } ?: BeepToneType.ACK,
            autoConfirmCompleteDurationMs = prefs[AUTO_CONFIRM_COMPLETE_DURATION] ?: 300
        )
    }

    suspend fun setVoiceRepeatEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[VOICE_REPEAT_ENABLED] = enabled
        }
    }

    suspend fun setVoiceRepeatCount(count: Int) {
        val normalized = count.coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT)
        dataStore.edit { prefs ->
            prefs[VOICE_REPEAT_COUNT] = normalized
        }
    }

    suspend fun setDosingMode(mode: DosingMode) {
        dataStore.edit { prefs ->
            prefs[DOSING_MODE] = mode.name
        }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.name
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    suspend fun setBeepMode(mode: BeepMode) {
        dataStore.edit { prefs ->
            prefs[BEEP_MODE] = mode.name
        }
    }

    suspend fun setBeepThresholdPercent(percent: Int) {
        dataStore.edit { prefs ->
            prefs[BEEP_THRESHOLD_PERCENT] = percent.coerceIn(50, 99)
        }
    }

    suspend fun setBeepThresholdContinuous(continuous: Boolean) {
        dataStore.edit { prefs ->
            prefs[BEEP_THRESHOLD_CONTINUOUS] = continuous
        }
    }

    suspend fun setProgressiveStartPercent(percent: Int) {
        dataStore.edit { prefs ->
            prefs[PROGRESSIVE_START_PERCENT] = percent.coerceIn(20, 80)
        }
    }

    suspend fun setProgressiveMaxIntervalMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[PROGRESSIVE_MAX_INTERVAL] = ms.coerceIn(500, 3000)
        }
    }

    suspend fun setProgressiveMinIntervalMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[PROGRESSIVE_MIN_INTERVAL] = ms.coerceIn(50, 500)
        }
    }

    suspend fun setProgressiveCurveExponent(exponent: Float) {
        dataStore.edit { prefs ->
            prefs[PROGRESSIVE_CURVE_EXPONENT] = exponent.coerceIn(1.0f, 4.0f)
        }
    }

    suspend fun setBeepToneDurationMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[BEEP_TONE_DURATION] = ms.coerceIn(30, 300)
        }
    }

    suspend fun setBeepToneType(type: BeepToneType) {
        dataStore.edit { prefs ->
            prefs[BEEP_TONE_TYPE] = type.name
        }
    }

    suspend fun setBeepArrivedToneType(type: BeepToneType) {
        dataStore.edit { prefs ->
            prefs[BEEP_ARRIVED_TONE_TYPE] = type.name
        }
    }

    suspend fun setBeepArrivedDurationMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[BEEP_ARRIVED_DURATION] = ms.coerceIn(100, 500)
        }
    }

    suspend fun setThresholdIntervalMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[THRESHOLD_INTERVAL] = ms.coerceIn(100, 1000)
        }
    }

    suspend fun setAutoConfirmBeepEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_CONFIRM_BEEP_ENABLED] = enabled
        }
    }

    suspend fun setAutoConfirmBeepToneType(type: BeepToneType) {
        dataStore.edit { prefs ->
            prefs[AUTO_CONFIRM_BEEP_TONE_TYPE] = type.name
        }
    }

    suspend fun setAutoConfirmCompleteToneType(type: BeepToneType) {
        dataStore.edit { prefs ->
            prefs[AUTO_CONFIRM_COMPLETE_TONE_TYPE] = type.name
        }
    }

    suspend fun setAutoConfirmCompleteDurationMs(ms: Int) {
        dataStore.edit { prefs ->
            prefs[AUTO_CONFIRM_COMPLETE_DURATION] = ms.coerceIn(100, 500)
        }
    }

    companion object {
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 5
        const val DEFAULT_REPEAT_COUNT = 2

        private val VOICE_REPEAT_ENABLED = booleanPreferencesKey("voice_repeat_enabled")
        private val VOICE_REPEAT_COUNT = intPreferencesKey("voice_repeat_count")
        private val DOSING_MODE = stringPreferencesKey("dosing_mode")
        private val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val BEEP_MODE = stringPreferencesKey("beep_mode")
        private val BEEP_THRESHOLD_PERCENT = intPreferencesKey("beep_threshold_percent")
        private val BEEP_THRESHOLD_CONTINUOUS = booleanPreferencesKey("beep_threshold_continuous")
        private val PROGRESSIVE_START_PERCENT = intPreferencesKey("progressive_start_percent")
        private val PROGRESSIVE_MAX_INTERVAL = intPreferencesKey("progressive_max_interval_ms")
        private val PROGRESSIVE_MIN_INTERVAL = intPreferencesKey("progressive_min_interval_ms")
        private val PROGRESSIVE_CURVE_EXPONENT = floatPreferencesKey("progressive_curve_exponent")
        private val BEEP_TONE_DURATION = intPreferencesKey("beep_tone_duration_ms")
        private val BEEP_TONE_TYPE = stringPreferencesKey("beep_tone_type")
        private val BEEP_ARRIVED_TONE_TYPE = stringPreferencesKey("beep_arrived_tone_type")
        private val BEEP_ARRIVED_DURATION = intPreferencesKey("beep_arrived_duration_ms")
        private val THRESHOLD_INTERVAL = intPreferencesKey("threshold_interval_ms")
        private val AUTO_CONFIRM_BEEP_ENABLED = booleanPreferencesKey("auto_confirm_beep_enabled")
        private val AUTO_CONFIRM_BEEP_TONE_TYPE = stringPreferencesKey("auto_confirm_beep_tone_type")
        private val AUTO_CONFIRM_COMPLETE_TONE_TYPE = stringPreferencesKey("auto_confirm_complete_tone_type")
        private val AUTO_CONFIRM_COMPLETE_DURATION = intPreferencesKey("auto_confirm_complete_duration_ms")
    }
}
