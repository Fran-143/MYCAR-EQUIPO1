package com.mycar.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formateo centralizado de fechas para UI MyCar (horario Perú).
 * El backend permanece en UTC; la conversión es solo de presentación.
 */
object FechaMyCar {
    val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")
    private val LOCALE_ES = Locale.forLanguageTag("es-PE")

    private val FMT_FECHA_HORA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy, h:mm a", LOCALE_ES)
    private val FMT_FECHA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_ES)
    private val FMT_HORA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", LOCALE_ES)

    fun fechaHora(isoUtc: String?): String {
        val instant = parse(isoUtc) ?: return "—"
        return FMT_FECHA_HORA.format(instant.atZone(ZONA_LIMA))
    }

    fun fecha(isoUtc: String?): String {
        val instant = parse(isoUtc) ?: return "—"
        return FMT_FECHA.format(instant.atZone(ZONA_LIMA))
    }

    fun hora(isoUtc: String?): String {
        val instant = parse(isoUtc) ?: return "—"
        return FMT_HORA.format(instant.atZone(ZONA_LIMA))
    }

    /** Texto aproximado para bloqueos RF03 (sin ISO ni segundos). */
    fun aproximadoRestante(hastaIsoUtc: String?): String {
        val hasta = parse(hastaIsoUtc) ?: return "más tarde"
        val secs = java.time.Duration.between(Instant.now(), hasta).seconds
        if (secs <= 0) return "unos momentos"
        val horas = (secs + 1799) / 3600 // redondeo hacia arriba por horas ~completas
        return when {
            horas <= 1 -> "aproximadamente 1 hora"
            horas <= 3 -> "aproximadamente 2 horas"
            horas <= 6 -> "aproximadamente 4 horas"
            else -> "aproximadamente 24 horas"
        }
    }

    private fun parse(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw.trim())
        } catch (_: Exception) {
            null
        }
    }
}
