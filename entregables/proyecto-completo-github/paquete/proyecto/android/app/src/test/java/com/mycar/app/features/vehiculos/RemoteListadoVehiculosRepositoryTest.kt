package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteListadoVehiculosRepositoryTest {
    private val vacio = ListadoVehiculosDto(emptyList(), ResumenEstadosDto(0, 0, 0), "TODOS", "")
    private class Api(var respuesta: Response<ListadoVehiculosDto>) : ListadoVehiculosApi {
        var enviado: Pair<String, String>? = null
        override suspend fun listar(filtro: String, busqueda: String): Response<ListadoVehiculosDto> {
            enviado = filtro to busqueda; return respuesta
        }
    }
    @Test fun listaVaciaEsUnaRespuestaRealYNoIncluyeUsuario() = runBlocking<Unit> {
        val api = Api(Response.success(vacio))
        assertEquals(vacio, RemoteListadoVehiculosRepository(api).listar("TODOS", ""))
        assertEquals("TODOS" to "", api.enviado)
    }
    @Test fun errorTecnicoNoSeConvierteEnListaVacia() = runBlocking<Unit> {
        val api = Api(Response.error(503, "{}".toResponseBody("application/json".toMediaType())))
        val e = fallo { RemoteListadoVehiculosRepository(api).listar("TODOS", "") }
        assertEquals(503, e.estadoHttp)
    }
    @Test fun reconoceEl401DeElsa() = runBlocking<Unit> {
        val api = Api(Response.error(401, "{\"codigo\":\"SESION_INVALIDA\"}".toResponseBody("application/json".toMediaType())))
        val e = fallo { RemoteListadoVehiculosRepository(api).listar("TODOS", "") }
        assertEquals(401, e.estadoHttp); assertEquals("SESION_INVALIDA", e.codigo)
    }
    @Test fun rechazaContadoresIncoherentes() = runBlocking<Unit> {
        val api = Api(Response.success(vacio.copy(resumen = ResumenEstadosDto(1, 0, 0))))
        assertEquals("RESPUESTA_INVALIDA", fallo { RemoteListadoVehiculosRepository(api).listar("TODOS", "") }.codigo)
    }
    @Test fun rechazaUnaRespuestaDeOtroFiltro() = runBlocking<Unit> {
        val api = Api(Response.success(vacio))
        assertEquals("RESPUESTA_INVALIDA", fallo { RemoteListadoVehiculosRepository(api).listar("ACTIVO", "") }.codigo)
    }
    @Test fun permiteKmAusenteSinInventarCero() = runBlocking<Unit> {
        val v = VehiculoResumenDto(1, "A1B234", "Toyota", "Corolla", 2020, "Particular", "Gris", "Eléctrico", null, "ACTIVO", true, null)
        val data = vacio.copy(vehiculos = listOf(v), resumen = ResumenEstadosDto(1, 1, 0))
        assertNull(RemoteListadoVehiculosRepository(Api(Response.success(data))).listar("TODOS", "").vehiculos.single().kilometrajeActual)
    }
    @Test fun filtrosSinCoincidenciasConservanContadores() = runBlocking<Unit> {
        val data = vacio.copy(resumen = ResumenEstadosDto(1, 1, 0), filtro = "INACTIVO", busqueda = "Honda")
        val r = RemoteListadoVehiculosRepository(Api(Response.success(data))).listar("INACTIVO", " Honda ")
        assertTrue(r.vehiculos.isEmpty()); assertEquals(1L, r.resumen.todos)
    }
    private suspend fun fallo(accion: suspend () -> Unit): FalloRegistroVehiculo {
        try { accion() } catch (e: FalloRegistroVehiculo) { return e }
        throw AssertionError("Se esperaba un error")
    }
}
