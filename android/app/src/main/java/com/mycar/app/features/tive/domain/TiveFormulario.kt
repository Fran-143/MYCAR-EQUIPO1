package com.mycar.app.features.tive.domain

import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.tive.data.TiveDto
import com.mycar.app.features.vehiculos.domain.ValidadorVin
import java.util.Locale
import java.util.regex.Pattern

data class CamposTive(
    val placa: String = "",
    val numeroVin: String = "",
    val numeroTitulo: String = "",
    val codigoVerificacion: String = "",
    val fechaTitulo: String = "",
    val zonaRegistral: String = "",
    val sedeRegistral: String = "",
    val numeroMotor: String = "",
    val numeroSerie: String = ""
)

object TiveFormulario {
    private val MOTOR = Pattern.compile("^[A-Z0-9 -]{5,15}$")
    private val SERIE = Pattern.compile("^[A-Z0-9]{6,80}$")
    private val TITULO = Pattern.compile("^[A-Z0-9-]{1,32}$")
    private val CODIGO = Pattern.compile("^[A-Z0-9]{4,32}$")
    private val REGISTRAL = Pattern.compile("^[A-ZÁÉÍÓÚÑÜ0-9 .\\-]{1,80}$")

    const val MAX_DOC_BYTES = 10L * 1024 * 1024
    const val MAX_TITULO = 32
    const val MAX_CODIGO = 32
    const val MIN_CODIGO = 4

    const val PH_TITULO = "Ej.: TIV-458721"
    const val AYUDA_TITULO = "Ingresa el número que figura en tu TIVE."
    const val PH_CODIGO = "Ej.: ABCD1234"
    const val AYUDA_CODIGO = "Ingresa el código que aparece en la documentación TIVE."
    const val PH_MOTOR = "Ej.: 2ZRFE123456"
    const val PH_SERIE = "Ej.: ABC123456"
    const val PH_ZONA = "Ej.: LIMA"
    const val PH_SEDE = "Ej.: LIMA"
    const val PH_FECHA = "Selecciona una fecha"

    const val OK_TITULO = "Número de título con formato válido"
    const val OK_CODIGO = "Formato de código válido"

    fun desdeDto(t: TiveDto): CamposTive = CamposTive(
        placa = t.placa,
        numeroVin = t.numeroVin,
        numeroTitulo = t.numeroTitulo,
        codigoVerificacion = t.codigoVerificacion,
        fechaTitulo = SoatFormulario.fechaIsoAUi(t.fechaTitulo),
        zonaRegistral = t.zonaRegistral.orEmpty(),
        sedeRegistral = t.sedeRegistral.orEmpty(),
        numeroMotor = t.numeroMotor,
        numeroSerie = t.numeroSerie
    )

    fun validar(
        campos: CamposTive,
        editar: Boolean = false,
        tocados: Set<String> = emptySet(),
        exigirTodos: Boolean = true
    ): Map<String, String> {
        val e = mutableMapOf<String, String>()
        fun incluir(campo: String, mensaje: String?) {
            if (mensaje == null) return
            if (exigirTodos || campo in tocados) e[campo] = mensaje
        }

        incluir("numeroTitulo", errorTitulo(campos.numeroTitulo, exigirTodos || "numeroTitulo" in tocados))
        incluir("codigoVerificacion", errorCodigo(campos.codigoVerificacion, exigirTodos || "codigoVerificacion" in tocados))
        incluir("fechaTitulo", errorFecha(campos.fechaTitulo, exigirTodos || "fechaTitulo" in tocados))

        val zonaVacia = campos.zonaRegistral.isBlank()
        val sedeVacia = campos.sedeRegistral.isBlank()
        if (zonaVacia != sedeVacia) {
            val msg = "Zona y sede registral deben informarse juntas"
            incluir("zonaRegistral", msg)
            incluir("sedeRegistral", msg)
        } else if (!zonaVacia) {
            incluir("zonaRegistral", errorRegistral(campos.zonaRegistral, "zona"))
            incluir("sedeRegistral", errorRegistral(campos.sedeRegistral, "sede"))
        }

        incluir("numeroMotor", errorMotor(campos.numeroMotor, exigirTodos || "numeroMotor" in tocados))
        incluir("numeroSerie", errorSerie(campos.numeroSerie, exigirTodos || "numeroSerie" in tocados))

        if (!editar && campos.numeroVin.isNotBlank()) {
            ValidadorVin.mensajeError(campos.numeroVin, requerido = false)?.let {
                incluir("numeroVin", it)
            }
        }
        return e
    }

    fun hintsOk(campos: CamposTive, tocados: Set<String>): Map<String, String> {
        val h = mutableMapOf<String, String>()
        if ("numeroTitulo" in tocados && errorTitulo(campos.numeroTitulo, true) == null && campos.numeroTitulo.isNotBlank()) {
            h["numeroTitulo"] = OK_TITULO
        }
        if ("codigoVerificacion" in tocados && errorCodigo(campos.codigoVerificacion, true) == null && campos.codigoVerificacion.isNotBlank()) {
            h["codigoVerificacion"] = OK_CODIGO
        }
        return h
    }

    fun errorTitulo(raw: String, exigirVacio: Boolean): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.isEmpty()) return if (exigirVacio) "Ingresa el número de título" else null
        if (v.length > MAX_TITULO) return "El número de título no puede superar los 32 caracteres"
        if (raw.any { it.isWhitespace() && it != ' ' }) return "El número de título contiene caracteres no permitidos"
        if (!TITULO.matcher(v).matches()) return "El número de título contiene caracteres no permitidos"
        return null
    }

    fun errorCodigo(raw: String, exigirVacio: Boolean): String? {
        if (raw.isEmpty()) return if (exigirVacio) "Ingresa el código de verificación" else null
        if (raw.any { it.isWhitespace() }) return "El código de verificación no permite espacios"
        val v = raw.uppercase(Locale.ROOT)
        if (!v.all { it.isLetterOrDigit() }) return "El código solo permite letras y números"
        if (v.length < MIN_CODIGO) return "El código debe tener al menos 4 caracteres"
        if (v.length > MAX_CODIGO) return "El código no puede superar los 32 caracteres"
        if (!CODIGO.matcher(v).matches()) return "El código solo permite letras y números"
        return null
    }

    fun errorFecha(raw: String, exigirVacio: Boolean): String? {
        if (raw.isBlank()) return if (exigirVacio) "Selecciona la fecha del título" else null
        val fechaIso = SoatFormulario.fechaUiAIso(raw) ?: return "La fecha ingresada no es válida"
        val d = SoatFormulario.parsearFecha(fechaIso) ?: return "La fecha ingresada no es válida"
        if (d.isAfter(SoatFormulario.hoyLima())) return "La fecha no puede ser posterior a la fecha actual"
        return null
    }

    fun errorMotor(raw: String, exigirVacio: Boolean): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.isEmpty()) return if (exigirVacio) "Ingresa el número de motor" else null
        if (v.length !in 5..15) return "El número de motor debe tener entre 5 y 15 caracteres"
        if (!MOTOR.matcher(v).matches()) return "El número de motor contiene caracteres no permitidos"
        return null
    }

    fun errorSerie(raw: String, exigirVacio: Boolean): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.isEmpty()) return if (exigirVacio) "Ingresa el número de serie" else null
        if (v.length < 6) return "El número de serie debe tener al menos 6 caracteres"
        if (!SERIE.matcher(v).matches()) return "El número de serie solo permite letras y números"
        return null
    }

    private fun errorRegistral(raw: String, tipo: String): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.length > 80) return "La $tipo registral no puede superar los 80 caracteres"
        if (!REGISTRAL.matcher(v).matches()) return "La $tipo registral contiene caracteres no permitidos"
        return null
    }

    fun normalizarMotor(raw: String): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.isBlank() || v.length > 15) return null
        return v.takeIf { MOTOR.matcher(it).matches() }
    }

    fun normalizarSerie(raw: String): String? {
        val v = raw.trim().uppercase(Locale.ROOT)
        if (v.isBlank() || v.length > 80) return null
        return v.takeIf { SERIE.matcher(it).matches() }
    }

    fun normalizarTitulo(raw: String): String = raw.trim().uppercase(Locale.ROOT)
    fun normalizarCodigo(raw: String): String = raw.trim().uppercase(Locale.ROOT).replace(" ", "")

    fun validarAdjunto(contentType: String, tamano: Long): String? {
        val ct = contentType.lowercase(Locale.ROOT)
        if (ct !in setOf("image/jpeg", "image/png", "application/pdf")) {
            return "El archivo no tiene un formato permitido"
        }
        if (tamano <= 0 || tamano > MAX_DOC_BYTES) return "El archivo supera el tamaño máximo de 10 MB"
        return null
    }
}
