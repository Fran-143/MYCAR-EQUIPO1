package com.mycar.app.features.autenticacion.data

import android.content.Context
import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Una sola instancia por proceso, sin logger HTTP, caché, cookies ni reintentos de POST. */
object AuthGraph {
    fun privacyPolicy() = PrivacyPolicyConfiguration.configured(
        BuildConfig.PRIVACY_SOURCE, BuildConfig.PRIVACY_VERSION, BuildConfig.PRIVACY_DOCUMENT_URL)

    @Volatile private var instance: AuthRepository? = null
    @Volatile private var services: Retrofit? = null

    /** Mismo transporte y URL para auth y módulos protegidos; la sesión adjunta su prueba al invocar. */
    fun <T> service(context: Context, type: Class<T>): T {
        repository(context)
        return requireNotNull(services).create(type)
    }
    fun repository(context: Context): AuthRepository = instance ?: synchronized(this) {
        instance ?: run {
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
                .client(client).addConverterFactory(GsonConverterFactory.create()).build()
            services = retrofit
            val api = retrofit.create(AuthApi::class.java)
            AuthRepository(api, KeystoreCredentialStore(context.applicationContext, BuildConfig.API_BASE_URL),
                registrationPolicy = privacyPolicy())
                .also { instance = it }
        }
    }
}
