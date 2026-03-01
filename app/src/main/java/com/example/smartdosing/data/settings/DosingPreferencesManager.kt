package com.example.smartdosing.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.smartdosing.audio.BeepMode
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
    val overLimitTolerancePercent: Float = 3f,
    val dosingMode: DosingMode = DosingMode.MANUAL,
    val weightUnit: WeightUnit = WeightUnit.GRAM,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val beepMode: BeepMode = BeepMode.OFF,
    val beepThresholdPercent: Int = 90,
    val beepThresholdContinuous: Boolean = true
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
            overLimitTolerancePercent = prefs[OVER_LIMIT_TOLERANCE] ?: DEFAULT_TOLERANCE,
            dosingMode = DosingMode.entries.find { it.name == prefs[DOSING_MODE] } ?: DosingMode.MANUAL,
            weightUnit = WeightUnit.entries.find { it.name == prefs[WEIGHT_UNIT] } ?: WeightUnit.GRAM,
            themeMode = ThemeMode.entries.find { it.name == prefs[THEME_MODE] } ?: ThemeMode.LIGHT,
            beepMode = BeepMode.entries.find { it.name == prefs[BEEP_MODE] } ?: BeepMode.OFF,
            beepThresholdPercent = prefs[BEEP_THRESHOLD_PERCENT] ?: 90,
            beepThresholdContinuous = prefs[BEEP_THRESHOLD_CONTINUOUS] ?: true
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

    suspend fun setTolerancePercent(percent: Float) {
        val normalized = percent.coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
        dataStore.edit { prefs ->
            prefs[OVER_LIMIT_TOLERANCE] = normalized
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

    companion object {
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 5
        const val DEFAULT_REPEAT_COUNT = 2
        const val MIN_TOLERANCE = 0f
        const val MAX_TOLERANCE = 15f
        const val DEFAULT_TOLERANCE = 3f

        private val VOICE_REPEAT_ENABLED = booleanPreferencesKey("voice_repeat_enabled")
        private val VOICE_REPEAT_COUNT = intPreferencesKey("voice_repeat_count")
        private val OVER_LIMIT_TOLERANCE = floatPreferencesKey("over_limit_tolerance")
        private val DOSING_MODE = stringPreferencesKey("dosing_mode")
        private val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val BEEP_MODE = stringPreferencesKey("beep_mode")
        private val BEEP_THRESHOLD_PERCENT = intPreferencesKey("beep_threshold_percent")
        private val BEEP_THRESHOLD_CONTINUOUS = booleanPreferencesKey("beep_threshold_continuous")
    }
}
