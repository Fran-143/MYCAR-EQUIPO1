package com.mycar.app.features.mantenimiento

import com.mycar.app.features.mantenimiento.data.VehiculoRf17Dto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lógica de sugerencia de aceite (sin inventar datos). */
class MantenimientoAceiteVehiculoTest {
    @Test fun aceiteRegistradoResuelveEtiqueta() {
        val v = VehiculoRf17Dto(1, "ABC", "M", "X", true, 1, 100, null, "5W30 Sintético")
        assertEquals("5W30 Sintético", v.aceiteRegistrado)
    }

    @Test fun sinAceiteNoSugiere() {
        val v = VehiculoRf17Dto(1, "ABC", "M", "X", true, 1, 100, null, null)
        assertNull(v.aceiteRegistrado)
    }
}
