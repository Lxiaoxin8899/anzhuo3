package com.example.smartdosing.ui.theme

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * 设备宽度分类，统一用于判断当前是手机、折叠屏还是平板
 */
enum class SmartDosingWindowWidthClass {
    Compact,
    Medium,
    Expanded
}

/**
 * 设备高度分类，用于决定垂直方向的安全间距策略
 */
enum class SmartDosingWindowHeightClass {
    Compact,
    Medium,
    Expanded
}

/**
 * 窗口尺寸描述对象，既包含分类也保留了原始 DP 值和推荐的间距缩放
 */
data class SmartDosingWindowSize(
    val widthClass: SmartDosingWindowWidthClass,
    val heightClass: SmartDosingWindowHeightClass,
    val widthDp: Int,
    val heightDp: Int,
    val spacingScale: Float
)

/**
 * 响应式尺寸配置，根据屏幕尺寸返回适合的组件尺寸
 */
data class ResponsiveDimensions(
    val buttonMinHeight: Dp,      // 按钮最小高度
    val cardMaxWidth: Dp,         // 卡片最大宽度
    val dialogMaxWidth: Dp,       // 对话框最大宽度
    val keypadButtonSize: Dp,     // 数字键盘按钮尺寸
    val cardWidthFraction: Float  // 卡片占屏幕宽度比例
)

/**
 * 根据窗口尺寸获取响应式尺寸配置
 */
fun SmartDosingWindowSize.getResponsiveDimensions(): ResponsiveDimensions {
    return when (widthClass) {
        SmartDosingWindowWidthClass.Compact -> ResponsiveDimensions(
            buttonMinHeight = 48.dp,   // 增大到 48dp 更易点击
            cardMaxWidth = 360.dp,     // 适应手机宽度
            dialogMaxWidth = 320.dp,
            keypadButtonSize = 52.dp,
            cardWidthFraction = 0.95f
        )
        SmartDosingWindowWidthClass.Medium -> ResponsiveDimensions(
            buttonMinHeight = 44.dp,
            cardMaxWidth = 480.dp,
            dialogMaxWidth = 420.dp,
            keypadButtonSize = 56.dp,
            cardWidthFraction = 0.85f
        )
        SmartDosingWindowWidthClass.Expanded -> ResponsiveDimensions(
            buttonMinHeight = 40.dp,
            cardMaxWidth = 560.dp,
            dialogMaxWidth = 500.dp,
            keypadButtonSize = 64.dp,
            cardWidthFraction = 0.7f
        )
    }
}

/**
 * 判断当前是否为紧凑(手机)布局
 */
val SmartDosingWindowSize.isCompact: Boolean
    get() = widthClass == SmartDosingWindowWidthClass.Compact

val LocalWindowSize = staticCompositionLocalOf {
    SmartDosingWindowSize(
        widthClass = SmartDosingWindowWidthClass.Compact,
        heightClass = SmartDosingWindowHeightClass.Compact,
        widthDp = 360,
        heightDp = 640,
        spacingScale = 0.85f
    )
}

/**
 * 按当前窗口（而非整块屏幕，分屏/自由窗口下更准确）计算宽/高分类，并衍生推荐的间距缩放比。
 * 断点沿用 Material 官方窗口尺寸类：宽度 600 / 840dp，高度 480 / 900dp。
 */
@Composable
fun rememberSmartWindowSize(): SmartDosingWindowSize {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val widthClass = when (sizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.EXPANDED -> SmartDosingWindowWidthClass.Expanded
        WindowWidthSizeClass.MEDIUM -> SmartDosingWindowWidthClass.Medium
        else -> SmartDosingWindowWidthClass.Compact
    }
    val heightClass = when (sizeClass.windowHeightSizeClass) {
        WindowHeightSizeClass.EXPANDED -> SmartDosingWindowHeightClass.Expanded
        WindowHeightSizeClass.MEDIUM -> SmartDosingWindowHeightClass.Medium
        else -> SmartDosingWindowHeightClass.Compact
    }

    val spacingScale = when (widthClass) {
        SmartDosingWindowWidthClass.Compact -> 0.85f
        SmartDosingWindowWidthClass.Medium -> 0.95f
        SmartDosingWindowWidthClass.Expanded -> 1.0f
    }

    return SmartDosingWindowSize(
        widthClass = widthClass,
        heightClass = heightClass,
        widthDp = widthDp,
        heightDp = heightDp,
        spacingScale = spacingScale
    )
}
