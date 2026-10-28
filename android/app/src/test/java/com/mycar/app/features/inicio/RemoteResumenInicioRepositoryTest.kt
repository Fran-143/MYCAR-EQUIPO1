package com.mycar.app.features.inicio

import com.mycar.app.features.inicio.data.RemoteResumenInicioRepository
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.ListadoVehiculosRepository
import com.mycar.app.features.historial.domain.*
import com.mycar.app.features.mantenimiento.data.*
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RemoteResumenInicioRepositoryTest {
    private fun repositorio(historialFalla:Boolean=false)=RemoteResumenInicioRepository(
        object:ListadoVehiculosRepository {
            override suspend fun listar(filtro:String,busqueda:String):ListadoVehiculosDto {
                assertEquals("ACTIVO",filtro);assertEquals("",busqueda)
                return ListadoVehiculosDto(listOf(autoInicio()),ResumenEstadosDto(1,1,0),"ACTIVO","")
            }
        },object:HistorialRepository {
            override suspend fun vehiculos()=error("RF50 reutiliza lista de vehículos")
            override suspend fun historial(idVehiculo:Long):ResultadoHistorial {
                if(historialFalla) throw java.io.IOException()
                return ResultadoHistorial(VehiculoHistorial(idVehiculo,"A1B234","Toyota","Corolla","ACTIVO",true),
                    listOf(MantenimientoHistorial(1,"FRENOS","Frenos",null,LocalDate.of(2026,9,10),57000,"REALIZADO",null)))
            }
        },object:MantenimientoRepository {
            override suspend fun contexto()=ContextoRf17Dto("2026-10-27","America/Lima",listOf(
                VehiculoRf17Dto(1,"A1B234","Toyota","Corolla",true,9,58240,"2026-10-27T12:00:00Z",null)),emptyList(),true)
            override suspend fun lecturas(vehiculo:Long,fecha:String):List<LecturaRf17Dto> = error("No se consulta historial de km")
            override suspend fun registrar(vehiculo:Long,solicitud:SolicitudRf17Dto):ResultadoRf17Dto = error("Inicio no registra mantenimiento")
            override suspend fun consultar(idOperacion:String):EstadoRf17Dto = error("Inicio no consulta operación")
        })
    @Test fun usaLecturaActualYFechaRealSinSustituirlaPorKmHistorico()=runBlocking {
        val b=repositorio().bloques(autoInicio())
        assertEquals(58240,b.kilometraje);assertEquals(57000,b.ultimoMantenimiento?.kmServicio)
        assertEquals("2026-10-27T12:00:00Z",b.fechaLectura.toString())
    }
    @Test fun historialNoDisponibleNoSePresentaComoListaVaciaConfirmada()=runBlocking {
        val b=repositorio(true).bloques(autoInicio())
        assertEquals(DisponibilidadInicio.NO_DISPONIBLE,b.historial);assertNull(b.ultimoMantenimiento)
        assertEquals(58240,b.kilometraje)
    }
    @Test fun consultaSoloActivosSinInventarDatosNiEstados()=runBlocking {
        assertEquals(listOf(autoInicio()),repositorio().vehiculosActivos())
    }
}
