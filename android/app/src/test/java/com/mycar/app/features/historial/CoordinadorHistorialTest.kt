package com.mycar.app.features.historial

import com.mycar.app.features.historial.domain.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CoordinadorHistorialTest {
    private val activo = VehiculoHistorial(10, "ABC123", "Toyota", "Corolla", "ACTIVO", true)
    private val inactivo = VehiculoHistorial(11, "DEF456", "Nissan", "Versa", "INACTIVO", false)
    private val registro = MantenimientoHistorial(20, "CAMBIO_ACEITE", "Cambio de aceite", null,
        LocalDate.of(2026, 10, 10), 47000, "REALIZADO", null)

    private class Repositorio : HistorialRepository {
        var lista: List<VehiculoHistorial> = emptyList()
        var consultas = mutableListOf<Long>()
        var respuesta: suspend (Long) -> ResultadoHistorial = { error("Sin respuesta de prueba") }
        var falloContexto = false
        override suspend fun vehiculos(): List<VehiculoHistorial> {
            if (falloContexto) throw ErrorHistorial(CodigoErrorHistorial.CONEXION, "Prueba")
            return lista
        }
        override suspend fun historial(idVehiculo: Long): ResultadoHistorial {
            consultas += idVehiculo
            return respuesta(idVehiculo)
        }
    }

    @Test fun principalCargaAutomaticamenteYSinPrincipalEsperaEleccion() = runBlocking {
        val r = Repositorio().apply { lista = listOf(activo, inactivo); respuesta = { ResultadoHistorial(activo, listOf(registro)) } }
        val c = CoordinadorHistorial(r)
        c.cargar()
        assertEquals(listOf(10L), r.consultas)
        assertEquals(1, c.estado.value.contador)
        assertTrue(c.estado.value.puedeRegistrar)
        r.lista = listOf(activo.copy(principal = false), inactivo)
        r.consultas.clear()
        c.cargar(idPreferido = null)
        assertEquals(FaseHistorial.SIN_SELECCION, c.estado.value.fase)
        assertTrue(r.consultas.isEmpty())
    }

    @Test fun sinVehiculosNoConsultaHistorial() = runBlocking {
        val r = Repositorio(); val c = CoordinadorHistorial(r)
        c.cargar()
        assertEquals(FaseHistorial.SIN_VEHICULOS, c.estado.value.fase)
        assertNull(c.estado.value.contador)
        assertTrue(r.consultas.isEmpty())
    }

    @Test fun inactivoEsConsultablePeroNoPermiteRegistrar() = runBlocking {
        val r = Repositorio().apply { lista = listOf(inactivo); respuesta = { ResultadoHistorial(inactivo, listOf(registro)) } }
        val c = CoordinadorHistorial(r); c.cargar(); c.seleccionar(11)
        assertEquals(FaseHistorial.LISTO, c.estado.value.fase)
        assertFalse(c.estado.value.puedeRegistrar)
        assertFalse(c.estado.value.seleccionado!!.principal)
    }

    @Test fun listaVaciaExitosaTieneContadorCero() = runBlocking {
        val r = Repositorio().apply { lista = listOf(activo); respuesta = { ResultadoHistorial(activo, emptyList()) } }
        val c = CoordinadorHistorial(r); c.cargar()
        assertEquals(FaseHistorial.LISTO, c.estado.value.fase)
        assertEquals(0, c.estado.value.contador)
    }

    @Test fun respuestaAtrasadaNoReemplazaVehiculoActualYSeOcultanTarjetasDuranteCarga() = runBlocking {
        val a = CompletableDeferred<ResultadoHistorial>(); val b = CompletableDeferred<ResultadoHistorial>()
        val iniciaA = CompletableDeferred<Unit>(); val iniciaB = CompletableDeferred<Unit>()
        val r = Repositorio().apply {
            lista = listOf(activo.copy(principal = false), inactivo)
            respuesta = { if (it == 10L) { iniciaA.complete(Unit); a.await() } else { iniciaB.complete(Unit); b.await() } }
        }
        val c = CoordinadorHistorial(r); c.cargar()
        val trabajoA = launch { c.seleccionar(10) }; iniciaA.await()
        val trabajoB = launch { c.seleccionar(11) }; iniciaB.await()
        assertEquals(FaseHistorial.CARGANDO_HISTORIAL, c.estado.value.fase)
        assertTrue(c.estado.value.mantenimientos.isEmpty()); assertNull(c.estado.value.contador)
        val registroB = registro.copy(id = 99, kmServicio = 31000)
        b.complete(ResultadoHistorial(inactivo, listOf(registroB))); trabajoB.join()
        a.complete(ResultadoHistorial(activo, listOf(registro))); trabajoA.join()
        assertEquals(11L, c.estado.value.seleccionado!!.id)
        assertEquals(listOf(registroB), c.estado.value.mantenimientos)
    }

    @Test fun falloDeConsultaNoSeDisfrazaDeVacioYReintentoRecupera() = runBlocking {
        var falla = false
        val r = Repositorio().apply {
            lista = listOf(activo)
            respuesta = { if (falla) throw ErrorHistorial(CodigoErrorHistorial.CONEXION, "Prueba")
                else ResultadoHistorial(activo, listOf(registro)) }
        }
        val c = CoordinadorHistorial(r); c.cargar(); falla = true; c.seleccionar(10)
        assertEquals(FaseHistorial.ERROR_HISTORIAL, c.estado.value.fase)
        assertTrue(c.estado.value.mantenimientos.isEmpty()); assertNull(c.estado.value.contador)
        falla = false; c.reintentar()
        assertEquals(FaseHistorial.LISTO, c.estado.value.fase); assertEquals(1, c.estado.value.contador)
    }

    @Test fun rechazoDeSeleccionConservaContextoValidoAnterior() = runBlocking {
        val r = Repositorio().apply {
            lista = listOf(activo, inactivo)
            respuesta = { if (it == 11L) throw ErrorHistorial(CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE, "Prueba")
                else ResultadoHistorial(activo, listOf(registro)) }
        }
        val c = CoordinadorHistorial(r); c.cargar(); c.seleccionar(11)
        assertEquals(10L, c.estado.value.seleccionado!!.id)
        assertEquals(listOf(registro), c.estado.value.mantenimientos)
        assertNotNull(c.estado.value.aviso)
        c.seleccionar(999)
        assertEquals(listOf(10L, 11L), r.consultas)
    }

    @Test fun sesionExpiradaRetiraTodosLosDatosDeLaCuenta() = runBlocking {
        var expirada = false
        val r = Repositorio().apply {
            lista = listOf(activo)
            respuesta = { if (expirada) throw ErrorHistorial(CodigoErrorHistorial.SESION, "Prueba")
                else ResultadoHistorial(activo, listOf(registro)) }
        }
        val c = CoordinadorHistorial(r); c.cargar(); expirada = true; c.seleccionar(10)
        assertEquals(FaseHistorial.SESION_REQUERIDA, c.estado.value.fase)
        assertTrue(c.estado.value.vehiculos.isEmpty()); assertTrue(c.estado.value.mantenimientos.isEmpty())
        assertNull(c.estado.value.seleccionado)
    }

    @Test fun refrescarConservaVehiculoElegidoYRecuperaNuevosRegistros() = runBlocking {
        var registros = emptyList<MantenimientoHistorial>()
        val r = Repositorio().apply { lista = listOf(activo, inactivo); respuesta = { id ->
            ResultadoHistorial(lista.first { it.id == id }, registros) } }
        val c = CoordinadorHistorial(r); c.cargar(); c.seleccionar(11)
        registros = listOf(registro.copy(costo = BigDecimal.ZERO))
        c.cargar()
        assertEquals(11L, c.estado.value.seleccionado!!.id)
        assertEquals(1, c.estado.value.contador)
    }

    @Test fun errorAlCargarVehiculosTieneReintentoManual() = runBlocking {
        val r = Repositorio().apply { falloContexto = true }
        val c = CoordinadorHistorial(r); c.cargar()
        assertEquals(FaseHistorial.ERROR_VEHICULOS, c.estado.value.fase)
        r.falloContexto = false; c.reintentar()
        assertEquals(FaseHistorial.SIN_VEHICULOS, c.estado.value.fase)
    }
}
