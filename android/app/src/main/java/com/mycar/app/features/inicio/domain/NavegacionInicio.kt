package com.mycar.app.features.inicio.domain

enum class SeccionMyCar(val titulo: String) {
    INICIO("Inicio"),
    VEHICULOS("Vehículos"),
    /** Historial integral: pestañas Mantenimientos / Kilometraje / Talleres. */
    MANTENIMIENTOS("Historial"),
    ALERTAS("Alertas")
}
enum class SecundariaMyCar(val titulo: String) {
    PERFIL("Perfil"),
    NOTIFICACIONES("Notificaciones"),
    DETALLE("Detalle de vehículo"),
    EDITAR_VEHICULO("Editar datos"),
    KILOMETRAJE("Actualizar kilometraje"),
    SOAT_REGISTRAR("Registrar SOAT"),
    SOAT_CONSULTAR("Consultar SOAT"),
    SOAT_CONFIGURAR("Configurar SOAT"),
    SOAT_CORREGIR("Corregir SOAT"),
    SOAT_RENOVAR("Renovar SOAT"),
    SOAT_RECORDATORIO("Recordatorio SOAT"),
    TIVE_REGISTRAR("Registrar TIVE"),
    TIVE_CONSULTAR("Consultar TIVE"),
    TIVE_EDITAR("Editar TIVE"),
    DIAGNOSTICO("Conexión")
}
data class NavegacionInicio(val seccion: SeccionMyCar=SeccionMyCar.INICIO, val secundaria: SecundariaMyCar?=null) {
    fun abrir(destino: SeccionMyCar) = copy(seccion=destino,secundaria=null)
    fun abrir(destino: SecundariaMyCar) = copy(secundaria=destino)
    /** Vuelve a la pantalla previa del flujo (SOAT/detalle), no al listado de sección. */
    fun atras() = when (secundaria) {
        SecundariaMyCar.SOAT_CORREGIR,
        SecundariaMyCar.SOAT_RENOVAR,
        SecundariaMyCar.SOAT_RECORDATORIO -> copy(secundaria = SecundariaMyCar.SOAT_CONFIGURAR)
        SecundariaMyCar.SOAT_CONFIGURAR,
        SecundariaMyCar.SOAT_CONSULTAR,
        SecundariaMyCar.SOAT_REGISTRAR,
        SecundariaMyCar.TIVE_REGISTRAR,
        SecundariaMyCar.TIVE_CONSULTAR,
        SecundariaMyCar.TIVE_EDITAR,
        SecundariaMyCar.EDITAR_VEHICULO,
        SecundariaMyCar.KILOMETRAJE -> copy(secundaria = SecundariaMyCar.DETALLE)
        null -> copy(seccion = SeccionMyCar.INICIO)
        else -> copy(secundaria = null)
    }
}
