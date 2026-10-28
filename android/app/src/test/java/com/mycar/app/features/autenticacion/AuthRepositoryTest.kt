package com.mycar.app.features.autenticacion

import com.google.gson.Gson
import com.mycar.app.features.autenticacion.data.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {
    private val installation = "643bf983-8f74-41bb-9488-1fbaaf49065b"
    private val end = "2030-01-02T00:00:00Z"
    private val expiry = "2030-01-01T00:05:00Z"
    private val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
    private fun tokens(access: String = "fixture-access-old") =
        StoredTokens(7, access, expiry, "fixture-refresh", end)
    private fun response(access: String = "fixture-access-new", absolute: String = end) =
        TokenResponse("AUTENTICADO", 7, access, expiry, "fixture-refresh-new", absolute)

    private class MemoryStore(var value: CredentialVault? = null) : CredentialStore {
        var failSave = false
        override fun load() = value
        override fun save(vault: CredentialVault) {
            if (failSave) throw IOException("fixture storage unavailable")
            value = vault
        }
    }
    private inner class Api : AuthApi {
        override suspend fun logout(bearer: String): Response<Unit> = error("Not part of this regression")

        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> =
            error("RF02 does not register accounts")
        var loginCode = 200
        var renewCode = 200
        var networkFailure = false
        var renewalAbsolute = end
        var lastLogin: LoginRequest? = null
        var lastRenewal: RenewalRequest? = null
        val renewals = AtomicInteger()
        override suspend fun login(request: LoginRequest): Response<TokenResponse> {
            lastLogin = request
            if (networkFailure) throw IOException()
            return if (loginCode == 200) Response.success(response()) else failure(loginCode)
        }
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> {
            renewals.incrementAndGet()
            lastRenewal = request
            if (networkFailure) throw IOException()
            return if (renewCode == 200) Response.success(response(absolute = renewalAbsolute)) else failure(renewCode)
        }
        override suspend fun session(bearer: String): Response<SessionResponse> {
            if (networkFailure) throw IOException()
            return if (bearer.endsWith("old")) failure(401)
                else Response.success(SessionResponse(true, 7, end))
        }
        private fun <T> failure(code: Int): Response<T> =
            Response.error(code, "{}".toResponseBody())
    }

    @Test fun loginKeepsInstallationAndPersistsOnlyIssuedCredentials() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, null))
        val api = Api()
        val repo = AuthRepository(api, store, clock)
        repo.login("  persona@example.org  ", "fixture password")
        assertEquals("persona@example.org", api.lastLogin?.correo)
        assertEquals(installation, api.lastLogin?.idInstalacion)
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        assertEquals(expiry, store.value?.tokens?.accessTokenExpiraUtc)
    }

    @Test fun incorrectCredentialsStayGeneric() = runBlocking {
        val repo = AuthRepository(Api().apply { loginCode = 401 }, MemoryStore(), clock)
        try { repo.login("persona@example.org", "fixture"); fail("Expected rejection") }
        catch (failure: AuthProblem) { assertEquals("Correo o contraseña incorrectos.", failure.message) }
        assertNotEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
    }

    @Test fun disabledAccountDoesNotExposeSpecificAccountStatus() = runBlocking {
        val repo = AuthRepository(Api().apply { loginCode = 403 }, MemoryStore(), clock)
        try { repo.login("persona@example.org", "fixture"); fail("Expected rejection") }
        catch (failure: AuthProblem) {
            assertEquals("La cuenta no está habilitada para iniciar sesión.", failure.message)
        }
    }

    @Test fun concurrentValidationRotatesRefreshOnlyOnce() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, tokens()))
        val api = Api()
        val repo = AuthRepository(api, store, clock)
        coroutineScope { List(12) { async { repo.validateSession() } }.awaitAll() }
        assertEquals(1, api.renewals.get())
        assertEquals(installation, api.lastRenewal?.idInstalacion)
        assertEquals("fixture-refresh", api.lastRenewal?.refreshToken)
        assertEquals(end, store.value?.tokens?.sesionExpiraUtc)
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
    }

    @Test fun revokedRefreshRemovesTokensButPreservesInstallation() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, tokens()))
        val repo = AuthRepository(Api().apply { renewCode = 401 }, store, clock)
        repo.validateSession()
        assertNull(store.value?.tokens)
        assertEquals(installation, store.value?.installation)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
    }

    @Test fun networkFailureDoesNotDestroyPersistedSession() = runBlocking {
        val existing = tokens()
        val store = MemoryStore(CredentialVault(installation, existing))
        val repo = AuthRepository(Api().apply { networkFailure = true }, store, clock)
        repo.validateSession()
        assertSame(existing, store.value?.tokens)
        assertEquals(SessionPhase.RETRY, repo.session.value.phase)
    }

    @Test fun temporaryRenewalFailureDoesNotDestroySession() = runBlocking {
        val existing = tokens()
        val store = MemoryStore(CredentialVault(installation, existing))
        val repo = AuthRepository(Api().apply { renewCode = 503 }, store, clock)
        repo.validateSession()
        assertSame(existing, store.value?.tokens)
        assertEquals(SessionPhase.RETRY, repo.session.value.phase)
    }

    @Test fun serverCannotExtendAbsoluteSessionInRenewal() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, tokens()))
        val repo = AuthRepository(Api().apply { renewalAbsolute = "2030-01-03T00:00:00Z" }, store, clock)
        repo.validateSession()
        assertEquals(end, store.value?.tokens?.sesionExpiraUtc)
        assertEquals(SessionPhase.RETRY, repo.session.value.phase)
    }

    @Test fun persistenceFailureNeverPublishesSuccessfulLogin() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, null)).apply { failSave = true }
        val repo = AuthRepository(Api(), store, clock)
        try { repo.login("persona@example.org", "fixture"); fail("Expected storage failure") }
        catch (_: AuthProblem) { }
        assertNotEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        assertNull(store.value?.tokens)
    }

    @Test fun noSessionStartsSignedOutAndUuidSurvivesRecreation() = runBlocking {
        val store = MemoryStore()
        AuthRepository(Api(), store, clock).validateSession()
        val uuid = store.value?.installation
        AuthRepository(Api(), store, clock).validateSession()
        assertNotNull(uuid)
        assertEquals(uuid, store.value?.installation)
    }

    @Test fun clockAheadDoesNotInvalidateServerValidatedSession() = runBlocking {
        val store = MemoryStore(CredentialVault(installation, tokens("fixture-access-new")))
        val api = Api()
        val repo = AuthRepository(api, store,
            Clock.fixed(Instant.parse("2031-01-01T00:00:00Z"), ZoneOffset.UTC))
        repo.validateSession()
        assertEquals(0, api.renewals.get())
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
    }

    @Test fun exactBackendJsonNamesDeserializeWithoutInventedFields() {
        val json = """{"estado":"AUTENTICADO","idSesion":7,"accessToken":"fixture-access","accessTokenExpiraUtc":"2030-01-01T00:05:00Z","refreshToken":"fixture-refresh","sesionExpiraUtc":"2030-01-02T00:00:00Z"}"""
        val dto = Gson().fromJson(json, TokenResponse::class.java)
        assertEquals(7L, dto.idSesion)
        assertEquals(expiry, dto.accessTokenExpiraUtc)
        assertEquals(end, dto.sesionExpiraUtc)
    }
}
