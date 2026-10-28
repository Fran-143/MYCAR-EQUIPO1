package com.mycar.app.features.inicio.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un coordinador por ciclo de cuenta. Respuestas viejas nunca reemplazan el vehículo seleccionado. */
class CoordinadorInicio(private val repository: ResumenInicioRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(EstadoResumenInicio(fase=FaseInicio.CARGANDO))
    val estado = mutable.asStateFlow()
    private var revision=0L
    private var trabajo: Job?=null
    fun cargar(): Job {
        val turno=++revision; trabajo?.cancel()
        val anterior=mutable.value.seleccionado
        mutable.value=mutable.value.copy(fase=FaseInicio.CARGANDO,mensaje=null,bloques=null)
        return scope.launch {
            try {
                val lista=repository.vehiculosActivos()
                val elegido=ReglasResumenInicio.validarYSeleccionar(lista,anterior)
                if(turno!=revision) return@launch
                mutable.value=EstadoResumenInicio(if(elegido==null) FaseInicio.SIN_VEHICULOS else FaseInicio.LISTO,lista,elegido)
                if(elegido!=null) cargarBloques(turno,lista.single { it.idVehiculo==elegido })
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(turno==revision) publicarError(e) }
        }.also { trabajo=it }
    }
    fun seleccionar(id: Long): Job? {
        val v=mutable.value.vehiculos.firstOrNull { it.idVehiculo==id } ?: return null
        if(mutable.value.seleccionado==id) return null
        val turno=++revision; trabajo?.cancel()
        mutable.value=mutable.value.copy(seleccionado=id,bloques=null,mensaje=null,fase=FaseInicio.LISTO)
        return scope.launch { cargarBloques(turno,v) }.also { trabajo=it }
    }
    private suspend fun cargarBloques(turno: Long, v: com.mycar.app.features.vehiculos.data.VehiculoResumenDto) {
        try {
            val b=repository.bloques(v)
            if(turno==revision && mutable.value.seleccionado==v.idVehiculo) mutable.value=mutable.value.copy(bloques=b)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            if(turno==revision) {
                if(e is ErrorResumenInicio && e.sesion) publicarError(e)
                else mutable.value=mutable.value.copy(bloques=BloquesInicio(v.kilometrajeActual,
                    historial=DisponibilidadInicio.NO_DISPONIBLE,lectura=DisponibilidadInicio.NO_DISPONIBLE))
            }
        }
    }
    private fun publicarError(e: Exception) {
        val error=e as? ErrorResumenInicio
        mutable.value=EstadoResumenInicio(fase=when {
            error?.sesion==true -> FaseInicio.SIN_SESION
            error?.inconsistente==true -> FaseInicio.INCONSISTENTE
            else -> FaseInicio.ERROR
        },mensaje=when {
            error?.sesion==true -> "Inicia sesión para consultar tu resumen."
            error?.inconsistente==true -> "No se pudo identificar un único vehículo principal activo. Revisa tus vehículos."
            else -> "No se pudo cargar tu resumen. Inténtalo de nuevo."
        })
    }
}
