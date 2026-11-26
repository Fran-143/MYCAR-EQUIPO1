package com.mycar.app.features.planes.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.BuildConfig
import com.mycar.app.features.planes.data.CatalogoPlanesDto
import com.mycar.app.features.planes.data.EventoSuscripcionDto
import com.mycar.app.features.planes.data.MiSuscripcionDto
import com.mycar.app.features.planes.data.OperacionPagoDto
import com.mycar.app.features.planes.data.SolicitudSuscripcionDto
import com.mycar.app.features.planes.domain.ErrorPlanes
import com.mycar.app.features.planes.domain.PlanesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaPlanes {
    CATALOGO, PRUEBA_RESUMEN, PAGO_CONFIRMAR, CONTRATAR, ESCENARIOS, PROCESANDO, RESULTADO,
    MI_SUSCRIPCION, DETALLE_SOLICITUD
}

data class EstadoPlanes(
    val pantalla: PantallaPlanes = PantallaPlanes.CATALOGO,
    val cargando: Boolean = false,
    val procesando: Boolean = false,
    val mensaje: String? = null,
    val sesionInvalida: Boolean = false,
    val catalogo: CatalogoPlanesDto? = null,
    val mia: MiSuscripcionDto? = null,
    val eventos: List<EventoSuscripcionDto> = emptyList(),
    val solicitudes: List<SolicitudSuscripcionDto> = emptyList(),
    val solicitudDetalle: SolicitudSuscripcionDto? = null,
    val operacion: OperacionPagoDto? = null,
    val resultadoTitulo: String? = null,
    val resultadoDetalle: String? = null,
    val exito: Boolean = false
)

class PlanesViewModel(private val repository: PlanesRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPlanes(cargando = true))
    val estado: StateFlow<EstadoPlanes> = _estado.asStateFlow()
    /** Escenarios local-test / sidecar hotfix (no requerido para simulación académica en release). */
    val herramientasLocalTest: Boolean = BuildConfig.LOCAL_TEST_TOOLS

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(cargando = true, mensaje = null)
            try {
                val cat = repository.catalogo()
                val mia = repository.miSuscripcion()
                val eventos = runCatching { repository.eventos() }.getOrDefault(emptyList())
                val sols = runCatching { repository.solicitudes() }.getOrDefault(emptyList())
                _estado.value = _estado.value.copy(
                    cargando = false, catalogo = cat, mia = mia,
                    eventos = eventos, solicitudes = sols, mensaje = null
                )
            } catch (e: ErrorPlanes) {
                if (e.codigo.contains("SESION", true) || e.codigo == "NO_AUTORIZADO") {
                    _estado.value = _estado.value.copy(cargando = false, sesionInvalida = true, mensaje = e.message)
                } else {
                    _estado.value = _estado.value.copy(cargando = false, mensaje = e.message)
                }
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false, mensaje = e.message ?: "No se pudo cargar planes."
                )
            }
        }
    }

    fun abrirPruebaGratuita() {
        if (_estado.value.mia?.puedeSolicitarPruebaGratuita != true) {
            _estado.value = _estado.value.copy(
                mensaje = _estado.value.mia?.motivoNoPruebaGratuita
                    ?: "Configuración pendiente para prueba gratuita."
            )
            return
        }
        _estado.value = _estado.value.copy(pantalla = PantallaPlanes.PRUEBA_RESUMEN, mensaje = null)
    }

    fun enviarPruebaGratuita() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(procesando = true, mensaje = null)
            try {
                val sol = repository.solicitarPruebaGratuita()
                val sols = runCatching { repository.solicitudes() }.getOrDefault(listOf(sol))
                val mia = runCatching { repository.miSuscripcion() }.getOrNull() ?: _estado.value.mia
                _estado.value = _estado.value.copy(
                    procesando = false,
                    pantalla = PantallaPlanes.RESULTADO,
                    solicitudDetalle = sol,
                    solicitudes = sols,
                    mia = mia,
                    exito = true,
                    resultadoTitulo = "Solicitud enviada",
                    resultadoDetalle = "Tu solicitud de Premium gratuito está pendiente de aprobación."
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    procesando = false,
                    mensaje = e.message ?: "No se pudo enviar la solicitud."
                )
                runCatching { repository.miSuscripcion() }.onSuccess { mia ->
                    _estado.value = _estado.value.copy(mia = mia)
                }
            }
        }
    }

    fun abrirContratarSandbox() {
        if (_estado.value.mia?.puedeContratarSandbox != true) {
            _estado.value = _estado.value.copy(
                mensaje = _estado.value.mia?.motivoNoSandbox
                    ?: "Configuración pendiente para Probar Premium."
            )
            return
        }
        // Flujo académico: confirmación explícita (no escenarios local-test).
        abrirConfirmacionPagoSimulado()
    }

    /** Resumen + confirmación expresa antes de la simulación académica. */
    fun abrirConfirmacionPagoSimulado() {
        val abierta = _estado.value.mia?.codigoSolicitudAbierta
        if (!abierta.isNullOrBlank()) {
            abrirDetalleSolicitud(abierta)
            return
        }
        if (_estado.value.mia?.puedeContratarSandbox != true) {
            _estado.value = _estado.value.copy(
                mensaje = _estado.value.mia?.motivoNoSandbox ?: "No disponible."
            )
            return
        }
        _estado.value = _estado.value.copy(
            pantalla = PantallaPlanes.PAGO_CONFIRMAR, mensaje = null, operacion = null
        )
    }

    /** Confirma simulación → backend (idempotente). Sin cobro real ni datos bancarios. */
    fun confirmarPagoSimulado() {
        if (_estado.value.procesando) return
        val abierta = _estado.value.mia?.codigoSolicitudAbierta
        if (!abierta.isNullOrBlank()) {
            abrirDetalleSolicitud(abierta)
            return
        }
        if (_estado.value.mia?.puedeContratarSandbox != true) {
            _estado.value = _estado.value.copy(
                mensaje = _estado.value.mia?.motivoNoSandbox ?: "No disponible."
            )
            return
        }
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                procesando = true, mensaje = null, pantalla = PantallaPlanes.PROCESANDO
            )
            try {
                val sol = repository.solicitarPagoSimulado()
                val sols = runCatching { repository.solicitudes() }.getOrDefault(listOf(sol))
                val mia = runCatching { repository.miSuscripcion() }.getOrNull() ?: _estado.value.mia
                val estadoSol = sol.estado?.uppercase().orEmpty()
                val premiumOk = mia?.planCodigo.equals("PREMIUM", ignoreCase = true) &&
                    estadoSol == "APROBADA"
                _estado.value = _estado.value.copy(
                    procesando = false,
                    pantalla = PantallaPlanes.RESULTADO,
                    solicitudDetalle = sol,
                    solicitudes = sols,
                    mia = mia,
                    exito = true,
                    resultadoTitulo = if (premiumOk) "Premium activado" else "Solicitud enviada",
                    resultadoDetalle = if (premiumOk)
                        "Premium activo (simulación académica). Sin cobro real. Comprobante: SIMULADO · SIN VALIDEZ DE PAGO."
                    else
                        "Solicitud ${sol.codigo} pendiente de aprobación administrativa. Sin cobro real. Puedes volver a solicitar pago simulado cuando se decida o se anule el plan."
                )
            } catch (e: Exception) {
                val msg = e.message ?: "No se pudo completar la simulación."
                _estado.value = _estado.value.copy(
                    procesando = false,
                    pantalla = PantallaPlanes.PAGO_CONFIRMAR,
                    mensaje = msg
                )
                runCatching { repository.miSuscripcion() }.onSuccess { mia ->
                    _estado.value = _estado.value.copy(mia = mia)
                    mia.codigoSolicitudAbierta?.takeIf { it.isNotBlank() }?.let { abrirDetalleSolicitud(it) }
                }
            }
        }
    }

    @Deprecated("Usar confirmarPagoSimulado / abrirConfirmacionPagoSimulado")
    fun enviarPagoSimulado() = abrirConfirmacionPagoSimulado()

    fun abrirMiSuscripcion() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                pantalla = PantallaPlanes.MI_SUSCRIPCION, cargando = true, mensaje = null
            )
            try {
                val mia = repository.miSuscripcion()
                val eventos = runCatching { repository.eventos() }.getOrDefault(emptyList())
                val sols = runCatching { repository.solicitudes() }.getOrDefault(emptyList())
                _estado.value = _estado.value.copy(
                    cargando = false, mia = mia, eventos = eventos, solicitudes = sols, mensaje = null
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false, mensaje = e.message ?: "No se pudo cargar la suscripción."
                )
            }
        }
    }

    fun cancelarPremium() {
        if (_estado.value.procesando) return
        if (_estado.value.mia?.puedeCancelar != true) {
            _estado.value = _estado.value.copy(
                mensaje = "No hay Premium cancelable o ya programaste la cancelación."
            )
            return
        }
        viewModelScope.launch {
            _estado.value = _estado.value.copy(procesando = true, mensaje = null)
            try {
                val r = repository.cancelarPremium()
                val mia = runCatching { repository.miSuscripcion() }.getOrNull() ?: _estado.value.mia
                _estado.value = _estado.value.copy(
                    procesando = false,
                    mia = mia,
                    pantalla = PantallaPlanes.RESULTADO,
                    exito = true,
                    resultadoTitulo = "Cancelación programada",
                    resultadoDetalle = r.mensaje
                        ?: "Conservas Premium hasta la fecha de fin; luego pasarás a Free."
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    procesando = false,
                    mensaje = e.message ?: "No se pudo cancelar la suscripción."
                )
            }
        }
    }

    fun abrirDetalleSolicitud(codigo: String) {
        if (codigo.isBlank()) return
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                pantalla = PantallaPlanes.DETALLE_SOLICITUD,
                cargando = true,
                mensaje = null,
                solicitudDetalle = _estado.value.solicitudDetalle?.takeIf { it.codigo == codigo }
                    ?: _estado.value.solicitudes.firstOrNull { it.codigo == codigo }
            )
            try {
                val d = repository.solicitudDetalle(codigo)
                _estado.value = _estado.value.copy(
                    cargando = false,
                    pantalla = PantallaPlanes.DETALLE_SOLICITUD,
                    solicitudDetalle = d.solicitud,
                    eventos = d.historial,
                    mensaje = null
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargando = false,
                    mensaje = e.message ?: "No se pudo cargar la solicitud."
                )
            }
        }
    }

    fun continuarAEscenarios() {
        _estado.value = _estado.value.copy(pantalla = PantallaPlanes.ESCENARIOS, mensaje = null)
    }

    fun simularEscenario(escenario: String) {
        if (!herramientasLocalTest) return
        viewModelScope.launch {
            _estado.value = _estado.value.copy(
                pantalla = PantallaPlanes.PROCESANDO, procesando = true, mensaje = null
            )
            try {
                var op = _estado.value.operacion
                if (op?.referencia.isNullOrBlank()) {
                    op = repository.crearPagoSimulador()
                }
                val ref = op.referencia ?: throw ErrorPlanes("VACIO", "Sin referencia.")
                val finalOp = repository.simular(ref, escenario)
                val mia = runCatching { repository.miSuscripcion() }.getOrNull()
                val sols = runCatching { repository.solicitudes() }.getOrDefault(_estado.value.solicitudes)
                val titulo = when {
                    finalOp.estado == "APROBADA" -> "Pago de prueba completado"
                    finalOp.estado == "RECHAZADA" -> "Pago de prueba rechazado"
                    finalOp.estado == "CANCELADA" -> "Pago de prueba cancelado"
                    finalOp.estado == "PENDIENTE" -> "Esperando confirmación de pago de prueba"
                    else -> "Resultado: ${finalOp.estado}"
                }
                val detalle = finalOp.mensajeUsuario
                    ?: if (finalOp.estado == "APROBADA")
                        "Tu solicitud Premium está pendiente de aprobación administrativa."
                    else "Simulación interna · ref ${finalOp.referencia}"
                _estado.value = _estado.value.copy(
                    procesando = false,
                    pantalla = PantallaPlanes.RESULTADO,
                    operacion = finalOp,
                    mia = mia ?: _estado.value.mia,
                    solicitudes = sols,
                    exito = finalOp.estado == "APROBADA",
                    resultadoTitulo = titulo,
                    resultadoDetalle = detalle
                )
            } catch (e: ErrorPlanes) {
                _estado.value = _estado.value.copy(
                    procesando = false, pantalla = PantallaPlanes.ESCENARIOS, mensaje = e.message
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    procesando = false,
                    pantalla = PantallaPlanes.ESCENARIOS,
                    mensaje = e.message ?: "No se pudo simular el pago."
                )
            }
        }
    }

    fun volverCatalogo() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaPlanes.CATALOGO, mensaje = null, operacion = null
        )
        cargar()
    }

    fun volverAtras() {
        when (_estado.value.pantalla) {
            PantallaPlanes.CATALOGO -> Unit
            PantallaPlanes.RESULTADO, PantallaPlanes.MI_SUSCRIPCION -> volverCatalogo()
            PantallaPlanes.DETALLE_SOLICITUD ->
                _estado.value = _estado.value.copy(pantalla = PantallaPlanes.MI_SUSCRIPCION)
            PantallaPlanes.PRUEBA_RESUMEN, PantallaPlanes.PAGO_CONFIRMAR, PantallaPlanes.CONTRATAR ->
                _estado.value = _estado.value.copy(pantalla = PantallaPlanes.CATALOGO)
            PantallaPlanes.ESCENARIOS ->
                _estado.value = _estado.value.copy(pantalla = PantallaPlanes.CONTRATAR)
            PantallaPlanes.PROCESANDO -> Unit
        }
    }
}
