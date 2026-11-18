package com.mycar.app.features.tive.data

import com.google.gson.Gson
import com.mycar.app.features.tive.domain.TiveRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteTiveRepository(private val api: TiveApi) : TiveRepository {
    private data class ErrorApi(
        val codigo: String?,
        val mensaje: String? = null,
        val campos: Map<String, String>? = null
    )

    override suspend fun precheck(idVehiculo: Long): TivePrecheckResponse {
        val r = api.precheck(idVehiculo)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: TivePrecheckResponse()
    }

    override suspend fun consultar(idVehiculo: Long): TiveConsultaResponse {
        val r = api.consultar(idVehiculo)
        if (r.code() == 404) return TiveConsultaResponse()
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: TiveConsultaResponse()
    }

    override suspend fun registrar(idVehiculo: Long, solicitud: TiveRegistrarRequest): TiveDto {
        val r = api.registrar(idVehiculo, solicitud)
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (body.idTive <= 0) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirTive(idVehiculo)
    }

    override suspend fun actualizar(
        idVehiculo: Long,
        idTive: Long,
        solicitud: TiveActualizarRequest
    ): TiveDto {
        val r = api.actualizar(idVehiculo, idTive, solicitud)
        if (!r.isSuccessful) throw fallo(r)
        r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirTive(idVehiculo)
    }

    override suspend fun subirDocumento(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombreOriginal: String
    ): TiveDocumentoRefDto = withContext(Dispatchers.IO) {
        val media = contentType.toMediaType()
        val filePart = MultipartBody.Part.createFormData(
            "file", nombreOriginal, bytes.toRequestBody(media)
        )
        val r = api.subirDocumento(
            idVehiculo,
            filePart,
            contentType.toRequestBody("text/plain".toMediaType()),
            nombreOriginal.toRequestBody("text/plain".toMediaType())
        )
        if (!r.isSuccessful) throw fallo(r)
        r.body()?.takeIf { it.objectKey.isNotBlank() }
            ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun descargarDocumento(idVehiculo: Long, idTive: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val r = api.descargarDocumento(idVehiculo, idTive)
            if (!r.isSuccessful) throw fallo(r)
            val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
            val bytes = body.bytes()
            if (bytes.isEmpty()) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
            bytes
        }

    private suspend fun exigirTive(idVehiculo: Long): TiveDto {
        val consulta = consultar(idVehiculo)
        return consulta.tive ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    private fun fallo(r: retrofit2.Response<*>): FalloRegistroVehiculo {
        val parsed = runCatching {
            r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
        }.getOrNull()
        return FalloRegistroVehiculo(
            codigo = parsed?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP",
            estadoHttp = r.code(),
            campos = parsed?.campos.orEmpty().keys,
            mensajeServidor = parsed?.mensaje,
            erroresPorCampo = parsed?.campos.orEmpty()
        )
    }
}