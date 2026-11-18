package com.mycar.app.features.soporte.domain

import com.mycar.app.features.tive.domain.TiveFormulario
import com.mycar.app.features.vehiculos.domain.ValidadorVin
import java.util.Locale

object SoporteFormulario {
    const val MAX_MOTIVO = 250
    const val PH_MOTIVO = "Ej.: ERROR DE DIGITACIÓN AL REGISTRAR EL VIN."
    const val AYUDA_EVIDENCIA = "Adjunta una imagen o PDF que respalde tu solicitud."

    fun validarNueva(
        vinPropuesto: String,
        vinActual: String?,
        motivo: String,
        tocados: Set<String> = emptySet(),
        exigirTodos: Boolean = true
    ): Map<String, String> {
        val e = mutableMapOf<String, String>()
        fun incluir(campo: String, mensaje: String?) {
            if (mensaje == null) return
            if (exigirTodos || campo in tocados) e[campo] = mensaje
        }
        incluir(
            "vinPropuesto",
            ValidadorVin.mensajeError(vinPropuesto, requerido = exigirTodos || "vinPropuesto" in tocados, vinActual = vinActual)
        )
        incluir("motivo", errorMotivo(motivo, exigirTodos || "motivo" in tocados))
        return e
    }

    fun errorMotivo(raw: String, exigirVacio: Boolean): String? {
        val m = raw.trim()
        if (m.isEmpty()) return if (exigirVacio) "Describe el motivo de la corrección" else null
        if (m.length > MAX_MOTIVO) return "El motivo no puede superar los 250 caracteres"
        return null
    }

    fun normalizarMotivo(raw: String): String =
        raw.trim().uppercase(Locale.forLanguageTag("es-PE")).take(MAX_MOTIVO)

    fun contadorMotivo(raw: String): String = "${raw.length.coerceAtMost(MAX_MOTIVO + 50)}/$MAX_MOTIVO"

    fun validarEvidencia(contentType: String, tamano: Long): String? =
        TiveFormulario.validarAdjunto(contentType, tamano)

    fun mensajeEvidenciaAusente(): String = "Debes adjuntar una evidencia" // legado; evidencia ahora es opcional

    fun etiquetaEstado(estado: String): String = when (estado.uppercase(Locale.ROOT)) {
        "PENDIENTE" -> "Pendiente"
        "EN_REVISION", "EN REVISIÓN" -> "En revisión"
        "APROBADA" -> "Aprobada"
        "RECHAZADA" -> "Rechazada"
        else -> estado
    }

    fun esPdf(contentType: String?, nombre: String?): Boolean {
        val ct = contentType?.lowercase(Locale.ROOT).orEmpty()
        if (ct.contains("pdf")) return true
        return nombre?.lowercase(Locale.ROOT)?.endsWith(".pdf") == true
    }
}
