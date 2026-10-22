package com.mycar.app.features.inicio.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.BuildConfig
import com.mycar.app.core.network.ApiClient
import com.mycar.app.features.inicio.data.RemoteHealthRepository
import com.mycar.app.features.inicio.domain.HealthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val message: String = "Pulsa el botón para comprobar la conexión."
)

class HomeViewModel(
    private val repository: HealthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun checkConnection() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.value = HomeUiState(
            isLoading = true,
            message = "Comprobando conexión..."
        )

        viewModelScope.launch {
            try {
                val available = repository.isServerAvailable()

                _uiState.value = HomeUiState(
                    message = if (available) {
                        "Servidor disponible"
                    } else {
                        "El servidor respondió, pero no está disponible."
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(
                        "MyCarConexion",
                        "Error consultando ${BuildConfig.API_BASE_URL}actuator/health",
                        error
                    )
                }

                _uiState.value = HomeUiState(
                    message = "No se pudo comprobar la conexión. Inténtalo nuevamente."
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = RemoteHealthRepository(
                        api = ApiClient.healthApi
                    )
                )
            }
        }
    }
}