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
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.adminPreferencesDataStore by preferencesDataStore(name = "admin_preferences")

/**
 * 管理员偏好设置管理器
 * 管理管理员密码（PBKDF2 带盐哈希存储）和受保护的设置项
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

    /** 验证密码并登录，旧版 SHA-256 哈希在成功登录后自动升级为 PBKDF2。 */
    suspend fun verifyAndLogin(password: String, storedHash: String): Boolean {
        val result = verifyPassword(password, storedHash)
        if (!result) {
            return false
        }

        if (isLegacySha256Hash(storedHash)) {
            dataStore.edit { prefs ->
                prefs[PASSWORD_HASH] = hashPassword(password)
            }
        }
        _isAdminLoggedIn.value = true
        return true
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
        _isAdminLoggedIn.value = true
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
        private const val HASH_SCHEME = "pbkdf2_sha256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16

        // 全局会话状态，多个 Screen 共享
        private val _isAdminLoggedIn = MutableStateFlow(false)
        val isAdminLoggedIn: StateFlow<Boolean> = _isAdminLoggedIn

        /** 生成 PBKDF2 带盐哈希记录。 */
        fun hashPassword(password: String): String {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS)
            val encoder = Base64.getEncoder()
            return listOf(
                HASH_SCHEME,
                PBKDF2_ITERATIONS.toString(),
                encoder.encodeToString(salt),
                encoder.encodeToString(hash)
            ).joinToString(":")
        }

        private fun verifyPassword(password: String, storedHash: String): Boolean {
            if (storedHash.isBlank()) {
                return false
            }
            return if (storedHash.startsWith("$HASH_SCHEME:")) {
                verifyPbkdf2(password, storedHash)
            } else if (isLegacySha256Hash(storedHash)) {
                MessageDigest.isEqual(
                    legacySha256(password).toByteArray(Charsets.UTF_8),
                    storedHash.toByteArray(Charsets.UTF_8)
                )
            } else {
                false
            }
        }

        private fun verifyPbkdf2(password: String, storedHash: String): Boolean {
            val parts = storedHash.split(":")
            if (parts.size != 4 || parts[0] != HASH_SCHEME) {
                return false
            }

            return runCatching {
                val iterations = parts[1].toInt()
                val decoder = Base64.getDecoder()
                val salt = decoder.decode(parts[2])
                val expected = decoder.decode(parts[3])
                val actual = pbkdf2(password, salt, iterations)
                MessageDigest.isEqual(expected, actual)
            }.getOrDefault(false)
        }

        private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH_BITS)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        }

        private fun isLegacySha256Hash(hash: String): Boolean {
            return hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }

        private fun legacySha256(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return bytes.toHexString()
        }

        private fun ByteArray.toHexString(): String {
            val table = "0123456789abcdef"
            return buildString(size * 2) {
                for (byte in this@toHexString) {
                    val value = byte.toInt() and 0xff
                    append(table[value ushr 4])
                    append(table[value and 0x0f])
                }
            }
        }
    }
}
