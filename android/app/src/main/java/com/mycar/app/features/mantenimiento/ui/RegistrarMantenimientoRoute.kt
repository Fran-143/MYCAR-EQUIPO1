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
import com.mycar.app.features.mantenimiento.domain.ContextoDesdeAlerta
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.features.mantenimiento.viewmodel.AtenderAlertaDesdeFormulario
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoViewModel
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta

/**
 * Entrada propia de RF17. El host proporciona repositorio autenticado, clave de cuenta
 * opaca, cierre y, cuando exista el contrato, navegación RF14. No modifica MainActivity.
 * Alojarla en la entrada de navegación de RF17 para conservar el VM al ir/volver de RF14.
 * Con [desdeAlerta] (RF24) reutiliza el mismo formulario en modo DESDE_ALERTA.
 */
@Composable
fun RegistrarMantenimientoRoute(
    repository: MantenimientoRepository,
    claveCuentaBorrador: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    idVehiculoInicial: Long? = null,
    onActualizarKm: ((Long) -> Unit)? = null,
    onSesionRequerida: (() -> Unit)? = null,
    archivoPendiente: ArchivoRegistroPendiente? = null,
    estadoEnSistema: Boolean = true,
    recargaToken: Int = 0,
    onProgramarAlerta: ((OfertaProgramarAlerta) -> Unit)? = null,
    desdeAlerta: ContextoDesdeAlerta? = null,
    atenderAlerta: AtenderAlertaDesdeFormulario? = null,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null
) {
    val context = LocalContext.current.applicationContext
    val factory = remember(
        repository, claveCuentaBorrador, idVehiculoInicial, archivoPendiente, estadoEnSistema,
        desdeAlerta, atenderAlerta, talleresRepository
    ) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass.isAssignableFrom(MantenimientoViewModel::class.java))
                return MantenimientoViewModel(
                    repository,
                    archivoPendiente ?: ArchivoRegistroPendiente(context, claveCuentaBorrador),
                    if (estadoEnSistema) extras.createSavedStateHandle() else androidx.lifecycle.SavedStateHandle(),
                    idVehiculoInicial ?: desdeAlerta?.idVehiculo,
                    desdeAlerta,
                    atenderAlerta,
                    talleresRepository
                ) as T
            }
        }
    }
    val vmKey = if (desdeAlerta != null) {
        "rf17:desdeAlerta:${desdeAlerta.idAlerta}:$claveCuentaBorrador"
    } else {
        "rf17:$claveCuentaBorrador"
    }
    val vm: MantenimientoViewModel = viewModel(key = vmKey, factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Recarga en resume solo si ya hubo una carga (evita doble fetch con init que bloquea la UI).
    var resumeListo by remember { mutableStateOf(false) }
    LaunchedEffect(state.contexto, state.cargando) {
        if (state.contexto != null || !state.cargando) resumeListo = true
    }
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && resumeListo) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(recargaToken) { if (recargaToken > 0) vm.recargar() }
    val cerrarConfirmado = { vm.finalizarConfirmacion(); onCerrar() }
    BackHandler {
        when {
            state.resultado != null -> cerrarConfirmado()
            state.editable -> vm.solicitarSalir()
            else -> onCerrar() // Solicitud incierta permanece en almacenamiento privado.
        }
    }
    RegistrarMantenimientoScreen(state, vm, modifier, onCerrar,
        cerrarConfirmado, onActualizarKm, onSesionRequerida,
        onProgramarAlerta = { oferta ->
            vm.finalizarConfirmacion()
            onProgramarAlerta?.invoke(oferta) ?: onCerrar()
        })
}
