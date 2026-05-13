package com.apexmark.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 亮色方案 — 源自图标深蓝主色 + 冷灰中性色。
 * 整体清爽、专业，蓝色渐变贯穿品牌感。
 */
private val LightScheme = lightColorScheme(
    primary = Apex500,
    onPrimary = Neutral0,
    primaryContainer = Apex100,
    onPrimaryContainer = Apex800,
    inversePrimary = Apex300,

    secondary = Teal500,
    onSecondary = Neutral0,
    secondaryContainer = Teal300.copy(alpha = 0.2f),
    onSecondaryContainer = Teal600,

    tertiary = Amber500,
    onTertiary = Neutral0,
    tertiaryContainer = Amber400.copy(alpha = 0.2f),
    onTertiaryContainer = Amber600,

    background = Neutral50,
    onBackground = Neutral900,

    surface = Neutral0,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral600,

    surfaceTint = Apex500,
    inverseSurface = Neutral800,
    inverseOnSurface = Neutral100,

    outline = Neutral300,
    outlineVariant = Neutral200,

    error = Error,
    onError = Neutral0,
    errorContainer = ErrorLight,
    onErrorContainer = Error,

    scrim = Neutral900.copy(alpha = 0.32f)
)

/**
 * 暗色方案 — 深蓝灰底色（非纯黑），保留品牌色温。
 * 表面色有微妙的蓝调倾向，与图标色系统一。
 */
private val DarkScheme = darkColorScheme(
    primary = Apex400,
    onPrimary = Apex900,
    primaryContainer = Apex700,
    onPrimaryContainer = Apex100,
    inversePrimary = Apex600,

    secondary = Teal300,
    onSecondary = Teal600,
    secondaryContainer = Teal600.copy(alpha = 0.3f),
    onSecondaryContainer = Teal300,

    tertiary = Amber400,
    onTertiary = Neutral900,
    tertiaryContainer = Amber600.copy(alpha = 0.3f),
    onTertiaryContainer = Amber400,

    background = DarkSurface0,
    onBackground = DarkOnSurface,

    surface = DarkSurface1,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = Neutral400,

    surfaceTint = Apex400,
    inverseSurface = Neutral200,
    inverseOnSurface = Neutral800,

    outline = DarkSurface4,
    outlineVariant = DarkSurface3,

    error = Error,
    onError = Neutral0,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = Error,

    scrim = DarkSurface0.copy(alpha = 0.6f)
)

/**
 * ApexMark 全局主题。
 *
 * 主题模式优先级：用户偏好 > 系统设置。
 * Android 12+ 支持动态取色但默认关闭，优先使用品牌色板。
 */
@Composable
fun ApexMarkTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeMode by ThemePreference.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = ApexShapes,
        content = content
    )
}
