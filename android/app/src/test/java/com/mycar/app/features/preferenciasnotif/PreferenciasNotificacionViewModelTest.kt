package com.mycar.app.features.preferenciasnotif

import com.mycar.app.features.preferenciasnotif.domain.ErrorPreferencias
import com.mycar.app.features.preferenciasnotif.domain.PermisoDispositivo
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasExternas
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository
import com.mycar.app.features.preferenciasnotif.viewmodel.PreferenciasNotificacionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferenciasNotificacionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeRepo
    private var permisoOs = PermisoDispositivo.NO_SOLICITADO
    private var permisoTrasSolicitud = PermisoDispositivo.CONCEDIDO
    private var solicitudes = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo()
        permisoOs = PermisoDispositivo.NO_SOLICITADO
        permisoTrasSolicitud = PermisoDispositivo.CONCEDIDO
        solicitudes = 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = PreferenciasNotificacionViewModel(
        repository = repo,
        consultarPermisoOs = { permisoOs },
        solicitarPermisoOs = {
            solicitudes++
            permisoTrasSolicitud
        }
    )

    @Test
    fun ea01_todasOffNoSolicitaPermiso() = runTest {
        repo.actual = PreferenciasExternas(true, true, true, PermisoDispositivo.NO_SOLICITADO)
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setMantenimiento(false)
        vm.setSoat(false)
        vm.setKilometraje(false)
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, solicitudes)
        assertFalse(repo.ultimoGuardado!!.mantenimiento)
        assertFalse(repo.ultimoGuardado!!.soat)
        assertFalse(repo.ultimoGuardado!!.kilometraje)
    }

    @Test
    fun ea02_permisoDenegadoGuardaYAvisa() = runTest {
        repo.actual = PreferenciasExternas(false, false, false, PermisoDispositivo.NO_SOLICITADO)
        permisoOs = PermisoDispositivo.DENEGADO
        permisoTrasSolicitud = PermisoDispositivo.DENEGADO
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setMantenimiento(true)
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, solicitudes)
        assertEquals(PermisoDispositivo.DENEGADO, repo.ultimoGuardado!!.permisoDispositivo)
        assertTrue(vm.estado.value.avisoPermiso!!.contains("Activa los permisos"))
    }

    @Test
    fun ea03_permisoYaConcedidoNoSolicita() = runTest {
        repo.actual = PreferenciasExternas(false, false, false, PermisoDispositivo.CONCEDIDO)
        permisoOs = PermisoDispositivo.CONCEDIDO
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setKilometraje(true)
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, solicitudes)
        assertTrue(repo.ultimoGuardado!!.kilometraje)
        assertNull(vm.estado.value.avisoPermiso)
    }

    @Test
    fun ea04_alAbrirRevocadoActualizaSoloPermiso() = runTest {
        repo.actual = PreferenciasExternas(true, true, false, PermisoDispositivo.CONCEDIDO)
        permisoOs = PermisoDispositivo.DENEGADO
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PermisoDispositivo.DENEGADO, repo.ultimoPermisoPatch)
        assertTrue(vm.estado.value.mantenimiento)
        assertTrue(vm.estado.value.soat)
        assertFalse(vm.estado.value.kilometraje)
        assertEquals(PermisoDispositivo.DENEGADO, vm.estado.value.permisoDispositivo)
    }

    @Test
    fun ea05_falloGuardarRevierteSwitches() = runTest {
        repo.actual = PreferenciasExternas(true, false, false, PermisoDispositivo.CONCEDIDO)
        permisoOs = PermisoDispositivo.CONCEDIDO
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        vm.setSoat(true)
        repo.fallarGuardar = true
        vm.guardar()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.estado.value.soat)
        assertTrue(vm.estado.value.mantenimiento)
        assertEquals("No pudimos guardar tus preferencias", vm.estado.value.mensajeError)
    }

    private class FakeRepo : PreferenciasNotificacionRepository {
        var actual = PreferenciasExternas(true, true, true, PermisoDispositivo.NO_SOLICITADO)
        var ultimoGuardado: PreferenciasExternas? = null
        var ultimoPermisoPatch: PermisoDispositivo? = null
        var fallarGuardar = false

        override suspend fun consultar(): PreferenciasExternas = actual

        override suspend fun guardar(
            mantenimiento: Boolean,
            soat: Boolean,
            kilometraje: Boolean,
            permisoDispositivo: PermisoDispositivo
        ): PreferenciasExternas {
            if (fallarGuardar) throw ErrorPreferencias.Red("fail")
            actual = PreferenciasExternas(mantenimiento, soat, kilometraje, permisoDispositivo)
            ultimoGuardado = actual
            return actual
        }

        override suspend fun actualizarPermisoDispositivo(permiso: PermisoDispositivo): PreferenciasExternas {
            ultimoPermisoPatch = permiso
            actual = actual.copy(permisoDispositivo = permiso)
            return actual
        }
    }
}
