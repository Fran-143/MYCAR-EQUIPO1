package com.mycar.app.features.tive.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiveFormularioValidacionTest {
    @Test fun tituloVacio() =
        assertEquals("Ingresa el número de título", TiveFormulario.errorTitulo("", true))

    @Test fun tituloLargo() =
        assertTrue(TiveFormulario.errorTitulo("X".repeat(33), true)!!.contains("32"))

    @Test fun tituloOk() {
        assertNull(TiveFormulario.errorTitulo("TIV-458721", true))
        assertEquals("TIV-458721", TiveFormulario.normalizarTitulo("tiv-458721"))
    }

    @Test fun codigoCorto() =
        assertEquals("El código debe tener al menos 4 caracteres", TiveFormulario.errorCodigo("AB", true))

    @Test fun codigoEspacios() =
        assertEquals("El código de verificación no permite espacios", TiveFormulario.errorCodigo("ABCD 123", true))

    @Test fun codigoOk() = assertNull(TiveFormulario.errorCodigo("ABCD1234", true))

    @Test fun motorRango() =
        assertEquals(
            "El número de motor debe tener entre 5 y 15 caracteres",
            TiveFormulario.errorMotor("AB", true)
        )

    @Test fun serieMinima() =
        assertEquals(
            "El número de serie debe tener al menos 6 caracteres",
            TiveFormulario.errorSerie("ABC12", true)
        )
}
