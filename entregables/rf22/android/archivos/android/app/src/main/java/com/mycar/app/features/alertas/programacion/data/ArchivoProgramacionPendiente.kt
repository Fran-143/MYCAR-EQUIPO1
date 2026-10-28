package com.mycar.app.features.alertas.programacion.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.mycar.app.features.alertas.programacion.domain.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import com.mycar.app.features.autenticacion.data.AuthRepository

/** Operación sin tokens, en noBackupFilesDir, separada por cuenta validada por el servidor. */
class ArchivoProgramacionPendiente(context: Context, claveCuenta: String) : PendientesProgramacion {
    private val gson = Gson()
    private var cerrado = false
    private val archivo: AtomicFile
    private data class Sobre(val version: Int, val solicitud: SolicitudRf22)
    init {
        require(claveCuenta.isNotBlank())
        val nombre = MessageDigest.getInstance("SHA-256").digest(claveCuenta.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        archivo = AtomicFile(File(context.noBackupFilesDir, "rf22-pendiente-$nombre.json"))
    }
    @Synchronized override fun leer(): SolicitudRf22? {
        check(!cerrado)
        if (!archivo.baseFile.exists() && !File(archivo.baseFile.path + ".bak").exists()) return null
        return try {
            require(maxOf(archivo.baseFile.length(), File(archivo.baseFile.path + ".bak").length()) <= 262144)
            val sobre = archivo.openRead().bufferedReader(Charsets.UTF_8).use { gson.fromJson(it, Sobre::class.java) }
            require(sobre.version == 1); UUID.fromString(sobre.solicitud.idOperacion)
            RemoteProgramacionRepository.validarRevision(sobre.solicitud.revision)
            sobre.solicitud
        } catch (e: Exception) { throw IOException("No se pudo leer la operación pendiente. Conservamos el archivo para revisarlo.", e) }
    }
    @Synchronized override fun guardar(solicitud: SolicitudRf22) {
        check(!cerrado); RemoteProgramacionRepository.validarRevision(solicitud.revision); UUID.fromString(solicitud.idOperacion)
        val anterior = leer(); if (anterior != null && anterior != solicitud) throw IOException("Existe otra operación pendiente")
        val salida = archivo.startWrite()
        try { salida.write(gson.toJson(Sobre(1, solicitud)).toByteArray(Charsets.UTF_8)); archivo.finishWrite(salida) }
        catch (e: Exception) { archivo.failWrite(salida); throw IOException("No se pudo conservar la operación", e) }
    }
    @Synchronized override fun borrar() { check(!cerrado); archivo.delete() }
    @Synchronized override fun cerrar(borrar: Boolean) { cerrado = true; if (borrar) archivo.delete() }

    companion object {
        /** RF06 explícito: solo archivos temporales RF22, sin tocar BD, RF17 ni otros módulos. */
        fun limpiarLocales(context: Context) {
            val archivos = context.noBackupFilesDir.listFiles().orEmpty().filter {
                it.name.startsWith("rf22-pendiente-") && (it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".json.new"))
            }
            archivos.forEach { archivo ->
                if (!archivo.delete() && archivo.exists()) throw IOException("No se pudo completar la limpieza local RF22")
            }
        }
    }
}

/** Usa el mismo bloqueo de cierre que el almacén cifrado de Elsa; no contiene otra credencial. */
class PendientesSesionRf22(private val sesiones: AuthRepository, private val archivo: PendientesProgramacion,
    private val contextoVersion: Long) : PendientesProgramacion {
    override fun leer() = sesiones.withLocalSessionContext(contextoVersion) { archivo.leer() }
    override fun guardar(solicitud: SolicitudRf22) = sesiones.withLocalSessionContext(contextoVersion) { archivo.guardar(solicitud) }
    override fun borrar() = sesiones.withLocalSessionContext(contextoVersion) { archivo.borrar() }
    override fun cerrar(borrar: Boolean) = archivo.cerrar(borrar)
}
