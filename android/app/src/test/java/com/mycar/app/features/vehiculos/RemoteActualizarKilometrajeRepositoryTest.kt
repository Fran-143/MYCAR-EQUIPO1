package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.util.UUID

class RemoteActualizarKilometrajeRepositoryTest {
    private class Api(
        var ctx: Response<ContextoKilometrajeDto>,
        var post: Response<ResultadoActualizarKmDto>? = null
    ) : KilometrajeApi {
        override suspend fun contexto(idVehiculo: Long) = ctx
        override suspend fun listarLecturas(idVehiculo: Long) =
            Response.success(emptyList<LecturaKilometrajeDto>())
        override suspend fun registrar(idVehiculo: Long, solicitud: SolicitudActualizarKmDto) =
            post ?: Response.success(ResultadoActualizarKmDto(UUID.randomUUID(), idVehiculo, 1, solicitud.kmNuevo, null, "CONFIRMADA"))
        override suspend fun operacion(idOperacion: UUID) =
            Response.success(EstadoOperacionKmDto(idOperacion, "EN_PROCESO", null))
        override suspend fun contextoCorreccion(idVehiculo: Long, idLectura: Long) =
            Response.success(
                ContextoCorreccionKmDto(
                    idVehiculo, "ABC", "M", "X", idLectura, 1.0, 2.0, 1, true, null
                )
            )
        override suspend fun corregir(
            idVehiculo: Long,
            idLectura: Long,
            solicitud: SolicitudCorregirKmDto
        ) = Response.success(
            ResultadoCorregirKmDto(
                solicitud.idOperacion, idVehiculo, idLectura, 1, 2.0, 1.0, 1.0,
                solicitud.motivo, null, null, "CONFIRMADA"
            )
        )
        override suspend fun operacionCorreccion(idOperacion: UUID) =
            Response.success(EstadoOperacionCorreccionKmDto(idOperacion, "EN_PROCESO", null))
    }

    @Test fun contextoValido() = runBlocking {
        val dto = ContextoKilometrajeDto(2, 9, 50000, null, 0, "ACTIVO")
        val r = RemoteActualizarKilometrajeRepository(Api(Response.success(dto))).contexto(2)
        assertEquals(50000, r.kmActual)
    }

    @Test fun kmObsoletoPropaga409() = runBlocking {
        val cuerpo = """{"codigo":"KM_OBSOLETO","mensaje":"x","campos":[]}"""
        val e = assertThrows(FalloRegistroVehiculo::class.java) {
            runBlocking {
                RemoteActualizarKilometrajeRepository(
                    Api(Response.success(ContextoKilometrajeDto(1, 1, 1, null, 0, "ACTIVO")),
                        Response.error(409, cuerpo.toResponseBody(null)))
                ).registrar(1, SolicitudActualizarKmDto(UUID.randomUUID(), 2, 1, 0))
            }
        }
        assertEquals("KM_OBSOLETO", e.codigo)
    }
}
