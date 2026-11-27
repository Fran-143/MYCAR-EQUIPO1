package com.mycar.app.features.soporte.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.data.SolicitudResumenDto
import com.mycar.app.features.soporte.data.VehiculoElegibleDto
import com.mycar.app.features.soporte.domain.IncidenciaGeneralFormulario
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
    CARGANDO, CENTRO, CORRECCION, FORMULARIO, INCIDENCIA_FORM, HISTORIAL,
    DETALLE, EXITO, EXITO_INCIDENCIA, ERROR, SESION
}

enum class PestanaCorreccion { NUEVA, HISTORIAL }

enum class FiltroTipoHistorial { TODAS, VIN, GENERALES }

data class EstadoSoporte(
    val pantalla: PantallaSoporte = PantallaSoporte.CARGANDO,
    val pestana: PestanaCorreccion = PestanaCorreccion.NUEVA,
    val vehiculos: List<VehiculoElegibleDto> = emptyList(),
    val solicitudes: List<SolicitudResumenDto> = emptyList(),
    val detalle: SolicitudDetalleDto? = null,
    val idVehiculoSel: Long? = null,
    val vinPropuesto: String = "",
    val motivo: String = "",
    val categoria: String? = null,
    val temaOtro: String = "",
    val asunto: String = "",
    val descripcion: String = "",
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
    val categoriaExito: String? = null,
    val asuntoExito: String? = null,
    val fechaExito: String? = null,
    val filtroTipoHistorial: FiltroTipoHistorial = FiltroTipoHistorial.TODAS,
    val filtroEstadoHistorial: String? = null,
    val origenDetalle: PantallaSoporte = PantallaSoporte.HISTORIAL,
    val sugerirVin: Boolean = false
) {
    val tieneSolicitudes: Boolean get() = solicitudes.isNotEmpty()

    val solicitudesFiltradas: List<SolicitudResumenDto>
        get() {
            var lista = when (filtroTipoHistorial) {
                FiltroTipoHistorial.TODAS -> solicitudes
                FiltroTipoHistorial.VIN -> solicitudes.filter { it.esCorreccionVin }
                FiltroTipoHistorial.GENERALES -> solicitudes.filter { it.esIncidenciaGeneral }
            }
            val est = filtroEstadoHistorial
            if (!est.isNullOrBlank()) {
                lista = lista.filter { it.estado.equals(est, ignoreCase = true) }
            }
            return lista
        }

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

    val incidenciaIncompleta: Boolean
        get() {
            val base = IncidenciaGeneralFormulario.validar(
                categoria, temaOtro, asunto, descripcion, exigirTodos = true
            )
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
            codigoExito = null,
            sugerirVin = false
        )
        refrescarVehiculos()
        if (pestana == PestanaCorreccion.HISTORIAL) refrescarHistorial()
    }

    fun abrirIncidenciaGeneral() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.INCIDENCIA_FORM,
            categoria = null,
            temaOtro = "",
            asunto = "",
            descripcion = "",
            adjunto = null,
            errores = emptyMap(),
            tocados = emptySet(),
            mensaje = null,
            codigoExito = null,
            sugerirVin = false,
            ocupado = false
        )
    }

    fun abrirHistorialUnificado() {
        if (!_estado.value.tieneSolicitudes) return
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.HISTORIAL,
            filtroTipoHistorial = FiltroTipoHistorial.TODAS,
            filtroEstadoHistorial = null,
            mensaje = null,
            detalle = null
        )
        refrescarHistorial(PantallaSoporte.HISTORIAL)
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
                if (selValida) revalidarVin()
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e, PantallaSoporte.CORRECCION)
            }
        }
    }

    fun refrescarHistorial(pantallaFallback: PantallaSoporte = PantallaSoporte.CORRECCION) {
        viewModelScope.launch {
            try {
                val s = repository.listarSolicitudes()
                _estado.value = _estado.value.copy(solicitudes = s)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                manejar(e, pantallaFallback)
            }
        }
    }

    fun setPestana(p: PestanaCorreccion) {
        _estado.value = _estado.value.copy(pestana = p, mensaje = null)
        if (p == PestanaCorreccion.HISTORIAL) refrescarHistorial()
    }

    fun setFiltroTipoHistorial(f: FiltroTipoHistorial) {
        _estado.value = _estado.value.copy(filtroTipoHistorial = f, filtroEstadoHistorial = null)
    }

    fun setFiltroEstadoHistorial(estado: String?) {
        _estado.value = _estado.value.copy(filtroEstadoHistorial = estado)
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
        revalidarVin()
    }

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
        revalidarVin()
    }

    fun actualizarIncidencia(
        categoria: String? = null,
        temaOtro: String? = null,
        asunto: String? = null,
        descripcion: String? = null,
        campo: String? = null
    ) {
        val cat = categoria ?: _estado.value.categoria
        val tema = temaOtro ?: _estado.value.temaOtro
        val asu = asunto ?: _estado.value.asunto
        val des = descripcion ?: _estado.value.descripcion
        _estado.value = _estado.value.copy(
            categoria = cat,
            temaOtro = if (cat.equals("OTROS", true)) tema else "",
            asunto = asu.take(IncidenciaGeneralFormulario.MAX_ASUNTO + 20),
            descripcion = des.take(IncidenciaGeneralFormulario.MAX_DESCRIPCION + 50),
            tocados = _estado.value.tocados + setOfNotNull(campo),
            sugerirVin = IncidenciaGeneralFormulario.sugiereCorreccionVin(asu, des),
            mensaje = null
        )
        revalidarIncidencia()
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
        if (_estado.value.pantalla == PantallaSoporte.INCIDENCIA_FORM) revalidarIncidencia() else revalidarVin()
    }

    private fun revalidarVin() {
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

    private fun revalidarIncidencia() {
        val actual = _estado.value
        val errores = IncidenciaGeneralFormulario.validar(
            actual.categoria, actual.temaOtro, actual.asunto, actual.descripcion,
            tocados = actual.tocados, exigirTodos = false
        ).toMutableMap()
        if ("evidencia" in actual.tocados && actual.adjunto != null) {
            SoporteFormulario.validarEvidencia(actual.adjunto.contentType, actual.adjunto.tamanoBytes)
                ?.let { errores["evidencia"] = it }
        }
        _estado.value = actual.copy(errores = errores)
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
                manejar(e, PantallaSoporte.FORMULARIO)
            }
        }
    }

    fun enviarIncidencia() {
        val actual = _estado.value
        val errores = IncidenciaGeneralFormulario.validar(
            actual.categoria, actual.temaOtro, actual.asunto, actual.descripcion
        ).toMutableMap()
        val adj = actual.adjunto
        if (adj != null) {
            SoporteFormulario.validarEvidencia(adj.contentType, adj.tamanoBytes)?.let { errores["evidencia"] = it }
        }
        if (errores.isNotEmpty()) {
            _estado.value = actual.copy(
                errores = errores,
                tocados = actual.tocados + setOf("categoria", "temaOtro", "asunto", "descripcion", "evidencia")
            )
            return
        }
        _estado.value = actual.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                val op = operacionPendiente.get() ?: UUID.randomUUID().toString()
                operacionPendiente.set(op)
                val bytes = adj?.let { lectorUri(it.uri) }
                val cat = actual.categoria!!
                val asuntoNorm = IncidenciaGeneralFormulario.normalizarEspacios(actual.asunto)
                val descNorm = actual.descripcion.trim()
                val tema = if (cat.equals("OTROS", true)) {
                    IncidenciaGeneralFormulario.normalizarEspacios(actual.temaOtro)
                } else null
                val resp = repository.crearIncidenciaGeneral(
                    op, cat, tema, asuntoNorm, descNorm,
                    bytes, adj?.contentType, adj?.nombre
                )
                operacionPendiente.set(null)
                val lista = runCatching { repository.listarSolicitudes() }.getOrElse { _estado.value.solicitudes }
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    pantalla = PantallaSoporte.EXITO_INCIDENCIA,
                    codigoExito = resp.codigo,
                    categoriaExito = cat,
                    asuntoExito = asuntoNorm,
                    fechaExito = java.time.Instant.now().toString(),
                    solicitudes = lista,
                    mensaje = null,
                    errores = emptyMap()
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejar(e, PantallaSoporte.INCIDENCIA_FORM)
            }
        }
    }

    fun abrirDetalle(codigo: String, origen: PantallaSoporte = PantallaSoporte.HISTORIAL) {
        _estado.value = _estado.value.copy(
            pantalla = PantallaSoporte.CARGANDO,
            origenDetalle = origen,
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
            PantallaSoporte.INCIDENCIA_FORM, PantallaSoporte.EXITO,
            PantallaSoporte.EXITO_INCIDENCIA, PantallaSoporte.HISTORIAL,
            PantallaSoporte.CORRECCION -> cargarCentro()
            PantallaSoporte.DETALLE -> {
                val origen = _estado.value.origenDetalle
                when (origen) {
                    PantallaSoporte.CORRECCION -> {
                        _estado.value = _estado.value.copy(
                            pantalla = PantallaSoporte.CORRECCION,
                            pestana = PestanaCorreccion.HISTORIAL,
                            detalle = null,
                            evidenciaBytes = null,
                            evidenciaRevelada = false
                        )
                    }
                    else -> {
                        _estado.value = _estado.value.copy(
                            pantalla = PantallaSoporte.HISTORIAL,
                            detalle = null,
                            evidenciaBytes = null,
                            evidenciaRevelada = false
                        )
                        refrescarHistorial(PantallaSoporte.HISTORIAL)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun manejar(e: Exception, pantalla: PantallaSoporte = PantallaSoporte.ERROR) {
        val fallo = e as? FalloRegistroVehiculo
        // 401 / SESION_* no implica logout inmediato: cold start o carrera de renovación.
        // Mostrar ERROR recuperable; "Iniciar sesión" queda solo en PantallaSoporte.SESION
        // si el usuario lo elige tras reintentar.
        if (fallo?.codigo == "SESION_NO_VALIDA"
            || fallo?.codigo == "SESION_INVALIDA"
            || fallo?.estadoHttp == 401
        ) {
            _estado.value = _estado.value.copy(
                pantalla = PantallaSoporte.ERROR,
                mensaje = fallo.mensajeServidor?.takeIf { it.isNotBlank() }
                    ?: "No se pudo validar la petición. Tu sesión puede seguir activa: pulsa Reintentar."
            )
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
            "DATOS_INVALIDOS" -> fallo.erroresPorCampo.ifEmpty { emptyMap() }
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
