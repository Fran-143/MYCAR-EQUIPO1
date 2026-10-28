package com.mycar.app.features.historial.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/** Exclusiva de debug: diseño interactivo con ejemplos identificados, sin sesión, red ni persistencia. */
class HistorialPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Box(Modifier.fillMaxSize().safeDrawingPadding()) { VistaHistorial(EjemplosHistorial.listo, onCerrar = ::finish) } }
    }
}
