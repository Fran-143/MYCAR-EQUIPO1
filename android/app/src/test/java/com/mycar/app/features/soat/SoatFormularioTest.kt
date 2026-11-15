package com.mycar.app.features.soat

import com.mycar.app.features.soat.domain.AseguradoraSoat
import com.mycar.app.features.soat.domain.CamposSoat
import com.mycar.app.features.soat.domain.SoatFormulario
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoatFormularioTest {

    private val hoy = LocalDate.of(2026, 11, 14)

    @Test
    fun rf30Ea01_rechazaFechasIgualesYAnteriores() {
        val iguales = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/12/2026", "01/12/2026", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_VENCIMIENTO_POSTERIOR, iguales["fechaVencimiento"])

        val anteriores = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/12/2026", "30/11/2026", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_VENCIMIENTO_POSTERIOR, anteriores["fechaVencimiento"])
    }

    @Test
    fun rf30_aceptaVencimientoPosteriorFuturo() {
        val ok = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/12/2026", "02/12/2026", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(ok.isEmpty())
    }

    @Test
    fun rn_rechazaVencimientoPasadoEnAlta() {
        val err = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/01/2024", "31/12/2024", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_FECHA_MENOR_HOY, err["fechaVencimiento"])
    }

    @Test
    fun corregir_permiteVencimientoPasado() {
        val ok = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/01/2024", "31/12/2024", "", "",
            exigirVencimientoFuturo = false, hoy = hoy
        )
        assertTrue(ok.isEmpty())
    }

    @Test
    fun rf30Ea03_camposVacios() {
        val err = SoatFormulario.validarCampos("", "", "", "", "", "", exigirVencimientoFuturo = true, hoy = hoy)
        assertTrue(err.containsKey("aseguradora"))
        assertTrue(err.containsKey("numeroPoliza"))
        assertTrue(err.containsKey("fechaInicio"))
        assertTrue(err.containsKey("fechaVencimiento"))
    }

    @Test
    fun aseguradoraFueraDeCatalogo() {
        val err = SoatFormulario.validarCampos(
            "ASEGURADORA INVENTADA", "0124589", "01/12/2026", "01/12/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_ASEGURADORA, err["aseguradora"])
        assertTrue(AseguradoraSoat.esValida("PACÍFICO SEGUROS"))
    }

    @Test
    fun polizaExactamente7Digitos() {
        assertEquals(SoatFormulario.MSG_POLIZA, SoatFormulario.validarPoliza("ABC-123"))
        assertEquals(SoatFormulario.MSG_POLIZA, SoatFormulario.validarPoliza("012458"))
        assertEquals(SoatFormulario.MSG_POLIZA, SoatFormulario.validarPoliza("01245890"))
        assertNull(SoatFormulario.validarPoliza("0124589"))
        assertNull(SoatFormulario.validarPoliza("012-4589"))
        assertEquals("0124589", SoatFormulario.limitarPoliza("0124589999"))
        assertEquals("012-4589", SoatFormulario.limitarPoliza("012-45899"))
        assertEquals("012-4589", SoatFormulario.normalizarPoliza(" 012-4589 "))
    }

    @Test
    fun mascaraYParseoFecha() {
        assertEquals("01/12/2026", SoatFormulario.aplicarMascaraFecha("01122026"))
        assertEquals(LocalDate.of(2026, 12, 1), SoatFormulario.parsearFecha("01/12/2026"))
        assertEquals(LocalDate.of(2026, 12, 1), SoatFormulario.parsearFecha("2026-12-01"))
        assertNull(SoatFormulario.parsearFecha("31/02/2027"))
        assertEquals("2026-12-01", SoatFormulario.fechaUiAIso("01/12/2026"))
        // Borrar: con 4 dígitos no debe quedar slash colgante (antes: 11/11/).
        assertEquals("11/11", SoatFormulario.aplicarMascaraFecha("1111"))
        assertEquals("11/1", SoatFormulario.aplicarMascaraFecha("111"))
        assertEquals("11", SoatFormulario.aplicarMascaraFecha("11"))
        assertEquals("11/11/2", SoatFormulario.aplicarMascaraFecha("11112"))
    }

    @Test
    fun rf30Ea07_primaNegativaYTope() {
        val err = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/01/2027", "01/01/2028", "", "-10",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, err["importePrima"])
        val tope = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/01/2027", "01/01/2028", "", "1000000",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, tope["importePrima"])
        assertEquals("999999.99", SoatFormulario.limitarPrima("9999999.999"))
        assertEquals("150.00", SoatFormulario.limitarPrima("150.00"))
    }

    @Test
    fun rf31Ea02_sinCambios() {
        val base = CamposSoat("RÍMAC SEGUROS", "0124589", "01/01/2026", "01/01/2027", "", "", "100")
        assertFalse(SoatFormulario.hayCambiosRespectoA(base, base, "MANTENER"))
        assertTrue(SoatFormulario.hayCambiosRespectoA(base, base.copy(aseguradora = "PACÍFICO SEGUROS"), "MANTENER"))
        assertTrue(SoatFormulario.hayCambiosRespectoA(base, base, "REEMPLAZAR"))
    }

    @Test
    fun rf30Ea06_vencimientoPasadoHelper() {
        assertTrue(SoatFormulario.vencimientoYaPasado("01/01/2020", hoy))
        assertFalse(SoatFormulario.vencimientoYaPasado("01/01/2030", hoy))
    }

    @Test
    fun renovarSoloDesdeDiaDeVencimiento() {
        assertFalse(SoatFormulario.puedeRenovar("14/11/2027", hoy))
        assertTrue(SoatFormulario.puedeRenovar("14/11/2026", hoy))
        assertTrue(SoatFormulario.puedeRenovar("01/01/2020", hoy))
        assertFalse(SoatFormulario.puedeRenovar(null, hoy))
    }

    @Test
    fun rf31Ea01Ea04_renovacion() {
        val solape = SoatFormulario.validarFechasRenovacion(
            "01/12/2025", "01/01/2027", "01/01/2025", "01/01/2026"
        )
        assertEquals(SoatFormulario.MSG_RENOV_INICIO, solape["fechaInicio"])

        val venc = SoatFormulario.validarFechasRenovacion(
            "02/01/2026", "01/01/2026", "01/01/2025", "01/01/2026"
        )
        assertEquals(SoatFormulario.MSG_RENOV_VENC, venc["fechaVencimiento"])

        val ok = SoatFormulario.validarFechasRenovacion(
            "02/01/2026", "02/01/2027", "01/01/2025", "01/01/2026"
        )
        assertTrue(ok.isEmpty())
    }

    @Test
    fun regresion_camposOpcionalesVaciosNoInvalidanParseo() {
        val ok = SoatFormulario.validarCampos(
            "MAPFRE PERÚ", "9876543", "15/01/2027", "15/01/2028", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(ok.isEmpty())
        assertNull(SoatFormulario.parsearFecha(""))
        assertNull(SoatFormulario.parsearFecha(null))
    }
}
