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
            errores["fechaVencimiento"] = "El vencimiento debe ser posterior al inicio."
        }
        if (fechaEmision.isNotBlank() && parsearFecha(fechaEmision) == null) {
            errores["fechaEmision"] = "Fecha de emisión inválida."
        }
        if (importePrima.isNotBlank()) {
            val valor = importePrima.replace(",", ".").toDoubleOrNull()
            if (valor == null || valor < 0) errores["importePrima"] = "La prima debe ser un número ≥ 0."
        }
        return errores
    }

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