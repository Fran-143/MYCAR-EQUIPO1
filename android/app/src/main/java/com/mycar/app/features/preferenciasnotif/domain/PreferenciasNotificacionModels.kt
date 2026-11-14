package com.mycar.app.features.preferenciasnotif.domain

enum class PermisoDispositivo(val api: String) {
    NO_SOLICITADO("NO_SOLICITADO"),
    CONCEDIDO("CONCEDIDO"),
    DENEGADO("DENEGADO");

    companion object {
        fun fromApi(raw: String?): PermisoDispositivo =
            entries.firstOrNull { it.api.equals(raw, ignoreCase = true) } ?: NO_SOLICITADO
    }
}

data class PreferenciasExternas(
    val mantenimiento: Boolean,
    val soat: Boolean,
    val kilometraje: Boolean,
    val permisoDispositivo: PermisoDispositivo
)

sealed class ErrorPreferencias(message: String) : Exception(message) {
    class Sesion : ErrorPreferencias("Inicia sesión para continuar.")
    class Red(mensaje: String) : ErrorPreferencias(mensaje)
}
