package com.mycar.app.features.perfil.domain

data class DatosPerfil(
    val nombre: String,
    val apellido: String,
    val correo: String,
    val fechaNacimientoIso: String
) {
    val nombreCompleto: String get() = "$nombre $apellido".trim()
    val iniciales: String get() {
        val n = nombre.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val a = apellido.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        return (n + a).ifBlank { "?" }
    }
}

data class FormularioEdicionPerfil(
    val nombre: String = "",
    val apellido: String = "",
    val fechaNacimiento: String = "",
    val correo: String = ""
)

sealed class ErrorPerfil(message: String) : Exception(message) {
    class Sesion : ErrorPerfil("Tu sesión expiró. Vuelve a iniciar sesión.")
    class Validacion(val campos: Map<String, String>, mensaje: String = "Revisa los datos del perfil.") :
        ErrorPerfil(mensaje)
    class Red(mensaje: String = "No se pudo conectar. Inténtalo de nuevo.") : ErrorPerfil(mensaje)
}

interface PerfilRepository {
    suspend fun consultar(): DatosPerfil
    suspend fun actualizar(nombre: String, apellido: String, fechaNacimientoIso: String): DatosPerfil
    suspend fun verificarContrasenaActual(actual: String)
    suspend fun cambiarContrasena(actual: String, nueva: String, confirmacion: String)
}
