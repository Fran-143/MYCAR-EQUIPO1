package com.mycar.app.features.historial

import com.mycar.app.features.historial.domain.FiltroHistorialMantenimientos
import com.mycar.app.features.historial.domain.MantenimientoHistorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FiltroHistorialMantenimientosTest {
    private val a = MantenimientoHistorial(1, "FILTROS", "Cambio de filtros", null, LocalDate.of(2026, 11, 5), 120, "REALIZADO", null)
    private val b = MantenimientoHistorial(2, "FILTROS", "Cambio de filtros", null, LocalDate.of(2026, 11, 4), 70, "REALIZADO", null)
    private val c = MantenimientoHistorial(3, "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 10, 20), 50, "ANULADO", null)
    private val lista = listOf(a, b, c)

    @Test fun todosConservaTodosYOrdenRecientes() {
        val r = FiltroHistorialMantenimientos.aplicar(lista, null, true)
        assertEquals(listOf(1L, 2L, 3L), r.map { it.id })
    }

    @Test fun masAntiguosInvierteOrden() {
        val r = FiltroHistorialMantenimientos.aplicar(lista, null, false)
        assertEquals(listOf(3L, 2L, 1L), r.map { it.id })
    }

    @Test fun filtroTipoYOrdenCombinados() {
        val r = FiltroHistorialMantenimientos.aplicar(lista, "FILTROS", false)
        assertEquals(listOf(2L, 1L), r.map { it.id })
        assertTrue(r.all { it.codigoTipo == "FILTROS" })
    }

    @Test fun tiposPresentesIncluyeHistoricos() {
        val tipos = FiltroHistorialMantenimientos.tiposPresentes(lista)
        assertEquals(2, tipos.size)
        assertTrue(tipos.any { it.first == "FILTROS" })
        assertTrue(tipos.any { it.first == "CAMBIO_ACEITE" })
    }

    @Test fun sinCoincidenciasDevuelveVacio() {
        assertTrue(FiltroHistorialMantenimientos.aplicar(lista, "FRENOS", true).isEmpty())
    }
}
