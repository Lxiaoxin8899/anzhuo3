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
data class SmartDosingSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val giant: Dp = 48.dp
)

/**
 * SmartDosing 设计系统 - 圆角规范
 */
data class SmartDosingRadius(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp
)

/**
 * SmartDosing 设计系统 - 阴影/海拔高度
 */
data class SmartDosingElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 10.dp,
    val level5: Dp = 14.dp
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
