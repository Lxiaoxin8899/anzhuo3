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

private val DarkColorScheme = darkColorScheme(
    primary = IndustrialBlueLight,
    secondary = IndustrialGreen,
    tertiary = IndustrialOrange,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IndustrialBlue,
    secondary = IndustrialGreen,
    tertiary = IndustrialOrange,
    background = IndustrialBackground,
    surface = IndustrialSurface,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = IndustrialSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = IndustrialRed
)

private val DefaultSpacing = SmartDosingSpacing(
    xxs = 2.dp,
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp,
    xxl = 32.dp,
    giant = 48.dp
)

private val DefaultRadius = SmartDosingRadius(
    xs = 4.dp,
    sm = 8.dp,
    md = 12.dp,
    lg = 16.dp,
    xl = 24.dp
)

private val DefaultElevation = SmartDosingElevation(
    level0 = 0.dp,
    level1 = 1.dp,
    level2 = 3.dp,
    level3 = 6.dp,
    level4 = 10.dp,
    level5 = 14.dp
)

private val LightExtendedColors = SmartDosingExtendedColors(
    success = IndustrialGreen,
    warning = IndustrialOrange,
    danger = IndustrialRed,
    info = IndustrialBlue,
    neutral = TextSecondary,
    border = Color(0xFFE0E0E0)
)

private val DarkExtendedColors = SmartDosingExtendedColors(
    success = IndustrialGreen.copy(alpha = 0.9f),
    warning = IndustrialOrange.copy(alpha = 0.9f),
    danger = IndustrialRed.copy(alpha = 0.9f),
    info = IndustrialBlueLight,
    neutral = Color(0xFFB0BEC5),
    border = Color(0xFF2C2C2C)
)

@Composable
fun SmartDosingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color to enforce industrial theme
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    val spacingScale = windowSize.spacingScale
    val adaptiveSpacing = SmartDosingSpacing(
        none = DefaultSpacing.none,
        xxs = DefaultSpacing.xxs * spacingScale,
        xs = DefaultSpacing.xs * spacingScale,
        sm = DefaultSpacing.sm * spacingScale,
        md = DefaultSpacing.md * spacingScale,
        lg = DefaultSpacing.lg * spacingScale,
        xl = DefaultSpacing.xl * spacingScale,
        xxl = DefaultSpacing.xxl * spacingScale,
        giant = DefaultSpacing.giant * spacingScale
    )

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
