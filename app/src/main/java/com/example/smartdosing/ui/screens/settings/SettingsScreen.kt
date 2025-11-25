package com.example.smartdosing.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferencesManager = remember { DosingPreferencesManager(context) }
    val preferencesState by preferencesManager.preferencesFlow.collectAsState(initial = DosingPreferencesState())
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 页面标题
            Text(
                text = "系统设置",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            SettingsSection(
                title = "投料语音设置",
                items = listOf(
                    SettingsItem.Switch(
                        title = "启用语音重复播报",
                        subtitle = "按步骤自动重复当前材料",
                        icon = Icons.Default.VolumeUp,
                        isChecked = preferencesState.voiceRepeatEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { preferencesManager.setVoiceRepeatEnabled(enabled) }
                        }
                    ),
                    SettingsItem.Slider(
                        title = "重复次数",
                        subtitle = "设置每个材料的播报次数",
                        icon = Icons.Default.Repeat,
                        value = preferencesState.voiceRepeatCount.toFloat(),
                        onValueChange = { value ->
                            scope.launch { preferencesManager.setVoiceRepeatCount(value.roundToInt()) }
                        },
                        valueRange = DosingPreferencesManager.MIN_REPEAT_COUNT.toFloat()..DosingPreferencesManager.MAX_REPEAT_COUNT.toFloat(),
                        valueFormatter = { "${it.roundToInt()} 次" }
                    )
                )
            )
        }

        item {
            // 语音设置
            SettingsSection(
                title = "语音设置",
                items = listOf(
                    SettingsItem.Switch(
                        title = "启用语音播报",
                        subtitle = "开启材料投料语音提示",
                        icon = Icons.Default.Settings,
                        isChecked = true,
                        onCheckedChange = { }
                    ),
                    SettingsItem.Slider(
                        title = "语音速度",
                        subtitle = "调节语音播报速度",
                        icon = Icons.Default.Edit,
                        value = 0.8f,
                        onValueChange = { }
                    ),
                    SettingsItem.Slider(
                        title = "音量大小",
                        subtitle = "调节语音播报音量",
                        icon = Icons.Default.Settings,
                        value = 0.75f,
                        onValueChange = { }
                    )
                )
            )
        }

        item {
            // 界面设置
            SettingsSection(
                title = "界面设置",
                items = listOf(
                    SettingsItem.Switch(
                        title = "夜间模式",
                        subtitle = "适合低光环境使用",
                        icon = Icons.Default.Settings,
                        isChecked = false,
                        onCheckedChange = { }
                    ),
                    SettingsItem.Selection(
                        title = "字体大小",
                        subtitle = "调整界面文字大小",
                        icon = Icons.Default.Edit,
                        selectedValue = "标准",
                        options = listOf("小", "标准", "大", "特大")
                    ),
                    SettingsItem.Switch(
                        title = "震动反馈",
                        subtitle = "按钮点击时震动",
                        icon = Icons.Default.Phone,
                        isChecked = true,
                        onCheckedChange = { }
                    )
                )
            )
        }

        item {
            // 投料设置
            SettingsSection(
                title = "投料设置",
                items = listOf(
                    SettingsItem.Slider(
                        title = "精度要求",
                        subtitle = "投料重量精度要求（%）",
                        icon = Icons.Default.Settings,
                        value = 0.95f,
                        onValueChange = { },
                        valueRange = 0.85f..0.99f,
                        valueFormatter = { "${(it * 100).toInt()}%" }
                    ),
                    SettingsItem.Slider(
                        title = "超标允许浮动",
                        subtitle = "添加数量允许超过目标的百分比",
                        icon = Icons.Default.Warning,
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
                        icon = Icons.Default.PlayArrow,
                        isChecked = false,
                        onCheckedChange = { }
                    ),
                    SettingsItem.Selection(
                        title = "重量单位",
                        subtitle = "投料重量显示单位",
                        icon = Icons.Default.Build,
                        selectedValue = "公斤",
                        options = listOf("克", "公斤", "磅")
                    )
                )
            )
        }

        item {
            // 数据管理
            SettingsSection(
                title = "数据管理",
                items = listOf(
                    SettingsItem.Action(
                        title = "备份数据",
                        subtitle = "备份配方和投料记录",
                        icon = Icons.Default.Info,
                        onClick = { }
                    ),
                    SettingsItem.Action(
                        title = "恢复数据",
                        subtitle = "从备份文件恢复数据",
                        icon = Icons.Default.Add,
                        onClick = { }
                    ),
                    SettingsItem.Action(
                        title = "清除缓存",
                        subtitle = "清理应用缓存文件",
                        icon = Icons.Default.Delete,
                        onClick = { },
                        isDestructive = true
                    )
                )
            )
        }

        item {
            // 关于应用
            SettingsSection(
                title = "关于应用",
                items = listOf(
                    SettingsItem.Info(
                        title = "版本信息",
                        subtitle = "SmartDosing v1.0.0",
                        icon = Icons.Default.Info
                    ),
                    SettingsItem.Action(
                        title = "使用帮助",
                        subtitle = "查看操作指南和常见问题",
                        icon = Icons.Default.Info,
                        onClick = { }
                    ),
                    SettingsItem.Action(
                        title = "反馈建议",
                        subtitle = "提交使用反馈和改进建议",
                        icon = Icons.Default.Send,
                        onClick = { }
                    )
                )
            )
        }
    }
}

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
 * 设置分区组件
 */
@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            items.forEachIndexed { index, item ->
                SettingsItemView(item = item)
                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
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
                options = item.options
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
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
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
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
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
            Text(
                text = valueFormatter(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
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
    options: List<String>
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
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
                imageVector = Icons.Default.ArrowForward,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ArrowForward,
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
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun SettingsScreenPreview() {
    SmartDosingTheme {
        SettingsScreen()
    }
}
