package com.mycar.app.features.sesion

import com.mycar.app.features.autenticacion.data.SessionRequired
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

class ClienteSesionCompartidaTest {
    interface Probe {
        @POST("api/v1/vehiculos") fun registrar(@Body body: Map<String, String>): Call<ResponseBody>
    }
    private fun conServidor(status: Int, test: (String, AtomicInteger, () -> String?) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val count = AtomicInteger(); var authorization: String? = null
        server.createContext("/") { exchange ->
            count.incrementAndGet()
            authorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.requestBody.use { it.readBytes() }
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try { test("http://127.0.0.1:${server.address.port}/", count) { authorization } }
        finally { server.stop(0) }
    }
    @Test fun usaElBearerCompartidoEnUnUnicoPost() = conServidor(201) { base, count, header ->
        val client = ClienteVehiculosAutenticado(base, autorizacion = { "Bearer fixture" })
        val result = client.retrofit.create(Probe::class.java).registrar(mapOf("idOperacion" to "fixture")).execute()
        result.body()?.close()
        assertEquals(201, result.code()); assertEquals(1, count.get()); assertEquals("Bearer fixture", header())
    }
    @Test fun un401NoReenviaElPostYNotificaAlCiclo() = conServidor(401) { base, count, _ ->
        var invalidations = 0
        val client = ClienteVehiculosAutenticado(base, autorizacion = { "Bearer fixture" }, alCaducar = { invalidations++ })
        val result = client.retrofit.create(Probe::class.java).registrar(emptyMap()).execute()
        result.errorBody()?.close()
        assertEquals(401, result.code()); assertEquals(1, count.get()); assertEquals(1, invalidations)
    }
    @Test fun falloTemporalNoSeConvierteEn401NiEnviaElPost() = conServidor(201) { base, count, _ ->
        var invalidations = 0
        val client = ClienteVehiculosAutenticado(base, autorizacion = { throw IOException("fixture temporal") }, alCaducar = { invalidations++ })
        assertThrows(IOException::class.java) { client.retrofit.create(Probe::class.java).registrar(emptyMap()).execute() }
        assertEquals(0, count.get()); assertEquals(0, invalidations)
    }
    @Test fun contextoRetiradoNoAlcanzaElServidor() = conServidor(201) { base, count, _ ->
        var invalidations = 0
        val client = ClienteVehiculosAutenticado(base, autorizacion = { throw SessionRequired() }, alCaducar = { invalidations++ })
        val result = client.retrofit.create(Probe::class.java).registrar(emptyMap()).execute()
        result.errorBody()?.close()
        assertEquals(401, result.code()); assertEquals(0, count.get()); assertEquals(1, invalidations)
    }
    @Test fun clienteCerradoNoPuedeEnviarOtroRegistro() = conServidor(201) { base, count, _ ->
        val client = ClienteVehiculosAutenticado(base, autorizacion = { "Bearer fixture" })
        client.cancelar()
        assertThrows(IOException::class.java) { client.retrofit.create(Probe::class.java).registrar(emptyMap()).execute() }
        assertEquals(0, count.get())
    }
}
