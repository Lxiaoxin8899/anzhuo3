package com.example.smartdosing.ui.screens.settings

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.smartdosing.SmartDosingApplication
import com.example.smartdosing.audio.BeepMode
import com.example.smartdosing.audio.BeepToneType
import com.example.smartdosing.data.settings.AdminPreferencesManager
import com.example.smartdosing.data.settings.DosingPreferencesManager
import com.example.smartdosing.data.settings.DosingPreferencesState
import com.example.smartdosing.ui.theme.SmartDosingTheme
import kotlinx.coroutines.launch

/**
 * 系统设置页面
 * 提供应用配置和个性化设置
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToBluetoothSettings: () -> Unit = {},
    onNavigateToWirelessSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferencesManager = remember { DosingPreferencesManager(context) }
    val preferencesState by preferencesManager.preferencesFlow.collectAsState(initial = DosingPreferencesState())
    val scope = rememberCoroutineScope()

    // 管理员权限
    val adminManager = remember {
        (context.applicationContext as SmartDosingApplication).adminPreferencesManager
    }
    val adminSettings by adminManager.settingsFlow.collectAsState(
        initial = AdminPreferencesManager.AdminSettingsState()
    )
    val isAdminLoggedIn by AdminPreferencesManager.isAdminLoggedIn.collectAsState()
    val hasAdminPassword = adminSettings.passwordHash.isNotEmpty()

    // 管理员对话框状态
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }

    // 音调预览播放器
    val toneGenerator = remember {
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }
    val previewTone: (BeepToneType, Int) -> Unit = { type, durationMs ->
        try { toneGenerator?.startTone(type.toneId, durationMs) } catch (_: Exception) { }
    }

    var headerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
    }

    val settingsSections = listOf(
        SettingsSection(
            title = "实验配置",
            icon = Icons.Outlined.Scale,
            items = listOf(
                SettingsItem.Selection(
                    title = "配置模式",
                    subtitle = if (hasAdminPassword && !isAdminLoggedIn)
                        "选择手动输入或蓝牙电子秤模式（需管理员权限）"
                    else
                        "选择手动输入或蓝牙电子秤模式",
                    icon = Icons.Outlined.Tune,
                    selectedValue = preferencesState.dosingMode.displayName,
                    options = com.example.smartdosing.data.settings.DosingMode.entries.map { it.displayName },
                    onSelectionChange = { selectedName ->
                        if (hasAdminPassword && !isAdminLoggedIn) {
                            showAdminLoginDialog = true
                            return@Selection
                        }
                        val mode = com.example.smartdosing.data.settings.DosingMode.entries.find { it.displayName == selectedName }
                        if (mode != null) {
                            scope.launch { preferencesManager.setDosingMode(mode) }
                        }
                    }
                ),
                SettingsItem.Action(
                    title = "天平设置",
                    subtitle = "管理蓝牙电子天平连接和参数",
                    icon = Icons.Outlined.Bluetooth,
                    onClick = onNavigateToBluetoothSettings
                ),
                SettingsItem.Action(
                    title = "无线传输服务",
                    subtitle = "配置无线传输端口、自动启动与服务状态",
                    icon = Icons.Outlined.Wifi,
                    onClick = onNavigateToWirelessSettings
                ),
                SettingsItem.Switch(
                    title = "允许手动输入",
                    subtitle = if (adminSettings.manualInputEnabled)
                        "蓝牙模式下允许手动校准重量"
                    else
                        "蓝牙模式下禁止手动输入（仅天平数据）",
                    icon = if (adminSettings.manualInputEnabled) Icons.Outlined.Edit else Icons.Outlined.EditOff,
                    isChecked = adminSettings.manualInputEnabled,
                    onCheckedChange = { enabled ->
                        if (hasAdminPassword && !isAdminLoggedIn) {
                            showAdminLoginDialog = true
                            return@Switch
                        }
                        scope.launch { adminManager.setManualInputEnabled(enabled) }
                    }
                )
            )
        ),
        // TTS 语音播报设置已软下线
        SettingsSection(
            title = "界面偏好",
            icon = Icons.Outlined.Palette,
            items = listOf(
                SettingsItem.Selection(
                    title = "主题模式",
                    subtitle = "选择应用外观主题",
                    icon = Icons.Outlined.DarkMode,
                    selectedValue = preferencesState.themeMode.displayName,
                    options = com.example.smartdosing.data.settings.ThemeMode.entries.map { it.displayName },
                    onSelectionChange = { selectedName ->
                        val mode = com.example.smartdosing.data.settings.ThemeMode.entries.find { it.displayName == selectedName }
                        if (mode != null) {
                            scope.launch { preferencesManager.setThemeMode(mode) }
                        }
                    }
                ),
                SettingsItem.Selection(
                    title = "字体大小",
                    subtitle = "调整界面文字大小",
                    icon = Icons.Outlined.FormatSize,
                    selectedValue = "标准",
                    options = listOf("小", "标准", "大", "特大")
                ),
                SettingsItem.Switch(
                    title = "震动反馈",
                    subtitle = "按钮点击时震动",
                    icon = Icons.Outlined.Vibration,
                    isChecked = true,
                    onCheckedChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                )
            )
        ),
        SettingsSection(
            title = "提示音",
            icon = Icons.Outlined.VolumeUp,
            items = buildList {
                add(SettingsItem.Selection(
                    title = "提示音模式",
                    subtitle = "配料过程中的声音提示方式",
                    icon = Icons.Outlined.Notifications,
                    selectedValue = preferencesState.beepMode.displayName,
                    options = BeepMode.entries.map { it.displayName },
                    onSelectionChange = { selectedName ->
                        val mode = BeepMode.entries.find { it.displayName == selectedName }
                        if (mode != null) {
                            scope.launch { preferencesManager.setBeepMode(mode) }
                        }
                    }
                ))
                // 通用声音设置（渐进或阈值模式时显示）
                if (preferencesState.beepMode != BeepMode.OFF) {
                    add(SettingsItem.Selection(
                        title = "提示音调",
                        subtitle = "配料过程中的提示音类型",
                        icon = Icons.Outlined.MusicNote,
                        selectedValue = preferencesState.beepToneType.displayName,
                        options = BeepToneType.entries.map { it.displayName },
                        onSelectionChange = { name ->
                            val type = BeepToneType.entries.find { it.displayName == name }
                            if (type != null) {
                                previewTone(type, preferencesState.beepToneDurationMs)
                                scope.launch { preferencesManager.setBeepToneType(type) }
                            }
                        }
                    ))
                    add(SettingsItem.Slider(
                        title = "提示音长",
                        subtitle = "单次提示音持续时长",
                        icon = Icons.Outlined.Timer,
                        value = preferencesState.beepToneDurationMs.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setBeepToneDurationMs(it.toInt()) } },
                        valueRange = 30f..300f,
                        valueFormatter = { "${it.toInt()}ms" }
                    ))
                    add(SettingsItem.Selection(
                        title = "到达音调",
                        subtitle = "重量到达目标时的提示音",
                        icon = Icons.Outlined.CheckCircle,
                        selectedValue = preferencesState.beepArrivedToneType.displayName,
                        options = BeepToneType.entries.map { it.displayName },
                        onSelectionChange = { name ->
                            val type = BeepToneType.entries.find { it.displayName == name }
                            if (type != null) {
                                previewTone(type, preferencesState.beepArrivedDurationMs)
                                scope.launch { preferencesManager.setBeepArrivedToneType(type) }
                            }
                        }
                    ))
                    add(SettingsItem.Slider(
                        title = "到达音长",
                        subtitle = "到达目标时提示音持续时长",
                        icon = Icons.Outlined.Timer,
                        value = preferencesState.beepArrivedDurationMs.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setBeepArrivedDurationMs(it.toInt()) } },
                        valueRange = 100f..500f,
                        valueFormatter = { "${it.toInt()}ms" }
                    ))
                }
                // 渐进模式专属设置
                if (preferencesState.beepMode == BeepMode.PROGRESSIVE) {
                    add(SettingsItem.Slider(
                        title = "开始提示比例",
                        subtitle = "达到目标重量的百分比后开始渐进提示",
                        icon = Icons.Outlined.PlayArrow,
                        value = preferencesState.progressiveStartPercent.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setProgressiveStartPercent(it.toInt()) } },
                        valueRange = 20f..80f,
                        valueFormatter = { "${it.toInt()}%" }
                    ))
                    add(SettingsItem.Slider(
                        title = "最慢间隔",
                        subtitle = "刚开始提示时的最大间隔",
                        icon = Icons.Outlined.SlowMotionVideo,
                        value = preferencesState.progressiveMaxIntervalMs.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setProgressiveMaxIntervalMs(it.toInt()) } },
                        valueRange = 500f..3000f,
                        valueFormatter = { "${it.toInt()}ms" }
                    ))
                    add(SettingsItem.Slider(
                        title = "最快间隔",
                        subtitle = "接近目标时的最小间隔",
                        icon = Icons.Outlined.Speed,
                        value = preferencesState.progressiveMinIntervalMs.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setProgressiveMinIntervalMs(it.toInt()) } },
                        valueRange = 50f..500f,
                        valueFormatter = { "${it.toInt()}ms" }
                    ))
                    add(SettingsItem.Slider(
                        title = "加速曲线",
                        subtitle = "1.0=线性 2.0=平方 4.0=急加速",
                        icon = Icons.Outlined.TrendingUp,
                        value = preferencesState.progressiveCurveExponent,
                        onValueChange = { scope.launch { preferencesManager.setProgressiveCurveExponent(it) } },
                        valueRange = 1.0f..4.0f,
                        valueFormatter = { "%.1f".format(it) }
                    ))
                }
                // 阈值模式专属设置
                if (preferencesState.beepMode == BeepMode.THRESHOLD) {
                    add(SettingsItem.Slider(
                        title = "提示阈值",
                        subtitle = "达到目标重量的百分比后开始提示",
                        icon = Icons.Outlined.TrendingUp,
                        value = preferencesState.beepThresholdPercent.toFloat(),
                        onValueChange = { value ->
                            scope.launch { preferencesManager.setBeepThresholdPercent(value.toInt()) }
                        },
                        valueRange = 50f..99f,
                        valueFormatter = { "${it.toInt()}%" }
                    ))
                    add(SettingsItem.Switch(
                        title = "连续提示",
                        subtitle = "到达阈值后持续发出提示音，关闭则仅提示一次",
                        icon = Icons.Outlined.RepeatOne,
                        isChecked = preferencesState.beepThresholdContinuous,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setBeepThresholdContinuous(enabled) }
                        }
                    ))
                    if (preferencesState.beepThresholdContinuous) {
                        add(SettingsItem.Slider(
                            title = "连续提示间隔",
                            subtitle = "阈值模式下连续提示的时间间隔",
                            icon = Icons.Outlined.Timelapse,
                            value = preferencesState.thresholdIntervalMs.toFloat(),
                            onValueChange = { scope.launch { preferencesManager.setThresholdIntervalMs(it.toInt()) } },
                            valueRange = 100f..1000f,
                            valueFormatter = { "${it.toInt()}ms" }
                        ))
                    }
                }
            }
        ),
        SettingsSection(
            title = "自动确认提示音",
            icon = Icons.Outlined.NotificationsActive,
            items = buildList {
                add(SettingsItem.Switch(
                    title = "倒计时提示音",
                    subtitle = "自动确认倒计时过程中播放提示音",
                    icon = Icons.Outlined.Alarm,
                    isChecked = preferencesState.autoConfirmBeepEnabled,
                    onCheckedChange = { scope.launch { preferencesManager.setAutoConfirmBeepEnabled(it) } }
                ))
                if (preferencesState.autoConfirmBeepEnabled) {
                    add(SettingsItem.Selection(
                        title = "倒计时音调",
                        subtitle = "倒计时过程中的提示音类型",
                        icon = Icons.Outlined.MusicNote,
                        selectedValue = preferencesState.autoConfirmBeepToneType.displayName,
                        options = BeepToneType.entries.map { it.displayName },
                        onSelectionChange = { name ->
                            val type = BeepToneType.entries.find { it.displayName == name }
                            if (type != null) {
                                previewTone(type, preferencesState.beepToneDurationMs)
                                scope.launch { preferencesManager.setAutoConfirmBeepToneType(type) }
                            }
                        }
                    ))
                    add(SettingsItem.Selection(
                        title = "确认完成音调",
                        subtitle = "自动确认完成时的提示音",
                        icon = Icons.Outlined.Verified,
                        selectedValue = preferencesState.autoConfirmCompleteToneType.displayName,
                        options = BeepToneType.entries.map { it.displayName },
                        onSelectionChange = { name ->
                            val type = BeepToneType.entries.find { it.displayName == name }
                            if (type != null) {
                                previewTone(type, preferencesState.autoConfirmCompleteDurationMs)
                                scope.launch { preferencesManager.setAutoConfirmCompleteToneType(type) }
                            }
                        }
                    ))
                    add(SettingsItem.Slider(
                        title = "确认完成音长",
                        subtitle = "确认完成提示音持续时长",
                        icon = Icons.Outlined.Timer,
                        value = preferencesState.autoConfirmCompleteDurationMs.toFloat(),
                        onValueChange = { scope.launch { preferencesManager.setAutoConfirmCompleteDurationMs(it.toInt()) } },
                        valueRange = 100f..500f,
                        valueFormatter = { "${it.toInt()}ms" }
                    ))
                }
            }
        ),
        SettingsSection(
            title = "数据维护",
            icon = Icons.Outlined.Storage,
            items = listOf(
                SettingsItem.Action(
                    title = "备份数据",
                    subtitle = "备份配方和实验记录",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Action(
                    title = "恢复数据",
                    subtitle = "从备份文件恢复数据",
                    icon = Icons.Outlined.CloudDownload,
                    onClick = { Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Action(
                    title = "清除缓存",
                    subtitle = "清理应用缓存文件",
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = { Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() },
                    isDestructive = true
                )
            )
        ),
        SettingsSection(
            title = "管理员",
            icon = Icons.Outlined.Lock,
            items = buildList {
                if (!hasAdminPassword) {
                    add(SettingsItem.Action(
                        title = "设置管理员密码",
                        subtitle = "设置后可锁定关键配置项",
                        icon = Icons.Outlined.Lock,
                        onClick = { showSetPasswordDialog = true }
                    ))
                } else if (!isAdminLoggedIn) {
                    add(SettingsItem.Action(
                        title = "管理员登录",
                        subtitle = "登录后可修改受保护的设置",
                        icon = Icons.Outlined.LockOpen,
                        onClick = { showAdminLoginDialog = true }
                    ))
                } else {
                    add(SettingsItem.Info(
                        title = "管理员状态",
                        subtitle = "已登录（应用重启后需重新验证）",
                        icon = Icons.Outlined.Shield
                    ))
                    add(SettingsItem.Action(
                        title = "修改管理员密码",
                        subtitle = "更改管理员密码",
                        icon = Icons.Outlined.Lock,
                        onClick = { showSetPasswordDialog = true }
                    ))
                    add(SettingsItem.Action(
                        title = "退出管理员",
                        subtitle = "退出后受保护设置将被锁定",
                        icon = Icons.Outlined.ExitToApp,
                        onClick = { adminManager.logout() }
                    ))
                }
            }
        ),
        SettingsSection(
            title = "关于应用",
            icon = Icons.Outlined.Info,
            items = listOf(
                SettingsItem.Info(
                    title = "版本信息",
                    subtitle = "SmartDosing v1.0.0",
                    icon = Icons.Outlined.NewReleases
                ),
                SettingsItem.Action(
                    title = "使用帮助",
                    subtitle = "查看操作指南和常见问题",
                    icon = Icons.Outlined.Help,
                    onClick = { Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Action(
                    title = "反馈建议",
                    subtitle = "提交使用反馈和改进建议",
                    icon = Icons.Outlined.Feedback,
                    onClick = { Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                )
            )
        )
    )

    // 管理员登录对话框
    if (showAdminLoginDialog) {
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAdminLoginDialog = false },
            title = { Text("管理员验证") },
            text = {
                Column {
                    Text(
                        "请输入管理员密码以修改受保护的设置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("密码") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = error?.let { msg -> { Text(msg) } }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (adminManager.verifyAndLogin(password, adminSettings.passwordHash)) {
                        showAdminLoginDialog = false
                    } else {
                        error = "密码错误"
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLoginDialog = false }) { Text("取消") }
            }
        )
    }

    // 设置/修改密码对话框
    if (showSetPasswordDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSetPasswordDialog = false },
            title = { Text(if (!hasAdminPassword) "设置管理员密码" else "修改管理员密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text("新密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text("确认密码") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = error?.let { msg -> { Text(msg) } }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        newPassword.length < 6 -> error = "密码至少6位"
                        newPassword != confirmPassword -> error = "两次输入不一致"
                        else -> {
                            scope.launch { adminManager.setPassword(newPassword) }
                            adminManager.verifyAndLogin(
                                newPassword,
                                AdminPreferencesManager.hashPassword(newPassword)
                            )
                            showSetPasswordDialog = false
                            Toast.makeText(context, "管理员密码已设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showSetPasswordDialog = false }) { Text("取消") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = headerVisible,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                    initialOffsetY = { -30 },
                    animationSpec = tween(500)
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "系统设置",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "自定义应用行为和外观",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(settingsSections) { index, section ->
            AnimatedSettingsSection(
                section = section,
                index = index
            )
        }
    }
}

/**
 * 带动画的设置分区
 */
@Composable
private fun AnimatedSettingsSection(
    section: SettingsSection,
    index: Int
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L + 100)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(300)
                )
    ) {
        SettingsSectionCard(section = section)
    }
}

/**
 * 设置分区数据类
 */
data class SettingsSection(
    val title: String,
    val icon: ImageVector,
    val items: List<SettingsItem>
)

/**
 * 设置项数据类
 */
sealed class SettingsItem {
    data class Switch(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val isChecked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingsItem()

    data class Slider(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val value: Float,
        val onValueChange: (Float) -> Unit,
        val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        val valueFormatter: (Float) -> String = { "${(it * 100).toInt()}%" }
    ) : SettingsItem()

    data class Selection(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val selectedValue: String,
        val options: List<String>,
        val onSelectionChange: (String) -> Unit = { }
    ) : SettingsItem()

    data class Action(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val onClick: () -> Unit,
        val isDestructive: Boolean = false
    ) : SettingsItem()

    data class Info(
        val title: String,
        val subtitle: String,
        val icon: ImageVector
    ) : SettingsItem()
}

/**
 * 设置分区卡片组件
 */
@Composable
fun SettingsSectionCard(
    section: SettingsSection
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
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
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = section.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            section.items.forEachIndexed { index, item ->
                SettingsItemView(item = item)
                if (index < section.items.size - 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

/**
 * 设置项视图
 */
@Composable
fun SettingsItemView(item: SettingsItem) {
    when (item) {
        is SettingsItem.Switch -> {
            SwitchSettingItem(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                isChecked = item.isChecked,
                onCheckedChange = item.onCheckedChange
            )
        }
        is SettingsItem.Slider -> {
            SliderSettingItem(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                value = item.value,
                onValueChange = item.onValueChange,
                valueRange = item.valueRange,
                valueFormatter = item.valueFormatter
            )
        }
        is SettingsItem.Selection -> {
            SelectionSettingItem(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                selectedValue = item.selectedValue,
                options = item.options,
                onSelectionChange = item.onSelectionChange
            )
        }
        is SettingsItem.Action -> {
            ActionSettingItem(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                onClick = item.onClick,
                isDestructive = item.isDestructive
            )
        }
        is SettingsItem.Info -> {
            InfoSettingItem(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon
            )
        }
    }
}

/**
 * 开关设置项
 */
@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 滑块设置项
 */
@Composable
fun SliderSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = valueFormatter(value),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * 选择设置项
 */
@Composable
fun SelectionSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selectedValue: String,
    options: List<String>,
    onSelectionChange: (String) -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectionChange(option)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedValue == option,
                                onClick = {
                                    onSelectionChange(option)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showDialog = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedValue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "选择",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 操作设置项
 */
@Composable
fun ActionSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val backgroundColor = if (isDestructive)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "执行",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 信息设置项
 */
@Composable
fun InfoSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun SettingsScreenPreview() {
    SmartDosingTheme {
        SettingsScreen()
    }
}
