package com.mycar.app.features.vehiculos.domain

import java.util.Locale
import java.util.regex.Pattern

/**
 * Validación de VIN alineada con RF08 ([ValidadorFormularioRegistro] / backend).
 * Devuelve un único mensaje específico por prioridad; no elimina caracteres en silencio.
 */
object ValidadorVin {
    private val FORMATO = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$")
    private val SOLO_ALFANUM = Pattern.compile("^[A-Z0-9]*$")

    const val LONGITUD = 17
    const val PLACEHOLDER = "Ej.: 1HGCM82633A004352"
    const val AYUDA = "17 caracteres, sin I, O ni Q."
    const val MSG_FORMATO_OK = "Formato de VIN válido"
    const val MSG_VACIO = "Ingresa el número de VIN"
    const val MSG_CORTO = "El VIN debe tener 17 caracteres. Actualmente tienes %d de 17"
    const val MSG_LARGO = "El VIN no puede superar los 17 caracteres"
    const val MSG_IOQ = "El VIN no permite las letras I, O ni Q"
    const val MSG_ESPACIOS = "El VIN no puede contener espacios"
    const val MSG_SIMBOLOS = "El VIN solo permite letras y números"
    const val MSG_DUPLICADO = "Este VIN ya está registrado en otro vehículo"
    const val MSG_IGUAL = "El VIN solicitado debe ser diferente del VIN actual"

    fun normalizarVisual(raw: String): String = raw.uppercase(Locale.ROOT)

    fun esFormatoValido(raw: String): Boolean =
        FORMATO.matcher(raw.trim().uppercase(Locale.ROOT)).matches()

    /**
     * @param requerido si true, vacío es error; si false, vacío es OK (RF08 opcional).
     * @param vinActual si no null, exige diferencia (corrección VIN).
     * @return mensaje de error, o null si el formato es aceptable.
     */
    fun mensajeError(
        raw: String,
        requerido: Boolean = true,
        vinActual: String? = null
    ): String? {
        if (raw.isEmpty()) {
            return if (requerido) MSG_VACIO else null
        }
        if (raw.any { it.isWhitespace() }) return MSG_ESPACIOS
        val upper = raw.uppercase(Locale.ROOT)
        if (!SOLO_ALFANUM.matcher(upper).matches()) return MSG_SIMBOLOS
        if (upper.any { it == 'I' || it == 'O' || it == 'Q' }) return MSG_IOQ
        if (upper.length < LONGITUD) return MSG_CORTO.format(upper.length)
        if (upper.length > LONGITUD) return MSG_LARGO
        if (!FORMATO.matcher(upper).matches()) return MSG_SIMBOLOS
        val actual = vinActual?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (actual != null && upper == actual) return MSG_IGUAL
        return null
    }

    fun mensajeFormatoOk(raw: String, vinActual: String? = null): String? {
        if (mensajeError(raw, requerido = true, vinActual = vinActual) != null) return null
        return if (esFormatoValido(raw)) MSG_FORMATO_OK else null
    }

    fun contador(raw: String): String = "${raw.length.coerceAtMost(99)}/$LONGITUD"
}
