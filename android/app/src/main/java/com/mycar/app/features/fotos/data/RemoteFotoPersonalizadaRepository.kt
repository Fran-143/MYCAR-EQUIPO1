package com.mycar.app.features.fotos.data

import com.google.gson.Gson
import com.mycar.app.features.fotos.domain.ErrorFotoPersonalizada
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import com.mycar.app.features.fotos.domain.ResultadoFotoPersonalizada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

class RemoteFotoPersonalizadaRepository(
    private val perfilApi: FotoPerfilApi,
    private val vehiculoApi: FotoVehiculoApi,
    private val cache: CacheFotosSesion
) : FotoPersonalizadaRepository {
    private val gson = Gson()
    private data class ErrorApi(val codigo: String? = null, val mensaje: String? = null)

    override suspend fun bytesPerfil(forzarRed: Boolean): ByteArray? = withContext(Dispatchers.IO) {
        if (!forzarRed) cache.obtener(CacheFotosSesion.CLAVE_PERFIL)?.let { return@withContext it }
        descargar(perfilApi.descargar(), CacheFotosSesion.CLAVE_PERFIL)
    }

    override suspend fun registrarPerfil(
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = mutarPerfil(alta = true, bytes, contentType, nombre)

    override suspend fun actualizarPerfil(
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = mutarPerfil(alta = false, bytes, contentType, nombre)

    override suspend fun eliminarPerfil(): ResultadoFotoPersonalizada = withContext(Dispatchers.IO) {
        val r = perfilApi.eliminar()
        interpretarMutacion(r).also {
            cache.invalidar(CacheFotosSesion.CLAVE_PERFIL)
        }
    }

    override suspend fun bytesVehiculo(idVehiculo: Long, forzarRed: Boolean): ByteArray? =
        withContext(Dispatchers.IO) {
            val clave = CacheFotosSesion.claveVehiculo(idVehiculo)
            if (!forzarRed) cache.obtener(clave)?.let { return@withContext it }
            descargar(vehiculoApi.descargar(idVehiculo), clave)
        }

    override suspend fun registrarVehiculo(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = mutarVehiculo(idVehiculo, alta = true, bytes, contentType, nombre)

    override suspend fun actualizarVehiculo(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = mutarVehiculo(idVehiculo, alta = false, bytes, contentType, nombre)

    override suspend fun eliminarVehiculo(idVehiculo: Long): ResultadoFotoPersonalizada =
        withContext(Dispatchers.IO) {
            val r = vehiculoApi.eliminar(idVehiculo)
            interpretarMutacion(r).also {
                cache.invalidar(CacheFotosSesion.claveVehiculo(idVehiculo))
            }
        }

    override fun invalidarPerfil() = cache.invalidar(CacheFotosSesion.CLAVE_PERFIL)

    override fun invalidarVehiculo(idVehiculo: Long) =
        cache.invalidar(CacheFotosSesion.claveVehiculo(idVehiculo))

    private suspend fun mutarPerfil(
        alta: Boolean,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = withContext(Dispatchers.IO) {
        val part = parte(bytes, contentType, nombre)
        val ct = contentType.toRequestBody("text/plain".toMediaType())
        val r = if (alta) perfilApi.registrar(part, ct) else perfilApi.actualizar(part, ct)
        interpretarMutacion(r).also {
            cache.guardar(CacheFotosSesion.CLAVE_PERFIL, bytes)
        }
    }

    private suspend fun mutarVehiculo(
        idVehiculo: Long,
        alta: Boolean,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada = withContext(Dispatchers.IO) {
        val part = parte(bytes, contentType, nombre)
        val ct = contentType.toRequestBody("text/plain".toMediaType())
        val r = if (alta) vehiculoApi.registrar(idVehiculo, part, ct)
        else vehiculoApi.actualizar(idVehiculo, part, ct)
        interpretarMutacion(r).also {
            cache.guardar(CacheFotosSesion.claveVehiculo(idVehiculo), bytes)
        }
    }

    private fun descargar(r: Response<okhttp3.ResponseBody>, clave: String): ByteArray? {
        if (r.code() == 404) {
            cache.invalidar(clave)
            return null
        }
        if (!r.isSuccessful) throw errorOf(r)
        val body = r.body() ?: throw ErrorFotoPersonalizada.Red("Respuesta vacía del servidor.")
        val bytes = body.bytes()
        if (bytes.isEmpty()) {
            cache.invalidar(clave)
            return null
        }
        cache.guardar(clave, bytes)
        return bytes
    }

    private fun parte(bytes: ByteArray, contentType: String, nombre: String): MultipartBody.Part {
        val media = contentType.toMediaType()
        return MultipartBody.Part.createFormData(
            "file",
            nombre.ifBlank { if (contentType.contains("png")) "foto.png" else "foto.jpg" },
            bytes.toRequestBody(media)
        )
    }

    private fun interpretarMutacion(r: Response<ResultadoFotoDto>): ResultadoFotoPersonalizada {
        if (r.isSuccessful) {
            val body = r.body()
            return ResultadoFotoPersonalizada(
                resultado = body?.resultado.orEmpty(),
                mensaje = body?.mensaje?.takeIf { it.isNotBlank() }
                    ?: "Operación completada correctamente"
            )
        }
        throw errorOf(r)
    }

    private fun errorOf(r: Response<*>): ErrorFotoPersonalizada {
        if (r.code() == 401) return ErrorFotoPersonalizada.Sesion()
        val raw = r.errorBody()?.string().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, ErrorApi::class.java) }.getOrNull()
        val mensaje = parsed?.mensaje ?: "No se pudo completar la operación."
        return when {
            r.code() == 409 || parsed?.codigo == "FOTO_YA_REGISTRADA" ->
                ErrorFotoPersonalizada.Conflicto(mensaje)
            r.code() == 404 || parsed?.codigo == "FOTO_NO_ENCONTRADA" ->
                ErrorFotoPersonalizada.NoEncontrada(mensaje)
            r.code() == 400 || parsed?.codigo == "FOTO_INVALIDA" || parsed?.codigo == "VALIDACION" ->
                ErrorFotoPersonalizada.Validacion(mensaje)
            else -> ErrorFotoPersonalizada.Red(mensaje)
        }
    }
}
