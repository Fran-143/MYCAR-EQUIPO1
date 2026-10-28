package com.mycar.app.features.sesion.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.util.UUID

/** UUID técnico, sin contraseña ni token. Excluido de backup para no copiar instalaciones. */
object InstalacionMyCar {
    @Synchronized fun obtener(context: Context): String {
        val archivo = AtomicFile(File(context.noBackupFilesDir, "instalacion-mycar.txt"))
        if (archivo.baseFile.exists() || File(archivo.baseFile.path + ".bak").exists()) {
            return archivo.openRead().bufferedReader().use { UUID.fromString(it.readText()).toString() }
        }
        val id = UUID.randomUUID().toString()
        val salida = archivo.startWrite()
        try { salida.write(id.toByteArray(Charsets.UTF_8)); archivo.finishWrite(salida) }
        catch (e: Exception) { archivo.failWrite(salida); throw IOException("No se pudo preparar la instalación", e) }
        return id
    }
}
