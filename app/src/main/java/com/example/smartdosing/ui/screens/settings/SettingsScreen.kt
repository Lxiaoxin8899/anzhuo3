package com.example.smartdosing.ui.screens.settings

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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.smartdosing.data.settings.DosingPreferencesManager
import com.example.smartdosing.data.settings.DosingPreferencesState
import com.example.smartdosing.ui.theme.SmartDosingTheme
import kotlin.math.roundToInt
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

    var headerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
    }

    val settingsSections = listOf(
        SettingsSection(
            title = "投料设置",
            icon = Icons.Outlined.Scale,
            items = listOf(
                SettingsItem.Selection(
                    title = "投料模式",
                    subtitle = "选择手动输入或蓝牙电子秤模式",
                    icon = Icons.Outlined.Tune,
                    selectedValue = preferencesState.dosingMode.displayName,
                    options = com.example.smartdosing.data.settings.DosingMode.entries.map { it.displayName },
                    onSelectionChange = { selectedName ->
                        val mode = com.example.smartdosing.data.settings.DosingMode.entries.find { it.displayName == selectedName }
                        if (mode != null) {
                            scope.launch { preferencesManager.setDosingMode(mode) }
                        }
                    }
                ),
                SettingsItem.Action(
                    title = "蓝牙秤设置",
                    subtitle = "管理蓝牙电子秤连接和参数",
                    icon = Icons.Outlined.Bluetooth,
                    onClick = onNavigateToBluetoothSettings
                ),
                SettingsItem.Action(
                    title = "无线传输服务",
                    subtitle = "配置无线传输端口、自动启动与服务状态",
                    icon = Icons.Outlined.Wifi,
                    onClick = onNavigateToWirelessSettings
                )
            )
        ),
        SettingsSection(
            title = "投料语音设置",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            items = listOf(
                SettingsItem.Switch(
                    title = "启用语音重复播报",
                    subtitle = "按步骤自动重复当前材料",
                    icon = Icons.Outlined.RecordVoiceOver,
                    isChecked = preferencesState.voiceRepeatEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { preferencesManager.setVoiceRepeatEnabled(enabled) }
                    }
                ),
                SettingsItem.Slider(
                    title = "重复次数",
                    subtitle = "设置每个材料的播报次数",
                    icon = Icons.Outlined.Repeat,
                    value = preferencesState.voiceRepeatCount.toFloat(),
                    onValueChange = { value ->
                        scope.launch { preferencesManager.setVoiceRepeatCount(value.roundToInt()) }
                    },
                    valueRange = DosingPreferencesManager.MIN_REPEAT_COUNT.toFloat()..DosingPreferencesManager.MAX_REPEAT_COUNT.toFloat(),
                    valueFormatter = { "${it.roundToInt()} 次" }
                )
            )
        ),
        SettingsSection(
            title = "语音设置",
            icon = Icons.Outlined.Campaign,
            items = listOf(
                SettingsItem.Switch(
                    title = "启用语音播报",
                    subtitle = "开启材料投料语音提示",
                    icon = Icons.Outlined.VolumeUp,
                    isChecked = true,
                    onCheckedChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Slider(
                    title = "语音速度",
                    subtitle = "调节语音播报速度",
                    icon = Icons.Outlined.Speed,
                    value = 0.8f,
                    onValueChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Slider(
                    title = "音量大小",
                    subtitle = "调节语音播报音量",
                    icon = Icons.Outlined.VolumeUp,
                    value = 0.75f,
                    onValueChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                )
            )
        ),
        SettingsSection(
            title = "界面设置",
            icon = Icons.Outlined.Palette,
            items = listOf(
                SettingsItem.Switch(
                    title = "夜间模式",
                    subtitle = "适合低光环境使用",
                    icon = Icons.Outlined.DarkMode,
                    isChecked = false,
                    onCheckedChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
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
            title = "投料设置",
            icon = Icons.Outlined.Scale,
            items = listOf(
                SettingsItem.Slider(
                    title = "精度要求",
                    subtitle = "投料重量精度要求（%）",
                    icon = Icons.Outlined.Straighten,
                    value = 0.95f,
                    onValueChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() },
                    valueRange = 0.85f..0.99f,
                    valueFormatter = { "${(it * 100).toInt()}%" }
                ),
                SettingsItem.Slider(
                    title = "超标允许浮动",
                    subtitle = "添加数量允许超过目标的百分比",
                    icon = Icons.Outlined.Warning,
                    value = preferencesState.overLimitTolerancePercent,
                    onValueChange = { value ->
                        scope.launch { preferencesManager.setTolerancePercent(value) }
                    },
                    valueRange = DosingPreferencesManager.MIN_TOLERANCE..DosingPreferencesManager.MAX_TOLERANCE,
                    valueFormatter = { "${it.toInt()}%" }
                ),
                SettingsItem.Switch(
                    title = "自动进入下一步",
                    subtitle = "输入重量后自动进入下一材料",
                    icon = Icons.Outlined.SkipNext,
                    isChecked = false,
                    onCheckedChange = { _ -> Toast.makeText(context, "该功能开发中", Toast.LENGTH_SHORT).show() }
                ),
                SettingsItem.Selection(
                    title = "重量单位",
                    subtitle = "投料重量显示单位",
                    icon = Icons.Outlined.Balance,
                    selectedValue = "公斤",
                    options = listOf("克", "公斤", "磅")
                )
            )
        ),
        SettingsSection(
            title = "数据管理",
            icon = Icons.Outlined.Storage,
            items = listOf(
                SettingsItem.Action(
                    title = "备份数据",
                    subtitle = "备份配方和投料记录",
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
