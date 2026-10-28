package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.ui.AuthViewModel
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class LogoutTest {
    private val uuid = "643bf983-8f74-41bb-9488-1fbaaf49065b"
    private val expiry = "2030-01-01T00:05:00Z"
    private val end = "2030-01-02T00:00:00Z"
    private val clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
    private fun tokens() = StoredTokens(7, "fixture-old", expiry, "fixture-refresh", end)
    private fun issued(id: Long = 7) = TokenResponse("AUTENTICADO", id, "fixture-new", expiry, "fixture-new-refresh", end)
    private inner class Store : CredentialStore {
        var value = CredentialVault(uuid, tokens())
        var clearFailure = false
        override fun load() = value
        override fun save(vault: CredentialVault) {
            if (clearFailure && vault.tokens == null) throw IOException("fixture")
            value = vault
        }
    }
    private inner class Api : AuthApi {
        var logoutCode = 204
        var logoutFailure = false
        var expireAccess = false
        var validationCode = 200
        var logouts = 0
        var renewals = 0
        var lastBearer: String? = null
        var logoutGate: CompletableDeferred<Unit>? = null
        var renewalGate: CompletableDeferred<Unit>? = null
        var validationGate: CompletableDeferred<Unit>? = null
        var loginGate: CompletableDeferred<Unit>? = null
        val logoutStarted = CompletableDeferred<Unit>()
        val secondLogoutStarted = CompletableDeferred<Unit>()
        val renewalStarted = CompletableDeferred<Unit>()
        val validationStarted = CompletableDeferred<Unit>()
        val loginStarted = CompletableDeferred<Unit>()
        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> = error("No registration")
        override suspend fun login(request: LoginRequest): Response<TokenResponse> {
            loginStarted.complete(Unit)
            loginGate?.await()
            return Response.success(issued(8))
        }
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> {
            renewals++
            renewalStarted.complete(Unit)
            renewalGate?.await()
            return Response.success(issued())
        }
        override suspend fun session(bearer: String): Response<SessionResponse> {
            validationStarted.complete(Unit)
            validationGate?.await()
            return if (expireAccess || validationCode != 200) Response.error(
                if (expireAccess) 401 else validationCode, "{}".toResponseBody())
            else Response.success(SessionResponse(true, if (bearer == "Bearer fixture-new") 8 else 7, end))
        }
        override suspend fun logout(bearer: String): Response<Unit> {
            logouts++
            lastBearer = bearer
            logoutStarted.complete(Unit)
            if (logouts == 2) secondLogoutStarted.complete(Unit)
            logoutGate?.await()
            if (logoutFailure) throw IOException()
            return if (logoutCode == 204) Response.success<Unit>(204, null)
                else if (logoutCode == 200) Response.success(Unit)
                else Response.error(logoutCode, "{}".toResponseBody())
        }
    }
    private fun repo(api: Api, store: Store) = AuthRepository(api, store, clock)

    @Test fun cancelDialogLeavesCredentialsAndSessionUntouched() = runBlocking<Unit> {
        val api = Api(); val store = Store(); val repo = repo(api, store)
        repo.validateSession()
        val vm = AuthViewModel(repo, Dispatchers.Unconfined)
        val original = store.value
        vm.requestLogout()
        assertTrue(vm.logout.value.confirmation)
        vm.cancelLogout()
        assertFalse(vm.logout.value.confirmation)
        assertSame(original, store.value)
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        assertEquals(0, api.logouts)
    }

    @Test fun confirmClearsStorageAndShowsWelcomeBeforeRemoteCompletes() = runBlocking<Unit> {
        val api = Api().apply { logoutGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        repo.validateSession()
        val closing = async { repo.logout() }
        api.logoutStarted.await()
        assertNull(store.value.tokens)
        assertEquals(uuid, store.value.installation)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
        assertFalse(closing.isCompleted)
        assertEquals("Bearer fixture-old", api.lastBearer)
        api.logoutGate!!.complete(Unit)
        closing.await()
        assertEquals("Sesión cerrada.", repo.session.value.message)
    }

    @Test fun successRequires204() = runBlocking<Unit> {
        val api = Api().apply { logoutCode = 200 }
        val store = Store(); val repo = repo(api, store)
        repo.logout()
        assertNull(store.value.tokens)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
    }

    @Test fun networkFailureDoesNotRestoreCredentialsOrClaimRevocation() = runBlocking<Unit> {
        val api = Api().apply { logoutFailure = true }
        val store = Store(); val repo = repo(api, store)
        repo.logout()
        assertNull(store.value.tokens)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
        assertEquals(1, api.logouts)
    }

    @Test fun serverFailureDoesNotRestoreCredentialsOrClaimRevocation() = runBlocking<Unit> {
        val api = Api().apply { logoutCode = 503 }
        val store = Store(); val repo = repo(api, store)
        repo.logout()
        assertNull(store.value.tokens)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
    }

    @Test fun expiredProofDoesNotRenewForLogout() = runBlocking<Unit> {
        val api = Api().apply { logoutCode = 401 }
        val store = Store(); val repo = repo(api, store)
        repo.logout()
        assertEquals(0, api.renewals)
        assertNull(store.value.tokens)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
    }

    @Test fun concurrentCloseAttemptsSendOnlyOneRequest() = runBlocking<Unit> {
        val api = Api().apply { logoutGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val first = async { repo.logout() }
        api.logoutStarted.await()
        coroutineScope { List(10) { async { repo.logout() } }.awaitAll() }
        assertEquals(1, api.logouts)
        api.logoutGate!!.complete(Unit)
        first.await()
    }

    @Test fun viewModelRequiresConfirmationAndRejectsDoubleTap() = runBlocking<Unit> {
        val api = Api().apply { logoutGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        repo.validateSession()
        val vm = AuthViewModel(repo, Dispatchers.Unconfined)
        vm.confirmedLogout()
        assertEquals(0, api.logouts)
        vm.requestLogout(); vm.confirmedLogout(); vm.confirmedLogout()
        api.logoutStarted.await()
        assertEquals(1, api.logouts)
        assertEquals(SessionPhase.SIGNED_OUT, vm.session.value.phase)
        vm.onLocalSessionCleared()
        assertFalse(vm.logout.value.confirmation)
        assertFalse(vm.login.value.busy)
        api.logoutGate!!.complete(Unit)
    }

    @Test fun noStoredSessionDoesNotSendAnonymousLogout() = runBlocking<Unit> {
        val api = Api(); val store = Store().apply { value = CredentialVault(uuid, null) }
        repo(api, store).logout()
        assertEquals(0, api.logouts)
    }

    @Test fun cleanupFailureIsBlockedWithoutFalseSuccessAndCanBeRetried() = runBlocking<Unit> {
        val api = Api(); val store = Store().apply { clearFailure = true }
        val repo = repo(api, store)
        repo.logout()
        assertEquals(SessionPhase.CLEAR_FAILED, repo.session.value.phase)
        assertEquals(0, api.logouts)
        store.clearFailure = false
        repo.logout()
        assertNull(store.value.tokens)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals(1, api.logouts)
    }

    @Test fun lateRenewalCannotRestoreTokensAfterImmediateLogout() = runBlocking<Unit> {
        val api = Api().apply { expireAccess = true; renewalGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val renewing = async { repo.validateSession() }
        api.renewalStarted.await()
        repo.logout() // No espera al Mutex ocupado por la petición de renovación.
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertNull(store.value.tokens)
        api.renewalGate!!.complete(Unit)
        renewing.await()
        assertNull(store.value.tokens)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals(1, api.logouts)
    }

    @Test fun lateValidationFailureCannotReplaceWelcomeWithRetry() = runBlocking<Unit> {
        val api = Api().apply { validationCode = 503; validationGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val checking = async { repo.validateSession() }
        api.validationStarted.await()
        repo.logout()
        api.validationGate!!.complete(Unit)
        checking.await()
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
    }

    @Test fun lateLoginCannotRepopulateClearedStorage() = runBlocking<Unit> {
        val api = Api().apply { loginGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val login = async {
            try { repo.login("persona@example.org", "fixture") } catch (_: CancellationException) { }
        }
        api.loginStarted.await()
        repo.logout()
        api.loginGate!!.complete(Unit)
        login.await()
        assertNull(store.value.tokens)
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
    }

    @Test fun oldRemoteAcknowledgementNeverClosesNewLogin() = runBlocking<Unit> {
        val api = Api().apply { logoutGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val oldClosing = async { repo.logout() }
        api.logoutStarted.await()
        repo.login("persona@example.org", "fixture")
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        api.logoutGate!!.complete(Unit)
        oldClosing.await()
        assertEquals(8L, store.value.tokens?.idSesion)
        assertEquals(SessionPhase.AUTHENTICATED, repo.session.value.phase)
        assertEquals("Bearer fixture-old", api.lastBearer)
    }

    @Test fun laterSessionCanCloseWhilePreviousRemoteAttemptIsPending() = runBlocking<Unit> {
        val api = Api().apply { logoutGate = CompletableDeferred() }
        val store = Store(); val repo = repo(api, store)
        val first = async { repo.logout() }
        api.logoutStarted.await()
        repo.login("persona@example.org", "fixture")
        val second = async { repo.logout() }
        // El segundo cierre vuelve a limpiar localmente; ambos intentos siguen sin cola persistente.
        api.secondLogoutStarted.await()
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        api.logoutGate!!.complete(Unit)
        first.await(); second.await()
        assertEquals(2, api.logouts)
        assertEquals("Bearer fixture-new", api.lastBearer)
    }

    @Test fun postLogoutValidationCannotLoseLocalClosureNotice() = runBlocking<Unit> {
        val api = Api().apply { logoutFailure = true }
        val store = Store(); val repo = repo(api, store)
        repo.logout()
        repo.validateSession()
        assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
        assertEquals("Sesión cerrada en este dispositivo.", repo.session.value.message)
        assertNull(store.value.tokens)
    }
}
