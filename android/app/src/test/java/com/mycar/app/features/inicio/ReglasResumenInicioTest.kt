package com.mycar.app.features.inicio

import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.vehiculos.data.VehiculoResumenDto
import com.mycar.app.features.historial.domain.MantenimientoHistorial
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

internal fun autoInicio(id:Long=1,principal:Boolean=true,estado:String="ACTIVO") =
    VehiculoResumenDto(id,"A1B234","Toyota","Corolla",2020,"Particular","Gris","Combustión","Gasolina",estado,principal,58240)
class ReglasResumenInicioTest {
    @Test fun seleccionInicialEsPrincipalAunqueNoSeaElPrimero() {
        assertEquals(2L,ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio(1,false),autoInicio(2)),null))
    }
    @Test fun conservaSeleccionSinModificarElPrincipal() {
        val lista=listOf(autoInicio(1),autoInicio(2,false))
        assertEquals(2L,ReglasResumenInicio.validarYSeleccionar(lista,2)); assertTrue(lista[0].principal);assertFalse(lista[1].principal)
    }
    @Test fun vacioEsVacioYNoUnVehiculoDeEjemplo() { assertNull(ReglasResumenInicio.validarYSeleccionar(emptyList(),8)) }
    @Test fun noInventaPrincipalCuandoFaltaOHayMasDeUno() {
        assertThrows(ErrorResumenInicio::class.java) { ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio(1,false)),null) }
        assertThrows(ErrorResumenInicio::class.java) { ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio(1),autoInicio(2)),null) }
    }
    @Test fun rechazaInactivosEIdentificadoresDuplicados() {
        assertThrows(ErrorResumenInicio::class.java) { ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio(estado="INACTIVO")),null) }
        assertThrows(ErrorResumenInicio::class.java) { ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio(),autoInicio(principal=false)),null) }
    }
    @Test fun seleccionQueDejoDeEstarActivaVuelveAlPrincipal() {
        assertEquals(1L,ReglasResumenInicio.validarYSeleccionar(listOf(autoInicio()),9))
    }
    @Test fun ultimoMantenimientoExcluyeAnuladosYDesempataPorId() {
        fun m(id:Long,estado:String,fecha:String)=MantenimientoHistorial(id,"FRENOS","Frenos",null,LocalDate.parse(fecha),50000,estado,null)
        val elegido=ReglasResumenInicio.ultimoRealizado(listOf(m(9,"ANULADO","2026-10-27"),m(2,"REALIZADO","2026-10-25"),m(3,"REALIZADO","2026-10-25")))
        assertEquals(3L,elegido?.id)
        assertNull(ReglasResumenInicio.ultimoRealizado(listOf(m(9,"ANULADO","2026-10-27"))))
    }
    @Test fun barraTieneExactamenteLasCuatroSeccionesSolicitadas() {
        assertEquals(listOf("Inicio","Vehículos","Mantenimientos","Alertas"),SeccionMyCar.entries.map { it.titulo })
    }
    @Test fun atrasSecundariaVuelveASuOrigenSinDuplicarPestanas() {
        val origen=NavegacionInicio().abrir(SeccionMyCar.VEHICULOS)
        assertEquals(origen,origen.abrir(SecundariaMyCar.PERFIL).atras())
        assertEquals(NavegacionInicio(),origen.atras())
        assertEquals(origen,origen.abrir(SeccionMyCar.VEHICULOS))
    }
}
