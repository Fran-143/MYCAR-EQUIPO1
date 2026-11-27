package com.mycar.app.features.sesion.data

import com.mycar.app.features.sesion.domain.CredencialesSesion
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.mycar.app.features.autenticacion.data.SessionRequired
import java.util.concurrent.TimeUnit

object ClientesSesion {
    fun publico(baseUrl: String): Retrofit = Retrofit.Builder().baseUrl(baseUrl)
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
            .followSslRedirects(false).callTimeout(60, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build()
}

class ClienteVehiculosAutenticado(baseUrl: String, autorizacion: () -> String, alCaducar: () -> Unit = {}) {
    // Compatibilidad de las pruebas antiguas; el host real usa exclusivamente AuthRepository.
    constructor(baseUrl: String, credenciales: CredencialesSesion, alCaducar: () -> Unit = {}) : this(
        baseUrl, { try { credenciales.autorizacion() } catch (_: IOException) { throw SessionRequired() } }, alCaducar)
    private val activo = AtomicBoolean(true)
    private val origen = baseUrl.toHttpUrl()
    // Render Free puede superar 30s en arranque en frío; 90s evita abortar POST TIVE/SOAT a medias.
    private val cliente = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .followSslRedirects(false).callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { cadena ->
            val r = cadena.request()
            if (r.url.scheme != origen.scheme || r.url.host != origen.host || r.url.port != origen.port)
                throw IOException("Origen HTTP no autorizado")
            // Cliente ya cerrado (logout / recarga de cuenta): no tumbar la UI con
            // "ciclo terminó"; devolver 401 para que se reconstruya la sesión.
            if (!activo.get()) {
                alCaducar()
                return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                    .code(401).message("Sesión requerida")
                    .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            val prueba = try { autorizacion() } catch (_: SessionRequired) { null }
            if (prueba == null) {
                alCaducar()
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(401).message("Sesión requerida")
                    .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType())).build()
            } else {
                if (!activo.get()) {
                    alCaducar()
                    return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                        .code(401).message("Sesión requerida")
                        .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                val respuesta = cadena.proceed(r.newBuilder().header("Authorization", prueba).build())
                if (respuesta.code != 401) return@addInterceptor respuesta
                // Un 401 puntual (cold start / token a punto de vencer) no debe tumbar el ciclo:
                // renovar una vez y reintentar. Solo alCaducar si la renovación confirma cierre.
                respuesta.close()
                val renovado = try {
                    autorizacion()
                } catch (_: SessionRequired) {
                    null
                } catch (_: IOException) {
                    null
                }
                if (renovado.isNullOrBlank() || renovado == prueba) {
                    alCaducar()
                    return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                        .code(401).message("Sesión requerida")
                        .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                if (!activo.get()) {
                    alCaducar()
                    return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                        .code(401).message("Sesión requerida")
                        .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                val reintento = cadena.proceed(r.newBuilder().header("Authorization", renovado).build())
                if (reintento.code == 401) alCaducar()
                reintento
            }
        }.build()
    val retrofit: Retrofit = Retrofit.Builder().baseUrl(baseUrl).client(cliente)
        .addConverterFactory(GsonConverterFactory.create()).build()
    fun cancelar() { activo.set(false); cliente.dispatcher.cancelAll() }
}
