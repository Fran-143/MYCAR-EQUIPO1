package com.mycar.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MyCarLightScheme = lightColorScheme(
    primary = MyCarNavy,
    onPrimary = MyCarSurface,
    primaryContainer = MyCarTile,
    onPrimaryContainer = MyCarNavyDeep,
    secondary = MyCarTeal,
    onSecondary = MyCarSurface,
    secondaryContainer = MyCarTile,
    onSecondaryContainer = MyCarNavy,
    tertiary = MyCarSky,
    onTertiary = MyCarSurface,
    background = MyCarSurface,
    onBackground = MyCarOnSurface,
    surface = MyCarSurface,
    onSurface = MyCarOnSurface,
    surfaceVariant = MyCarBgSoft,
    onSurfaceVariant = MyCarMuted,
    outline = MyCarOutline,
    outlineVariant = MyCarOutlineSoft,
    error = MyCarStatusVencida,
    onError = MyCarSurface
)

private val MyCarDarkScheme = darkColorScheme(
    primary = MyCarSky,
    onPrimary = MyCarNavy,
    primaryContainer = MyCarTileDark,
    onPrimaryContainer = MyCarOnSurfaceDark,
    secondary = MyCarTeal,
    onSecondary = MyCarNavy,
    secondaryContainer = MyCarTileDark,
    onSecondaryContainer = MyCarOnSurfaceDark,
    tertiary = MyCarBlue,
    onTertiary = MyCarOnSurfaceDark,
    background = MyCarBgDark,
    onBackground = MyCarOnSurfaceDark,
    surface = MyCarSurfaceDark,
    onSurface = MyCarOnSurfaceDark,
    surfaceVariant = MyCarBgSoftDark,
    onSurfaceVariant = MyCarMutedDark,
    outline = MyCarOutlineDark,
    outlineVariant = MyCarOutlineSoftDark,
    error = MyCarStatusVencida,
    onError = MyCarOnSurfaceDark
)

@Composable
fun MyCarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MyCarDarkScheme else MyCarLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
