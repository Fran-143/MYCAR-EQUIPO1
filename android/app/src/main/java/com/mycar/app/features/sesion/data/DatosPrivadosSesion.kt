package com.mycar.app.features.sesion.data

import android.content.Context
import java.io.File
import java.io.IOException

/** Sólo datos locales de RF08/RF17. No toca PostgreSQL ni credenciales. */
open class GestorDatosPrivadosSesion {
    private val ciclos = linkedMapOf<String, (Boolean) -> Unit>()
    @Synchronized fun registrar(ciclo: String, cerrar: (Boolean) -> Unit) {
        registrar(ciclo, cerrar, alRegistrar = {})
    }

    /** Registra el cierre y prepara el almacén sin soltar el bloqueo de la limpieza. */
    @Synchronized fun registrar(ciclo: String, cerrar: (Boolean) -> Unit, alRegistrar: () -> Unit) {
        ciclos[ciclo] = cerrar
        alRegistrar()
    }
    @Synchronized fun retirar(ciclo: String) {
        ciclos[ciclo]?.invoke(false)
        ciclos.remove(ciclo)
    }
    fun limpiar(context: Context) = limpiar(context.noBackupFilesDir, context.filesDir)
    @Synchronized internal fun limpiar(sinBackup: File, anteriores: File) {
        var fallo = false
        ciclos.toMap().forEach { (clave, cerrar) ->
            try { cerrar(true); ciclos.remove(clave) } catch (_: Exception) { fallo = true }
        }
        // También limpiar pendientes de un proceso anterior o de un ciclo ya retirado.
        val vehiculos = File(sinBackup, "registros-vehiculo")
        val nombreVehiculo = Regex("[a-f0-9]{64}\\.json(?:\\.bak|\\.new)?")
        val nombreMantenimiento = Regex("rf17-pendiente-[a-f0-9]{64}\\.json(?:\\.bak|\\.new)?")
        fun borrar(directorio: File, patron: Regex) {
            if (!directorio.exists()) return
            val archivos = directorio.listFiles() ?: throw IOException("No se pudo revisar la limpieza local")
            archivos.filter { it.isFile && patron.matches(it.name) }.forEach {
                if (!it.delete() && it.exists()) fallo = true
            }
        }
        borrar(vehiculos, nombreVehiculo)
        borrar(sinBackup, nombreMantenimiento)
        borrar(anteriores, nombreMantenimiento)
        if (fallo) throw IOException("No se pudo completar la limpieza local de vehículos o mantenimientos")
    }
}

object DatosPrivadosSesion : GestorDatosPrivadosSesion()
