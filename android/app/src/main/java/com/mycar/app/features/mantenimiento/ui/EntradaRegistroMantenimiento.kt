package com.mycar.app.features.mantenimiento.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mycar.app.features.mantenimiento.domain.BorradorMantenimiento
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.features.mantenimiento.domain.RepuestoBorrador
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState

/** Entrada de producción. El host entregará el repositorio y la clave opaca de la sesión real. */
@Composable
fun EntradaRegistroMantenimiento(
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    repository: MantenimientoRepository? = null,
    claveCuentaBorrador: String? = null,
    idVehiculoInicial: Long? = null,
    onActualizarKm: ((Long) -> Unit)? = null,
    onSesionRequerida: (() -> Unit)? = null
) {
    if (repository != null && !claveCuentaBorrador.isNullOrBlank()) {
        RegistrarMantenimientoRoute(
            repository = repository, claveCuentaBorrador = claveCuentaBorrador,
            onCerrar = onCerrar, modifier = modifier,
            idVehiculoInicial = idVehiculoInicial,
            onActualizarKm = onActualizarKm, onSesionRequerida = onSesionRequerida
        )
    } else {
        BackHandler(onBack = onCerrar)
        FormularioRegistroMantenimiento(
            s = MantenimientoUiState(cargando = false, requiereSesion = true),
            vm = AccionesSinSesion, modifier = modifier,
            onCerrar = onCerrar, onCerrarConfirmado = onCerrar,
            onActualizarKm = null, onSesionRequerida = onSesionRequerida
        )
    }
}

/** Los controles están bloqueados hasta recibir una sesión real; no hay peticiones ni borrador de otra cuenta. */
private object AccionesSinSesion : AccionesMantenimiento {
    override fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) {}
    override fun recargar() {}
    override fun fecha(fecha: String) {}
    override fun origen(origen: String) {}
    override fun lectura(id: Long) {}
    override fun solicitarVehiculo(id: Long) {}
    override fun confirmarVehiculo() {}
    override fun cancelarVehiculo() {}
    override fun editarRepuesto(id: String?) {}
    override fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador) {}
    override fun cerrarEditor() {}
    override fun confirmarRepuesto() {}
    override fun quitarRepuesto(id: String) {}
    override fun guardar() {}
    override fun reintentarMismaOperacion() {}
    override fun consultarOperacion() {}
    override fun solicitarSalir() {}
    override fun seguirEditando() {}
    override fun descartar() {}
}
