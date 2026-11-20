package com.mycar.app.features.inicio.data

import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.historial.domain.*
import com.mycar.app.features.mantenimiento.domain.*
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.features.vehiculos.data.VehiculoResumenDto
import kotlinx.coroutines.*
import java.time.Instant

/** Solo lecturas mediante los contratos existentes. No programa alertas ni modifica estados. */
class RemoteResumenInicioRepository(
    private val vehiculos: ListadoVehiculosRepository,
    private val historial: HistorialRepository,
    private val mantenimiento: MantenimientoRepository,
    private val alertas: AlertasRepository? = null
) : ResumenInicioRepository {
    override suspend fun vehiculosActivos(): List<VehiculoResumenDto> = try {
        vehiculos.listar("ACTIVO", "").vehiculos
    } catch(e: CancellationException) { throw e }
    catch(e: FalloRegistroVehiculo) { throw ErrorResumenInicio(sesion=e.estadoHttp==401) }

    override suspend fun vehiculosTodos(): List<VehiculoResumenDto> = try {
        vehiculos.listar("TODOS", "").vehiculos
    } catch(e: CancellationException) { throw e }
    catch(e: FalloRegistroVehiculo) { throw ErrorResumenInicio(sesion=e.estadoHttp==401) }

    override suspend fun bloques(vehiculo: VehiculoResumenDto): BloquesInicio = coroutineScope {
        val lectura=async {
            try {
                val actual=mantenimiento.contexto().vehiculos.singleOrNull { it.idVehiculo==vehiculo.idVehiculo }
                    ?: return@async null
                actual.kmActual?.takeIf { it>0 }?.let { km -> km to actual.fechaLecturaActual?.let(Instant::parse) }
            } catch(e: CancellationException) { throw e }
            catch(e: ErrorMantenimientoRemoto) {
                if(e.codigo in setOf("SESION_REQUERIDA","SESION_NO_VALIDA","SESION_INVALIDA")) throw ErrorResumenInicio(sesion=true)
                null
            } catch(_: Exception) { null }
        }
        val servicios=async {
            try {
                val resultado=historial.historial(vehiculo.idVehiculo)
                if(resultado.vehiculo.id!=vehiculo.idVehiculo) throw ErrorResumenInicio(inconsistente=true)
                ReglasResumenInicio.ultimoRealizado(resultado.mantenimientos) to DisponibilidadInicio.DISPONIBLE
            } catch(e: CancellationException) { throw e }
            catch(e: ErrorHistorial) {
                if(e.codigo==CodigoErrorHistorial.SESION) throw ErrorResumenInicio(sesion=true)
                null to DisponibilidadInicio.NO_DISPONIBLE
            } catch(_: Exception) { null to DisponibilidadInicio.NO_DISPONIBLE }
        }
        val alertasAsync = async {
            val repo = alertas ?: return@async Triple(emptyList<ResumenAlertaInicio>(), 0, DisponibilidadInicio.NO_DISPONIBLE)
            try {
                val (total, lista) = repo.listar(vehiculo.idVehiculo)
                val resumen = lista.take(3).map {
                    ResumenAlertaInicio(
                        id = it.id,
                        titulo = it.tipo.ifBlank { it.codigoTipo }.ifBlank { "Alerta" },
                        estado = it.estado
                    )
                }
                Triple(resumen, total, DisponibilidadInicio.DISPONIBLE)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                Triple(emptyList(), 0, DisponibilidadInicio.NO_DISPONIBLE)
            }
        }
        val km=lectura.await(); val ultimo=servicios.await(); val alertasRes=alertasAsync.await()
        BloquesInicio(
            kilometraje = km?.first ?: vehiculo.kilometrajeActual,
            fechaLectura = km?.second,
            ultimoMantenimiento = ultimo.first,
            historial = ultimo.second,
            lectura = if(km!=null) DisponibilidadInicio.DISPONIBLE else DisponibilidadInicio.NO_DISPONIBLE,
            alertas = alertasRes.first,
            alertasDisponibilidad = alertasRes.third,
            totalAlertas = alertasRes.second
        )
    }
}
