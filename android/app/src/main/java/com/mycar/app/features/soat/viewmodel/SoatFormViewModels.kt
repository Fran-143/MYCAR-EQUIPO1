package com.mycar.app.features.soat.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.soat.data.SoatDocumentoRefDto
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.data.SoatMutacionDto
import com.mycar.app.features.soat.data.SoatPresignSolicitudDto
import com.mycar.app.features.soat.domain.*
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ModoDocSoat { NINGUNO, MANTENER, REEMPLAZAR, ELIMINAR }

/** Pasos visuales del PDF RF30 (registrar). Corregir/Renovar usan FORM + ÉXITO. */
enum class PasoSoatForm { FORMULARIO, ADJUNTO, REVISION, EXITO }

data class AdjuntoSoatUi(
    val uri: Uri,
    val nombre: String,
    val contentType: String,
    val tamanoBytes: Long
)

data class EstadoSoatForm(
    val idVehiculo: Long,
    val modo: TipoFormSoat,
    val idSoat: Long? = null,
    val campos: CamposSoat = CamposSoat(),
    val errores: Map<String, String> = emptyMap(),
    val ocupado: Boolean = false,
    val mensaje: String? = null,
    val exito: SoatDto? = null,
    val sesionRequerida: Boolean = false,
    val adjunto: AdjuntoSoatUi? = null,
    val errorAdjunto: String? = null,
    val ofrecerRecordatorio: Boolean = false,
    /** Preferencia UI del PDF en la pantalla de revisión (RF30); se aplica con PUT recordatorio tras el alta. */
    val recordarAlRegistrar: Boolean = true,
    val recordatorioAplicado: Boolean? = null,
    val modoDocumento: ModoDocSoat = ModoDocSoat.NINGUNO,
    val tieneDocumentoActual: Boolean = false,
    val confirmarVisible: Boolean = false,
    val paso: PasoSoatForm = PasoSoatForm.FORMULARIO,
    /** Ayuda al renovar: cuándo terminó / termina el SOAT anterior. */
    val avisoRenovacion: String? = null
)

enum class TipoFormSoat { REGISTRAR, CORREGIR, RENOVAR }

abstract class SoatFormViewModelBase(
    protected val idVehiculo: Long,
    protected val repository: SoatRepository,
    val modo: TipoFormSoat,
    private val idSoat: Long? = null,
    camposIniciales: CamposSoat = CamposSoat(),
    tieneDocumento: Boolean = false
) : ViewModel() {
    protected val _estado = MutableStateFlow(
        EstadoSoatForm(
            idVehiculo = idVehiculo,
            modo = modo,
            idSoat = idSoat,
            campos = camposIniciales,
            tieneDocumentoActual = tieneDocumento,
            modoDocumento = if (tieneDocumento && modo == TipoFormSoat.CORREGIR) ModoDocSoat.MANTENER else ModoDocSoat.NINGUNO
        )
    )
    val estado = _estado.asStateFlow()

    private fun exigirVencimientoFuturo(): Boolean =
        modo == TipoFormSoat.REGISTRAR || modo == TipoFormSoat.RENOVAR

    private fun mapearErroresServidor(fallo: FalloRegistroVehiculo?): Map<String, String> {
        if (fallo == null) return emptyMap()
        if (fallo.erroresPorCampo.isNotEmpty()) return fallo.erroresPorCampo
        val msg = fallo.mensajeServidor ?: return emptyMap()
        // Códigos conocidos → campo concreto (evita solo el banner superior).
        return when (fallo.codigo) {
            "SOAT_POLIZA_DUPLICADA" -> mapOf("numeroPoliza" to msg)
            "SOAT_DUPLICADO_PRIMA" -> mapOf(
                "aseguradora" to msg,
                "importePrima" to msg
            )
            "SOAT_SOLAPE" -> mapOf("fechaInicio" to msg)
            "DATOS_INVALIDOS" -> when {
                msg.contains("vencimiento", ignoreCase = true) &&
                    msg.contains("inicio", ignoreCase = true) -> mapOf("fechaVencimiento" to msg)
                msg.contains("vencimiento", ignoreCase = true) ||
                    msg.contains("pasada", ignoreCase = true) -> mapOf("fechaVencimiento" to msg)
                msg.contains("póliza", ignoreCase = true) ||
                    msg.contains("poliza", ignoreCase = true) -> mapOf("numeroPoliza" to msg)
                msg.contains("prima", ignoreCase = true) -> mapOf("importePrima" to msg)
                msg.contains("aseguradora", ignoreCase = true) -> mapOf("aseguradora" to msg)
                msg.contains("emisión", ignoreCase = true) ||
                    msg.contains("emision", ignoreCase = true) -> mapOf("fechaEmision" to msg)
                msg.contains("inicio", ignoreCase = true) -> mapOf("fechaInicio" to msg)
                else -> emptyMap()
            }
            else -> emptyMap()
        }
    }

    fun actualizarCampos(transform: (CamposSoat) -> CamposSoat) {
        val nuevos = transform(_estado.value.campos)
        // Validación en vivo solo de incoherencias (fechas/prima); obligatorios vacíos se bloquean al continuar.
        val errores = SoatFormulario.validarCampos(
            nuevos.aseguradora.ifBlank { "_" },
            nuevos.numeroPoliza.ifBlank { "0000000" },
            nuevos.fechaInicio,
            nuevos.fechaVencimiento,
            nuevos.fechaEmision,
            nuevos.importePrima,
            exigirVencimientoFuturo = exigirVencimientoFuturo()
        ).toMutableMap()
        if (nuevos.aseguradora.isBlank()) errores.remove("aseguradora")
        if (nuevos.numeroPoliza.isBlank()) errores.remove("numeroPoliza")
        if (nuevos.fechaInicio.isBlank()) errores.remove("fechaInicio")
        if (nuevos.fechaVencimiento.isBlank()) errores.remove("fechaVencimiento")
        camposBaseRenovacion()?.let { base ->
            if (nuevos.fechaInicio.isNotBlank() && nuevos.fechaVencimiento.isNotBlank()) {
                errores.putAll(
                    SoatFormulario.validarFechasRenovacion(
                        nuevos.fechaInicio, nuevos.fechaVencimiento,
                        base.fechaInicio, base.fechaVencimiento
                    )
                )
            }
        }
        _estado.value = _estado.value.copy(campos = nuevos, errores = errores, mensaje = null)
    }

    /** Para RF31 renovar: vigencia actual de referencia (mismo idSoat). */
    protected open fun camposBaseRenovacion(): CamposSoat? = null

    fun formularioValido(): Boolean {
        val c = _estado.value.campos
        val errores = SoatFormulario.validarCampos(
            c.aseguradora, c.numeroPoliza, c.fechaInicio, c.fechaVencimiento,
            c.fechaEmision, c.importePrima,
            exigirVencimientoFuturo = exigirVencimientoFuturo()
        ).toMutableMap()
        camposBaseRenovacion()?.let { base ->
            errores.putAll(
                SoatFormulario.validarFechasRenovacion(
                    c.fechaInicio, c.fechaVencimiento, base.fechaInicio, base.fechaVencimiento
                )
            )
        }
        return errores.isEmpty()
    }

    fun puedeGuardarCorregir(): Boolean {
        if (modo != TipoFormSoat.CORREGIR) return formularioValido()
        if (!formularioValido()) return false
        val s = _estado.value
        val iniciales = camposInicialesCorregir() ?: return true
        val modoDoc = when (s.modoDocumento) {
            ModoDocSoat.REEMPLAZAR -> "REEMPLAZAR"
            ModoDocSoat.ELIMINAR -> "ELIMINAR"
            else -> "MANTENER"
        }
        return SoatFormulario.hayCambiosRespectoA(iniciales, s.campos, modoDoc)
    }

    protected open fun camposInicialesCorregir(): CamposSoat? = null

    fun setAdjunto(adjunto: AdjuntoSoatUi?) {
        val err = adjunto?.let { validarAdjunto(it) }
        _estado.value = _estado.value.copy(
            adjunto = if (err == null) adjunto else null,
            errorAdjunto = err,
            modoDocumento = when {
                adjunto != null -> ModoDocSoat.REEMPLAZAR
                _estado.value.modo == TipoFormSoat.CORREGIR && _estado.value.tieneDocumentoActual ->
                    ModoDocSoat.MANTENER
                else -> ModoDocSoat.NINGUNO
            }
        )
    }

    fun setModoDocumento(modo: ModoDocSoat) {
        _estado.value = _estado.value.copy(
            modoDocumento = modo,
            adjunto = if (modo == ModoDocSoat.REEMPLAZAR) _estado.value.adjunto else null,
            errorAdjunto = null
        )
    }

    fun setRecordarAlRegistrar(activo: Boolean) {
        _estado.value = _estado.value.copy(recordarAlRegistrar = activo)
    }

    fun avanzarDesdeFormulario() {
        val campos = _estado.value.campos
        val errores = SoatFormulario.validarCampos(
            campos.aseguradora, campos.numeroPoliza, campos.fechaInicio, campos.fechaVencimiento,
            campos.fechaEmision, campos.importePrima,
            exigirVencimientoFuturo = exigirVencimientoFuturo()
        ).toMutableMap()
        camposBaseRenovacion()?.let { base ->
            errores.putAll(
                SoatFormulario.validarFechasRenovacion(
                    campos.fechaInicio, campos.fechaVencimiento, base.fechaInicio, base.fechaVencimiento
                )
            )
        }
        if (errores.isNotEmpty()) {
            _estado.value = _estado.value.copy(errores = errores)
            return
        }
        when (modo) {
            TipoFormSoat.REGISTRAR -> {
                // RN actualizada: alta/renovación rechazan vencimiento pasado; el recordatorio se ofrece si aplica.
                _estado.value = _estado.value.copy(
                    paso = PasoSoatForm.ADJUNTO,
                    errores = emptyMap(),
                    ofrecerRecordatorio = true
                )
            }
            TipoFormSoat.CORREGIR -> {
                if (!puedeGuardarCorregir()) return
                if (_estado.value.modoDocumento == ModoDocSoat.REEMPLAZAR && _estado.value.adjunto == null) {
                    _estado.value = _estado.value.copy(errorAdjunto = "Selecciona el nuevo documento.")
                    return
                }
                _estado.value = _estado.value.copy(confirmarVisible = true, mensaje = null, errores = emptyMap())
            }
            TipoFormSoat.RENOVAR -> {
                if (!formularioValido()) {
                    val c = _estado.value.campos
                    _estado.value = _estado.value.copy(
                        errores = SoatFormulario.validarCampos(
                            c.aseguradora, c.numeroPoliza, c.fechaInicio, c.fechaVencimiento,
                            c.fechaEmision, c.importePrima, exigirVencimientoFuturo = true
                        )
                    )
                    return
                }
                _estado.value = _estado.value.copy(confirmarVisible = true, mensaje = null)
            }
        }
    }

    fun avanzarDesdeAdjunto() {
        val vencido = modo == TipoFormSoat.REGISTRAR &&
            SoatFormulario.vencimientoYaPasado(_estado.value.campos.fechaVencimiento)
        _estado.value = _estado.value.copy(
            paso = PasoSoatForm.REVISION,
            errorAdjunto = null,
            recordarAlRegistrar = if (vencido) false else _estado.value.recordarAlRegistrar,
            ofrecerRecordatorio = if (modo == TipoFormSoat.REGISTRAR) !vencido else _estado.value.ofrecerRecordatorio
        )
    }

    fun retrocederPaso() {
        val s = _estado.value
        when (s.paso) {
            PasoSoatForm.ADJUNTO -> _estado.value = s.copy(paso = PasoSoatForm.FORMULARIO)
            PasoSoatForm.REVISION -> _estado.value = s.copy(paso = PasoSoatForm.ADJUNTO)
            else -> Unit
        }
    }

    fun mostrarConfirmacion() = avanzarDesdeFormulario()

    fun ocultarConfirmacion() {
        _estado.value = _estado.value.copy(confirmarVisible = false)
    }

    fun confirmar() {
        val s = _estado.value
        if (s.ocupado) return
        _estado.value = s.copy(ocupado = true, confirmarVisible = false, mensaje = null)
        viewModelScope.launch {
            try {
                val doc = prepararDocumento(s)
                val solicitud = construirSolicitud(s, doc)
                // 1) Persistir SOAT (RF30 / renovar). 2) Recordatorio = operación aparte.
                var resultado = enviar(solicitud)
                var recordatorioOk: Boolean? = false
                // RF30-EA06: registro con vencimiento pasado → no activar recordatorio en el mismo flujo.
                val permitirRecordatorioAlta = when (modo) {
                    TipoFormSoat.REGISTRAR ->
                        s.recordarAlRegistrar && !SoatFormulario.vencimientoYaPasado(s.campos.fechaVencimiento)
                    TipoFormSoat.RENOVAR -> s.recordarAlRegistrar
                    TipoFormSoat.CORREGIR -> false
                }
                if (permitirRecordatorioAlta) {
                    try {
                        resultado = repository.recordatorio(idVehiculo, resultado.idSoat, true)
                        recordatorioOk = resultado.recordatorioActivo
                    } catch (_: Exception) {
                        // Conservar SOAT; confirmación muestra recordatorio inactivo.
                        recordatorioOk = false
                        resultado = repository.consultar(idVehiculo).actual ?: resultado
                    }
                }
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    exito = resultado,
                    paso = PasoSoatForm.EXITO,
                    ofrecerRecordatorio = false,
                    recordatorioAplicado = recordatorioOk
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                val fallo = e as? FalloRegistroVehiculo
                val porCampo = mapearErroresServidor(fallo)
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    sesionRequerida = fallo?.estadoHttp == 401,
                    errores = if (porCampo.isNotEmpty()) porCampo else _estado.value.errores,
                    // Si el fallo tiene campos, no saturar con mensaje genérico arriba.
                    mensaje = if (porCampo.isNotEmpty()) null
                    else fallo?.mensajeServidor ?: "No pudimos guardar el SOAT. Inténtalo de nuevo",
                    paso = if (porCampo.isNotEmpty()) PasoSoatForm.FORMULARIO else _estado.value.paso
                )
            }
        }
    }

    private suspend fun prepararDocumento(s: EstadoSoatForm): SoatDocumentoRefDto? {
        if (s.modoDocumento == ModoDocSoat.MANTENER || s.modoDocumento == ModoDocSoat.ELIMINAR) return null
        val adj = s.adjunto ?: return null
        val tamano = adj.tamanoBytes.toInt().coerceAtLeast(1)
        val presign = repository.presignUpload(
            idVehiculo,
            SoatPresignSolicitudDto(adj.contentType, tamano, adj.nombre)
        )
        val bytes = leerBytes(adj.uri)
        repository.subirDocumento(presign, bytes, adj.contentType)
        return SoatDocumentoRefDto(presign.objectKey, adj.contentType, tamano, adj.nombre)
    }

    protected open suspend fun leerBytes(uri: Uri): ByteArray =
        throw UnsupportedOperationException("El host debe proveer lectura de URI")

    protected open fun construirSolicitud(s: EstadoSoatForm, doc: SoatDocumentoRefDto?): SoatMutacionDto {
        val c = s.campos
        val prima = c.importePrima.trim().takeIf { it.isNotEmpty() }?.replace(",", ".")?.toDoubleOrNull()
        val accion = when (s.modo) {
            TipoFormSoat.CORREGIR -> when (s.modoDocumento) {
                ModoDocSoat.ELIMINAR -> "QUITAR"
                ModoDocSoat.REEMPLAZAR -> "REEMPLAZAR"
                else -> "MANTENER"
            }
            else -> null
        }
        val aseg = AseguradoraSoat.normalizarParaGuardar(c.aseguradora) ?: c.aseguradora.trim()
        return SoatMutacionDto(
            idOperacion = if (s.modo == TipoFormSoat.CORREGIR) null else UUID.randomUUID().toString(),
            idSoatAnterior = if (s.modo == TipoFormSoat.RENOVAR) s.idSoat else null,
            aseguradora = aseg,
            numeroPoliza = SoatFormulario.normalizarPoliza(c.numeroPoliza),
            fechaInicio = SoatFormulario.fechaUiAIso(c.fechaInicio) ?: c.fechaInicio.trim(),
            fechaVencimiento = SoatFormulario.fechaUiAIso(c.fechaVencimiento) ?: c.fechaVencimiento.trim(),
            fechaEmision = SoatFormulario.fechaUiAIso(c.fechaEmision),
            nombreContratante = c.nombreContratante.trim().uppercase(Locale("es", "PE")).takeIf { it.isNotEmpty() },
            importePrima = prima,
            documento = doc,
            accionDocumento = accion
        )
    }

    protected abstract suspend fun enviar(solicitud: SoatMutacionDto): SoatDto

    private fun validarAdjunto(adj: AdjuntoSoatUi): String? {
        val esPdf = adj.contentType == "application/pdf"
        val esImg = adj.contentType.startsWith("image/")
        if (!esPdf && !esImg) return "Solo JPG, PNG o PDF."
        val max = if (esPdf) SOAT_MAX_PDF_BYTES else SOAT_MAX_IMAGEN_BYTES
        if (adj.tamanoBytes > max) {
            return if (esPdf) "El PDF no puede superar 10 MB." else "La imagen no puede superar 5 MB."
        }
        return null
    }
}

class RegistrarSoatViewModel(
    idVehiculo: Long,
    repository: SoatRepository,
    private val lectorUri: suspend (Uri) -> ByteArray
) : SoatFormViewModelBase(idVehiculo, repository, TipoFormSoat.REGISTRAR) {
    override suspend fun leerBytes(uri: Uri) = lectorUri(uri)
    override suspend fun enviar(solicitud: SoatMutacionDto) = repository.registrar(idVehiculo, solicitud)

    companion object {
        fun factory(
            idVehiculo: Long,
            repository: SoatRepository,
            lectorUri: suspend (Uri) -> ByteArray
        ) = viewModelFactory {
            initializer { RegistrarSoatViewModel(idVehiculo, repository, lectorUri) }
        }
    }
}

class CorregirSoatViewModel(
    idVehiculo: Long,
    private val idSoatRegistro: Long,
    repository: SoatRepository,
    soat: SoatDto,
    private val lectorUri: suspend (Uri) -> ByteArray
) : SoatFormViewModelBase(
    idVehiculo, repository, TipoFormSoat.CORREGIR, idSoatRegistro,
    SoatFormulario.desdeSoat(soat), soat.conDocumento
) {
    private val iniciales = SoatFormulario.desdeSoat(soat)
    override fun camposInicialesCorregir(): CamposSoat = iniciales
    override suspend fun leerBytes(uri: Uri) = lectorUri(uri)
    override suspend fun enviar(solicitud: SoatMutacionDto) =
        repository.corregir(idVehiculo, idSoatRegistro, solicitud)

    companion object {
        fun factory(
            idVehiculo: Long,
            idSoat: Long,
            repository: SoatRepository,
            soat: SoatDto,
            lectorUri: suspend (Uri) -> ByteArray
        ) = viewModelFactory {
            initializer { CorregirSoatViewModel(idVehiculo, idSoat, repository, soat, lectorUri) }
        }
    }
}

class RenovarSoatViewModel(
    idVehiculo: Long,
    idSoatAnterior: Long,
    repository: SoatRepository,
    soatAnterior: SoatDto?,
    private val lectorUri: suspend (Uri) -> ByteArray
) : SoatFormViewModelBase(
    idVehiculo,
    repository,
    TipoFormSoat.RENOVAR,
    idSoatAnterior,
    camposIniciales = soatAnterior?.let {
        val prev = SoatFormulario.desdeSoat(it)
        CamposSoat(
            aseguradora = prev.aseguradora,
            numeroPoliza = "",
            fechaInicio = "",
            fechaVencimiento = "",
            fechaEmision = "",
            nombreContratante = prev.nombreContratante,
            importePrima = ""
        )
    } ?: CamposSoat()
) {
    private val base = soatAnterior?.let { SoatFormulario.desdeSoat(it) }
    init {
        soatAnterior?.let { ant ->
            val fin = SoatFormulario.formatearFecha(ant.fechaVencimiento)
            _estado.value = _estado.value.copy(
                avisoRenovacion =
                    "Tu SOAT anterior termina el $fin. La nueva vigencia debe empezar después de esa fecha."
            )
        }
    }
    override fun camposBaseRenovacion(): CamposSoat? = base
    override suspend fun leerBytes(uri: Uri) = lectorUri(uri)
    override suspend fun enviar(solicitud: SoatMutacionDto) = repository.renovar(idVehiculo, solicitud)

    companion object {
        fun factory(
            idVehiculo: Long,
            idSoatAnterior: Long,
            repository: SoatRepository,
            soatAnterior: SoatDto?,
            lectorUri: suspend (Uri) -> ByteArray
        ) = viewModelFactory {
            initializer {
                RenovarSoatViewModel(idVehiculo, idSoatAnterior, repository, soatAnterior, lectorUri)
            }
        }
    }
}

class RecordatorioSoatViewModel(
    private val idVehiculo: Long,
    private val idSoat: Long,
    private val repository: SoatRepository,
    recordatorioInicial: Boolean
) : ViewModel() {
    data class Estado(
        val activo: Boolean,
        val ocupado: Boolean = false,
        val mensaje: String? = null,
        val exito: Boolean = false,
        val sesion: Boolean = false
    )

    private val _estado = MutableStateFlow(Estado(recordatorioInicial))
    val estado = _estado.asStateFlow()

    fun alternar(activo: Boolean) {
        if (_estado.value.ocupado) return
        if (_estado.value.activo == activo) return
        _estado.value = _estado.value.copy(ocupado = true, mensaje = null, exito = false)
        viewModelScope.launch {
            try {
                repository.recordatorio(idVehiculo, idSoat, activo)
                // Mantener la pantalla operable para volver a activar/desactivar.
                _estado.value = Estado(activo = activo, exito = true)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                val fallo = e as? FalloRegistroVehiculo
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    exito = false,
                    sesion = fallo?.estadoHttp == 401,
                    mensaje = fallo?.mensajeServidor ?: "No se pudo actualizar el recordatorio."
                )
            }
        }
    }

    companion object {
        fun factory(
            idVehiculo: Long,
            idSoat: Long,
            repository: SoatRepository,
            recordatorioInicial: Boolean
        ) = viewModelFactory {
            initializer { RecordatorioSoatViewModel(idVehiculo, idSoat, repository, recordatorioInicial) }
        }
    }
}