package com.mycar.app.features.vehiculos.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Entrada y formato de kilometraje: máximo 9,999,999.99, hasta 2 decimales, solo dígitos. */
object KilometrajeTexto {
    val MAXIMO: BigDecimal = BigDecimal("9999999.99")

    private val formatoVista = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
    })

    /** Filtra letras y limita a 7 dígitos enteros + opcional '.' + hasta 2 decimales. */
    fun filtrarEntrada(crudo: String): String {
        val limpio = buildString(crudo.length) {
            var punto = false
            var enteros = 0
            var decimales = 0
            for (c in crudo) {
                when {
                    c in '0'..'9' -> {
                        if (!punto) {
                            if (enteros >= 7) continue
                            append(c)
                            enteros++
                        } else {
                            if (decimales >= 2) continue
                            append(c)
                            decimales++
                        }
                    }
                    (c == '.' || c == ',') && !punto -> {
                        if (enteros == 0) continue
                        append('.')
                        punto = true
                    }
                }
            }
        }
        return limpio
    }

    fun parsear(texto: String): BigDecimal? {
        val t = texto.trim().replace(',', '.')
        if (t.isEmpty() || t == ".") return null
        return try {
            BigDecimal(t)
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Completa enteros con .00 y deja como máximo 2 decimales. */
    fun normalizarConCeros(texto: String): String? {
        val valor = parsear(texto) ?: return null
        if (valor.scale() > 2 && valor.stripTrailingZeros().scale() > 2) return null
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    fun esValido(texto: String): Boolean {
        val valor = parsear(normalizarConCeros(texto) ?: return false) ?: return false
        return valor.compareTo(BigDecimal.ZERO) > 0 && valor.compareTo(MAXIMO) <= 0
    }

    fun aDouble(texto: String): Double? =
        normalizarConCeros(texto)?.let { parsear(it)?.toDouble() }

    fun formatear(valor: Number?): String {
        if (valor == null) return ""
        val bd = when (valor) {
            is BigDecimal -> valor
            else -> BigDecimal.valueOf(valor.toDouble())
        }.setScale(2, RoundingMode.HALF_UP)
        return formatoVista.format(bd)
    }

    fun mensajeError(texto: String, minimoExclusivo: Number? = null): String? {
        val normalizado = normalizarConCeros(texto)
            ?: return "Ingresa solo números, con hasta 2 decimales."
        val valor = parsear(normalizado) ?: return "Ingresa solo números, con hasta 2 decimales."
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            return "Ingresa un kilometraje mayor que cero."
        }
        if (valor.compareTo(MAXIMO) > 0) {
            return "El máximo permitido es 9 999 999.99 km."
        }
        if (minimoExclusivo != null) {
            val minimo = BigDecimal.valueOf(minimoExclusivo.toDouble())
            if (valor.compareTo(minimo) <= 0) {
                return "Debe ser mayor que ${formatear(minimo)} km."
            }
        }
        return null
    }
}
