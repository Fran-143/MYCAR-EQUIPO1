package com.mycar.app.features.tive.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.tive.data.TiveConsultaResponse
import com.mycar.app.features.tive.data.TiveDto
import com.mycar.app.features.tive.domain.TiveRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseConsultaTive { CARGANDO, LISTO, SIN_REGISTRO, ERROR, SESION }

data class EstadoConsultaTive(
    val idVehiculo: Long,
    val fase: FaseConsultaTive = FaseConsultaTive.CARGANDO,
    val consulta: TiveConsultaResponse? = null,
    val mensaje: String? = null,
    val docBytes: ByteArray? = null,
    val docEsPdf: Boolean = false,
    val docCargando: Boolean = false,
    val docError: String? = null,
    val docRevelado: Boolean = false
)

class ConsultarTiveViewModel(
    private val idVehiculo: Long,
    private val repository: TiveRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoConsultaTive(idVehiculo))
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.value = _estado.value.copy(
            fase = FaseConsultaTive.CARGANDO,
            mensaje = null,
            consulta = null,
            docBytes = null,
            docRevelado = false,
            docError = null
        )
        viewModelScope.launch {
            try {
                val c = repository.consultar(idVehiculo)
                val fase = if (c.tive == null) FaseConsultaTive.SIN_REGISTRO else FaseConsultaTive.LISTO
                _estado.value = _estado.value.copy(fase = fase, consulta = c)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejarError(e)
            }
        }
    }

    fun cargarDocumento(tive: TiveDto) {
        if (!tive.conDocumento) return
        _estado.value = _estado.value.copy(docRevelado = true, docCargando = true, docError = null, docBytes = null)
        viewModelScope.launch {
            try {
                val bytes = repository.descargarDocumento(idVehiculo, tive.idTive)
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
        _estado.value = _estado.value.copy(docRevelado = false, docBytes = null, docError = null, docCargando = false)
    }

    private fun manejarError(e: Exception) {
        val fallo = e as? FalloRegistroVehiculo
        val sesion = fallo?.codigo == "SESION_NO_VALIDA" || fallo?.estadoHttp == 401
        _estado.value = _estado.value.copy(
            fase = if (sesion) FaseConsultaTive.SESION else FaseConsultaTive.ERROR,
            mensaje = fallo?.mensajeServidor ?: e.message ?: "No se pudo consultar la TIVE."
        )
    }

    companion object {
        fun factory(idVehiculo: Long, repository: TiveRepository) = viewModelFactory {
            initializer { ConsultarTiveViewModel(idVehiculo, repository) }
        }
    }
}