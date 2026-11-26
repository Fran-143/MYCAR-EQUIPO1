package com.mycar.app.features.planes.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface PlanesApi {
    @GET("api/v1/planes")
    suspend fun catalogo(): Response<CatalogoPlanesDto>

    @GET("api/v1/suscripciones/mia")
    suspend fun miSuscripcion(): Response<MiSuscripcionDto>

    @GET("api/v1/suscripciones/mia/eventos")
    suspend fun misEventos(): Response<EventosPageDto>

    @GET("api/v1/suscripciones/mia/solicitudes")
    suspend fun misSolicitudes(): Response<SolicitudesPageDto>

    @GET("api/v1/suscripciones/mia/solicitudes/{codigo}")
    suspend fun solicitudDetalle(@Path("codigo") codigo: String): Response<SolicitudDetalleDto>

    @POST("api/v1/suscripciones/solicitudes/prueba-gratuita")
    suspend fun solicitarPruebaGratuita(): Response<SolicitudSuscripcionDto>

    @POST("api/v1/suscripciones/solicitudes/pago-simulado")
    suspend fun solicitarPagoSimulado(): Response<SolicitudSuscripcionDto>

    @POST("api/v1/suscripciones/mia/cancelar")
    suspend fun cancelarPremium(): Response<CancelarPremiumResponse>

    @POST("api/v1/local-test/pagos-prueba")
    suspend fun crearPago(
        @Header("X-MyCar-Local-Test") token: String,
        @Body body: CrearPagoBody
    ): Response<OperacionPagoDto>

    @POST("api/v1/local-test/pagos-prueba/{referencia}/simular")
    suspend fun simular(
        @Header("X-MyCar-Local-Test") token: String,
        @Path("referencia") referencia: String,
        @Body body: SimularPagoBody
    ): Response<OperacionPagoDto>

    @GET("api/v1/local-test/pagos-prueba/{referencia}")
    suspend fun consultarPago(
        @Header("X-MyCar-Local-Test") token: String,
        @Path("referencia") referencia: String
    ): Response<OperacionPagoDto>
}
