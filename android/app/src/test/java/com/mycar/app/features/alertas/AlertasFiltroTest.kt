package com.mycar.app.features.alertas

import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.viewmodel.AlertasViewModel
import com.mycar.app.features.alertas.viewmodel.FiltroAlertas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertasFiltroTest {
    private val muestra = listOf(
        DatosAlerta(1, "Aceite", "POR_TIEMPO", "PROGRAMADA", null, null, null),
        DatosAlerta(2, "Filtros", "POR_KILOMETRAJE", "PROXIMA", null, 60000, null),
        DatosAlerta(3, "Frenos", "AMBOS", "PENDIENTE", null, null, null),
        DatosAlerta(4, "Batería", "POR_TIEMPO", "VENCIDA", null, null, null),
        DatosAlerta(5, "Otro", "POR_TIEMPO", "REALIZADA", null, null, null),
        DatosAlerta(6, "Vieja", "POR_TIEMPO", "ANULADA", null, null, null)
    )

    @Test fun todasExcluyeAnulada() {
        val r = AlertasViewModel.filtrar(muestra, FiltroAlertas.TODAS)
        assertEquals(5, r.size)
        assertTrue(r.none { it.estado == "ANULADA" })
    }

    @Test fun chipsPorEstado() {
        assertEquals(1, AlertasViewModel.filtrar(muestra, FiltroAlertas.PROGRAMADAS).size)
        assertEquals(1, AlertasViewModel.filtrar(muestra, FiltroAlertas.PROXIMAS).size)
        assertEquals(1, AlertasViewModel.filtrar(muestra, FiltroAlertas.PENDIENTES).size)
        assertEquals(1, AlertasViewModel.filtrar(muestra, FiltroAlertas.VENCIDAS).size)
        assertEquals(1, AlertasViewModel.filtrar(muestra, FiltroAlertas.REALIZADAS).size)
    }

    @Test fun haySeisFiltrosSinAnuladas() {
        assertEquals(6, FiltroAlertas.entries.size)
        assertTrue(FiltroAlertas.entries.none { it.name.contains("ANUL") })
    }
}
