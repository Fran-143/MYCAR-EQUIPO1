package com.mycar.app.features.soat.data

import com.google.gson.Gson
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteSoatRepository(
    private val api: SoatApi,
    private val http: OkHttpClient = OkHttpClient()
) : SoatRepository {
    private data class ErrorApi(
        val codigo: String?,
        val mensaje: String? = null,
        val campos: Map<String, String>? = null
    )

    override suspend fun consultar(idVehiculo: Long): SoatConsultaResponse {
        val r = api.consultar(idVehiculo)
        if (r.code() == 404) return SoatConsultaResponse()
        if (!r.isSuccessful) throw fallo(r)
        return normalizar(r.body() ?: SoatConsultaResponse())
    }

    override suspend fun registrar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto {
        val r = api.registrar(idVehiculo, solicitud.copy(idSoatAnterior = null, accionDocumento = null))
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (body.idSoat <= 0) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirActual(idVehiculo)
    }

    override suspend fun corregir(idVehiculo: Long, idSoat: Long, solicitud: SoatMutacionDto): SoatDto {
        val payload = solicitud.copy(idOperacion = null, idSoatAnterior = null)
        val r = api.corregir(idVehiculo, idSoat, payload)
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (body.idSoat != idSoat) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirActual(idVehiculo)
    }

    override suspend fun renovar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto {
        require(solicitud.idSoatAnterior != null && solicitud.idSoatAnterior > 0) {
            "idSoatAnterior requerido"
        }
        val r = api.renovar(idVehiculo, solicitud.copy(accionDocumento = null))
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        if (body.idSoatNuevo <= 0) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirActual(idVehiculo)
    }

    override suspend fun recordatorio(idVehiculo: Long, idSoat: Long, activo: Boolean): SoatDto {
        val r = api.recordatorio(idVehiculo, idSoat, SoatRecordatorioDto(activo))
        if (!r.isSuccessful) throw fallo(r)
        r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
        return exigirActual(idVehiculo)
    }

    override suspend fun subirDocumento(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombreOriginal: String
    ): SoatDocumentoRefDto = withContext(Dispatchers.IO) {
        // 1) Intento multipart (backend nuevo). 2) Fallback presign (Render actual).
        val multipart = runCatching {
            val media = contentType.toMediaType()
            val filePart = MultipartBody.Part.createFormData(
                "file", nombreOriginal, bytes.toRequestBody(media)
            )
            val r = api.subirDocumentoMultipart(
                idVehiculo,
                filePart,
                contentType.toRequestBody("text/plain".toMediaType()),
                nombreOriginal.toRequestBody("text/plain".toMediaType())
            )
            if (r.isSuccessful) {
                r.body()?.takeIf { it.objectKey.isNotBlank() }
            } else null
        }.getOrNull()
        if (multipart != null) return@withContext multipart

        val tamano = bytes.size.coerceAtLeast(1)
        val presign = exitoso(
            api.presignUpload(idVehiculo, SoatPresignSolicitudDto(contentType, tamano, nombreOriginal))
        ) { it.uploadUrl.isNotBlank() && it.objectKey.isNotBlank() }
        val body = bytes.toRequestBody(contentType.toMediaType())
        val builder = Request.Builder().url(presign.uploadUrl).put(body)
        presign.headers?.forEach { (k, v) -> builder.header(k, v) }
        val resp = http.newCall(builder.build()).execute()
        if (!resp.isSuccessful) throw FalloRegistroVehiculo("SUBIDA_DOCUMENTO_FALLIDA", resp.code)
        SoatDocumentoRefDto(presign.objectKey, contentType, tamano, nombreOriginal)
    }

    override suspend fun descargarDocumento(idVehiculo: Long, idSoat: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val r = api.descargarDocumento(idVehiculo, idSoat)
            if (!r.isSuccessful) throw fallo(r)
            val body = r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
            val bytes = body.bytes()
            val ct = body.contentType()?.toString().orEmpty()
            // Render actual: application/json con downloadUrl firmada.
            if (ct.contains("json", ignoreCase = true) ||
                (bytes.size > 2 && bytes[0] == '{'.code.toByte())
            ) {
                val parsed = runCatching {
                    Gson().fromJson(String(bytes), SoatDocumentoDescargaDto::class.java)
                }.getOrNull()
                val url = parsed?.downloadUrl?.takeIf { it.isNotBlank() }
                    ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
                val resp = http.newCall(Request.Builder().url(url).get().build()).execute()
                if (!resp.isSuccessful) throw FalloRegistroVehiculo("DESCARGA_FALLIDA", resp.code)
                return@withContext resp.body?.bytes()
                    ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
            }
            if (bytes.isEmpty()) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
            bytes
        }

    private suspend fun exigirActual(idVehiculo: Long): SoatDto {
        val consulta = consultar(idVehiculo)
        return consulta.actual ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
    }

    private fun normalizar(c: SoatConsultaResponse): SoatConsultaResponse {
        fun norm(s: SoatDto): SoatDto {
            val tiene = s.tieneDocumento || s.documento?.objectKey?.isNotBlank() == true
            val cobertura = s.estadoCobertura?.takeIf { it.isNotBlank() } ?: c.estadoCobertura
            return s.copy(tieneDocumento = tiene, estadoCobertura = cobertura)
        }
        return c.copy(
            actual = c.actual?.let(::norm),
            historial = c.historial.map(::norm)
        )
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
        val porCampo = parsed?.campos.orEmpty()
        return FalloRegistroVehiculo(
            codigo = codigo,
            estadoHttp = r.code(),
            campos = porCampo.keys,
            mensajeServidor = parsed?.mensaje,
            erroresPorCampo = porCampo
        )
    }
}
