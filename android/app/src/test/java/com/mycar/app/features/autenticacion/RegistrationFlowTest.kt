package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.domain.*
import com.mycar.app.features.autenticacion.ui.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class RegistrationFlowTest {
    private val policy = PrivacyPolicyConfiguration("UNIT-TEST-NO-LEGAL", "https://example.invalid/privacy")
    private val clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)
    private fun request() = RegistrationRequest("Ana", "Pérez", "persona@example.org", "1995-01-01",
        "Aa1!abcd", "Aa1!abcd", PrivacyConsent(true, policy.version))
    private class ForbiddenStore : CredentialStore {
        override fun load(): CredentialVault? = error("Registration must not load credentials")
        override fun save(vault: CredentialVault) = error("Registration must not persist credentials")
    }
    private class Api : AuthApi {
        override suspend fun logout(bearer: String): Response<Unit> = error("Not part of this regression")

        var status = 201
        var body = RegistrationResponse("CUENTA_CREADA", "INICIO_SESION")
        var error = """{"codigo":"VALIDACION","erroresPorCampo":{"correo":"fixture"}}"""
        var networkFailure = false
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> {
            calls++
            started.complete(Unit)
            gate?.await()
            if (networkFailure) throw IOException()
            return if (status in 200..299) Response.success(status, body) else Response.error(status, error.toResponseBody())
        }
        override suspend fun login(request: LoginRequest): Response<TokenResponse> = error("No auto login")
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> = error("No renewal")
        override suspend fun session(bearer: String): Response<SessionResponse> = error("No session created")
    }
    private fun repository(api: Api, config: PrivacyPolicyConfiguration = policy) =
        AuthRepository(api, ForbiddenStore(), clock, config)
    private suspend fun failure(repo: AuthRepository): RegistrationFailure {
        try { repo.register(request()); fail("Expected rejection") }
        catch (failure: RegistrationFailure) { return failure }
        error("Unreachable")
    }
    @Test fun only201ExpectedBodyConfirmsRegistrationWithoutLoginOrStorage() = runBlocking {
        val api = Api()
        val repo = repository(api)
        val before = repo.session.value
        repo.register(request())
        assertSame(before, repo.session.value)
        assertEquals(1, api.calls)
    }
    @Test fun http200NeverShowsRegistrationSuccess() = runBlocking {
        val api = Api().apply { status = 200 }
        assertEquals(RegistrationFailureKind.UNCONFIRMED, failure(repository(api)).kind)
    }
    @Test fun malformedSuccessContractDoesNotConfirmRegistration() = runBlocking {
        val api = Api().apply { body = RegistrationResponse("UNKNOWN", "HOME") }
        assertEquals(RegistrationFailureKind.UNCONFIRMED, failure(repository(api)).kind)
    }
    @Test fun http400MapsOnlyKnownFieldsWithoutEchoingRejectedValues() = runBlocking {
        val api = Api().apply { status = 400 }
        val result = failure(repository(api))
        assertEquals(RegistrationFailureKind.VALIDATION, result.kind)
        assertEquals(RegistrationValidator.messages["correo"], result.fields["correo"])
        assertFalse(result.fields.values.any { "fixture" in it })
    }
    @Test fun http409IsUniformEmailUnavailable() = runBlocking {
        val result = failure(repository(Api().apply { status = 409 }))
        assertEquals(RegistrationFailureKind.EMAIL_UNAVAILABLE, result.kind)
        assertEquals("Correo no disponible.", result.message)
    }
    @Test fun http503IsUnavailable() = runBlocking {
        assertEquals(RegistrationFailureKind.UNAVAILABLE, failure(repository(Api().apply { status = 503 })).kind)
    }
    @Test fun http500IsSanitized() = runBlocking {
        assertEquals(RegistrationFailureKind.UNAVAILABLE, failure(repository(Api().apply { status = 500 })).kind)
    }
    @Test fun networkFailureDoesNotCreateSuccess() = runBlocking {
        val result = failure(repository(Api().apply { networkFailure = true }))
        assertEquals(RegistrationFailureKind.NETWORK, result.kind)
    }
    @Test fun absentPolicyNeverSendsRequest() = runBlocking {
        val api = Api()
        assertEquals(RegistrationFailureKind.PRIVACY_PENDING, failure(repository(api, PrivacyPolicyConfiguration())).kind)
        assertEquals(0, api.calls)
    }
    @Test fun configuredVersionMismatchNeverSendsRequest() = runBlocking {
        val api = Api()
        val repo = repository(api, PrivacyPolicyConfiguration("OTHER-TEST-VERSION", policy.documentUrl))
        assertEquals(RegistrationFailureKind.VERSION_MISMATCH, failure(repo).kind)
        assertEquals(0, api.calls)
    }
    @Test fun repositoryAlsoRequiresExplicitConsent() = runBlocking {
        val api = Api()
        val dto = request()
        val unchecked = RegistrationRequest(dto.nombre, dto.apellido, dto.correo, dto.fechaNacimiento,
            dto.contrasena, dto.confirmacionContrasena, PrivacyConsent(false, policy.version))
        try { repository(api).register(unchecked); fail("Expected rejection") }
        catch (failure: RegistrationFailure) {
            assertEquals(RegistrationFailureKind.VALIDATION, failure.kind)
        }
        assertEquals(0, api.calls)
    }
    @Test fun rejectedLegalVersionIsExplicit() = runBlocking {
        val api = Api().apply {
            status = 400
            error = """{"codigo":"VALIDACION","erroresPorCampo":{"versionPrivacidad":"fixture"}}"""
        }
        assertEquals(RegistrationFailureKind.VERSION_MISMATCH, failure(repository(api)).kind)
    }
    @Test fun repositoryRejectsOverlappingRegistrationsWithoutSendingSecondRequest() = runBlocking {
        val api = Api().apply { gate = CompletableDeferred() }
        val repo = repository(api)
        val first = async { repo.register(request()) }
        api.started.await()
        assertEquals(RegistrationFailureKind.IN_PROGRESS, failure(repo).kind)
        api.gate!!.complete(Unit)
        first.await()
        assertEquals(1, api.calls)
    }

    private fun vm(service: RegistrationService, config: PrivacyPolicyConfiguration = policy) =
        RegistrationViewModel(service, config, clock, Dispatchers.Unconfined)
    private fun fill(vm: RegistrationViewModel) = vm.edit {
        RegistrationForm(" Ana ", " Pérez ", " Persona@Example.ORG ", "01/01/1995",
            "Aa1!abcd", "Aa1!abcd", true)
    }
    @Test fun viewModelPreventsDoubleTapAndClearsFormAfterConfirmedSuccess() = runBlocking {
        var count = 0
        var sent: RegistrationRequest? = null
        val gate = CompletableDeferred<Unit>()
        val vm = vm(RegistrationService { request -> count++; sent = request; gate.await() })
        fill(vm)
        vm.submit(); vm.submit()
        assertEquals(1, count)
        assertTrue(vm.state.value.busy)
        gate.complete(Unit)
        assertTrue(vm.state.value.success)
        assertEquals("", vm.state.value.form.contrasena)
        assertEquals("1995-01-01", sent?.fechaNacimiento)
        assertEquals("persona@example.org", sent?.correo)
        assertEquals("Ana", sent?.nombre)
        vm.submit()
        assertEquals(1, count)
    }
    @Test fun viewModelRefusesUncheckedConsent() {
        var count = 0
        val vm = vm(RegistrationService { count++ })
        fill(vm); vm.edit { it.copy(privacidadAceptada = false) }; vm.submit()
        assertEquals(0, count)
        assertTrue("privacidadAceptada" in vm.state.value.errors)
        assertFalse(vm.state.value.success)
    }
    @Test fun viewModelBlocksLegalPendingEvenWithCheckedConsent() {
        var count = 0
        val vm = vm(RegistrationService { count++ }, PrivacyPolicyConfiguration())
        fill(vm); vm.submit()
        assertEquals(0, count)
        assertFalse(vm.state.value.success)
    }
    @Test fun viewModelNeverShowsFalseSuccessOnNetworkError() {
        val vm = vm(RegistrationService { throw RegistrationFailure(RegistrationFailureKind.NETWORK) })
        fill(vm); vm.submit()
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.success)
        assertNotNull(vm.state.value.message)
    }
    @Test fun versionChangeUnchecksConsentAndBlocksFurtherAttempts() {
        var count = 0
        val vm = vm(RegistrationService { count++; throw RegistrationFailure(RegistrationFailureKind.VERSION_MISMATCH) })
        fill(vm); vm.submit()
        assertFalse(vm.state.value.form.privacidadAceptada)
        assertTrue(vm.state.value.privacyStale)
        fill(vm); vm.submit()
        assertEquals(1, count)
    }
    @Test fun returningWithoutSubmissionClearsSensitiveFormState() {
        val vm = vm(RegistrationService { fail("Must not submit") })
        fill(vm); vm.reset()
        assertEquals("", vm.state.value.form.contrasena)
        assertEquals("", vm.state.value.form.confirmacionContrasena)
        assertFalse(vm.state.value.form.privacidadAceptada)
    }
}
