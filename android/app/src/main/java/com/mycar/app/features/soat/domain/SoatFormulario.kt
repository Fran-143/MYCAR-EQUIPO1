package com.mycar.app.features.soat.domain

import com.mycar.app.features.soat.data.SoatDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object SoatFormulario {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun etiquetaCobertura(codigo: String): String = when (codigo) {
        "POR_INICIAR" -> "Por iniciar"
        "VIGENTE" -> "Vigente"
        "VENCIDA" -> "Vencida"
        else -> codigo
    }

    fun colorCobertura(codigo: String): Long = when (codigo) {
        "VIGENTE" -> 0xFF2E7D32
        "POR_INICIAR" -> 0xFF1565C0
        "VENCIDA" -> 0xFFC62828
        else -> 0xFF546E7A
    }

    fun formatearFecha(isoFecha: String?): String = runCatching {
        val d = LocalDate.parse(isoFecha)
        d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "PE")))
    }.getOrDefault(isoFecha ?: "—")

    /** Primer aviso RF32: 30 días antes del vencimiento. */
    fun fechaPrimerAviso(fechaVencimientoIso: String?): String? = runCatching {
        val v = LocalDate.parse(fechaVencimientoIso!!.trim(), iso)
        formatearFecha(v.minusDays(30).toString())
    }.getOrNull()

    const val MSG_VENCIMIENTO_POSTERIOR =
        "La fecha de vencimiento debe ser posterior a la fecha de inicio"
    const val MSG_PRIMA_INVALIDA = "El importe de la prima no es válido"
    const val MSG_RENOV_INICIO =
        "La nueva vigencia no puede empezar antes de que termine la actual"
    const val MSG_RENOV_VENC =
        "La nueva vigencia debe vencer después de la actual"

    fun validarCampos(
        aseguradora: String,
        numeroPoliza: String,
        fechaInicio: String,
        fechaVencimiento: String,
        fechaEmision: String,
        importePrima: String
    ): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (aseguradora.isBlank()) errores["aseguradora"] = "Indica la aseguradora."
        if (numeroPoliza.isBlank()) errores["numeroPoliza"] = "Indica el número de póliza."
        val inicio = parsearFecha(fechaInicio)
        val fin = parsearFecha(fechaVencimiento)
        if (inicio == null) errores["fechaInicio"] = "Fecha de inicio inválida (AAAA-MM-DD)."
        if (fin == null) errores["fechaVencimiento"] = "Fecha de vencimiento inválida (AAAA-MM-DD)."
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            errores["fechaVencimiento"] = MSG_VENCIMIENTO_POSTERIOR
        }
        if (fechaEmision.isNotBlank() && parsearFecha(fechaEmision) == null) {
            errores["fechaEmision"] = "Fecha de emisión inválida."
        }
        if (importePrima.isNotBlank()) {
            val valor = importePrima.replace(",", ".").toDoubleOrNull()
            if (valor == null || valor < 0) errores["importePrima"] = MSG_PRIMA_INVALIDA
        }
        return errores
    }

    /** RF31-EA01 / EA04 sobre la vigencia actual. */
    fun validarFechasRenovacion(
        fechaInicioNueva: String,
        fechaVencimientoNueva: String,
        fechaInicioAnterior: String,
        fechaVencimientoAnterior: String
    ): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        val inicioN = parsearFecha(fechaInicioNueva)
        val vencN = parsearFecha(fechaVencimientoNueva)
        val vencA = parsearFecha(fechaVencimientoAnterior)
        if (inicioN != null && vencA != null && !inicioN.isAfter(vencA)) {
            errores["fechaInicio"] = MSG_RENOV_INICIO
        }
        if (vencN != null && vencA != null && !vencN.isAfter(vencA)) {
            errores["fechaVencimiento"] = MSG_RENOV_VENC
        }
        // fechaInicioAnterior se valida solo para coherencia de parseo
        parsearFecha(fechaInicioAnterior)
        return errores
    }

    fun hayCambiosRespectoA(
        actual: CamposSoat,
        editado: CamposSoat,
        modoDocumento: String
    ): Boolean {
        val mismosDatos =
            actual.aseguradora.trim() == editado.aseguradora.trim() &&
                actual.numeroPoliza.trim() == editado.numeroPoliza.trim() &&
                actual.fechaInicio.trim() == editado.fechaInicio.trim() &&
                actual.fechaVencimiento.trim() == editado.fechaVencimiento.trim() &&
                actual.fechaEmision.trim() == editado.fechaEmision.trim() &&
                actual.nombreContratante.trim() == editado.nombreContratante.trim() &&
                normalizarPrima(actual.importePrima) == normalizarPrima(editado.importePrima)
        if (!mismosDatos) return true
        return modoDocumento == "REEMPLAZAR" || modoDocumento == "QUITAR" || modoDocumento == "ELIMINAR"
    }

    private fun normalizarPrima(texto: String): String =
        texto.trim().replace(",", ".").trimStart('0').ifBlank { "" }

    fun parsearFecha(texto: String): LocalDate? = runCatching {
        LocalDate.parse(texto.trim(), iso)
    }.getOrNull()

    fun desdeSoat(soat: SoatDto): CamposSoat = CamposSoat(
        aseguradora = soat.aseguradora,
        numeroPoliza = soat.numeroPoliza,
        fechaInicio = soat.fechaInicio,
        fechaVencimiento = soat.fechaVencimiento,
        fechaEmision = soat.fechaEmision.orEmpty(),
        nombreContratante = soat.nombreContratante.orEmpty(),
        importePrima = soat.importePrima?.toString().orEmpty(),
        versionFila = soat.versionFila
    )
}

data class CamposSoat(
    val aseguradora: String = "",
    val numeroPoliza: String = "",
    val fechaInicio: String = "",
    val fechaVencimiento: String = "",
    val fechaEmision: String = "",
    val nombreContratante: String = "",
    val importePrima: String = "",
    val versionFila: Int? = null
)

const val SOAT_MAX_IMAGEN_BYTES = 5L * 1024 * 1024
const val SOAT_MAX_PDF_BYTES = 10L * 1024 * 1024