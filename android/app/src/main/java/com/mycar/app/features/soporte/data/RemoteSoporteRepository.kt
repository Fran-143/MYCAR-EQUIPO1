package com.mycar.app.features.soporte.data

import com.google.gson.Gson
import com.mycar.app.features.soporte.domain.SoporteRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RemoteSoporteRepository(private val api: SoporteApi) : SoporteRepository {
    private data class ErrorApi(
        val codigo: String?,
        val mensaje: String? = null,
        val campos: Map<String, String>? = null
    )

    override suspend fun vehiculosElegibles(): List<VehiculoElegibleDto> {
        val r = api.vehiculosElegibles()
        if (!r.isSuccessful) throw fallo(r)
        return r.body().orEmpty()
    }

    override suspend fun listarSolicitudes(): List<SolicitudResumenDto> {
        val r = api.listarSolicitudes()
        if (!r.isSuccessful) throw fallo(r)
        return r.body().orEmpty()
    }

    override suspend fun detalle(codigo: String): SolicitudDetalleDto {
        val r = api.detalle(codigo)
        if (!r.isSuccessful) throw fallo(r)
        return r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun crearCorreccionVin(
        idOperacion: String,
        idVehiculo: Long,
        vinPropuesto: String,
        motivo: String,
        bytes: ByteArray?,
        contentType: String?,
        nombreOriginal: String?
    ): CrearSolicitudResponse = withContext(Dispatchers.IO) {
        val filePart = if (bytes != null && bytes.isNotEmpty() && !contentType.isNullOrBlank()) {
            val media = contentType.toMediaType()
            MultipartBody.Part.createFormData(
                "file",
                nombreOriginal ?: "evidencia",
                bytes.toRequestBody(media)
            )
        } else null
        val ctPart = contentType?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaType())
        val r = api.crear(
            idOperacion.toRequestBody("text/plain".toMediaType()),
            idVehiculo.toString().toRequestBody("text/plain".toMediaType()),
            vinPropuesto.toRequestBody("text/plain".toMediaType()),
            motivo.toRequestBody("text/plain".toMediaType()),
            filePart,
            ctPart
        )
        if (!r.isSuccessful) throw fallo(r)
        r.body() ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA", r.code())
    }

    override suspend fun descargarEvidencia(codigo: String): ByteArray = withContext(Dispatchers.IO) {
        val r = api.evidencia(codigo)
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