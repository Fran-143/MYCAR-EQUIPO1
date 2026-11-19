package com.mycar.app.features.soporte.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.soporte.data.VehiculoElegibleDto
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.soporte.domain.SoporteRepository
import com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import com.mycar.app.features.vehiculos.domain.ValidadorVin
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaSoporte {
    CARGANDO, CENTRO, CORRECCION, FORMULARIO, DETALLE, EXITO, ERROR, SESION
}

enum class PestanaCorreccion { NUEVA, HISTORIAL }

data class EstadoSoporte(
    val pantalla: PantallaSoporte = PantallaSoporte.CARGANDO,
    val pestana: PestanaCorreccion = PestanaCorreccion.NUEVA,
    val vehiculos: List<VehiculoElegibleDto> = emptyList(),
    val solicitudes: List<SolicitudResumenDto> = emptyList(),
    val detalle: SolicitudDetalleDto? = null,
    val idVehiculoSel: Long? = null,
    val vinPropuesto: String = "",
    val motivo: String = "",
    val adjunto: AdjuntoSoatUi? = null,
    val errores: Map<String, String> = emptyMap(),
    val hintsOk: Map<String, String> = emptyMap(),
    val tocados: Set<String> = emptySet(),
    val mensaje: String? = null,
    val ocupado: Boolean = false,
    val evidenciaBytes: ByteArray? = null,
    val evidenciaCargando: Boolean = false,
    val evidenciaError: String? = null,
    val evidenciaRevelada: Boolean = false,
    val codigoExito: String? = null,
    val proximamente: Boolean = false
) {
    val formularioIncompleto: Boolean
        get() {
            val veh = vehiculos.find { it.idVehiculo == idVehiculoSel }
            if (idVehiculoSel == null || veh == null) return true
            if (!veh.tieneTive) return true
            if (veh.numeroVin.isNullOrBlank()) return true
            val base = SoporteFormulario.validarNueva(vinPropuesto, veh.numeroVin, motivo, exigirTodos = true)
            val evid = adjunto?.let {
                SoporteFormulario.validarEvidencia(it.contentType, it.tamanoBytes)?.let { msg -> mapOf("evidencia" to msg) }
            } ?: emptyMap()
            return base.isNotEmpty() || evid.isNotEmpty()
        }
}

class SoporteViewModel(
    private val repository: SoporteRepository,
    private val lectorUri: suspend (Uri) -> ByteArray
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoSoporte())
    val estado = _estado.asStateFlow()
    private val operacionPendiente = AtomicReference<String?>(null)

    init { cargarCentro() }

    fun cargarCentro() {
        _estado.value = EstadoSoporte(pantalla = PantallaSoporte.CARGANDO)
        viewModelScope.launch {
            try {
                val v = repository.vehiculosElegibles()
                val s = repository.listarSolicitudes()
                _estado.value = EstadoSoporte(
                    pantalla = PantallaSoporte.CENTRO,
                    vehiculos = v,
                    solicitudes = s
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e)
            }
        }
    }

    fun abrirCorreccionVin(pestana: PestanaCorreccion = PestanaCorreccion.NUEVA) {
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.CORRECCION,
            pestana = pestana,
            idVehiculoSel = if (pestana == PestanaCorreccion.NUEVA) null else _estado.value.idVehiculoSel,
            vinPropuesto = if (pestana == PestanaCorreccion.NUEVA) "" else _estado.value.vinPropuesto,
            motivo = if (pestana == PestanaCorreccion.NUEVA) "" else _estado.value.motivo,
            adjunto = if (pestana == PestanaCorreccion.NUEVA) null else _estado.value.adjunto,
            errores = emptyMap(),
            hintsOk = emptyMap(),
            tocados = emptySet(),
            mensaje = null,
            proximamente = false,
            codigoExito = null
        )
        refrescarVehiculos()
        if (pestana == PestanaCorreccion.HISTORIAL) refrescarHistorial()
    }

    fun refrescarVehiculos() {
        viewModelScope.launch {
            try {
                val v = repository.vehiculosElegibles()
                val sel = _estado.value.idVehiculoSel
                val selValida = sel != null && v.any { it.idVehiculo == sel }
                _estado.value = _estado.value.copy(
                    vehiculos = v,
                    idVehiculoSel = if (selValida) sel else null,
                    mensaje = null
                )
                if (selValida) revalidar()
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e, PantallaSoporte.CORRECCION)
            }
        }
    }

    fun refrescarHistorial() {
        viewModelScope.launch {
            try {
                val s = repository.listarSolicitudes()
                _estado.value = _estado.value.copy(solicitudes = s)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e, PantallaSoporte.CORRECCION)
            }
        }
    }

    fun abrirProximamente() {
        _estado.value = _estado.value.copy(proximamente = true, mensaje = null)
    }

    fun cerrarProximamente() {
        _estado.value = _estado.value.copy(proximamente = false)
    }

    fun setPestana(p: PestanaCorreccion) {
        _estado.value = _estado.value.copy(pestana = p, mensaje = null)
        if (p == PestanaCorreccion.HISTORIAL) refrescarHistorial()
    }

    fun seleccionarVehiculo(id: Long) {
        _estado.value = _estado.value.copy(
            idVehiculoSel = id,
            pantalla = PantallaSoporte.FORMULARIO,
            vinPropuesto = "",
            motivo = "",
            adjunto = null,
            errores = emptyMap(),
            hintsOk = emptyMap(),
            tocados = emptySet(),
            mensaje = null
        )
        revalidar()
    }

    /** Vuelve del formulario de solicitud a la lista de vehículos. */
    fun volverAListaVehiculos() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.CORRECCION,
            pestana = PestanaCorreccion.NUEVA,
            idVehiculoSel = null,
            vinPropuesto = "",
            motivo = "",
            adjunto = null,
            errores = emptyMap(),
            hintsOk = emptyMap(),
            tocados = emptySet(),
            mensaje = null,
            ocupado = false
        )
    }

    fun actualizarCampo(vin: String? = null, motivo: String? = null, campo: String? = null) {
        val vinNorm = vin?.let { ValidadorVin.normalizarVisual(it) }
        val motivoNorm = motivo?.let {
            it.uppercase(java.util.Locale.forLanguageTag("es-PE")).take(SoporteFormulario.MAX_MOTIVO + 20)
        }
        _estado.value = _estado.value.copy(
            vinPropuesto = vinNorm ?: _estado.value.vinPropuesto,
            motivo = motivoNorm ?: _estado.value.motivo,
            tocados = _estado.value.tocados + setOfNotNull(campo),
            mensaje = null
        )
        revalidar()
    }

    fun setAdjunto(adj: AdjuntoSoatUi?) {
        val err = adj?.let { SoporteFormulario.validarEvidencia(it.contentType, it.tamanoBytes) }
        val errores = _estado.value.errores.toMutableMap()
        if (err != null) errores["evidencia"] = err else errores.remove("evidencia")
        _estado.value = _estado.value.copy(
            adjunto = adj,
            errores = errores,
            tocados = _estado.value.tocados + "evidencia"
        )
        revalidar()
    }

    private fun revalidar() {
        val actual = _estado.value
        val veh = actual.vehiculos.find { it.idVehiculo == actual.idVehiculoSel }
        val errores = SoporteFormulario.validarNueva(
            actual.vinPropuesto, veh?.numeroVin, actual.motivo,
            tocados = actual.tocados, exigirTodos = false
        ).toMutableMap()
        if ("evidencia" in actual.tocados && actual.adjunto != null) {
            SoporteFormulario.validarEvidencia(actual.adjunto.contentType, actual.adjunto.tamanoBytes)
                ?.let { errores["evidencia"] = it }
        }
        if (veh != null && !veh.tieneTive) {
            errores["idVehiculo"] =
                "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN."
        }
        if (veh != null && veh.numeroVin.isNullOrBlank()) {
            errores["numeroVin"] = "Este vehículo no tiene VIN registrado."
        }
        val hints = mutableMapOf<String, String>()
        if ("vinPropuesto" in actual.tocados) {
            ValidadorVin.mensajeFormatoOk(actual.vinPropuesto, veh?.numeroVin)?.let { hints["vinPropuesto"] = it }
        }
        _estado.value = actual.copy(errores = errores, hintsOk = hints)
    }

    fun enviar() {
        val actual = _estado.value
        val veh = actual.vehiculos.find { it.idVehiculo == actual.idVehiculoSel }
        if (veh == null) {
            _estado.value = actual.copy(errores = mapOf("idVehiculo" to "Selecciona un vehículo."))
            return
        }
        if (!veh.tieneTive) {
            _estado.value = actual.copy(
                errores = mapOf(
                    "idVehiculo" to
                        "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN."
                ),
                mensaje = "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN."
            )
            return
        }
        if (veh.numeroVin.isNullOrBlank()) {
            _estado.value = actual.copy(
                errores = mapOf("numeroVin" to "Este vehículo no tiene VIN registrado."),
                mensaje = "Este vehículo no tiene VIN registrado."
            )
            return
        }
        val errores = SoporteFormulario.validarNueva(actual.vinPropuesto, veh.numeroVin, actual.motivo).toMutableMap()
        val adj = actual.adjunto
        if (adj != null) {
            SoporteFormulario.validarEvidencia(adj.contentType, adj.tamanoBytes)?.let { errores["evidencia"] = it }
        }
        if (errores.isNotEmpty()) {
            _estado.value = actual.copy(
                errores = errores,
                tocados = actual.tocados + setOf("vinPropuesto", "motivo", "evidencia", "idVehiculo")
            )
            return
        }
        _estado.value = actual.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                val op = operacionPendiente.get() ?: UUID.randomUUID().toString()
                operacionPendiente.set(op)
                val bytes = adj?.let { lectorUri(it.uri) }
                val resp = repository.crearCorreccionVin(
                    op,
                    veh.idVehiculo,
                    actual.vinPropuesto.trim().uppercase(),
                    SoporteFormulario.normalizarMotivo(actual.motivo),
                    bytes,
                    adj?.contentType,
                    adj?.nombre
                )
                operacionPendiente.set(null)
                val lista = runCatching { repository.listarSolicitudes() }.getOrElse { _estado.value.solicitudes }
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    pantalla = PantallaSoporte.EXITO,
                    codigoExito = resp.codigo,
                    solicitudes = lista,
                    mensaje = null,
                    errores = emptyMap()
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejar(e, PantallaSoporte.CORRECCION)
            }
        }
    }

    fun abrirDetalle(codigo: String) {
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.CARGANDO,
            evidenciaBytes = null,
            evidenciaRevelada = false,
            evidenciaError = null
        )
        viewModelScope.launch {
            try {
                val d = repository.detalle(codigo)
                _estado.value = _estado.value.copy(pantalla = PantallaSoporte.DETALLE, detalle = d)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e)
            }
        }
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
            } catch (cancelada: CancellationException) {
                throw cancelada
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

    fun volver() {
        when (_estado.value.pantalla) {
            PantallaSoporte.FORMULARIO -> volverAListaVehiculos()
            PantallaSoporte.CORRECCION, PantallaSoporte.EXITO -> cargarCentro()
            PantallaSoporte.DETALLE -> {
                _estado.value = _estado.value.copy(
                    pantalla = PantallaSoporte.CORRECCION,
                    pestana = PestanaCorreccion.HISTORIAL,
                    detalle = null,
                    evidenciaBytes = null,
                    evidenciaRevelada = false
                )
            }
            else -> Unit
        }
    }

    private fun manejar(e: Exception, pantalla: PantallaSoporte = PantallaSoporte.ERROR) {
        val fallo = e as? FalloRegistroVehiculo
        if (fallo?.codigo == "SESION_NO_VALIDA" || fallo?.estadoHttp == 401) {
            _estado.value = _estado.value.copy(pantalla = PantallaSoporte.SESION, mensaje = fallo.mensajeServidor)
            return
        }
        val timeout = e is SocketTimeoutException
            || (e is IOException && (e.message?.contains("timeout", ignoreCase = true) == true
                || e.message?.contains("timed out", ignoreCase = true) == true))
        val incierto = fallo?.incierto == true || fallo?.codigo == "OPERACION_EN_PROCESO" || timeout
        if (!incierto && fallo?.codigo != "CONFLICTO_IDEMPOTENCIA") {
            operacionPendiente.set(null)
        }
        val mensaje = when {
            timeout ->
                "La red tardó demasiado. Si reintentas, se reutilizará la misma operación para evitar duplicados."
            fallo?.codigo == "VIN_DUPLICADO" -> ValidadorVin.MSG_DUPLICADO
            fallo?.codigo == "VIN_SIN_CAMBIO" -> ValidadorVin.MSG_IGUAL
            fallo?.codigo == "TIVE_REQUERIDA" ->
                "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN."
            fallo?.codigo == "VIN_AUSENTE" ->
                fallo.mensajeServidor ?: "El vehículo no tiene VIN registrado."
            !fallo?.mensajeServidor.isNullOrBlank() -> fallo!!.mensajeServidor
            !e.message.isNullOrBlank() -> e.message
            else -> "No se pudo completar la operación."
        }
        val errores = when (fallo?.codigo) {
            "VIN_DUPLICADO", "VIN_INVALIDO", "VIN_SIN_CAMBIO" ->
                mapOf("vinPropuesto" to mensaje!!) + fallo.erroresPorCampo
            "VIN_AUSENTE" -> mapOf("numeroVin" to mensaje!!) + fallo.erroresPorCampo
            "TIVE_REQUERIDA" -> mapOf("idVehiculo" to mensaje!!)
            else -> fallo?.erroresPorCampo ?: emptyMap()
        }
        _estado.value = _estado.value.copy(
            pantalla = pantalla,
            mensaje = mensaje,
            errores = errores
        )
    }

    companion object {
        fun factory(repository: SoporteRepository, lectorUri: suspend (Uri) -> ByteArray) = viewModelFactory {
            initializer { SoporteViewModel(repository, lectorUri) }
        }
    }
}
