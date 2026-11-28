package com.mycar.app.features.alertas.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Conversión fecha base ↔ objetivo / intervalo (compartido RF17 recomendación y RF22 programar).
 * Preferimos SEMANAS/MESES si coinciden exactamente; si no, DIAS.
 */
object FechaObjetivoAlerta {
    fun fechaObjetivoDesde(base: LocalDate, unidad: String, cantidad: Int): LocalDate = when (unidad) {
        "SEMANAS" -> base.plusWeeks(cantidad.toLong())
        "MESES" -> base.plusMonths(cantidad.toLong())
        else -> base.plusDays(cantidad.toLong()) // DIAS
    }

    fun unidadCantidadDesdeFechas(base: LocalDate, objetivo: LocalDate): Pair<String, Int> {
        val dias = ChronoUnit.DAYS.between(base, objetivo).toInt()
        require(dias > 0) { "La fecha objetivo debe ser posterior a la fecha base." }
        if (dias % 7 == 0) return "SEMANAS" to (dias / 7)
        var meses = 1
        while (meses <= 120) {
            val cand = base.plusMonths(meses.toLong())
            when {
                cand == objetivo -> return "MESES" to meses
                cand.isAfter(objetivo) -> break
            }
            meses++
        }
        return "DIAS" to dias
    }
}
