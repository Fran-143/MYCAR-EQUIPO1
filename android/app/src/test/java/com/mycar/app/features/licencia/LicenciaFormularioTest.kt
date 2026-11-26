package com.mycar.app.features.licencia

import com.mycar.app.features.licencia.domain.LicenciaFormulario
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenciaFormularioTest {
    private val hoy = LocalDate.of(2026, 11, 15)

    @Test
    fun numeroExactoLetraYOchoDigitos() {
        assertNull(LicenciaFormulario.validarNumero("Q12345678"))
        assertNull(LicenciaFormulario.validarNumero("q12345678"))
        assertEquals(LicenciaFormulario.MSG_NUMERO, LicenciaFormulario.validarNumero("Q1234567"))
        assertEquals(LicenciaFormulario.MSG_NUMERO, LicenciaFormulario.validarNumero("Q123456789"))
        assertEquals(LicenciaFormulario.MSG_NUMERO, LicenciaFormulario.validarNumero("Q123-4567"))
        assertEquals(LicenciaFormulario.MSG_NUMERO, LicenciaFormulario.validarNumero("12345678A"))
    }

    @Test
    fun vigenciaSeisMesesYDiezAnios() {
        val ok6 = LicenciaFormulario.validarCampos("A-I", "Q12345678", "15/01/2026", "15/07/2026", hoy)
        assertTrue(ok6.isEmpty())
        val ok10 = LicenciaFormulario.validarCampos("A-I", "Q12345678", "15/01/2026", "15/01/2036", hoy)
        assertTrue(ok10.isEmpty())
        val bajo = LicenciaFormulario.validarCampos("A-I", "Q12345678", "15/01/2026", "14/07/2026", hoy)
        assertEquals(LicenciaFormulario.MSG_VENCIMIENTO_RANGO, bajo["fechaVencimiento"])
        val alto = LicenciaFormulario.validarCampos("A-I", "Q12345678", "15/01/2026", "16/01/2036", hoy)
        assertEquals(LicenciaFormulario.MSG_VENCIMIENTO_RANGO, alto["fechaVencimiento"])
    }

    @Test
    fun emisionFuturaYVencidaHistorica() {
        val fut = LicenciaFormulario.validarCampos("A-IIa", "Q12345678", "16/11/2026", "16/05/2027", hoy)
        assertEquals(LicenciaFormulario.MSG_EMISION_FUTURA, fut["fechaEmision"])
        val vencida = LicenciaFormulario.validarCampos("A-I", "Q12345678", "15/01/2020", "15/01/2030", hoy)
        assertTrue(vencida.isEmpty())
    }

    @Test
    fun mascaraYLimite() {
        assertEquals("****5678", LicenciaFormulario.enmascararNumero("Q12345678"))
        assertEquals("Q12345678", LicenciaFormulario.limitarNumero("q12345678xxx"))
    }
}
