package com.mycar.app.features.inicio

import com.mycar.app.features.inicio.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CoordinadorInicioTest {
    @Test fun cargaPrincipalYElKilometrajeNoSeTomaDelServicioHistorico()=runBlocking {
        val r=object:ResumenInicioRepository {
            override suspend fun vehiculosActivos()=listOf(autoInicio())
            override suspend fun bloques(vehiculo:com.mycar.app.features.vehiculos.data.VehiculoResumenDto)=BloquesInicio(58240)
        }
        val c=CoordinadorInicio(r,this); c.cargar().join()
        assertEquals(FaseInicio.LISTO,c.estado.value.fase);assertEquals(58240,c.estado.value.bloques?.kilometraje)
    }
    @Test fun inconsistenciaNoEligeUnAutoArbitrario()=runBlocking {
        val r=object:ResumenInicioRepository {
            override suspend fun vehiculosActivos()=listOf(autoInicio(principal=false))
            override suspend fun bloques(vehiculo:com.mycar.app.features.vehiculos.data.VehiculoResumenDto):BloquesInicio=error("No consultar")
        }
        val c=CoordinadorInicio(r,this);c.cargar().join()
        assertEquals(FaseInicio.INCONSISTENTE,c.estado.value.fase);assertNull(c.estado.value.seleccionado)
    }
    @Test fun respuestaViejaNoCambiaElVehiculoElegido()=runBlocking {
        val entrada=CompletableDeferred<Unit>();val liberar=CompletableDeferred<Unit>()
        val r=object:ResumenInicioRepository {
            override suspend fun vehiculosActivos()=listOf(autoInicio(1),autoInicio(2,false))
            override suspend fun bloques(vehiculo:com.mycar.app.features.vehiculos.data.VehiculoResumenDto):BloquesInicio {
                if(vehiculo.idVehiculo==1L) withContext(NonCancellable) { entrada.complete(Unit);liberar.await() }
                return BloquesInicio(if(vehiculo.idVehiculo==1L) 111 else 222)
            }
        }
        val c=CoordinadorInicio(r,this);val primera=c.cargar();entrada.await()
        c.seleccionar(2)!!.join();liberar.complete(Unit);primera.join()
        assertEquals(2L,c.estado.value.seleccionado);assertEquals(222,c.estado.value.bloques?.kilometraje)
    }
    @Test fun falloDeConsultaNoSeConfundeConCuentaSinVehiculos()=runBlocking {
        val r=object:ResumenInicioRepository {
            override suspend fun vehiculosActivos():List<com.mycar.app.features.vehiculos.data.VehiculoResumenDto> = throw java.io.IOException()
            override suspend fun bloques(vehiculo:com.mycar.app.features.vehiculos.data.VehiculoResumenDto)=BloquesInicio(null)
        }
        val c=CoordinadorInicio(r,this);c.cargar().join();assertEquals(FaseInicio.ERROR,c.estado.value.fase)
    }
    @Test fun sesionRevocadaNoConservaResumenPrivado()=runBlocking {
        val r=object:ResumenInicioRepository {
            override suspend fun vehiculosActivos():List<com.mycar.app.features.vehiculos.data.VehiculoResumenDto> = throw ErrorResumenInicio(sesion=true)
            override suspend fun bloques(vehiculo:com.mycar.app.features.vehiculos.data.VehiculoResumenDto)=BloquesInicio(null)
        }
        val c=CoordinadorInicio(r,this);c.cargar().join()
        assertEquals(FaseInicio.SIN_SESION,c.estado.value.fase);assertTrue(c.estado.value.vehiculos.isEmpty())
    }
}
