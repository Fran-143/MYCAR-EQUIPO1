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

    private val hoy = LocalDate.of(2026, 11, 15)

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
    fun vigenciaAnual_aceptaAniversarioYUltimoDiaIncluido() {
        val aniversario = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(aniversario.isEmpty())

        val ultimoIncluido = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "14/11/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(ultimoIncluido.isEmpty())
    }

    @Test
    fun vigenciaAnual_rechazaSeisYDosAnios() {
        val seis = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2020", "15/11/2026", "", "",
            exigirVencimientoFuturo = false, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_VIGENCIA_ANUAL, seis["fechaVencimiento"])

        val dos = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2028", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_VIGENCIA_ANUAL, dos["fechaVencimiento"])
    }

    @Test
    fun vigenciaAnual_bisiesto() {
        assertTrue(
            SoatFormulario.esVigenciaAnualOrdinaria(
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28)
            )
        )
        assertTrue(
            SoatFormulario.esVigenciaAnualOrdinaria(
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 27)
            )
        )
    }

    @Test
    fun rn_rechazaVencimientoPasadoEnAlta() {
        val err = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/01/2024", "15/01/2025", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_FECHA_MENOR_HOY, err["fechaVencimiento"])
    }

    @Test
    fun corregir_permiteVencimientoPasadoSiAnual() {
        val ok = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/01/2024", "15/01/2025", "", "",
            exigirVencimientoFuturo = false, exigirCatalogoActivo = false, hoy = hoy
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
    fun catalogoApesegNueveActivas() {
        assertEquals(9, AseguradoraSoat.activas().size)
        assertTrue(AseguradoraSoat.esValidaParaAlta("PACÍFICO SEGUROS"))
        assertTrue(AseguradoraSoat.esValidaParaAlta("VIVIR SEGUROS"))
        assertTrue(AseguradoraSoat.esValidaParaAlta("QUÁLITAS PERÚ"))
        assertFalse(AseguradoraSoat.esValidaParaAlta("HDI SEGUROS"))
        assertTrue(AseguradoraSoat.esReconocida("HDI SEGUROS"))
        val err = SoatFormulario.validarCampos(
            "ASEGURADORA INVENTADA", "0124589", "15/11/2026", "15/11/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_ASEGURADORA, err["aseguradora"])
    }

    @Test
    fun polizaGeneral_sinLongitudFija() {
        assertEquals(SoatFormulario.MSG_POLIZA_VACIA, SoatFormulario.validarPoliza(""))
        assertEquals(SoatFormulario.MSG_POLIZA, SoatFormulario.validarPoliza("ABC@123"))
        assertNull(SoatFormulario.validarPoliza("0124589"))
        assertNull(SoatFormulario.validarPoliza("012-4589"))
        assertNull(SoatFormulario.validarPoliza("001234567890"))
        assertNull(SoatFormulario.validarPoliza("AB-12/34.5", "INTERSEGURO"))
        // Interseguro: ejemplo 8 dígitos NO es obligatorio
        assertNull(SoatFormulario.validarPoliza("1234567", "INTERSEGURO"))
        assertNull(SoatFormulario.validarPoliza("123456789", "INTERSEGURO"))
        assertEquals("0124589999", SoatFormulario.limitarPoliza("0124589999"))
        assertEquals("012-4589", SoatFormulario.normalizarPoliza(" 012-4589 "))
        assertTrue(SoatFormulario.normalizarPoliza("00-123").startsWith("00"))
    }

    @Test
    fun cambioAseguradora_revalidaConPerfil() {
        assertNotNull(AseguradoraSoat.ayudaPoliza("RÍMAC SEGUROS"))
        assertEquals("12345678", AseguradoraSoat.ejemploPoliza("INTERSEGURO"))
        assertTrue(AseguradoraSoat.ayudaPoliza("INTERSEGURO").contains("ilustrativo"))
    }

    @Test
    fun mascaraYParseoFecha() {
        assertEquals("01/12/2026", SoatFormulario.aplicarMascaraFecha("01122026"))
        assertEquals(LocalDate.of(2026, 12, 1), SoatFormulario.parsearFecha("01/12/2026"))
        assertEquals(LocalDate.of(2026, 12, 1), SoatFormulario.parsearFecha("2026-12-01"))
        assertNull(SoatFormulario.parsearFecha("31/02/2027"))
        assertEquals("2026-12-01", SoatFormulario.fechaUiAIso("01/12/2026"))
        assertEquals("11/11", SoatFormulario.aplicarMascaraFecha("1111"))
        assertEquals("11/1", SoatFormulario.aplicarMascaraFecha("111"))
        assertEquals("11", SoatFormulario.aplicarMascaraFecha("11"))
        assertEquals("11/11/2", SoatFormulario.aplicarMascaraFecha("11112"))
    }

    @Test
    fun rf30Ea07_primaNegativaYTope() {
        val err = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "-10",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, err["importePrima"])
        val letras = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "abc",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, letras["importePrima"])
        val decimalOk = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "150.50",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(decimalOk.isEmpty())
        val tope = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "1000000",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_PRIMA_INVALIDA, tope["importePrima"])
        assertEquals("999999.99", SoatFormulario.limitarPrima("9999999.999"))
        assertEquals("150.00", SoatFormulario.limitarPrima("150.00"))
    }

    @Test
    fun enmascaraPolizaUltimosCuatro() {
        assertEquals("****5678", SoatFormulario.enmascararPoliza("Q12345678"))
        assertEquals("****", SoatFormulario.enmascararPoliza("AB"))
        assertEquals("", SoatFormulario.enmascararPoliza(""))
    }

    @Test
    fun rechazaInicioPosteriorAHoy() {
        val err = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "01/01/2036", "01/01/2037", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_INICIO_FUTURO, err["fechaInicio"])
        val hoyOk = SoatFormulario.validarCampos(
            "RÍMAC SEGUROS", "0124589", "15/11/2026", "15/11/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(hoyOk.isEmpty())
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
            "02/01/2026", "02/01/2027", "01/01/2025", "01/01/2026",
            hoy = LocalDate.of(2026, 1, 2)
        )
        assertTrue(ok.isEmpty())

        val futuroLejos = SoatFormulario.validarFechasRenovacion(
            "01/01/2036", "01/01/2037", "01/01/2025", "01/01/2026",
            hoy = hoy
        )
        assertEquals(SoatFormulario.MSG_INICIO_FUTURO, futuroLejos["fechaInicio"])

        // Renovar el día de vencimiento: se tolera inicio = vencimiento+1.
        val diaSiguiente = SoatFormulario.validarFechasRenovacion(
            "16/11/2026", "16/11/2027", "15/11/2025", "15/11/2026",
            hoy = hoy
        )
        assertTrue(diaSiguiente.isEmpty())
    }

    @Test
    fun regresion_camposOpcionalesVaciosNoInvalidanParseo() {
        val ok = SoatFormulario.validarCampos(
            "MAPFRE PERÚ", "9876543", "15/11/2026", "15/11/2027", "", "",
            exigirVencimientoFuturo = true, hoy = hoy
        )
        assertTrue(ok.isEmpty())
        assertNull(SoatFormulario.parsearFecha(""))
        assertNull(SoatFormulario.parsearFecha(null))
    }
}
