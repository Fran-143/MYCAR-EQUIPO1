package com.mycar.app.features.alertas.programacion.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class FaseProgramacion { CARGANDO, FORMULARIO, REVISION, GUARDANDO, POR_COMPROBAR, CONFIRMADO, SIN_VEHICULOS, ERROR }
data class EstadoProgramacion(
    val fase: FaseProgramacion = FaseProgramacion.CARGANDO,
    val contexto: ContextoRf22? = null, val vehiculo: Long? = null, val tipo: Long? = null,
    val servicio: String = "", val criterio: CriterioRf22 = CriterioRf22.POR_TIEMPO,
    val unidad: UnidadRf22 = UnidadRf22.MESES, val cantidad: String = "", val kilometros: String = "",
    val antecedentes: AntecedentesRf22? = null, val referencia: ReferenciaRf22? = null,
    val revision: RevisionRf22? = null, val pendiente: SolicitudRf22? = null,
    val resultado: ResultadoRf22? = null, val mensaje: String? = null, val permiteReenvio: Boolean = false
) {
    val vehiculoActual get() = contexto?.vehiculos?.firstOrNull { it.idVehiculo == vehiculo }
    val tipoActual get() = contexto?.tipos?.firstOrNull { it.idTipoMantenimiento == tipo }
    val esOtro get() = tipoActual?.codigo == "OTRO"
    val puedeRevisar get() = fase == FaseProgramacion.FORMULARIO && vehiculo != null && tipo != null && antecedentes != null
}

/** Orquesta el formulario. El servidor calcula, autoriza y confirma; la UI no fabrica resultados reales. */
class CoordinadorProgramacion(private val repository: ProgramacionRepository,
    private val archivos: (String) -> PendientesProgramacion,
    private val nuevaOperacion: () -> String = { UUID.randomUUID().toString() }) {
    private val mutable = MutableStateFlow(EstadoProgramacion())
    val estado = mutable.asStateFlow()
    private var archivo: PendientesProgramacion? = null
    private var version = 0L
    private var cerrado = false

    suspend fun iniciar() {
        val peticion = ++version
        mutable.value = EstadoProgramacion()
        try {
            val contexto = repository.contexto()
            if (!vigente(peticion)) return
            archivo = archivos(contexto.claveCuenta)
            val pendiente = archivo!!.leer()
            if (pendiente != null) {
                mutable.value = EstadoProgramacion(FaseProgramacion.POR_COMPROBAR, contexto,
                    pendiente.revision.configuracion.idVehiculo, pendiente.revision.configuracion.idTipoMantenimiento,
                    pendiente = pendiente, mensaje = "Hay una programación pendiente de comprobar. No se creará otra operación.")
                return
            }
            mutable.value = EstadoProgramacion(if (contexto.vehiculos.isEmpty()) FaseProgramacion.SIN_VEHICULOS else FaseProgramacion.FORMULARIO,
                contexto, contexto.vehiculos.firstOrNull { it.principal }?.idVehiculo)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (vigente(peticion)) mutable.value = mutable.value.copy(fase = FaseProgramacion.ERROR, mensaje = mensaje(e)) }
    }
    suspend fun seleccionarVehiculo(id: Long) {
        if (!editable() || estado.value.contexto?.vehiculos?.none { it.idVehiculo == id } != false) return
        ++version
        mutable.value = estado.value.copy(vehiculo = id, antecedentes = null, referencia = null, servicio = "", revision = null, mensaje = null)
        cargarAntecedentes()
    }
    suspend fun seleccionarTipo(id: Long) {
        if (!editable() || estado.value.contexto?.tipos?.none { it.idTipoMantenimiento == id } != false) return
        ++version
        mutable.value = estado.value.copy(tipo = id, servicio = "", antecedentes = null, referencia = null, revision = null, mensaje = null)
        cargarAntecedentes()
    }
    private suspend fun cargarAntecedentes(servicioElegido: String? = null) {
        val actual = estado.value
        val vehiculo = actual.vehiculo ?: return
        val tipo = actual.tipo ?: return
        val peticion = ++version
        mutable.value = actual.copy(fase = FaseProgramacion.CARGANDO, antecedentes = null, referencia = null, mensaje = null)
        try {
            val datos = repository.antecedentes(vehiculo, tipo, servicioElegido)
            if (!vigente(peticion)) return
            mutable.value = estado.value.copy(fase = FaseProgramacion.FORMULARIO, antecedentes = datos,
                referencia = datos.referencia,
                criterio = if (datos.kmActual == null) CriterioRf22.POR_TIEMPO else estado.value.criterio)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (vigente(peticion)) mutable.value = estado.value.copy(fase = FaseProgramacion.FORMULARIO, mensaje = mensaje(e)) }
    }
    suspend fun elegirSugerencia(nombre: String) {
        if (!editable() || !estado.value.esOtro || nombre !in (estado.value.antecedentes?.sugerencias ?: emptyList())) return
        mutable.value = estado.value.copy(servicio = nombre, referencia = null, revision = null)
        cargarAntecedentes(nombre)
    }
    fun editarServicio(texto: String) {
        if (!editable()) return
        ++version
        mutable.value = estado.value.copy(servicio = texto, referencia = null,
            antecedentes = estado.value.antecedentes?.copy(referencia = null), revision = null, mensaje = null)
    }
    fun usarReferencia(usar: Boolean) {
        if (!editable()) return
        mutable.value = estado.value.copy(referencia = if (usar) estado.value.antecedentes?.referencia else null, revision = null)
    }
    fun criterio(c: CriterioRf22) {
        if (!editable() || c != CriterioRf22.POR_TIEMPO && estado.value.antecedentes?.kmActual == null) return
        mutable.value = estado.value.copy(criterio = c, revision = null, mensaje = null)
    }
    fun intervalos(cantidad: String = estado.value.cantidad, km: String = estado.value.kilometros,
        unidad: UnidadRf22 = estado.value.unidad) {
        if (!editable()) return
        mutable.value = estado.value.copy(cantidad = cantidad, kilometros = km, unidad = unidad, revision = null, mensaje = null)
    }
    suspend fun recargarDatos() { if (editable()) cargarAntecedentes() }
    suspend fun revisar() {
        if (!estado.value.puedeRevisar) return
        val actual = estado.value
        val c = try {
            fun positivo(texto: String): Int = texto.toIntOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException()
            if (actual.esOtro && (actual.servicio.isBlank() || actual.servicio.codePointCount(0, actual.servicio.length) > 160)) throw IllegalArgumentException()
            ConfiguracionRf22(actual.vehiculo!!, actual.tipo!!, actual.servicio.takeIf { actual.esOtro }, actual.criterio,
                actual.unidad.takeUnless { actual.criterio == CriterioRf22.POR_KILOMETRAJE },
                if (actual.criterio != CriterioRf22.POR_KILOMETRAJE) positivo(actual.cantidad) else null,
                if (actual.criterio != CriterioRf22.POR_TIEMPO) positivo(actual.kilometros) else null,
                actual.referencia?.idMantenimiento)
        } catch (_: Exception) { mutable.value = actual.copy(mensaje = "Revisa el servicio y los intervalos: deben ser enteros positivos."); return }
        val peticion = ++version
        mutable.value = actual.copy(fase = FaseProgramacion.CARGANDO, mensaje = null)
        try {
            val revision = repository.revisar(c)
            if (vigente(peticion)) mutable.value = actual.copy(fase = FaseProgramacion.REVISION, revision = revision, mensaje = null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (vigente(peticion)) mutable.value = actual.copy(mensaje = mensaje(e)) }
    }
    fun volverEditar() {
        if (estado.value.fase == FaseProgramacion.REVISION && estado.value.pendiente == null)
            mutable.value = estado.value.copy(fase = FaseProgramacion.FORMULARIO, revision = null, mensaje = null)
    }
    suspend fun confirmar() {
        val actual = estado.value
        if (actual.fase != FaseProgramacion.REVISION || actual.pendiente != null || actual.revision == null) return
        val solicitud = SolicitudRf22(nuevaOperacion(), actual.revision)
        try { archivo!!.guardar(solicitud) }
        catch (_: Exception) { mutable.value = actual.copy(mensaje = "No se pudo conservar la operación. No se envió el guardado."); return }
        enviar(solicitud)
    }
    private suspend fun enviar(solicitud: SolicitudRf22) {
        val peticion = ++version
        mutable.value = estado.value.copy(fase = FaseProgramacion.GUARDANDO, pendiente = solicitud, mensaje = null, permiteReenvio = false)
        try {
            val resultado = repository.confirmar(solicitud)
            if (vigente(peticion)) confirmado(solicitud, resultado)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (!vigente(peticion)) return
            val rechazosConfirmados = setOf("REVISION_CAMBIO", "ALERTA_EQUIVALENTE", "DATOS_INVALIDOS", "TIPO_NO_DISPONIBLE", "REFERENCIA_NO_DISPONIBLE", "VEHICULO_NO_DISPONIBLE", "KM_NO_REGISTRADO")
            if (e is ErrorRf22 && e.codigo in rechazosConfirmados) {
                archivo!!.borrar()
                val datosRevisados = e.revision?.let { revision ->
                    estado.value.antecedentes?.copy(fechaServidor = revision.bases.fechaBase,
                        kmActual = if (revision.configuracion.criterio == CriterioRf22.POR_TIEMPO) estado.value.antecedentes?.kmActual else revision.bases.kmBase,
                        idLectura = if (revision.configuracion.criterio == CriterioRf22.POR_TIEMPO) estado.value.antecedentes?.idLectura else revision.bases.idLectura,
                        referencia = revision.referencia)
                } ?: if (e.codigo == "REFERENCIA_NO_DISPONIBLE") estado.value.antecedentes?.copy(referencia = null) else estado.value.antecedentes
                mutable.value = estado.value.copy(fase = if (e.revision != null) FaseProgramacion.REVISION else FaseProgramacion.FORMULARIO,
                    pendiente = null, revision = e.revision, mensaje = e.message, antecedentes = datosRevisados,
                    referencia = if (e.revision != null) e.revision.referencia else if (e.codigo == "REFERENCIA_NO_DISPONIBLE") null else estado.value.referencia)
                if (e.codigo in setOf("TIPO_NO_DISPONIBLE", "VEHICULO_NO_DISPONIBLE")) actualizarCatalogo()
                if (e.codigo == "KM_NO_REGISTRADO") cargarAntecedentes()
            } else mutable.value = estado.value.copy(fase = FaseProgramacion.POR_COMPROBAR, mensaje = mensaje(e))
        }
    }
    suspend fun comprobar() {
        val actual = estado.value
        if (actual.fase != FaseProgramacion.POR_COMPROBAR || actual.pendiente == null) return
        val peticion = ++version
        mutable.value = actual.copy(fase = FaseProgramacion.GUARDANDO, mensaje = null)
        try {
            val consulta = repository.consultar(actual.pendiente.idOperacion)
            if (!vigente(peticion)) return
            if (consulta.estado == "CONFIRMADA" && consulta.resultado != null) confirmado(actual.pendiente, consulta.resultado)
            else mutable.value = actual.copy(permiteReenvio = consulta.estado == "NO_ENCONTRADA",
                mensaje = if (consulta.estado == "NO_ENCONTRADA") "Todavía no hay respuesta confirmada. Puedes reenviar exactamente la misma operación; no se generará otra clave."
                    else "La operación sigue en proceso. Vuelve a comprobar el resultado.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (vigente(peticion)) mutable.value = actual.copy(mensaje = mensaje(e)) }
    }
    suspend fun reenviarMismaOperacion() {
        val actual = estado.value
        if (actual.fase == FaseProgramacion.POR_COMPROBAR && actual.permiteReenvio) actual.pendiente?.let { enviar(it) }
    }
    private suspend fun actualizarCatalogo() {
        val anterior = estado.value
        val peticion = ++version
        mutable.value = anterior.copy(fase = FaseProgramacion.CARGANDO)
        try {
            val contexto = repository.contexto()
            if (!vigente(peticion)) return
            require(contexto.claveCuenta == anterior.contexto?.claveCuenta)
            val vehiculo = anterior.vehiculo?.takeIf { id -> contexto.vehiculos.any { it.idVehiculo == id } }
            val tipo = anterior.tipo?.takeIf { id -> contexto.tipos.any { it.idTipoMantenimiento == id } }
            mutable.value = anterior.copy(contexto = contexto, vehiculo = vehiculo, tipo = tipo,
                fase = if (contexto.vehiculos.isEmpty()) FaseProgramacion.SIN_VEHICULOS else FaseProgramacion.FORMULARIO,
                antecedentes = null, referencia = null, revision = null)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (vigente(peticion)) mutable.value = anterior.copy(antecedentes = null,
            mensaje = "No se pudo actualizar el catálogo. Consulta los datos nuevamente antes de programar.") }
    }
    private fun confirmado(solicitud: SolicitudRf22, resultado: ResultadoRf22) {
        require(resultado.idOperacion == solicitud.idOperacion && resultado.idVehiculo == solicitud.revision.configuracion.idVehiculo && resultado.eventoCreacionRegistrado)
        archivo!!.borrar()
        mutable.value = estado.value.copy(fase = FaseProgramacion.CONFIRMADO, pendiente = null, resultado = resultado, mensaje = null)
    }
    fun cerrar(borrar: Boolean) {
        cerrado = true; ++version; archivo?.cerrar(borrar)
    }
    private fun editable() = !cerrado && estado.value.fase == FaseProgramacion.FORMULARIO && estado.value.pendiente == null
    private fun vigente(peticion: Long) = !cerrado && peticion == version
    private fun mensaje(e: Exception) = (e as? ErrorRf22)?.message ?: "No se pudo completar la operación. Comprueba tu conexión y vuelve a intentarlo."
}
