package com.mycar.app.features.perfil.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Static colors still used by cuenta/recuperación.
 * Perfil screens read [MaterialTheme.colorScheme] and the helpers below.
 */
internal object PerfilColors {
    val Navy = Color(0xFF12285F)
    val Teal = Color(0xFF007E98)
    val Bg = Color(0xFFF0F5FA)
    val SuccessBg = Color(0xFFE8F5E9)
    val SuccessText = Color(0xFF2E7D32)
    val Danger = Color(0xFFC62828)
    val Card = Color.White
    val Mute = Color(0xFF5A6B7D)
    val IconLock = Color(0xFF455A64)
    val IconLicense = Color(0xFF1565C0)
    val IconSupport = Color(0xFF00897B)
    val IconPlanes = Color(0xFFEF6C00)
    val IconKm = Color(0xFF5E35B1)
    val IconNotif = Color(0xFF0277BD)
    val IconLogout = Color(0xFF546E7A)
    val IconDanger = Color(0xFFC62828)
}

internal data class PerfilIconos(
    val lock: Color,
    val license: Color,
    val support: Color,
    val planes: Color,
    val km: Color,
    val notif: Color,
    val logout: Color,
    val danger: Color,
)

/** Same hues as the light palette, lifted when the surface is dark so icons stay readable. */
@Composable
internal fun perfilDanger(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) Color(0xFFFF8A80) else scheme.error
}

@Composable
internal fun perfilIconos(): PerfilIconos {
    val danger = perfilDanger()
    return if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        PerfilIconos(
            lock = Color(0xFFB0BEC5),
            license = Color(0xFF90CAF9),
            support = Color(0xFF80CBC4),
            planes = Color(0xFFFFB74D),
            km = Color(0xFFB39DDB),
            notif = Color(0xFF81D4FA),
            logout = Color(0xFFCFD8DC),
            danger = danger,
        )
    } else {
        PerfilIconos(
            lock = PerfilColors.IconLock,
            license = PerfilColors.IconLicense,
            support = PerfilColors.IconSupport,
            planes = PerfilColors.IconPlanes,
            km = PerfilColors.IconKm,
            notif = PerfilColors.IconNotif,
            logout = PerfilColors.IconLogout,
            danger = danger,
        )
    }
}

@Composable
internal fun perfilSuccessContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF1B3A28) else PerfilColors.SuccessBg

@Composable
internal fun perfilSuccessContent(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFA5D6A7) else PerfilColors.SuccessText

@Composable
internal fun perfilFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorCursorColor = MaterialTheme.colorScheme.error,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    errorSupportingTextColor = MaterialTheme.colorScheme.error,
)
