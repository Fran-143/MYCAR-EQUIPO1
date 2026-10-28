package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class SessionModulesIntegrationTest {
    private val now = Instant.parse("2030-01-01T00:00:00Z")
    private val end = "2030-01-02T00:00:00Z"
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private class Store(var value: CredentialVault) : CredentialStore {
        override fun load() = value
        override fun save(vault: CredentialVault) { value = vault }
    }
    private inner class Api : AuthApi {
        var renewals = 0
        var logouts = 0
        var renewCode = 200
        var logoutCode = 204
        var failingSession = false
        var id = 7L
        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> = error("No registration")
        override suspend fun login(request: LoginRequest) = Response.success(response())
        override suspend fun session(bearer: String): Response<SessionResponse> {
            if (failingSession) throw IOException("fixture")
            return Response.success(SessionResponse(true, id, end))
        }
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> {
            renewals++
            return if (renewCode == 200) Response.success(response())
            else Response.error(renewCode, "{}".toResponseBody())
        }
        override suspend fun logout(bearer: String): Response<Unit> {
            logouts++
            return if (logoutCode == 204) Response.success<Unit>(204, null)
            else Response.error(logoutCode, "{}".toResponseBody())
        }
        private fun response() = TokenResponse("AUTENTICADO", id, "fixture-fresh", "2030-01-01T00:05:00Z", "fixture-refresh-new", end)
    }
    private fun store(expiry: String = "2030-01-01T00:05:00Z") = Store(CredentialVault(
        "643bf983-8f74-41bb-9488-1fbaaf49065b", StoredTokens(7, "fixture-old", expiry, "fixture-refresh", end)))

    @Test fun cannotIssueHeaderBeforeSessionValidation() = runBlocking<Unit> {
        val repo = AuthRepository(Api(), store(), clock)
        assertNull(repo.authenticatedContext())
        assertThrows(SessionRequired::class.java) { runBlocking { repo.authorization(AuthenticatedContext(7, 0)) } }
    }
    @Test fun allModulesUseTheSameValidatedBearer() = runBlocking<Unit> {
        val api = Api(); val repo = AuthRepository(api, store(), clock)
        repo.validateSession(); val context = requireNotNull(repo.authenticatedContext())
        repeat(4) { assertEquals("Bearer fixture-old", repo.authorization(context)) }
        assertEquals(0, api.renewals)
    }
    @Test fun concurrentModuleRequestsRotateOnlyOnceAndPersistNewTokens() = runBlocking<Unit> {
        val api = Api(); val store = store("2030-01-01T00:00:05Z")
        val repo = AuthRepository(api, store, clock)
        repo.validateSession(); val context = requireNotNull(repo.authenticatedContext())
        val headers = coroutineScope { List(12) { async { repo.authorization(context) } }.awaitAll() }
        assertTrue(headers.all { it == "Bearer fixture-fresh" })
        assertEquals(1, api.renewals)
        assertEquals("fixture-refresh-new", store.value.tokens?.refreshToken)
    }
    @Test fun retiredContextCannotAuthorizeAfterLogoutAndAnotherLogin() = runBlocking<Unit> {
        val api = Api(); val repo = AuthRepository(api, store(), clock)
        repo.validateSession(); val old = requireNotNull(repo.authenticatedContext())
        repo.logout(); api.id = 8; repo.login("persona@example.org", "fixture")
        assertThrows(SessionRequired::class.java) { runBlocking { repo.authorization(old) } }
        assertEquals("Bearer fixture-fresh", repo.authorization(requireNotNull(repo.authenticatedContext())))
    }
    @Test fun privateCleanupPrecedesTokenRemovalAndRemoteLogout() = runBlocking<Unit> {
        val api = Api().apply { logoutCode = 503 }; val store = store(); var clean = false
        val repo = AuthRepository(api, store, clock, clearPrivateData = {
            assertNotNull(store.value.tokens); assertEquals(0, api.logouts); clean = true
        })
        repo.validateSession(); repo.logout()
        assertTrue(clean); assertNull(store.value.tokens)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
    }
    @Test fun cleanupFailureBlocksNewAccountAndRemoteConfirmationUntilRetried() = runBlocking<Unit> {
        val api = Api(); val store = store(); var failure = true
        val repo = AuthRepository(api, store, clock, clearPrivateData = {
            if (failure) throw IOException("fixture cleanup failed")
        })
        repo.validateSession(); val old = requireNotNull(repo.authenticatedContext()); repo.logout()
        assertEquals(SessionPhase.CLEAR_FAILED, repo.session.value.phase)
        assertNull(repo.authenticatedContext()); assertEquals(0, api.logouts)
        assertThrows(SessionRequired::class.java) { runBlocking { repo.authorization(old) } }
        failure = false; repo.logout()
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase); assertNull(store.value.tokens)
    }
    @Test fun temporaryRenewalFailureIsNotReportedAsInvalidSessionOrRetriedPost() = runBlocking<Unit> {
        val api = Api().apply { renewCode = 503 }; val store = store("2030-01-01T00:00:05Z")
        val repo = AuthRepository(api, store, clock)
        repo.validateSession(); val context = requireNotNull(repo.authenticatedContext())
        try { repo.authorization(context); fail("Expected temporary failure") }
        catch (error: IOException) { assertFalse(error is SessionRequired) }
        assertEquals(SessionPhase.RETRY, repo.session.value.phase)
        assertNotNull(store.value.tokens); assertEquals(1, api.renewals)
    }
    @Test fun revokedRefreshPreventsModuleRequest() = runBlocking<Unit> {
        val api = Api().apply { renewCode = 401 }
        val repo = AuthRepository(api, store("2030-01-01T00:00:05Z"), clock)
        repo.validateSession(); val context = requireNotNull(repo.authenticatedContext())
        assertThrows(SessionRequired::class.java) { runBlocking { repo.authorization(context) } }
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
    }
    @Test fun lifecycleResumeDoesNotTemporarilyRetireValidatedAccount() = runBlocking<Unit> {
        val api = Api(); val repo = AuthRepository(api, store(), clock)
        repo.validateSession(); val context = repo.authenticatedContext()
        val maintain = launch(start = CoroutineStart.UNDISPATCHED) { repo.maintainSession() }
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        assertEquals(context, repo.authenticatedContext())
        maintain.cancelAndJoin()
    }
}
