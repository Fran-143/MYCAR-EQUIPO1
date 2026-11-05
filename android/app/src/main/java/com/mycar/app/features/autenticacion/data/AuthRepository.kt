package com.mycar.app.features.autenticacion.data

import com.google.gson.Gson
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
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

class AuthProblem(message: String) : Exception(message)

/** Identifica un ciclo validado; no contiene credenciales ni autoriza al backend por sí mismo. */
data class AuthenticatedContext(val sessionId: Long, val version: Long)
class SessionRequired : IOException("SESION_NO_VALIDA")

/**
 * Único dueño de las credenciales. Un Mutex serializa login/validación/rotación.
 * La UI sólo recibe estado, nunca tokens. El reloj local programa comprobaciones;
 * exclusivamente el servidor declara válida o inválida la sesión.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: CredentialStore,
    private val clock: Clock = Clock.systemUTC(),
    private val registrationPolicy: PrivacyPolicyConfiguration = PrivacyPolicyConfiguration(),
    private val clearPrivateData: () -> Unit = {}
) : RegistrationService {
    private val registrationMutex = Mutex()

    override suspend fun register(request: RegistrationRequest) {
        // Registro directo deshabilitado en backend (OTP obligatorio).
        throw RegistrationFailure(RegistrationFailureKind.UNAVAILABLE)
    }

    override suspend fun startEmailVerification(request: RegistrationRequest): VerificationSession =
        withContext(Dispatchers.IO) {
            guardRegistration(request)
            if (!registrationMutex.tryLock()) throw RegistrationFailure(RegistrationFailureKind.IN_PROGRESS)
            try {
                val origin = api.verificationOrigin()
                if (!origin.isSuccessful) throw mapRegistrationHttp(origin.code(), origin.errorBody()?.string())
                val o = origin.body()
                if (o?.idOrigen.isNullOrBlank() || o?.secretoOrigen.isNullOrBlank())
                    throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
                emitVerification(
                    VerificationEmitRequest(
                        idOrigen = o.idOrigen!!,
                        secretoOrigen = o.secretoOrigen!!,
                        idSolicitud = UUID.randomUUID().toString(),
                        revision = 0,
                        formulario = request
                    )
                )
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (known: RegistrationFailure) { throw known
            } catch (_: IOException) { throw RegistrationFailure(RegistrationFailureKind.NETWORK)
            } catch (_: Exception) { throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
            } finally { registrationMutex.unlock() }
        }

    override suspend fun resendEmailCode(
        session: VerificationSession,
        request: RegistrationRequest
    ): VerificationSession = withContext(Dispatchers.IO) {
        guardRegistration(request)
        if (!registrationMutex.tryLock()) throw RegistrationFailure(RegistrationFailureKind.IN_PROGRESS)
        try {
            emitVerification(
                VerificationEmitRequest(
                    idOrigen = session.idOrigen,
                    secretoOrigen = session.secretoOrigen,
                    idOperacion = session.idOperacion,
                    secretoOperacion = session.secretoOperacion,
                    idSolicitud = UUID.randomUUID().toString(),
                    revision = session.revision,
                    formulario = request
                )
            )
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (known: RegistrationFailure) { throw known
        } catch (_: IOException) { throw RegistrationFailure(RegistrationFailureKind.NETWORK)
        } catch (_: Exception) { throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
        } finally { registrationMutex.unlock() }
    }

    override suspend fun completeEmailVerification(
        session: VerificationSession,
        code: String,
        request: RegistrationRequest
    ) = withContext(Dispatchers.IO) {
        guardRegistration(request)
        if (!registrationMutex.tryLock()) throw RegistrationFailure(RegistrationFailureKind.IN_PROGRESS)
        try {
            val response = api.verificationComplete(
                VerificationCompleteRequest(
                    idOperacion = session.idOperacion,
                    secretoOperacion = session.secretoOperacion,
                    idSolicitud = UUID.randomUUID().toString(),
                    revision = session.revision,
                    idEmision = session.idEmision,
                    codigo = code.trim(),
                    formulario = request
                )
            )
            if (response.code() == 201) {
                val body = response.body()
                if (body?.resultado != "CUENTA_CREADA" || body.destino != "INICIO_SESION")
                    throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
            } else {
                throw mapRegistrationHttp(response.code(), response.errorBody()?.string())
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (known: RegistrationFailure) { throw known
        } catch (_: IOException) { throw RegistrationFailure(RegistrationFailureKind.NETWORK)
        } catch (_: Exception) { throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
        } finally { registrationMutex.unlock() }
    }

    private fun guardRegistration(request: RegistrationRequest) {
        if (!registrationPolicy.available) throw RegistrationFailure(RegistrationFailureKind.PRIVACY_PENDING)
        if (request.consentimientoPrivacidad.version != registrationPolicy.version)
            throw RegistrationFailure(RegistrationFailureKind.VERSION_MISMATCH)
        if (!request.consentimientoPrivacidad.aceptado)
            throw RegistrationFailure(RegistrationFailureKind.VALIDATION,
                mapOf("privacidadAceptada" to RegistrationValidator.messages.getValue("privacidadAceptada")))
    }

    private suspend fun emitVerification(body: VerificationEmitRequest): VerificationSession {
        val response = api.verificationEmit(body)
        if (response.code() != 202 && response.code() != 200) {
            throw mapRegistrationHttp(response.code(), response.errorBody()?.string())
        }
        val emit = response.body()
        if (emit?.resultado != "EMISION_CREADA" ||
            emit.idOperacion.isNullOrBlank() ||
            emit.secretoOperacion.isNullOrBlank() ||
            emit.idEmision.isNullOrBlank()) {
            throw RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
        }
        return VerificationSession(
            idOrigen = body.idOrigen,
            secretoOrigen = body.secretoOrigen,
            idOperacion = emit.idOperacion!!,
            secretoOperacion = emit.secretoOperacion!!,
            idEmision = emit.idEmision!!,
            revision = emit.revision,
            codigoDesarrollo = emit.codigoDesarrollo
        )
    }

    private fun mapRegistrationHttp(code: Int, raw: String?): RegistrationFailure {
        val error = try {
            raw?.let { Gson().fromJson(it, RegistrationError::class.java) }
        } catch (_: Exception) { null }
        val codigo = error?.codigo.orEmpty()
        val mensajeServidor = error?.mensaje?.takeIf { it.isNotBlank() }
        return when {
            code == 400 -> {
                val fields = error?.erroresPorCampo?.keys.orEmpty()
                    .mapNotNull { key -> RegistrationValidator.messages[key]?.let { key to it } }.toMap()
                RegistrationFailure(if ("versionPrivacidad" in fields) RegistrationFailureKind.VERSION_MISMATCH
                    else RegistrationFailureKind.VALIDATION, fields)
            }
            code == 409 -> RegistrationFailure(RegistrationFailureKind.EMAIL_UNAVAILABLE)
            code == 429 || codigo in RATE_LIMIT_CODES -> RegistrationFailure(
                RegistrationFailureKind.RATE_LIMITED,
                detail = mensajeServidor ?: messageForRateLimit(codigo)
            )
            code == 422 -> RegistrationFailure(
                RegistrationFailureKind.VALIDATION,
                detail = mensajeServidor ?: "Código incorrecto o solicitud inválida."
            )
            code == 503 || code == 500 || code == 410 ->
                RegistrationFailure(RegistrationFailureKind.UNAVAILABLE)
            else -> RegistrationFailure(RegistrationFailureKind.UNCONFIRMED)
        }
    }

    private companion object {
        val RATE_LIMIT_CODES = setOf(
            "ESPERA_REENVIO", "LIMITE_ENVIO", "LIMITE_TECNICO",
            "RESERVA_PENDIENTE", "PAUSA_TECNICA", "BLOQUEO_ACTIVO", "BLOQUEO_INICIADO"
        )

        fun messageForRateLimit(codigo: String) = when (codigo) {
            "ESPERA_REENVIO" ->
                "Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro."
            "LIMITE_ENVIO", "LIMITE_TECNICO" ->
                "Alcanzaste el límite de envíos. Espera un rato e inténtalo de nuevo."
            "RESERVA_PENDIENTE", "PAUSA_TECNICA" ->
                "Hay un envío en curso. Espera unos segundos e inténtalo de nuevo."
            "BLOQUEO_ACTIVO", "BLOQUEO_INICIADO" ->
                "Demasiados intentos fallidos. Espera aproximadamente 1 hora antes de reintentar."
            else ->
                "Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro."
        }
    }

    private val mutex = Mutex()
    private val localLock = Any()
    private var generation = 0L
    private var cleanupBlocked = false
    private var vault: CredentialVault? = null
    private val mutableSession = MutableStateFlow(SessionState(SessionPhase.CHECKING))
    val session = mutableSession.asStateFlow()

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

    fun authenticatedContext(): AuthenticatedContext? = synchronized(localLock) {
        if (cleanupBlocked || mutableSession.value.phase != SessionPhase.AUTHENTICATED) null
        else vault?.tokens?.let { AuthenticatedContext(it.idSesion, generation) }
    }

    /** Único emisor para los clientes del equipo. Serializa renovación; jamás reintenta su POST. */
    suspend fun authorization(context: AuthenticatedContext): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = synchronized(localLock) {
                ensureContext(context)
                requireNotNull(vault?.tokens)
            }
            if (!clock.instant().isBefore(Instant.parse(previous.accessTokenExpiraUtc).minusSeconds(10))) {
                try { renew(previous, context.version) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { retry(context.version) }
            }
            synchronized(localLock) {
                ensureContext(context)
                "Bearer ${requireNotNull(vault?.tokens).accessToken}"
            }
        }
    }
    private fun ensureContext(context: AuthenticatedContext) {
        if (cleanupBlocked || generation != context.version || vault?.tokens?.idSesion != context.sessionId)
            throw SessionRequired()
        when (mutableSession.value.phase) {
            SessionPhase.AUTHENTICATED -> Unit
            SessionPhase.RETRY -> throw IOException("No se pudo validar la sesión; vuelve a intentarlo.")
            else -> throw SessionRequired()
        }
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
                // Primero cerrar escritores y limpiar RF08/RF17; un fallo bloquea el cambio de cuenta.
                clearPrivateData()
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
        // Al volver del segundo plano conservar el ámbito autenticado mientras se valida.
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
