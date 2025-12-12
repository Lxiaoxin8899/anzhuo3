package com.example.smartdosing.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.bluetooth.BluetoothPermissionHelper
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager
import com.example.smartdosing.bluetooth.BluetoothScalePreferencesManager.Companion.BAUD_RATE_OPTIONS
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.bluetooth.model.ScaleDevice
import com.example.smartdosing.ui.components.BluetoothDeviceSelectDialog
import kotlinx.coroutines.launch

/**
 * 蓝牙电子秤设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScaleSettingsScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as com.example.smartdosing.SmartDosingApplication

    // 使用全局管理器（确保整个应用共享同一个连接）
    val preferencesManager = application.bluetoothPreferencesManager
    val scaleManager = application.bluetoothScaleManager

    // 状态
    val preferencesState by preferencesManager.preferencesFlow.collectAsState(
        initial = BluetoothScalePreferencesManager.BluetoothScalePreferencesState()
    )
    val connectionState by scaleManager.connectionState.collectAsState()
    val connectedDeviceName by scaleManager.connectedDeviceName.collectAsState()
    val scannedDevices by scaleManager.scannedDevices.collectAsState()
    val errorMessage by scaleManager.errorMessage.collectAsState()

    // UI 状态
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showBaudRateDialog by remember { mutableStateOf(false) }
    var showProtocolDialog by remember { mutableStateOf(false) }
    var showAutoConfirmDelayDialog by remember { mutableStateOf(false) }
    var showAutoConfirmToleranceDialog by remember { mutableStateOf(false) }
    var headerVisible by remember { mutableStateOf(false) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showDeviceDialog = true
        } else {
            Toast.makeText(context, "需要蓝牙权限才能搜索设备", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        headerVisible = true
    }

    // 注意：不在这里销毁 scaleManager，因为它是全局共享的
    // 页面退出时只停止扫描
    DisposableEffect(Unit) {
        onDispose {
            scaleManager.stopScan()
        }
    }

    // 设备选择对话框
    BluetoothDeviceSelectDialog(
        scaleManager = scaleManager,
        isVisible = showDeviceDialog,
        onDismiss = { showDeviceDialog = false },
        onDeviceSelected = { device ->
            showDeviceDialog = false
            scaleManager.connect(device.mac)
            // 绑定设备（而不是仅保存）
            scope.launch {
                preferencesManager.bindDevice(device.mac, device.name)
                Toast.makeText(context, "已绑定设备: ${device.name}", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // 波特率选择对话框
    if (showBaudRateDialog) {
        AlertDialog(
            onDismissRequest = { showBaudRateDialog = false },
            title = { Text("选择波特率") },
            text = {
                Column {
                    BAUD_RATE_OPTIONS.forEach { baudRate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        preferencesManager.setBaudRate(baudRate)
                                    }
                                    showBaudRateDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferencesState.baudRate == baudRate,
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setBaudRate(baudRate)
                                    }
                                    showBaudRateDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$baudRate bps",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBaudRateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 协议选择对话框
    if (showProtocolDialog) {
        val protocols = listOf(
            "ohaus" to "奥豪斯 (OHAUS)",
            "generic" to "通用协议",
            "mettler" to "梅特勒 (Mettler Toledo)"
        )
        AlertDialog(
            onDismissRequest = { showProtocolDialog = false },
            title = { Text("选择数据协议") },
            text = {
                Column {
                    protocols.forEach { (key, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        preferencesManager.setProtocol(key)
                                    }
                                    showProtocolDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferencesState.protocol == key,
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setProtocol(key)
                                    }
                                    showProtocolDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProtocolDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 自动确认等待时间选择对话框
    if (showAutoConfirmDelayDialog) {
        AlertDialog(
            onDismissRequest = { showAutoConfirmDelayDialog = false },
            title = { Text("选择等待时间") },
            text = {
                Column {
                    Text(
                        text = "读数稳定后等待指定时间再自动确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    BluetoothScalePreferencesManager.AUTO_CONFIRM_DELAY_OPTIONS.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        preferencesManager.setAutoConfirmDelaySeconds(seconds)
                                    }
                                    showAutoConfirmDelayDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferencesState.autoConfirmDelaySeconds == seconds,
                                onClick = {
                                    scope.launch {
                                        preferencesManager.setAutoConfirmDelaySeconds(seconds)
                                    }
                                    showAutoConfirmDelayDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$seconds 秒",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoConfirmDelayDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 自动确认误差范围选择对话框（千分比‰）- 支持自定义输入
    if (showAutoConfirmToleranceDialog) {
        var customInputValue by remember { mutableStateOf("") }
        var showCustomInput by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAutoConfirmToleranceDialog = false },
            title = { Text("设置误差范围") },
            text = {
                Column {
                    Text(
                        text = "只有当实际重量在目标重量的误差范围内时，才会开始自动确认计时。使用千分比(‰)以满足精密投料需求。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 常用预设选项
                    Text(
                        text = "常用预设",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 使用 FlowRow 风格的布局显示预设选项
                    val presets = listOf(1, 2, 5, 10, 20, 50)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.take(3).forEach { permille ->
                            FilterChip(
                                selected = preferencesState.autoConfirmTolerancePermille == permille && !showCustomInput,
                                onClick = {
                                    showCustomInput = false
                                    scope.launch {
                                        preferencesManager.setAutoConfirmTolerancePermille(permille)
                                    }
                                    showAutoConfirmToleranceDialog = false
                                },
                                label = { Text("±${permille}‰") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.drop(3).forEach { permille ->
                            FilterChip(
                                selected = preferencesState.autoConfirmTolerancePermille == permille && !showCustomInput,
                                onClick = {
                                    showCustomInput = false
                                    scope.launch {
                                        preferencesManager.setAutoConfirmTolerancePermille(permille)
                                    }
                                    showAutoConfirmToleranceDialog = false
                                },
                                label = { Text("±${permille}‰") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // 自定义输入区域
                    Text(
                        text = "自定义输入",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { newValue ->
                                // 只允许输入数字
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    customInputValue = newValue
                                    inputError = null
                                    showCustomInput = newValue.isNotEmpty()
                                }
                            },
                            label = { Text("误差值") },
                            placeholder = { Text("如: 3") },
                            suffix = { Text("‰") },
                            singleLine = true,
                            isError = inputError != null,
                            supportingText = inputError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                val value = customInputValue.toIntOrNull()
                                when {
                                    value == null -> inputError = "请输入有效数字"
                                    value < 1 -> inputError = "最小值为1‰"
                                    value > 500 -> inputError = "最大值为500‰"
                                    else -> {
                                        scope.launch {
                                            preferencesManager.setAutoConfirmTolerancePermille(value)
                                        }
                                        showAutoConfirmToleranceDialog = false
                                    }
                                }
                            },
                            enabled = customInputValue.isNotEmpty()
                        ) {
                            Text("确定")
                        }
                    }

                    // 当前值提示
                    Spacer(modifier = Modifier.height(12.dp))
                    val currentPermille = preferencesState.autoConfirmTolerancePermille
                    val currentPercent = currentPermille / 10.0
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "当前设置: ±${currentPermille}‰ (相当于 ±${String.format("%.1f", currentPercent)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoConfirmToleranceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("蓝牙电子秤设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 连接状态卡片
            item {
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { -20 })
                ) {
                    ConnectionStatusCard(
                        connectionState = connectionState,
                        deviceName = connectedDeviceName ?: preferencesState.lastDeviceName,
                        errorMessage = errorMessage,
                        onConnectClick = {
                            if (BluetoothPermissionHelper.hasAllPermissions(context)) {
                                // 如果有保存的设备，尝试自动连接
                                if (preferencesState.lastDeviceMac != null && preferencesState.autoConnect) {
                                    scaleManager.connect(preferencesState.lastDeviceMac!!)
                                } else {
                                    showDeviceDialog = true
                                }
                            } else {
                                permissionLauncher.launch(
                                    BluetoothPermissionHelper.getRequiredPermissions()
                                )
                            }
                        },
                        onDisconnectClick = {
                            scaleManager.disconnect()
                        },
                        onScanClick = {
                            if (BluetoothPermissionHelper.hasAllPermissions(context)) {
                                showDeviceDialog = true
                            } else {
                                permissionLauncher.launch(
                                    BluetoothPermissionHelper.getRequiredPermissions()
                                )
                            }
                        }
                    )
                }
            }

            // 设备绑定分区
            item {
                SettingsCard(
                    title = "设备绑定",
                    icon = Icons.Outlined.Bluetooth
                ) {
                    // 绑定状态提示
                    if (preferencesState.hasBoundDevice()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已绑定设备",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "此平板只会连接绑定的秤，不会误连其他设备",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF558B2F)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "未绑定设备",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE65100)
                                )
                                Text(
                                    text = "建议绑定一台秤，避免多设备环境下连错",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF57C00)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 绑定的设备
                    SettingsRow(
                        icon = Icons.Outlined.PhonelinkSetup,
                        title = "绑定设备",
                        subtitle = preferencesState.getDeviceDisplayName() ?: "点击扫描并绑定设备",
                        onClick = { showDeviceDialog = true }
                    )

                    // 设备别名（仅在已绑定设备时显示）
                    if (preferencesState.lastDeviceMac != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingsEditableRow(
                            icon = Icons.Outlined.Edit,
                            title = "设备别名",
                            value = preferencesState.deviceAlias ?: "",
                            placeholder = "如：1号秤、配料间秤",
                            onValueChange = { newAlias ->
                                scope.launch {
                                    preferencesManager.setDeviceAlias(newAlias.takeIf { it.isNotBlank() })
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 解除绑定
                        if (preferencesState.isBound) {
                            SettingsActionRow(
                                icon = Icons.Outlined.LinkOff,
                                title = "解除绑定",
                                subtitle = "解绑后可以连接其他设备",
                                isDestructive = false,
                                onClick = {
                                    scope.launch {
                                        preferencesManager.unbindDevice()
                                        Toast.makeText(context, "已解除绑定", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // 清除设备记录
                        SettingsActionRow(
                            icon = Icons.Outlined.Delete,
                            title = "清除设备记录",
                            subtitle = "完全清除设备信息和绑定",
                            isDestructive = true,
                            onClick = {
                                scope.launch {
                                    preferencesManager.clearLastDevice()
                                    Toast.makeText(context, "设备记录已清除", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // 连接设置分区
            item {
                SettingsCard(
                    title = "连接设置",
                    icon = Icons.Outlined.Settings
                ) {
                    // 自动连接
                    SettingsToggleRow(
                        icon = Icons.Outlined.BluetoothConnected,
                        title = "自动连接",
                        subtitle = if (preferencesState.hasBoundDevice())
                            "启动应用时自动连接绑定的设备"
                        else
                            "启动应用时自动连接上次设备",
                        checked = preferencesState.autoConnect,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setAutoConnect(enabled) }
                        }
                    )
                }
            }

            // 串口设置分区
            item {
                SettingsCard(
                    title = "串口设置",
                    icon = Icons.Outlined.Settings
                ) {
                    // 波特率
                    SettingsRow(
                        icon = Icons.Outlined.Speed,
                        title = "波特率",
                        subtitle = "${preferencesState.baudRate} bps",
                        onClick = { showBaudRateDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 数据协议
                    SettingsRow(
                        icon = Icons.Outlined.Code,
                        title = "数据协议",
                        subtitle = when (preferencesState.protocol) {
                            "ohaus" -> "奥豪斯 (OHAUS)"
                            "mettler" -> "梅特勒 (Mettler Toledo)"
                            else -> "通用协议"
                        },
                        onClick = { showProtocolDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 串口参数说明
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "当前配置: ${preferencesState.baudRate}-${preferencesState.dataBits}-N-${preferencesState.stopBits}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 投料设置分区
            item {
                SettingsCard(
                    title = "投料设置",
                    icon = Icons.Outlined.Scale
                ) {
                    // 稳定后自动确认
                    SettingsToggleRow(
                        icon = Icons.Outlined.CheckCircle,
                        title = "稳定后自动确认",
                        subtitle = "读数稳定后自动确认并进入下一个物料",
                        checked = preferencesState.autoConfirmOnStable,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setAutoConfirmOnStable(enabled) }
                        }
                    )

                    // 仅在启用自动确认时显示等待时间和误差范围设置
                    if (preferencesState.autoConfirmOnStable) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 自动确认误差范围（千分比）
                        SettingsRow(
                            icon = Icons.Outlined.Tune,
                            title = "误差范围",
                            subtitle = "±${preferencesState.autoConfirmTolerancePermille}‰（重量需在此范围内才开始计时）",
                            onClick = { showAutoConfirmToleranceDialog = true }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 自动确认等待时间
                        SettingsRow(
                            icon = Icons.Outlined.Timer,
                            title = "等待时间",
                            subtitle = "${preferencesState.autoConfirmDelaySeconds} 秒（稳定后等待）",
                            onClick = { showAutoConfirmDelayDialog = true }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 确认后自动去皮
                    SettingsToggleRow(
                        icon = Icons.Outlined.Refresh,
                        title = "确认后自动去皮",
                        subtitle = "确认当前物料后自动执行去皮，准备下一个物料",
                        checked = preferencesState.autoTareOnConfirm,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setAutoTareOnConfirm(enabled) }
                        }
                    )
                }
            }

            // 演示模式分区
            item {
                val demoManager = application.demoModeManager
                val demoActive by demoManager.isActive.collectAsState()
                val demoWeight by demoManager.currentWeight.collectAsState()

                SettingsCard(
                    title = "演示模式",
                    icon = Icons.Outlined.PlayCircle
                ) {
                    // 演示模式说明
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (preferencesState.demoModeEnabled) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (preferencesState.demoModeEnabled) Icons.Outlined.Info else Icons.Outlined.Info,
                            contentDescription = null,
                            tint = if (preferencesState.demoModeEnabled) Color(0xFF1976D2) else Color(0xFF757575),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (preferencesState.demoModeEnabled) "演示模式已启用" else "演示模式",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (preferencesState.demoModeEnabled) Color(0xFF1565C0) else Color(0xFF424242)
                            )
                            Text(
                                text = "无需真实蓝牙秤，模拟投料过程用于演示和培训",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (preferencesState.demoModeEnabled) Color(0xFF1976D2) else Color(0xFF757575)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 演示模式开关
                    SettingsToggleRow(
                        icon = Icons.Outlined.PlayCircle,
                        title = "启用演示模式",
                        subtitle = if (preferencesState.demoModeEnabled) "投料界面将使用模拟数据" else "关闭后使用真实蓝牙秤",
                        checked = preferencesState.demoModeEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesManager.setDemoModeEnabled(enabled)
                                if (enabled) {
                                    demoManager.startDemo()
                                    Toast.makeText(context, "演示模式已启用", Toast.LENGTH_SHORT).show()
                                } else {
                                    demoManager.stopDemo()
                                    Toast.makeText(context, "演示模式已关闭", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    // 演示场景选择（仅在启用时显示）
                    if (preferencesState.demoModeEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        val scenarios = com.example.smartdosing.bluetooth.DemoScenarioConfig.PRESETS
                        val currentScenario = scenarios.getOrNull(preferencesState.demoScenarioIndex) ?: scenarios[0]

                        SettingsRow(
                            icon = Icons.Outlined.Tune,
                            title = "演示场景",
                            subtitle = "${currentScenario.name} - ${currentScenario.description}",
                            onClick = {
                                // 循环切换场景
                                val nextIndex = (preferencesState.demoScenarioIndex + 1) % scenarios.size
                                scope.launch {
                                    preferencesManager.setDemoScenarioIndex(nextIndex)
                                    val nextScenario = scenarios[nextIndex]
                                    demoManager.scenario = nextScenario.scenario
                                    demoManager.speedMs = nextScenario.speedMs
                                    Toast.makeText(context, "已切换到: ${nextScenario.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // 演示状态显示
                        if (demoActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color(0xFFE8F5E9),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "当前模拟重量",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = demoWeight?.getFullDisplay() ?: "0.000 g",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { demoManager.simulateTare() }
                                    ) {
                                        Text("去皮")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            demoManager.simulateWeighing(50.0 + Math.random() * 50)
                                        }
                                    ) {
                                        Text("模拟投料")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 测试分区
            item {
                SettingsCard(
                    title = "测试与调试",
                    icon = Icons.Outlined.BugReport
                ) {
                    // 发送测试命令
                    if (connectionState == ConnectionState.CONNECTED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { scaleManager.tare() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("去皮 (T)")
                            }
                            OutlinedButton(
                                onClick = { scaleManager.zero() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("置零 (Z)")
                            }
                            OutlinedButton(
                                onClick = { scaleManager.requestWeight() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("读数 (IP)")
                            }
                        }
                    } else {
                        Text(
                            text = "请先连接蓝牙电子秤",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 重置设置
                    SettingsActionRow(
                        icon = Icons.Outlined.RestartAlt,
                        title = "重置为默认设置",
                        subtitle = "恢复所有蓝牙设置到默认值",
                        isDestructive = true,
                        onClick = {
                            scope.launch {
                                preferencesManager.resetToDefaults()
                                Toast.makeText(context, "设置已重置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 连接状态卡片
 */
@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    deviceName: String?,
    errorMessage: String?,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFFE8F5E9)
                ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFF3E0)
                ConnectionState.ERROR -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 状态图标
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                    ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                                    ConnectionState.ERROR -> Color(0xFFF44336)
                                    else -> Color(0xFFBDBDBD)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                ConnectionState.CONNECTING, ConnectionState.SCANNING -> Icons.Default.BluetoothSearching
                                ConnectionState.ERROR -> Icons.Default.BluetoothDisabled
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "已连接"
                                ConnectionState.CONNECTING -> "正在连接..."
                                ConnectionState.SCANNING -> "正在扫描..."
                                ConnectionState.ERROR -> "连接失败"
                                ConnectionState.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (deviceName != null) {
                            Text(
                                text = deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }

                // 操作按钮
                when (connectionState) {
                    ConnectionState.CONNECTED -> {
                        OutlinedButton(
                            onClick = onDisconnectClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Text("断开")
                        }
                    }
                    ConnectionState.CONNECTING, ConnectionState.SCANNING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onScanClick) {
                                Text("扫描")
                            }
                            Button(onClick = onConnectClick) {
                                Text("连接")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 设置卡片容器
 */
@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * 设置行 - 可点击
 */
@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 设置行 - 开关
 */
@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 设置行 - 操作按钮
 */
@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 设置行 - 可编辑文本
 */
@Composable
private fun SettingsEditableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = newValue
                    onValueChange(newValue)
                },
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
