package com.mycar.app.features.vehiculos.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.mycar.app.features.vehiculos.domain.SolicitudRegistroPendienteStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Archivo privado y excluido de backup, separado por servidor y cuenta real. No guarda credenciales. */
class ArchivoSolicitudRegistroPendiente(
    context: Context,
    origenApi: String,
    cuentaAutenticada: Long
) : SolicitudRegistroPendienteStore {
    private val gson = Gson()
    private val archivo: AtomicFile

    init {
        require(cuentaAutenticada > 0 && origenApi.isNotBlank())
        val directorio = File(context.applicationContext.noBackupFilesDir, "registros-vehiculo")
        if (!directorio.isDirectory && !directorio.mkdirs()) throw IOException("No se pudo preparar el almacenamiento local")
        val huella = MessageDigest.getInstance("SHA-256")
            .digest("$origenApi|$cuentaAutenticada".toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
        archivo = AtomicFile(File(directorio, "$huella.json"))
    }

    @Synchronized override fun cargar(): SolicitudRegistroVehiculoDto? {
        if (!archivo.baseFile.exists() && !File(archivo.baseFile.path + ".bak").exists()) return null
        try {
            val solicitud = archivo.openRead().bufferedReader(Charsets.UTF_8).use {
                gson.fromJson(it, SolicitudRegistroVehiculoDto::class.java)
            } ?: throw IOException("Solicitud local vacía")
            UUID.fromString(solicitud.idOperacion)
            if (solicitud.marca.isNullOrBlank() || solicitud.placa.isNullOrBlank())
                throw IOException("Solicitud local incompleta")
            return solicitud
        } catch (error: Exception) {
            // No borrar ni sustituir una operación cuyo resultado podría ser incierto.
            throw IOException("No se pudo recuperar la solicitud pendiente", error)
        }
    }

    @Synchronized override fun guardar(solicitud: SolicitudRegistroVehiculoDto) {
        val anterior = cargar()
        if (anterior != null && anterior != solicitud)
            throw IOException("Ya existe una solicitud pendiente diferente")
        val salida = archivo.startWrite()
        try {
            salida.write(gson.toJson(solicitud).toByteArray(Charsets.UTF_8))
            archivo.finishWrite(salida)
            if (cargar() != solicitud) throw IOException("No se pudo verificar la solicitud conservada")
        } catch (error: Exception) {
            archivo.failWrite(salida)
            throw IOException("No se pudo conservar la solicitud", error)
        }
    }

    @Synchronized override fun eliminar(idOperacion: String) {
        val solicitud = cargar() ?: return
        if (solicitud.idOperacion != idOperacion) throw IOException("La operación pendiente no coincide")
        archivo.delete()
        if (archivo.baseFile.exists()) throw IOException("No se pudo cerrar la solicitud local")
    }
}
