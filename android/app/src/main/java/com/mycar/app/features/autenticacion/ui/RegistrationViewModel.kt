package com.mycar.app.features.autenticacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.domain.*
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RegistrationStep { FORM, CODE, DONE }

data class RegistrationState(
    val form: RegistrationForm = RegistrationForm(),
    val step: RegistrationStep = RegistrationStep.FORM,
    val busy: Boolean = false,
    val success: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val privacyStale: Boolean = false,
    val policyVisible: Boolean = false,
    val policyConsulted: Boolean = false,
    val codeInput: String = "",
    val codigoDesarrollo: String? = null
) {
    override fun toString() = "RegistrationState[REDACTED]"
}

/** Formulario en memoria únicamente: sin SavedStateHandle, contraseñas en rutas ni almacenamiento. */
class RegistrationViewModel(
    private val service: RegistrationService,
    val policy: PrivacyPolicyConfiguration,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val inFlight = AtomicBoolean(false)
    private val flowGeneration = AtomicInteger(0)
    private val mutable = MutableStateFlow(RegistrationState())
    val state = mutable.asStateFlow()
    private var pendingRequest: RegistrationRequest? = null
    private var session: VerificationSession? = null

    fun edit(transform: (RegistrationForm) -> RegistrationForm) {
        if (inFlight.get() || mutable.value.success || mutable.value.step == RegistrationStep.CODE) return
        mutable.value = mutable.value.copy(form = transform(mutable.value.form), errors = emptyMap(), message = null)
    }
    fun editCode(value: String) {
        if (inFlight.get() || mutable.value.step != RegistrationStep.CODE) return
        mutable.value = mutable.value.copy(
            codeInput = value.filter { it.isDigit() }.take(6),
            message = null
        )
    }
    fun reset() {
        flowGeneration.incrementAndGet()
        inFlight.set(false)
        pendingRequest = null
        session = null
        mutable.value = RegistrationState(privacyStale = mutable.value.privacyStale)
    }
    fun backFromCode() {
        // Cancela trabajo en curso y vuelve al formulario; conserva la sesión OTP
        // para que Continuar pueda reabrir la pantalla del código sin reemitir.
        flowGeneration.incrementAndGet()
        inFlight.set(false)
        mutable.value = mutable.value.copy(
            step = RegistrationStep.FORM,
            codeInput = "",
            message = null,
            busy = false
        )
    }
    fun openLocalPolicy() {
        if (!inFlight.get() && !mutable.value.success && !mutable.value.privacyStale &&
            policy.available && policy.localDocument != null)
            mutable.value = mutable.value.copy(policyVisible = true)
    }
    fun closeLocalPolicy() { mutable.value = mutable.value.copy(policyVisible = false) }
    fun localPolicyLoaded() {
        if (mutable.value.policyVisible && policy.available && policy.localDocument != null)
            mutable.value = mutable.value.copy(policyConsulted = true)
    }
    fun policyOpenFailed() {
        mutable.value = mutable.value.copy(message = "No se pudo abrir el documento aprobado. No se ha aceptado la política.")
    }

    fun submit() {
        if (!inFlight.compareAndSet(false, true)) return
        val current = mutable.value
        if (current.success || current.step == RegistrationStep.CODE) { inFlight.set(false); return }
        if (policy.available && policy.localDocument != null && !current.policyConsulted) {
            mutable.value = current.copy(message = "Consulta la Política de Privacidad antes de aceptarla.")
            inFlight.set(false)
            return
        }
        val errors = RegistrationValidator.validate(current.form, policy, clock)
        if (!policy.available || current.privacyStale || errors.isNotEmpty()) {
            mutable.value = current.copy(errors = errors, message = when {
                !policy.available -> RegistrationFailure(RegistrationFailureKind.PRIVACY_PENDING).message
                current.privacyStale -> RegistrationFailure(RegistrationFailureKind.VERSION_MISMATCH).message
                else -> "Revisa los campos indicados."
            })
            inFlight.set(false)
            return
        }
        val form = current.form
        val request = toRequest(form)
        val existing = session
        val previous = pendingRequest
        if (existing != null && previous != null && sameVerificationTarget(previous, request)) {
            mutable.value = current.copy(
                busy = false,
                errors = emptyMap(),
                message = null,
                step = RegistrationStep.CODE,
                codigoDesarrollo = existing.codigoDesarrollo,
                codeInput = ""
            )
            inFlight.set(false)
            return
        }
        session = null
        pendingRequest = null
        val gen = flowGeneration.get()
        mutable.value = current.copy(busy = true, errors = emptyMap(), message = null)
        viewModelScope.launch(dispatcher) {
            try {
                val started = service.startEmailVerification(request)
                if (gen != flowGeneration.get()) return@launch
                pendingRequest = request
                session = started
                mutable.value = mutable.value.copy(
                    busy = false,
                    step = RegistrationStep.CODE,
                    codigoDesarrollo = started.codigoDesarrollo,
                    codeInput = "",
                    message = null
                )
            } catch (cancelled: CancellationException) {
                if (gen == flowGeneration.get()) mutable.value = mutable.value.copy(busy = false)
                throw cancelled
            } catch (failure: RegistrationFailure) {
                if (gen == flowGeneration.get()) applyFailure(failure, form)
            } catch (_: Exception) {
                if (gen == flowGeneration.get()) {
                    mutable.value = mutable.value.copy(busy = false,
                        message = "No se pudo enviar el código. Inténtalo de nuevo.")
                }
            } finally {
                if (gen == flowGeneration.get()) inFlight.set(false)
            }
        }
    }

    private fun sameVerificationTarget(previous: RegistrationRequest, next: RegistrationRequest) =
        previous.correo.equals(next.correo, ignoreCase = true) &&
            previous.nombre == next.nombre &&
            previous.apellido == next.apellido &&
            previous.fechaNacimiento == next.fechaNacimiento &&
            previous.contrasena == next.contrasena &&
            previous.confirmacionContrasena == next.confirmacionContrasena &&
            previous.consentimientoPrivacidad.version == next.consentimientoPrivacidad.version &&
            previous.consentimientoPrivacidad.aceptado == next.consentimientoPrivacidad.aceptado

    fun confirmCode() {
        if (!inFlight.compareAndSet(false, true)) return
        val current = mutable.value
        val req = pendingRequest
        val ver = session
        if (current.step != RegistrationStep.CODE || req == null || ver == null) {
            inFlight.set(false)
            return
        }
        if (current.codeInput.length != 6) {
            mutable.value = current.copy(message = "Ingresa el código de 6 dígitos.")
            inFlight.set(false)
            return
        }
        val gen = flowGeneration.get()
        mutable.value = current.copy(busy = true, message = null)
        viewModelScope.launch(dispatcher) {
            try {
                service.completeEmailVerification(ver, current.codeInput, req)
                if (gen != flowGeneration.get()) return@launch
                pendingRequest = null
                session = null
                mutable.value = RegistrationState(success = true, step = RegistrationStep.DONE)
            } catch (cancelled: CancellationException) {
                if (gen == flowGeneration.get()) mutable.value = mutable.value.copy(busy = false)
                throw cancelled
            } catch (failure: RegistrationFailure) {
                if (gen == flowGeneration.get()) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        message = when (failure.kind) {
                            RegistrationFailureKind.VALIDATION -> "Código incorrecto o inválido."
                            RegistrationFailureKind.EMAIL_UNAVAILABLE -> failure.message
                            else -> failure.message
                        }
                    )
                }
            } catch (_: Exception) {
                if (gen == flowGeneration.get()) {
                    mutable.value = mutable.value.copy(busy = false,
                        message = "No se pudo confirmar el código.")
                }
            } finally {
                if (gen == flowGeneration.get()) inFlight.set(false)
            }
        }
    }

    fun resendCode() {
        if (!inFlight.compareAndSet(false, true)) return
        val current = mutable.value
        val req = pendingRequest
        val ver = session
        if (current.step != RegistrationStep.CODE || req == null || ver == null) {
            inFlight.set(false)
            return
        }
        val gen = flowGeneration.get()
        mutable.value = current.copy(busy = true, message = null)
        viewModelScope.launch(dispatcher) {
            try {
                val again = service.resendEmailCode(ver, req)
                if (gen != flowGeneration.get()) return@launch
                session = again
                mutable.value = mutable.value.copy(
                    busy = false,
                    codigoDesarrollo = again.codigoDesarrollo,
                    codeInput = "",
                    message = "Te enviamos un código nuevo."
                )
            } catch (cancelled: CancellationException) {
                if (gen == flowGeneration.get()) mutable.value = mutable.value.copy(busy = false)
                throw cancelled
            } catch (failure: RegistrationFailure) {
                if (gen == flowGeneration.get()) {
                    mutable.value = mutable.value.copy(busy = false, message = failure.message)
                }
            } catch (_: Exception) {
                if (gen == flowGeneration.get()) {
                    mutable.value = mutable.value.copy(busy = false, message = "No se pudo reenviar el código.")
                }
            } finally {
                if (gen == flowGeneration.get()) inFlight.set(false)
            }
        }
    }

    private fun applyFailure(failure: RegistrationFailure, form: RegistrationForm) {
        val stale = failure.kind == RegistrationFailureKind.VERSION_MISMATCH
        mutable.value = mutable.value.copy(
            busy = false,
            message = failure.message,
            errors = failure.fields,
            privacyStale = stale || mutable.value.privacyStale,
            form = if (stale) form.copy(privacidadAceptada = false) else form
        )
    }

    private fun toRequest(form: RegistrationForm) = RegistrationRequest(
        RegistrationValidator.normalizedName(form.nombre),
        RegistrationValidator.normalizedName(form.apellido),
        RegistrationValidator.normalizedEmail(form.correo),
        requireNotNull(RegistrationValidator.birthDate(form.fechaNacimiento)).toString(),
        form.contrasena, form.confirmacionContrasena, PrivacyConsent(true, policy.version)
    )

    class Factory(private val service: RegistrationService, private val policy: PrivacyPolicyConfiguration) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RegistrationViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return RegistrationViewModel(service, policy) as T
        }
    }
}
