package com.mycar.app.features.notificaciones

import com.mycar.app.features.notificaciones.domain.BandejaNotificaciones
import com.mycar.app.features.notificaciones.domain.NotificacionItem
import com.mycar.app.features.notificaciones.domain.NotificacionesRepository
import com.mycar.app.features.notificaciones.domain.ResultadoLectura
import com.mycar.app.features.notificaciones.viewmodel.CategoriaNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.FaseNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.FiltroLecturaNotificaciones
import com.mycar.app.features.notificaciones.viewmodel.NotificacionesViewModel
import com.mycar.app.features.notificaciones.viewmodel.PestanaNotificaciones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificacionesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun filtrosConservanContadoresAlCambiarPestanaYCategoria() = runTest {
        val repo = FakeRepo()
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.estado.value.contadorPendientesGlobal)
        assertEquals(1, vm.estado.value.contadorNoLeidasGlobal)
        vm.seleccionarFiltroLectura(FiltroLecturaNotificaciones.NO_LEIDAS)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("NO_LEIDAS", repo.ultimoFiltroLectura)
        vm.seleccionarCategoria(CategoriaNotificaciones.SOAT)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("SOAT", repo.ultimaCategoria)
        assertEquals("NO_LEIDAS", repo.ultimoFiltroLectura)
        assertEquals(2, vm.estado.value.contadorPendientesGlobal)
        assertEquals(1, vm.estado.value.contadorNoLeidasGlobal)
        vm.seleccionarPestana(PestanaNotificaciones.HISTORIAL)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("HISTORIAL", repo.ultimaPestana)
        assertEquals("SOAT", repo.ultimaCategoria)
        assertEquals("NO_LEIDAS", repo.ultimoFiltroLectura)
        vm.seleccionarCategoria(CategoriaNotificaciones.TODAS)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("NO_LEIDAS", repo.ultimoFiltroLectura)
    }

    @Test
    fun marcarLeidaNoCambiaGestionAAtendida() = runTest {
        val repo = FakeRepo()
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        var destino: ResultadoLectura? = null
        val mant = repo.items.first { it.categoria == "MANTENIMIENTO" }
        vm.abrir(mant, onDestino = { destino = it }, onDestinoInvalido = {})
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(destino?.leida == true)
        assertEquals("PENDIENTE", destino?.estadoGestion)
    }

    @Test
    fun soatNoAbreDetalleRf24() = runTest {
        val repo = FakeRepo()
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        var destinoOk = false
        var msg: String? = null
        vm.abrir(
            repo.items.first { it.categoria == "SOAT" },
            onDestino = { destinoOk = true },
            onDestinoInvalido = { msg = it }
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(!destinoOk)
        assertTrue(msg?.contains("SOAT") == true)
    }

    @Test
    fun listaVaciaMuestraEstadoVacio() = runTest {
        val repo = FakeRepo(items = emptyList())
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FaseNotificaciones.VACIO, vm.estado.value.fase)
    }

    private class FakeRepo(
        val items: List<NotificacionItem> = listOf(
            NotificacionItem(
                1, "SOAT", "PENDIENTE", false, "SOAT por vencer", "AVB456",
                "DETALLE_ALERTA", null, null, "2026-11-08T00:00:00.000Z"
            ),
            NotificacionItem(
                2, "MANTENIMIENTO", "PENDIENTE", true, "Aceite", "AUD146",
                "DETALLE_ALERTA", 21, 8, "2026-11-08T00:00:00.000Z"
            )
        )
    ) : NotificacionesRepository {
        var ultimaPestana: String = "PENDIENTES"
        var ultimaCategoria: String = "TODAS"
        var ultimoFiltroLectura: String = "TODAS"
        override suspend fun consultarBandeja(
            pestana: String,
            categoria: String,
            filtroLectura: String
        ): BandejaNotificaciones {
            ultimaPestana = pestana
            ultimaCategoria = categoria
            ultimoFiltroLectura = filtroLectura
            var filtrados = if (categoria == "TODAS") items else items.filter { it.categoria == categoria }
            filtrados = when (filtroLectura) {
                "NO_LEIDAS" -> filtrados.filter { !it.leida }
                "LEIDAS" -> filtrados.filter { it.leida }
                else -> filtrados
            }
            return BandejaNotificaciones(pestana, categoria, filtroLectura, 2, 1, filtrados)
        }
        override suspend fun marcarLeida(idNotificacion: Long): ResultadoLectura {
            val n = items.first { it.id == idNotificacion }
            return ResultadoLectura(n.id, true, "PENDIENTE", n.destino, n.idAlerta, n.idVehiculo)
        }
    }
}
