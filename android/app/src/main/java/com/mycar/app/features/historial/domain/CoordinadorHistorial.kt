package com.mycar.app.features.historial.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Las respuestas atrasadas se descartan; ningún error se convierte en una lista vacía válida. */
class CoordinadorHistorial(private val repository: HistorialRepository) {
    private val mutable = MutableStateFlow(EstadoHistorial())
    val estado = mutable.asStateFlow()
    private var version = 0L
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
        mutable.value = EstadoHistorial(FaseHistorial.CARGANDO_HISTORIAL, vehiculos, candidato)
        try {
            val resultado = repository.historial(id)
            if (peticion != version) return
            if (resultado.vehiculo.id != id || resultado.vehiculo.estado !in setOf("ACTIVO", "INACTIVO"))
                throw ErrorHistorial(CodigoErrorHistorial.DATOS, "No se pudo presentar el historial")
            val actualizados = vehiculos.map { if (it.id == id) resultado.vehiculo else it }
            val listo = EstadoHistorial(FaseHistorial.LISTO, actualizados, resultado.vehiculo,
                resultado.mantenimientos.toList())
            mutable.value = listo
            ultimoValido = listo
        } catch (ex: CancellationException) { throw ex
        } catch (ex: Exception) {
            if (peticion != version) return
            if (ex is ErrorHistorial && ex.codigo == CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE) {
                mutable.value = (ultimoValido ?: EstadoHistorial(FaseHistorial.SIN_SELECCION, vehiculos))
                    .copy(aviso = "El vehículo no está disponible. Elige otro de tus vehículos")
            } else fallo(ex, contexto = false)
        }
    }

    suspend fun reintentar() {
        val actual = mutable.value
        if (actual.fase == FaseHistorial.ERROR_VEHICULOS) cargar()
        else if (actual.fase == FaseHistorial.ERROR_HISTORIAL) actual.seleccionado?.id?.let { seleccionar(it) }
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
            mutable.value = mutable.value.copy(
                fase = if (contexto) FaseHistorial.ERROR_VEHICULOS else FaseHistorial.ERROR_HISTORIAL,
                mantenimientos = emptyList(), mensaje = mensaje, aviso = null)
        }
    }
}
