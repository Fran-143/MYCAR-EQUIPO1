package com.mycar.app.features.soat.domain

import com.mycar.app.features.soat.data.SoatDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object SoatFormulario {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    private val uiFecha = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")

    fun hoyLima(): LocalDate = LocalDate.now(ZONA_LIMA)

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

    /** Presentación larga (consultas). */
    fun formatearFecha(isoFecha: String?): String = runCatching {
        val d = parsearFecha(isoFecha) ?: return isoFecha ?: "—"
        d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "PE")))
    }.getOrDefault(isoFecha ?: "—")

    /** Campo editable DD/MM/AAAA. */
    fun fechaIsoAUi(isoFecha: String?): String {
        val d = parsearFecha(isoFecha) ?: return ""
        return d.format(uiFecha)
    }

    fun fechaUiAIso(fechaUiOIso: String?): String? =
        parsearFecha(fechaUiOIso)?.format(iso)

    fun fechaPrimerAviso(fechaVencimiento: String?): String? = runCatching {
        val v = parsearFecha(fechaVencimiento) ?: return null
        formatearFecha(v.minusDays(30).toString())
    }.getOrNull()

    const val MSG_VENCIMIENTO_POSTERIOR =
        "La fecha de vencimiento debe ser posterior a la fecha de inicio"
    const val MSG_VENCIMIENTO_PASADO =
        "No puedes registrar un SOAT con fecha de vencimiento pasada"
    const val MSG_FECHA_INVALIDA = "Ingresa una fecha válida"
    const val MSG_SOLAPE = "Esta vigencia se superpone con un SOAT registrado"
    const val MSG_PRIMA_INVALIDA = "El importe de la prima no es válido"
    const val MSG_ASEGURADORA = "Selecciona una aseguradora del catálogo"
    const val MSG_POLIZA = "Ingresa un número de póliza válido (ej. 012-4589)"
    const val MSG_RENOV_INICIO =
        "La nueva vigencia no puede empezar antes de que termine la actual"
    const val MSG_RENOV_VENC =
        "La nueva vigencia debe vencer después de la actual"

    /**
     * @param exigirVencimientoFuturo true en REGISTRO y RENOVACIÓN (cambio RN vs RF30-EA06 antiguo).
     *        false en CORREGIR para no bloquear edición de históricos ya vencidos.
     */
    fun validarCampos(
        aseguradora: String,
        numeroPoliza: String,
        fechaInicio: String,
        fechaVencimiento: String,
        fechaEmision: String,
        importePrima: String,
        exigirVencimientoFuturo: Boolean = true,
        hoy: LocalDate = hoyLima()
    ): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        if (!AseguradoraSoat.esValida(aseguradora)) {
            errores["aseguradora"] = if (aseguradora.isBlank()) "Indica la aseguradora." else MSG_ASEGURADORA
        }
        val polizaErr = validarPoliza(numeroPoliza)
        if (polizaErr != null) errores["numeroPoliza"] = polizaErr

        val inicio = parsearFecha(fechaInicio)
        val fin = parsearFecha(fechaVencimiento)
        if (fechaInicio.isNotBlank() && inicio == null) errores["fechaInicio"] = MSG_FECHA_INVALIDA
        if (fechaInicio.isBlank()) errores["fechaInicio"] = "Indica la fecha de inicio."
        if (fechaVencimiento.isNotBlank() && fin == null) errores["fechaVencimiento"] = MSG_FECHA_INVALIDA
        if (fechaVencimiento.isBlank()) errores["fechaVencimiento"] = "Indica la fecha de vencimiento."
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            errores["fechaVencimiento"] = MSG_VENCIMIENTO_POSTERIOR
        }
        if (exigirVencimientoFuturo && fin != null && fin.isBefore(hoy)) {
            errores["fechaVencimiento"] = MSG_VENCIMIENTO_PASADO
        }
        if (fechaEmision.isNotBlank() && parsearFecha(fechaEmision) == null) {
            errores["fechaEmision"] = MSG_FECHA_INVALIDA
        }
        if (importePrima.isNotBlank()) {
            val valor = importePrima.replace(",", ".").toDoubleOrNull()
            if (valor == null || valor < 0) errores["importePrima"] = MSG_PRIMA_INVALIDA
        }
        return errores
    }

    fun validarPoliza(raw: String): String? {
        val limpio = raw.trim()
        if (limpio.isEmpty()) return "Indica el número de póliza."
        // Formato ilustrativo SOAT.pdf: dígitos y guiones (conserva ceros a la izquierda).
        if (!limpio.matches(Regex("^[0-9]+(-[0-9]+)*$"))) {
            return MSG_POLIZA
        }
        if (limpio.length < 4 || limpio.length > 40) return MSG_POLIZA
        return null
    }

    fun normalizarPoliza(raw: String): String = raw.trim()

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
        parsearFecha(fechaInicioAnterior)
        return errores
    }

    fun hayCambiosRespectoA(
        actual: CamposSoat,
        editado: CamposSoat,
        modoDocumento: String
    ): Boolean {
        val mismosDatos =
            AseguradoraSoat.normalizar(actual.aseguradora) == AseguradoraSoat.normalizar(editado.aseguradora) &&
                normalizarPoliza(actual.numeroPoliza) == normalizarPoliza(editado.numeroPoliza) &&
                fechaUiAIso(actual.fechaInicio) == fechaUiAIso(editado.fechaInicio) &&
                fechaUiAIso(actual.fechaVencimiento) == fechaUiAIso(editado.fechaVencimiento) &&
                fechaUiAIso(actual.fechaEmision).orEmpty() == fechaUiAIso(editado.fechaEmision).orEmpty() &&
                actual.nombreContratante.trim().uppercase() == editado.nombreContratante.trim().uppercase() &&
                normalizarPrima(actual.importePrima) == normalizarPrima(editado.importePrima)
        if (!mismosDatos) return true
        return modoDocumento == "REEMPLAZAR" || modoDocumento == "QUITAR" || modoDocumento == "ELIMINAR"
    }

    private fun normalizarPrima(texto: String): String =
        texto.trim().replace(",", ".").trimStart('0').ifBlank { "" }

    /** Acepta ISO (yyyy-MM-dd) o UI (dd/MM/yyyy / dígitos DDMMAAAA). */
    fun parsearFecha(texto: String?): LocalDate? {
        if (texto.isNullOrBlank()) return null
        val t = texto.trim()
        runCatching { return LocalDate.parse(t, iso) }
        runCatching { return LocalDate.parse(t, uiFecha) }
        val digitos = t.filter { it.isDigit() }
        if (digitos.length == 8) {
            val dd = digitos.substring(0, 2).toInt()
            val mm = digitos.substring(2, 4).toInt()
            val yyyy = digitos.substring(4, 8).toInt()
            return runCatching { LocalDate.of(yyyy, mm, dd) }.getOrNull()
        }
        return null
    }

    /** Máscara de entrada: solo dígitos → DD/MM/AAAA (máx. 8 dígitos). */
    fun aplicarMascaraFecha(entrada: String): String {
        val d = entrada.filter { it.isDigit() }.take(8)
        return buildString {
            d.forEachIndexed { i, c ->
                append(c)
                if (i == 1 || i == 3) append('/')
            }
        }
    }

    fun vencimientoYaPasado(fechaVencimiento: String, hoy: LocalDate = hoyLima()): Boolean {
        val venc = parsearFecha(fechaVencimiento) ?: return false
        return hoy.isAfter(venc)
    }

    fun desdeSoat(soat: SoatDto): CamposSoat = CamposSoat(
        aseguradora = AseguradoraSoat.porCodigo(soat.aseguradora)?.nombre ?: soat.aseguradora,
        numeroPoliza = soat.numeroPoliza,
        fechaInicio = fechaIsoAUi(soat.fechaInicio),
        fechaVencimiento = fechaIsoAUi(soat.fechaVencimiento),
        fechaEmision = fechaIsoAUi(soat.fechaEmision),
        nombreContratante = soat.nombreContratante.orEmpty(),
        importePrima = soat.importePrima?.toString().orEmpty(),
        versionFila = soat.versionFila
    )

    fun aMayusculasConservandoCursor(texto: String): String =
        texto.uppercase(Locale("es", "PE"))
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
