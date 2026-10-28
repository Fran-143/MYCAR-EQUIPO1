package com.mycar.app.features.alertas.programacion

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.mycar.app.features.alertas.programacion.data.ArchivoProgramacionPendiente
import com.mycar.app.features.alertas.programacion.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ArchivoProgramacionTest {
    @Test fun archivoRealPorCuentaYGuardaContraEscrituraPosteriorAlCierre() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val sufijo = UUID.randomUUID().toString()
        val a = ArchivoProgramacionPendiente(contexto, "FIXTURE-A-$sufijo")
        val b = ArchivoProgramacionPendiente(contexto, "FIXTURE-B-$sufijo")
        val r = RevisionRf22(ConfiguracionRf22(1, 1, null, CriterioRf22.POR_TIEMPO, UnidadRf22.MESES, 1, null, null),
            BasesRf22("2026-01-31", null, null), ObjetivosRf22("2026-02-28", null, "PROXIMA"), null)
        val s = SolicitudRf22(UUID.randomUUID().toString(), r)
        try {
            a.guardar(s); assertEquals(s, a.leer()); assertNull(b.leer())
            a.cerrar(true)
            assertThrows(IllegalStateException::class.java) { a.guardar(s) }
            val reabierto = ArchivoProgramacionPendiente(contexto, "FIXTURE-A-$sufijo")
            try { assertNull(reabierto.leer()) } finally { reabierto.cerrar(true) }
        } finally { a.cerrar(true); b.cerrar(true) }
    }
}
