package com.mycar.app.features.preferenciasnotif.domain

interface PreferenciasNotificacionRepository {
    suspend fun consultar(): PreferenciasExternas
    suspend fun guardar(
        mantenimiento: Boolean,
        soat: Boolean,
        kilometraje: Boolean,
        permisoDispositivo: PermisoDispositivo
    ): PreferenciasExternas

    suspend fun actualizarPermisoDispositivo(permiso: PermisoDispositivo): PreferenciasExternas
}
