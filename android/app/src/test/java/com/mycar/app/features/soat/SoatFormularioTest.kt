package com.mycar.app.features.soat

import com.mycar.app.features.soat.domain.CamposSoat
import com.mycar.app.features.soat.domain.SoatFormulario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoatFormularioTest {

    @Test
    fun rf30Ea01_rechazaFechasIgualesYAnteriores() {
        val iguales = SoatFormulario.validarCampos("Rimac", "POL", "2026-12-01", "2026-12-01", "", "")
        assertEquals(SoatFormulario.MSG_VENCIMIENTO_POSTERIOR, iguales["fechaVencimiento"])

        val anteriores = SoatFormulario.validarCampos("Rimac", "POL", "2026-12-01", "2026-11-30", "", "")
        assertEquals(SoatFormulario.MSG_VENCIMIENTO_POSTERIOR, anteriores["fechaVencimiento"])
    }

    @Test
    fun rf30Ea01_aceptaVencimientoPosterior() {
        val ok = SoatFormulario.validarCampos("Rimac", "POL", "2026-12-01", "2026-12-02", "", "")
        assertTrue(ok.isEmpty())
    }

    @Test
    fun rf30Ea03_camposVacios() {
        val err = SoatFormulario.validarCampos("", "", "", "", "", "")
        assertTrue(err.containsKey("aseguradora"))
        assertTrue(err.containsKey("numeroPoliza"))
        assertTrue(err.containsKey("fechaInicio"))
        assertTrue(err.containsKey("fechaVencimiento"))
    }

    @Test
    fun rf30Ea07_primaNegativa() {
        val err = SoatFormulario.validarCampos("Rimac", "POL", "2026-01-01", "2027-01-01", "", "-10")
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, err["importePrima"])
    }

    @Test
    fun rf31Ea02_sinCambios() {
        val base = CamposSoat("Rimac", "POL", "2026-01-01", "2027-01-01", "", "", "100")
        assertFalse(SoatFormulario.hayCambiosRespectoA(base, base, "MANTENER"))
        assertTrue(SoatFormulario.hayCambiosRespectoA(base, base.copy(aseguradora = "Pacifico"), "MANTENER"))
        assertTrue(SoatFormulario.hayCambiosRespectoA(base, base, "REEMPLAZAR"))
    }

    @Test
    fun rf30Ea06_vencimientoPasado() {
        assertTrue(SoatFormulario.vencimientoYaPasado("2020-01-01", java.time.LocalDate.of(2026, 11, 14)))
        assertFalse(SoatFormulario.vencimientoYaPasado("2030-01-01", java.time.LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun rf31Ea01Ea04_renovacion() {
        val solape = SoatFormulario.validarFechasRenovacion(
            "2025-12-01", "2027-01-01", "2025-01-01", "2026-01-01"
        )
        assertEquals(SoatFormulario.MSG_RENOV_INICIO, solape["fechaInicio"])

        val venc = SoatFormulario.validarFechasRenovacion(
            "2026-01-02", "2026-01-01", "2025-01-01", "2026-01-01"
        )
        assertEquals(SoatFormulario.MSG_RENOV_VENC, venc["fechaVencimiento"])

        val ok = SoatFormulario.validarFechasRenovacion(
            "2026-01-02", "2027-01-02", "2025-01-01", "2026-01-01"
        )
        assertTrue(ok.isEmpty())
    }
}
