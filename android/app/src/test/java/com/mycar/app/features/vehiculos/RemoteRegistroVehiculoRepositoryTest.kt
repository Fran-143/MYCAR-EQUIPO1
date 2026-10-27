package com.mycar.app.features.vehiculos

import com.google.gson.Gson
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RemoteRegistroVehiculoRepositoryTest {
    private class ApiPrueba : RegistroVehiculoApi {
        var cupoRespuesta: Response<CupoRegistroDto> = Response.success(CupoRegistroDto("FREE", 1, 0, true))
        var catalogosRespuesta: Response<CatalogosRegistroDto> = Response.success(DatosPruebaRf08.catalogos)
        var peticion: SolicitudRegistroVehiculoDto? = null
        override suspend fun catalogos() = catalogosRespuesta
        override suspend fun cupo() = cupoRespuesta
        override suspend fun registrar(solicitud: SolicitudRegistroVehiculoDto): Response<ResultadoRegistroVehiculoDto> {
            peticion = solicitud; return Response.success(DatosPruebaRf08.resultado(solicitud))
        }
        override suspend fun operacion(idOperacion: String): Response<EstadoOperacionRegistroDto> =
            Response.success(EstadoOperacionRegistroDto(idOperacion, "EN_PROCESO", null))
    }
    @Test fun cupoRealSeDevuelveSinInventarDatos() = runBlocking<Unit> {
        assertEquals(0, RemoteRegistroVehiculoRepository(ApiPrueba()).cupo().registrados)
    }
    @Test fun errorNoJsonDeSesionSeConvierteEnSesionRequerida() = runBlocking<Unit> {
        val api = ApiPrueba().apply { cupoRespuesta = Response.error(401, "<html>Error</html>".toResponseBody("text/html".toMediaType())) }
        try { RemoteRegistroVehiculoRepository(api).cupo(); fail("Se esperaba error") }
        catch (error: FalloRegistroVehiculo) { assertEquals("SESION_NO_VALIDA", error.codigo); assertEquals(401, error.estadoHttp) }
    }
    @Test fun error503NuncaSeConvierteEnCupoDisponible() = runBlocking<Unit> {
        val api = ApiPrueba().apply { cupoRespuesta = Response.error(503, "{}".toResponseBody("application/json".toMediaType())) }
        try { RemoteRegistroVehiculoRepository(api).cupo(); fail("Se esperaba error") }
        catch (error: FalloRegistroVehiculo) { assertEquals(503, error.estadoHttp) }
    }
    @Test fun catalogosIncompletosSeRechazan() = runBlocking<Unit> {
        val api = ApiPrueba().apply { catalogosRespuesta = Response.success(DatosPruebaRf08.catalogos.copy(tiposMotor = emptyList())) }
        try { RemoteRegistroVehiculoRepository(api).catalogos(); fail("Se esperaba error") }
        catch (error: FalloRegistroVehiculo) { assertEquals("RESPUESTA_INVALIDA", error.codigo) }
    }
    @Test fun enviaMismoDtoSinAgregarUsuarioNiModificarClave() = runBlocking<Unit> {
        val api = ApiPrueba(); val s = DatosPruebaRf08.solicitud()
        val resultado = RemoteRegistroVehiculoRepository(api).registrar(s)
        assertEquals(s, api.peticion); assertEquals(s.idOperacion, resultado.idOperacion)
        assertFalse(Gson().toJson(s).contains("idUsuario"))
    }
    @Test fun solicitudSePuedeConservarYRecuperarSinCambiarContenido() {
        val solicitud = DatosPruebaRf08.solicitud()
        val recuperada = Gson().fromJson(Gson().toJson(solicitud), SolicitudRegistroVehiculoDto::class.java)
        assertEquals(solicitud, recuperada)
    }
}
