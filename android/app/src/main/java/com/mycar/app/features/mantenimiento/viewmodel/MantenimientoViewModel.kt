package com.mycar.app.features.mantenimiento.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mycar.app.features.alertas.domain.RepuestoAtencionAlerta
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
import com.mycar.app.features.mantenimiento.data.*
import com.mycar.app.features.mantenimiento.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Tras RF17, datos para abrir RF22 con referencia al mantenimiento recién creado. */
data class OfertaProgramarAlerta(
    val idVehiculo: Long,
    val idMantenimiento: Long,
    val codigoTipo: String,
    val servicioOtro: String?,
    val kmServicio: Int,
    val proximaFecha: String?,
    val proximoKm: Int?
)

data class MantenimientoUiState(
    val borrador: BorradorMantenimiento = BorradorMantenimiento(),
    val contexto: ContextoRf17Dto? = null, val lecturas: List<LecturaRf17Dto> = emptyList(),
    val talleresActivos: List<com.mycar.app.features.talleres.domain.Taller> = emptyList(),
    val cargando: Boolean = true, val cargandoLecturas: Boolean = false,
    val enviando: Boolean = false, val incierto: Boolean = false,
    val resultado: ResultadoRf17Dto? = null, val mensaje: String? = null,
    val errores: Map<String,String> = emptyMap(), val editor: RepuestoBorrador? = null,
    val erroresEditor: Map<String,String> = emptyMap(), val confirmarSalida: Boolean = false,
    val vehiculoPendiente: Long? = null, val recuperacionBloqueada: Boolean = false,
    val requiereSesion: Boolean = false,
    /** true si el usuario eligió «Guardar y programar alerta». */
    val programarAlertaTrasExito: Boolean = false,
    val ofertaProgramarAlerta: OfertaProgramarAlerta? = null,
    /** RF24: formulario abierto desde alerta (oculta próxima / oferta RF22). */
    val desdeAlerta: ContextoDesdeAlerta? = null
) {
    val editable get() = !requiereSesion && !enviando && !incierto && resultado == null && !recuperacionBloqueada
    val modoDesdeAlerta get() = desdeAlerta != null
}

/** Envía atención RF24; retorna idMantenimientoAtencion. */
fun interface AtenderAlertaDesdeFormulario {
    suspend fun atender(solicitud: com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta): Long
}

class MantenimientoViewModel(
    private val repository: MantenimientoRepository,
    private val archivo: ArchivoRegistroPendiente,
    private val saved: SavedStateHandle,
    private val vehiculoInicial: Long?,
    private val desdeAlerta: ContextoDesdeAlerta? = null,
    private val atenderAlerta: AtenderAlertaDesdeFormulario? = null,
    private val talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null
) : ViewModel() {
    private val gson = Gson()
    private val mutable = MutableStateFlow(MantenimientoUiState(desdeAlerta = desdeAlerta))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                if (desdeAlerta != null) {
                    // DESDE_ALERTA: no reutilizar borrador RF17 genérico; precarga bloqueada.
                    val b = BorradorMantenimiento(
                        idVehiculo = desdeAlerta.idVehiculo,
                        codigoTipo = desdeAlerta.codigoTipo,
                        servicioOtro = desdeAlerta.servicioOtro.orEmpty()
                    )
                    mutable.value = mutable.value.copy(borrador = b, desdeAlerta = desdeAlerta)
                    cargarContexto()
                    return@launch
                }
                val pendiente = runCatching { withContext(Dispatchers.IO) { archivo.leer() } }.getOrNull()
                val temporal = saved.get<String>("rf17.borrador")?.let {
                    runCatching { gson.fromJson(it, BorradorMantenimiento::class.java) }.getOrNull()
                }
                // Pendiente trabado sin poder verificar: empezar limpio para no bloquear el alta.
                val b = when {
                    pendiente != null && pendiente.envioPendiente -> pendiente
                    temporal != null && !temporal.envioPendiente -> temporal
                    else -> {
                        if (pendiente != null) runCatching { withContext(Dispatchers.IO) { archivo.borrar() } }
                        BorradorMantenimiento(idVehiculo = vehiculoInicial)
                    }
                }
                mutable.value = mutable.value.copy(borrador = b, incierto = b.envioPendiente)
                cargarContexto()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(cargando = false, recuperacionBloqueada = true,
                    mensaje = "No se pudo recuperar la solicitud pendiente. Verifica su resultado antes de iniciar otra.")
            }
        }
    }
    private fun recordar(b: BorradorMantenimiento) { saved["rf17.borrador"] = gson.toJson(b) }
    fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) {
        val actual = mutable.value
        if (!actual.editable) return
        var nuevo = cambio(actual.borrador).copy(idOperacion = UUID.randomUUID().toString(), envioPendiente = false)
        actual.desdeAlerta?.let { ctx ->
            // RF24: no permitir mutar vehículo/tipo/servicio precargados ni próxima recomendación.
            nuevo = nuevo.copy(
                idVehiculo = ctx.idVehiculo,
                codigoTipo = ctx.codigoTipo,
                servicioOtro = ctx.servicioOtro.orEmpty(),
                proximaFecha = "",
                proximoKm = ""
            )
        }
        recordar(nuevo)
        mutable.value = actual.copy(borrador = nuevo, errores = emptyMap(), mensaje = null)
        if (nuevo.codigoTipo == "CAMBIO_ACEITE" && actual.borrador.codigoTipo != "CAMBIO_ACEITE") {
            proponerAceiteVehiculo()
        }
    }
    /**
     * Recarga referencias y, si el formulario está trabado (error / sin vehículos / pendiente),
     * limpia borrador local para desbloquear la UI.
     */
    fun recargar() {
        viewModelScope.launch {
            val s = mutable.value
            val trabado = s.mensaje != null || s.contexto == null || s.contexto.vehiculos.isEmpty()
                    || s.recuperacionBloqueada || (s.incierto && s.contexto == null)
            if (trabado && desdeAlerta == null) {
                runCatching { withContext(Dispatchers.IO) { archivo.borrar() } }
                saved.remove<String>("rf17.borrador")
                mutable.value = MantenimientoUiState(
                    cargando = true,
                    borrador = BorradorMantenimiento(idVehiculo = vehiculoInicial ?: s.borrador.idVehiculo)
                )
            }
            cargarContexto()
        }
    }
    private suspend fun cargarContexto() {
        // Si ya hay contexto, no bloquear el formulario (ON_RESUME/recarga en segundo plano).
        val bloqueante = mutable.value.contexto == null
        if (bloqueante) mutable.value = mutable.value.copy(cargando = true)
        try {
            val c = repository.contexto()
            var b = mutable.value.borrador
            val ids = c.vehiculos.map { it.idVehiculo }.toSet()
            // Borrador con vehículo ausente/inactivo → reasignar; si no hay lista, limpia.
            if (!mutable.value.incierto && (b.idVehiculo == null || b.idVehiculo !in ids)) {
                val nuevoId = (c.vehiculos.firstOrNull { it.principal } ?: c.vehiculos.firstOrNull())?.idVehiculo
                b = if (nuevoId != b.idVehiculo) {
                    b.copy(idVehiculo = nuevoId, lectura = null, km = if (b.origen == "LECTURA") "" else b.km)
                } else b.copy(idVehiculo = nuevoId)
            }
            if (b.fecha.isBlank() && !mutable.value.incierto) b = b.copy(fecha = c.fechaServidor)
            val ctxAlerta = mutable.value.desdeAlerta
            if (ctxAlerta != null) {
                b = b.copy(
                    idVehiculo = ctxAlerta.idVehiculo,
                    codigoTipo = ctxAlerta.codigoTipo,
                    servicioOtro = ctxAlerta.servicioOtro.orEmpty(),
                    proximaFecha = "",
                    proximoKm = ""
                )
            }
            // Taller ya no ACTIVO → Sin taller (no bloquea el alta).
            val talleres = runCatching { talleresRepository?.listarActivos().orEmpty() }.getOrDefault(emptyList())
            if (b.idTaller != null && talleres.none { it.id == b.idTaller }) {
                b = b.copy(idTaller = null)
            }
            recordar(b)
            mutable.value = mutable.value.copy(
                contexto = c,
                borrador = b,
                talleresActivos = talleres,
                cargando = false,
                recuperacionBloqueada = false,
                mensaje = when {
                    c.vehiculos.isEmpty() -> "No tienes vehículos activos disponibles."
                    !c.registroDisponible -> "El registro está pendiente de integración. Puedes preparar el borrador."
                    else -> null
                }
            )
            cargarLecturas()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val codigo = (e as? ErrorMantenimientoRemoto)?.codigo
            val sesion = codigo in setOf("SESION_REQUERIDA", "SESION_NO_VALIDA", "SESION_INVALIDA")
                    || (e.message?.contains("sesión", ignoreCase = true) == true)
            mutable.value = mutable.value.copy(
                cargando = false,
                requiereSesion = sesion,
                recuperacionBloqueada = false,
                mensaje = when {
                    sesion -> "Tu sesión expiró. Inicia sesión de nuevo para registrar el mantenimiento."
                    else -> e.message ?: "No se pudieron cargar las referencias"
                }
            )
        }
    }
    fun fecha(fecha: String) {
        actualizar { it.copy(fecha = fecha, fechaModificada = true, lectura = null, km = if (it.origen == "LECTURA") "" else it.km) }
        viewModelScope.launch { cargarLecturas() }
    }
    fun origen(origen: String) {
        actualizar { it.copy(origen = origen, lectura = null, km = "") }
    }
    fun lectura(id: Long) {
        val l = mutable.value.lecturas.firstOrNull { it.idLecturaKm == id } ?: return
        actualizar { it.copy(lectura = id, km = l.valorKm.toString()) }
    }
    fun solicitarVehiculo(id: Long) {
        if (mutable.value.modoDesdeAlerta) return // RF24: vehículo bloqueado
        if (!mutable.value.editable || mutable.value.borrador.idVehiculo == id) return
        if (mutable.value.borrador.tieneContenido()) mutable.value = mutable.value.copy(vehiculoPendiente = id)
        else cambiarVehiculo(id)
    }
    fun confirmarVehiculo() { mutable.value.vehiculoPendiente?.let { cambiarVehiculo(it) } }
    fun cancelarVehiculo() { mutable.value = mutable.value.copy(vehiculoPendiente = null) }
    private fun cambiarVehiculo(id: Long) {
        val b = BorradorMantenimiento(idVehiculo = id, fecha = mutable.value.contexto?.fechaServidor ?: "")
        recordar(b)
        mutable.value = mutable.value.copy(borrador = b, vehiculoPendiente = null, lecturas = emptyList(), errores = emptyMap())
        viewModelScope.launch { cargarLecturas() }
    }
    private suspend fun cargarLecturas() {
        val b = mutable.value.borrador
        val id = b.idVehiculo ?: return
        if (runCatching { LocalDate.parse(b.fecha) }.isFailure) return
        mutable.value = mutable.value.copy(cargandoLecturas = true)
        try {
            val lecturas = repository.lecturas(id, b.fecha)
            if (mutable.value.borrador.idVehiculo == id && mutable.value.borrador.fecha == b.fecha)
                mutable.value = mutable.value.copy(lecturas = lecturas, cargandoLecturas = false)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (mutable.value.borrador.idVehiculo == id && mutable.value.borrador.fecha == b.fecha)
                mutable.value = mutable.value.copy(cargandoLecturas = false,
                    mensaje = e.message ?: "No se pudieron cargar las lecturas")
        }
    }
    fun editarRepuesto(id: String? = null) {
        if (!mutable.value.editable) return
        mutable.value = mutable.value.copy(editor = mutable.value.borrador.repuestos.firstOrNull { it.idLocal == id }
            ?: RepuestoBorrador(), erroresEditor = emptyMap())
    }
    fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador) {
        if (!mutable.value.editable) return
        mutable.value.editor?.let { mutable.value = mutable.value.copy(editor = cambio(it), erroresEditor = emptyMap()) }
    }
    fun cerrarEditor() { mutable.value = mutable.value.copy(editor = null, erroresEditor = emptyMap()) }
    fun confirmarRepuesto() {
        val p = mutable.value.editor ?: return
        val errores = ValidacionFormularioMantenimiento.repuesto(p)
        if (errores.isNotEmpty()) { mutable.value = mutable.value.copy(erroresEditor = errores); return }
        actualizar { b -> b.copy(repuestos = b.repuestos.filterNot { it.idLocal == p.idLocal } + p) }
        cerrarEditor()
    }
    fun quitarRepuesto(id: String) { actualizar { b -> b.copy(repuestos = b.repuestos.filterNot { it.idLocal == id }) } }
    fun proponerAceiteVehiculo() {
        val aceite = mutable.value.contexto?.vehiculos
            ?.firstOrNull { it.idVehiculo == mutable.value.borrador.idVehiculo }
            ?.aceiteRegistrado?.takeIf { it.isNotBlank() && !it.equals("No informado", true) } ?: return
        if (mutable.value.borrador.codigoTipo != "CAMBIO_ACEITE") return
        editarRepuesto()
        mutable.value.editor?.let { ed ->
            mutable.value = mutable.value.copy(editor = ed.copy(nombre = aceite))
        } ?: run {
            mutable.value = mutable.value.copy(editor = RepuestoBorrador(nombre = aceite))
        }
    }
    fun guardar(programarAlerta: Boolean = false) {
        val s = mutable.value
        if (!s.editable) return
        val errores = ValidacionFormularioMantenimiento.formulario(s.borrador, s.contexto)
        if (errores.isNotEmpty()) { mutable.value = s.copy(errores = errores); return }
        if (s.contexto?.registroDisponible != true) {
            mutable.value = s.copy(mensaje = "El registro aún no está disponible. Conserva el borrador."); return
        }
        // RF24: nunca oferta programar alerta tras atención.
        val programar = if (s.modoDesdeAlerta) false else programarAlerta
        mutable.value = s.copy(programarAlertaTrasExito = programar)
        if (s.modoDesdeAlerta) enviarAtencion(s.borrador) else enviar(s.borrador)
    }
    fun reintentarMismaOperacion() {
        val s = mutable.value
        if (s.incierto && !s.enviando && s.contexto?.registroDisponible == true) enviar(s.borrador)
    }
    private fun enviar(original: BorradorMantenimiento) {
        if (mutable.value.enviando) return
        val b = original.copy(envioPendiente = true)
        mutable.value = mutable.value.copy(enviando = true, mensaje = null)
        viewModelScope.launch {
            var envioIniciado = false
            try {
                val solicitud = ValidacionFormularioMantenimiento.solicitud(b)
                withContext(Dispatchers.IO) { archivo.guardar(b) }
                recordar(b)
                mutable.value = mutable.value.copy(borrador = b)
                envioIniciado = true
                val r = repository.registrar(requireNotNull(b.idVehiculo), solicitud)
                confirmarResultado(r, b)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val remoto = e as? ErrorMantenimientoRemoto
                val incierto = envioIniciado && (remoto?.incierto ?: true)
                if (!incierto) withContext(Dispatchers.IO) { archivo.borrar() }
                // Nueva operación tras fallo definitivo (evita 409/conflicto al reintentar).
                val conservado = b.copy(
                    envioPendiente = incierto,
                    idOperacion = if (!incierto) UUID.randomUUID().toString() else b.idOperacion
                )
                recordar(conservado)
                mutable.value = mutable.value.copy(borrador = conservado, enviando = false, incierto = incierto,
                    mensaje = e.message ?: "No se pudo confirmar el guardado",
                    errores = remoto?.campos ?: emptyMap())
                if (!incierto && remoto?.codigo == "CONTEXTO_DESACTUALIZADO") {
                    cargarContexto()
                }
            }
        }
    }

    /** RF24: registra mantenimiento nuevo vía endpoint atender (TX + marca REALIZADA). */
    private fun enviarAtencion(original: BorradorMantenimiento) {
        if (mutable.value.enviando) return
        val atender = atenderAlerta ?: run {
            mutable.value = mutable.value.copy(mensaje = "No hay servicio de atención de alerta disponible.")
            return
        }
        val b = original.copy(envioPendiente = true, proximaFecha = "", proximoKm = "")
        mutable.value = mutable.value.copy(enviando = true, mensaje = null, programarAlertaTrasExito = false)
        viewModelScope.launch {
            try {
                val sol = ValidacionFormularioMantenimiento.solicitud(b)
                recordar(b)
                mutable.value = mutable.value.copy(borrador = b)
                val idMant = atender.atender(
                    SolicitudAtenderAlerta(
                        idOperacion = sol.idOperacion,
                        fechaServicio = sol.fechaServicio,
                        kmServicio = sol.kmServicio,
                        origenKmServicio = sol.origenKmServicio,
                        idLecturaKmReferencia = sol.idLecturaKmReferencia,
                        costoTotal = sol.costoTotal?.toPlainString(),
                        observaciones = sol.observaciones,
                        repuestos = sol.repuestos.map {
                            RepuestoAtencionAlerta(
                                it.nombre, it.marca, it.cantidad,
                                it.precioUnitario?.toPlainString()
                            )
                        },
                        idTaller = b.idTaller
                    )
                )
                val r = ResultadoRf17Dto(
                    idOperacion = sol.idOperacion,
                    idMantenimiento = idMant,
                    estado = "REALIZADO",
                    kmServicio = sol.kmServicio,
                    fechaServicio = sol.fechaServicio,
                    costoTotal = sol.costoTotal,
                    cantidadRepuestos = sol.repuestos.size,
                    alertaCreada = false
                )
                withContext(Dispatchers.IO) { archivo.borrar() }
                saved.remove<String>("rf17.borrador")
                mutable.value = mutable.value.copy(
                    enviando = false, incierto = false, resultado = r, mensaje = null,
                    ofertaProgramarAlerta = null
                )
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val conservado = b.copy(envioPendiente = false)
                recordar(conservado)
                mutable.value = mutable.value.copy(
                    borrador = conservado, enviando = false, incierto = false,
                    mensaje = e.message ?: "No se pudo atender la alerta"
                )
            }
        }
    }
    fun consultarOperacion() {
        val s = mutable.value
        if (!s.incierto || s.enviando) return
        mutable.value = s.copy(enviando = true)
        viewModelScope.launch {
            try {
                val e = repository.consultar(s.borrador.idOperacion)
                when (e.estado) {
                    "CONFIRMADA" -> confirmarResultado(requireNotNull(e.resultado), s.borrador)
                    "EN_PROCESO" -> mutable.value = mutable.value.copy(enviando = false,
                        mensaje = "La operación sigue en proceso. Conserva esta solicitud.")
                    else -> mutable.value = mutable.value.copy(enviando = false,
                        mensaje = "El servidor aún no confirma esta operación. No inicies otro registro para el mismo servicio.")
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.value = mutable.value.copy(enviando = false,
                mensaje = e.message ?: "No se pudo verificar la operación") }
        }
    }
    private suspend fun confirmarResultado(r: ResultadoRf17Dto, b: BorradorMantenimiento) {
        val p = ValidacionFormularioMantenimiento.solicitud(b)
        val costoIgual = if (p.costoTotal == null) r.costoTotal == null else r.costoTotal?.compareTo(p.costoTotal) == 0
        if (r.idOperacion != p.idOperacion || r.idMantenimiento?.let { it > 0 } != true
            || r.estado != "REALIZADO" || r.kmServicio != p.kmServicio || r.fechaServicio != p.fechaServicio
            || r.cantidadRepuestos != p.repuestos.size || r.alertaCreada != false || !costoIgual)
            throw ErrorMantenimientoRemoto("RESPUESTA_INVALIDA", "La respuesta no confirma esta solicitud", true)
        withContext(Dispatchers.IO) { archivo.borrar() }
        saved.remove<String>("rf17.borrador")
        val oferta = if (mutable.value.programarAlertaTrasExito) OfertaProgramarAlerta(
            idVehiculo = requireNotNull(b.idVehiculo),
            idMantenimiento = requireNotNull(r.idMantenimiento),
            codigoTipo = b.codigoTipo,
            servicioOtro = b.servicioOtro.takeIf { it.isNotBlank() },
            kmServicio = requireNotNull(b.km.toIntOrNull() ?: r.kmServicio),
            proximaFecha = b.proximaFecha.takeIf { it.isNotBlank() },
            proximoKm = b.proximoKm.toIntOrNull()
        ) else null
        mutable.value = mutable.value.copy(
            enviando = false, incierto = false, resultado = r, mensaje = null,
            ofertaProgramarAlerta = oferta
        )
    }
    fun finalizarConfirmacion() {
        if (mutable.value.resultado == null) return
        val contexto = mutable.value.contexto
        saved.remove<String>("rf17.borrador")
        mutable.value = MantenimientoUiState(contexto = contexto, cargando = false,
            borrador = BorradorMantenimiento(idVehiculo = vehiculoInicial,
                fecha = contexto?.fechaServidor ?: ""))
    }
    fun solicitarSalir() {
        if (!mutable.value.editable) return
        mutable.value = mutable.value.copy(confirmarSalida = true)
    }
    fun seguirEditando() { mutable.value = mutable.value.copy(confirmarSalida = false) }
    fun descartar() {
        if (!mutable.value.editable) return
        saved.remove<String>("rf17.borrador")
        mutable.value = mutable.value.copy(borrador = BorradorMantenimiento(), confirmarSalida = false)
    }
}
