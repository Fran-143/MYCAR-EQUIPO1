package com.mycar.app.features.planes.domain

import com.mycar.app.features.planes.data.CatalogoPlanesDto
import com.mycar.app.features.planes.data.EventoSuscripcionDto
import com.mycar.app.features.planes.data.MiSuscripcionDto
import com.mycar.app.features.planes.data.OperacionPagoDto
import com.mycar.app.features.planes.data.SolicitudDetalleDto
import com.mycar.app.features.planes.data.SolicitudSuscripcionDto

interface PlanesRepository {
    suspend fun catalogo(): CatalogoPlanesDto
    suspend fun miSuscripcion(): MiSuscripcionDto
    suspend fun eventos(): List<EventoSuscripcionDto>
    suspend fun solicitudes(): List<SolicitudSuscripcionDto>
    suspend fun solicitudDetalle(codigo: String): SolicitudDetalleDto
    suspend fun solicitarPruebaGratuita(): SolicitudSuscripcionDto
    suspend fun solicitarPagoSimulado(): SolicitudSuscripcionDto
    suspend fun crearPagoSimulador(): OperacionPagoDto
    suspend fun simular(referencia: String, escenario: String): OperacionPagoDto
    suspend fun consultar(referencia: String): OperacionPagoDto
}

class ErrorPlanes(
    val codigo: String,
    override val message: String,
    val reintentable: Boolean = false
) : Exception(message)
