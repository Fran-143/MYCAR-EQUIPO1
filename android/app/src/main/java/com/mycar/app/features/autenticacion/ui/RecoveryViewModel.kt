package com.mycar.app.features.autenticacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.autenticacion.data.RecoveryRepository
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecoveryStep { EMAIL, CODE, PASSWORD, SUCCESS }

data class RecoveryState(
    val step: RecoveryStep = RecoveryStep.EMAIL,
    val correo: String = "",
    val codigo: String = "",
    val nueva: String = "",
    val confirmacion: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val codigoDesarrollo: String? = null,
    val errors: Map<String, String> = emptyMap(),
    /** Segundos restantes antes de poder reenviar (anti-spam SMTP / RF03). */
    val reenvioEnSegundos: Int = 0
) {
    val puedeReenviar: Boolean get() = reenvioEnSegundos <= 0 && !busy
}

class RecoveryViewModel(private val repository: RecoveryRepository) : ViewModel() {
    private val mutable = MutableStateFlow(RecoveryState())
    val state = mutable.asStateFlow()
    private var cooldownJob: Job? = null

    fun reset() {
        cooldownJob?.cancel()
        mutable.value = RecoveryState()
    }

    fun editEmail(value: String) {
        mutable.value = mutable.value.copy(
            correo = RegistrationValidator.limitCodePoints(value, RegistrationValidator.MAX_CORREO),
            errors = emptyMap(), message = null)
    }
    fun editCode(value: String) {
        mutable.value = mutable.value.copy(
            codigo = value.filter { it.isDigit() }.take(RegistrationValidator.MAX_CODIGO_OTP),
            errors = emptyMap(), message = null)
    }
    fun editPassword(nueva: String? = null, confirmacion: String? = null) {
        mutable.value = mutable.value.copy(
            nueva = nueva?.let { RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_CONTRASENA) }
                ?: mutable.value.nueva,
            confirmacion = confirmacion?.let {
                RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_CONTRASENA)
            } ?: mutable.value.confirmacion,
            errors = emptyMap(), message = null
        )
    }

    fun emitir() {
        if (mutable.value.busy) return
        if (mutable.value.step == RecoveryStep.CODE && !mutable.value.puedeReenviar) return
        val correo = mutable.value.correo.trim().lowercase()
        if (correo.isBlank() || correo.length > 254
            || !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mutable.value = mutable.value.copy(errors = mapOf("correo" to "Ingresa un correo válido."))
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = null, errors = emptyMap())
            try {
                val emitido = repository.emitir(correo)
                mutable.value = mutable.value.copy(
                    busy = false,
                    step = RecoveryStep.CODE,
                    message = emitido.mensaje,
                    codigoDesarrollo = emitido.codigoDesarrollo
                )
                startCooldown(60)
            } catch (e: RecoveryRepository.RecoveryProblem) {
                val wait = cooldownSecondsFromMessage(e.message) ?: cooldownSecondsFromMessage(e.campos["correo"])
                if (wait != null) startCooldown(wait)
                mutable.value = mutable.value.copy(busy = false, errors = e.campos, message = e.message)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "No se pudo enviar el código.")
            }
        }
    }

    fun verificar() {
        if (mutable.value.busy) return
        val s = mutable.value
        if (s.codigo.length != 6) {
            mutable.value = s.copy(errors = mapOf("codigo" to "Ingresa el código de 6 dígitos."))
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = null, errors = emptyMap())
            try {
                repository.verificar(s.correo, s.codigo)
                mutable.value = mutable.value.copy(busy = false, step = RecoveryStep.PASSWORD)
            } catch (e: RecoveryRepository.RecoveryProblem) {
                mutable.value = mutable.value.copy(busy = false, errors = e.campos, message = e.message)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "No se pudo verificar el código.")
            }
        }
    }

    fun restablecer() {
        if (mutable.value.busy) return
        val s = mutable.value
        val locales = linkedMapOf<String, String>()
        if (RegistrationValidator.passwordRequirements(s.nueva).any { !it.second }) {
            locales["nuevaContrasena"] = RegistrationValidator.messages.getValue("contrasena")
        }
        if (s.nueva != s.confirmacion) {
            locales["confirmacionContrasena"] = RegistrationValidator.messages.getValue("confirmacionContrasena")
        }
        if (locales.isNotEmpty()) {
            mutable.value = s.copy(errors = locales)
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = null, errors = emptyMap())
            try {
                repository.restablecer(s.correo, s.codigo, s.nueva, s.confirmacion)
                cooldownJob?.cancel()
                mutable.value = mutable.value.copy(
                    busy = false, step = RecoveryStep.SUCCESS,
                    nueva = "", confirmacion = "", codigo = "", codigoDesarrollo = null, reenvioEnSegundos = 0
                )
            } catch (e: RecoveryRepository.RecoveryProblem) {
                mutable.value = mutable.value.copy(busy = false, errors = e.campos, message = e.message)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "No se pudo actualizar la contraseña.")
            }
        }
    }

    fun back() {
        val s = mutable.value
        if (s.busy) return
        mutable.value = when (s.step) {
            RecoveryStep.EMAIL -> s
            RecoveryStep.CODE -> {
                cooldownJob?.cancel()
                s.copy(step = RecoveryStep.EMAIL, codigo = "", codigoDesarrollo = null, reenvioEnSegundos = 0)
            }
            RecoveryStep.PASSWORD -> s.copy(step = RecoveryStep.CODE, nueva = "", confirmacion = "")
            RecoveryStep.SUCCESS -> RecoveryState()
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        val total = seconds.coerceAtLeast(1)
        mutable.value = mutable.value.copy(reenvioEnSegundos = total)
        cooldownJob = viewModelScope.launch {
            var left = total
            while (left > 0) {
                delay(1_000)
                left--
                mutable.value = mutable.value.copy(reenvioEnSegundos = left)
            }
        }
    }

    private fun cooldownSecondsFromMessage(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        if (message.contains("minuto", ignoreCase = true) || message.contains("60")) return 60
        return null
    }

    class Factory(private val repository: RecoveryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecoveryViewModel::class.java))
            return RecoveryViewModel(repository) as T
        }
    }
}
