package com.mycar.app.features.tive.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi
import com.mycar.app.features.tive.data.TiveActualizarRequest
import com.mycar.app.features.tive.data.TiveDto
import com.mycar.app.features.tive.data.TivePrecheckResponse
import com.mycar.app.features.tive.data.TiveRegistrarRequest
import com.mycar.app.features.tive.domain.CamposTive
import com.mycar.app.features.tive.domain.TiveFormulario
import com.mycar.app.features.tive.domain.TiveRepository
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TipoFormTive { REGISTRAR, EDITAR }
enum class ModoDocTive { NINGUNO, MANTENER, AGREGAR, REEMPLAZAR, QUITAR }

data class EstadoTiveForm(
    val idVehiculo: Long,
    val modo: TipoFormTive,
    val idTive: Long? = null,
    val versionFila: Int = 0,
    val fase: FaseTiveForm = FaseTiveForm.CARGANDO,
    val precheck: TivePrecheckResponse? = null,
    val campos: CamposTive = CamposTive(),
    val errores: Map<String, String> = emptyMap(),
    val hintsOk: Map<String, String> = emptyMap(),
    val tocados: Set<String> = emptySet(),
    val ocupado: Boolean = false,
    val mensaje: String? = null,
    val sesionRequerida: Boolean = false,
    val exito: Boolean = false,
    val adjunto: AdjuntoSoatUi? = null,
    val errorAdjunto: String? = null,
    val modoDocumento: ModoDocTive = ModoDocTive.NINGUNO,
    val tieneDocumentoActual: Boolean = false,
    val mostrarDialogoVin: Boolean = false
) {
    val formularioIncompleto: Boolean
        get() = TiveFormulario.validar(
            campos,
            editar = modo == TipoFormTive.EDITAR,
            exigirTodos = true
        ).isNotEmpty() || errorAdjunto != null
}

enum class FaseTiveForm { CARGANDO, FORMULARIO, BLOQUEO, ERROR, SESION, EXITO }

class RegistrarTiveViewModel(
    private val idVehiculo: Long,
    private val repository: TiveRepository,
    private val lectorUri: suspend (Uri) -> ByteArray
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoTiveForm(idVehiculo, TipoFormTive.REGISTRAR))
    val estado = _estado.asStateFlow()
    /** Reutiliza el UUID si el intento previo quedó incierto (timeout / 409 OPERACION_EN_PROCESO). */
    private val operacionPendiente = AtomicReference<String?>(null)

    init { cargarPrecheck() }

    fun cargarPrecheck() {
        _estado.value = _estado.value.copy(
            fase = FaseTiveForm.CARGANDO, mensaje = null, errores = emptyMap(), sesionRequerida = false
        )
        viewModelScope.launch {
            try {
                val p = repository.precheck(idVehiculo)
                when {
                    p.codigoBloqueo == "VIN_AUSENTE" -> _estado.value = _estado.value.copy(
                        fase = FaseTiveForm.BLOQUEO,
                        precheck = p,
                        mostrarDialogoVin = true,
                        campos = CamposTive(
                            placa = p.placa.orEmpty(),
                            numeroMotor = p.numeroMotor.orEmpty(),
                            numeroSerie = p.numeroSerie.orEmpty()
                        )
                    )
                    !p.puedeRegistrar -> _estado.value = _estado.value.copy(
                        fase = FaseTiveForm.BLOQUEO,
                        precheck = p,
                        mensaje = p.mensaje ?: "No puedes registrar TIVE en este vehículo."
                    )
                    else -> _estado.value = _estado.value.copy(
                        fase = FaseTiveForm.FORMULARIO,
                        precheck = p,
                        campos = CamposTive(
                            placa = p.placa.orEmpty(),
                            numeroVin = p.numeroVin.orEmpty(),
                            numeroMotor = p.numeroMotor.orEmpty(),
                            numeroSerie = p.numeroSerie.orEmpty()
                        )
                    )
                }
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejarError(e)
            }
        }
    }

    fun cerrarDialogoVin() {
        _estado.value = _estado.value.copy(mostrarDialogoVin = false)
    }

    fun actualizarCampos(transform: (CamposTive) -> CamposTive, campoTocado: String? = null) {
        val nuevos = transform(_estado.value.campos)
        val tocados = _estado.value.tocados + setOfNotNull(campoTocado)
        val errores = TiveFormulario.validar(nuevos, tocados = tocados, exigirTodos = false)
        val hints = TiveFormulario.hintsOk(nuevos, tocados)
        _estado.value = _estado.value.copy(
            campos = nuevos, errores = errores, hintsOk = hints, tocados = tocados, mensaje = null
        )
    }

    fun setAdjunto(adj: AdjuntoSoatUi?) {
        val err = adj?.let { TiveFormulario.validarAdjunto(it.contentType, it.tamanoBytes) }
        _estado.value = _estado.value.copy(adjunto = adj, errorAdjunto = err)
    }

    fun confirmar() {
        val actual = _estado.value
        val todos = setOf("numeroTitulo", "codigoVerificacion", "fechaTitulo")
        val errores = TiveFormulario.validar(actual.campos, exigirTodos = true)
        if (errores.isNotEmpty() || actual.errorAdjunto != null) {
            _estado.value = actual.copy(errores = errores, tocados = actual.tocados + todos)
            return
        }
        _estado.value = actual.copy(ocupado = true, mensaje = null, errores = emptyMap())
        viewModelScope.launch {
            try {
                val op = operacionPendiente.get() ?: UUID.randomUUID().toString()
                operacionPendiente.set(op)
                val doc = actual.adjunto?.let { adj ->
                    val bytes = lectorUri(adj.uri)
                    repository.subirDocumento(idVehiculo, bytes, adj.contentType, adj.nombre)
                }
                val fechaIso = SoatFormulario.fechaUiAIso(actual.campos.fechaTitulo)
                    ?: throw FalloRegistroVehiculo("DATOS_INVALIDOS", mensajeServidor = "Fecha inválida.")
                val req = TiveRegistrarRequest(
                    idOperacion = op,
                    numeroTitulo = actual.campos.numeroTitulo.trim().uppercase(Locale.ROOT),
                    codigoVerificacion = actual.campos.codigoVerificacion.trim().uppercase(Locale.ROOT),
                    fechaTitulo = fechaIso,
                    zonaRegistral = null,
                    sedeRegistral = null,
                    numeroMotor = null,
                    numeroSerie = null,
                    documento = doc
                )
                repository.registrar(idVehiculo, req)
                operacionPendiente.set(null)
                _estado.value = _estado.value.copy(
                    ocupado = false, fase = FaseTiveForm.EXITO, exito = true, mensaje = null, errores = emptyMap()
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejarError(e)
            }
        }
    }

    private fun manejarError(e: Exception) {
        val fallo = e as? FalloRegistroVehiculo
        if (fallo?.codigo == "SESION_NO_VALIDA" || fallo?.estadoHttp == 401) {
            _estado.value = _estado.value.copy(fase = FaseTiveForm.SESION, sesionRequerida = true)
            return
        }
        val timeout = e is SocketTimeoutException
            || (e is IOException && (e.message?.contains("timeout", ignoreCase = true) == true
                || e.message?.contains("timed out", ignoreCase = true) == true))
        val incierto = fallo?.incierto == true
            || fallo?.codigo == "OPERACION_EN_PROCESO"
            || timeout
        if (!incierto && fallo?.codigo != null && fallo.codigo != "OPERACION_EN_PROCESO") {
            // Error definitivo del servidor: el siguiente intento puede usar un UUID nuevo.
            if (fallo.codigo != "CONFLICTO_IDEMPOTENCIA") {
                operacionPendiente.set(null)
            }
        }
        val porCampo = fallo?.erroresPorCampo.orEmpty()
        val mensaje = when {
            timeout -> "La red tardó demasiado. Si reintentas, se consultará la misma operación para evitar duplicados."
            !fallo?.mensajeServidor.isNullOrBlank() -> fallo!!.mensajeServidor
            !e.message.isNullOrBlank() -> e.message
            else -> "No se pudo guardar la TIVE."
        }
        _estado.value = _estado.value.copy(
            fase = if (_estado.value.fase == FaseTiveForm.CARGANDO) FaseTiveForm.ERROR else FaseTiveForm.FORMULARIO,
            mensaje = mensaje,
            errores = if (porCampo.isNotEmpty()) porCampo else _estado.value.errores
        )
    }

    companion object {
        fun factory(idVehiculo: Long, repository: TiveRepository, lectorUri: suspend (Uri) -> ByteArray) =
            viewModelFactory {
                initializer { RegistrarTiveViewModel(idVehiculo, repository, lectorUri) }
            }
    }
}

class EditarTiveViewModel(
    private val idVehiculo: Long,
    private val repository: TiveRepository,
    private val lectorUri: suspend (Uri) -> ByteArray
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoTiveForm(idVehiculo, TipoFormTive.EDITAR))
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.value = _estado.value.copy(fase = FaseTiveForm.CARGANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val c = repository.consultar(idVehiculo)
                val t = c.tive ?: throw FalloRegistroVehiculo("TIVE_NO_ENCONTRADA", mensajeServidor = c.mensajeDocumento)
                _estado.value = _estado.value.copy(
                    fase = FaseTiveForm.FORMULARIO,
                    idTive = t.idTive,
                    versionFila = t.versionFila,
                    campos = TiveFormulario.desdeDto(t),
                    tieneDocumentoActual = t.conDocumento,
                    modoDocumento = if (t.conDocumento) ModoDocTive.MANTENER else ModoDocTive.NINGUNO
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejarError(e)
            }
        }
    }

    fun actualizarCampos(transform: (CamposTive) -> CamposTive, campoTocado: String? = null) {
        val nuevos = transform(_estado.value.campos)
        val tocados = _estado.value.tocados + setOfNotNull(campoTocado)
        val errores = TiveFormulario.validar(nuevos, editar = true, tocados = tocados, exigirTodos = false)
        val hints = TiveFormulario.hintsOk(nuevos, tocados)
        _estado.value = _estado.value.copy(
            campos = nuevos, errores = errores, hintsOk = hints, tocados = tocados, mensaje = null
        )
    }

    fun setAdjunto(adj: AdjuntoSoatUi?) {
        val err = adj?.let { TiveFormulario.validarAdjunto(it.contentType, it.tamanoBytes) }
        _estado.value = _estado.value.copy(adjunto = adj, errorAdjunto = err)
    }

    fun setModoDocumento(modo: ModoDocTive) {
        _estado.value = _estado.value.copy(
            modoDocumento = modo,
            adjunto = if (modo == ModoDocTive.MANTENER || modo == ModoDocTive.QUITAR) null else _estado.value.adjunto,
            errorAdjunto = null
        )
    }

    fun confirmar() {
        val actual = _estado.value
        val idTive = actual.idTive ?: return
        val todos = setOf("numeroTitulo", "codigoVerificacion", "fechaTitulo")
        val errores = TiveFormulario.validar(actual.campos, editar = true, exigirTodos = true)
        if (errores.isNotEmpty() || actual.errorAdjunto != null) {
            _estado.value = actual.copy(errores = errores, tocados = actual.tocados + todos)
            return
        }
        val accion = when {
            actual.modoDocumento == ModoDocTive.QUITAR -> ModoDocTive.QUITAR.name
            actual.adjunto != null && (actual.modoDocumento == ModoDocTive.REEMPLAZAR ||
                actual.modoDocumento == ModoDocTive.AGREGAR || !actual.tieneDocumentoActual) -> {
                if (actual.tieneDocumentoActual) ModoDocTive.REEMPLAZAR.name else ModoDocTive.AGREGAR.name
            }
            actual.modoDocumento == ModoDocTive.REEMPLAZAR || actual.modoDocumento == ModoDocTive.AGREGAR -> {
                _estado.value = actual.copy(errores = mapOf("documento" to "Adjunta el documento."))
                return
            }
            else -> ModoDocTive.MANTENER.name
        }
        _estado.value = actual.copy(ocupado = true, mensaje = null, errores = emptyMap())
        viewModelScope.launch {
            try {
                val doc = if (accion == ModoDocTive.AGREGAR.name || accion == ModoDocTive.REEMPLAZAR.name) {
                    val adj = actual.adjunto!!
                    val bytes = lectorUri(adj.uri)
                    repository.subirDocumento(idVehiculo, bytes, adj.contentType, adj.nombre)
                } else null
                val fechaIso = SoatFormulario.fechaUiAIso(actual.campos.fechaTitulo)!!
                val req = TiveActualizarRequest(
                    versionFila = actual.versionFila,
                    numeroTitulo = actual.campos.numeroTitulo.trim().uppercase(Locale.ROOT),
                    codigoVerificacion = actual.campos.codigoVerificacion.trim().uppercase(Locale.ROOT),
                    fechaTitulo = fechaIso,
                    zonaRegistral = null,
                    sedeRegistral = null,
                    numeroMotor = null,
                    numeroSerie = null,
                    accionDocumento = accion,
                    documento = doc
                )
                repository.actualizar(idVehiculo, idTive, req)
                _estado.value = _estado.value.copy(ocupado = false, fase = FaseTiveForm.EXITO, exito = true)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejarError(e)
            }
        }
    }

    private fun manejarError(e: Exception) {
        val fallo = e as? FalloRegistroVehiculo
        if (fallo?.codigo == "SESION_NO_VALIDA" || fallo?.estadoHttp == 401) {
            _estado.value = _estado.value.copy(fase = FaseTiveForm.SESION, sesionRequerida = true)
            return
        }
        _estado.value = _estado.value.copy(
            fase = if (_estado.value.campos.numeroTitulo.isBlank()) FaseTiveForm.ERROR else _estado.value.fase,
            mensaje = fallo?.mensajeServidor ?: e.message,
            errores = fallo?.erroresPorCampo ?: _estado.value.errores
        )
    }

    companion object {
        fun factory(idVehiculo: Long, repository: TiveRepository, lectorUri: suspend (Uri) -> ByteArray) =
            viewModelFactory {
                initializer { EditarTiveViewModel(idVehiculo, repository, lectorUri) }
            }
    }
}