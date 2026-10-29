package com.mycar.app.features.autenticacion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.autenticacion.data.AuthProblem
import com.mycar.app.features.autenticacion.data.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val busy: Boolean = false, val error: String? = null,
    val emailError: String? = null, val passwordError: String? = null
)

data class LogoutUiState(val confirmation: Boolean = false, val busy: Boolean = false)

class AuthViewModel(
    private val repository: AuthRepository,
    private val logoutDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val mutableLogout = MutableStateFlow(LogoutUiState())
    val logout = mutableLogout.asStateFlow()
    private var logoutAttempt = 0L

    fun requestLogout() {
        if (!mutableLogout.value.busy && session.value.canRequestLogout)
            mutableLogout.value = LogoutUiState(confirmation = true)
    }
    fun cancelLogout() {
        if (!mutableLogout.value.busy) mutableLogout.value = LogoutUiState()
    }
    fun confirmedLogout() {
        if (!mutableLogout.value.confirmation || mutableLogout.value.busy) return
        val attempt = ++logoutAttempt
        mutableLogout.value = LogoutUiState(busy = true)
        viewModelScope.launch(logoutDispatcher) {
            try { repository.logout() }
            finally { if (attempt == logoutAttempt) mutableLogout.value = LogoutUiState() }
        }
    }
    fun onLocalSessionCleared() {
        ++logoutAttempt
        mutableLogin.value = LoginState()
        mutableLogout.value = LogoutUiState()
    }

    val session = repository.session
    private val mutableLogin = MutableStateFlow(LoginState())
    val login = mutableLogin.asStateFlow()

    fun signIn(email: String, password: String) {
        if (mutableLogin.value.busy) return
        val emailError = when {
            email.isBlank() -> "Ingresa tu correo electrónico."
            email.trim().length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                "Ingresa un correo con formato válido."
            else -> null
        }
        val passwordError = if (password.isEmpty()) "Ingresa tu contraseña." else null
        mutableLogin.value = LoginState(emailError = emailError, passwordError = passwordError)
        if (emailError != null || passwordError != null) return
        // Password sólo se mantiene en memoria durante el intento, nunca SavedStateHandle.
        mutableLogin.value = LoginState(busy = true)
        val contextVersion = session.value.contextVersion
        viewModelScope.launch {
            try {
                repository.login(email, password)
                if (contextVersion == session.value.contextVersion) mutableLogin.value = LoginState()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: AuthProblem) {
                if (contextVersion == session.value.contextVersion) mutableLogin.value = LoginState(error = failure.message)
            }
        }
    }

    fun clearErrors() { if (!mutableLogin.value.busy) mutableLogin.value = LoginState() }
    suspend fun maintainSession() = repository.maintainSession()
    fun retry() { viewModelScope.launch { repository.validateSession() } }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
    }
}
