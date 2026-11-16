package com.mycar.app.features.licencia.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mycar.app.features.licencia.data.*
import com.mycar.app.features.licencia.domain.LicenciaFormulario
import com.mycar.app.features.licencia.domain.LicenciaRepository
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.vehiculos.domain.FalloRegistroVehiculo
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaLicencia {
    CARGANDO, VACIA, REGISTRAR_DATOS, REGISTRAR_FOTOS, EXITO, DETALLE,
    EDITAR, EDITAR_FOTOS, RENOVAR, ERROR, SESION
}

data class AdjuntoLicencia(val uri: Uri, val nombre: String, val contentType: String, val tamano: Long)

data class EstadoLicencia(
    val pantalla: PantallaLicencia = PantallaLicencia.CARGANDO,
    val consulta: LicenciaConsultaResponse? = null,
    val mensaje: String? = null,
    val errores: Map<String, String> = emptyMap(),
    val ocupado: Boolean = false,
    val tabHistorial: Boolean = false,
    val categoria: String = "A-I",
    val numero: String = "",
    val fechaEmision: String = "",
    val fechaVencimiento: String = "",
    val fotoFrontal: AdjuntoLicencia? = null,
    val fotoPosterior: AdjuntoLicencia? = null,
    val errorFoto: String? = null,
    val numeroVisible: Boolean = false,
    val fotoFrontalBytes: ByteArray? = null,
    val fotoPosteriorBytes: ByteArray? = null,
    val fotoFrontalRevelada: Boolean = false,
    val fotoPosteriorRevelada: Boolean = false,
    val accionFrontal: String = "MANTENER",
    val accionPosterior: String = "MANTENER",
    val mensajeExito: String? = null,
    val editCategoriaIni: String = "",
    val editNumeroIni: String = "",
    val editEmisionIni: String = "",
    val editVencimientoIni: String = "",
    /** Preferencia al registrar (como SOAT): activar recordatorio tras el alta. */
    val recordarAlRegistrar: Boolean = false,
    val recordatorioOcupado: Boolean = false
) {
    val puedeGuardarEdicion: Boolean
        get() = LicenciaFormulario.hayCambiosEdicion(
            editCategoriaIni, editNumeroIni, editEmisionIni, editVencimientoIni,
            accionFrontal, accionPosterior,
            categoria, numero, fechaEmision, fechaVencimiento
        )
}

class LicenciaViewModel(
    private val repository: LicenciaRepository,
    private val lectorUri: suspend (Uri) -> ByteArray
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoLicencia())
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        _estado.value = EstadoLicencia(pantalla = PantallaLicencia.CARGANDO)
        viewModelScope.launch {
            try {
                val c = repository.consultar()
                _estado.value = _estado.value.copy(
                    consulta = c,
                    pantalla = if (c.actual == null) PantallaLicencia.VACIA else PantallaLicencia.DETALLE,
                    mensaje = null,
                    mensajeExito = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                manejar(e)
            }
        }
    }

    fun abrirRegistrar() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaLicencia.REGISTRAR_DATOS,
            categoria = "A-I", numero = "", fechaEmision = "", fechaVencimiento = "",
            fotoFrontal = null, fotoPosterior = null, errores = emptyMap(), mensaje = null
        )
    }

    fun abrirEditar() {
        val a = _estado.value.consulta?.actual ?: return
        // Campos editables usan DD/MM/AAAA (no "10 ene. 2025").
        val em = SoatFormulario.fechaIsoAUi(a.fechaEmision)
        val ve = SoatFormulario.fechaIsoAUi(a.fechaVencimiento)
        _estado.value = _estado.value.copy(
            pantalla = PantallaLicencia.EDITAR,
            categoria = a.categoria,
            numero = a.numero,
            fechaEmision = em,
            fechaVencimiento = ve,
            accionFrontal = "MANTENER",
            accionPosterior = "MANTENER",
            fotoFrontal = null,
            fotoPosterior = null,
            errores = emptyMap(),
            mensaje = null,
            editCategoriaIni = a.categoria,
            editNumeroIni = a.numero,
            editEmisionIni = em,
            editVencimientoIni = ve
        )
    }


    fun abrirEditarFotos() {
        val s = _estado.value
        val a = s.consulta?.actual
        val cat = a?.categoria ?: s.categoria
        val num = a?.numero ?: s.numero
        val em = when {
            s.editEmisionIni.isNotBlank() -> s.editEmisionIni
            s.fechaEmision.isNotBlank() && SoatFormulario.parsearFecha(s.fechaEmision) != null -> s.fechaEmision
            a != null -> SoatFormulario.fechaIsoAUi(a.fechaEmision)
            else -> ""
        }
        val ve = when {
            s.editVencimientoIni.isNotBlank() -> s.editVencimientoIni
            s.fechaVencimiento.isNotBlank() && SoatFormulario.parsearFecha(s.fechaVencimiento) != null -> s.fechaVencimiento
            a != null -> SoatFormulario.fechaIsoAUi(a.fechaVencimiento)
            else -> ""
        }
        _estado.value = s.copy(
            pantalla = PantallaLicencia.EDITAR_FOTOS,
            categoria = cat,
            numero = num,
            fechaEmision = em,
            fechaVencimiento = ve,
            accionFrontal = "MANTENER",
            accionPosterior = "MANTENER",
            fotoFrontal = null,
            fotoPosterior = null,
            errorFoto = null,
            mensaje = null,
            editCategoriaIni = cat,
            editNumeroIni = num,
            editEmisionIni = em,
            editVencimientoIni = ve
        )
    }

    fun abrirRenovar() {
        val a = _estado.value.consulta?.actual ?: return
        if (!LicenciaFormulario.puedeRenovar(a.fechaVencimiento)) {
            _estado.value = _estado.value.copy(
                mensaje = "Aún no puedes renovar: espera hasta la fecha de vencimiento (${SoatFormulario.formatearFecha(a.fechaVencimiento)})."
            )
            return
        }
        _estado.value = _estado.value.copy(
            pantalla = PantallaLicencia.RENOVAR,
            categoria = a.categoria,
            numero = "",
            fechaEmision = "",
            fechaVencimiento = "",
            fotoFrontal = null,
            fotoPosterior = null,
            errores = emptyMap(),
            mensaje = null
        )
    }

    fun volver() {
        ocultarSensibles()
        val tiene = _estado.value.consulta?.actual != null
        _estado.value = _estado.value.copy(
            pantalla = if (tiene) PantallaLicencia.DETALLE else PantallaLicencia.VACIA,
            tabHistorial = false,
            errores = emptyMap(),
            mensaje = null,
            mensajeExito = null
        )
    }

    fun irADetalleTrasExito() {
        ocultarSensibles()
        _estado.value = _estado.value.copy(
            pantalla = PantallaLicencia.DETALLE,
            mensajeExito = null,
            mensaje = null,
            tabHistorial = false
        )
    }

    fun setTabHistorial(v: Boolean) {
        _estado.value = _estado.value.copy(tabHistorial = v)
    }

    fun actualizarCampo(
        categoria: String? = null,
        numero: String? = null,
        emision: String? = null,
        vencimiento: String? = null
    ) {
        val s = _estado.value
        _estado.value = s.copy(
            categoria = categoria ?: s.categoria,
            numero = numero?.let { LicenciaFormulario.limitarNumero(it) } ?: s.numero,
            fechaEmision = emision ?: s.fechaEmision,
            fechaVencimiento = vencimiento ?: s.fechaVencimiento,
            errores = emptyMap()
        )
    }

    fun setFoto(lado: String, adj: AdjuntoLicencia?) {
        val err = adj?.let { validarFoto(it) }
        _estado.value = when (lado) {
            "FRONTAL" -> _estado.value.copy(
                fotoFrontal = if (err == null) adj else null,
                errorFoto = err,
                accionFrontal = if (adj != null) "REEMPLAZAR" else _estado.value.accionFrontal
            )
            else -> _estado.value.copy(
                fotoPosterior = if (err == null) adj else null,
                errorFoto = err,
                accionPosterior = if (adj != null) "REEMPLAZAR" else _estado.value.accionPosterior
            )
        }
    }

    fun quitarFoto(lado: String) {
        _estado.value = when (lado) {
            "FRONTAL" -> _estado.value.copy(fotoFrontal = null, accionFrontal = "QUITAR", errorFoto = null)
            else -> _estado.value.copy(fotoPosterior = null, accionPosterior = "QUITAR", errorFoto = null)
        }
    }

    fun toggleNumero() {
        _estado.value = _estado.value.copy(numeroVisible = !_estado.value.numeroVisible)
    }

    fun setRecordarAlRegistrar(v: Boolean) {
        _estado.value = _estado.value.copy(recordarAlRegistrar = v)
    }

    fun setRecordatorioActivo(activo: Boolean) {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(recordatorioOcupado = true, mensaje = null)
            try {
                repository.recordatorio(activo)
                val c = repository.consultar()
                _estado.value = _estado.value.copy(
                    consulta = c,
                    recordatorioOcupado = false,
                    mensaje = if (activo) "Recordatorio activado." else "Recordatorio desactivado."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(recordatorioOcupado = false)
                manejar(e, PantallaLicencia.DETALLE)
            }
        }
    }

    fun revelarFoto(lado: String) {
        viewModelScope.launch {
            try {
                if (lado == "FRONTAL") {
                    _estado.value = _estado.value.copy(fotoFrontalRevelada = true)
                    if (_estado.value.fotoFrontalBytes == null) {
                        _estado.value = _estado.value.copy(fotoFrontalBytes = repository.descargarFoto("FRONTAL"))
                    }
                } else {
                    _estado.value = _estado.value.copy(fotoPosteriorRevelada = true)
                    if (_estado.value.fotoPosteriorBytes == null) {
                        _estado.value = _estado.value.copy(fotoPosteriorBytes = repository.descargarFoto("POSTERIOR"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fallo = e as? FalloRegistroVehiculo
                val msg = when (fallo?.codigo) {
                    "OBJETO_NO_ENCONTRADO", "FOTO_NO_DISPONIBLE" ->
                        "La foto no está disponible. Usa Editar fotografías para volver a subirla."
                    else -> fallo?.mensajeServidor ?: "No se pudo cargar la foto."
                }
                _estado.value = _estado.value.copy(
                    mensaje = msg,
                    fotoFrontalRevelada = if (lado == "FRONTAL") false else _estado.value.fotoFrontalRevelada,
                    fotoPosteriorRevelada = if (lado != "FRONTAL") false else _estado.value.fotoPosteriorRevelada
                )
            }
        }
    }

    fun ocultarFoto(lado: String) {
        _estado.value = if (lado == "FRONTAL") {
            _estado.value.copy(fotoFrontalRevelada = false, fotoFrontalBytes = null)
        } else {
            _estado.value.copy(fotoPosteriorRevelada = false, fotoPosteriorBytes = null)
        }
    }

    fun ocultarSensibles() {
        _estado.value = _estado.value.copy(
            numeroVisible = false,
            fotoFrontalRevelada = false,
            fotoPosteriorRevelada = false,
            fotoFrontalBytes = null,
            fotoPosteriorBytes = null
        )
    }

    fun continuarRegistroDatos() {
        val s = _estado.value
        val err = LicenciaFormulario.validarCampos(s.categoria, s.numero, s.fechaEmision, s.fechaVencimiento)
        if (err.isNotEmpty()) {
            _estado.value = s.copy(errores = err)
            return
        }
        _estado.value = s.copy(pantalla = PantallaLicencia.REGISTRAR_FOTOS, errores = emptyMap())
    }

    fun confirmarRegistro() {
        val s = _estado.value
        if (s.fotoFrontal == null || s.fotoPosterior == null) {
            _estado.value = s.copy(errorFoto = "Ambas fotografías son obligatorias.")
            return
        }
        viewModelScope.launch {
            _estado.value = s.copy(ocupado = true, mensaje = null)
            try {
                val f = subir("FRONTAL", s.fotoFrontal)
                val p = subir("POSTERIOR", s.fotoPosterior)
                repository.registrar(
                    LicenciaRegistrarRequest(
                        idOperacion = UUID.randomUUID().toString(),
                        categoria = s.categoria,
                        numero = LicenciaFormulario.normalizarNumero(s.numero),
                        fechaEmision = SoatFormulario.fechaUiAIso(s.fechaEmision)!!,
                        fechaVencimiento = SoatFormulario.fechaUiAIso(s.fechaVencimiento)!!,
                        fotoFrontal = f,
                        fotoPosterior = p
                    )
                )
                var recOk: Boolean? = null
                if (s.recordarAlRegistrar &&
                    !SoatFormulario.vencimientoYaPasado(s.fechaVencimiento)
                ) {
                    recOk = runCatching {
                        repository.recordatorio(true)
                        true
                    }.getOrElse { false }
                }
                mostrarExito(
                    when (recOk) {
                        true -> "Licencia registrada. Recordatorio activado."
                        false -> "Licencia registrada. El recordatorio no se pudo activar."
                        null -> "Licencia registrada correctamente."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                val fallo = e as? FalloRegistroVehiculo
                if (fallo?.codigo == "LICENCIA_ACTUAL_EXISTE") {
                    cargar()
                } else {
                    manejar(e, PantallaLicencia.REGISTRAR_DATOS)
                }
            }
        }
    }

    fun confirmarEditar() {
        val s = _estado.value
        if (!s.puedeGuardarEdicion) {
            _estado.value = s.copy(mensaje = "No hay cambios para guardar.")
            return
        }
        val err = LicenciaFormulario.validarCampos(s.categoria, s.numero, s.fechaEmision, s.fechaVencimiento)
        if (err.isNotEmpty()) {
            _estado.value = s.copy(errores = err)
            return
        }
        viewModelScope.launch {
            _estado.value = s.copy(ocupado = true, mensaje = null)
            try {
                val front = if (s.accionFrontal == "REEMPLAZAR" && s.fotoFrontal != null) subir("FRONTAL", s.fotoFrontal) else null
                val back = if (s.accionPosterior == "REEMPLAZAR" && s.fotoPosterior != null) subir("POSTERIOR", s.fotoPosterior) else null
                repository.editar(
                    LicenciaEditarRequest(
                        categoria = s.categoria,
                        numero = LicenciaFormulario.normalizarNumero(s.numero),
                        fechaEmision = SoatFormulario.fechaUiAIso(s.fechaEmision)!!,
                        fechaVencimiento = SoatFormulario.fechaUiAIso(s.fechaVencimiento)!!,
                        accionFotoFrontal = s.accionFrontal,
                        accionFotoPosterior = s.accionPosterior,
                        fotoFrontal = front,
                        fotoPosterior = back
                    )
                )
                mostrarExito("Licencia actualizada.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejar(e, if (s.pantalla == PantallaLicencia.EDITAR_FOTOS) PantallaLicencia.EDITAR_FOTOS else PantallaLicencia.EDITAR)
            }
        }
    }

    fun confirmarEditarFotos() = confirmarEditar()

    fun confirmarRenovar() {
        val s = _estado.value
        val actual = s.consulta?.actual ?: return
        if (!LicenciaFormulario.puedeRenovar(actual.fechaVencimiento)) {
            _estado.value = s.copy(
                mensaje = "Aún no puedes renovar: espera hasta la fecha de vencimiento."
            )
            return
        }
        val numero = s.numero.ifBlank { actual.numero }
        val err = LicenciaFormulario.validarCampos(s.categoria, numero, s.fechaEmision, s.fechaVencimiento).toMutableMap()
        LicenciaFormulario.validarRenovacionVencimiento(
            s.fechaVencimiento,
            SoatFormulario.formatearFecha(actual.fechaVencimiento)
        )?.let { err["fechaVencimiento"] = it }
        if (err.isNotEmpty()) {
            _estado.value = s.copy(errores = err)
            return
        }
        viewModelScope.launch {
            _estado.value = s.copy(ocupado = true)
            try {
                val front = s.fotoFrontal?.let { subir("FRONTAL", it) }
                val back = s.fotoPosterior?.let { subir("POSTERIOR", it) }
                repository.renovar(
                    LicenciaRenovarRequest(
                        idOperacion = UUID.randomUUID().toString(),
                        idLicenciaAnterior = actual.idLicencia,
                        categoria = s.categoria,
                        numero = LicenciaFormulario.normalizarNumero(numero),
                        fechaEmision = SoatFormulario.fechaUiAIso(s.fechaEmision)!!,
                        fechaVencimiento = SoatFormulario.fechaUiAIso(s.fechaVencimiento)!!,
                        fotoFrontal = front,
                        fotoPosterior = back
                    )
                )
                if (s.recordarAlRegistrar &&
                    !SoatFormulario.vencimientoYaPasado(s.fechaVencimiento)
                ) {
                    runCatching { repository.recordatorio(true) }
                }
                mostrarExito("Licencia renovada. Ya puedes ver el historial.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ocupado = false)
                manejar(e, PantallaLicencia.RENOVAR)
            }
        }
    }

    private suspend fun mostrarExito(mensaje: String) {
        val c = repository.consultar()
        _estado.value = EstadoLicencia(
            pantalla = PantallaLicencia.EXITO,
            consulta = c,
            mensajeExito = mensaje,
            ocupado = false
        )
    }

    private suspend fun subir(lado: String, adj: AdjuntoLicencia): LicenciaFotoDto {
        val bytes = lectorUri(adj.uri)
        return repository.subirFoto(lado, bytes, adj.contentType, adj.nombre)
    }

    private fun validarFoto(adj: AdjuntoLicencia): String? {
        val tipo = adj.contentType.lowercase()
        val esPdf = tipo == "application/pdf"
        val esImagen = tipo == "image/jpeg" || tipo == "image/png" ||
            tipo == "image/jpg" || tipo.endsWith("/jpg") || tipo.endsWith("/jpeg") || tipo.endsWith("/png")
        if (!esPdf && !esImagen) return "Solo JPG, PNG o PDF."
        if (esPdf && adj.tamano > 10L * 1024 * 1024) return "El PDF no puede superar 10 MB."
        if (esImagen && adj.tamano > 5L * 1024 * 1024) return "La imagen no puede superar 5 MB."
        return null
    }

    private fun manejar(e: Exception, pantalla: PantallaLicencia? = null) {
        val fallo = e as? FalloRegistroVehiculo
        val sesion = fallo?.estadoHttp == 401
        val apiAusente = fallo?.estadoHttp == 404
        _estado.value = _estado.value.copy(
            ocupado = false,
            pantalla = when {
                sesion -> PantallaLicencia.SESION
                apiAusente && pantalla == null -> PantallaLicencia.ERROR
                pantalla != null -> pantalla
                else -> PantallaLicencia.ERROR
            },
            errores = fallo?.erroresPorCampo.orEmpty(),
            mensaje = when {
                sesion -> "Inicia sesión."
                apiAusente ->
                    "Licencia no está disponible en este servidor (404). Usa el APK USB local o publica el backend."
                fallo?.erroresPorCampo?.isNotEmpty() == true -> null
                else -> fallo?.mensajeServidor ?: "No se pudo completar la operación."
            }
        )
    }

    override fun onCleared() {
        ocultarSensibles()
        super.onCleared()
    }

    companion object {
        fun factory(repository: LicenciaRepository, lectorUri: suspend (Uri) -> ByteArray) =
            viewModelFactory {
                initializer { LicenciaViewModel(repository, lectorUri) }
            }
    }
}
