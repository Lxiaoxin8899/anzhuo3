package com.example.smartdosing.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.adminPreferencesDataStore by preferencesDataStore(name = "admin_preferences")

/**
 * 管理员偏好设置管理器
 * 管理管理员密码（SHA-256哈希存储）和受保护的设置项
 * 登录状态为会话级别，应用重启后自动重置
 */
class AdminPreferencesManager(context: Context) {

    private val dataStore = context.applicationContext.adminPreferencesDataStore

    /**
     * 受管理员保护的设置状态
     */
    data class AdminSettingsState(
        val passwordHash: String = "",            // 空字符串表示未设置密码
        val manualInputEnabled: Boolean = true    // 蓝牙模式下是否允许手动输入
    )

    val settingsFlow: Flow<AdminSettingsState> = dataStore.data.map { prefs ->
        AdminSettingsState(
            passwordHash = prefs[PASSWORD_HASH] ?: "",
            manualInputEnabled = prefs[MANUAL_INPUT_ENABLED] ?: true
        )
    }

    /** 验证密码并登录 */
    fun verifyAndLogin(password: String, storedHash: String): Boolean {
        val result = hashPassword(password) == storedHash
        if (result) _isAdminLoggedIn.value = true
        return result
    }

    /** 登出管理员 */
    fun logout() {
        _isAdminLoggedIn.value = false
    }

    /** 设置/修改管理员密码 */
    suspend fun setPassword(password: String) {
        dataStore.edit { prefs ->
            prefs[PASSWORD_HASH] = hashPassword(password)
        }
    }

    /** 设置是否允许手动输入 */
    suspend fun setManualInputEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[MANUAL_INPUT_ENABLED] = enabled
        }
    }

    companion object {
        private val PASSWORD_HASH = stringPreferencesKey("admin_password_hash")
        private val MANUAL_INPUT_ENABLED = booleanPreferencesKey("admin_manual_input_enabled")

        // 全局会话状态，多个 Screen 共享
        private val _isAdminLoggedIn = MutableStateFlow(false)
        val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn

        /** SHA-256 哈希 */
        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
