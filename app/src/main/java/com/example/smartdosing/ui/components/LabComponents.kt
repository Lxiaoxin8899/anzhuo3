package com.example.smartdosing.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartdosing.ui.theme.SmartDosingTokens
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.getResponsiveDimensions

/**
 * 实验室风格按钮 - Primary
 * 强调清晰、干练。高度适中 (40dp)，圆角更小。
 */
@Composable
fun LabButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp), // Standard Lab Height
        enabled = enabled,
        shape = RoundedCornerShape(SmartDosingTokens.radius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = SmartDosingTokens.spacing.lg, vertical = 0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp) // Flat style
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(SmartDosingTokens.spacing.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp)
        )
    }
}

/**
 * 实验室风格按钮 - Outlined
 * 用于次级操作，带细边框。
 */
@Composable
fun LabOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(SmartDosingTokens.radius.sm),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        contentPadding = PaddingValues(horizontal = SmartDosingTokens.spacing.lg)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(SmartDosingTokens.spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 实验室风格卡片
 * 极简边框，极淡阴影或无阴影，强调内容边界。
 * @param useResponsiveWidth 启用时自动根据屏幕尺寸限制最大宽度
 */
@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    useResponsiveWidth: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val windowSize = LocalWindowSize.current
    val responsiveDimensions = windowSize.getResponsiveDimensions()
    
    val responsiveModifier = if (useResponsiveWidth) {
        modifier
            .widthIn(max = responsiveDimensions.cardMaxWidth)
            .fillMaxWidth(responsiveDimensions.cardWidthFraction)
    } else {
        modifier
    }
    
    Surface(
        modifier = responsiveModifier,
        shape = RoundedCornerShape(SmartDosingTokens.radius.md),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp // Lab style prefers flat with borders
    ) {
        Column(modifier = Modifier.padding(SmartDosingTokens.spacing.md)) {
            content()
        }
    }
}

/**
 * 实验室风格输入框
 * 类似文档的输入体验，减少视觉干扰。
 */
@Composable
fun LabTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = SmartDosingTokens.spacing.xxs)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
            suffix = if (suffix != null) { { Text(suffix, style = MaterialTheme.typography.bodySmall) } } else null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), // Monospace for data
            singleLine = singleLine,
            isError = isError,
            shape = RoundedCornerShape(SmartDosingTokens.radius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = keyboardOptions
        )
    }
}

/**
 * 实验室数据展示组件
 * 专门用于显示 "123.45 g" 这种带单位的精确数值
 */
@Composable
fun DataValueDisplay(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    statusColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = statusColor
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 实验室区块标题
 */
@Composable
fun LabSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SmartDosingTokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(SmartDosingTokens.spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * 状态标签 Pills
 */
@Composable
fun LabStatusBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(SmartDosingTokens.radius.xs),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
