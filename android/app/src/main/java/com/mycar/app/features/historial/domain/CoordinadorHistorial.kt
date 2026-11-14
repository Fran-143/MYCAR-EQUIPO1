package com.mycar.app.features.historial.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fuente opcional de lecturas RF15; si es null la pestaña KM queda vacía informativa. */
fun interface FuenteLecturasKilometraje {
    suspend fun listar(idVehiculo: Long): List<LecturaKilometrajeHistorial>
}

/** Las respuestas atrasadas se descartan; ningún error se convierte en una lista vacía válida. */
class CoordinadorHistorial(
    private val repository: HistorialRepository,
    private val lecturasKm: FuenteLecturasKilometraje? = null
) {
    private val mutable = MutableStateFlow(EstadoHistorial())
    val estado = mutable.asStateFlow()
    private var version = 0L
    private var versionKm = 0L
    private var ultimoValido: EstadoHistorial? = null

    suspend fun cargar(idPreferido: Long? = mutable.value.seleccionado?.id) {
        val peticion = ++version
        ultimoValido = null
        mutable.value = EstadoHistorial(fase = FaseHistorial.CARGANDO_VEHICULOS)
        try {
            val vehiculos = repository.vehiculos().toList()
            if (peticion != version) return
            if (vehiculos.any { it.id <= 0 || it.estado !in setOf("ACTIVO", "INACTIVO") }
                || vehiculos.distinctBy { it.id }.size != vehiculos.size
                || vehiculos.count { it.principal } > 1) throw ErrorHistorial(CodigoErrorHistorial.DATOS, "No se pudieron presentar tus vehículos")
            if (vehiculos.isEmpty()) {
                mutable.value = EstadoHistorial(fase = FaseHistorial.SIN_VEHICULOS)
                return
            }
            mutable.value = EstadoHistorial(fase = FaseHistorial.SIN_SELECCION, vehiculos = vehiculos)
            // Incluye principal inactivo: consultable en historial aunque Inicio solo muestre activos.
            val inicial = vehiculos.firstOrNull { it.id == idPreferido } ?: vehiculos.firstOrNull { it.principal }
            if (inicial != null) seleccionar(inicial.id)
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (peticion == version) fallo(ex, contexto = true)
        }
    }

    suspend fun seleccionar(id: Long) {
        val candidato = mutable.value.vehiculos.firstOrNull { it.id == id }
        if (candidato == null) {
            mutable.value = mutable.value.copy(aviso = "El vehículo no está disponible")
            return
        }
        val peticion = ++version
        val vehiculos = mutable.value.vehiculos
        mutable.value = EstadoHistorial(
            fase = FaseHistorial.CARGANDO_HISTORIAL,
            vehiculos = vehiculos,
            seleccionado = candidato,
            faseKm = FaseKilometrajeHistorial.IDLE
        )
        try {
            val resultado = repository.historial(id)
            if (peticion != version) return
            if (resultado.vehiculo.id != id || resultado.vehiculo.estado !in setOf("ACTIVO", "INACTIVO"))
                throw ErrorHistorial(CodigoErrorHistorial.DATOS, "No se pudo presentar el historial")
            val actualizados = vehiculos.map { if (it.id == id) resultado.vehiculo else it }
            val listo = EstadoHistorial(
                fase = FaseHistorial.LISTO,
                vehiculos = actualizados,
                seleccionado = resultado.vehiculo,
                mantenimientos = resultado.mantenimientos.toList(),
                filtroCodigoTipo = null,
                ordenRecientes = true,
                faseKm = FaseKilometrajeHistorial.IDLE
            )
            mutable.value = listo
            ultimoValido = listo
            cargarKilometraje(id)
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (peticion != version) return
            if (ex is ErrorHistorial && ex.codigo == CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE) {
                mutable.value = (ultimoValido ?: EstadoHistorial(FaseHistorial.SIN_SELECCION, vehiculos))
                    .copy(aviso = "El vehículo no está disponible. Elige otro de tus vehículos")
            } else fallo(ex, contexto = false)
        }
    }

    fun filtrarTipo(codigoTipo: String?) {
        val s = mutable.value
        if (s.fase != FaseHistorial.LISTO) return
        val codigo = codigoTipo?.takeIf { it.isNotBlank() }
        if (codigo != null && s.tiposFiltro.none { it.first == codigo }) return
        mutable.value = s.copy(filtroCodigoTipo = codigo, aviso = null)
    }

    fun ordenar(recientes: Boolean) {
        val s = mutable.value
        if (s.fase != FaseHistorial.LISTO) return
        val lecturas = if (s.faseKm == FaseKilometrajeHistorial.LISTO) {
            ordenarLecturas(s.lecturasKm, recientes)
        } else s.lecturasKm
        mutable.value = s.copy(ordenRecientes = recientes, aviso = null, lecturasKm = lecturas)
    }

    private fun ordenarLecturas(
        lista: List<LecturaKilometrajeHistorial>,
        recientes: Boolean
    ): List<LecturaKilometrajeHistorial> {
        val cmp = compareBy<LecturaKilometrajeHistorial>(
            { it.fechaUtc.orEmpty() },
            { it.id }
        )
        return if (recientes) lista.sortedWith(cmp.reversed()) else lista.sortedWith(cmp)
    }

    fun limpiarFiltros() {
        val s = mutable.value
        if (s.fase != FaseHistorial.LISTO) return
        mutable.value = s.copy(filtroCodigoTipo = null, ordenRecientes = true, aviso = null)
    }

    suspend fun cargarKilometraje(idVehiculo: Long? = mutable.value.seleccionado?.id) {
        val id = idVehiculo ?: return
        val fuente = lecturasKm
        if (fuente == null) {
            mutable.value = mutable.value.copy(
                faseKm = FaseKilometrajeHistorial.VACIO,
                lecturasKm = emptyList(),
                mensajeKm = "No hay lecturas de kilometraje registradas."
            )
            return
        }
        val gen = ++versionKm
        mutable.value = mutable.value.copy(
            faseKm = FaseKilometrajeHistorial.CARGANDO,
            mensajeKm = null
        )
        try {
            val lista = ordenarLecturas(fuente.listar(id), mutable.value.ordenRecientes)
            if (gen != versionKm || mutable.value.seleccionado?.id != id) return
            mutable.value = mutable.value.copy(
                faseKm = if (lista.isEmpty()) FaseKilometrajeHistorial.VACIO else FaseKilometrajeHistorial.LISTO,
                lecturasKm = lista,
                mensajeKm = if (lista.isEmpty()) "No hay lecturas de kilometraje registradas." else null
            )
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (gen != versionKm || mutable.value.seleccionado?.id != id) return
            mutable.value = mutable.value.copy(
                faseKm = FaseKilometrajeHistorial.ERROR,
                mensajeKm = ex.message ?: "No se pudo cargar el historial de kilometraje."
            )
        }
    }

    suspend fun reintentar() {
        val actual = mutable.value
        when {
            actual.fase == FaseHistorial.ERROR_VEHICULOS -> cargar()
            actual.fase == FaseHistorial.ERROR_HISTORIAL -> actual.seleccionado?.id?.let { seleccionar(it) }
            actual.faseKm == FaseKilometrajeHistorial.ERROR -> cargarKilometraje()
        }
    }

    private fun fallo(ex: Exception, contexto: Boolean) {
        if (ex is ErrorHistorial && ex.codigo == CodigoErrorHistorial.SESION) {
            ++version
            ultimoValido = null
            mutable.value = EstadoHistorial()
        } else {
            val mensaje = when ((ex as? ErrorHistorial)?.codigo) {
                CodigoErrorHistorial.DATOS -> "No se pudo presentar el historial. Inténtalo nuevamente"
                CodigoErrorHistorial.INTEGRACION -> "El historial todavía no está disponible. Inténtalo más tarde"
                else -> "No se pudo cargar el historial. Comprueba tu conexión e inténtalo nuevamente"
            }
            val km = mutable.value
            mutable.value = mutable.value.copy(
                fase = if (contexto) FaseHistorial.ERROR_VEHICULOS else FaseHistorial.ERROR_HISTORIAL,
                mantenimientos = emptyList(),
                mensaje = mensaje,
                aviso = null,
                faseKm = km.faseKm,
                lecturasKm = km.lecturasKm,
                mensajeKm = km.mensajeKm
            )
        }
    }
}
