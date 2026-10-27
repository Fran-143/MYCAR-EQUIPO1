package com.mycar.app.features.mantenimiento.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import java.io.File
import java.security.MessageDigest

/** Solo solicitud, nunca tokens. Clave opaca de cuenta proporcionada por la sesión del equipo. */
class ArchivoRegistroPendiente(context: Context, claveCuenta: String) {
    init { require(claveCuenta.isNotBlank()) { "Se requiere una cuenta para aislar el borrador" } }
    private val gson = Gson()
    private val nombre = MessageDigest.getInstance("SHA-256").digest(claveCuenta.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val archivo = AtomicFile(File(context.filesDir, "rf17-pendiente-$nombre.json"))
    fun leer(): BorradorMantenimiento? {
        if (!archivo.baseFile.exists()) return null
        val texto = archivo.openRead().bufferedReader().use { it.readText() }
        return requireNotNull(gson.fromJson(texto, BorradorMantenimiento::class.java))
            .also { require(it.envioPendiente && it.idVehiculo != null && it.idOperacion.isNotBlank()) }
    }
    fun guardar(b: BorradorMantenimiento) {
        val flujo = archivo.startWrite()
        try {
            flujo.write(gson.toJson(b).toByteArray(Charsets.UTF_8))
            archivo.finishWrite(flujo)
        } catch (e: Exception) { archivo.failWrite(flujo); throw e }
    }
    fun borrar() = archivo.delete()
}
