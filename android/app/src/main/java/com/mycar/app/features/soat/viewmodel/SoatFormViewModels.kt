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
    val paso: PasoSoatForm = PasoSoatForm.FORMULARIO
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

    fun actualizarCampos(transform: (CamposSoat) -> CamposSoat) {
        _estado.value = _estado.value.copy(campos = transform(_estado.value.campos), errores = emptyMap())
    }

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
            campos.fechaEmision, campos.importePrima
        )
        if (errores.isNotEmpty()) {
            _estado.value = _estado.value.copy(errores = errores)
            return
        }
        when (modo) {
            TipoFormSoat.REGISTRAR -> _estado.value = _estado.value.copy(paso = PasoSoatForm.ADJUNTO, errores = emptyMap())
            TipoFormSoat.CORREGIR -> {
                if (_estado.value.modoDocumento == ModoDocSoat.REEMPLAZAR && _estado.value.adjunto == null) {
                    _estado.value = _estado.value.copy(errorAdjunto = "Selecciona el nuevo documento.")
                    return
                }
                _estado.value = _estado.value.copy(confirmarVisible = true)
            }
            TipoFormSoat.RENOVAR -> _estado.value = _estado.value.copy(confirmarVisible = true)
        }
    }

    fun avanzarDesdeAdjunto() {
        _estado.value = _estado.value.copy(paso = PasoSoatForm.REVISION, errorAdjunto = null)
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
                if ((modo == TipoFormSoat.REGISTRAR || modo == TipoFormSoat.RENOVAR) && s.recordarAlRegistrar) {
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
                _estado.value = _estado.value.copy(
                    ocupado = false,
                    sesionRequerida = fallo?.estadoHttp == 401,
                    mensaje = fallo?.mensajeServidor ?: "No se pudo guardar. Inténtalo nuevamente."
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
        return SoatMutacionDto(
            idOperacion = if (s.modo == TipoFormSoat.CORREGIR) null else UUID.randomUUID().toString(),
            idSoatAnterior = if (s.modo == TipoFormSoat.RENOVAR) s.idSoat else null,
            aseguradora = c.aseguradora.trim(),
            numeroPoliza = c.numeroPoliza.trim(),
            fechaInicio = c.fechaInicio.trim(),
            fechaVencimiento = c.fechaVencimiento.trim(),
            fechaEmision = c.fechaEmision.trim().takeIf { it.isNotEmpty() },
            nombreContratante = c.nombreContratante.trim().takeIf { it.isNotEmpty() },
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
    private val lectorUri: suspend (Uri) -> ByteArray
) : SoatFormViewModelBase(idVehiculo, repository, TipoFormSoat.RENOVAR, idSoatAnterior) {
    override suspend fun leerBytes(uri: Uri) = lectorUri(uri)
    override suspend fun enviar(solicitud: SoatMutacionDto) = repository.renovar(idVehiculo, solicitud)

    companion object {
        fun factory(
            idVehiculo: Long,
            idSoatAnterior: Long,
            repository: SoatRepository,
            lectorUri: suspend (Uri) -> ByteArray
        ) = viewModelFactory {
            initializer { RenovarSoatViewModel(idVehiculo, idSoatAnterior, repository, lectorUri) }
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
        _estado.value = _estado.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                repository.recordatorio(idVehiculo, idSoat, activo)
                _estado.value = Estado(activo, exito = true)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (e: Exception) {
                val fallo = e as? FalloRegistroVehiculo
                _estado.value = _estado.value.copy(
                    ocupado = false,
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