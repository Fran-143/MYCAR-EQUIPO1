package com.mycar.app.features.sesion.data

import com.mycar.app.features.sesion.domain.CredencialesSesion
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ClientesSesion {
    fun publico(baseUrl: String): Retrofit = Retrofit.Builder().baseUrl(baseUrl)
        .client(OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
            .followSslRedirects(false).callTimeout(20, TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build()
}

class ClienteVehiculosAutenticado(baseUrl: String, credenciales: CredencialesSesion) {
    private val origen = baseUrl.toHttpUrl()
    private val cliente = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
        .followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { cadena ->
            val r = cadena.request()
            if (r.url.scheme != origen.scheme || r.url.host != origen.host || r.url.port != origen.port)
                throw IOException("Origen HTTP no autorizado")
            val prueba = try { credenciales.autorizacion() } catch (_: IOException) { null }
            if (prueba == null) {
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(401).message("Sesión requerida")
                    .body("{\"codigo\":\"SESION_NO_VALIDA\"}".toResponseBody("application/json".toMediaType())).build()
            } else cadena.proceed(r.newBuilder().header("Authorization", prueba).build())
        }.build()
    val retrofit: Retrofit = Retrofit.Builder().baseUrl(baseUrl).client(cliente)
        .addConverterFactory(GsonConverterFactory.create()).build()
    fun cancelar() = cliente.dispatcher.cancelAll()
}
