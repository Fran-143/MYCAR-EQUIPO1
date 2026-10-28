package com.mycar.app.features.sesion

import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.SolicitudRegistroPendienteStore
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PendientesSesionVehiculosTest {
    private class Memoria : SolicitudRegistroPendienteStore {
        var lecturas=0
        override fun cargar(): SolicitudRegistroVehiculoDto? { lecturas++; return null }
        override fun guardar(solicitud: SolicitudRegistroVehiculoDto) { }
        override fun eliminar(idOperacion: String) { }
    }
    @Test fun caducidadConservaArchivoPeroCierraAccesoAlCicloAnterior() {
        val a=Memoria(); var borrados=0
        val p=PendientesSesionVehiculos(a) { borrados++ }
        assertNull(p.cargar()); p.cerrar(false)
        assertThrows(IOException::class.java) { p.cargar() }
        assertThrows(IOException::class.java) { p.eliminar("op") }
        assertEquals(0,borrados); assertEquals(1,a.lecturas)
    }
    @Test fun unaEscrituraTardiaNoPuedeRecrearElArchivoTrasLogout() {
        val archivo=com.mycar.app.features.vehiculos.PendientesPrueba()
        val solicitud=com.mycar.app.features.vehiculos.DatosPruebaRf08.solicitud()
        val p=PendientesSesionVehiculos(archivo) { archivo.actual=null }
        p.guardar(solicitud); p.cerrar(true)
        assertNull(archivo.actual)
        assertThrows(IOException::class.java) { p.guardar(solicitud) }
        assertNull(archivo.actual)
    }
    @Test fun cierreExplicitoBorraYSigueBloqueadoAunqueFalleLaLimpieza() {
        val p=PendientesSesionVehiculos(Memoria()) { throw IOException("Disco") }
        assertThrows(IOException::class.java) { p.cerrar(true) }
        assertThrows(IOException::class.java) { p.cargar() }
    }
}
