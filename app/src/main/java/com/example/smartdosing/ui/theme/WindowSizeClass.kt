package com.example.smartdosing.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

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
 * 使用 Compose Configuration 计算宽/高分类，并衍生一个推荐的间距缩放比
 */
@Composable
fun rememberSmartWindowSize(): SmartDosingWindowSize {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val widthClass = when {
        widthDp >= 900 -> SmartDosingWindowWidthClass.Expanded
        widthDp >= 600 -> SmartDosingWindowWidthClass.Medium
        else -> SmartDosingWindowWidthClass.Compact
    }
    val heightClass = when {
        heightDp >= 900 -> SmartDosingWindowHeightClass.Expanded
        heightDp >= 600 -> SmartDosingWindowHeightClass.Medium
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
