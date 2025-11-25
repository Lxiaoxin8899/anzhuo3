package com.example.smartdosing.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dosingPreferencesDataStore by preferencesDataStore(name = "dosing_preferences")

data class DosingPreferencesState(
    val voiceRepeatEnabled: Boolean = false,
    val voiceRepeatCount: Int = 1,
    val overLimitTolerancePercent: Float = 3f
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
            overLimitTolerancePercent = prefs[OVER_LIMIT_TOLERANCE] ?: DEFAULT_TOLERANCE
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
    }
}
