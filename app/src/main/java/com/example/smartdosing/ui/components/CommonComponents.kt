package com.example.smartdosing.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTokens

// ==================== 加载状态组件 ====================

/**
 * 统一的加载状态组件
 * @param message 加载提示文字
 * @param modifier Modifier
 */
@Composable
fun LoadingState(
    message: String = "正在加载...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(SmartDosingTokens.spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.lg)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 骨架屏加载效果 - 用于列表项
 */
@Composable
fun ShimmerLoadingItem(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnimation.value - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnimation.value, 0f)
    )

    LabCard(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        borderColor = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
        }
    }
}

/**
 * 骨架屏列表
 */
@Composable
fun ShimmerLoadingList(
    itemCount: Int = 5,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(SmartDosingTokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
    ) {
        repeat(itemCount) {
            ShimmerLoadingItem()
        }
    }
}

// ==================== 空状态组件 ====================

/**
 * 统一的空状态组件
 * @param icon 图标
 * @param title 标题
 * @param description 描述
 * @param actionLabel 操作按钮文字
 * @param onAction 操作回调
 */
@Composable
fun EmptyState(
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String = "暂无数据",
    description: String = "没有找到相关内容",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SmartDosingTokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.lg))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.xs))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.xl))
            LabButton(onClick = onAction, text = actionLabel)
        }
    }
}

/**
 * 搜索无结果状态
 */
@Composable
fun SearchEmptyState(
    searchText: String,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "未找到结果",
        description = "没有找到包含 \"$searchText\" 的内容，\n请尝试其他关键词",
        actionLabel = "清除搜索",
        onAction = onClearSearch,
        modifier = modifier
    )
}

// ==================== 错误状态组件 ====================

/**
 * 统一的错误状态组件
 * @param message 错误信息
 * @param onRetry 重试回调
 */
@Composable
fun ErrorState(
    message: String = "加载失败，请稍后重试",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SmartDosingTokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.lg))

        Text(
            text = "出错了",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.xs))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(SmartDosingTokens.spacing.xl))

            LabButton(
                onClick = onRetry,
                text = "重试",
                icon = Icons.Default.Refresh,
                containerColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ==================== 统计卡片组件 ====================

/**
 * 数据统计卡片
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    LabCard(
        modifier = modifier,
        backgroundColor = color.copy(alpha = 0.05f),
        borderColor = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 迷你统计标签
 */
@Composable
fun MiniStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SmartDosingTokens.radius.xl),
        color = color.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 状态标签组件 ====================

/**
 * 状态标签
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    // Redirect to LabStatusBadge logic but generic
    LabStatusBadge(text = text, color = color, modifier = modifier)
}

/**
 * 优先级标签
 */
@Composable
fun PriorityBadge(
    priority: String,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (priority.lowercase()) {
        "urgent", "紧急" -> MaterialTheme.colorScheme.error to Icons.Default.PriorityHigh
        "high", "高" -> MaterialTheme.colorScheme.tertiary to Icons.Default.ArrowUpward
        "normal", "普通", "标准" -> MaterialTheme.colorScheme.primary to Icons.Default.Remove
        else -> MaterialTheme.colorScheme.outline to Icons.Default.ArrowDownward
    }

    StatusBadge(
        text = priority,
        color = color,
        icon = icon,
        modifier = modifier
    )
}

// ==================== 确认对话框组件 ====================

/**
 * 确认对话框
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    icon: ImageVector? = Icons.Default.Warning,
    iconColor: Color = MaterialTheme.colorScheme.error,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            LabButton(
                onClick = onConfirm,
                text = confirmText,
                containerColor = iconColor
            )
        },
        dismissButton = {
            LabOutlinedButton(onClick = onDismiss, text = dismissText)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(SmartDosingTokens.radius.lg)
    )
}

// ==================== 进度指示器组件 ====================

/**
 * 步骤进度指示器
 */
@Composable
fun StepProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { step ->
            val isActive = step < currentStep
            val isCurrent = step == currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        when {
                            isActive -> activeColor
                            isCurrent -> activeColor.copy(alpha = 0.6f)
                            else -> inactiveColor
                        }
                    )
            )
        }
    }
}

/**
 * 圆形进度指示器（带百分比）
 */
@Composable
fun CircularProgressWithText(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = strokeWidth,
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ==================== 动画包装组件 ====================

/**
 * 淡入动画包装
 */
@Composable
fun FadeInAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(),
        modifier = modifier,
        content = content
    )
}

/**
 * 列表项动画包装
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 30L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(300)
                ),
        modifier = modifier
    ) {
        content()
    }
}

// ==================== 搜索栏组件 ====================

/**
 * 统一搜索栏
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索...",
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search,
    onClear: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear?.invoke() ?: onQueryChange("")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(SmartDosingTokens.radius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ==================== 分割线组件 ====================

/**
 * 带标题的分割线
 */
@Composable
fun SectionDivider(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SmartDosingTokens.spacing.md)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}
