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

enum class SessionPhase { CHECKING, SIGNED_OUT, AUTHENTICATED, RETRY, CLEAR_FAILED, MFA_FLOW }
enum class MfaStep {
    ENROLL_INTRO, ENROLL_QR, ENROLL_CONFIRM, RECOVERY_CODES,
    VERIFY, BLOCKED, RECOVER, RECONFIGURE
}
data class MfaUiState(
    val step: MfaStep,
    val desafioId: String? = null,
    val otpauthUri: String? = null,
    val secretoManual: String? = null,
    val recoveryCodes: List<String> = emptyList(),
    val intento: Int? = null,
    val maxIntentos: Int = 5,
    val bloqueoHastaUtc: String? = null,
    val error: String? = null,
    val busy: Boolean = false
)
data class SessionState(
    val phase: SessionPhase, val message: String? = null, val contextVersion: Long = 0,
    val hasLocalSession: Boolean = false,
    val alcance: String? = null,
    val mfa: MfaUiState? = null
) {
    // RETRY no implica revocación: permite limpiar credenciales existentes aun sin conexión.
    val canRequestLogout: Boolean get() = phase == SessionPhase.AUTHENTICATED ||
        phase == SessionPhase.CLEAR_FAILED || phase == SessionPhase.MFA_FLOW ||
        (phase == SessionPhase.RETRY && hasLocalSession)
    val isAdminSession: Boolean get() = phase == SessionPhase.AUTHENTICATED && alcance == "ADMIN"
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
    private val clearPrivateData: () -> Unit = {},
    private val onSessionReady: suspend (accessToken: String, installation: String) -> Unit = { _, _ -> },
    private val onSessionClosing: suspend (accessToken: String?, installation: String) -> Unit = { _, _ -> }
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
                catch (_: Exception) {
                    retry(context.version)
                    // Temporal: no SessionRequired (evita 401 sintético / “sesión inválida”).
                    throw IOException("No se pudo renovar la sesión. Revisa la conexión.")
                }
                // renew() ante HTTP 5xx → RETRY sin lanzar; 401/403 → SIGNED_OUT (SessionRequired abajo).
                if (synchronized(localLock) { mutableSession.value.phase == SessionPhase.RETRY }) {
                    throw IOException("No se pudo renovar la sesión. Revisa la conexión.")
                }
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
            // RETRY: forzar revalidación vía 401 sintético, no confundir con fallo de red del API.
            SessionPhase.RETRY -> throw SessionRequired()
            else -> throw SessionRequired()
        }
    }

    suspend fun login(email: String, password: String) {
        val expected = version()
        var pushSync: Pair<String, String>? = null
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
                    val body = response.body()
                    val estado = body?.estado
                    when (estado) {
                        "MFA_ENROLL_REQUERIDO" -> {
                            val tokens = body.toStoredChallenge()
                            withContext(NonCancellable) { persist(tokens, expected) }
                            publish(expected, SessionState(
                                SessionPhase.MFA_FLOW,
                                alcance = tokens.alcance,
                                hasLocalSession = true,
                                mfa = MfaUiState(MfaStep.ENROLL_INTRO, desafioId = tokens.desafioId)
                            ))
                        }
                        "MFA_REQUERIDO" -> {
                            val tokens = body.toStoredChallenge()
                            withContext(NonCancellable) { persist(tokens, expected) }
                            publish(expected, SessionState(
                                SessionPhase.MFA_FLOW,
                                alcance = tokens.alcance,
                                hasLocalSession = true,
                                mfa = MfaUiState(MfaStep.VERIFY, desafioId = tokens.desafioId)
                            ))
                        }
                        "AUTENTICADO" -> {
                            val tokens = body.toStored()
                            val installation = load().installation
                            withContext(NonCancellable) { persist(tokens, expected) }
                            publish(expected, SessionState(SessionPhase.AUTHENTICATED, alcance = tokens.alcance))
                            pushSync = tokens.accessToken to installation
                        }
                        else -> throw AuthProblem("No se pudo completar el inicio de sesión de forma segura.")
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (known: AuthProblem) { throw known
                } catch (_: IOException) { throw AuthProblem("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.")
                } catch (_: Exception) { throw AuthProblem("No se pudo completar el inicio de sesión de forma segura.") }
            }
            notifySessionReadyOutsideMutex(pushSync)
        }
    }

    suspend fun mfaStartEnroll() = mfaAction { expected, bearer, mfa ->
        val response = api.mfaEnrollStart(bearer)
        if (!response.isSuccessful) throw mapMfaError(response.code(), response.errorBody()?.string())
        val body = response.body()
        if (body?.otpauthUri.isNullOrBlank() || body?.secretoManual.isNullOrBlank())
            throw AuthProblem("No se pudo generar el autenticador.")
        publish(expected, mutableSession.value.copy(
            mfa = mfa.copy(
                step = MfaStep.ENROLL_QR,
                otpauthUri = body.otpauthUri,
                secretoManual = body.secretoManual,
                error = null,
                busy = false
            )
        ))
    }

    suspend fun mfaConfirmEnroll(codigo: String) = mfaAction { expected, bearer, mfa ->
        val response = api.mfaEnrollConfirm(bearer, MfaCodigoRequest(codigo.trim()))
        if (!response.isSuccessful) throw mapMfaError(response.code(), response.errorBody()?.string())
        val codes = response.body()?.codigosRecuperacion.orEmpty()
        if (codes.isEmpty()) throw AuthProblem("No se recibieron códigos de recuperación.")
        publish(expected, mutableSession.value.copy(
            mfa = mfa.copy(step = MfaStep.RECOVERY_CODES, recoveryCodes = codes, error = null, busy = false)
        ))
    }

    suspend fun mfaConfirmBackup() = mfaAction { expected, bearer, _ ->
        val response = api.mfaEnrollConfirmBackup(bearer)
        if (!response.isSuccessful) throw mapMfaError(response.code(), response.errorBody()?.string())
        val tokens = response.body().toStored()
        withContext(NonCancellable) { persist(tokens, expected) }
        publish(expected, SessionState(SessionPhase.AUTHENTICATED, alcance = tokens.alcance))
        notifySessionReadyOutsideMutex(tokens.accessToken to load().installation)
    }

    suspend fun mfaVerify(codigo: String) = mfaAction { expected, bearer, mfa ->
        val desafioId = mfa.desafioId ?: load().tokens?.desafioId
            ?: throw AuthProblem("Desafío MFA no disponible. Inicia sesión de nuevo.")
        val response = api.mfaVerify(bearer, MfaVerificarRequest(desafioId, codigo.trim()))
        if (!response.isSuccessful) {
            val err = parseMfaError(response.code(), response.errorBody()?.string())
            if (err.codigo == "MFA_BLOQUEO") {
                publish(expected, mutableSession.value.copy(
                    mfa = mfa.copy(step = MfaStep.BLOCKED, bloqueoHastaUtc = err.bloqueoHastaUtc,
                        error = err.mensaje, busy = false)
                ))
                return@mfaAction
            }
            publish(expected, mutableSession.value.copy(
                mfa = mfa.copy(intento = err.intento, maxIntentos = err.maxIntentos ?: 5,
                    error = err.mensaje, busy = false)
            ))
            throw AuthProblem(err.mensaje)
        }
        val tokens = response.body().toStored()
        withContext(NonCancellable) { persist(tokens, expected) }
        publish(expected, SessionState(SessionPhase.AUTHENTICATED, alcance = tokens.alcance))
        notifySessionReadyOutsideMutex(tokens.accessToken to load().installation)
    }

    suspend fun mfaRecover(codigoRespaldo: String) = mfaAction { expected, bearer, mfa ->
        val desafioId = mfa.desafioId ?: load().tokens?.desafioId
            ?: throw AuthProblem("Desafío MFA no disponible. Inicia sesión de nuevo.")
        val response = api.mfaRecover(bearer, MfaRecuperarRequest(desafioId, codigoRespaldo.trim()))
        if (!response.isSuccessful) throw mapMfaError(response.code(), response.errorBody()?.string())
        val tokens = response.body().toStoredChallenge(forceAlcance = "MFA_REENROLL")
        withContext(NonCancellable) { persist(tokens, expected) }
        publish(expected, SessionState(
            SessionPhase.MFA_FLOW,
            alcance = "MFA_REENROLL",
            hasLocalSession = true,
            mfa = MfaUiState(MfaStep.RECONFIGURE)
        ))
    }

    fun mfaGoToRecover() {
        val expected = version()
        synchronized(localLock) {
            val mfa = mutableSession.value.mfa ?: return
            publish(expected, mutableSession.value.copy(
                mfa = mfa.copy(step = MfaStep.RECOVER, error = null)
            ))
        }
    }

    fun mfaContinueFromIntro() {
        val expected = version()
        synchronized(localLock) {
            val mfa = mutableSession.value.mfa ?: return
            publish(expected, mutableSession.value.copy(
                mfa = mfa.copy(step = MfaStep.ENROLL_QR, error = null)
            ))
        }
    }

    fun mfaContinueToConfirm() {
        val expected = version()
        synchronized(localLock) {
            val mfa = mutableSession.value.mfa ?: return
            publish(expected, mutableSession.value.copy(
                mfa = mfa.copy(step = MfaStep.ENROLL_CONFIRM, error = null)
            ))
        }
    }

    private suspend fun mfaAction(block: suspend (Long, String, MfaUiState) -> Unit) {
        val expected = version()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val mfa = synchronized(localLock) {
                        ensureCurrent(expected)
                        val current = mutableSession.value.mfa
                            ?: throw AuthProblem("Flujo MFA no activo.")
                        publish(expected, mutableSession.value.copy(mfa = current.copy(busy = true, error = null)))
                        current
                    }
                    val tokens = load().tokens ?: throw AuthProblem("Sesión MFA no disponible.")
                    block(expected, "Bearer ${tokens.accessToken}", mfa)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (known: AuthProblem) {
                    synchronized(localLock) {
                        val current = mutableSession.value.mfa
                        if (current != null && expected == generation)
                            publish(expected, mutableSession.value.copy(
                                mfa = current.copy(busy = false, error = known.message)
                            ))
                    }
                    throw known
                } catch (_: IOException) {
                    throw AuthProblem("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.")
                } catch (_: Exception) {
                    throw AuthProblem("No se pudo completar la verificación MFA.")
                }
            }
        }
    }

    private data class MfaErr(
        val codigo: String, val mensaje: String, val intento: Int? = null,
        val maxIntentos: Int? = null, val bloqueoHastaUtc: String? = null
    )
    private fun parseMfaError(code: Int, raw: String?): MfaErr {
        val parsed = try { raw?.let { Gson().fromJson(it, Map::class.java) } } catch (_: Exception) { null }
        val codigo = parsed?.get("codigo")?.toString().orEmpty()
        val mensaje = parsed?.get("mensaje")?.toString()?.takeIf { it.isNotBlank() }
            ?: when (code) {
                401 -> "Código incorrecto."
                403 -> "Operación no permitida."
                429 -> "Demasiados intentos. Espera e inténtalo de nuevo."
                503 -> "MFA no disponible en el servidor."
                else -> "No se pudo verificar el código."
            }
        val intento = (parsed?.get("intento") as? Number)?.toInt()
        val max = (parsed?.get("maxIntentos") as? Number)?.toInt()
        val bloqueo = parsed?.get("bloqueoHastaUtc")?.toString()
        return MfaErr(codigo, if (intento != null && max != null)
            "Código incorrecto. Intento $intento de $max." else mensaje, intento, max, bloqueo)
    }
    private fun mapMfaError(code: Int, raw: String?): AuthProblem = AuthProblem(parseMfaError(code, raw).mensaje)

    suspend fun validateSession() {
        val expected = version()
        var pushSync: Pair<String, String>? = null
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val previousPhase = synchronized(localLock) {
                        ensureCurrent(expected)
                        mutableSession.value.phase
                    }
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
                        // Tras renovación: sync push opcional con access nuevo (fuera del mutex).
                        pushSync = synchronized(localLock) {
                            if (mutableSession.value.phase == SessionPhase.AUTHENTICATED)
                                vault?.tokens?.let { it.accessToken to load().installation }
                            else null
                        }
                    } else if (response.isSuccessful) {
                        val body = response.body()
                        check(body?.vigente == true && body.idSesion == tokens.idSesion &&
                            Instant.parse(body.sesionExpiraUtc) == Instant.parse(tokens.sesionExpiraUtc))
                        val alcance = body.alcance ?: tokens.alcance
                        if (alcance == "MFA_CHALLENGE" || alcance == "MFA_REENROLL") {
                            val step = when {
                                alcance == "MFA_REENROLL" -> MfaStep.RECONFIGURE
                                mutableSession.value.mfa?.step != null -> mutableSession.value.mfa!!.step
                                else -> MfaStep.VERIFY
                            }
                            publish(expected, SessionState(
                                SessionPhase.MFA_FLOW,
                                alcance = alcance,
                                hasLocalSession = true,
                                mfa = mutableSession.value.mfa?.copy(step = step)
                                    ?: MfaUiState(step, desafioId = tokens.desafioId)
                            ))
                        } else {
                            publish(expected, SessionState(SessionPhase.AUTHENTICATED, alcance = alcance))
                            if (previousPhase != SessionPhase.AUTHENTICATED && alcance != "ADMIN") {
                                pushSync = tokens.accessToken to load().installation
                            }
                        }
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
            notifySessionReadyOutsideMutex(pushSync)
        }
    }

    /** Push/FCM es opcional: fallos o demoras no retienen el mutex ni invalidan la sesión. */
    private suspend fun notifySessionReadyOutsideMutex(pushSync: Pair<String, String>?) {
        val ready = pushSync ?: return
        runCatching { onSessionReady(ready.first, ready.second) }
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
        var installation = ""
        val closedVersion = synchronized(localLock) {
            val existing = load()
            if (existing.tokens == null && !cleanupBlocked) return@withContext
            generation++
            cleanupBlocked = true // Invalida inmediatamente las operaciones iniciadas antes del cierre.
            try {
                proof = existing.tokens?.accessToken
                installation = existing.installation
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
                runCatching { onSessionClosing(captured, installation) }
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

    suspend fun loadAdminHub(context: AuthenticatedContext): AdminHubResponse = withContext(Dispatchers.IO) {
        val bearer = authorization(context)
        val response = api.adminHub(bearer)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw AuthProblem(
                if (response.code() == 403) "No tienes acceso de administrador."
                else "No se pudo cargar Administración."
            )
        }
        response.body() ?: throw AuthProblem("No se pudo cargar Administración.")
    }

    private fun TokenResponse?.toStored(): StoredTokens {
        check(this?.estado == "AUTENTICADO")
        return StoredTokens(requireNotNull(this?.idSesion), requireNotNull(this?.accessToken),
            requireNotNull(this?.accessTokenExpiraUtc), requireNotNull(this?.refreshToken),
            requireNotNull(this?.sesionExpiraUtc),
            alcance = this?.alcance ?: "USUARIO",
            desafioId = this?.desafioId).validate()
    }

    private fun TokenResponse?.toStoredChallenge(forceAlcance: String? = null): StoredTokens {
        val estado = this?.estado
        check(estado == "MFA_REQUERIDO" || estado == "MFA_ENROLL_REQUERIDO" ||
            estado == "MFA_REENROLL_REQUERIDO" || forceAlcance != null)
        return StoredTokens(requireNotNull(this?.idSesion), requireNotNull(this?.accessToken),
            requireNotNull(this?.accessTokenExpiraUtc), requireNotNull(this?.refreshToken),
            requireNotNull(this?.sesionExpiraUtc),
            alcance = forceAlcance ?: this?.alcance ?: "MFA_CHALLENGE",
            desafioId = this?.desafioId).validate()
    }
}
