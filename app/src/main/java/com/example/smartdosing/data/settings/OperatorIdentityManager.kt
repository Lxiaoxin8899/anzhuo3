package com.example.smartdosing.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.operatorIdentityDataStore by preferencesDataStore(name = "operator_identity")

/**
 * 本机操作员身份占位管理器。
 *
 * 中文注释：这里先只做设备端本地会话占位，不实现真实账号密码或主机认证；
 * 后续主机端确认协议后，再把 operatorId / sessionId / authSource 映射到正式登录协议。
 */
class OperatorIdentityManager(context: Context) {

    private val dataStore = context.applicationContext.operatorIdentityDataStore

    val sessionFlow: Flow<OperatorSession> = dataStore.data.map { prefs ->
        OperatorSession(
            operatorId = prefs[KEY_OPERATOR_ID].orEmpty(),
            displayName = prefs[KEY_DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: DEFAULT_OPERATOR_NAME,
            role = prefs[KEY_ROLE]?.takeIf { it.isNotBlank() } ?: DEFAULT_ROLE,
            authSource = prefs[KEY_AUTH_SOURCE]?.takeIf { it.isNotBlank() } ?: AUTH_SOURCE_LOCAL_PLACEHOLDER,
            sessionId = prefs[KEY_SESSION_ID].orEmpty(),
            loginAt = prefs[KEY_LOGIN_AT] ?: 0L,
            protocolDraft = PROTOCOL_DRAFT
        )
    }

    suspend fun setLocalOperator(displayName: String, operatorId: String, role: String) {
        val cleanName = displayName.trim().ifBlank { DEFAULT_OPERATOR_NAME }
        val cleanOperatorId = operatorId.trim()
        val cleanRole = role.trim().ifBlank { DEFAULT_ROLE }
        dataStore.edit { prefs ->
            prefs[KEY_DISPLAY_NAME] = cleanName
            prefs[KEY_OPERATOR_ID] = cleanOperatorId
            prefs[KEY_ROLE] = cleanRole
            prefs[KEY_AUTH_SOURCE] = AUTH_SOURCE_LOCAL_PLACEHOLDER
            prefs[KEY_SESSION_ID] = "LOCAL-${UUID.randomUUID()}"
            prefs[KEY_LOGIN_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearLocalOperator() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        const val DEFAULT_OPERATOR_NAME = "本机操作员"
        const val DEFAULT_ROLE = "操作员"
        const val AUTH_SOURCE_LOCAL_PLACEHOLDER = "LOCAL_PLACEHOLDER"
        const val PROTOCOL_DRAFT = "待主机端确认"

        private val KEY_OPERATOR_ID = stringPreferencesKey("operator_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("operator_display_name")
        private val KEY_ROLE = stringPreferencesKey("operator_role")
        private val KEY_AUTH_SOURCE = stringPreferencesKey("auth_source")
        private val KEY_SESSION_ID = stringPreferencesKey("session_id")
        private val KEY_LOGIN_AT = longPreferencesKey("login_at")
    }
}

data class OperatorSession(
    val operatorId: String,
    val displayName: String,
    val role: String,
    val authSource: String,
    val sessionId: String,
    val loginAt: Long,
    val protocolDraft: String
) {
    val isPlaceholderLogin: Boolean
        get() = authSource == OperatorIdentityManager.AUTH_SOURCE_LOCAL_PLACEHOLDER && sessionId.isNotBlank()

    val displayLabel: String
        get() = if (operatorId.isBlank()) displayName else "$displayName（$operatorId）"

    companion object {
        val Default = OperatorSession(
            operatorId = "",
            displayName = OperatorIdentityManager.DEFAULT_OPERATOR_NAME,
            role = OperatorIdentityManager.DEFAULT_ROLE,
            authSource = OperatorIdentityManager.AUTH_SOURCE_LOCAL_PLACEHOLDER,
            sessionId = "",
            loginAt = 0L,
            protocolDraft = OperatorIdentityManager.PROTOCOL_DRAFT
        )
    }
}
