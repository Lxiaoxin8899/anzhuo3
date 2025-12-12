package com.example.smartdosing.ui.components

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.bluetooth.BluetoothPermissionHelper
import com.example.smartdosing.bluetooth.BluetoothScaleManager
import com.example.smartdosing.bluetooth.model.ConnectionState
import com.example.smartdosing.bluetooth.model.ScaleDevice
import com.example.smartdosing.bluetooth.model.WeightData

/**
 * 蓝牙电子秤状态栏组件
 * 显示连接状态、设备名称、当前重量
 */
@Composable
fun BluetoothScaleStatusBar(
    scaleManager: BluetoothScaleManager,
    modifier: Modifier = Modifier,
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {}
) {
    val connectionState by scaleManager.connectionState.collectAsState()
    val deviceName by scaleManager.connectedDeviceName.collectAsState()
    val currentWeight by scaleManager.currentWeight.collectAsState()
    val errorMessage by scaleManager.errorMessage.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = when (connectionState) {
            ConnectionState.CONNECTED -> Color(0xFFE8F5E9)
            ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFF3E0)
            ConnectionState.ERROR -> Color(0xFFFFEBEE)
            else -> Color(0xFFF5F5F5)
        },
        border = BorderStroke(
            1.dp,
            when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                ConnectionState.ERROR -> Color(0xFFF44336)
                else -> Color(0xFFBDBDBD)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧 - 连接状态指示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态指示灯
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                                ConnectionState.ERROR -> Color(0xFFF44336)
                                else -> Color(0xFFBDBDBD)
                            }
                        )
                )

                Column {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "已连接"
                            ConnectionState.CONNECTING -> "连接中..."
                            ConnectionState.SCANNING -> "扫描中..."
                            ConnectionState.ERROR -> "连接错误"
                            ConnectionState.DISCONNECTED -> "未连接"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF2E7D32)
                            ConnectionState.ERROR -> Color(0xFFD32F2F)
                            else -> Color(0xFF616161)
                        }
                    )

                    if (deviceName != null) {
                        Text(
                            text = deviceName!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                            maxLines = 1
                        )
                    }
                }
            }

            // 中间 - 当前重量显示 (仅在已连接时显示)
            if (connectionState == ConnectionState.CONNECTED && currentWeight != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = currentWeight!!.getDisplayValue(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )
                        Text(
                            text = currentWeight!!.unit,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF757575)
                        )
                    }
                    // 稳定性指示
                    Text(
                        text = if (currentWeight!!.isStable) "稳定" else "不稳定",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentWeight!!.isStable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }

            // 右侧 - 操作按钮
            when (connectionState) {
                ConnectionState.CONNECTED -> {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = "断开",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("断开")
                    }
                }
                ConnectionState.CONNECTING, ConnectionState.SCANNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "连接",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("连接秤")
                    }
                }
            }
        }
    }
}

/**
 * 蓝牙设备选择对话框
 */
@Composable
fun BluetoothDeviceSelectDialog(
    scaleManager: BluetoothScaleManager,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onDeviceSelected: (ScaleDevice) -> Unit
) {
    val context = LocalContext.current
    val scannedDevices by scaleManager.scannedDevices.collectAsState()
    val connectionState by scaleManager.connectionState.collectAsState()

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            scaleManager.startScan()
        }
    }

    if (isVisible) {
        AlertDialog(
            onDismissRequest = {
                scaleManager.stopScan()
                onDismiss()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color(0xFF1976D2)
                    )
                    Text("选择蓝牙电子秤")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)
                ) {
                    // 扫描状态提示
                    if (connectionState == ConnectionState.SCANNING) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在扫描附近设备...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF757575)
                            )
                        }
                    }

                    // 设备列表
                    if (scannedDevices.isEmpty() && connectionState != ConnectionState.SCANNING) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color(0xFFBDBDBD)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "未发现设备\n请点击扫描按钮",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF9E9E9E),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(scannedDevices) { device ->
                                DeviceListItem(
                                    device = device,
                                    onClick = {
                                        scaleManager.stopScan()
                                        onDeviceSelected(device)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 扫描按钮
                    if (connectionState != ConnectionState.SCANNING) {
                        Button(
                            onClick = {
                                if (BluetoothPermissionHelper.hasAllPermissions(context)) {
                                    scaleManager.startScan()
                                } else {
                                    permissionLauncher.launch(
                                        BluetoothPermissionHelper.getRequiredPermissions()
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("扫描")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { scaleManager.stopScan() }
                        ) {
                            Text("停止")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scaleManager.stopScan()
                        onDismiss()
                    }
                ) {
                    Text("取消")
                }
            },
            modifier = Modifier.widthIn(min = 350.dp, max = 500.dp)
        )
    }

    // 首次显示时自动开始扫描
    LaunchedEffect(isVisible) {
        if (isVisible) {
            if (BluetoothPermissionHelper.hasAllPermissions(context)) {
                scaleManager.startScan()
            } else {
                permissionLauncher.launch(
                    BluetoothPermissionHelper.getRequiredPermissions()
                )
            }
        }
    }
}

/**
 * 设备列表项
 */
@Composable
private fun DeviceListItem(
    device: ScaleDevice,
    onClick: () -> Unit
) {
    val isLikelyCH9140 = device.isLikelyCH9140()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isLikelyCH9140) Color(0xFFE3F2FD) else Color(0xFFF5F5F5),
        border = if (isLikelyCH9140) BorderStroke(1.dp, Color(0xFF1976D2)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Scale,
                    contentDescription = null,
                    tint = if (isLikelyCH9140) Color(0xFF1976D2) else Color(0xFF757575),
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.getDisplayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (isLikelyCH9140) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1976D2)
                            ) {
                                Text(
                                    text = "推荐",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = device.mac,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF757575)
                    )
                }
            }

            // 信号强度指示
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = when {
                            device.rssi > -60 -> Color(0xFF4CAF50)
                            device.rssi > -80 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = device.getSignalStrength(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF757575)
                    )
                }
            }
        }
    }
}

/**
 * 带蓝牙功能的重量显示框
 * 当蓝牙已连接时显示蓝牙读取的重量，否则显示手动输入的重量
 */
@Composable
fun BluetoothWeightDisplayBox(
    modifier: Modifier = Modifier,
    manualWeight: String,
    bluetoothWeight: WeightData?,
    isBluetoothConnected: Boolean,
    onUseBluetoothWeight: () -> Unit = {}
) {
    val displayWeight = if (isBluetoothConnected && bluetoothWeight != null) {
        bluetoothWeight.getDisplayValue()
    } else {
        manualWeight.ifBlank { "0.0" }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isBluetoothConnected) Color(0xFFE3F2FD) else Color.White,
        border = BorderStroke(
            width = if (isBluetoothConnected) 2.dp else 1.dp,
            color = if (isBluetoothConnected) Color(0xFF1976D2) else Color(0xFFB0BEC5)
        ),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 数据来源标签
                if (isBluetoothConnected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (bluetoothWeight?.isStable == true) "蓝牙读取 · 稳定" else "蓝牙读取 · 等待稳定",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bluetoothWeight?.isStable == true) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 重量数值
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayWeight,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238),
                        textAlign = TextAlign.Center
                    )
                    if (isBluetoothConnected && bluetoothWeight != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bluetoothWeight.unit,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFF757575),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // 使用此重量按钮 (蓝牙模式下)
                if (isBluetoothConnected && bluetoothWeight != null && bluetoothWeight.isStable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onUseBluetoothWeight,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF1976D2)
                        )
                    ) {
                        Text("点击使用此重量", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * 蓝牙秤操作按钮组
 */
@Composable
fun BluetoothScaleActionButtons(
    scaleManager: BluetoothScaleManager,
    modifier: Modifier = Modifier
) {
    val connectionState by scaleManager.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    if (isConnected) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 去皮按钮
            OutlinedButton(
                onClick = { scaleManager.tare() },
                modifier = Modifier.weight(1f)
            ) {
                Text("去皮")
            }

            // 置零按钮
            OutlinedButton(
                onClick = { scaleManager.zero() },
                modifier = Modifier.weight(1f)
            ) {
                Text("置零")
            }
        }
    }
}

/**
 * 投料界面专用蓝牙状态栏
 * 紧凑设计，包含连接状态、当前重量、去皮/置零按钮
 */
@Composable
fun DosingBluetoothStatusBar(
    scaleManager: BluetoothScaleManager,
    deviceAlias: String? = null,
    modifier: Modifier = Modifier,
    onConnectClick: () -> Unit = {}
) {
    val connectionState by scaleManager.connectionState.collectAsState()
    val deviceName by scaleManager.connectedDeviceName.collectAsState()
    val currentWeight by scaleManager.currentWeight.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when (connectionState) {
            ConnectionState.CONNECTED -> Color(0xFFE8F5E9)
            ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFF3E0)
            ConnectionState.ERROR -> Color(0xFFFFEBEE)
            else -> Color(0xFFFAFAFA)
        },
        border = BorderStroke(
            1.dp,
            when (connectionState) {
                ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                ConnectionState.ERROR -> Color(0xFFF44336)
                else -> Color(0xFFE0E0E0)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 连接状态指示灯
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionState) {
                            ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFF9800)
                            ConnectionState.ERROR -> Color(0xFFF44336)
                            else -> Color(0xFFBDBDBD)
                        }
                    )
            )

            // 设备名称/状态
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> deviceAlias ?: deviceName ?: "已连接"
                        ConnectionState.CONNECTING -> "连接中..."
                        ConnectionState.SCANNING -> "扫描中..."
                        ConnectionState.ERROR -> "连接错误"
                        ConnectionState.DISCONNECTED -> "未连接蓝牙秤"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 当前重量显示（仅连接时）
            if (isConnected && currentWeight != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentWeight!!.getDisplayValue(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF263238)
                    )
                    Text(
                        text = currentWeight!!.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF757575)
                    )
                    // 稳定指示
                    if (currentWeight!!.isStable) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "稳定",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 操作按钮
            if (isConnected) {
                // 去皮按钮
                OutlinedButton(
                    onClick = { scaleManager.tare() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("去皮", style = MaterialTheme.typography.bodySmall)
                }
                // 置零按钮
                OutlinedButton(
                    onClick = { scaleManager.zero() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("置零", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                // 连接按钮
                Button(
                    onClick = onConnectClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("连接", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
