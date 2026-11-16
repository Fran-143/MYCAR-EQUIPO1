package com.mycar.app.features.soat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.soat.data.SoatConsultaResponse
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseConsultaSoat { CARGANDO, LISTO, ERROR, SESION }

data class EstadoConsultaSoat(
    val idVehiculo: Long,
    val fase: FaseConsultaSoat = FaseConsultaSoat.CARGANDO,
    val consulta: SoatConsultaResponse? = null,
    val mensaje: String? = null,
    val docBytes: ByteArray? = null,
    val docEsPdf: Boolean = false,
    val docCargando: Boolean = false,
    val docError: String? = null,
    val docRevelado: Boolean = false
)

class ConsultarSoatViewModel(
    private val idVehiculo: Long,
    private val repository: SoatRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoConsultaSoat(idVehiculo))
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.value = _estado.value.copy(
            fase = FaseConsultaSoat.CARGANDO,
            mensaje = null,
            consulta = null,
            docBytes = null,
            docError = null,
            docRevelado = false
        )
        viewModelScope.launch {
            try {
                val consulta = repository.consultar(idVehiculo)
                _estado.value = _estado.value.copy(fase = FaseConsultaSoat.LISTO, consulta = consulta)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejarError(e)
            }
        }
    }

    fun cargarDocumento(soat: SoatDto) {
        if (!soat.conDocumento) return
        _estado.value = _estado.value.copy(
            docRevelado = true,
            docCargando = true,
            docError = null,
            docBytes = null
        )
        viewModelScope.launch {
            try {
                val bytes = repository.descargarDocumento(idVehiculo, soat.idSoat)
                val esPdf = bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte()
                _estado.value = _estado.value.copy(docCargando = false, docBytes = bytes, docEsPdf = esPdf)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    docCargando = false,
                    docError = (e as? FalloRegistroVehiculo)?.mensajeServidor
                        ?: "No se pudo cargar el documento."
                )
            }
        }
    }

    fun ocultarDocumento() {
        _estado.value = _estado.value.copy(
            docRevelado = false,
            docBytes = null,
            docError = null,
            docCargando = false
        )
    }

    override fun onCleared() {
        ocultarDocumento()
        super.onCleared()
    }

    private fun manejarError(e: Exception) {
        val fallo = e as? FalloRegistroVehiculo
        val sesion = fallo?.estadoHttp == 401
        _estado.value = _estado.value.copy(
            fase = if (sesion) FaseConsultaSoat.SESION else FaseConsultaSoat.ERROR,
            mensaje = when {
                sesion -> "Inicia sesión para consultar el SOAT."
                fallo?.mensajeServidor != null -> fallo.mensajeServidor
                else -> "No se pudo consultar el SOAT."
            }
        )
    }

    companion object {
        fun factory(idVehiculo: Long, repository: SoatRepository) = viewModelFactory {
            initializer { ConsultarSoatViewModel(idVehiculo, repository) }
        }
    }
}
