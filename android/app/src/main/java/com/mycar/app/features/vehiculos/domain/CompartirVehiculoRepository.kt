package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.CompartirVehiculoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FalloCompartirVehiculo(
    val estadoHttp: Int,
    val codigo: String?,
    mensaje: String
) : Exception(mensaje)

interface CompartirVehiculoRepository {
    suspend fun descargarPdf(idVehiculo: Long, incluirFoto: Boolean): ByteArray
}

class RemoteCompartirVehiculoRepository(
    private val api: CompartirVehiculoApi
) : CompartirVehiculoRepository {
    override suspend fun descargarPdf(idVehiculo: Long, incluirFoto: Boolean): ByteArray =
        withContext(Dispatchers.IO) {
            val respuesta = api.descargarPdf(idVehiculo, incluirFoto)
            if (respuesta.isSuccessful) {
                val cuerpo = respuesta.body()?.bytes()
                if (cuerpo == null || cuerpo.isEmpty()) {
                    throw FalloCompartirVehiculo(
                        respuesta.code(),
                        "PDF_NO_GENERADO",
                        "No se pudo generar el documento. Inténtalo nuevamente."
                    )
                }
                if (cuerpo.size < 5 || !cuerpo.copyOfRange(0, 4).contentEquals("%PDF".toByteArray())) {
                    throw FalloCompartirVehiculo(
                        respuesta.code(),
                        "PDF_NO_GENERADO",
                        "No se pudo generar el documento. Inténtalo nuevamente."
                    )
                }
                return@withContext cuerpo
            }
            val error = respuesta.errorBody()?.string().orEmpty()
            val codigo = Regex("\"codigo\"\\s*:\\s*\"([^\"]+)\"").find(error)?.groupValues?.getOrNull(1)
            val mensaje = when (respuesta.code()) {
                401 -> "Inicia sesión para continuar."
                403 -> "No es posible compartir este vehículo en este momento."
                404 -> "No se encontró el vehículo en tu cuenta."
                422 -> "No se pudo generar el documento. Inténtalo nuevamente."
                else -> "No se pudo generar el documento. Inténtalo nuevamente."
            }
            throw FalloCompartirVehiculo(respuesta.code(), codigo, mensaje)
        }
}
