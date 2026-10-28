package com.mycar.app.features.sesion

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mycar.app.features.vehiculos.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AlmacenamientoSesionVehiculosTest {
    @Test fun aislamientoPorCuentaYCierreEliminaSoloLaCuentaActual() {
        val contexto=InstrumentationRegistry.getInstrumentation().targetContext
        val origen="https://instrumentacion.invalid/${UUID.randomUUID()}/"
        val a=ArchivoSolicitudRegistroPendiente(contexto,origen,71)
        val b=ArchivoSolicitudRegistroPendiente(contexto,origen,72)
        val solicitud=SolicitudRegistroVehiculoDto(UUID.randomUUID().toString(),"Toyota","Corolla","PARTICULAR",
            "A1B234",2020,"Gris",1,1,50000,null,null,null,null,null,null,null)
        val ciclo=PendientesSesionVehiculos(a,a::borrarAlCerrarSesion)
        try {
            a.guardar(solicitud)
            assertNull(b.cargar())
            b.guardar(solicitud.copy(idOperacion=UUID.randomUUID().toString()))
            ciclo.cerrar(true)
            assertNull(a.cargar()); assertNotNull(b.cargar())
            assertThrows(IOException::class.java) { ciclo.guardar(solicitud) }
            assertNull(a.cargar())
        } finally { a.borrarAlCerrarSesion(); b.borrarAlCerrarSesion() }
    }
}
