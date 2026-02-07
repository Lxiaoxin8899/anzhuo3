package com.example.smartdosing.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import cn.wch.ch9140lib.CH9140BluetoothManager
import cn.wch.ch9140lib.callback.ConnectStatus
import cn.wch.ch9140lib.callback.EnumResult
import cn.wch.ch9140lib.exception.CH9140LibException
import androidx.core.content.ContextCompat
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.bluetooth.model.ScaleDevice
import com.example.smartdosing.bluetooth.model.WeightData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothScaleManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothScaleManager"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentWeight = MutableStateFlow<WeightData?>(null)
    val currentWeight: StateFlow<WeightData?> = _currentWeight.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScaleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScaleDevice>> = _scannedDevices.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var connectedMac: String? = null
    private var pendingConnectMac: String? = null
    private var retryCount = 0
    private val parser = OhausDataParser()
    private val handler = Handler(Looper.getMainLooper())
    private val deviceMap = mutableMapOf<String, ScaleDevice>()

    private val enumResult = object : EnumResult {
        override fun onResult(device: BluetoothDevice?, rssi: Int, broadcastRecord: ByteArray?) {
            device?.let {
                try {
                    val deviceName = it.name ?: "Unknown"
                    val scaleDevice = ScaleDevice(
                        name = deviceName,
                        mac = it.address,
                        rssi = rssi,
                        lastSeen = System.currentTimeMillis()
                    )
                    deviceMap[it.address] = scaleDevice
                    // 排序：优先显示可能是 CH9140 的设备，然后按信号强度排序
                    _scannedDevices.value = deviceMap.values
                        .sortedWith(compareByDescending<ScaleDevice> { it.isLikelyCH9140() }
                            .thenByDescending { it.rssi })
                        .toList()
                    Log.d(TAG, "发现设备: $deviceName (${it.address}), RSSI: $rssi, 可能是CH9140: ${scaleDevice.isLikelyCH9140()}")
                } catch (e: SecurityException) {
                    Log.e(TAG, "获取设备名称需要 BLUETOOTH_CONNECT 权限", e)
                }
            }
        }
    }

    private val connectCallback = object : ConnectStatus {
        override fun onSerialReadData(data: ByteArray?) {
            data?.let {
                Log.d(TAG, "收到数据: ${String(it, Charsets.UTF_8).trim()}")
                parser.parse(it)?.let { weight ->
                    Log.d(TAG, "解析重量: ${weight.value} ${weight.unit}, 稳定: ${weight.isStable}")
                    _currentWeight.value = weight
                }
            }
        }

        override fun OnConnectSuccess(mac: String?) {
            mac?.let {
                Log.i(TAG, "连接成功: $it")
                connectedMac = it
                pendingConnectMac = null
                retryCount = 0
                _connectedDeviceName.value = deviceMap[it]?.name ?: it
                _connectionState.value = ConnectionState.CONNECTED
                _errorMessage.value = null
                // 延迟配置串口，给 BLE 操作留出时间
                handler.postDelayed({
                    configureForOhaus()
                }, 500)
            }
        }

        override fun OnConnecting() {
            Log.d(TAG, "正在连接...")
            _connectionState.value = ConnectionState.CONNECTING
        }

        override fun OnDisconnect(mac: String?, status: Int) {
            Log.i(TAG, "设备断开: $mac, status: $status")
            _connectionState.value = ConnectionState.DISCONNECTED
            _currentWeight.value = null
            _connectedDeviceName.value = null
            connectedMac = null
            parser.clear()
        }

        override fun OnConnectTimeout(mac: String?) {
            Log.w(TAG, "连接超时: $mac")
            handleConnectFailure(mac, "连接超时")
        }

        override fun OnError(t: Throwable?) {
            Log.e(TAG, "连接错误", t)
            handleConnectFailure(pendingConnectMac, t?.message ?: "连接错误")
        }

        override fun onInvalidDevice(mac: String?) {
            Log.e(TAG, "无效设备: $mac")
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "无效的设备"
            retryCount = 0
            pendingConnectMac = null
        }
    }

    private fun hasScanPermissionForSdk(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleConnectFailure(mac: String?, errorMsg: String) {
        if (mac != null && retryCount < MAX_RETRY_COUNT) {
            retryCount++
            Log.w(TAG, "连接失败，第 $retryCount 次重试: $errorMsg")
            _errorMessage.value = "正在重试连接 ($retryCount/$MAX_RETRY_COUNT)..."
            handler.postDelayed({
                connectInternal(mac)
            }, RETRY_DELAY_MS)
        } else {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = errorMsg
            retryCount = 0
            pendingConnectMac = null
        }
    }

    fun startScan() {
        // 显式权限判断：让 lint 能识别，同时避免运行时因权限被拒而崩溃
        if (!hasScanPermissionForSdk()) {
            Log.e(TAG, "缺少扫描权限")
            _errorMessage.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "请授予蓝牙扫描权限"
            } else {
                "请授予定位权限以进行蓝牙扫描"
            }
            return
        }
        try {
            Log.d(TAG, "开始扫描设备")
            _connectionState.value = ConnectionState.SCANNING
            deviceMap.clear()
            _scannedDevices.value = emptyList()
            CH9140BluetoothManager.getInstance().startEnumDevices(enumResult)
        } catch (e: SecurityException) {
            // 部分 ROM/设备会在权限被用户拒绝时抛出 SecurityException
            Log.e(TAG, "扫描需要权限", e)
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = "扫描需要权限"
        } catch (e: CH9140LibException) {
            Log.e(TAG, "扫描失败", e)
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = e.message
        }
    }

    fun stopScan() {
        try {
            Log.d(TAG, "停止扫描")
            CH9140BluetoothManager.getInstance().stopEnumDevices()
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        } catch (e: CH9140LibException) {
            // 忽略
        }
    }

    fun connect(mac: String) {
        if (!BluetoothPermissionHelper.hasAllPermissions(context)) {
            Log.e(TAG, "缺少蓝牙权限，无法连接")
            _errorMessage.value = "请授予蓝牙权限"
            _connectionState.value = ConnectionState.ERROR
            return
        }
        stopScan()
        retryCount = 0
        pendingConnectMac = mac
        connectInternal(mac)
    }

    private fun connectInternal(mac: String) {
        try {
            Log.i(TAG, "尝试连接设备: $mac")
            _connectionState.value = ConnectionState.CONNECTING
            // 先关闭可能存在的旧连接
            try {
                CH9140BluetoothManager.getInstance().closeDevice(mac, true)
            } catch (e: Exception) {
                // 忽略
            }
            // 等待一小段时间再连接
            handler.postDelayed({
                try {
                    CH9140BluetoothManager.getInstance().openDevice(mac, 15000, connectCallback)
                } catch (e: CH9140LibException) {
                    Log.e(TAG, "打开设备失败", e)
                    handleConnectFailure(mac, e.message ?: "打开设备失败")
                }
            }, 300)
        } catch (e: CH9140LibException) {
            Log.e(TAG, "连接失败", e)
            handleConnectFailure(mac, e.message ?: "连接失败")
        }
    }

    fun disconnect() {
        Log.d(TAG, "断开连接")
        handler.removeCallbacksAndMessages(null)
        pendingConnectMac = null
        retryCount = 0
        connectedMac?.let { mac ->
            try {
                CH9140BluetoothManager.getInstance().closeDevice(mac, false)
            } catch (e: CH9140LibException) {
                Log.e(TAG, "断开连接异常", e)
            }
        }
    }

    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    private fun configureForOhaus() {
        try {
            Log.d(TAG, "配置串口参数: 9600-8-N-1")
            // 奥豪斯默认: 9600波特率, 8数据位, 1停止位, 无校验
            CH9140BluetoothManager.getInstance().setSerialBaud(9600, 8, 1, 0)
            // 配置完成后发送连续打印命令
            startContinuousPrint()
        } catch (e: CH9140LibException) {
            Log.e(TAG, "配置串口失败", e)
            _errorMessage.value = "配置串口失败: ${e.message}"
        }
    }

    private fun startContinuousPrint() {
        // 发送连续打印命令
        handler.postDelayed({
            Log.d(TAG, "发送 CP 命令启动连续打印")
            sendCommand("CP")
        }, 300)
    }

    fun sendCommand(cmd: String) {
        try {
            val data = "$cmd\r\n".toByteArray(Charsets.UTF_8)
            Log.d(TAG, "发送命令: $cmd")
            CH9140BluetoothManager.getInstance().write(data, data.size)
        } catch (e: Exception) {
            Log.e(TAG, "发送命令失败: $cmd", e)
            _errorMessage.value = "发送命令失败: ${e.message}"
        }
    }

    // 去皮
    fun tare() {
        Log.d(TAG, "执行去皮")
        sendCommand("T")
    }

    // 置零
    fun zero() {
        Log.d(TAG, "执行置零")
        sendCommand("Z")
    }

    // 请求单次读数
    fun requestWeight() {
        Log.d(TAG, "请求单次读数")
        sendCommand("IP")
    }

    // 停止连续打印
    fun stopContinuousPrint() {
        Log.d(TAG, "停止连续打印")
        sendCommand("SCP")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun destroy() {
        Log.d(TAG, "销毁 BluetoothScaleManager")
        handler.removeCallbacksAndMessages(null)
        disconnect()
        stopScan()
    }
}
