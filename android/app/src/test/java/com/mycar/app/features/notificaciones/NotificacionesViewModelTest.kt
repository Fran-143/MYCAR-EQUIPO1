package com.mycar.app.features.notificaciones

import com.mycar.app.features.notificaciones.domain.BandejaNotificaciones
import com.mycar.app.features.notificaciones.domain.DetalleNotificacion
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
        assertEquals(1, vm.estado.value.contadorLeidasGlobal)
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
    fun abrirDetalleMarcaLeidaYConservaGestionPendiente() = runTest {
        val repo = FakeRepo()
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        val mant = repo.items.first { it.categoria == "MANTENIMIENTO" }
        vm.abrirDetalle(mant)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, vm.estado.value.detalle?.leida)
        assertEquals("PENDIENTE", vm.estado.value.detalle?.estadoGestion)
        assertTrue(repo.marcarLeidaLlamado)
    }

    @Test
    fun accionDetalleSoatNavegaConDestino() = runTest {
        val repo = FakeRepo()
        val vm = NotificacionesViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        val soat = repo.items.first { it.categoria == "SOAT" }
        vm.abrirDetalle(soat)
        dispatcher.scheduler.advanceUntilIdle()
        var destinoOk = false
        var msg: String? = null
        vm.ejecutarAccionDetalle(
            onDestino = { destinoOk = true },
            onDestinoInvalido = { msg = it }
        )
        assertTrue(destinoOk)
        assertEquals(null, msg)
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
                "DETALLE_ALERTA", null, 8, "2026-11-08T00:00:00.000Z",
                placa = "AVB456", vehiculoEtiqueta = "Toyota Corolla"
            ),
            NotificacionItem(
                2, "MANTENIMIENTO", "PENDIENTE", true, "Aceite", "AUD146",
                "DETALLE_ALERTA", 21, 8, "2026-11-08T00:00:00.000Z",
                placa = "AUD146", vehiculoEtiqueta = "Hyundai Accent"
            )
        )
    ) : NotificacionesRepository {
        var ultimaPestana: String = "PENDIENTES"
        var ultimaCategoria: String = "TODAS"
        var ultimoFiltroLectura: String = "TODAS"
        var marcarLeidaLlamado = false
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
            return BandejaNotificaciones(pestana, categoria, filtroLectura, 2, 1, 1, filtrados)
        }
        override suspend fun consultarDetalle(idNotificacion: Long): DetalleNotificacion {
            val n = items.first { it.id == idNotificacion }
            return DetalleNotificacion(
                id = n.id,
                categoria = n.categoria,
                estadoGestion = n.estadoGestion,
                leida = n.leida,
                titulo = n.titulo,
                cuerpo = n.cuerpo,
                destino = n.destino,
                idAlerta = n.idAlerta,
                idVehiculo = n.idVehiculo,
                fechaEmisionUtc = n.fechaEmisionUtc,
                nombreEvento = n.titulo,
                vehiculoEtiqueta = n.vehiculoEtiqueta,
                estadoActual = "PROXIMA",
                fechaObjetivo = null,
                diasRestantes = null,
                diasTranscurridos = null,
                kmRestantes = null,
                kmExcedidos = null,
                kmObjetivo = null,
                frecuenciaKm = null,
                ultimaActualizacionKm = null,
                recordatorioCorrespondienteKm = null,
                textoTemporal = null,
                textoKilometraje = null,
                accionDisponible = if (n.categoria == "SOAT") "Ver SOAT" else "Ver alerta",
                accionHabilitada = true
            )
        }
        override suspend fun marcarLeida(idNotificacion: Long): ResultadoLectura {
            marcarLeidaLlamado = true
            val n = items.first { it.id == idNotificacion }
            return ResultadoLectura(n.id, true, "PENDIENTE", n.destino, n.idAlerta, n.idVehiculo)
        }
    }
}
