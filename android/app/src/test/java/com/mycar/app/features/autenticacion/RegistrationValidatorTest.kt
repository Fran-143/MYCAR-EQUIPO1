package com.mycar.app.features.autenticacion

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class RegistrationValidatorTest {
    private val policy = PrivacyPolicyConfiguration("UNIT-TEST-NO-LEGAL", "https://example.invalid/privacy")
    private val clock = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneOffset.UTC)
    private val form = RegistrationForm("Ana María", "D'Ávila", "persona@example.org",
        "01/01/1995", "Aa1!abcd", "Aa1!abcd", true)
    private fun errors(value: RegistrationForm) = RegistrationValidator.validate(value, policy, clock)

    @Test fun exactJsonContractWithoutInstallationOrSessionFields() {
        val dto = RegistrationRequest("Ana", "Pérez", "persona@example.org", "1995-01-01",
            "Aa1!abcd", "Aa1!abcd", PrivacyConsent(true, policy.version))
        val json = JsonParser.parseString(Gson().toJson(dto)).asJsonObject
        assertEquals(setOf("nombre", "apellido", "correo", "fechaNacimiento", "contrasena",
            "confirmacionContrasena", "consentimientoPrivacidad"), json.keySet())
        val consent = json.getAsJsonObject("consentimientoPrivacidad")
        assertEquals(setOf("aceptado", "version"), consent.keySet())
        assertTrue(consent["aceptado"].asBoolean)
        assertEquals(policy.version, consent["version"].asString)
        assertEquals("1995-01-01", json["fechaNacimiento"].asString)
    }
    @Test fun validFormAndUnicodeNames() {
        assertTrue(errors(form).isEmpty())
        assertTrue(errors(form.copy(nombre = "Zoë", apellido = "O’Neal")).isEmpty())
    }
    @Test fun namesNormalizeWhitespaceWithoutRestrictedWordCatalog() {
        assertEquals("Ana María", RegistrationValidator.normalizedName("  Ana\t María  "))
        assertTrue(errors(form.copy(nombre = "Admin", apellido = "Sistema")).isEmpty())
    }
    @Test fun unicodeWhitespaceNormalizationMatchesBackend() {
        assertEquals("Ana María", RegistrationValidator.normalizedName("\u00a0Ana\u0085\u2003María\u3000"))
    }
    @Test fun nameBoundariesAndSeparators() {
        for (name in listOf("", "A", "A".repeat(101), "-Ana", "Ana-", "Ana2", "Ana--María"))
            assertTrue(name, "nombre" in errors(form.copy(nombre = name)))
        assertFalse("nombre" in errors(form.copy(nombre = "A".repeat(100))))
    }
    @Test fun mandatoryFields() {
        val errors = errors(RegistrationForm())
        assertTrue(errors.keys.containsAll(listOf("nombre", "apellido", "correo", "fechaNacimiento",
            "contrasena", "confirmacionContrasena", "privacidadAceptada")))
    }
    @Test fun invalidEmailFormatsAndLength() {
        for (email in listOf("", "carlos@", "@example.org", "a..b@example.org", "persona @example.org", "a@" + "a".repeat(254)))
            assertTrue(email, "correo" in errors(form.copy(correo = email)))
        assertFalse("correo" in errors(form.copy(correo = "ana+mycar@example.org")))
        assertEquals("persona@example.org", RegistrationValidator.normalizedEmail("  Persona@Example.ORG  "))
    }
    @Test fun validLeapDayAndStrictInvalidDates() {
        assertNotNull(RegistrationValidator.birthDate("29/02/2004"))
        for (date in listOf("29/02/2003", "31/04/1995", "1995-01-01", ""))
            assertNull(date, RegistrationValidator.birthDate(date))
    }
    @Test fun minimumAgeUsesLimaRatherThanDeviceDay() {
        assertFalse("fechaNacimiento" in errors(form.copy(fechaNacimiento = "26/10/2008")))
        assertTrue("fechaNacimiento" in errors(form.copy(fechaNacimiento = "27/10/2008")))
    }
    @Test fun maximumAgeIs85CompletedYears() {
        assertFalse("fechaNacimiento" in errors(form.copy(fechaNacimiento = "27/10/1940")))
        assertTrue("fechaNacimiento" in errors(form.copy(fechaNacimiento = "26/10/1940")))
        assertTrue("fechaNacimiento" in errors(form.copy(fechaNacimiento = "01/01/2030")))
    }
    @Test fun passwordRequiresAllCategoriesAndLength() {
        for (value in listOf("", "Aa1!abc", "aa1!abcd", "AA1!ABCD", "Aa!!abcd", "Aa11abcd", "Aa1!" + "x".repeat(61)))
            assertTrue(value, "contrasena" in errors(form.copy(contrasena = value)))
        assertFalse("contrasena" in errors(form))
    }
    @Test fun unicodeCodePointsRatherThanUtf16Length() {
        assertFalse("contrasena" in errors(form.copy(contrasena = "Aa1!" + "😀".repeat(60))))
        assertTrue("contrasena" in errors(form.copy(contrasena = "Aa1!" + "😀".repeat(61))))
    }
    @Test fun rejectsAsciiAndUnicodeSpaces() {
        for (space in listOf(" ", "\t", "\n", "\u00a0", "\u2003"))
            assertTrue("contrasena" in errors(form.copy(contrasena = "Aa1!abcd" + space)))
    }
    @Test fun unicodeDigitsAndSymbolsAreAccepted() {
        assertFalse("contrasena" in errors(form.copy(contrasena = "Aa١😀abcd")))
    }
    @Test fun confirmationMustMatchExactly() {
        for (confirmation in listOf("", "Aa1!abcd ", "aa1!abcd"))
            assertTrue("confirmacionContrasena" in errors(form.copy(confirmacionContrasena = confirmation)))
    }
    @Test fun consentStartsFalseAndMustBeExplicit() {
        assertFalse(RegistrationForm().privacidadAceptada)
        assertTrue("privacidadAceptada" in errors(form.copy(privacidadAceptada = false)))
    }
    @Test fun missingOrUnusableLegalConfigurationBlocksValidation() {
        for (config in listOf(PrivacyPolicyConfiguration(), PrivacyPolicyConfiguration("UNIT-TEST"),
            PrivacyPolicyConfiguration("", policy.documentUrl), PrivacyPolicyConfiguration("x".repeat(41), policy.documentUrl),
            PrivacyPolicyConfiguration("UNIT-TEST", "http://example.invalid/privacy"))) {
            assertFalse(config.available)
            assertTrue("versionPrivacidad" in RegistrationValidator.validate(form, config, clock))
        }
    }
    @Test fun secretsAreRedactedFromGeneratedStateRepresentations() {
        assertFalse(form.toString().contains(form.contrasena))
        assertFalse(com.mycar.app.features.autenticacion.ui.RegistrationState(form).toString().contains(form.contrasena))
    }
}
