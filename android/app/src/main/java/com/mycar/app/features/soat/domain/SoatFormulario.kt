package com.mycar.app.features.soat.domain

import com.mycar.app.features.soat.data.SoatDto
import java.math.BigDecimal
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
    const val MSG_PRIMA_INVALIDA = "El importe de la prima no es válido (máx. 999999.00)"
    const val MSG_ASEGURADORA = "Selecciona una aseguradora del catálogo"
    const val MSG_POLIZA =
        "Ingresa un número de póliza o certificado válido (letras, números y separadores)"
    const val MSG_POLIZA_VACIA = "Indica el número de póliza."
    const val MSG_FECHA_MENOR_HOY = "La fecha no puede ser menor a la fecha actual"
    const val MSG_VIGENCIA_ANUAL =
        "El SOAT ordinario debe tener vigencia anual (aniversario calendario)"
    const val MSG_RENOV_INICIO =
        "La nueva vigencia no puede empezar antes de que termine la actual"
    const val MSG_RENOV_VENC =
        "La nueva vigencia debe vencer después de la actual"
    /** Límite técnico alineado a mycar.soat.numero_poliza VARCHAR(80). */
    const val MAX_CHARS_POLIZA = 80
    private val MAX_PRIMA = BigDecimal("999999.00")

    /**
     * @param exigirVencimientoFuturo true en REGISTRO y RENOVACIÓN (decisión MyCar vs RF30-EA06 antiguo).
     *        false en CORREGIR para no bloquear edición de históricos ya vencidos.
     * @param exigirCatalogoActivo true en alta/renovación; false en corregir (admite históricos).
     */
    fun validarCampos(
        aseguradora: String,
        numeroPoliza: String,
        fechaInicio: String,
        fechaVencimiento: String,
        fechaEmision: String,
        importePrima: String,
        exigirVencimientoFuturo: Boolean = true,
        exigirCatalogoActivo: Boolean = true,
        hoy: LocalDate = hoyLima()
    ): Map<String, String> {
        val errores = mutableMapOf<String, String>()
        val asegOk = if (exigirCatalogoActivo) {
            AseguradoraSoat.esValidaParaAlta(aseguradora)
        } else {
            AseguradoraSoat.esReconocida(aseguradora)
        }
        if (!asegOk) {
            errores["aseguradora"] = if (aseguradora.isBlank()) "Indica la aseguradora." else MSG_ASEGURADORA
        }
        val polizaErr = validarPoliza(numeroPoliza, aseguradora)
        if (polizaErr != null) errores["numeroPoliza"] = polizaErr

        val inicio = parsearFecha(fechaInicio)
        val fin = parsearFecha(fechaVencimiento)
        if (fechaInicio.isNotBlank() && inicio == null) errores["fechaInicio"] = MSG_FECHA_INVALIDA
        if (fechaInicio.isBlank()) errores["fechaInicio"] = "Indica la fecha de inicio."
        if (fechaVencimiento.isNotBlank() && fin == null) errores["fechaVencimiento"] = MSG_FECHA_INVALIDA
        if (fechaVencimiento.isBlank()) errores["fechaVencimiento"] = "Indica la fecha de vencimiento."
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            errores["fechaVencimiento"] = MSG_VENCIMIENTO_POSTERIOR
        } else if (inicio != null && fin != null && !esVigenciaAnualOrdinaria(inicio, fin)) {
            errores["fechaVencimiento"] = MSG_VIGENCIA_ANUAL
        }
        if (exigirVencimientoFuturo && fin != null && fin.isBefore(hoy)) {
            errores["fechaVencimiento"] = MSG_FECHA_MENOR_HOY
        }
        if (fechaEmision.isNotBlank() && parsearFecha(fechaEmision) == null) {
            errores["fechaEmision"] = MSG_FECHA_INVALIDA
        }
        if (importePrima.isNotBlank()) {
            val primaErr = validarPrima(importePrima)
            if (primaErr != null) errores["importePrima"] = primaErr
        }
        return errores
    }

    /**
     * Validación de formato (no autenticidad oficial).
     * Perfil por aseguradora solo aporta ayuda/límites documentados; sin evidencia → reglas generales.
     */
    fun validarPoliza(raw: String, aseguradora: String? = null): String? {
        val limpio = raw.trim()
        if (limpio.isEmpty()) return MSG_POLIZA_VACIA
        if (limpio.codePointCount(0, limpio.length) > MAX_CHARS_POLIZA) {
            return "El número de póliza supera el límite permitido"
        }
        if (limpio.any { Character.isISOControl(it) }) return MSG_POLIZA
        // Letras, dígitos y separadores razonables de certificados; sin emojis ni símbolos raros.
        if (!limpio.matches(Regex("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9][A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9\\-/. ]*$"))) {
            return MSG_POLIZA
        }
        val perfil = AseguradoraSoat.perfilDe(aseguradora)
        val digitosYLetras = limpio.count { it.isLetterOrDigit() }
        if (digitosYLetras < 1) return MSG_POLIZA
        perfil?.longitudMinDocumentada?.let { min ->
            if (digitosYLetras < min) return MSG_POLIZA
        }
        perfil?.longitudMaxDocumentada?.let { max ->
            if (digitosYLetras > max) return MSG_POLIZA
        }
        return null
    }

    /** Filtra a caracteres admitidos; no impone longitud fija ni agrega guiones. */
    fun limitarPoliza(raw: String): String {
        val out = StringBuilder()
        for (ch in raw) {
            if (out.codePointCount(0, out.length) >= MAX_CHARS_POLIZA) break
            when {
                ch.isLetterOrDigit() -> out.append(ch.uppercaseChar())
                ch == '-' || ch == '/' || ch == '.' || ch == ' ' -> {
                    if (out.isNotEmpty() && out.last() != ch) out.append(ch)
                }
            }
        }
        return out.toString().trimEnd()
    }

    /** Valor para guardar/mostrar (String; conserva ceros). */
    fun normalizarPoliza(raw: String): String = limitarPoliza(raw.trim())

    /** Clave de comparación (mayúsculas, sin acentos superfluos en letras). */
    fun normalizarPolizaParaComparar(raw: String): String =
        AseguradoraSoat.normalizar(normalizarPoliza(raw)).orEmpty()

    fun validarPrima(raw: String): String? {
        val t = raw.trim().replace(',', '.')
        if (t.isEmpty()) return null
        if (!t.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return MSG_PRIMA_INVALIDA
        val valor = runCatching { BigDecimal(t) }.getOrNull() ?: return MSG_PRIMA_INVALIDA
        if (valor.signum() < 0 || valor > MAX_PRIMA) return MSG_PRIMA_INVALIDA
        if (valor.scale() > 2) return MSG_PRIMA_INVALIDA
        return null
    }

    /** Entero hasta 6 cifras + hasta 2 decimales (máx 999999.00). */
    fun limitarPrima(raw: String): String {
        val limpio = raw.replace(',', '.')
        val out = StringBuilder()
        var punto = false
        var enteros = 0
        var decimales = 0
        for (ch in limpio) {
            when {
                ch.isDigit() && !punto -> {
                    if (enteros >= 6) continue
                    out.append(ch)
                    enteros++
                }
                ch.isDigit() && punto -> {
                    if (decimales >= 2) continue
                    out.append(ch)
                    decimales++
                }
                ch == '.' && !punto -> {
                    out.append('.')
                    punto = true
                }
            }
        }
        return out.toString()
    }

    /**
     * SOAT ordinario: aniversario calendario (LocalDate.plusYears), no 365 días fijos.
     * Acepta fin = inicio+1 año o el día anterior (último día incluido del certificado).
     * Placas de exhibición/rotativas/extranjeras: sin flujo aprobado → se aplica regla ordinaria.
     */
    fun esVigenciaAnualOrdinaria(inicio: LocalDate, fin: LocalDate): Boolean {
        if (!fin.isAfter(inicio)) return false
        val aniversario = inicio.plusYears(1)
        val ultimoDiaIncluido = aniversario.minusDays(1)
        return fin == aniversario || fin == ultimoDiaIncluido
    }

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
                normalizarPolizaParaComparar(actual.numeroPoliza) ==
                normalizarPolizaParaComparar(editado.numeroPoliza) &&
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

    /**
     * Máscara DD/MM/AAAA. El `/` solo aparece si hay dígitos después del grupo,
     * para poder borrar más allá de `11/11/` sin que el slash se regenerue.
     */
    fun aplicarMascaraFecha(entrada: String): String {
        val d = entrada.filter { it.isDigit() }.take(8)
        return buildString {
            d.forEachIndexed { i, c ->
                append(c)
                if ((i == 1 && d.length > 2) || (i == 3 && d.length > 4)) append('/')
            }
        }
    }

    fun digitosPoliza(raw: String): String = raw.filter { it.isDigit() }

    fun vencimientoYaPasado(fechaVencimiento: String, hoy: LocalDate = hoyLima()): Boolean {
        val venc = parsearFecha(fechaVencimiento) ?: return false
        return hoy.isAfter(venc)
    }

    /**
     * Renovar solo a partir del día de vencimiento (hoy >= vencimiento, Lima).
     * Antes de esa fecha el SOAT sigue vigente y no se renueva.
     */
    fun puedeRenovar(fechaVencimiento: String?, hoy: LocalDate = hoyLima()): Boolean {
        val venc = parsearFecha(fechaVencimiento) ?: return false
        return !hoy.isBefore(venc)
    }

    const val MSG_RENOVAR_TEMPRANO =
        "Solo puedes renovar el SOAT a partir de su fecha de vencimiento"

    fun desdeSoat(soat: SoatDto): CamposSoat = CamposSoat(
        aseguradora = AseguradoraSoat.porCodigo(soat.aseguradora)?.nombre ?: soat.aseguradora,
        numeroPoliza = soat.numeroPoliza,
        fechaInicio = fechaIsoAUi(soat.fechaInicio),
        fechaVencimiento = fechaIsoAUi(soat.fechaVencimiento),
        fechaEmision = fechaIsoAUi(soat.fechaEmision),
        nombreContratante = soat.nombreContratante.orEmpty(),
        importePrima = soat.importePrima?.let { v ->
            BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
        }.orEmpty(),
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
