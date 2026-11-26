package com.mycar.app.features.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.admin.domain.AdminVinRepository
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaAdminVin { LISTA, DETALLE, RESOLVER, CONFIRMACION }

data class EstadoAdminVin(
    val pantalla: PantallaAdminVin = PantallaAdminVin.LISTA,
    val cargando: Boolean = true,
    val ocupado: Boolean = false,
    val lista: List<SolicitudResumenDto> = emptyList(),
    val filtroEstado: String? = null,
    val busqueda: String = "",
    val detalle: SolicitudDetalleDto? = null,
    val mensaje: String? = null,
    val motivoRechazo: String = "",
    val aprobarSeleccionado: Boolean = true,
    val evidenciaBytes: ByteArray? = null,
    val evidenciaCargando: Boolean = false,
    val evidenciaError: String? = null,
    val evidenciaRevelada: Boolean = false,
    val vinAnteriorConfirm: String? = null
)

class AdminVinViewModel(private val repository: AdminVinRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoAdminVin())
    val estado = _estado.asStateFlow()

    init { cargarLista() }

    fun cargarLista() {
        _estado.value = _estado.value.copy(cargando = true, mensaje = null, pantalla = PantallaAdminVin.LISTA)
        viewModelScope.launch {
            try {
                val q = _estado.value.busqueda.takeIf { it.isNotBlank() }
                val lista = repository.listar(_estado.value.filtroEstado, q)
                _estado.value = _estado.value.copy(cargando = false, lista = lista)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    fun setFiltro(estado: String?) {
        _estado.value = _estado.value.copy(filtroEstado = estado)
        cargarLista()
    }

    fun setBusqueda(q: String) {
        _estado.value = _estado.value.copy(busqueda = q)
    }

    fun abrirDetalle(codigo: String) {
        _estado.value = _estado.value.copy(
            cargando = true,
            evidenciaBytes = null,
            evidenciaRevelada = false,
            evidenciaError = null,
            mensaje = null
        )
        viewModelScope.launch {
            try {
                val d = repository.detalle(codigo)
                _estado.value = _estado.value.copy(
                    cargando = false,
                    pantalla = PantallaAdminVin.DETALLE,
                    detalle = d
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    fun volverLista() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminVin.LISTA,
            detalle = null,
            motivoRechazo = "",
            evidenciaBytes = null,
            evidenciaRevelada = false
        )
        cargarLista()
    }

    fun revelarEvidencia() {
        val codigo = _estado.value.detalle?.codigo ?: return
        if (!_estado.value.detalle!!.tieneEvidencia) return
        if (_estado.value.evidenciaBytes != null) {
            _estado.value = _estado.value.copy(evidenciaRevelada = true)
            return
        }
        _estado.value = _estado.value.copy(evidenciaCargando = true, evidenciaError = null, evidenciaRevelada = true)
        viewModelScope.launch {
            try {
                val bytes = repository.descargarEvidencia(codigo)
                _estado.value = _estado.value.copy(evidenciaCargando = false, evidenciaBytes = bytes)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    evidenciaCargando = false,
                    evidenciaError = (e as? FalloRegistroVehiculo)?.mensajeServidor
                        ?: "No se pudo cargar el documento. Inténtalo nuevamente"
                )
            }
        }
    }

    fun ocultarEvidencia() {
        _estado.value = _estado.value.copy(evidenciaRevelada = false)
    }

    fun iniciarRevision() = accion { repository.iniciarRevision(it) }

    fun abrirResolver(aprobar: Boolean = true) {
        _estado.value = _estado.value.copy(
            pantalla = PantallaAdminVin.RESOLVER,
            aprobarSeleccionado = aprobar,
            motivoRechazo = "",
            mensaje = null
        )
    }

    fun cancelarResolver() {
        _estado.value = _estado.value.copy(pantalla = PantallaAdminVin.DETALLE, mensaje = null)
    }

    fun setResultado(aprobar: Boolean) {
        _estado.value = _estado.value.copy(aprobarSeleccionado = aprobar, mensaje = null)
    }

    fun confirmarResolucion() {
        if (!_estado.value.aprobarSeleccionado) {
            val motivo = _estado.value.motivoRechazo.trim()
            if (motivo.length < 5) {
                _estado.value = _estado.value.copy(mensaje = "Indica un motivo de rechazo (mínimo 5 caracteres).")
                return
            }
            val vinAnterior = _estado.value.detalle?.vinRegistrado
            accion(irConfirmacion = true, vinAnterior = vinAnterior) {
                repository.rechazar(it, motivo.uppercase(java.util.Locale.forLanguageTag("es-PE")))
            }
        } else {
            val vinAnterior = _estado.value.detalle?.vinRegistrado
            accion(irConfirmacion = true, vinAnterior = vinAnterior) { repository.aprobar(it) }
        }
    }

    fun editarMotivoRechazo(v: String) {
        _estado.value = _estado.value.copy(
            motivoRechazo = v.uppercase(java.util.Locale.forLanguageTag("es-PE")).take(250),
            mensaje = null
        )
    }

    private fun accion(
        irConfirmacion: Boolean = false,
        vinAnterior: String? = null,
        bloque: suspend (String) -> Unit
    ) {
        val codigo = _estado.value.detalle?.codigo ?: return
        _estado.value = _estado.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                bloque(codigo)
                val d = repository.detalle(codigo)
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    detalle = d,
                    pantalla = if (irConfirmacion) PantallaAdminVin.CONFIRMACION else PantallaAdminVin.DETALLE,
                    vinAnteriorConfirm = vinAnterior
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    mensaje = (e as? FalloRegistroVehiculo)?.mensajeServidor ?: e.message
                )
            }
        }
    }

    fun etiqueta(estado: String) = SoporteFormulario.etiquetaEstado(estado)

    companion object {
        fun factory(repository: AdminVinRepository) = viewModelFactory {
            initializer { AdminVinViewModel(repository) }
        }
    }
}
