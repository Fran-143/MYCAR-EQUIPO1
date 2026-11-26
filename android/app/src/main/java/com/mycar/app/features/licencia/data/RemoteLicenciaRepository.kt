package com.mycar.app.features.licencia.data

import com.google.gson.Gson
import com.mycar.app.features.licencia.domain.LicenciaRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteLicenciaRepository(private val api: LicenciaApi) : LicenciaRepository {
    private data class ErrorApi(
        val codigo: String?,
        val mensaje: String? = null,
        val campos: Map<String, String>? = null
    )

    override suspend fun consultar(): LicenciaConsultaResponse {
        val r = api.consultar()
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: LicenciaConsultaResponse()
    }

    override suspend fun registrar(solicitud: LicenciaRegistrarRequest): LicenciaDto {
        val r = api.registrar(solicitud)
        if (!r.isSuccessful) throw fallo(r)
        return consultar().actual ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun editar(solicitud: LicenciaEditarRequest): LicenciaDto {
        val r = api.editar(solicitud)
        if (!r.isSuccessful) throw fallo(r)
        return consultar().actual ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun renovar(solicitud: LicenciaRenovarRequest): LicenciaDto {
        val r = api.renovar(solicitud)
        if (!r.isSuccessful) throw fallo(r)
        return consultar().actual ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun recordatorio(activo: Boolean): LicenciaDto {
        val r = api.recordatorio(LicenciaRecordatorioRequest(activo))
        if (!r.isSuccessful) throw fallo(r)
        return consultar().actual ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun subirFoto(
        lado: String,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): LicenciaFotoDto = withContext(Dispatchers.IO) {
        val media = contentType.toMediaType()
        val file = MultipartBody.Part.createFormData("file", nombre, bytes.toRequestBody(media))
        val r = api.subirFoto(
            lado.toRequestBody("text/plain".toMediaType()),
            file,
            contentType.toRequestBody("text/plain".toMediaType()),
            nombre.toRequestBody("text/plain".toMediaType())
        )
        if (!r.isSuccessful) throw fallo(r)
        r.body()?.takeIf { it.objectKey.isNotBlank() }
            ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun descargarFoto(lado: String): ByteArray = withContext(Dispatchers.IO) {
        val r = api.descargarFoto(lado)
        if (!r.isSuccessful) throw fallo(r)
        r.body()?.bytes() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
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
