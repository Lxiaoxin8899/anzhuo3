package com.example.smartdosing.bluetooth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 蓝牙电子秤设置偏好管理器
 * 用于持久化蓝牙设备配置
 */
private val Context.bluetoothScaleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bluetooth_scale_preferences"
)

class BluetoothScalePreferencesManager(private val context: Context) {

    companion object {
        // 偏好键
        private val KEY_LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
        private val KEY_LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        private val KEY_DEVICE_ALIAS = stringPreferencesKey("device_alias") // 用户自定义别名
        private val KEY_IS_BOUND = booleanPreferencesKey("is_bound") // 是否已绑定
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_AUTO_CONFIRM_ON_STABLE = booleanPreferencesKey("auto_confirm_on_stable")
        private val KEY_AUTO_CONFIRM_DELAY_SECONDS = intPreferencesKey("auto_confirm_delay_seconds")
        private val KEY_AUTO_TARE_ON_CONFIRM = booleanPreferencesKey("auto_tare_on_confirm")
        private val KEY_BAUD_RATE = intPreferencesKey("baud_rate")
        private val KEY_DATA_BITS = intPreferencesKey("data_bits")
        private val KEY_STOP_BITS = intPreferencesKey("stop_bits")
        private val KEY_PARITY = intPreferencesKey("parity")
        private val KEY_PROTOCOL = stringPreferencesKey("protocol")
        private val KEY_DEMO_MODE_ENABLED = booleanPreferencesKey("demo_mode_enabled")
        private val KEY_DEMO_SCENARIO_INDEX = intPreferencesKey("demo_scenario_index")
        private val KEY_AUTO_CONFIRM_TOLERANCE_PERMILLE = intPreferencesKey("auto_confirm_tolerance_permille")

        // 默认值
        const val DEFAULT_BAUD_RATE = 9600
        const val DEFAULT_DATA_BITS = 8
        const val DEFAULT_STOP_BITS = 1
        const val DEFAULT_PARITY = 0 // 无校验
        const val DEFAULT_PROTOCOL = "ohaus"
        const val DEFAULT_AUTO_CONFIRM_DELAY_SECONDS = 10 // 默认稳定后等待10秒自动确认
        const val DEFAULT_AUTO_TARE_ON_CONFIRM = true // 默认确认后自动去皮
        const val DEFAULT_AUTO_CONFIRM_TOLERANCE_PERMILLE = 10 // 默认误差范围10‰（千分之十，即1%）

        // 可选波特率
        val BAUD_RATE_OPTIONS = listOf(2400, 4800, 9600, 19200, 38400, 57600, 115200)

        // 可选自动确认等待时间（秒）
        val AUTO_CONFIRM_DELAY_OPTIONS = listOf(3, 5, 8, 10, 15, 20, 30)

        // 可选误差范围（千分比‰）- 精密投料需要更精确的控制
        // 1‰=0.1%, 5‰=0.5%, 10‰=1%, 20‰=2%, 50‰=5%
        val AUTO_CONFIRM_TOLERANCE_OPTIONS = listOf(1, 2, 3, 5, 8, 10, 15, 20, 30, 50)
    }

    /**
     * 蓝牙设置状态
     */
    data class BluetoothScalePreferencesState(
        val lastDeviceMac: String? = null,
        val lastDeviceName: String? = null,
        val deviceAlias: String? = null, // 用户自定义别名，如"1号秤"
        val isBound: Boolean = false, // 是否已绑定设备（绑定后只连接此设备）
        val autoConnect: Boolean = true,
        val autoConfirmOnStable: Boolean = false,
        val autoConfirmDelaySeconds: Int = DEFAULT_AUTO_CONFIRM_DELAY_SECONDS,
        val autoTareOnConfirm: Boolean = DEFAULT_AUTO_TARE_ON_CONFIRM,
        val baudRate: Int = DEFAULT_BAUD_RATE,
        val dataBits: Int = DEFAULT_DATA_BITS,
        val stopBits: Int = DEFAULT_STOP_BITS,
        val parity: Int = DEFAULT_PARITY,
        val protocol: String = DEFAULT_PROTOCOL,
        val demoModeEnabled: Boolean = false, // 演示模式开关
        val demoScenarioIndex: Int = 0, // 演示场景索引
        val autoConfirmTolerancePermille: Int = DEFAULT_AUTO_CONFIRM_TOLERANCE_PERMILLE // 自动确认误差范围（千分比‰）
    ) {
        /**
         * 是否有已绑定的设备
         */
        fun hasBoundDevice(): Boolean = isBound && lastDeviceMac != null

        /**
         * 获取设备显示名称（优先使用别名）
         */
        fun getDeviceDisplayName(): String? {
            return deviceAlias?.takeIf { it.isNotBlank() }
                ?: lastDeviceName?.let { name ->
                    val macSuffix = lastDeviceMac?.takeLast(5)?.replace(":", "") ?: ""
                    if (macSuffix.isNotEmpty()) "$name ($macSuffix)" else name
                }
        }

        /**
         * 获取绑定状态描述
         */
        fun getBindingStatusText(): String {
            return when {
                !isBound -> "未绑定"
                lastDeviceMac == null -> "未绑定"
                else -> "已绑定"
            }
        }
    }

    /**
     * 偏好流
     */
    val preferencesFlow: Flow<BluetoothScalePreferencesState> = context.bluetoothScaleDataStore.data
        .map { preferences ->
            BluetoothScalePreferencesState(
                lastDeviceMac = preferences[KEY_LAST_DEVICE_MAC],
                lastDeviceName = preferences[KEY_LAST_DEVICE_NAME],
                deviceAlias = preferences[KEY_DEVICE_ALIAS],
                isBound = preferences[KEY_IS_BOUND] ?: false,
                autoConnect = preferences[KEY_AUTO_CONNECT] ?: true,
                autoConfirmOnStable = preferences[KEY_AUTO_CONFIRM_ON_STABLE] ?: false,
                autoConfirmDelaySeconds = preferences[KEY_AUTO_CONFIRM_DELAY_SECONDS] ?: DEFAULT_AUTO_CONFIRM_DELAY_SECONDS,
                autoTareOnConfirm = preferences[KEY_AUTO_TARE_ON_CONFIRM] ?: DEFAULT_AUTO_TARE_ON_CONFIRM,
                baudRate = preferences[KEY_BAUD_RATE] ?: DEFAULT_BAUD_RATE,
                dataBits = preferences[KEY_DATA_BITS] ?: DEFAULT_DATA_BITS,
                stopBits = preferences[KEY_STOP_BITS] ?: DEFAULT_STOP_BITS,
                parity = preferences[KEY_PARITY] ?: DEFAULT_PARITY,
                protocol = preferences[KEY_PROTOCOL] ?: DEFAULT_PROTOCOL,
                demoModeEnabled = preferences[KEY_DEMO_MODE_ENABLED] ?: false,
                demoScenarioIndex = preferences[KEY_DEMO_SCENARIO_INDEX] ?: 0,
                autoConfirmTolerancePermille = preferences[KEY_AUTO_CONFIRM_TOLERANCE_PERMILLE] ?: DEFAULT_AUTO_CONFIRM_TOLERANCE_PERMILLE
            )
        }

    /**
     * 保存上次连接的设备（不绑定）
     */
    suspend fun saveLastDevice(mac: String, name: String) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_LAST_DEVICE_MAC] = mac
            preferences[KEY_LAST_DEVICE_NAME] = name
        }
    }

    /**
     * 绑定设备（绑定后此安卓设备只会连接这个秤）
     * @param mac 设备 MAC 地址
     * @param name 设备名称
     * @param alias 可选的设备别名
     */
    suspend fun bindDevice(mac: String, name: String, alias: String? = null) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_LAST_DEVICE_MAC] = mac
            preferences[KEY_LAST_DEVICE_NAME] = name
            preferences[KEY_IS_BOUND] = true
            if (!alias.isNullOrBlank()) {
                preferences[KEY_DEVICE_ALIAS] = alias
            }
        }
    }

    /**
     * 解除绑定（解绑后可以连接其他设备）
     */
    suspend fun unbindDevice() {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_IS_BOUND] = false
            // 保留设备信息，只是解除绑定
        }
    }

    /**
     * 清除设备记录（完全清除，包括绑定状态）
     */
    suspend fun clearLastDevice() {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences.remove(KEY_LAST_DEVICE_MAC)
            preferences.remove(KEY_LAST_DEVICE_NAME)
            preferences.remove(KEY_DEVICE_ALIAS)
            preferences.remove(KEY_IS_BOUND)
        }
    }

    /**
     * 设置设备别名
     */
    suspend fun setDeviceAlias(alias: String?) {
        context.bluetoothScaleDataStore.edit { preferences ->
            if (alias.isNullOrBlank()) {
                preferences.remove(KEY_DEVICE_ALIAS)
            } else {
                preferences[KEY_DEVICE_ALIAS] = alias
            }
        }
    }

    /**
     * 设置自动连接
     */
    suspend fun setAutoConnect(enabled: Boolean) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_AUTO_CONNECT] = enabled
        }
    }

    /**
     * 设置稳定后自动确认
     */
    suspend fun setAutoConfirmOnStable(enabled: Boolean) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_AUTO_CONFIRM_ON_STABLE] = enabled
        }
    }

    /**
     * 设置自动确认等待时间（秒）
     */
    suspend fun setAutoConfirmDelaySeconds(seconds: Int) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_AUTO_CONFIRM_DELAY_SECONDS] = seconds
        }
    }

    /**
     * 设置确认后自动去皮
     */
    suspend fun setAutoTareOnConfirm(enabled: Boolean) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_AUTO_TARE_ON_CONFIRM] = enabled
        }
    }

    /**
     * 设置波特率
     */
    suspend fun setBaudRate(baudRate: Int) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_BAUD_RATE] = baudRate
        }
    }

    /**
     * 设置串口参数
     */
    suspend fun setSerialParams(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_BAUD_RATE] = baudRate
            preferences[KEY_DATA_BITS] = dataBits
            preferences[KEY_STOP_BITS] = stopBits
            preferences[KEY_PARITY] = parity
        }
    }

    /**
     * 设置数据协议
     */
    suspend fun setProtocol(protocol: String) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_PROTOCOL] = protocol
        }
    }

    /**
     * 设置演示模式
     */
    suspend fun setDemoModeEnabled(enabled: Boolean) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_DEMO_MODE_ENABLED] = enabled
        }
    }

    /**
     * 设置演示场景索引
     */
    suspend fun setDemoScenarioIndex(index: Int) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_DEMO_SCENARIO_INDEX] = index
        }
    }

    /**
     * 设置自动确认误差范围（千分比‰）
     */
    suspend fun setAutoConfirmTolerancePermille(permille: Int) {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences[KEY_AUTO_CONFIRM_TOLERANCE_PERMILLE] = permille
        }
    }

    /**
     * 重置为默认设置
     */
    suspend fun resetToDefaults() {
        context.bluetoothScaleDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
