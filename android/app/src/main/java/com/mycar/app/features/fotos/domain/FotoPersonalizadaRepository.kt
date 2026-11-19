package com.mycar.app.features.fotos.domain

sealed class ErrorFotoPersonalizada(message: String) : Exception(message) {
    class Sesion : ErrorFotoPersonalizada("Tu sesión expiró. Vuelve a iniciar sesión.")
    class Validacion(mensaje: String) : ErrorFotoPersonalizada(mensaje)
    class Conflicto(mensaje: String) : ErrorFotoPersonalizada(mensaje)
    class NoEncontrada(mensaje: String = "No hay foto registrada.") : ErrorFotoPersonalizada(mensaje)
    class Red(mensaje: String = "No se pudo completar la operación.") : ErrorFotoPersonalizada(mensaje)
}

data class ResultadoFotoPersonalizada(
    val resultado: String,
    val mensaje: String
)

interface FotoPersonalizadaRepository {
    suspend fun bytesPerfil(forzarRed: Boolean = false): ByteArray?
    suspend fun registrarPerfil(bytes: ByteArray, contentType: String, nombre: String): ResultadoFotoPersonalizada
    suspend fun actualizarPerfil(bytes: ByteArray, contentType: String, nombre: String): ResultadoFotoPersonalizada
    suspend fun eliminarPerfil(): ResultadoFotoPersonalizada

    suspend fun bytesVehiculo(idVehiculo: Long, forzarRed: Boolean = false): ByteArray?
    suspend fun registrarVehiculo(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada
    suspend fun actualizarVehiculo(
        idVehiculo: Long,
        bytes: ByteArray,
        contentType: String,
        nombre: String
    ): ResultadoFotoPersonalizada
    suspend fun eliminarVehiculo(idVehiculo: Long): ResultadoFotoPersonalizada

    fun invalidarPerfil()
    fun invalidarVehiculo(idVehiculo: Long)
}
