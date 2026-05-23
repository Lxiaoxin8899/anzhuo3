package com.example.smartdosing.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background

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
) {
    fun scaled(factor: Float): SmartDosingSpacing {
        return if (factor == 1f) this else copy(
            compact = compact * factor,
            xxs = xxs * factor,
            xs = xs * factor,
            sm = sm * factor,
            md = md * factor,
            lg = lg * factor,
            xl = xl * factor,
            xxl = xxl * factor,
            giant = giant * factor
        )
    }
}

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
 * Material3 基础色板不足以覆盖实验室语义，此处额外定义
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
        success = LabGreen,
        warning = LabOrange,
        danger = LabRed,
        info = LabBlue,
        neutral = Color(0xFF757575), // TextSecondary
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

/**
 * 极简精致金属流光扫光骨架屏 Modifier
 */
fun Modifier.shimmer(
    durationMillis: Int = 1200,
    shimmerWidth: Float = 600f
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -shimmerWidth,
        targetValue = 1200f + shimmerWidth,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.03f),
        Color.LightGray.copy(alpha = 0.15f),
        Color.LightGray.copy(alpha = 0.03f),
    )
    this.background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim, 0f),
            end = Offset(translateAnim + shimmerWidth, shimmerWidth)
        )
    )
}

/**
 * 深色称重面板的轻量外发光 Modifier，仅用于局部状态反馈
 */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 8.dp,
    intensity: Float = 0.16f,
    shapeRadius: Dp = 24.dp
): Modifier = this.drawBehind {
    if (intensity <= 0.01f) return@drawBehind
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        setShadowLayer(
            radius.toPx(),
            0f,
            0f,
            color.copy(alpha = intensity).toArgb()
        )
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            shapeRadius.toPx(), shapeRadius.toPx(),
            paint
        )
    }
}
