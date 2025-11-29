package com.example.smartdosing.ui.screens.device

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.example.smartdosing.database.entities.ReceivedTaskEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 设备信息界面 - 显示本机 UID 和接收端状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 设备信息
    val deviceIdentity = remember { DeviceUIDManager.getDeviceIdentity(context) }

    // 数据库和任务接收器
    val database = remember { SmartDosingDatabase.getDatabase(context) }
    val taskReceiver = remember { TaskReceiver.getInstance(context) }

    // 授权发送端列表
    val authorizedSenders by taskReceiver.getAuthorizedSendersFlow().collectAsState(initial = emptyList())

    // 待处理任务
    val pendingTasks by taskReceiver.getPendingTasksFlow().collectAsState(initial = emptyList())

    // 当前选中的 Tab
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("设备信息", "授权设备", "接收任务")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Tab 栏
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title)
                                if (index == 2 && pendingTasks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text("${pendingTasks.size}") }
                                }
                            }
                        }
                    )
                }
            }

            // Tab 内容
            when (selectedTabIndex) {
                0 -> DeviceInfoTab(
                    deviceIdentity = deviceIdentity,
                    onCopyUID = {
                        clipboardManager.setText(AnnotatedString(deviceIdentity.uid))
                        scope.launch {
                            snackbarHostState.showSnackbar("UID 已复制到剪贴板")
                        }
                    },
                    onCopyIP = {
                        deviceIdentity.ipAddress?.let {
                            clipboardManager.setText(AnnotatedString("http://$it:${deviceIdentity.port}"))
                            scope.launch {
                                snackbarHostState.showSnackbar("地址已复制到剪贴板")
                            }
                        }
                    }
                )
                1 -> AuthorizedSendersTab(
                    senders = authorizedSenders,
                    onToggleSender = { sender ->
                        scope.launch {
                            taskReceiver.setSenderActive(sender.uid, !sender.isActive)
                        }
                    },
                    onRemoveSender = { sender ->
                        scope.launch {
                            taskReceiver.revokeSender(sender.uid)
                            snackbarHostState.showSnackbar("已移除 ${sender.name}")
                        }
                    }
                )
                2 -> ReceivedTasksTab(
                    tasks = pendingTasks,
                    onAcceptTask = { task ->
                        scope.launch {
                            if (taskReceiver.acceptTask(task.id)) {
                                snackbarHostState.showSnackbar("已接收任务")
                            }
                        }
                    },
                    onRejectTask = { task ->
                        scope.launch {
                            if (taskReceiver.rejectTask(task.id)) {
                                snackbarHostState.showSnackbar("已拒绝任务")
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * 设备信息 Tab
 */
@Composable
private fun DeviceInfoTab(
    deviceIdentity: DeviceIdentity,
    onCopyUID: () -> Unit,
    onCopyIP: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // UID 卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "本机 UID",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deviceIdentity.uid,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCopyUID,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制 UID")
                    }
                }
            }
        }

        // 设备状态卡片
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "设备状态",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    DeviceInfoRow(
                        icon = Icons.Outlined.Smartphone,
                        label = "设备名称",
                        value = deviceIdentity.deviceName
                    )
                    DeviceInfoRow(
                        icon = Icons.Outlined.Wifi,
                        label = "IP 地址",
                        value = deviceIdentity.ipAddress ?: "未连接网络",
                        valueColor = if (deviceIdentity.ipAddress != null)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    DeviceInfoRow(
                        icon = Icons.Outlined.Router,
                        label = "服务端口",
                        value = "${deviceIdentity.port}"
                    )
                    DeviceInfoRow(
                        icon = Icons.Outlined.Circle,
                        label = "接收状态",
                        value = when (deviceIdentity.status) {
                            ReceiverStatus.IDLE -> "空闲"
                            ReceiverStatus.BUSY -> "忙碌"
                            ReceiverStatus.OFFLINE -> "离线"
                        },
                        valueColor = when (deviceIdentity.status) {
                            ReceiverStatus.IDLE -> Color(0xFF4CAF50)
                            ReceiverStatus.BUSY -> Color(0xFFFF9800)
                            ReceiverStatus.OFFLINE -> MaterialTheme.colorScheme.error
                        }
                    )
                    DeviceInfoRow(
                        icon = Icons.Outlined.Info,
                        label = "应用版本",
                        value = deviceIdentity.appVersion
                    )
                }
            }
        }

        // 接收地址卡片
        item {
            deviceIdentity.ipAddress?.let { ip ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "接收地址",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "发送端可通过以下地址连接本机：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "http://$ip:${deviceIdentity.port}",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onCopyIP,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("复制地址")
                        }
                    }
                }
            }
        }

        // 使用说明
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "使用说明",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. 确保发送端和本机在同一局域网内\n" +
                               "2. 在发送端输入本机 UID 或扫描二维码进行绑定\n" +
                               "3. 绑定成功后，发送端即可向本机推送任务\n" +
                               "4. 收到任务后可在「接收任务」中查看和处理",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}

/**
 * 设备信息行
 */
@Composable
private fun DeviceInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * 授权设备 Tab
 */
@Composable
private fun AuthorizedSendersTab(
    senders: List<AuthorizedSenderEntity>,
    onToggleSender: (AuthorizedSenderEntity) -> Unit,
    onRemoveSender: (AuthorizedSenderEntity) -> Unit
) {
    if (senders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                Text(
                    text = "暂无授权设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当其他设备发送任务时会自动添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(senders) { sender ->
                AuthorizedSenderCard(
                    sender = sender,
                    onToggle = { onToggleSender(sender) },
                    onRemove = { onRemoveSender(sender) }
                )
            }
        }
    }
}

/**
 * 授权发送端卡片
 */
@Composable
private fun AuthorizedSenderCard(
    sender: AuthorizedSenderEntity,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (sender.isActive)
                MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
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

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sender.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "UID: ${sender.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "已发送 ${sender.taskCount} 个任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 操作
            Switch(
                checked = sender.isActive,
                onCheckedChange = { onToggle() }
            )

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 接收任务 Tab
 */
@Composable
private fun ReceivedTasksTab(
    tasks: List<ReceivedTaskEntity>,
    onAcceptTask: (ReceivedTaskEntity) -> Unit,
    onRejectTask: (ReceivedTaskEntity) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无待处理任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "等待其他设备发送任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tasks) { task ->
                ReceivedTaskCard(
                    task = task,
                    onAccept = { onAcceptTask(task) },
                    onReject = { onRejectTask(task) }
                )
            }
        }
    }
}

/**
 * 接收任务卡片
 */
@Composable
private fun ReceivedTaskCard(
    task: ReceivedTaskEntity,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val priorityColor = when (task.priority) {
        "URGENT" -> Color(0xFFD32F2F)
        "HIGH" -> Color(0xFFFF9800)
        "NORMAL" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和优先级
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = priorityColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = when (task.priority) {
                            "URGENT" -> "紧急"
                            "HIGH" -> "高优先级"
                            "NORMAL" -> "普通"
                            else -> "低优先级"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 配方信息
            Text(
                text = "${task.recipeCode} - ${task.recipeName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "数量: ${task.quantity} ${task.unit}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 来源信息
            Row {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "来自: ${task.senderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = dateFormat.format(Date(task.receivedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 备注
            task.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拒绝")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAccept) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("接收")
                }
            }
        }
    }
}
