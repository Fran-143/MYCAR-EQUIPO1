package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.domain.*
import com.mycar.app.features.autenticacion.ui.RegistrationViewModel
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class LocalPrivacyPolicyTest {
    private val local = PrivacyPolicyConfiguration.academicLocal()
    private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC)
    private fun vm(service: RegistrationService = RegistrationService { error("Must not submit") }) =
        RegistrationViewModel(service, local, clock, Dispatchers.Unconfined)
    private fun fill(vm: RegistrationViewModel, accepted: Boolean = false) = vm.edit {
        RegistrationForm("Ana", "Pérez", "persona@example.org", "01/01/1995", "Aa1!abcd", "Aa1!abcd", accepted)
    }

    @Test fun versionOneBelongsOnlyToKnownBundledDocument() {
        assertTrue(local.available)
        assertEquals("1.0", local.version)
        assertEquals("", local.documentUrl)
        assertEquals(LocalPrivacyDocument.MYCAR_ACADEMIC_V1, local.localDocument)
        assertFalse(PrivacyPolicyConfiguration("1.0").available)
        assertFalse(PrivacyPolicyConfiguration("2.0", localDocument = local.localDocument).available)
    }
    @Test fun externalHttpsRulesRemainUnchanged() {
        assertTrue(PrivacyPolicyConfiguration("external-approved", "https://example.invalid/privacy").available)
        for (url in listOf("", "http://example.invalid/privacy", "file:///privacy.txt", "content://privacy", "https://name@example.invalid/privacy"))
            assertFalse(url, PrivacyPolicyConfiguration("1.0", url).available)
        assertFalse(PrivacyPolicyConfiguration("1.0", "https://example.invalid/privacy", local.localDocument).available)
    }
    @Test fun sourceSelectionNeverInventsAnExternalVersion() {
        assertEquals("1.0", PrivacyPolicyConfiguration.configured("local-academic", "", "").version)
        assertFalse(PrivacyPolicyConfiguration.configured("external", "", "").available)
        assertFalse(PrivacyPolicyConfiguration.configured("unknown", "1.0", "https://example.invalid").available)
        assertNull(PrivacyPolicyConfiguration.configured("external", "1.0", "https://example.invalid").localDocument)
    }
    @Test fun bundledResourceMatchesApprovedTextExactly() {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("mycar_privacy_1_0.txt"))
            .use { it.reader(Charsets.UTF_8).readText().replace("\r\n", "\n").toByteArray(Charsets.UTF_8) }
        val actual = MessageDigest.getInstance("SHA-256").digest(text).joinToString("") { "%02x".format(it) }
        assertEquals("c85fb340c7bd4e6e22fd30bc4db9dc932260d0767fce4fbd8a3daf679cfb7713", actual)
    }
    @Test fun openingAndReturningNeverAutomaticallyAccepts() {
        val vm = vm()
        fill(vm)
        assertFalse(vm.state.value.form.privacidadAceptada)
        vm.openLocalPolicy()
        assertTrue(vm.state.value.policyVisible)
        assertFalse(vm.state.value.policyConsulted)
        vm.localPolicyLoaded()
        vm.closeLocalPolicy()
        assertFalse(vm.state.value.policyVisible)
        assertTrue(vm.state.value.policyConsulted)
        assertFalse(vm.state.value.form.privacidadAceptada)
        assertEquals("Ana", vm.state.value.form.nombre)
    }
    @Test fun leavingBeforeDocumentLoadsDoesNotCountAsConsultation() {
        val vm = vm()
        vm.openLocalPolicy()
        vm.closeLocalPolicy()
        vm.localPolicyLoaded()
        assertFalse(vm.state.value.policyConsulted)
        assertFalse(vm.state.value.form.privacidadAceptada)
    }
    @Test fun consentCannotBypassConsultation() {
        var calls = 0
        val vm = vm(RegistrationService { calls++ })
        fill(vm, accepted = true)
        vm.submit()
        assertEquals(0, calls)
        assertFalse(vm.state.value.success)
    }
    @Test fun readingDoesNotReplaceMandatoryConsent() {
        var calls = 0
        val vm = vm(RegistrationService { calls++ })
        fill(vm)
        vm.openLocalPolicy(); vm.localPolicyLoaded(); vm.closeLocalPolicy()
        vm.submit()
        assertEquals(0, calls)
        assertTrue("privacidadAceptada" in vm.state.value.errors)
    }
    @Test fun explicitConsentSendsExactLocalVersionAndDoesNotSignIn() {
        var sent: RegistrationRequest? = null
        val vm = vm(RegistrationService { sent = it })
        fill(vm)
        vm.openLocalPolicy(); vm.localPolicyLoaded(); vm.closeLocalPolicy()
        vm.edit { it.copy(privacidadAceptada = true) }
        vm.submit()
        assertEquals("1.0", sent?.consentimientoPrivacidad?.version)
        assertEquals(true, sent?.consentimientoPrivacidad?.aceptado)
        assertTrue(vm.state.value.success)
        assertEquals("", vm.state.value.form.contrasena)
        assertFalse(vm.state.value.form.privacidadAceptada)
    }
    @Test fun resetRequiresConsultationAndConsentAgain() {
        val vm = vm()
        vm.openLocalPolicy(); vm.localPolicyLoaded(); vm.closeLocalPolicy()
        vm.edit { it.copy(privacidadAceptada = true) }
        vm.reset()
        assertFalse(vm.state.value.policyVisible)
        assertFalse(vm.state.value.policyConsulted)
        assertFalse(vm.state.value.form.privacidadAceptada)
    }
}
