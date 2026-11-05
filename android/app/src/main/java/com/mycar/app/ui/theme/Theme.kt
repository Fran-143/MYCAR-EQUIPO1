package com.mycar.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MyCarLightScheme = lightColorScheme(
    primary = MyCarBlue,
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

@Composable
fun MyCarTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Estética clara MyCar (PDF/mocks); dark/dynamic desactivados en este lote.
    val colorScheme = MyCarLightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MyCarBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
