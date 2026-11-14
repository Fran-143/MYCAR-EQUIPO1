package com.mycar.app.features.historial.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.historial.domain.EstadoHistorial
import com.mycar.app.features.historial.domain.HistorialRepository
import com.mycar.app.features.historial.viewmodel.HistorialViewModel
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.features.mantenimiento.ui.EntradaRegistroMantenimiento
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta

/** Host de producción. El equipo inyecta ambos repositorios autenticados y la clave opaca de cuenta. */
@Composable
fun FlujoHistorialMantenimiento(
    onVolverInicio: () -> Unit,
    modifier: Modifier = Modifier,
    historialRepository: HistorialRepository? = null,
    mantenimientoRepository: MantenimientoRepository? = null,
    claveCuenta: String? = null,
    onIniciarSesion: (() -> Unit)? = null,
    onActualizarKm: ((Long) -> Unit)? = null,
    idVehiculoInicial: Long? = null,
    archivoPendiente: com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente? = null,
    estadoEnSistema: Boolean = true,
    onRegistroAbierto: (Boolean) -> Unit = {},
    recargaRegistroToken: Int = 0,
    onProgramarAlerta: ((OfertaProgramarAlerta) -> Unit)? = null,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null
) {
    key(claveCuenta) {
        var registrando by rememberSaveable { mutableStateOf(false) }
        var vehiculoElegido by rememberSaveable { mutableStateOf<Long?>(idVehiculoInicial) }
        LaunchedEffect(registrando) { onRegistroAbierto(registrando) }
        if (registrando) {
            AmbitoHistorial {
                EntradaRegistroMantenimiento(onCerrar = { registrando = false }, modifier = modifier,
                    repository = mantenimientoRepository, claveCuentaBorrador = claveCuenta,
                    idVehiculoInicial = vehiculoElegido, onActualizarKm = onActualizarKm,
                    onSesionRequerida = onIniciarSesion, archivoPendiente = archivoPendiente,
                    estadoEnSistema = estadoEnSistema, recargaToken = recargaRegistroToken,
                    onProgramarAlerta = { oferta ->
                        registrando = false
                        onProgramarAlerta?.invoke(oferta)
                    },
                    talleresRepository = talleresRepository)
            }
        } else {
            AmbitoHistorial {
                EntradaHistorialMantenimiento(repository = historialRepository, claveCuenta = claveCuenta,
                    idInicial = vehiculoElegido, onSeleccionConfirmada = { vehiculoElegido = it },
                    onVolver = onVolverInicio, modifier = modifier,
                    registroDisponible = mantenimientoRepository != null && !claveCuenta.isNullOrBlank(),
                    onRegistrar = { vehiculoElegido = it; registrando = true }, onIniciarSesion = onIniciarSesion,
                    talleresRepository = talleresRepository,
                    onNotificaciones = onNotificaciones,
                    onPerfil = onPerfil)
            }
        }
    }
}

/** Ámbito retirado al salir de cada pantalla; RF17 conserva el SavedStateHandle del host real. */
@Composable
private fun AmbitoHistorial(contenido: @Composable () -> Unit) {
    val padre = LocalViewModelStoreOwner.current
    val owner = remember(padre) {
        if (padre is HasDefaultViewModelProviderFactory) {
            object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
                override val viewModelStore = ViewModelStore()
                override val defaultViewModelProviderFactory = padre.defaultViewModelProviderFactory
                override val defaultViewModelCreationExtras = padre.defaultViewModelCreationExtras
            }
        } else object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = contenido)
}

@Composable
fun EntradaHistorialMantenimiento(
    repository: HistorialRepository?, claveCuenta: String?, idInicial: Long?,
    onSeleccionConfirmada: (Long) -> Unit, onVolver: () -> Unit, onRegistrar: (Long) -> Unit,
    modifier: Modifier = Modifier, registroDisponible: Boolean = false,
    onIniciarSesion: (() -> Unit)? = null,
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null
) {
    var detalleId by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(onBack = {
        if (detalleId != null) detalleId = null else onVolver()
    })
    if (repository == null || claveCuenta.isNullOrBlank()) {
        HistorialMantenimientoScreen(
            EstadoHistorial(), onVolver, {}, {}, {}, {}, modifier,
            registroDisponible = false, onIniciarSesion = onIniciarSesion,
            onNotificaciones = onNotificaciones, onPerfil = onPerfil
        )
        return
    }
    val factory = remember(repository, claveCuenta, idInicial) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HistorialViewModel::class.java))
                return HistorialViewModel(repository, idInicial) as T
            }
        }
    }
    val vm: HistorialViewModel = viewModel(key = "rf18:$claveCuenta", factory = factory)
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME && detalleId == null) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(estado.seleccionado?.id, estado.fase) {
        if (estado.fase == com.mycar.app.features.historial.domain.FaseHistorial.LISTO)
            estado.seleccionado?.id?.let(onSeleccionConfirmada)
    }
    val idVehiculoDetalle = estado.seleccionado?.id
    if (detalleId != null && idVehiculoDetalle != null) {
        DetalleMantenimientoRoute(
            repository = repository,
            idVehiculo = idVehiculoDetalle,
            idMantenimiento = detalleId!!,
            onVolver = {
                detalleId = null
                vm.recargar()
            },
            modifier = modifier,
            talleresRepository = talleresRepository
        )
    } else {
        HistorialMantenimientoScreen(
            estado, onVolver, vm::seleccionar, vm::reintentar, vm::recargar,
            onRegistrar, modifier, registroDisponible, onIniciarSesion,
            onVerDetalle = { detalleId = it },
            talleresRepository = talleresRepository,
            onNotificaciones = onNotificaciones,
            onPerfil = onPerfil
        )
    }
}
