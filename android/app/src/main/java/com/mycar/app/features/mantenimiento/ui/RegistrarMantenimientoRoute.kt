package com.mycar.app.features.mantenimiento.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoViewModel

/**
 * Entrada propia de RF17. El host proporciona repositorio autenticado, clave de cuenta
 * opaca, cierre y, cuando exista el contrato, navegación RF14. No modifica MainActivity.
 * Alojarla en la entrada de navegación de RF17 para conservar el VM al ir/volver de RF14.
 */
@Composable
fun RegistrarMantenimientoRoute(
    repository: MantenimientoRepository,
    claveCuentaBorrador: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    idVehiculoInicial: Long? = null,
    onActualizarKm: ((Long) -> Unit)? = null,
    onSesionRequerida: (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val factory = remember(repository, claveCuentaBorrador, idVehiculoInicial) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(MantenimientoViewModel::class.java))
                return MantenimientoViewModel(repository,
                    ArchivoRegistroPendiente(context, claveCuentaBorrador),
                    extras.createSavedStateHandle(), idVehiculoInicial) as T
            }
        }
    }
    val vm: MantenimientoViewModel = viewModel(key = "rf17:$claveCuentaBorrador", factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cerrarConfirmado = { vm.finalizarConfirmacion(); onCerrar() }
    BackHandler {
        when {
            state.resultado != null -> cerrarConfirmado()
            state.editable -> vm.solicitarSalir()
            else -> onCerrar() // Solicitud incierta permanece en almacenamiento privado.
        }
    }
    RegistrarMantenimientoScreen(state, vm, modifier, onCerrar,
        cerrarConfirmado, onActualizarKm, onSesionRequerida)
}
