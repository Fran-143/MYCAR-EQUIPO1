package com.mycar.app.features.vehiculos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.vehiculos.data.CatalogosRegistroDto
import com.mycar.app.features.vehiculos.domain.EditarVehiculoRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.FormularioRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.ValidadorFormularioEdicion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseEdicion { CARGANDO, FORMULARIO, GUARDANDO, ERROR, SESION_REQUERIDA, NO_EDITABLE }

data class EstadoEdicionVehiculo(
    val fase: FaseEdicion = FaseEdicion.CARGANDO,
    val formulario: FormularioRegistroVehiculo = FormularioRegistroVehiculo(),
    val original: FormularioRegistroVehiculo = FormularioRegistroVehiculo(),
    val catalogos: CatalogosRegistroDto? = null,
    val tieneTive: Boolean = false,
    val placa: String = "",
    val kilometrajeActual: String = "",
    val ocupado: Boolean = false,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val mostrarAvisoSinCambios: Boolean = false
) {
    val hayCambios: Boolean get() = formulario != original
}

class EditarVehiculoViewModel(
    private val idVehiculo: Long,
    private val repository: EditarVehiculoRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEdicionVehiculo())
    val estado = _estado.asStateFlow()
    private var trabajo: Job? = null
    private var revision = 0L

    fun cargar() {
        val actual = ++revision
        trabajo?.cancel()
        _estado.value = EstadoEdicionVehiculo(fase = FaseEdicion.CARGANDO, ocupado = true)
        trabajo = viewModelScope.launch {
            try {
                val catalogos = repository.catalogos()
                val detalle = repository.consultar(idVehiculo)
                if (actual != revision) return@launch
                if (detalle.estadoVehiculo != "ACTIVO") {
                    _estado.value = _estado.value.copy(
                        fase = FaseEdicion.NO_EDITABLE,
                        ocupado = false,
                        mensaje = "Solo puedes editar vehículos activos."
                    )
                    return@launch
                }
                val formulario = ValidadorFormularioEdicion.desdeDetalle(detalle, catalogos)
                if (formulario.usoVehiculoCodigo.isBlank() || formulario.idTipoMotor.isBlank()) {
                    _estado.value = _estado.value.copy(
                        fase = FaseEdicion.ERROR,
                        ocupado = false,
                        mensaje = "No se pudieron precargar los datos para editar. Inténtalo nuevamente."
                    )
                    return@launch
                }
                _estado.value = EstadoEdicionVehiculo(
                    fase = FaseEdicion.FORMULARIO,
                    formulario = formulario,
                    original = formulario,
                    catalogos = catalogos,
                    tieneTive = detalle.tieneTive,
                    placa = detalle.placa,
                    kilometrajeActual = detalle.kilometrajeActual?.toString().orEmpty(),
                    ocupado = false,
                    mensaje = null
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (error: Exception) {
                if (actual != revision) return@launch
                val sesion = error is FalloRegistroVehiculo && error.estadoHttp == 401
                val noDisp = error is FalloRegistroVehiculo &&
                    (error.codigo == "VEHICULO_NO_ENCONTRADO" || error.estadoHttp == 404)
                _estado.value = _estado.value.copy(
                    fase = when {
                        sesion -> FaseEdicion.SESION_REQUERIDA
                        noDisp -> FaseEdicion.NO_EDITABLE
                        else -> FaseEdicion.ERROR
                    },
                    ocupado = false,
                    mensaje = when {
                        sesion -> "Inicia sesión para editar el vehículo."
                        noDisp -> "No se encontró el vehículo en tu cuenta."
                        else -> "No se pudo cargar la edición. Inténtalo nuevamente."
                    }
                )
            }
        }
    }

    fun cambiar(campo: String, valor: String) {
        if (_estado.value.ocupado || campo == "placa" || campo == "kilometrajeInicial") return
        if (_estado.value.tieneTive && campo in setOf("numeroVin", "numeroMotor", "numeroSerie")) return
        val form = _estado.value.formulario.cambiar(campo, valor, _estado.value.catalogos)
        val limpiar = _estado.value.formulario.camposAfectados(campo)
        _estado.value = _estado.value.copy(
            formulario = form,
            errores = _estado.value.errores - limpiar,
            mensaje = null,
            mostrarAvisoSinCambios = false
        )
    }

    fun cerrarAvisoSinCambios() {
        _estado.value = _estado.value.copy(mostrarAvisoSinCambios = false, mensaje = null)
    }

    fun guardar(onExito: () -> Unit) {
        val actual = _estado.value
        val catalogos = actual.catalogos ?: return
        if (actual.ocupado || actual.fase != FaseEdicion.FORMULARIO) return
        if (!actual.hayCambios) {
            _estado.value = actual.copy(mostrarAvisoSinCambios = true, mensaje = null)
            return
        }
        val errores = ValidadorFormularioEdicion.validar(actual.formulario, catalogos, actual.tieneTive)
        if (errores.isNotEmpty()) {
            _estado.value = actual.copy(errores = errores, mensaje = "Revisa los campos señalados.")
            return
        }
        val solicitud = ValidadorFormularioEdicion.solicitud(actual.formulario, catalogos, actual.tieneTive)
        val rev = ++revision
        trabajo?.cancel()
        _estado.value = actual.copy(fase = FaseEdicion.GUARDANDO, ocupado = true, mensaje = null, errores = emptyMap())
        trabajo = viewModelScope.launch {
            try {
                repository.actualizar(idVehiculo, solicitud)
                if (rev == revision) onExito()
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (error: Exception) {
                if (rev != revision) return@launch
                val fallo = error as? FalloRegistroVehiculo
                val sesion = fallo?.estadoHttp == 401
                val mensaje = when (fallo?.codigo) {
                    "VEHICULO_INACTIVO" -> "Solo puedes editar vehículos activos."
                    "VIN_DUPLICADO" -> "El VIN ya está registrado."
                    "VEHICULO_DUPLICADO" -> "Ya tienes un vehículo registrado con exactamente los mismos datos."
                    "SIN_CAMBIOS" -> MENSAJE_SIN_CAMBIOS
                    "IDENTIFICADORES_BLOQUEADOS_TIVE" ->
                        "VIN, código de motor y número de serie están protegidos por la TIVE."
                    "CAMPO_PROTEGIDO" -> "No puedes modificar este campo desde aquí."
                    "DATOS_INVALIDOS" -> "Revisa los campos señalados."
                    else -> fallo?.mensajeServidor
                        ?: if (sesion) "Inicia sesión para continuar."
                        else "No se pudieron guardar los cambios. Inténtalo nuevamente."
                }
                val sinCambios = fallo?.codigo == "SIN_CAMBIOS"
                _estado.value = _estado.value.copy(
                    fase = if (sesion) FaseEdicion.SESION_REQUERIDA else FaseEdicion.FORMULARIO,
                    ocupado = false,
                    errores = if (sinCambios) emptyMap() else fallo?.campos.orEmpty().associateWith { campo ->
                        when {
                            fallo?.codigo == "VEHICULO_DUPLICADO" ->
                                "Ya registraste un vehículo con estos mismos datos."
                            fallo?.codigo == "VIN_DUPLICADO" && campo == "numeroVin" ->
                                "El VIN ya está registrado."
                            else -> fallo?.mensajeServidor ?: "Revisa este campo."
                        }
                    },
                    mensaje = if (sinCambios) null else mensaje,
                    mostrarAvisoSinCambios = sinCambios
                )
            }
        }
    }

    companion object {
        const val MENSAJE_SIN_CAMBIOS =
            "No puedes guardar la misma información. Modifica algún campo o vuelve sin guardar."

        fun factory(idVehiculo: Long, repository: EditarVehiculoRepository) = viewModelFactory {
            initializer { EditarVehiculoViewModel(idVehiculo, repository) }
        }
    }
}
