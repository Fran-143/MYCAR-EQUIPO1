package com.mycar.app.features.autenticacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.autenticacion.domain.*
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegistrationState(
    val form: RegistrationForm = RegistrationForm(), val busy: Boolean = false,
    val success: Boolean = false, val errors: Map<String, String> = emptyMap(),
    val message: String? = null, val privacyStale: Boolean = false,
    val policyVisible: Boolean = false, val policyConsulted: Boolean = false
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
    private val mutable = MutableStateFlow(RegistrationState())
    val state = mutable.asStateFlow()

    fun edit(transform: (RegistrationForm) -> RegistrationForm) {
        if (inFlight.get() || mutable.value.success) return
        mutable.value = mutable.value.copy(form = transform(mutable.value.form), errors = emptyMap(), message = null)
    }
    fun reset() {
        if (!inFlight.get()) mutable.value = RegistrationState(privacyStale = mutable.value.privacyStale)
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
        if (current.success) { inFlight.set(false); return }
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
        val request = RegistrationRequest(
            RegistrationValidator.normalizedName(form.nombre), RegistrationValidator.normalizedName(form.apellido),
            RegistrationValidator.normalizedEmail(form.correo),
            requireNotNull(RegistrationValidator.birthDate(form.fechaNacimiento)).toString(),
            form.contrasena, form.confirmacionContrasena, PrivacyConsent(true, policy.version)
        )
        mutable.value = current.copy(busy = true, errors = emptyMap(), message = null)
        viewModelScope.launch(dispatcher) {
            try {
                service.register(request)
                // La confirmación no conserva datos personales ni credenciales, ni inicia sesión.
                mutable.value = RegistrationState(success = true)
            } catch (cancelled: CancellationException) {
                mutable.value = mutable.value.copy(busy = false)
                throw cancelled
            } catch (failure: RegistrationFailure) {
                val stale = failure.kind == RegistrationFailureKind.VERSION_MISMATCH
                mutable.value = mutable.value.copy(busy = false, message = failure.message,
                    errors = failure.fields, privacyStale = stale || mutable.value.privacyStale,
                    form = if (stale) form.copy(privacidadAceptada = false) else form)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false,
                    message = "No se pudo confirmar el registro. No se ha iniciado sesión.")
            } finally { inFlight.set(false) }
        }
    }

    class Factory(private val service: RegistrationService, private val policy: PrivacyPolicyConfiguration) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RegistrationViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return RegistrationViewModel(service, policy) as T
        }
    }
}
