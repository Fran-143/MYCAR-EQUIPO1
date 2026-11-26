package com.mycar.app.features.planes.data

import com.google.gson.Gson
import com.mycar.app.features.localtest.LocalTestClient
import com.mycar.app.features.planes.domain.ErrorPlanes
import com.mycar.app.features.planes.domain.PlanesRepository
import retrofit2.Response

class RemotePlanesRepository(private val api: PlanesApi) : PlanesRepository {
    private val gson = Gson()

    override suspend fun catalogo(): CatalogoPlanesDto =
        leer(api.catalogo()) { it ?: CatalogoPlanesDto() }

    override suspend fun miSuscripcion(): MiSuscripcionDto =
        leer(api.miSuscripcion()) { it ?: throw ErrorPlanes("VACIO", "Sin suscripción.") }

    override suspend fun eventos(): List<EventoSuscripcionDto> =
        leer(api.misEventos()) { it?.items.orEmpty() }

    override suspend fun solicitudes(): List<SolicitudSuscripcionDto> =
        leer(api.misSolicitudes()) { it?.items.orEmpty() }

    override suspend fun solicitudDetalle(codigo: String): SolicitudDetalleDto =
        leer(api.solicitudDetalle(codigo)) { it ?: throw ErrorPlanes("VACIO", "Sin solicitud.") }

    override suspend fun solicitarPruebaGratuita(): SolicitudSuscripcionDto =
        leer(api.solicitarPruebaGratuita()) { it ?: throw ErrorPlanes("VACIO", "Sin solicitud.") }

    override suspend fun solicitarPagoSimulado(): SolicitudSuscripcionDto =
        leer(api.solicitarPagoSimulado()) { it ?: throw ErrorPlanes("VACIO", "Sin solicitud.") }

    override suspend fun cancelarPremium(): CancelarPremiumResponse =
        leer(api.cancelarPremium()) { it ?: throw ErrorPlanes("VACIO", "Sin respuesta de cancelación.") }

    override suspend fun crearPagoSimulador(): OperacionPagoDto =
        leer(
            api.crearPago(LocalTestClient.QA_TOKEN, CrearPagoBody(proveedor = "SIMULADOR_LOCAL"))
        ) { it ?: throw ErrorPlanes("VACIO", "Sin operación.") }

    override suspend fun simular(referencia: String, escenario: String): OperacionPagoDto =
        leer(
            api.simular(LocalTestClient.QA_TOKEN, referencia, SimularPagoBody(escenario))
        ) { it ?: throw ErrorPlanes("VACIO", "Sin operación.") }

    override suspend fun consultar(referencia: String): OperacionPagoDto =
        leer(api.consultarPago(LocalTestClient.QA_TOKEN, referencia)) {
            it ?: throw ErrorPlanes("VACIO", "Sin operación.")
        }

    private fun <T, R> leer(response: Response<T>, map: (T?) -> R): R {
        if (response.isSuccessful) return map(response.body())
        val raw = response.errorBody()?.string().orEmpty()
        val err = runCatching { gson.fromJson(raw, ApiErrorPlanes::class.java) }.getOrNull()
        throw ErrorPlanes(
            err?.codigo ?: "HTTP_${response.code()}",
            err?.mensaje ?: "Error al consultar planes (${response.code()}).",
            err?.reintentable == true
        )
    }
}
