package com.mycar.app.features.vehiculos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorVinTest {
    private val valido = "1HGCM82633A004352"

    @Test fun vacio() = assertEquals(ValidadorVin.MSG_VACIO, ValidadorVin.mensajeError(""))

    @Test fun corto() {
        val msg = ValidadorVin.mensajeError("1HGCM82633A00435")
        assertTrue(msg!!.contains("16 de 17"))
    }

    @Test fun largo() = assertEquals(ValidadorVin.MSG_LARGO, ValidadorVin.mensajeError(valido + "X"))

    @Test fun letraI() = assertEquals(ValidadorVin.MSG_IOQ, ValidadorVin.mensajeError("1HGCM82633A00435I"))

    @Test fun letraO() = assertEquals(ValidadorVin.MSG_IOQ, ValidadorVin.mensajeError("1HGCM82633O004352"))

    @Test fun letraQ() = assertEquals(ValidadorVin.MSG_IOQ, ValidadorVin.mensajeError("1HGCM82633Q004352"))

    @Test fun espacios() = assertEquals(ValidadorVin.MSG_ESPACIOS, ValidadorVin.mensajeError("1HGCM82633A00435 "))

    @Test fun simbolos() = assertEquals(ValidadorVin.MSG_SIMBOLOS, ValidadorVin.mensajeError("1HGCM82633A00435-"))

    @Test fun igualActual() =
        assertEquals(ValidadorVin.MSG_IGUAL, ValidadorVin.mensajeError(valido, vinActual = valido.lowercase()))

    @Test fun mayusculas() = assertNull(ValidadorVin.mensajeError(valido.lowercase()))

    @Test fun formatoOk() =
        assertEquals(ValidadorVin.MSG_FORMATO_OK, ValidadorVin.mensajeFormatoOk(valido, vinActual = "AAAAAAAAAAAAAAA1"))
}
