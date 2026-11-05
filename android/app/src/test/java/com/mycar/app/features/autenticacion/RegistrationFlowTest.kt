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
        var emitStatus = 202
        var completeStatus = 201
        var emitBody = VerificationEmitResponse(
            "EMISION_CREADA", "11111111-1111-1111-1111-111111111111", "secret-op",
            "22222222-2222-2222-2222-222222222222", 1, null, "123456")
        var completeBody = RegistrationResponse("CUENTA_CREADA", "INICIO_SESION")
        var error = """{"codigo":"VALIDACION","erroresPorCampo":{"correo":"fixture"}}"""
        var networkFailure = false
        var emitCalls = 0
        var completeCalls = 0
        var originCalls = 0
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> =
            Response.error(410, """{"codigo":"REGISTRO_REQUIERE_VERIFICACION"}""".toResponseBody())
        override suspend fun verificationOrigin(): Response<VerificationOriginResponse> {
            originCalls++
            return Response.success(VerificationOriginResponse(
                "33333333-3333-3333-3333-333333333333", "origin-secret-value-32bytes-pad!!"))
        }
        override suspend fun verificationEmit(body: VerificationEmitRequest): Response<VerificationEmitResponse> {
            emitCalls++
            started.complete(Unit)
            gate?.await()
            if (networkFailure) throw IOException()
            return if (emitStatus in 200..299) Response.success(emitStatus, emitBody)
            else Response.error(emitStatus, error.toResponseBody())
        }
        override suspend fun verificationResend(body: VerificationEmitRequest): Response<VerificationEmitResponse> =
            verificationEmit(body)
        override suspend fun verificationComplete(body: VerificationCompleteRequest): Response<RegistrationResponse> {
            completeCalls++
            if (networkFailure) throw IOException()
            return if (completeStatus in 200..299) Response.success(completeStatus, completeBody)
            else Response.error(completeStatus, error.toResponseBody())
        }
        override suspend fun login(request: LoginRequest): Response<TokenResponse> = error("No auto login")
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> = error("No renewal")
        override suspend fun session(bearer: String): Response<SessionResponse> = error("No session created")
    }
    private fun repository(api: Api, config: PrivacyPolicyConfiguration = policy) =
        AuthRepository(api, ForbiddenStore(), clock, config)

    private fun fakeService(
        onStart: suspend (RegistrationRequest) -> VerificationSession = {
            VerificationSession("o", "os", "op", "ops", "em", 1, "123456")
        },
        onComplete: suspend (VerificationSession, String, RegistrationRequest) -> Unit = { _, _, _ -> },
        onResend: suspend (VerificationSession, RegistrationRequest) -> VerificationSession = { s, _ -> s }
    ) = object : RegistrationService {
        override suspend fun register(request: RegistrationRequest) =
            throw RegistrationFailure(RegistrationFailureKind.UNAVAILABLE)
        override suspend fun startEmailVerification(request: RegistrationRequest) = onStart(request)
        override suspend fun resendEmailCode(session: VerificationSession, request: RegistrationRequest) =
            onResend(session, request)
        override suspend fun completeEmailVerification(
            session: VerificationSession, code: String, request: RegistrationRequest
        ) = onComplete(session, code, request)
    }

    @Test fun startVerificationConfirmsEmitWithoutLoginOrStorage() = runBlocking {
        val api = Api()
        val repo = repository(api)
        val before = repo.session.value
        val session = repo.startEmailVerification(request())
        assertSame(before, repo.session.value)
        assertEquals(1, api.originCalls)
        assertEquals(1, api.emitCalls)
        assertEquals("123456", session.codigoDesarrollo)
    }

    @Test fun completeVerificationRequires201Contract() = runBlocking {
        val api = Api()
        val repo = repository(api)
        val session = repo.startEmailVerification(request())
        repo.completeEmailVerification(session, "123456", request())
        assertEquals(1, api.completeCalls)
    }

    @Test fun emitHttp409IsUniformEmailUnavailable() = runBlocking {
        val api = Api().apply { emitStatus = 409 }
        try {
            repository(api).startEmailVerification(request())
            fail("Expected rejection")
        } catch (failure: RegistrationFailure) {
            assertEquals(RegistrationFailureKind.EMAIL_UNAVAILABLE, failure.kind)
        }
    }

    @Test fun emitHttp429MapsWaitMessage() = runBlocking {
        val api = Api().apply {
            emitStatus = 429
            error = """{"codigo":"ESPERA_REENVIO","mensaje":"Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro."}"""
        }
        try {
            repository(api).startEmailVerification(request())
            fail("Expected rejection")
        } catch (failure: RegistrationFailure) {
            assertEquals(RegistrationFailureKind.RATE_LIMITED, failure.kind)
            assertTrue(failure.message!!.contains("10 minutos"))
        }
    }

    @Test fun absentPolicyNeverSendsRequest() = runBlocking {
        val api = Api()
        try {
            repository(api, PrivacyPolicyConfiguration()).startEmailVerification(request())
            fail("Expected rejection")
        } catch (failure: RegistrationFailure) {
            assertEquals(RegistrationFailureKind.PRIVACY_PENDING, failure.kind)
        }
        assertEquals(0, api.originCalls)
    }

    @Test fun directRegisterIsDisabled() = runBlocking {
        try {
            repository(Api()).register(request())
            fail("Expected rejection")
        } catch (failure: RegistrationFailure) {
            assertEquals(RegistrationFailureKind.UNAVAILABLE, failure.kind)
        }
    }

    private fun vm(service: RegistrationService, config: PrivacyPolicyConfiguration = policy) =
        RegistrationViewModel(service, config, clock, Dispatchers.Unconfined)
    private fun fill(vm: RegistrationViewModel) = vm.edit {
        RegistrationForm(" Ana ", " Pérez ", " Persona@Example.ORG ", "01/01/1995",
            "Aa1!abcd", "Aa1!abcd", true)
    }

    @Test fun viewModelMovesToCodeStepAfterEmit() = runBlocking {
        var count = 0
        val gate = CompletableDeferred<Unit>()
        val vm = vm(fakeService(onStart = {
            count++; gate.await()
            VerificationSession("o", "os", "op", "ops", "em", 1, "654321")
        }))
        fill(vm)
        vm.submit(); vm.submit()
        assertEquals(1, count)
        assertTrue(vm.state.value.busy)
        gate.complete(Unit)
        assertEquals(RegistrationStep.CODE, vm.state.value.step)
        assertEquals("654321", vm.state.value.codigoDesarrollo)
        assertFalse(vm.state.value.success)
    }

    @Test fun viewModelConfirmsCodeAndClearsSensitiveState() = runBlocking {
        val vm = vm(fakeService())
        fill(vm)
        vm.submit()
        assertEquals(RegistrationStep.CODE, vm.state.value.step)
        vm.editCode("123456")
        vm.confirmCode()
        assertTrue(vm.state.value.success)
        assertEquals("", vm.state.value.form.contrasena)
    }

    @Test fun viewModelRefusesUncheckedConsent() {
        var count = 0
        val vm = vm(fakeService(onStart = { count++; error("no") }))
        fill(vm); vm.edit { it.copy(privacidadAceptada = false) }; vm.submit()
        assertEquals(0, count)
        assertTrue("privacidadAceptada" in vm.state.value.errors)
        assertFalse(vm.state.value.success)
    }

    @Test fun viewModelNeverShowsFalseSuccessOnNetworkError() {
        val vm = vm(fakeService(onStart = {
            throw RegistrationFailure(RegistrationFailureKind.NETWORK)
        }))
        fill(vm); vm.submit()
        assertFalse(vm.state.value.busy)
        assertFalse(vm.state.value.success)
        assertNotNull(vm.state.value.message)
    }

    @Test fun returningWithoutSubmissionClearsSensitiveFormState() {
        val vm = vm(fakeService(onStart = { throw AssertionError("Must not submit") }))
        fill(vm); vm.reset()
        assertEquals("", vm.state.value.form.contrasena)
        assertEquals("", vm.state.value.form.confirmacionContrasena)
        assertFalse(vm.state.value.form.privacidadAceptada)
    }

    @Test fun backFromCodeWorksWhileEmitInFlightAndAllowsNewSubmit() = runBlocking {
        var starts = 0
        val gate = CompletableDeferred<Unit>()
        val vm = vm(fakeService(onStart = {
            starts++
            if (starts == 1) {
                gate.await()
                VerificationSession("o", "os", "op", "ops", "em", 1, "111111")
            } else {
                VerificationSession("o2", "os2", "op2", "ops2", "em2", 1, "222222")
            }
        }))
        fill(vm)
        vm.submit()
        assertTrue(vm.state.value.busy)
        vm.backFromCode()
        assertEquals(RegistrationStep.FORM, vm.state.value.step)
        assertFalse(vm.state.value.busy)
        gate.complete(Unit)
        assertEquals(RegistrationStep.FORM, vm.state.value.step)
        vm.submit()
        assertEquals(RegistrationStep.CODE, vm.state.value.step)
        assertEquals(2, starts)
        assertEquals("222222", vm.state.value.codigoDesarrollo)
    }

    @Test fun backFromCodeThenContinuarResumesWithoutNewEmit() = runBlocking {
        var starts = 0
        val vm = vm(fakeService(onStart = {
            starts++
            VerificationSession("o", "os", "op", "ops", "em", 1, "999999")
        }))
        fill(vm)
        vm.submit()
        assertEquals(RegistrationStep.CODE, vm.state.value.step)
        assertEquals(1, starts)
        vm.backFromCode()
        assertEquals(RegistrationStep.FORM, vm.state.value.step)
        assertFalse(vm.state.value.busy)
        vm.submit()
        assertEquals(RegistrationStep.CODE, vm.state.value.step)
        assertEquals(1, starts)
        assertEquals("999999", vm.state.value.codigoDesarrollo)
    }

    @Test fun rateLimitedFailureShowsWaitMessageOnForm() = runBlocking {
        val vm = vm(fakeService(onStart = {
            throw RegistrationFailure(
                RegistrationFailureKind.RATE_LIMITED,
                detail = "Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro."
            )
        }))
        fill(vm)
        vm.submit()
        assertEquals(RegistrationStep.FORM, vm.state.value.step)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.message!!.contains("10 minutos"))
    }
}
