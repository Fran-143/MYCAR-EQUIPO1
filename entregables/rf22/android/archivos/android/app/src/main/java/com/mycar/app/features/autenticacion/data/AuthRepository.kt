package com.mycar.app.features.autenticacion.data

import com.google.gson.Gson
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import retrofit2.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SessionPhase { CHECKING, SIGNED_OUT, AUTHENTICATED, RETRY, CLEAR_FAILED }
data class SessionState(
    val phase: SessionPhase, val message: String? = null, val contextVersion: Long = 0,
    val hasLocalSession: Boolean = false
) {
    // RETRY no implica revocación: permite limpiar credenciales existentes aun sin conexión.
    val canRequestLogout: Boolean get() = phase == SessionPhase.AUTHENTICATED ||
        phase == SessionPhase.CLEAR_FAILED || (phase == SessionPhase.RETRY && hasLocalSession)
}
data class AuthenticatedContext(val sessionId: Long, val contextVersion: Long)

class AuthProblem(message: String) : Exception(message)

/**
 * Único dueño de las credenciales. Un Mutex serializa login/validación/rotación.
 * La UI sólo recibe estado, nunca tokens. El reloj local programa comprobaciones;
 * exclusivamente el servidor declara válida o inválida la sesión.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: CredentialStore,
    private val clock: Clock = Clock.systemUTC(),
    private val registrationPolicy: PrivacyPolicyConfiguration = PrivacyPolicyConfiguration()
) : RegistrationService {
    private val registrationMutex = Mutex()

    override suspend fun register(request: RegistrationRequest) = withContext(Dispatchers.IO) {
        if (!registrationPolicy.available) throw RegistrationFailure(RegistrationFailureKind.PRIVACY_PENDING)
        if (request.consentimientoPrivacidad.version != registrationPolicy.version)
            throw RegistrationFailure(RegistrationFailureKind.VERSION_MISMATCH)
        if (!request.consentimientoPrivacidad.aceptado)
            throw RegistrationFailure(RegistrationFailureKind.VALIDATION,
                mapOf("privacidadAceptada" to RegistrationValidator.messages.getValue("privacidadAceptada")))
        if (!registrationMutex.tryLock()) throw RegistrationFailure(RegistrationFailureKind.IN_PROGRESS)
        try {
            val response = api.register(request)
            if (response.code() == 201) {
                val body = response.body()
                if (body?.resultado != "CUENTA_CREADA" || body.destino != "INICIO_SESION")
                    throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
                // No se llama a login, persist, load ni se modifica el estado de sesión.
            } else {
                val error = response.errorBody()?.use { body ->
                    try { Gson().fromJson(body.string(), RegistrationError::class.java) }
                    catch (_: Exception) { null }
                }
                throw when (response.code()) {
                    400 -> {
                        val fields = error?.erroresPorCampo?.keys.orEmpty()
                            .mapNotNull { key -> RegistrationValidator.messages[key]?.let { key to it } }.toMap()
                        RegistrationFailure(if ("versionPrivacidad" in fields) RegistrationFailureKind.VERSION_MISMATCH
                            else RegistrationFailureKind.VALIDATION, fields)
                    }
                    409 -> RegistrationFailure(RegistrationFailureKind.EMAIL_UNAVAILABLE)
                    503, 500 -> RegistrationFailure(RegistrationFailureKind.UNAVAILABLE)
                    else -> RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (known: RegistrationFailure) { throw known
        } catch (_: IOException) { throw RegistrationFailure(RegistrationFailureKind.NETWORK)
        } catch (_: Exception) { throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
        } finally { registrationMutex.unlock() }
    }

    private val mutex = Mutex()
    private val localLock = Any()
    private var generation = 0L
    private var cleanupBlocked = false
    private var vault: CredentialVault? = null
    private val localCleanups = linkedMapOf<String, () -> Unit>()
    private val mutableSession = MutableStateFlow(SessionState(SessionPhase.CHECKING))
    val session = mutableSession.asStateFlow()

    /** Cada módulo registra limpieza local sin que autenticación conozca sus archivos. Sin red. */
    fun registerLocalCleanup(module: String, cleanup: () -> Unit) = synchronized(localLock) {
        localCleanups[module] = cleanup
    }
    /** Serializa archivos sensibles con el cierre; una tarea anterior no los recrea después. */
    fun <T> withLocalSessionContext(expected: Long, action: () -> T): T = synchronized(localLock) {
        ensureCurrent(expected)
        if (mutableSession.value.phase != SessionPhase.AUTHENTICATED) throw AuthProblem("Sesión no disponible.")
        action()
    }

    /** Identificador no secreto para delimitar ViewModels; las credenciales siguen siendo privadas. */
    fun currentAuthenticatedContext(): AuthenticatedContext? = synchronized(localLock) {
        if (!cleanupBlocked && mutableSession.value.phase == SessionPhase.AUTHENTICATED)
            vault?.tokens?.idSesion?.let { AuthenticatedContext(it, generation) } else null
    }
    fun currentAuthenticatedSessionId(): Long? = currentAuthenticatedContext()?.sessionId

    /**
     * Adaptador compartido para módulos protegidos. Valida/renueva con RF02 antes del envío.
     * Nunca repite automáticamente una escritura y descarta respuestas de un contexto retirado.
     * El bearer sólo se entrega a la capa de transporte, no a ViewModels ni pantallas.
     */
    suspend fun <T> authenticatedRequest(request: suspend (String) -> Response<T>): Response<T> {
        val expected = version()
        validateSession()
        val response = withContext(Dispatchers.IO) {
            mutex.withLock {
                val bearer = synchronized(localLock) {
                    ensureCurrent(expected)
                    if (mutableSession.value.phase != SessionPhase.AUTHENTICATED)
                        throw AuthProblem("No se pudo validar la sesión. Vuelve a intentarlo.")
                    "Bearer ${requireNotNull(vault?.tokens).accessToken}"
                }
                val result = request(bearer)
                synchronized(localLock) { ensureCurrent(expected) }
                result
            }
        }
        // Un 401 puede retirar/renovar la sesión; el módulo concilia su operación, no se repite el POST aquí.
        synchronized(localLock) { ensureCurrent(expected) }
        if (response.code() == 401) validateSession()
        synchronized(localLock) { ensureCurrent(expected) }
        return response
    }

    private fun version(): Long = synchronized(localLock) { generation }
    private fun ensureCurrent(expected: Long) {
        if (expected != generation || cleanupBlocked) throw CancellationException("Session context retired")
    }
    private fun load(): CredentialVault = synchronized(localLock) {
        vault ?: (store.load()?.validate() ?: CredentialVault(UUID.randomUUID().toString(), null)
            .also { store.save(it) }).also { vault = it }
    }
    private fun persist(tokens: StoredTokens?, expected: Long) = synchronized(localLock) {
        ensureCurrent(expected)
        val next = CredentialVault(load().installation, tokens)
        store.save(next)
        vault = next
    }
    private fun publish(expected: Long, state: SessionState) = synchronized(localLock) {
        ensureCurrent(expected)
        mutableSession.value = state.copy(contextVersion = generation)
    }

    suspend fun login(email: String, password: String) {
        val expected = version()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    synchronized(localLock) { ensureCurrent(expected) }
                    val response = api.login(LoginRequest(email.trim(), password, load().installation))
                    if (!response.isSuccessful) {
                        response.errorBody()?.close()
                        throw AuthProblem(when (response.code()) {
                            400 -> "Revisa el correo y la contraseña."
                            401 -> "Correo o contraseña incorrectos."
                            403 -> "La cuenta no está habilitada para iniciar sesión."
                            else -> "No se pudo iniciar sesión. Inténtalo de nuevo."
                        })
                    }
                    val tokens = response.body().toStored()
                    withContext(NonCancellable) { persist(tokens, expected) }
                    publish(expected, SessionState(SessionPhase.AUTHENTICATED))
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (known: AuthProblem) { throw known
                } catch (_: IOException) { throw AuthProblem("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.")
                } catch (_: Exception) { throw AuthProblem("No se pudo completar el inicio de sesión de forma segura.") }
            }
        }
    }

    suspend fun validateSession() {
        val expected = version()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    synchronized(localLock) { ensureCurrent(expected) }
                    val tokens = load().tokens
                    if (tokens == null) {
                        // Conserva el aviso RF06 mientras no se haya iniciado otra sesión.
                        synchronized(localLock) {
                            ensureCurrent(expected)
                            if (mutableSession.value.phase != SessionPhase.SIGNED_OUT)
                                publish(expected, SessionState(SessionPhase.SIGNED_OUT))
                        }
                        return@withLock
                    }
                    val response = api.session("Bearer ${tokens.accessToken}")
                    if (response.code() == 401) {
                        response.errorBody()?.close()
                        synchronized(localLock) { ensureCurrent(expected) }
                        renew(tokens, expected)
                    } else if (response.isSuccessful) {
                        val body = response.body()
                        check(body?.vigente == true && body.idSesion == tokens.idSesion &&
                            Instant.parse(body.sesionExpiraUtc) == Instant.parse(tokens.sesionExpiraUtc))
                        publish(expected, SessionState(SessionPhase.AUTHENTICATED))
                    } else {
                        response.errorBody()?.close()
                        retry(expected)
                    }
                } catch (cancelled: CancellationException) {
                    // Una respuesta antigua no cancela el mantenimiento de una sesión posterior.
                    if (currentCoroutineContext().isActive && expected != version()) return@withLock
                    throw cancelled
                } catch (_: Exception) { retry(expected) }
            }
        }
    }

    private suspend fun renew(previous: StoredTokens, expected: Long) {
        withContext(NonCancellable) {
            synchronized(localLock) { ensureCurrent(expected) }
            val response = api.renew(RenewalRequest(load().installation, previous.refreshToken))
            if (response.code() == 401 || response.code() == 403) {
                response.errorBody()?.close()
                persist(null, expected)
                publish(expected, SessionState(SessionPhase.SIGNED_OUT, "Tu sesión ya no es válida. Inicia sesión de nuevo."))
            } else if (response.isSuccessful) {
                val fresh = response.body().toStored()
                check(fresh.idSesion == previous.idSesion &&
                    Instant.parse(fresh.sesionExpiraUtc) == Instant.parse(previous.sesionExpiraUtc))
                persist(fresh, expected)
                publish(expected, SessionState(SessionPhase.AUTHENTICATED))
            } else {
                response.errorBody()?.close()
                retry(expected)
            }
        }
    }

    private fun retry(expected: Long) = synchronized(localLock) {
        if (expected == generation && !cleanupBlocked)
            publish(expected, SessionState(SessionPhase.RETRY,
                "No se pudo validar la sesión. Revisa la conexión y vuelve a intentarlo.",
                hasLocalSession = vault?.tokens != null))
    }

    /**
     * No espera el Mutex de red: la limpieza sólo toma el bloqueo del almacenamiento local.
     * El JWT capturado vive únicamente en esta llamada y se descarta al finalizar.
     * No renueva tokens, no encola ni reintenta la revocación.
     */
    suspend fun logout() = withContext(Dispatchers.IO + NonCancellable) {
        var proof: String? = null
        val closedVersion = synchronized(localLock) {
            val existing = load()
            if (existing.tokens == null && !cleanupBlocked) return@withContext
            generation++
            cleanupBlocked = true // Invalida inmediatamente las operaciones iniciadas antes del cierre.
            try {
                proof = existing.tokens?.accessToken
                localCleanups.values.forEach { it() }
                store.clearSession(existing.installation)
                vault = CredentialVault(existing.installation, null)
                cleanupBlocked = false
                mutableSession.value = SessionState(SessionPhase.SIGNED_OUT,
                    "Sesión cerrada en este dispositivo.", generation)
            } catch (_: Exception) {
                proof = null
                mutableSession.value = SessionState(SessionPhase.CLEAR_FAILED,
                    "No se pudo completar la limpieza local. Vuelve a intentarlo.", generation)
                return@withContext // No confirma limpieza ni revocación.
            }
            generation
        }
        try {
            val captured = proof
            if (captured != null) {
                val response = api.logout("Bearer $captured")
                response.errorBody()?.close()
                synchronized(localLock) {
                    // La respuesta de un cierre antiguo no expulsa ni cambia una sesión nueva.
                    if (closedVersion == generation && vault?.tokens == null && !cleanupBlocked) {
                        mutableSession.value = SessionState(SessionPhase.SIGNED_OUT,
                            if (response.code() == 204) "Sesión cerrada."
                            else "Sesión cerrada en este dispositivo.", generation)
                    }
                }
            }
        } catch (_: Exception) {
            // El cierre local ya es efectivo: nunca se restauran tokens ni se afirma revocación.
        } finally { proof = null }
    }

    suspend fun maintainSession() {
        synchronized(localLock) {
            if (!cleanupBlocked && mutableSession.value.phase != SessionPhase.SIGNED_OUT)
                mutableSession.value = SessionState(SessionPhase.CHECKING, contextVersion = generation)
        }
        while (currentCoroutineContext().isActive) {
            if (!synchronized(localLock) { cleanupBlocked }) validateSession()
            val wait = synchronized(localLock) {
                if (mutableSession.value.phase == SessionPhase.AUTHENTICATED) {
                    val expiry = vault?.tokens?.accessTokenExpiraUtc?.let(Instant::parse)
                    val remaining = expiry?.toEpochMilli()?.minus(clock.millis()) ?: 0L
                    if (remaining <= 0L) 30_000L else remaining.coerceIn(1_000L, 60_000L)
                } else 30_000L
            }
            delay(wait)
        }
    }

    private fun TokenResponse?.toStored(): StoredTokens {
        check(this?.estado == "AUTENTICADO")
        return StoredTokens(requireNotNull(this?.idSesion), requireNotNull(this?.accessToken),
            requireNotNull(this?.accessTokenExpiraUtc), requireNotNull(this?.refreshToken),
            requireNotNull(this?.sesionExpiraUtc)).validate()
    }
}
