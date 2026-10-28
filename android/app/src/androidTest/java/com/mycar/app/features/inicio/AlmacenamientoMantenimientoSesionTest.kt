package com.mycar.app.features.inicio

import androidx.test.platform.app.InstrumentationRegistry
import com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente
import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AlmacenamientoMantenimientoSesionTest {
    @Test fun logoutImpideQueUnCallbackRecreePendientesDeMantenimiento() {
        val contexto=InstrumentationRegistry.getInstrumentation().targetContext
        val clave="instrumentacion-${UUID.randomUUID()}"
        val a=ArchivoRegistroPendiente(contexto,clave)
        val b=BorradorMantenimiento(idVehiculo=999,idOperacion=UUID.randomUUID().toString(),envioPendiente=true)
        try {
            a.guardar(b);assertNotNull(a.leer());a.cerrar(true)
            assertThrows(java.io.IOException::class.java) { a.guardar(b) }
            val siguiente=ArchivoRegistroPendiente(contexto,clave)
            assertNull(siguiente.leer());siguiente.cerrar(true)
        } finally { a.cerrar(true) }
    }
}
