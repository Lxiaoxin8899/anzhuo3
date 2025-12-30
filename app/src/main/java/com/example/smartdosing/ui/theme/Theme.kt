package com.example.smartdosing.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.times
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// 实验室/研发终端配色方案
// 主色 - 科技蓝 (Scientific Blue) - 专业、冷静、精准
val ScientificBlue = Color(0xFF0066CC)
val ScientificBlueLight = Color(0xFFE6F0FF) // Tonal Palette 90
val ScientificBlueDark = Color(0xFF004C99)

// 辅助色 - 青色 (Teal/Cyan) - 用于数据可视化、高亮
val LabTeal = Color(0xFF00897B)
val LabTealLight = Color(0xFFE0F2F1)

// 背景色 - 纯净白/冷灰
val LabBackground = Color(0xFFF8F9FA) // 极淡的冷灰，减少眩光
val LabSurface = Color(0xFFFFFFFF)
val LabSurfaceVariant = Color(0xFFF0F2F5)

// 语义色
val LabSuccess = Color(0xFF2E7D32) // Keep standard green
val LabWarning = Color(0xFFED6C02) // Darker orange for better contrast on white
val LabError = Color(0xFFD32F2F)   // Standard red
val TextPrimary = Color(0xFF1A1C1E) // Nearly black
val TextSecondary = Color(0xFF424242) // Dark grey
val TextDisabled = Color(0xFF9E9E9E)

private val LightColorScheme = lightColorScheme(
    primary = ScientificBlue,
    onPrimary = Color.White,
    primaryContainer = ScientificBlueLight,
    onPrimaryContainer = ScientificBlueDark,

    secondary = LabTeal,
    onSecondary = Color.White,
    secondaryContainer = LabTealLight,
    onSecondaryContainer = Color(0xFF004D40),

    tertiary = LabWarning, // Using Warning color as Tertiary for accents

    background = LabBackground,
    onBackground = TextPrimary,

    surface = LabSurface,
    onSurface = TextPrimary,
    surfaceVariant = LabSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = LabError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF448AFF), // Lighter blue for dark mode
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004C99),
    onPrimaryContainer = Color(0xFFD1E4FF),

    secondary = Color(0xFF4DB6AC),
    onSecondary = Color.Black,

    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0BEC5),

    error = Color(0xFFEF5350)
)

private val DefaultSpacing = SmartDosingSpacing(
    none = 0.dp,
    compact = 4.dp,
    xxs = 2.dp,
    xs = 6.dp,
    sm = 10.dp,
    md = 16.dp,
    lg = 24.dp,
    xl = 32.dp,
    xxl = 48.dp,
    giant = 64.dp
)

private val DefaultRadius = SmartDosingRadius(
    none = 0.dp,
    xs = 2.dp,
    sm = 4.dp,
    md = 8.dp,
    lg = 12.dp,
    xl = 16.dp
)

private val DefaultElevation = SmartDosingElevation(
    level0 = 0.dp,
    level1 = 1.dp,
    level2 = 2.dp,
    level3 = 4.dp,
    level4 = 8.dp,
    level5 = 12.dp
)

private val LightExtendedColors = SmartDosingExtendedColors(
    success = LabSuccess,
    warning = LabWarning,
    danger = LabError,
    info = ScientificBlue,
    neutral = TextSecondary,
    border = Color(0xFFE0E0E0)
)

private val DarkExtendedColors = SmartDosingExtendedColors(
    success = Color(0xFF66BB6A),
    warning = Color(0xFFFFA726),
    danger = Color(0xFFEF5350),
    info = Color(0xFF42A5F5),
    neutral = Color(0xFFB0BEC5),
    border = Color(0xFF333333)
)

@Composable
fun SmartDosingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic to enforce Lab theme
    content: @Composable () -> Unit
) {
    val windowSize = rememberSmartWindowSize()
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                window.statusBarColor = colorScheme.primary.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme // Light status bar on light theme
        }
    }

    // Lab scaling might be more subtle than industrial
    val spacingScale = if (windowSize.widthClass == SmartDosingWindowWidthClass.Compact) 0.8f else 1.0f

    val adaptiveSpacing = DefaultSpacing // For now, keep spacing consistent, maybe scale later

    CompositionLocalProvider(
        LocalSpacing provides adaptiveSpacing,
        LocalRadius provides DefaultRadius,
        LocalElevation provides DefaultElevation,
        LocalExtendedColors provides extendedColors,
        LocalWindowSize provides windowSize
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
