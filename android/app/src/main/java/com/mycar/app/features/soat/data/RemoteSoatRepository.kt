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

    override suspend fun listarAseguradoras(): List<AseguradoraSoatDto> {
        val r = api.listarAseguradoras()
        if (!r.isSuccessful) throw fallo(r)
        return r.body()?.items.orEmpty()
    }

    override suspend fun registrar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto {
        val r = api.registrar(idVehiculo, solicitud.copy(idSoatAnterior = null, accionDocumento = null))
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        if (body.idSoat <= 0) throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        return exigirActual(idVehiculo)
    }

    override suspend fun corregir(idVehiculo: Long, idSoat: Long, solicitud: SoatMutacionDto): SoatDto {
        val payload = solicitud.copy(idOperacion = null, idSoatAnterior = null)
        val r = api.corregir(idVehiculo, idSoat, payload)
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        if (body.idSoat != idSoat) throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        return exigirActual(idVehiculo)
    }

    override suspend fun renovar(idVehiculo: Long, solicitud: SoatMutacionDto): SoatDto {
        require(solicitud.idSoatAnterior != null && solicitud.idSoatAnterior > 0) {
            "idSoatAnterior requerido"
        }
        val r = api.renovar(idVehiculo, solicitud.copy(accionDocumento = null))
        if (!r.isSuccessful) throw fallo(r)
        val body = r.body() ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        if (body.idSoatNuevo <= 0) throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No recibimos confirmación del SOAT."
        )
        return exigirActual(idVehiculo)
    }

    override suspend fun recordatorio(idVehiculo: Long, idSoat: Long, activo: Boolean): SoatDto {
        val r = api.recordatorio(idVehiculo, idSoat, SoatRecordatorioDto(activo))
        if (!r.isSuccessful) throw fallo(r)
        r.body() ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "No se pudo actualizar el recordatorio."
        )
        return exigirActual(idVehiculo)
    }

    override suspend fun subirDocumento(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombreOriginal: String
    ): SoatDocumentoRefDto = withContext(Dispatchers.IO) {
        val tipo = normalizarContentType(contentType)
        // 1) Multipart (funciona con storage fake y supabase; cifra en servidor).
        val multipartResp = runCatching {
            val media = tipo.toMediaType()
            val filePart = MultipartBody.Part.createFormData(
                "file", nombreOriginal, bytes.toRequestBody(media)
            )
            api.subirDocumentoMultipart(
                idVehiculo,
                filePart,
                tipo.toRequestBody("text/plain".toMediaType()),
                nombreOriginal.toRequestBody("text/plain".toMediaType())
            )
        }.getOrElse { ex ->
            throw FalloRegistroVehiculo(
                "SUBIDA_DOCUMENTO_FALLIDA",
                mensajeServidor = ex.message?.takeIf { it.isNotBlank() }
                    ?: "No se pudo preparar la subida del documento."
            )
        }
        if (multipartResp.isSuccessful) {
            val body = multipartResp.body()?.takeIf { it.objectKey.isNotBlank() }
                ?: throw FalloRegistroVehiculo(
                    "RESPUESTA_INVALIDA",
                    multipartResp.code(),
                    mensajeServidor = "No se recibió la referencia del documento."
                )
            // Multipart siempre cifra; conservar flag para el POST de registro.
            return@withContext body.copy(cifrado = body.cifrado ?: true)
        }
        // Solo caer a presign si el endpoint multipart no existe en ese deploy.
        if (multipartResp.code() !in setOf(404, 405, 501)) {
            throw fallo(multipartResp)
        }

        val tamano = bytes.size.coerceAtLeast(1)
        val presign = exitoso(
            api.presignUpload(idVehiculo, SoatPresignSolicitudDto(tipo, tamano, nombreOriginal))
        ) { it.uploadUrl.isNotBlank() && it.objectKey.isNotBlank() }
        val url = presign.uploadUrl
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            // p.ej. fake://upload/... — OkHttp no puede PUT ahí; multipart debió usarse.
            throw FalloRegistroVehiculo(
                "SUBIDA_DOCUMENTO_FALLIDA",
                mensajeServidor = "No se pudo subir el documento al almacenamiento. Reintenta o continúa sin adjunto."
            )
        }
        val body = bytes.toRequestBody(tipo.toMediaType())
        val builder = Request.Builder().url(url).put(body)
        presign.headers?.forEach { (k, v) -> builder.header(k, v) }
        val resp = http.newCall(builder.build()).execute()
        if (!resp.isSuccessful) {
            throw FalloRegistroVehiculo(
                "SUBIDA_DOCUMENTO_FALLIDA",
                resp.code,
                mensajeServidor = "No se pudo subir el documento (HTTP ${resp.code})."
            )
        }
        // Presign sube bytes en claro; el servidor no marca cifrado.
        SoatDocumentoRefDto(presign.objectKey, tipo, tamano, nombreOriginal, cifrado = false)
    }

    override suspend fun descargarDocumento(idVehiculo: Long, idSoat: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val r = api.descargarDocumento(idVehiculo, idSoat)
            if (!r.isSuccessful) throw fallo(r)
            val body = r.body() ?: throw FalloRegistroVehiculo(
                "RESPUESTA_INVALIDA",
                r.code(),
                mensajeServidor = "Documento vacío."
            )
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
                    ?: throw FalloRegistroVehiculo(
                        "RESPUESTA_INVALIDA",
                        r.code(),
                        mensajeServidor = "URL de descarga no disponible."
                    )
                val resp = http.newCall(Request.Builder().url(url).get().build()).execute()
                if (!resp.isSuccessful) {
                    throw FalloRegistroVehiculo(
                        "DESCARGA_FALLIDA",
                        resp.code,
                        mensajeServidor = "No se pudo descargar el documento."
                    )
                }
                return@withContext resp.body?.bytes()
                    ?: throw FalloRegistroVehiculo(
                        "RESPUESTA_INVALIDA",
                        mensajeServidor = "Documento vacío."
                    )
            }
            if (bytes.isEmpty()) {
                throw FalloRegistroVehiculo(
                    "RESPUESTA_INVALIDA",
                    r.code(),
                    mensajeServidor = "Documento vacío."
                )
            }
            bytes
        }

    private suspend fun exigirActual(idVehiculo: Long): SoatDto {
        val consulta = consultar(idVehiculo)
        return consulta.actual ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            mensajeServidor = "El SOAT se guardó pero no pudimos mostrarlo. Vuelve a consultar."
        )
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

    private fun normalizarContentType(raw: String): String {
        val base = raw.trim().lowercase().substringBefore(';').trim()
        return when (base) {
            "image/jpg" -> "image/jpeg"
            else -> base.ifBlank { "application/octet-stream" }
        }
    }

    private inline fun <T> exitoso(r: retrofit2.Response<T>, validar: (T) -> Boolean): T {
        if (!r.isSuccessful) throw fallo(r)
        val cuerpo = r.body() ?: throw FalloRegistroVehiculo(
            "RESPUESTA_INVALIDA",
            r.code(),
            mensajeServidor = "Respuesta vacía del servidor."
        )
        if (!validar(cuerpo)) {
            throw FalloRegistroVehiculo(
                "RESPUESTA_INVALIDA",
                r.code(),
                mensajeServidor = "Respuesta incompleta del servidor."
            )
        }
        return cuerpo
    }

    private fun fallo(r: retrofit2.Response<*>): FalloRegistroVehiculo {
        val parsed = runCatching {
            r.errorBody()?.use { Gson().fromJson(it.string(), ErrorApi::class.java) }
        }.getOrNull()
        val codigo = parsed?.codigo ?: if (r.code() == 401) "SESION_NO_VALIDA" else "ERROR_HTTP"
        val porCampo = parsed?.campos.orEmpty()
        val mensaje = parsed?.mensaje?.takeIf { it.isNotBlank() }
            ?: when (r.code()) {
                401 -> "Sesión no válida. Vuelve a iniciar sesión."
                413 -> "El archivo es demasiado grande."
                else -> "No pudimos completar la operación (HTTP ${r.code()})."
            }
        return FalloRegistroVehiculo(
            codigo = codigo,
            estadoHttp = r.code(),
            campos = porCampo.keys,
            mensajeServidor = mensaje,
            erroresPorCampo = porCampo
        )
    }
}
