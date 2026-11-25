package com.mycar.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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
    background = MyCarBg,
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
    onPrimary = Color.White,
    primaryContainer = MyCarTileDark,
    onPrimaryContainer = MyCarOnSurfaceDark,
    secondary = MyCarTeal,
    onSecondary = Color.White,
    secondaryContainer = MyCarTileDark,
    onSecondaryContainer = MyCarOnSurfaceDark,
    tertiary = MyCarBlue,
    onTertiary = Color.White,
    background = MyCarBgDark,
    onBackground = MyCarOnSurfaceDark,
    surface = MyCarSurfaceDark,
    onSurface = MyCarOnSurfaceDark,
    surfaceVariant = MyCarFieldDark,
    onSurfaceVariant = MyCarMutedDark,
    outline = MyCarOutlineDark,
    outlineVariant = MyCarOutlineSoftDark,
    error = MyCarStatusVencida,
    onError = Color.White,
    inverseSurface = MyCarOnSurfaceDark,
    inverseOnSurface = MyCarBgDark
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
