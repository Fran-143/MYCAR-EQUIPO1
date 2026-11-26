package com.mycar.app.features.alertas

import com.mycar.app.features.alertas.viewmodel.AmbitoAlertas
import com.mycar.app.features.alertas.viewmodel.EstadoAlertas
import com.mycar.app.features.alertas.viewmodel.FaseAlertas
import com.mycar.app.features.alertas.viewmodel.FiltroAlertas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF23: el estado de licencia es independiente del vehicular;
 * cambiar de ámbito no debe mezclar listas.
 */
class AlertasAmbitoTest {

    @Test
    fun estadoInicialEsVehiculos() {
        val e = EstadoAlertas()
        assertEquals(AmbitoAlertas.VEHICULOS, e.ambito)
    }

    @Test
    fun licenciaVaciaNoReutilizaVisiblesVehiculares() {
        val e = EstadoAlertas(
            ambito = AmbitoAlertas.LICENCIA,
            fase = FaseAlertas.LISTO,
            visibles = emptyList(),
            alertas = emptyList(),
            faseLicencia = FaseAlertas.VACIO,
            mensajeLicencia = EstadoAlertas.MENSAJE_SIN_ALERTAS_LICENCIA,
            alertasLicencia = emptyList()
        )
        assertEquals(AmbitoAlertas.LICENCIA, e.ambito)
        assertTrue(e.alertasLicencia.isEmpty())
        assertEquals(FaseAlertas.VACIO, e.faseLicencia)
        assertEquals(EstadoAlertas.TITULO_SIN_ALERTAS_LICENCIA, EstadoAlertas.TITULO_SIN_ALERTAS_LICENCIA)
        // La fase vehicular LISTO no implica contenido de licencia.
        assertNotEquals(e.fase, e.faseLicencia)
    }

    @Test
    fun cambiarAmbitoConservaFiltroVehicularEnEstado() {
        val e = EstadoAlertas(
            ambito = AmbitoAlertas.VEHICULOS,
            filtro = FiltroAlertas.PROXIMAS,
            vehiculoId = 7L
        ).copy(ambito = AmbitoAlertas.LICENCIA)
        assertEquals(AmbitoAlertas.LICENCIA, e.ambito)
        assertEquals(FiltroAlertas.PROXIMAS, e.filtro)
        assertEquals(7L, e.vehiculoId)
    }

    @Test
    fun mensajesEaDocumentados() {
        assertTrue(EstadoAlertas.MENSAJE_SIN_VEHICULOS.contains("vehículo activo"))
        assertTrue(EstadoAlertas.MENSAJE_SIN_ALERTAS_VEHICULO.contains("este vehículo"))
        assertTrue(EstadoAlertas.MENSAJE_SIN_ALERTAS_LICENCIA.contains("licencia"))
    }
}
