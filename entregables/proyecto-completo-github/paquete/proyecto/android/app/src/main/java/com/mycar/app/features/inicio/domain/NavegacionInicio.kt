package com.mycar.app.features.inicio.domain

enum class SeccionMyCar(val titulo: String) { INICIO("Inicio"), VEHICULOS("Vehículos"), MANTENIMIENTOS("Mantenimientos"), ALERTAS("Alertas") }
enum class SecundariaMyCar(val titulo: String) { PERFIL("Perfil"), NOTIFICACIONES("Notificaciones"), DETALLE("Detalle de vehículo"), KILOMETRAJE("Actualizar kilometraje"), DIAGNOSTICO("Conexión") }
data class NavegacionInicio(val seccion: SeccionMyCar=SeccionMyCar.INICIO, val secundaria: SecundariaMyCar?=null) {
    fun abrir(destino: SeccionMyCar) = copy(seccion=destino,secundaria=null)
    fun abrir(destino: SecundariaMyCar) = copy(secundaria=destino)
    fun atras() = if(secundaria!=null) copy(secundaria=null) else copy(seccion=SeccionMyCar.INICIO)
}
