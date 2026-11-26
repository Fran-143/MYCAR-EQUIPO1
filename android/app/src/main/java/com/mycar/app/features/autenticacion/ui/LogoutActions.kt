package com.mycar.app.features.autenticacion.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Cierre de sesión confirmado (local-first) expuesto a Perfil RF06. */
val LocalConfirmedLogout = staticCompositionLocalOf<(() -> Unit)?> { null }
