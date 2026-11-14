package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.RecoveryApi
import com.mycar.app.features.autenticacion.data.RecoveryEmailRequest
import com.mycar.app.features.autenticacion.data.RecoveryEmitResponse
import com.mycar.app.features.autenticacion.data.RecoveryRepository
import com.mycar.app.features.autenticacion.data.RecoveryResetRequest
import com.mycar.app.features.autenticacion.data.RecoverySimpleResponse
import com.mycar.app.features.autenticacion.data.RecoveryVerifyRequest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RecoveryRepositoryTest {
    private class Api : RecoveryApi {
        var emitBody: RecoveryEmitResponse? = RecoveryEmitResponse("OK", "msg", null)
        var emitCode = 202
        var verifyCode = 200
        var resetCode = 200
        var errorJson: String? = null

        override suspend fun emitir(body: RecoveryEmailRequest): Response<RecoveryEmitResponse> =
            if (emitCode in 200..299) Response.success(emitCode, emitBody)
            else Response.error(emitCode, (errorJson ?: "{}").toResponseBody("application/json".toMediaType()))

        override suspend fun verificar(body: RecoveryVerifyRequest): Response<RecoverySimpleResponse> =
            if (verifyCode in 200..299) Response.success(RecoverySimpleResponse("CODIGO_VALIDO"))
            else Response.error(verifyCode, (errorJson ?: "{}").toResponseBody("application/json".toMediaType()))

        override suspend fun restablecer(body: RecoveryResetRequest): Response<RecoverySimpleResponse> =
            if (resetCode in 200..299) Response.success(RecoverySimpleResponse("OK"))
            else Response.error(resetCode, (errorJson ?: "{}").toResponseBody("application/json".toMediaType()))
    }

    @Test
    fun mapsExpiredCodeMessage() = runBlocking {
        val api = Api().apply {
            verifyCode = 422
            errorJson = """{"codigo":"CODIGO_EXPIRADO","mensaje":"El código expiró. Solicita uno nuevo.","erroresPorCampo":{"codigo":"El código expiró."}}"""
        }
        val ex = runCatching { RecoveryRepository(api).verificar("a@b.com", "123456") }.exceptionOrNull()
                as RecoveryRepository.RecoveryProblem
        assertTrue(ex.message!!.contains("expir"))
        assertEquals("El código expiró.", ex.campos["codigo"])
    }

    @Test
    fun mapsInvalidCodeMessage() = runBlocking {
        val api = Api().apply {
            verifyCode = 422
            errorJson = """{"codigo":"CODIGO_INVALIDO","mensaje":"El código no es válido.","erroresPorCampo":{"codigo":"Código incorrecto."}}"""
        }
        val ex = runCatching { RecoveryRepository(api).verificar("a@b.com", "000000") }.exceptionOrNull()
                as RecoveryRepository.RecoveryProblem
        assertTrue(ex.message!!.contains("válido") || ex.message!!.contains("valido"))
    }
}
