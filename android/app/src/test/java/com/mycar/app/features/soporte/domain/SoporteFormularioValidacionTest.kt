package com.mycar.app.features.soporte.domain

import com.mycar.app.features.vehiculos.domain.ValidadorVin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoporteFormularioValidacionTest {
    private val actual = "1HGCM82633A004352"
    private val nuevo = "1HGCM82633A004359"

    @Test fun vinInvalidoImpide() {
        val e = SoporteFormulario.validarNueva("ABC", actual, "MOTIVO DE PRUEBA SUFICIENTE")
        assertTrue(e.containsKey("vinPropuesto"))
    }

    @Test fun motivoVacio() {
        val e = SoporteFormulario.validarNueva(nuevo, actual, "")
        assertEquals("Describe el motivo de la corrección", e["motivo"])
    }

    @Test fun motivoLargo() {
        val e = SoporteFormulario.validarNueva(nuevo, actual, "X".repeat(251))
        assertTrue(e["motivo"]!!.contains("250"))
    }

    @Test fun evidenciaGrande() {
        val msg = SoporteFormulario.validarEvidencia("image/jpeg", 11L * 1024 * 1024)
        assertTrue(msg!!.contains("10 MB"))
    }

    @Test fun vinIgual() {
        val e = SoporteFormulario.validarNueva(actual, actual, "ERROR DE DIGITACION AL REGISTRAR")
        assertEquals(ValidadorVin.MSG_IGUAL, e["vinPropuesto"])
    }

    @Test fun mayusculasMotivo() {
        assertEquals("ERROR DE DIGITACIÓN", SoporteFormulario.normalizarMotivo("error de digitación"))
    }
}
