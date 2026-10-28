package com.mycar.app.features.historial

import com.google.gson.Gson
import com.mycar.app.features.historial.data.*
import com.mycar.app.features.historial.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal

class RemoteHistorialRepositoryTest {
    private val gson = Gson()
    private val vehiculo = """{"idVehiculo":10,"placa":"ABC123","marca":"Toyota","modelo":"Corolla","estado":"ACTIVO","principal":true}"""
    private val nulo = """{"idMantenimiento":20,"codigoTipo":"CAMBIO_ACEITE","tipo":"Cambio de aceite","fechaServicio":"2026-10-10","kmServicio":47000,"estado":"REALIZADO","costoTotal":null}"""
    private val cero = """{"idMantenimiento":21,"codigoTipo":"OTRO","tipo":"Otro","servicioOtro":"Alineación","fechaServicio":"2026-10-10","kmServicio":49000,"estado":"ANULADO","costoTotal":0}"""
    private class Api(var contexto: Response<ContextoHistorialDto>, var resultado: Response<ResultadoHistorialDto>) : HistorialApi {
        override suspend fun contexto() = contexto
        override suspend fun historial(id: Long) = resultado
    }
    private fun api(registros: String = "$cero,$nulo", total: Int = 2, v: String = vehiculo): Api = Api(
        Response.success(gson.fromJson("""{"vehiculos":[$vehiculo]}""", ContextoHistorialDto::class.java)),
        Response.success(gson.fromJson("""{"vehiculo":$v,"total":$total,"mantenimientos":[$registros]}""", ResultadoHistorialDto::class.java)))

    @Test fun conservaCostoNullCeroOtroyKmHistorico() = runBlocking {
        val repo = RemoteHistorialRepository(api())
        val r = repo.historial(10)
        assertEquals(BigDecimal.ZERO, r.mantenimientos[0].costo)
        assertNull(r.mantenimientos[1].costo)
        assertEquals("Alineación", r.mantenimientos[0].titulo)
        assertEquals(47000, r.mantenimientos[1].kmServicio)
    }

    @Test fun rechazaVehiculoDistintoAlSolicitado() { error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api()).historial(99) } }
    @Test fun rechazaContadorDistintoDeTarjetas() { error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api(total = 3)).historial(10) } }
    @Test fun rechazaOrdenInvertidoYRegistrosDuplicados() {
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api("$nulo,$cero")).historial(10) }
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api("$cero,$cero")).historial(10) }
    }
    @Test fun rechazaKmAusenteYFechaInvalida() {
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api(nulo.replace("47000", "null"), 1)).historial(10) }
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api(nulo.replace("2026-10-10", "2026-02-30"), 1)).historial(10) }
    }
    @Test fun noRedondeaCostoInvalidoNiAceptaNumeroComoTexto() {
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api(cero.replace(":0}", ":12.345}"), 1)).historial(10) }
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(api(cero.replace(":0}", ":\"0\"}"), 1)).historial(10) }
    }
    @Test fun historialVacioEsValido() = runBlocking {
        assertTrue(RemoteHistorialRepository(api("", 0)).historial(10).mantenimientos.isEmpty())
    }
    @Test fun traduceSesionExpiradaYVehiculoNoDisponible() {
        val a = api(); a.resultado = Response.error(401, "{}".toResponseBody())
        error(CodigoErrorHistorial.SESION) { RemoteHistorialRepository(a).historial(10) }
        a.resultado = Response.error(404, "{}".toResponseBody())
        error(CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE) { RemoteHistorialRepository(a).historial(10) }
    }
    @Test fun error503NoSeTransformaEnHistorialVacio() {
        val a = api(); a.resultado = Response.error(503, "{}".toResponseBody())
        error(CodigoErrorHistorial.CONEXION) { RemoteHistorialRepository(a).historial(10) }
    }
    @Test fun contextoSinListaNoEsUnaCuentaSinVehiculos() {
        val a = api(); a.contexto = Response.success(gson.fromJson("{}", ContextoHistorialDto::class.java))
        error(CodigoErrorHistorial.DATOS) { RemoteHistorialRepository(a).vehiculos() }
    }
    private fun error(codigo: CodigoErrorHistorial, bloque: suspend () -> Unit) {
        try { runBlocking { bloque() }; fail("Se esperaba rechazo")
        } catch (e: ErrorHistorial) { assertEquals(codigo, e.codigo) }
    }
}
