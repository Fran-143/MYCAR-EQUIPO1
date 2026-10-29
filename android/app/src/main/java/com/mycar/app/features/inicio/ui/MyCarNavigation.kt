package com.mycar.app.features.inicio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.historial.ui.FlujoHistorialMantenimiento
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.inicio.viewmodel.ResumenInicioViewModel
import com.mycar.app.features.sesion.viewmodel.SesionVehiculosViewModel
import com.mycar.app.features.vehiculos.ui.ActualizarKilometrajeRoute
import com.mycar.app.features.vehiculos.ui.DetalleVehiculoRoute
import com.mycar.app.features.vehiculos.ui.VehiculosRoute

/** Navegación única, siguiendo el patrón de estado Compose existente. No necesita otra biblioteca. */
@Composable
fun MyCarNavigation(solicitudVehiculos: Int=0, sesion: SesionVehiculosViewModel=viewModel()) {
    val acceso by sesion.estado.collectAsStateWithLifecycle()
    val cuenta=acceso.cuenta
    var seccionNombre by rememberSaveable { mutableStateOf(SeccionMyCar.INICIO.name) }
    var secundariaNombre by rememberSaveable { mutableStateOf<String?>(null) }
    var vehiculoMantenimiento by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehiculoDetalleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehiculoKmId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recargaInicio by rememberSaveable { mutableIntStateOf(0) }
    var formularioAbierto by remember { mutableStateOf(false) }
    val navegacion=NavegacionInicio(SeccionMyCar.valueOf(seccionNombre),secundariaNombre?.let(SecundariaMyCar::valueOf))
    fun navegar(n: NavegacionInicio) { seccionNombre=n.seccion.name; secundariaNombre=n.secundaria?.name; formularioAbierto=false }
    LaunchedEffect(solicitudVehiculos) { if(solicitudVehiculos>0) navegar(navegacion.abrir(SeccionMyCar.VEHICULOS)) }
    fun abrirDetalle(id: Long) { vehiculoDetalleId = id; navegar(navegacion.abrir(SecundariaMyCar.DETALLE)) }
    fun abrirKilometraje(id: Long) { vehiculoKmId = id; navegar(navegacion.abrir(SecundariaMyCar.KILOMETRAJE)) }
    LaunchedEffect(cuenta?.ciclo) {
        vehiculoMantenimiento=null; vehiculoDetalleId=null; vehiculoKmId=null; formularioAbierto=false; secundariaNombre=null
        if(cuenta!=null) seccionNombre=SeccionMyCar.INICIO.name
    }
    BackHandler(enabled=navegacion.secundaria!=null || navegacion.seccion!=SeccionMyCar.INICIO) { navegar(navegacion.atras()) }
    val esquema=lightColorScheme(primary=Color(0xFF079FDC),onPrimary=Color.White,
        primaryContainer=Color(0xFFE2F5FF),onPrimaryContainer=Color(0xFF163468),
        background=Color(0xFFF2FAFF),surface=Color.White,onSurface=Color(0xFF163468))
    MaterialTheme(colorScheme=esquema) {
        Scaffold(contentWindowInsets=WindowInsets(0),bottomBar={
            if(navegacion.secundaria==null && !formularioAbierto) NavigationBar(containerColor=Color.White) {
                SeccionMyCar.entries.forEach { destino ->
                    val icono=when(destino) {
                        SeccionMyCar.INICIO -> R.drawable.ic_inicio_inicio
                        SeccionMyCar.VEHICULOS -> R.drawable.ic_vehiculo_predeterminado_rf08
                        SeccionMyCar.MANTENIMIENTOS -> R.drawable.ic_inicio_llave
                        SeccionMyCar.ALERTAS -> R.drawable.ic_inicio_campana
                    }
                    NavigationBarItem(selected=navegacion.seccion==destino,onClick={ navegar(navegacion.abrir(destino)) },
                        icon={ Icon(painterResource(icono),destino.titulo,modifier=Modifier.size(24.dp)) },
                        label={ Text(destino.titulo,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom=padding.calculateBottomPadding())) {
                val secundaria=navegacion.secundaria
                if(secundaria!=null) {
                    when {
                        secundaria==SecundariaMyCar.DIAGNOSTICO -> ConexionServidorScreen(Modifier.statusBarsPadding())
                        secundaria==SecundariaMyCar.DETALLE && cuenta!=null -> {
                            val idDetalle = vehiculoDetalleId
                            if (idDetalle == null || idDetalle <= 0) {
                                PantallaProximamente("Detalle de vehículo", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                DetalleVehiculoRoute("${cuenta.ciclo}|$idDetalle", idDetalle, cuenta.detalle,
                                    onVolver={ navegar(navegacion.atras()) },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onActualizarKm={ abrirKilometraje(idDetalle) },
                                    modifier=Modifier.statusBarsPadding())
                            }
                        }
                        secundaria==SecundariaMyCar.KILOMETRAJE && cuenta!=null -> {
                            val idKm = vehiculoKmId
                            if (idKm == null || idKm <= 0) {
                                PantallaProximamente("Actualizar kilometraje", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                ActualizarKilometrajeRoute("${cuenta.ciclo}|$idKm", idKm, cuenta.kilometraje,
                                    onVolver={ navegar(navegacion.atras()) },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onActualizado={ recargaInicio++ },
                                    modifier=Modifier.statusBarsPadding())
                            }
                        }
                        else -> PantallaProximamente(secundaria.titulo,onVolver={ navegar(navegacion.atras()) })
                    }
                } else if(navegacion.seccion==SeccionMyCar.ALERTAS) {
                    PantallaProximamente("Alertas",onVolver={ navegar(navegacion.atras()) },mostrarVolver=false)
                } else if(cuenta==null) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("Preparando tu cuenta",style=MaterialTheme.typography.titleLarge)
                        if(acceso.ocupada) CircularProgressIndicator()
                        else {
                            Text(acceso.mensaje ?: "No se pudo cargar tu cuenta.")
                            Button(onClick=sesion::reintentar) { Text("Volver a intentar") }
                        }
                    }
                } else key(cuenta.ciclo) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides cuenta) {
                        when(navegacion.seccion) {
                            SeccionMyCar.INICIO -> {
                                val factory=remember(cuenta) {
                                    object : ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T:ViewModel> create(modelClass:Class<T>):T {
                                            require(modelClass.isAssignableFrom(ResumenInicioViewModel::class.java))
                                            return ResumenInicioViewModel(cuenta.resumen) as T
                                        }
                                    }
                                }
                                val vm:ResumenInicioViewModel=viewModel(key="rf50-${cuenta.ciclo}",factory=factory)
                                val estado by vm.estado.collectAsStateWithLifecycle()
                                LaunchedEffect(vm, recargaInicio) { vm.cargar() }
                                LaunchedEffect(estado.fase) {
                                    if(estado.fase==FaseInicio.SIN_SESION) sesion.sesionRequerida(cuenta.ciclo)
                                }
                                HomeScreen(estado,vm::seleccionar,
                                    onVehiculos={ navegar(navegacion.abrir(SeccionMyCar.VEHICULOS)) },
                                    onMantenimientos={ id -> vehiculoMantenimiento=id; navegar(navegacion.abrir(SeccionMyCar.MANTENIMIENTOS)) },
                                    onAlertas={ navegar(navegacion.abrir(SeccionMyCar.ALERTAS)) },
                                    onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                    onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },onRecargar=vm::cargar,
                                    onVerVehiculo={ abrirDetalle(it) },
                                    onActualizarKm={ abrirKilometraje(it) },modifier=Modifier.statusBarsPadding())
                            }
                            SeccionMyCar.VEHICULOS -> Column {
                                VehiculosRoute(cuenta.ciclo,cuenta.registro,cuenta.listado,cuenta.pendientes,
                                    onIrInicio={ navegar(navegacion.atras()) },onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onVerDetalle={ abrirDetalle(it) },
                                    estadoEnSistema=false,modifier=Modifier.weight(1f),onFormularioAbierto={ formularioAbierto=it })
                            }
                            SeccionMyCar.MANTENIMIENTOS -> FlujoHistorialMantenimiento(
                                onVolverInicio={ navegar(navegacion.atras()) },historialRepository=cuenta.historial,
                                mantenimientoRepository=cuenta.mantenimiento,claveCuenta="${com.mycar.app.BuildConfig.API_BASE_URL}|${cuenta.usuario}",
                                onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                onActualizarKm=null,
                                idVehiculoInicial=vehiculoMantenimiento,archivoPendiente=cuenta.archivoMantenimiento,
                                estadoEnSistema=false,onRegistroAbierto={ formularioAbierto=it })
                            else -> Unit
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun PantallaProximamente(titulo:String,onVolver:()->Unit,mostrarVolver:Boolean=true) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(mostrarVolver) TextButton(onClick=onVolver) { Text("Volver") }
        Text(titulo,style=MaterialTheme.typography.headlineMedium)
        Text("Próximamente",style=MaterialTheme.typography.titleLarge)
    }
}
