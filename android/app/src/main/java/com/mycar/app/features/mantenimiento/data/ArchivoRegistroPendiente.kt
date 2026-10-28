package com.mycar.app.features.mantenimiento.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Solo solicitud, nunca tokens. Clave opaca de cuenta proporcionada por la sesión del equipo. */
class ArchivoRegistroPendiente internal constructor(
    directorioActual: File,
    directorioAnterior: File,
    claveCuenta: String
) {
    constructor(context: Context, claveCuenta: String) : this(
        context.noBackupFilesDir, context.filesDir, claveCuenta)

    init { require(claveCuenta.isNotBlank()) { "Se requiere una cuenta para aislar el borrador" } }
    private val gson = Gson()
    internal val nombre = MessageDigest.getInstance("SHA-256").digest(claveCuenta.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val archivo = AtomicFile(File(directorioActual, "rf17-pendiente-$nombre.json"))
    private var abierta = true
    private val anterior = AtomicFile(File(directorioAnterior, "rf17-pendiente-$nombre.json"))

    private fun destinoOcupado() =
        archivo.baseFile.exists() || File(archivo.baseFile.path + ".bak").exists()
    private fun origenPresente() =
        anterior.baseFile.exists() || File(anterior.baseFile.path + ".bak").exists()

    /** Lee el pendiente antiguo sin publicarlo. El constructor no escribe. */
    internal fun capturarMigracion(): ByteArray? = synchronized(this) {
        if (!abierta || destinoOcupado() || !origenPresente()) null
        else anterior.openRead().use { it.readBytes() }
    }

    /**
     * Publica solo si este almacén sigue abierto.
     * Una limpieza que ya cerró el ciclo no puede dejar el archivo recién escrito.
     */
    internal fun publicarMigracion(datos: ByteArray) = synchronized(this) {
        if (!abierta || destinoOcupado()) return
        val salida = archivo.startWrite()
        try {
            salida.write(datos)
            archivo.finishWrite(salida)
            if (!archivo.openRead().use { it.readBytes() }.contentEquals(datos))
                throw IOException("No se pudo verificar el pendiente migrado")
            anterior.delete()
        } catch (e: Exception) {
            archivo.failWrite(salida)
            throw e
        }
    }

    /** Llamar solo con el cierre ya registrado, bajo el mismo bloqueo que lo ejecuta. */
    fun preparar() {
        val datos = capturarMigracion() ?: return
        publicarMigracion(datos)
    }

    private fun exigirAbierta() { if (!abierta) throw IOException("El ciclo de sesión terminó") }
    @Synchronized fun cerrar(borrarDatos: Boolean) {
        abierta=false
        if(borrarDatos) {
            archivo.delete(); anterior.delete()
            if(archivo.baseFile.exists() || File(archivo.baseFile.path+".bak").exists() || anterior.baseFile.exists() || File(anterior.baseFile.path+".bak").exists()) throw IOException("Limpieza local incompleta")
        }
    }
    @Synchronized fun leer(): BorradorMantenimiento? {
        exigirAbierta()
        preparar()
        if (!archivo.baseFile.exists() && !File(archivo.baseFile.path+".bak").exists()) return null
        val texto = archivo.openRead().bufferedReader().use { it.readText() }
        return requireNotNull(gson.fromJson(texto, BorradorMantenimiento::class.java))
            .also { require(it.envioPendiente && it.idVehiculo != null && it.idOperacion.isNotBlank()) }
    }
    @Synchronized fun guardar(b: BorradorMantenimiento) {
        exigirAbierta()
        preparar()
        val flujo = archivo.startWrite()
        try {
            flujo.write(gson.toJson(b).toByteArray(Charsets.UTF_8))
            archivo.finishWrite(flujo)
        } catch (e: Exception) { archivo.failWrite(flujo); throw e }
    }
    @Synchronized fun borrar() { exigirAbierta(); archivo.delete() }
}
