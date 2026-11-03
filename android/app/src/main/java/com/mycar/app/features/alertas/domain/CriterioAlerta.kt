package com.mycar.app.features.alertas.domain

/** Criterios RF22. No usar String.contains("TIEMPO") — falla con AMBOS. */
object CriterioAlerta {
    fun usaTiempo(criterio: String): Boolean =
        criterio == "POR_TIEMPO" || criterio == "AMBOS"

    fun usaKm(criterio: String): Boolean =
        criterio == "POR_KILOMETRAJE" || criterio == "AMBOS"
}
