package com.mycar.app.features.inicio

import com.mycar.app.features.sesion.data.GestorDatosPrivadosSesion
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Sólo archivos sintéticos en una carpeta temporal: no limpia los datos reales de la app. */
class LimpiezaSesionCompartidaTest {
    private data class Carpetas(val noBackupFilesDir: File, val filesDir: File)
    private fun conCarpeta(block: (Carpetas) -> Unit) {
        val raiz = Files.createTempDirectory("mycar-limpieza-test").toFile()
        val carpetas = Carpetas(File(raiz, "sin-backup").apply { mkdirs() }, File(raiz, "anterior").apply { mkdirs() })
        try { block(carpetas) } finally { raiz.deleteRecursively() }
    }
    @Test fun eliminaPendientesActualesAntiguosEIncompletosSinTocarOtrosArchivos() = conCarpeta { c ->
        val directorio = File(c.noBackupFilesDir, "registros-vehiculo").apply { mkdirs() }
        val nombres = listOf(".json", ".json.bak", ".json.new")
        val propios = nombres.flatMap { fin -> listOf(
            File(directorio, "a".repeat(64) + fin),
            File(c.noBackupFilesDir, "rf17-pendiente-" + "b".repeat(64) + fin),
            File(c.filesDir, "rf17-pendiente-" + "c".repeat(64) + fin)) }
        propios.forEach { it.writeText("fixture") }
        val otro = File(c.noBackupFilesDir, "archivo-ajeno.txt").apply { writeText("conservar") }
        GestorDatosPrivadosSesion().limpiar(c.noBackupFilesDir, c.filesDir)
        assertTrue(propios.none { it.exists() }); assertTrue(otro.exists())
    }
    @Test fun cierraEscritoresAntesDeBorrarArchivos() = conCarpeta { c ->
        val gestor = GestorDatosPrivadosSesion()
        val archivo = File(c.noBackupFilesDir, "rf17-pendiente-" + "b".repeat(64) + ".json").apply { writeText("fixture") }
        var cerrado = false
        gestor.registrar("fixture") { borrar -> assertTrue(borrar); assertTrue(archivo.exists()); cerrado = true }
        gestor.limpiar(c.noBackupFilesDir, c.filesDir)
        assertTrue(cerrado); assertFalse(archivo.exists())
    }
    @Test fun falloDeCierreNoSeConfirmaYSePuedeReintentar() = conCarpeta { c ->
        val gestor = GestorDatosPrivadosSesion(); var fallo = true; var llamadas = 0
        gestor.registrar("fixture") { llamadas++; if (fallo) throw IOException("fixture") }
        assertThrows(IOException::class.java) { gestor.limpiar(c.noBackupFilesDir, c.filesDir) }
        fallo = false; gestor.limpiar(c.noBackupFilesDir, c.filesDir)
        assertEquals(2, llamadas)
    }
}
