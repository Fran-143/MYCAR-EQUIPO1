package com.mycar.app.features.mantenimiento.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

data class MantenimientoUiState(
    val borrador: BorradorMantenimiento = BorradorMantenimiento(),
    val contexto: ContextoRf17Dto? = null, val lecturas: List<LecturaRf17Dto> = emptyList(),
    val cargando: Boolean = true, val cargandoLecturas: Boolean = false,
    val enviando: Boolean = false, val incierto: Boolean = false,
    val resultado: ResultadoRf17Dto? = null, val mensaje: String? = null,
    val errores: Map<String,String> = emptyMap(), val editor: RepuestoBorrador? = null,
    val erroresEditor: Map<String,String> = emptyMap(), val confirmarSalida: Boolean = false,
    val vehiculoPendiente: Long? = null, val recuperacionBloqueada: Boolean = false
) {
    val editable get() = !enviando && !incierto && resultado == null && !recuperacionBloqueada
}

class MantenimientoViewModel(
    private val repository: MantenimientoRepository,
    private val archivo: ArchivoRegistroPendiente,
    private val saved: SavedStateHandle,
    private val vehiculoInicial: Long?
) : ViewModel() {
    private val gson = Gson()
    private val mutable = MutableStateFlow(MantenimientoUiState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val pendiente = withContext(Dispatchers.IO) { archivo.leer() }
                val temporal = saved.get<String>("rf17.borrador")?.let {
                    gson.fromJson(it, BorradorMantenimiento::class.java)
                }
                val b = pendiente ?: temporal ?: BorradorMantenimiento(idVehiculo = vehiculoInicial)
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
        val nuevo = cambio(actual.borrador).copy(idOperacion = UUID.randomUUID().toString(), envioPendiente = false)
        recordar(nuevo)
        mutable.value = actual.copy(borrador = nuevo, errores = emptyMap(), mensaje = null)
    }
    fun recargar() { viewModelScope.launch { cargarContexto() } }
    private suspend fun cargarContexto() {
        mutable.value = mutable.value.copy(cargando = true)
        try {
            val c = repository.contexto()
            var b = mutable.value.borrador
            if (b.idVehiculo == null && !mutable.value.incierto) {
                b = b.copy(idVehiculo = (c.vehiculos.firstOrNull { it.principal } ?: c.vehiculos.firstOrNull())?.idVehiculo)
            }
            if (b.fecha.isBlank() && !mutable.value.incierto) b = b.copy(fecha = c.fechaServidor)
            recordar(b)
            mutable.value = mutable.value.copy(contexto = c, borrador = b, cargando = false,
                mensaje = if (!c.registroDisponible) "El registro está pendiente de integración. Puedes preparar el borrador." else null)
            cargarLecturas()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(cargando = false, mensaje = e.message ?: "No se pudieron cargar las referencias")
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
    fun guardar() {
        val s = mutable.value
        if (!s.editable) return
        val errores = ValidacionFormularioMantenimiento.formulario(s.borrador, s.contexto)
        if (errores.isNotEmpty()) { mutable.value = s.copy(errores = errores); return }
        if (s.contexto?.registroDisponible != true) {
            mutable.value = s.copy(mensaje = "El registro aún no está disponible. Conserva el borrador."); return
        }
        enviar(s.borrador)
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
                val incierto = envioIniciado && ((e as? ErrorMantenimientoRemoto)?.incierto ?: true)
                if (!incierto) withContext(Dispatchers.IO) { archivo.borrar() }
                val conservado = b.copy(envioPendiente = incierto)
                recordar(conservado)
                mutable.value = mutable.value.copy(borrador = conservado, enviando = false, incierto = incierto,
                    mensaje = e.message ?: "No se pudo confirmar el guardado",
                    errores = (e as? ErrorMantenimientoRemoto)?.campos ?: emptyMap())
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
        mutable.value = mutable.value.copy(enviando = false, incierto = false, resultado = r, mensaje = null)
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
