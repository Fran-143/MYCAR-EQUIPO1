package com.mycar.app.features.alertas.programacion

import com.mycar.app.features.autenticacion.data.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Prueba el puente compartido con RF02: no otro almacén ni renovación propia de RF22. */
class PeticionesSesionMyCarTest {
    private val fin = "2030-01-02T00:00:00Z"
    private val expira = "2030-01-01T00:05:00Z"
    private val reloj = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
    private inner class Store : CredentialStore {
        var value: CredentialVault? = CredentialVault(UUID.randomUUID().toString(), StoredTokens(7, "fixture-old", expira, "fixture-refresh", fin))
        override fun load() = value
        override fun save(vault: CredentialVault) { value = vault }
    }
    private inner class Api : AuthApi {
        var renovar = false; var renovaciones = 0
        override suspend fun register(request: RegistrationRequest): Response<RegistrationResponse> = error("No corresponde")
        override suspend fun login(request: LoginRequest): Response<TokenResponse> = error("No corresponde")
        override suspend fun logout(bearer: String): Response<Unit> = Response.success<Unit>(204, null)
        override suspend fun session(bearer: String): Response<SessionResponse> =
            if (renovar && bearer.endsWith("old")) Response.error(401, "{}".toResponseBody()) else Response.success(SessionResponse(true, 7, fin))
        override suspend fun renew(request: RenewalRequest): Response<TokenResponse> {
            renovaciones++; return Response.success(TokenResponse("AUTENTICADO", 7, "fixture-new", expira, "fixture-refresh-new", fin))
        }
    }
    @Test fun usaPruebaDelAlmacenUnicoYExponeSoloIdNoSecretoAlHost() = runBlocking {
        val repo = AuthRepository(Api(), Store(), reloj)
        var prueba: String? = null
        val r = repo.authenticatedRequest { prueba = it; Response.success("confirmado") }
        assertEquals("Bearer fixture-old", prueba); assertEquals("confirmado", r.body()); assertEquals(7L, repo.currentAuthenticatedSessionId())
    }
    @Test fun sinSesionNoEnviaLaPeticion() = runBlocking {
        val store = Store(); store.value = CredentialVault(UUID.randomUUID().toString(), null)
        val repo = AuthRepository(Api(), store, reloj); var envios = 0
        val r = runCatching { repo.authenticatedRequest { envios++; Response.success("no") } }
        assertTrue(r.exceptionOrNull() is AuthProblem); assertEquals(0, envios); assertNull(repo.currentAuthenticatedSessionId())
    }
    @Test fun renuevaConRf02AntesDeEnviarUnaSolaVez() = runBlocking {
        val api = Api().apply { renovar = true }; val repo = AuthRepository(api, Store(), reloj); var prueba: String? = null; var envios = 0
        repo.authenticatedRequest { prueba = it; envios++; Response.success("ok") }
        assertEquals(1, api.renovaciones); assertEquals(1, envios); assertEquals("Bearer fixture-new", prueba)
    }
    @Test fun respuesta401NoRepiteAutomaticamenteElPost() = runBlocking {
        val repo = AuthRepository(Api(), Store(), reloj); var envios = 0
        val r: Response<String> = repo.authenticatedRequest { envios++; Response.error(401, "{}".toResponseBody()) }
        assertEquals(401, r.code()); assertEquals(1, envios)
    }
    @Test fun respuestaPerdidaNoSeReenviaDesdeElClienteCompartido() = runBlocking {
        val repo = AuthRepository(Api(), Store(), reloj); var envios = 0
        val r = runCatching { repo.authenticatedRequest<String> { envios++; throw IOException("Fixture: respuesta perdida") } }
        assertTrue(r.isFailure); assertEquals(1, envios)
    }
    @Test fun cierreLocalNoEsperaRedYRespuestaTardiaSeDescarta() = runBlocking {
        val store = Store(); val repo = AuthRepository(Api(), store, reloj)
        val entro = CompletableDeferred<Unit>(); val responder = CompletableDeferred<Unit>()
        val tarea = async { repo.authenticatedRequest { entro.complete(Unit); responder.await(); Response.success("tardia") } }
        entro.await(); repo.logout(); assertNull(store.value?.tokens); responder.complete(Unit)
        assertTrue(runCatching { tarea.await() }.exceptionOrNull() is CancellationException)
        assertNull(repo.currentAuthenticatedSessionId()); assertEquals(SessionPhase.SIGNED_OUT, repo.session.value.phase)
    }
    @Test fun cierreLimpiaModuloAunqueSuPantallaYaNoEsteYBloqueaEscriturasAntiguas() = runBlocking {
        val repo = AuthRepository(Api(), Store(), reloj); repo.validateSession()
        val version = repo.session.value.contextVersion; var pendiente = true
        repo.registerLocalCleanup("rf22") { pendiente = false }
        repo.withLocalSessionContext(version) { pendiente = true }
        repo.logout(); assertFalse(pendiente)
        assertTrue(runCatching { repo.withLocalSessionContext(version) { pendiente = true } }.exceptionOrNull() is CancellationException)
        assertFalse(pendiente)
    }
}
