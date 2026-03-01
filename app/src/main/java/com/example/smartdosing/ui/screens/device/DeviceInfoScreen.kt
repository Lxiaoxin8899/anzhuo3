package com.example.smartdosing.ui.screens.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.data.device.DeviceIdentity
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.data.device.ReceiverStatus
import com.example.smartdosing.data.transfer.TaskReceiver
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val windowSize = LocalWindowSize.current
    val isCompact = windowSize.widthClass == SmartDosingWindowWidthClass.Compact

    val deviceIdentity = remember { DeviceUIDManager.getDeviceIdentity(context) }
    val taskReceiver = remember { TaskReceiver.getInstance(context) }

    val authorizedSenders by taskReceiver.getAuthorizedSendersFlow().collectAsState(initial = emptyList())
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("设备信息", "授权设备")

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设备管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.padding(horizontal = if (isCompact) 8.dp else 0.dp)) {
                if (isCompact) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DeviceTabs(
                            tabs = tabs,
                            selectedIndex = selectedTabIndex,
                            onSelect = { selectedTabIndex = it }
                        )
                    }
                } else {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        DeviceTabs(
                            tabs = tabs,
                            selectedIndex = selectedTabIndex,
                            onSelect = { selectedTabIndex = it }
                        )
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> DeviceInfoTab(
                    deviceIdentity = deviceIdentity,
                    isCompact = isCompact,
                    onCopyUID = {
                        clipboardManager.setText(AnnotatedString(deviceIdentity.uid))
                        scope.launch { snackbarHostState.showSnackbar("UID 已复制") }
                    },
                    onCopyIP = {
                        deviceIdentity.ipAddress?.let { ip ->
                            clipboardManager.setText(AnnotatedString("http://$ip:${deviceIdentity.port}"))
                            scope.launch { snackbarHostState.showSnackbar("地址已复制") }
                        }
                    }
                )
                1 -> AuthorizedSendersTab(
                    senders = authorizedSenders,
                    isCompact = isCompact,
                    onToggleSender = { sender ->
                        scope.launch { taskReceiver.setSenderActive(sender.uid, !sender.isActive) }
                    },
                    onRemoveSender = { sender ->
                        scope.launch {
                            taskReceiver.revokeSender(sender.uid)
                            snackbarHostState.showSnackbar("已移除 ${sender.name}")
                        }
                    }
                )

            }
        }
    }
}

@Composable
private fun DeviceTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = selectedIndex == index,
            onClick = { onSelect(index) },
            text = { Text(title, style = MaterialTheme.typography.bodyMedium) }
        )
    }
}

@Composable
private fun DeviceInfoTab(
    deviceIdentity: DeviceIdentity,
    isCompact: Boolean,
    onCopyUID: () -> Unit,
    onCopyIP: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (isCompact) 12.dp else 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isCompact) 12.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(if (isCompact) 36.dp else 48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("本机 UID", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deviceIdentity.uid,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onCopyUID) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制 UID")
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isCompact) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("设备状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    DeviceInfoRow(
                        icon = Icons.Default.Smartphone,
                        label = "设备名称",
                        value = deviceIdentity.deviceName,
                        compact = isCompact
                    )
                    DeviceInfoRow(
                        icon = Icons.Default.Wifi,
                        label = "IP 地址",
                        value = deviceIdentity.ipAddress ?: "未连接网络",
                        valueColor = if (deviceIdentity.ipAddress != null)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        compact = isCompact
                    )
                    DeviceInfoRow(
                        icon = Icons.Default.Router,
                        label = "服务端口",
                        value = "${deviceIdentity.port}",
                        compact = isCompact
                    )
                    DeviceInfoRow(
                        icon = Icons.Outlined.Circle,
                        label = "连接状态",
                        value = when (deviceIdentity.status) {
                            ReceiverStatus.IDLE -> "空闲"
                            ReceiverStatus.BUSY -> "忙碌"
                            ReceiverStatus.OFFLINE -> "离线"
                        },
                        valueColor = when (deviceIdentity.status) {
                            ReceiverStatus.IDLE -> Color(0xFF4CAF50)
                            ReceiverStatus.BUSY -> Color(0xFFFF9800)
                            ReceiverStatus.OFFLINE -> MaterialTheme.colorScheme.error
                        },
                        compact = isCompact
                    )
                    DeviceInfoRow(
                        icon = Icons.Default.Info,
                        label = "应用版本",
                        value = deviceIdentity.appVersion,
                        compact = isCompact
                    )
                }
            }
        }

        item {
            deviceIdentity.ipAddress?.let { ip ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isCompact) 12.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("无线传输接收地址", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "发送端可通过该地址进行无线任务下发（局域网）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "http://$ip:${deviceIdentity.port}",
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        OutlinedButton(
                            onClick = onCopyIP,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制地址")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isCompact) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("使用说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "1. 确保发送端与本机在同一网络（WiFi/有线）\n" +
                                "2. 发送端输入 UID 或扫描二维码完成绑定\n" +
                                "3. 绑定成功后即可推送任务\n" +
                                "（提示：可视化页面已软下线，仅保留无线传输能力）\n" +
                                "4. 收到任务后可在「任务中心」中处理",
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean
) {
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, color = valueColor, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AuthorizedSendersTab(
    senders: List<AuthorizedSenderEntity>,
    isCompact: Boolean,
    onToggleSender: (AuthorizedSenderEntity) -> Unit,
    onRemoveSender: (AuthorizedSenderEntity) -> Unit
) {
    if (senders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isCompact) 24.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.DevicesOther,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("暂无授权设备", color = MaterialTheme.colorScheme.outline)
                Text("当其他设备第一次发送任务时会自动添加", color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (isCompact) 12.dp else 16.dp,
                vertical = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(senders) { sender ->
                AuthorizedSenderCard(
                    sender = sender,
                    isCompact = isCompact,
                    onToggle = { onToggleSender(sender) },
                    onRemove = { onRemoveSender(sender) }
                )
            }
        }
    }
}

@Composable
private fun AuthorizedSenderCard(
    sender: AuthorizedSenderEntity,
    isCompact: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (sender.isActive)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 40.dp else 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (sender.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = if (sender.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(sender.name, style = MaterialTheme.typography.titleMedium)
                    Text("UID: ${sender.uid}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("已发送 ${sender.taskCount} 个任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isCompact) {
                    Switch(checked = sender.isActive, onCheckedChange = { onToggle() })
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (isCompact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = sender.isActive, onCheckedChange = { onToggle() })
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}


