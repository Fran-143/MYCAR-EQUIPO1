package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.DetalleVehiculoApi
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.data.RemoteDetalleVehiculosRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

class RemoteDetalleVehiculosRepositoryTest {
    private class Api(var respuesta: Response<DetalleVehiculoDto>) : DetalleVehiculoApi {
        override suspend fun consultar(idVehiculo: Long) = respuesta
    }

    private val dto = DetalleVehiculoDto(
        3, "A1B234", "Toyota", "Corolla", 2020, "Particular", "Gris",
        "Combustión", "Gasolina", "ACTIVO", true, 50000, null,
        null, "SERIE", "MOTOR", null, false
    )

    @Test fun consultaExitosa() = runBlocking {
        val r = RemoteDetalleVehiculosRepository(Api(Response.success(dto))).consultar(3)
        assertEquals("A1B234", r.placa)
    }

    @Test fun idDistintoEsInvalido() = runBlocking {
        val e = assertThrows(FalloRegistroVehiculo::class.java) {
            runBlocking {
                RemoteDetalleVehiculosRepository(Api(Response.success(dto.copy(idVehiculo = 99)))).consultar(3)
            }
        }
        assertEquals("RESPUESTA_INVALIDA", e.codigo)
    }

    @Test fun noEncontradoPropagaCodigo() = runBlocking {
        val cuerpo = """{"codigo":"VEHICULO_NO_ENCONTRADO","mensaje":"x","campos":[]}"""
        val e = assertThrows(FalloRegistroVehiculo::class.java) {
            runBlocking {
                RemoteDetalleVehiculosRepository(
                    Api(Response.error(404, cuerpo.toResponseBody(null)))
                ).consultar(3)
            }
        }
        assertEquals("VEHICULO_NO_ENCONTRADO", e.codigo)
        assertEquals(404, e.estadoHttp)
    }
}
