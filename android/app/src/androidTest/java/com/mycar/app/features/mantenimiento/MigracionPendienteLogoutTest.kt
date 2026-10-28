package com.mycar.app.features.mantenimiento

import androidx.test.platform.app.InstrumentationRegistry
import com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente
import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import com.mycar.app.features.sesion.data.GestorDatosPrivadosSesion
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Archivos solo en el caché del emulador de pruebas.
 * No usa la BD personal ni el almacenamiento real de la sesión.
 */
class MigracionPendienteLogoutTest {
    private data class Carpetas(val actual: File, val anterior: File)

    private fun conCarpetas(block: (Carpetas) -> Unit) {
        val raiz = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "mycar-migracion-${UUID.randomUUID()}"
        ).apply { mkdirs() }
        val carpetas = Carpetas(
            File(raiz, "actual").apply { mkdirs() },
            File(raiz, "anterior").apply { mkdirs() }
        )
        try {
            block(carpetas)
        } finally {
            raiz.deleteRecursively()
        }
    }

    private fun pendiente(directorio: File, nombre: String) =
        File(directorio, "rf17-pendiente-$nombre.json")

    private fun sembrar(carpetas: Carpetas, clave: String): Pair<ArchivoRegistroPendiente, BorradorMantenimiento> {
        val borrador = BorradorMantenimiento(
            idOperacion = UUID.randomUUID().toString(),
            idVehiculo = 7L,
            envioPendiente = true
        )
        val semilla = ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave)
        semilla.guardar(borrador)
        val destino = pendiente(carpetas.actual, semilla.nombre)
        val origen = pendiente(carpetas.anterior, semilla.nombre)
        check(destino.copyTo(origen, overwrite = true).exists())
        check(destino.delete())
        return ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave) to borrador
    }

    @Test fun constructorNoEscribeYLaLimpiezaImpidePublicarLoYaLeido() = conCarpetas { carpetas ->
        val clave = "cuenta-a-${UUID.randomUUID()}"
        val (archivo, _) = sembrar(carpetas, clave)
        val destino = pendiente(carpetas.actual, archivo.nombre)
        val origen = pendiente(carpetas.anterior, archivo.nombre)
        assertTrue(origen.exists())
        assertFalse(destino.exists())
        val leido = checkNotNull(archivo.capturarMigracion())
        assertFalse(destino.exists())
        val gestor = GestorDatosPrivadosSesion()
        val limpiezaLista = CountDownLatch(1)
        val fallo = AtomicReference<Throwable>()
        val hilo = Thread {
            try {
                limpiezaLista.await()
                archivo.publicarMigracion(leido)
            } catch (error: Throwable) {
                fallo.set(error)
            }
        }
        hilo.start()
        gestor.registrar("ciclo-a") { archivo.cerrar(it) }
        gestor.limpiar(carpetas.actual, carpetas.anterior)
        limpiezaLista.countDown()
        hilo.join()
        fallo.get()?.let { throw it }
        assertFalse(destino.exists())
        assertFalse(origen.exists())
        assertFalse(File(destino.path + ".new").exists())
        val siguiente = ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave)
        siguiente.preparar()
        assertNull(siguiente.leer())
    }

    @Test fun activacionPosteriorALaLimpiezaNoRecreaElPendiente() = conCarpetas { carpetas ->
        val clave = "cuenta-activacion-${UUID.randomUUID()}"
        val (archivo, _) = sembrar(carpetas, clave)
        val gestor = GestorDatosPrivadosSesion()
        gestor.limpiar(carpetas.actual, carpetas.anterior)
        gestor.registrar("ciclo", { archivo.cerrar(it) }) { archivo.preparar() }
        assertFalse(pendiente(carpetas.actual, archivo.nombre).exists())
        assertFalse(pendiente(carpetas.anterior, archivo.nombre).exists())
        assertNull(ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave).leer())
    }

    @Test fun invalidacionNaturalConservaElPendienteYOtraCuentaSigueAislada() = conCarpetas { carpetas ->
        val claveA = "cuenta-natural-${UUID.randomUUID()}"
        val claveB = "cuenta-ajena-${UUID.randomUUID()}"
        val (cuentaA, borradorA) = sembrar(carpetas, claveA)
        val (cuentaB, borradorB) = sembrar(carpetas, claveB)
        val gestor = GestorDatosPrivadosSesion()
        gestor.registrar("a", { cuentaA.cerrar(it) }) { cuentaA.preparar() }
        gestor.registrar("b", { cuentaB.cerrar(it) }) { cuentaB.preparar() }
        assertTrue(pendiente(carpetas.actual, cuentaA.nombre).exists())
        assertFalse(pendiente(carpetas.anterior, cuentaA.nombre).exists())
        gestor.retirar("a")
        assertEquals(borradorA.idOperacion, ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, claveA).leer()?.idOperacion)
        cuentaA.cerrar(true)
        assertFalse(pendiente(carpetas.actual, cuentaA.nombre).exists())
        assertEquals(borradorB.idOperacion, cuentaB.leer()?.idOperacion)
        assertTrue(pendiente(carpetas.actual, cuentaB.nombre).exists())
    }

    @Test fun cierreExplicitoEliminaElMigradoYNoLoRecupera() = conCarpetas { carpetas ->
        val clave = "cuenta-cierre-${UUID.randomUUID()}"
        val (archivo, _) = sembrar(carpetas, clave)
        val gestor = GestorDatosPrivadosSesion()
        gestor.registrar("ciclo", { archivo.cerrar(it) }) { archivo.preparar() }
        assertTrue(pendiente(carpetas.actual, archivo.nombre).exists())
        gestor.limpiar(carpetas.actual, carpetas.anterior)
        val siguiente = ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave)
        siguiente.preparar()
        assertNull(siguiente.leer())
        assertFalse(pendiente(carpetas.actual, archivo.nombre).exists())
    }

    @Test fun activacionYLimpiezaConcurrentesNoDejanPendiente() = conCarpetas { carpetas ->
        repeat(8) { indice ->
            val clave = "cuenta-conc-$indice-${UUID.randomUUID()}"
            val (archivo, _) = sembrar(carpetas, clave)
            val gestor = GestorDatosPrivadosSesion()
            val arranque = CountDownLatch(1)
            val listos = CountDownLatch(2)
            val fallo = AtomicReference<Throwable>()
            val activar = Thread {
                try {
                    listos.countDown()
                    arranque.await()
                    gestor.registrar("ciclo", { archivo.cerrar(it) }) { archivo.preparar() }
                } catch (error: Throwable) {
                    fallo.set(error)
                }
            }
            val limpiar = Thread {
                try {
                    listos.countDown()
                    arranque.await()
                    gestor.limpiar(carpetas.actual, carpetas.anterior)
                } catch (error: Throwable) {
                    fallo.set(error)
                }
            }
            activar.start()
            limpiar.start()
            listos.await()
            arranque.countDown()
            activar.join()
            limpiar.join()
            fallo.get()?.let { throw it }
            assertFalse(pendiente(carpetas.actual, archivo.nombre).exists())
            assertFalse(pendiente(carpetas.anterior, archivo.nombre).exists())
            assertNull(ArchivoRegistroPendiente(carpetas.actual, carpetas.anterior, clave).leer())
        }
    }
}
