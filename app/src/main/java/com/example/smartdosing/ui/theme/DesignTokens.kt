package com.example.smartdosing.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SmartDosing 设计系统 - 间距定义
 * 统一的 spacing 便于页面保持相同的密度
 */
/**
 * SmartDosing 设计系统 - 间距定义 (实验室版 - 更紧凑)
 */
data class SmartDosingSpacing(
    val none: Dp = 0.dp,
    val compact: Dp = 4.dp, // New for lab
    val xxs: Dp = 2.dp,
    val xs: Dp = 6.dp,     // Adjusted
    val sm: Dp = 10.dp,    // Adjusted
    val md: Dp = 16.dp,    // Adjusted
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val giant: Dp = 64.dp
)

/**
 * SmartDosing 设计系统 - 圆角规范 (实验室版 - 更精致)
 */
data class SmartDosingRadius(
    val none: Dp = 0.dp,
    val xs: Dp = 2.dp,    // Tiny
    val sm: Dp = 4.dp,    // Small, standard for inputs
    val md: Dp = 8.dp,    // Medium, for cards
    val lg: Dp = 12.dp,   // Large, for dialogs
    val xl: Dp = 16.dp    // Extra large
)

/**
 * SmartDosing 设计系统 - 阴影/海拔高度 (实验室版 - 更扁平)
 */
data class SmartDosingElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,  // Subtle border/shadow
    val level2: Dp = 2.dp,  // Card hover
    val level3: Dp = 4.dp,  // Dropdown
    val level4: Dp = 8.dp,  // Dialog
    val level5: Dp = 12.dp
)

/**
 * SmartDosing 设计系统 - 扩展语义色
 * Material3 基础色板不足以覆盖工控语义，此处额外定义
 */
data class SmartDosingExtendedColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
    val border: Color
)

// CompositionLocal 默认值，便于主题注入
internal val LocalSpacing = staticCompositionLocalOf { SmartDosingSpacing() }
internal val LocalRadius = staticCompositionLocalOf { SmartDosingRadius() }
internal val LocalElevation = staticCompositionLocalOf { SmartDosingElevation() }
internal val LocalExtendedColors = staticCompositionLocalOf {
    SmartDosingExtendedColors(
        success = IndustrialGreen,
        warning = IndustrialOrange,
        danger = IndustrialRed,
        info = IndustrialBlue,
        neutral = TextSecondary,
        border = Color(0xFFE0E0E0)
    )
}

/**
 * 对外暴露的 Token 访问入口，调用方可以 SmartDosingTokens.spacing 方式获取
 */
object SmartDosingTokens {
    val spacing: SmartDosingSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val radius: SmartDosingRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalRadius.current

    val elevation: SmartDosingElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalElevation.current

    val colors: SmartDosingExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
