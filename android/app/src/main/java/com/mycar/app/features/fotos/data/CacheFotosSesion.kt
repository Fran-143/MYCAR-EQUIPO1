package com.mycar.app.features.fotos.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché de bytes de fotos privadas por ciclo de sesión.
 * Se limpia en logout / cambio de cuenta vía [com.mycar.app.features.sesion.data.DatosPrivadosSesion].
 */
class CacheFotosSesion(private val directorio: File) {
    private val memoria = ConcurrentHashMap<String, ByteArray>()

    init {
        directorio.mkdirs()
    }

    fun obtener(clave: String): ByteArray? {
        memoria[clave]?.let { return it }
        val archivo = archivo(clave)
        if (!archivo.isFile || archivo.length() <= 0L) return null
        return runCatching { archivo.readBytes() }.getOrNull()?.also { memoria[clave] = it }
    }

    fun guardar(clave: String, bytes: ByteArray) {
        memoria[clave] = bytes
        runCatching {
            directorio.mkdirs()
            val tmp = File(directorio, "$clave.tmp")
            tmp.writeBytes(bytes)
            val dest = archivo(clave)
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        }
    }

    fun invalidar(clave: String) {
        memoria.remove(clave)
        runCatching { archivo(clave).delete() }
    }

    fun limpiar() {
        memoria.clear()
        runCatching {
            directorio.listFiles()?.forEach { f ->
                if (f.isFile) f.delete()
            }
            directorio.delete()
        }
    }

    private fun archivo(clave: String): File {
        val seguro = clave.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(directorio, "$seguro.bin")
    }

    companion object {
        const val CLAVE_PERFIL = "perfil"
        fun claveVehiculo(id: Long) = "vehiculo-$id"
    }
}
