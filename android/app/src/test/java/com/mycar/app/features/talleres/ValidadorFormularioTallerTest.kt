package com.mycar.app.features.talleres

import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.ValidadorFormularioTaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidadorFormularioTallerTest {

    @Test
    fun nombreSeNormalizaAMayusculas() {
        assertEquals("TALLER SAN MIGUEL", ValidadorFormularioTaller.normalizarNombre("  Taller San Miguel  "))
    }

    @Test
    fun telefonoCelularPeruExacto9EmpiezaCon9() {
        assertTrue(ValidadorFormularioTaller.errores("TALLER", "987654321", "", "", "").isEmpty())
        assertEquals(
            "Ingresa 9 dígitos (celular Perú)",
            ValidadorFormularioTaller.errores("TALLER", "98765432", "", "", "")["telefono"]
        )
        assertEquals(
            "El celular debe empezar con 9",
            ValidadorFormularioTaller.errores("TALLER", "187654321", "", "", "")["telefono"]
        )
        assertEquals(
            "Ingresa 9 dígitos (celular Perú)",
            ValidadorFormularioTaller.errores("TALLER", "9876543210", "", "", "")["telefono"]
        )
        assertEquals("987654321", ValidadorFormularioTaller.digitosTelefono("+51 987-654-321"))
        assertEquals("987654321", ValidadorFormularioTaller.digitosTelefono("51987654321"))
        assertEquals("+51 987654321", ValidadorFormularioTaller.formatearTelefonoUi("987654321"))
        assertNull(ValidadorFormularioTaller.formatearTelefonoUi(""))
    }

    @Test
    fun correoRequiereFormatoValido() {
        assertTrue(ValidadorFormularioTaller.errores("TALLER", "", "taller@correo.com", "", "").isEmpty())
        assertEquals(
            "El correo ingresado no es válido",
            ValidadorFormularioTaller.errores("TALLER", "", "tallercorreo.com", "", "")["correo"]
        )
        assertEquals(
            "El correo ingresado no es válido",
            ValidadorFormularioTaller.errores("TALLER", "", "taller@@correo.com", "", "")["correo"]
        )
    }

    @Test
    fun direccionMinimoNueveYMayusculas() {
        assertEquals("AV. LOS OLIVOS 123", ValidadorFormularioTaller.normalizarDireccion(" av. los olivos 123 "))
        assertEquals(
            "La dirección debe contener al menos 9 caracteres.",
            ValidadorFormularioTaller.errores("TALLER", "", "", "AV. LIMA", "")["direccion"]
        )
        assertTrue(ValidadorFormularioTaller.errores("TALLER", "", "", "AV. OLIVOS", "").isEmpty())
    }

    @Test
    fun guardarEdicionDeshabilitadoSinCambiosReales() {
        val original = Taller(
            id = 1L,
            nombre = "TALLER SAN MIGUEL",
            telefono = "987654321",
            correo = "taller@correo.com",
            direccion = "AV. LOS OLIVOS 123",
            notas = null,
            tieneWhatsapp = false
        )
        assertFalse(
            ValidadorFormularioTaller.hayCambiosReales(
                "taller san miguel",
                "987654321",
                "taller@correo.com",
                "  av. los olivos 123  ",
                "",
                false,
                original
            )
        )
        assertTrue(
            ValidadorFormularioTaller.puedeGuardar(
                "TALLER CENTRAL",
                "987654321",
                "taller@correo.com",
                "AV. LOS OLIVOS 123",
                "",
                false,
                original
            )
        )
        assertFalse(
            ValidadorFormularioTaller.puedeGuardar(
                "TALLER SAN MIGUEL",
                "987654321",
                "taller@correo.com",
                "AV. LOS OLIVOS 123",
                "",
                false,
                original
            )
        )
    }
}
