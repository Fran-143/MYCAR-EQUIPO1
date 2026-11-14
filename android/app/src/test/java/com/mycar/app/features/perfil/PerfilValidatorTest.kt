package com.mycar.app.features.perfil

import com.mycar.app.features.perfil.domain.FormularioEdicionPerfil
import com.mycar.app.features.perfil.domain.PerfilValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PerfilValidatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC)

    @Test
    fun acceptValidPersonalData() {
        val errors = PerfilValidator.validateEdition(
            FormularioEdicionPerfil("Marcelo", "Lopez", "15/08/1998", "a@b.com"), clock)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun rejectYoungAge() {
        val errors = PerfilValidator.validateEdition(
            FormularioEdicionPerfil("Ana", "Perez", "01/01/2015", "a@b.com"), clock)
        assertTrue(errors.containsKey("fechaNacimiento"))
    }

    @Test
    fun isoDisplayRoundTrip() {
        assertEquals("15/08/1998", PerfilValidator.isoToDisplay("1998-08-15"))
        assertEquals("1998-08-15", PerfilValidator.displayToIso("15/08/1998"))
    }

    @Test
    fun passwordMustDifferFromCurrent() {
        val errors = PerfilValidator.validatePasswordChange("ClaveActual1!", "ClaveActual1!", "ClaveActual1!")
        assertTrue(errors["nuevaContrasena"]!!.contains("distinta"))
    }
}
