package com.mycar.app.features.soat.data

import com.google.gson.Gson
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RemoteSoatRepository(
    private val api: SoatApi,
    private val http: OkHttpClient = OkHttpClient()
) : SoatRepository {
    private data class ErrorApi(val codigo: String?, val mensaje: String? = null)

    override suspend fun consultar(idVehiculo: Long): SoatConsultaResponse {
        val r = api.consultar(idVehiculo)
        if (r.code() == 404) return SoatConsultaResponse()
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: SoatConsultaResponse()
    }

    override suspend fun registrar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto =
        exitoso(api.registrar(idVehiculo, solicitud)) { it.idSoat > 0 }

    override suspend fun corregir(idVehiculo: Long, idSoat: Long, solicitud: SoatMutacionDto): SoatDto =
        exitoso(api.corregir(idVehiculo, idSoat, solicitud)) { it.idSoat == idSoat }

    override suspend fun renovar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto =
        exitoso(api.renovar(idVehiculo, solicitud)) { it.idSoat > 0 && it.vigencia == "ACTUAL" }

    override suspend fun recordatorio(idVehiculo: Long, idSoat: Long, activo: Boolean): SoatDto =
        exitoso(api.recordatorio(idVehiculo, idSoat, SoatRecordatorioDto(activo))) { it.idSoat == idSoat }

    override suspend fun presignUpload(idVehiculo: Long, solicitud: SoatPresignSolicitudDto): SoatPresignRespuestaDto =
        exitoso(api.presignUpload(idVehiculo, solicitud)) { it.uploadUrl.isNotBlank() && it.objectKey.isNotBlank() }

    override suspend fun subirDocumento(presign: SoatPresignRespuestaDto, bytes: ByteArray, contentType: String) =
        withContext(Dispatchers.IO) {
            val body = bytes.toRequestBody(contentType.toMediaType())
            val builder = Request.Builder().url(presign.uploadUrl).put(body)
            presign.headers?.forEach { (k, v) -> builder.header(k, v) }
            val resp = http.newCall(builder.build()).execute()
            if (!resp.isSuccessful) throw FalloRegistroVehiculo("SUBIDA_DOCUMENTO_FALLIDA", resp.code)
        }

    override suspend fun urlDocumento(idVehiculo: Long, idSoat: Long): String {
        val r = api.descargarDocumento(idVehiculo, idSoat)
        if (!r.isSuccessful) throw fallo(r)
        return r.body()?.downloadUrl?.takeIf { it.isNotBlank() }
            ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun descargarBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val resp = http.newCall(Request.Builder().url(url).get().build()).execute()
        if (!resp.isSuccessful) throw FalloRegistroVehiculo("DESCARGA_FALLIDA", resp.code)
        resp.body?.bytes() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    private inline fun <T> exitoso(r: retrofit2.Response<T>, validar: (T) -> Boolean): T {
        if (!r.isSuccessful) throw fallo(r)
        val cuerpo = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (!validar(cuerpo)) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return cuerpo
    }

    private fun fallo(r: retrofit2.Response<*>): FalloRegistroVehiculo {
        val parsed = runCatching {
            r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
        }.getOrNull()
        val codigo = parsed?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP"
        return FalloRegistroVehiculo(codigo, r.code(), mensajeServidor = parsed?.mensaje)
    }
}