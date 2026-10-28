package com.mycar.app.features.vehiculos.data

import com.mycar.app.features.vehiculos.domain.SolicitudRegistroPendienteStore
import java.io.IOException

/** El cierre y las escrituras tardías comparten el mismo bloqueo; un callback no puede recrear el archivo. */
class PendientesSesionVehiculos(private val archivo: SolicitudRegistroPendienteStore,
    private val borrarDatos: () -> Unit) : SolicitudRegistroPendienteStore {
    private var abierta = true
    @Synchronized override fun cargar(): SolicitudRegistroVehiculoDto? { exigirAbierta(); return archivo.cargar() }
    @Synchronized override fun guardar(solicitud: SolicitudRegistroVehiculoDto) { exigirAbierta(); archivo.guardar(solicitud) }
    @Synchronized override fun eliminar(idOperacion: String) { exigirAbierta(); archivo.eliminar(idOperacion) }
    @Synchronized fun cerrar(borrar: Boolean) { abierta = false; if (borrar) borrarDatos() }
    private fun exigirAbierta() { if (!abierta) throw IOException("La sesión local terminó") }
}
