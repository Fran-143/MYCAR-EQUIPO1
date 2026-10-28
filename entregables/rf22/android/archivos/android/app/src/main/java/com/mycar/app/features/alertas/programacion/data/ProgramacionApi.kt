package com.mycar.app.features.alertas.programacion.data

import com.mycar.app.features.alertas.programacion.domain.*
import retrofit2.Response
import retrofit2.http.*

/** Namespace RF22 implementado para revisión del equipo. Usa el Retrofit autenticado compartido. */
interface ProgramacionApi {
    @GET("api/v1/rf22/contexto") suspend fun contexto(@Header("Authorization") bearer: String): Response<ContextoRf22>
    @GET("api/v1/rf22/vehiculos/{id}/antecedentes")
    suspend fun antecedentes(@Header("Authorization") bearer: String, @Path("id") vehiculo: Long, @Query("idTipoMantenimiento") tipo: Long,
        @Query("servicioElegido") servicio: String?): Response<AntecedentesRf22>
    @POST("api/v1/rf22/revisiones") suspend fun revisar(@Header("Authorization") bearer: String, @Body configuracion: ConfiguracionRf22): Response<RevisionRf22>
    @POST("api/v1/rf22/alertas") suspend fun confirmar(@Header("Authorization") bearer: String, @Body solicitud: SolicitudRf22): Response<ResultadoRf22>
    @GET("api/v1/rf22/operaciones/{id}") suspend fun consultar(@Header("Authorization") bearer: String, @Path("id") id: String): Response<OperacionRf22>
}
