package com.mycar.app.features.licencia.ui

import androidx.compose.runtime.Composable
import com.mycar.app.features.licencia.viewmodel.AdjuntoLicencia
import com.mycar.app.features.soat.ui.SoatSeccionAdjunto
import com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi

/** Cámara, galería (JPG/PNG) o PDF — misma UX que SOAT. */
@Composable
fun LicenciaSeccionAdjunto(
    lado: String,
    adjunto: AdjuntoLicencia?,
    error: String?,
    enabled: Boolean,
    onAdjunto: (AdjuntoLicencia?) -> Unit
) {
    val etiqueta = if (lado == "FRONTAL") "Documento frontal" else "Documento posterior"
    SoatSeccionAdjunto(
        adjunto = adjunto?.toSoat(),
        error = error,
        enabled = enabled,
        onAdjunto = { onAdjunto(it?.toLicencia()) },
        estiloPdf = true,
        etiqueta = etiqueta,
        subtituloVacio = "Cámara, galería (JPG/PNG) o PDF"
    )
}

private fun AdjuntoLicencia.toSoat() =
    AdjuntoSoatUi(uri = uri, nombre = nombre, contentType = contentType, tamanoBytes = tamano)

private fun AdjuntoSoatUi.toLicencia() =
    AdjuntoLicencia(uri = uri, nombre = nombre, contentType = contentType, tamano = tamanoBytes)
