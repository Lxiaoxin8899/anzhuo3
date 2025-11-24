package com.example.smartdosing.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.*
import com.example.smartdosing.web.WebService
import com.example.smartdosing.web.WebServiceResult
import kotlinx.coroutines.launch

/**
 * Web服务设置页面 - 包含Web服务管理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebServiceSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webService = remember { WebService.getInstance(context) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // 服务状态
    var isServerRunning by remember { mutableStateOf(webService.isServiceRunning()) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var deviceIP by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    // 设置状态
    var webPort by remember { mutableStateOf(8080) }
    var autoStart by remember { mutableStateOf(true) }
    var showAdvanced by remember { mutableStateOf(false) }

    // 加载设备信息
    LaunchedEffect(Unit) {
        val deviceInfo = webService.getDeviceInfo()
        isServerRunning = deviceInfo.isServerRunning
        serverUrl = deviceInfo.serverUrl
        deviceIP = deviceInfo.ipAddress
        webPort = deviceInfo.port
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 页面标题
        SettingsHeader()

        // Web服务控制
        WebServiceControlCard(
            isServerRunning = isServerRunning,
            serverUrl = serverUrl,
            deviceIP = deviceIP,
            statusMessage = statusMessage,
            onStartStopClick = {
                scope.launch {
                    if (isServerRunning) {
                        // 停止服务
                        val success = webService.stopWebService()
                        isServerRunning = false
                        serverUrl = null
                        statusMessage = if (success) "Web服务已停止" else "停止服务失败"
                    } else {
                        // 启动服务
                        when (val result = webService.startWebService(webPort)) {
                            is WebServiceResult.Success -> {
                                isServerRunning = true
                                serverUrl = result.serverUrl
                                deviceIP = result.ipAddress
                                statusMessage = "Web服务启动成功"
                            }
                            is WebServiceResult.AlreadyRunning -> {
                                isServerRunning = true
                                serverUrl = result.serverUrl
                                statusMessage = "Web服务已在运行中"
                            }
                            is WebServiceResult.NetworkError -> {
                                statusMessage = result.message
                            }
                            is WebServiceResult.StartFailed -> {
                                statusMessage = result.message
                            }
                        }
                    }
                }
            },
            onCopyUrl = {
                serverUrl?.let { url ->
                    clipboardManager.setText(AnnotatedString(url))
                    statusMessage = "URL已复制到剪贴板"
                }
            }
        )

        // Web服务配置
        WebServiceConfigCard(
            webPort = webPort,
            autoStart = autoStart,
            showAdvanced = showAdvanced,
            onPortChange = { webPort = it },
            onAutoStartChange = { autoStart = it },
            onShowAdvancedChange = { showAdvanced = it },
            onRestartService = {
                scope.launch {
                    statusMessage = "正在重启服务..."
                    when (val result = webService.restartWebService(webPort)) {
                        is WebServiceResult.Success -> {
                            isServerRunning = true
                            serverUrl = result.serverUrl
                            deviceIP = result.ipAddress
                            statusMessage = "服务重启成功"
                        }
                        else -> {
                            statusMessage = "服务重启失败"
                        }
                    }
                }
            }
        )

        // 系统设置
        SystemSettingsCard()

        // 应用信息
        AppInfoCard()
    }
}

/**
 * 设置页面头部
 */
@Composable
fun SettingsHeader() {
    Column {
        Text(
            text = "系统设置",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "管理Web服务和应用配置",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Web服务控制卡片
 */
@Composable
fun WebServiceControlCard(
    isServerRunning: Boolean,
    serverUrl: String?,
    deviceIP: String?,
    statusMessage: String,
    onStartStopClick: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // 标题和状态指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Web管理后台",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                StatusIndicator(isRunning = isServerRunning)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 服务状态信息
            ServiceStatusInfo(
                isServerRunning = isServerRunning,
                deviceIP = deviceIP,
                serverUrl = serverUrl,
                statusMessage = statusMessage
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 控制按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartStopClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServerRunning)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isServerRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isServerRunning) "停止服务" else "启动服务",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServerRunning) "停止服务" else "启动服务",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isServerRunning && serverUrl != null) {
                    OutlinedButton(
                        onClick = onCopyUrl,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "复制链接",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制链接")
                    }
                }
            }
        }
    }
}

/**
 * 状态指示器
 */
@Composable
fun StatusIndicator(isRunning: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .drawBehind {
                    drawCircle(
                        color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
        )
        Text(
            text = if (isRunning) "运行中" else "已停止",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

/**
 * 服务状态信息
 */
@Composable
fun ServiceStatusInfo(
    isServerRunning: Boolean,
    deviceIP: String?,
    serverUrl: String?,
    statusMessage: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (deviceIP != null) {
            InfoRow(
                label = "设备IP",
                value = deviceIP,
                icon = Icons.Default.Phone
            )
        }

        if (isServerRunning && serverUrl != null) {
            InfoRow(
                label = "访问地址",
                value = serverUrl,
                icon = Icons.Default.Home
            )
        }

        if (statusMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Web服务配置卡片
 */
@Composable
fun WebServiceConfigCard(
    webPort: Int,
    autoStart: Boolean,
    showAdvanced: Boolean,
    onPortChange: (Int) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onShowAdvancedChange: (Boolean) -> Unit,
    onRestartService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "服务配置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 端口设置
            SettingRow(
                title = "服务端口",
                subtitle = "Web服务器监听端口号",
                icon = Icons.Default.Settings
            ) {
                OutlinedTextField(
                    value = webPort.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { port ->
                            if (port in 1024..65535) {
                                onPortChange(port)
                            }
                        }
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // 自动启动
            SettingRow(
                title = "自动启动",
                subtitle = "应用启动时自动开启Web服务",
                icon = Icons.Default.PlayArrow
            ) {
                Switch(
                    checked = autoStart,
                    onCheckedChange = onAutoStartChange
                )
            }

            // 高级设置
            SettingRow(
                title = "高级设置",
                subtitle = "显示更多配置选项",
                icon = Icons.Default.MoreVert
            ) {
                Switch(
                    checked = showAdvanced,
                    onCheckedChange = onShowAdvancedChange
                )
            }

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 重启服务按钮
                OutlinedButton(
                    onClick = onRestartService,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重启服务",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重启Web服务")
                }
            }
        }
    }
}

/**
 * 系统设置卡片
 */
@Composable
fun SystemSettingsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "系统设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingRow(
                title = "数据备份",
                subtitle = "备份配方和设置数据",
                icon = Icons.Default.Settings
            ) {
                OutlinedButton(onClick = { /* TODO */ }) {
                    Text("备份")
                }
            }

            SettingRow(
                title = "数据恢复",
                subtitle = "从备份文件恢复数据",
                icon = Icons.Default.Add
            ) {
                OutlinedButton(onClick = { /* TODO */ }) {
                    Text("恢复")
                }
            }

            SettingRow(
                title = "清除缓存",
                subtitle = "清除应用缓存数据",
                icon = Icons.Default.Clear
            ) {
                OutlinedButton(onClick = { /* TODO */ }) {
                    Text("清除")
                }
            }
        }
    }
}

/**
 * 应用信息卡片
 */
@Composable
fun AppInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "关于应用",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoRow(
                label = "应用版本",
                value = "1.0.0",
                icon = Icons.Default.Info
            )

            InfoRow(
                label = "开发者",
                value = "SmartDosing Team",
                icon = Icons.Default.Person
            )

            InfoRow(
                label = "技术支持",
                value = "smartdosing@example.com",
                icon = Icons.Default.Email
            )
        }
    }
}

/**
 * 设置行组件
 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action()
    }
}

/**
 * 信息行组件
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun WebServiceSettingsScreenPreview() {
    SmartDosingTheme {
        WebServiceSettingsScreen()
    }
}