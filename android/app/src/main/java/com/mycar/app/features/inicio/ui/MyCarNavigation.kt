package com.mycar.app.features.inicio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.alertas.ui.AlertasRoute
import com.mycar.app.features.alertas.ui.ProgramarAlertaRoute
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.autenticacion.ui.LocalConfirmedLogout
import com.mycar.app.features.historial.ui.FlujoHistorialMantenimiento
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.inicio.viewmodel.ResumenInicioViewModel
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta
import com.mycar.app.features.notificaciones.domain.NotificacionesBadgeStore
import com.mycar.app.features.notificaciones.ui.NotificacionesRoute
import com.mycar.app.features.perfil.ui.PerfilRoute
import com.mycar.app.features.perfil.viewmodel.PerfilViewModel
import com.mycar.app.features.sesion.viewmodel.SesionVehiculosViewModel
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.ui.*
import com.mycar.app.features.tive.ui.*
import com.mycar.app.features.vehiculos.ui.ActualizarKilometrajeRoute
import com.mycar.app.features.vehiculos.ui.CompartirVehiculoRoute
import com.mycar.app.features.vehiculos.ui.DetalleVehiculoRoute
import com.mycar.app.features.vehiculos.ui.VehiculosRoute

/** Navegación única, siguiendo el patrón de estado Compose existente. No necesita otra biblioteca. */
@Composable
fun MyCarNavigation(
    solicitudVehiculos: Int = 0,
    sesion: SesionVehiculosViewModel = viewModel(),
    solicitudesNotificaciones: Int = 0,
    pushIdVehiculo: Long? = null,
    pushIdAlerta: Long? = null,
    pushIdNotificacion: Long? = null,
    pushCategoria: String? = null
) {
    val acceso by sesion.estado.collectAsStateWithLifecycle()
    val cuenta=acceso.cuenta
    LaunchedEffect(cuenta?.ciclo) {
        val c = cuenta ?: return@LaunchedEffect
        runCatching {
            val perfil = c.perfil.consultar()
            c.inicialesPresentacion.value = perfil.iniciales
            c.nombrePresentacion.value = perfil.nombre
            if (perfil.tieneFoto) {
                c.avatarPresentacion.value = runCatching { c.fotos.bytesPerfil() }.getOrNull()
            } else {
                c.avatarPresentacion.value = null
            }
        }
        runCatching {
            c.notificaciones.consultarBandeja("PENDIENTES", "TODAS", "TODAS")
        }
    }
    var seccionNombre by rememberSaveable { mutableStateOf(SeccionMyCar.INICIO.name) }
    var secundariaNombre by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(cuenta?.ciclo, solicitudesNotificaciones, secundariaNombre, seccionNombre) {
        val c = cuenta
        if (c == null) {
            NotificacionesBadgeStore.reiniciar()
            return@LaunchedEffect
        }
        runCatching {
            c.notificaciones.consultarBandeja("PENDIENTES", "TODAS", "TODAS")
        }
    }
    var vehiculoMantenimiento by rememberSaveable { mutableStateOf<Long?>(null) }
    var abrirHistorialEnTalleres by rememberSaveable { mutableStateOf(false) }
    var vehiculoDetalleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehiculoCompartirId by rememberSaveable { mutableStateOf<Long?>(null) }
    var vehiculoCompartirMarca by rememberSaveable { mutableStateOf<String?>(null) }
    var vehiculoCompartirModelo by rememberSaveable { mutableStateOf<String?>(null) }
    var vehiculoKmId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recargaInicio by rememberSaveable { mutableIntStateOf(0) }
    var recargaDetalle by rememberSaveable { mutableIntStateOf(0) }
    var vehiculoSoatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var soatSesion by rememberSaveable { mutableIntStateOf(0) }
    var recargaSoat by rememberSaveable { mutableIntStateOf(0) }
    var soatRecordatorioActivo by rememberSaveable { mutableStateOf(false) }
    var soatIdRecordatorio by rememberSaveable { mutableStateOf<Long?>(null) }
    var soatFechaVencimiento by rememberSaveable { mutableStateOf<String?>(null) }
    /** true si Configurar SOAT se abrió desde Consultar (Volver debe regresar ahí). */
    var soatConfigDesdeConsultar by rememberSaveable { mutableStateOf(false) }
    var vehiculoTiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var tiveSesion by rememberSaveable { mutableIntStateOf(0) }
    var recargaTive by rememberSaveable { mutableIntStateOf(0) }
    /** Flujo VIN iniciado desde «Registrar TIVE» sin VIN. */
    var flujoVinDesdeTive by rememberSaveable { mutableStateOf(false) }
    var resaltarVinEdicion by rememberSaveable { mutableStateOf(false) }
    var modalVinCompletado by rememberSaveable { mutableStateOf(false) }
    var recargaMantenimiento by rememberSaveable { mutableIntStateOf(0) }
    var kmOrigenDetalle by rememberSaveable { mutableStateOf(false) }
    var kmOverlayMantenimiento by rememberSaveable { mutableStateOf<Long?>(null) }
    var kmSesion by rememberSaveable { mutableIntStateOf(0) }
    var alertasProgramarId by rememberSaveable { mutableStateOf<Long?>(null) }
    var alertasSesion by rememberSaveable { mutableIntStateOf(0) }
    var vehiculoAlertas by rememberSaveable { mutableStateOf<Long?>(null) }
    // Sobrevive al cambio de pestaña: si vive dentro de ALERTAS, el primer frame
    // vuelve con lista vacía y el ViewModel (filtro + caché) se recrea.
    var vehiculosAlertas by remember(cuenta?.ciclo) {
        mutableStateOf<List<Pair<Long, String>>>(emptyList())
    }
    var recargaAlertas by rememberSaveable { mutableIntStateOf(0) }
    var alertasFiltroTodasTrasExito by rememberSaveable { mutableStateOf(false) }
    var alertasOrdenRecientesTrasExito by rememberSaveable { mutableStateOf(false) }
    var formularioAbierto by remember { mutableStateOf(false) }
    var formularioAtencionAlerta by remember { mutableStateOf(false) }
    var detalleAlertaTrasAtencion by rememberSaveable { mutableStateOf<Long?>(null) }
    var perfilAbrirLicencia by rememberSaveable { mutableStateOf(false) }
    var perfilAbrirPlanes by rememberSaveable { mutableStateOf(false) }
    var borradorAlertaRf17 by remember { mutableStateOf<BorradorAlerta?>(null) }
    val navegacion=NavegacionInicio(SeccionMyCar.valueOf(seccionNombre),secundariaNombre?.let(SecundariaMyCar::valueOf))
    fun navegar(n: NavegacionInicio) { seccionNombre=n.seccion.name; secundariaNombre=n.secundaria?.name; formularioAbierto=false }
    LaunchedEffect(solicitudVehiculos) { if(solicitudVehiculos>0) navegar(navegacion.abrir(SeccionMyCar.VEHICULOS)) }
    // Deep link FCM: Push → bandeja Notificaciones (categoría/resumen) → detalle individual.
    var pushNotifResaltar by rememberSaveable { mutableStateOf<Long?>(null) }
    var pushNotifCategoria by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(solicitudesNotificaciones, pushIdNotificacion, pushCategoria, cuenta?.ciclo) {
        if (solicitudesNotificaciones <= 0 || cuenta == null) return@LaunchedEffect
        if (pushIdVehiculo != null) vehiculoAlertas = pushIdVehiculo
        pushNotifResaltar = pushIdNotificacion
        pushNotifCategoria = pushCategoria
        navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES))
    }
    fun abrirDetalle(id: Long) { vehiculoDetalleId = id; navegar(navegacion.abrir(SecundariaMyCar.DETALLE)) }
    fun abrirCompartir(id: Long, marca: String? = null, modelo: String? = null) {
        vehiculoCompartirId = id
        vehiculoCompartirMarca = marca
        vehiculoCompartirModelo = modelo
        navegar(navegacion.abrir(SecundariaMyCar.COMPARTIR_VEHICULO))
    }
    fun abrirEdicion(id: Long) { vehiculoDetalleId = id; navegar(navegacion.abrir(SecundariaMyCar.EDITAR_VEHICULO)) }
    fun abrirSoat(id: Long, destino: SecundariaMyCar) {
        soatSesion++
        vehiculoSoatId = id
        if (destino == SecundariaMyCar.SOAT_CONFIGURAR) {
            soatConfigDesdeConsultar = navegacion.secundaria == SecundariaMyCar.SOAT_CONSULTAR
        }
        navegar(navegacion.abrir(destino))
    }
    fun abrirTive(id: Long, destino: SecundariaMyCar) {
        tiveSesion++
        vehiculoTiveId = id
        modalVinCompletado = false
        navegar(navegacion.abrir(destino))
    }
    fun irInicioMyCar() {
        modalVinCompletado = false
        flujoVinDesdeTive = false
        resaltarVinEdicion = false
        navegar(NavegacionInicio(SeccionMyCar.INICIO, null))
    }
    fun completarVinParaTive(id: Long) {
        flujoVinDesdeTive = true
        resaltarVinEdicion = true
        modalVinCompletado = false
        vehiculoDetalleId = id
        vehiculoTiveId = id
        abrirEdicion(id)
    }
    fun volverDesdeConfigurarSoat() {
        navegar(
            navegacion.copy(
                secundaria = if (soatConfigDesdeConsultar) {
                    SecundariaMyCar.SOAT_CONSULTAR
                } else {
                    SecundariaMyCar.DETALLE
                }
            )
        )
    }
    fun abrirKilometraje(id: Long, desdeDetalle: Boolean = false) {
        kmSesion++
        vehiculoKmId = id
        kmOrigenDetalle = desdeDetalle
        navegar(navegacion.abrir(SecundariaMyCar.KILOMETRAJE))
    }
    /** Overlay sobre Mantenimientos. No llama a navegar(): eso desmonta el formulario. */
    fun abrirKmDesdeMantenimiento(id: Long) {
        kmSesion++
        kmOverlayMantenimiento = id
    }
    fun abrirProgramarAlerta(id: Long, borrador: BorradorAlerta? = null) {
        alertasSesion++
        alertasProgramarId = id
        vehiculoAlertas = id
        borradorAlertaRf17 = borrador
        navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
    }
    fun abrirProgramarDesdeRf17(oferta: OfertaProgramarAlerta) {
        // Compatibilidad residual: RF17 crea la alerta en flujo; este fallback abre RF22.
        val intervalo = oferta.proximoKm?.let { pk -> (pk - oferta.kmServicio).takeIf { it > 0 } }
        val criterio = when {
            intervalo != null && oferta.proximaFecha != null -> "AMBOS"
            intervalo != null -> "POR_KILOMETRAJE"
            else -> "POR_TIEMPO"
        }
        abrirProgramarAlerta(
            oferta.idVehiculo,
            BorradorAlerta(
                codigoTipo = oferta.codigoTipo,
                criterio = criterio,
                origenCalculo = "DESDE_MANTENIMIENTO",
                unidadTiempo = "MESES",
                cantidadTiempo = "6",
                intervaloKm = (intervalo ?: 5000).toString(),
                servicioOtro = oferta.servicioOtro.orEmpty(),
                idReferencia = oferta.idMantenimiento
            )
        )
    }
    LaunchedEffect(cuenta?.ciclo) {
        vehiculoMantenimiento=null; vehiculoDetalleId=null; vehiculoCompartirId=null
        vehiculoCompartirMarca=null; vehiculoCompartirModelo=null
        vehiculoKmId=null; vehiculoSoatId=null
        vehiculoTiveId=null; flujoVinDesdeTive=false; resaltarVinEdicion=false; modalVinCompletado=false
        kmOverlayMantenimiento=null
        alertasProgramarId=null; formularioAbierto=false; secundariaNombre=null
        detalleAlertaTrasAtencion=null; formularioAtencionAlerta=false
        if(cuenta!=null) seccionNombre=SeccionMyCar.INICIO.name
    }
    BackHandler(enabled=!formularioAtencionAlerta && (navegacion.secundaria!=null || navegacion.seccion!=SeccionMyCar.INICIO ||
        kmOverlayMantenimiento!=null || alertasProgramarId!=null)) {
        when {
            kmOverlayMantenimiento != null -> kmOverlayMantenimiento = null
            alertasProgramarId != null -> alertasProgramarId = null
            navegacion.secundaria == SecundariaMyCar.SOAT_CONFIGURAR -> volverDesdeConfigurarSoat()
            navegacion.secundaria != null || navegacion.seccion != SeccionMyCar.INICIO -> navegar(navegacion.atras())
        }
    }
    Scaffold(contentWindowInsets=WindowInsets(0),bottomBar={
            if(navegacion.secundaria==null && !formularioAbierto && !formularioAtencionAlerta && kmOverlayMantenimiento==null && alertasProgramarId==null)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                .union(WindowInsets.navigationBars)
                        )
                ) {
                NavigationBar(
                    containerColor=MaterialTheme.colorScheme.surface,
                    windowInsets=WindowInsets(0)
                ) {
                SeccionMyCar.entries.forEach { destino ->
                    val seleccionado = navegacion.seccion==destino
                    NavigationBarItem(selected=seleccionado,onClick={
                        if (destino==SeccionMyCar.MANTENIMIENTOS) abrirHistorialEnTalleres=false
                        navegar(navegacion.abrir(destino))
                    },
                        icon={
                            when (destino) {
                                SeccionMyCar.VEHICULOS -> Icon(Icons.Outlined.DirectionsCar, destino.titulo)
                                SeccionMyCar.INICIO -> Icon(
                                    painterResource(R.drawable.ic_inicio_inicio), destino.titulo, modifier=Modifier.size(24.dp)
                                )
                                SeccionMyCar.MANTENIMIENTOS -> Icon(
                                    painterResource(R.drawable.ic_inicio_llave), destino.titulo, modifier=Modifier.size(24.dp)
                                )
                                SeccionMyCar.ALERTAS -> Icon(
                                    painterResource(R.drawable.ic_inicio_campana), destino.titulo, modifier=Modifier.size(24.dp)
                                )
                            }
                        },
                        label={ Text(destino.titulo,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis) },
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=MaterialTheme.colorScheme.secondary,
                            selectedTextColor=MaterialTheme.colorScheme.primary,
                            indicatorColor=MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant
                        ))
                }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom=padding.calculateBottomPadding())) {
                val secundaria=navegacion.secundaria
                if(secundaria!=null) {
                    when {
                        secundaria==SecundariaMyCar.DIAGNOSTICO -> ConexionServidorScreen(Modifier.statusBarsPadding())
                        secundaria==SecundariaMyCar.COMPARTIR_VEHICULO && cuenta!=null -> {
                            val idCompartir = vehiculoCompartirId
                            if (idCompartir == null || idCompartir <= 0) {
                                PantallaProximamente("Compartir vehículo", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                CompartirVehiculoRoute(
                                    idVehiculo = idCompartir,
                                    repository = cuenta.compartir,
                                    onVolver = {
                                        vehiculoCompartirId = null
                                        vehiculoCompartirMarca = null
                                        vehiculoCompartirModelo = null
                                        navegar(navegacion.atras())
                                    },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    modifier = Modifier.statusBarsPadding(),
                                    marca = vehiculoCompartirMarca,
                                    modelo = vehiculoCompartirModelo
                                )
                            }
                        }
                        secundaria==SecundariaMyCar.DETALLE && cuenta!=null -> {
                            val idDetalle = vehiculoDetalleId
                            if (idDetalle == null || idDetalle <= 0) {
                                PantallaProximamente("Detalle de vehículo", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                DetalleVehiculoRoute(
                                    "${cuenta.ciclo}|$idDetalle", idDetalle, cuenta.detalle,
                                    soatRepository = cuenta.soat,
                                    fotosRepository = cuenta.fotos,
                                    onVolver={ navegar(navegacion.atras()) },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onActualizarKm={ abrirKilometraje(idDetalle, desdeDetalle = true) },
                                    onEditarDatos={
                                        flujoVinDesdeTive = false
                                        resaltarVinEdicion = false
                                        abrirEdicion(idDetalle)
                                    },
                                    onRegistrarSoat={ abrirSoat(idDetalle, SecundariaMyCar.SOAT_REGISTRAR) },
                                    onConsultarSoat={ abrirSoat(idDetalle, SecundariaMyCar.SOAT_CONSULTAR) },
                                    onConfigurarSoat={ abrirSoat(idDetalle, SecundariaMyCar.SOAT_CONFIGURAR) },
                                    onRegistrarTive={ abrirTive(idDetalle, SecundariaMyCar.TIVE_REGISTRAR) },
                                    onConsultarTive={ abrirTive(idDetalle, SecundariaMyCar.TIVE_CONSULTAR) },
                                    onEditarTive={ abrirTive(idDetalle, SecundariaMyCar.TIVE_EDITAR) },
                                    onCompletarVinParaTive={ completarVinParaTive(idDetalle) },
                                    onCancelarVinParaTive={ /* Cierra el modal y permanece en Detalle */ },
                                    mostrarModalVinCompletado = modalVinCompletado,
                                    onRegistrarTiveTrasVin = {
                                        modalVinCompletado = false
                                        abrirTive(idDetalle, SecundariaMyCar.TIVE_REGISTRAR)
                                    },
                                    onVolverDetalleTrasVin = { modalVinCompletado = false },
                                    onCambioOperativo={
                                        recargaDetalle++
                                        recargaInicio++
                                        recargaSoat++
                                        recargaTive++
                                    },
                                    recargaToken = recargaDetalle + recargaSoat + recargaTive,
                                    modifier=Modifier.statusBarsPadding())
                            }
                        }
                        secundaria==SecundariaMyCar.EDITAR_VEHICULO && cuenta!=null -> {
                            val idEdicion = vehiculoDetalleId
                            if (idEdicion == null || idEdicion <= 0) {
                                PantallaProximamente("Editar datos", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                com.mycar.app.features.vehiculos.ui.EditarVehiculoRoute(
                                    "${cuenta.ciclo}|$idEdicion|vin=$resaltarVinEdicion", idEdicion, cuenta.edicion,
                                    resaltarVin = resaltarVinEdicion,
                                    onVolver={
                                        flujoVinDesdeTive = false
                                        resaltarVinEdicion = false
                                        modalVinCompletado = false
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE))
                                    },
                                    onGuardado={
                                        recargaDetalle++
                                        recargaInicio++
                                        recargaTive++
                                        resaltarVinEdicion = false
                                        if (flujoVinDesdeTive) {
                                            flujoVinDesdeTive = false
                                            modalVinCompletado = true
                                        } else {
                                            modalVinCompletado = false
                                        }
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE))
                                    },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    modifier=Modifier.statusBarsPadding())
                            }
                        }
                        secundaria==SecundariaMyCar.KILOMETRAJE && cuenta!=null -> {
                            val idKm = vehiculoKmId
                            if (idKm == null || idKm <= 0) {
                                PantallaProximamente("Actualizar kilometraje", onVolver={ navegar(navegacion.atras()) })
                            } else {
                                ActualizarKilometrajeRoute(
                                    "${cuenta.ciclo}|$idKm|s$kmSesion", idKm, cuenta.kilometraje,
                                    onVolver={
                                        // No usar atras(): KILOMETRAJE→DETALLE siempre, aunque se abrió desde Inicio.
                                        if (kmOrigenDetalle && vehiculoDetalleId == idKm) {
                                            kmOrigenDetalle = false
                                            navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE))
                                        } else {
                                            kmOrigenDetalle = false
                                            vehiculoKmId = null
                                            navegar(navegacion.copy(secundaria = null))
                                        }
                                    },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onActualizado={
                                        recargaInicio++
                                        recargaAlertas++
                                        if (kmOrigenDetalle && vehiculoDetalleId == idKm) recargaDetalle++
                                    },
                                    onIrInicio={
                                        kmOrigenDetalle = false
                                        vehiculoKmId = null
                                        navegar(NavegacionInicio(SeccionMyCar.INICIO, null))
                                    },
                                    modifier=Modifier.fillMaxSize().statusBarsPadding())
                            }
                        }
                        secundaria==SecundariaMyCar.PERFIL && cuenta!=null -> {
                            val cerrarSesion = LocalConfirmedLogout.current
                            val factoryPerfil = remember(cuenta.ciclo) {
                                object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        require(modelClass.isAssignableFrom(PerfilViewModel::class.java))
                                        return PerfilViewModel(
                                            cuenta.perfil,
                                            fotos = cuenta.fotos,
                                            planes = cuenta.planes,
                                            onNombreActualizado = { nombre ->
                                                cuenta.nombrePresentacion.value = nombre
                                            },
                                            onAvatarActualizado = { bytes ->
                                                cuenta.avatarPresentacion.value = bytes
                                            }
                                        ) as T
                                    }
                                }
                            }
                            val perfilVm: PerfilViewModel = viewModel(key = "perfil-${cuenta.ciclo}", factory = factoryPerfil)
                            val estadoPerfilCabecera by perfilVm.estado.collectAsStateWithLifecycle()
                            // Solo al entrar a Perfil (no en cada recomposición): evita pisar Soporte/Planes
                            // si el usuario toca el módulo antes de que corra el efecto.
                            var perfilEntradaLista by remember(cuenta.ciclo) { mutableStateOf(false) }
                            LaunchedEffect(cuenta.ciclo, secundariaNombre, perfilAbrirPlanes, perfilAbrirLicencia) {
                                if (secundariaNombre != SecundariaMyCar.PERFIL.name) {
                                    perfilEntradaLista = false
                                    return@LaunchedEffect
                                }
                                when {
                                    perfilAbrirPlanes -> {
                                        perfilVm.abrirPlanes()
                                        perfilAbrirPlanes = false
                                        perfilEntradaLista = true
                                    }
                                    perfilAbrirLicencia -> {
                                        perfilVm.abrirLicencia()
                                        perfilAbrirLicencia = false
                                        perfilEntradaLista = true
                                    }
                                    !perfilEntradaLista -> {
                                        perfilVm.resetAlAbrir()
                                        perfilEntradaLista = true
                                    }
                                }
                            }
                            LaunchedEffect(estadoPerfilCabecera.perfil?.iniciales, estadoPerfilCabecera.fotoBytes) {
                                cuenta.inicialesPresentacion.value = estadoPerfilCabecera.perfil?.iniciales
                                cuenta.avatarPresentacion.value = estadoPerfilCabecera.fotoBytes
                            }
                            PerfilRoute(
                                vm = perfilVm,
                                preferenciasNotificacion = cuenta.preferenciasNotificacion,
                                recordatoriosKm = cuenta.recordatoriosKm,
                                fotosRepository = cuenta.fotos,
                                licenciaRepository = cuenta.licencia,
                                soporteRepository = cuenta.soporte,
                                planesRepository = cuenta.planes,
                                onVolver = { navegar(navegacion.atras()) },
                                onSesionInvalida = { sesion.sesionRequerida(cuenta.ciclo) },
                                onCerrarSesion = { cerrarSesion?.invoke() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        secundaria == SecundariaMyCar.SOAT_REGISTRAR && cuenta != null -> {
                            val idSoat = vehiculoSoatId
                            if (idSoat == null || idSoat <= 0) {
                                PantallaProximamente("Registrar SOAT", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                fun trasSoatDetalle(@Suppress("UNUSED_PARAMETER") dto: SoatDto) {
                                    recargaSoat++
                                    recargaDetalle++
                                    navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE))
                                }
                                fun trasSoatConsultar(@Suppress("UNUSED_PARAMETER") dto: SoatDto) {
                                    recargaSoat++
                                    recargaDetalle++
                                    navegar(navegacion.copy(secundaria = SecundariaMyCar.SOAT_CONSULTAR))
                                }
                                RegistrarSoatRoute(
                                    "${cuenta.ciclo}|soat-$idSoat|s$soatSesion|r$recargaSoat",
                                    idSoat,
                                    cuenta.soat,
                                    onVolver = { navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE)) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onExito = ::trasSoatDetalle,
                                    onVerSoat = ::trasSoatConsultar,
                                    onRecordatorio = { dto ->
                                        soatIdRecordatorio = dto.idSoat
                                        soatRecordatorioActivo = dto.recordatorioActivo
                                        soatFechaVencimiento = dto.fechaVencimiento
                                        navegar(navegacion.abrir(SecundariaMyCar.SOAT_RECORDATORIO))
                                    },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.SOAT_CONSULTAR && cuenta != null -> {
                            val idSoat = vehiculoSoatId
                            if (idSoat == null || idSoat <= 0) {
                                PantallaProximamente("Consultar SOAT", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                ConsultarSoatRoute(
                                    "${cuenta.ciclo}|soat-cons-$idSoat|s$soatSesion|r$recargaSoat",
                                    idSoat,
                                    cuenta.soat,
                                    onVolver = { navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE)) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onConfigurar = { abrirSoat(idSoat, SecundariaMyCar.SOAT_CONFIGURAR) },
                                    onCorregir = { abrirSoat(idSoat, SecundariaMyCar.SOAT_CORREGIR) },
                                    onRenovar = { abrirSoat(idSoat, SecundariaMyCar.SOAT_RENOVAR) },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.SOAT_CONFIGURAR && cuenta != null -> {
                            val idSoat = vehiculoSoatId
                            var recordatorio by remember(recargaSoat, idSoat) { mutableStateOf<Boolean?>(null) }
                            var idPolizaSoat by remember(recargaSoat, idSoat) { mutableStateOf<Long?>(null) }
                            var fechaVenceSoat by remember(recargaSoat, idSoat) { mutableStateOf<String?>(null) }
                            LaunchedEffect(idSoat, cuenta.soat, recargaSoat) {
                                if (idSoat != null) {
                                    val actual = runCatching { cuenta.soat.consultar(idSoat).actual }.getOrNull()
                                    recordatorio = actual?.recordatorioActivo
                                    idPolizaSoat = actual?.idSoat
                                    fechaVenceSoat = actual?.fechaVencimiento
                                }
                            }
                            if (idSoat == null || idSoat <= 0) {
                                PantallaProximamente("Configurar SOAT", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                ConfigurarSoatScreen(
                                    onVolver = { volverDesdeConfigurarSoat() },
                                    onCorregir = { abrirSoat(idSoat, SecundariaMyCar.SOAT_CORREGIR) },
                                    onRenovar = {
                                        if (SoatFormulario.puedeRenovar(fechaVenceSoat)) {
                                            abrirSoat(idSoat, SecundariaMyCar.SOAT_RENOVAR)
                                        }
                                    },
                                    onRecordatorio = {
                                        soatIdRecordatorio = idPolizaSoat
                                        soatRecordatorioActivo = recordatorio == true
                                        soatFechaVencimiento = fechaVenceSoat
                                        navegar(navegacion.abrir(SecundariaMyCar.SOAT_RECORDATORIO))
                                    },
                                    recordatorioActivo = recordatorio,
                                    fechaVencimiento = fechaVenceSoat,
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.SOAT_CORREGIR && cuenta != null -> {
                            val idSoat = vehiculoSoatId
                            if (idSoat == null || idSoat <= 0) {
                                PantallaProximamente("Corregir SOAT", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                CorregirSoatLoader(
                                    "${cuenta.ciclo}|soat-cor-$idSoat|s$soatSesion",
                                    idSoat,
                                    cuenta.soat,
                                    onVolver = { navegar(navegacion.atras()) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onExito = {
                                        recargaSoat++
                                        recargaDetalle++
                                        // Tras editar: volver al hub Configurar (pantalla previa), no al detalle.
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.SOAT_CONFIGURAR))
                                    },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.SOAT_RENOVAR && cuenta != null -> {
                            val idVehiculoSoat = vehiculoSoatId
                            var soatAnteriorRen by remember(recargaSoat, idVehiculoSoat) {
                                mutableStateOf<SoatDto?>(null)
                            }
                            var cargaRenovar by remember(recargaSoat, idVehiculoSoat) { mutableStateOf(true) }
                            var errorRenovar by remember(recargaSoat, idVehiculoSoat) { mutableStateOf<String?>(null) }
                            LaunchedEffect(idVehiculoSoat, cuenta.soat, recargaSoat) {
                                cargaRenovar = true
                                errorRenovar = null
                                soatAnteriorRen = if (idVehiculoSoat != null) {
                                    runCatching { cuenta.soat.consultar(idVehiculoSoat).actual }
                                        .onFailure { errorRenovar = "No se pudo cargar el SOAT para renovar." }
                                        .getOrNull()
                                } else null
                                if (soatAnteriorRen == null && errorRenovar == null) {
                                    errorRenovar = "No hay SOAT actual para renovar."
                                }
                                cargaRenovar = false
                            }
                            val volverConfig = { navegar(navegacion.atras()) }
                            when {
                                idVehiculoSoat == null || idVehiculoSoat <= 0 -> {
                                    PantallaProximamente("Renovar SOAT", onVolver = { navegar(navegacion.atras()) })
                                }
                                cargaRenovar -> {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        TextButton(onClick = volverConfig) { Text("Volver") }
                                        Text("Cargando renovación…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        CircularProgressIndicator()
                                    }
                                }
                                errorRenovar != null || soatAnteriorRen?.idSoat == null -> {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TextButton(onClick = volverConfig) { Text("Volver") }
                                        Text(errorRenovar ?: "No hay SOAT actual para renovar.")
                                    }
                                }
                                !SoatFormulario.puedeRenovar(soatAnteriorRen?.fechaVencimiento) -> {
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TextButton(onClick = volverConfig) { Text("Volver") }
                                        Text(SoatFormulario.MSG_RENOVAR_TEMPRANO)
                                        Text(
                                            "Vencimiento actual: ${SoatFormulario.formatearFecha(soatAnteriorRen?.fechaVencimiento)}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                else -> {
                                    val idAnterior = soatAnteriorRen!!.idSoat
                                    fun trasRenovar(dto: SoatDto) {
                                        recargaSoat++
                                        recargaDetalle++
                                        soatIdRecordatorio = dto.idSoat
                                        soatRecordatorioActivo = dto.recordatorioActivo
                                        soatFechaVencimiento = dto.fechaVencimiento
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.SOAT_CONFIGURAR))
                                    }
                                    RenovarSoatRoute(
                                        "${cuenta.ciclo}|soat-ren-$idVehiculoSoat-$idAnterior|s$soatSesion",
                                        idVehiculoSoat,
                                        idAnterior,
                                        soatAnteriorRen,
                                        cuenta.soat,
                                        onVolver = volverConfig,
                                        onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                        onExito = ::trasRenovar,
                                        onRecordatorio = { dto ->
                                            soatIdRecordatorio = dto.idSoat
                                            soatRecordatorioActivo = dto.recordatorioActivo
                                            soatFechaVencimiento = dto.fechaVencimiento
                                            navegar(navegacion.abrir(SecundariaMyCar.SOAT_RECORDATORIO))
                                        },
                                        modifier = Modifier.statusBarsPadding()
                                    )
                                }
                            }
                        }
                        secundaria == SecundariaMyCar.SOAT_RECORDATORIO && cuenta != null -> {
                            val idV = vehiculoSoatId
                            val idS = soatIdRecordatorio
                            if (idV == null || idS == null) {
                                PantallaProximamente("Recordatorio SOAT", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                RecordatorioSoatRoute(
                                    "${cuenta.ciclo}|soat-rec-$idV-$idS|s$soatSesion",
                                    idV,
                                    idS,
                                    soatRecordatorioActivo,
                                    cuenta.soat,
                                    onVolver = { navegar(navegacion.atras()) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onGuardado = {
                                        recargaSoat++
                                        recargaDetalle++
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.SOAT_CONFIGURAR))
                                    },
                                    fechaVencimiento = soatFechaVencimiento,
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.TIVE_REGISTRAR && cuenta != null -> {
                            val idTive = vehiculoTiveId ?: vehiculoDetalleId
                            if (idTive == null || idTive <= 0) {
                                PantallaProximamente("Registrar TIVE", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                RegistrarTiveRoute(
                                    "${cuenta.ciclo}|tive-$idTive|s$tiveSesion|r$recargaTive",
                                    idTive,
                                    cuenta.tive,
                                    onVolver = { navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE)) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onExito = {
                                        recargaTive++
                                        recargaDetalle++
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.TIVE_CONSULTAR))
                                    },
                                    onCompletarVin = { completarVinParaTive(idTive) },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.TIVE_CONSULTAR && cuenta != null -> {
                            val idTive = vehiculoTiveId ?: vehiculoDetalleId
                            if (idTive == null || idTive <= 0) {
                                PantallaProximamente("Consultar TIVE", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                ConsultarTiveRoute(
                                    "${cuenta.ciclo}|tive-cons-$idTive|s$tiveSesion|r$recargaTive",
                                    idTive,
                                    cuenta.tive,
                                    onVolver = { navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE)) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onEditar = { abrirTive(idTive, SecundariaMyCar.TIVE_EDITAR) },
                                    onRegistrar = { abrirTive(idTive, SecundariaMyCar.TIVE_REGISTRAR) },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria == SecundariaMyCar.TIVE_EDITAR && cuenta != null -> {
                            val idTive = vehiculoTiveId ?: vehiculoDetalleId
                            if (idTive == null || idTive <= 0) {
                                PantallaProximamente("Editar TIVE", onVolver = { navegar(navegacion.atras()) })
                            } else {
                                EditarTiveRoute(
                                    "${cuenta.ciclo}|tive-edit-$idTive|s$tiveSesion|r$recargaTive",
                                    idTive,
                                    cuenta.tive,
                                    onVolver = { navegar(navegacion.copy(secundaria = SecundariaMyCar.TIVE_CONSULTAR)) },
                                    onIniciarSesion = { sesion.sesionRequerida(cuenta.ciclo) },
                                    onExito = {
                                        recargaTive++
                                        recargaDetalle++
                                        navegar(navegacion.copy(secundaria = SecundariaMyCar.TIVE_CONSULTAR))
                                    },
                                    modifier = Modifier.statusBarsPadding()
                                )
                            }
                        }
                        secundaria==SecundariaMyCar.NOTIFICACIONES && cuenta!=null -> {
                            val avatarBytes by cuenta.avatarPresentacion.collectAsStateWithLifecycle()
                            val iniciales by cuenta.inicialesPresentacion.collectAsStateWithLifecycle()
                            NotificacionesRoute(
                                repository = cuenta.notificaciones,
                                categoriaInicial = pushNotifCategoria,
                                idResaltar = pushNotifResaltar,
                                onVolver = {
                                    pushNotifResaltar = null
                                    pushNotifCategoria = null
                                    navegar(navegacion.atras())
                                },
                                onAbrirDetalleAlerta = { idVehiculo, idAlerta ->
                                    pushNotifResaltar = null
                                    vehiculoAlertas = idVehiculo
                                    detalleAlertaTrasAtencion = idAlerta
                                    navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
                                },
                                onAbrirSoatDesdeAviso = { idVehiculo ->
                                    pushNotifResaltar = null
                                    vehiculoDetalleId = idVehiculo
                                    abrirSoat(idVehiculo, SecundariaMyCar.SOAT_CONSULTAR)
                                },
                                onAbrirLicenciaDesdeAviso = {
                                    pushNotifResaltar = null
                                    perfilAbrirLicencia = true
                                    navegar(navegacion.abrir(SecundariaMyCar.PERFIL))
                                },
                                onAbrirKilometrajeDesdeAviso = { idVehiculo ->
                                    pushNotifResaltar = null
                                    abrirKilometraje(idVehiculo)
                                },
                                onPerfil = { navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                avatarBytes = avatarBytes,
                                iniciales = iniciales,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> PantallaProximamente(secundaria.titulo,onVolver={ navegar(navegacion.atras()) })
                    }
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
                                val nombrePresentacion by cuenta.nombrePresentacion.collectAsStateWithLifecycle()
                                val lifecycleOwner = LocalLifecycleOwner.current
                                DisposableEffect(lifecycleOwner, vm) {
                                    val observer = LifecycleEventObserver { _, event ->
                                        if (event == Lifecycle.Event.ON_RESUME) vm.cargar()
                                    }
                                    lifecycleOwner.lifecycle.addObserver(observer)
                                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                                }
                                // Tras KM u otras acciones: refrescar bloques sin esperar ON_RESUME.
                                LaunchedEffect(recargaInicio) {
                                    if (recargaInicio > 0) vm.cargar()
                                }
                                LaunchedEffect(nombrePresentacion) { vm.actualizarNombreUsuario(nombrePresentacion) }
                                LaunchedEffect(estado.fase) {
                                    if(estado.fase==FaseInicio.SIN_SESION) sesion.sesionRequerida(cuenta.ciclo)
                                }
                                val avatarBytes by cuenta.avatarPresentacion.collectAsStateWithLifecycle()
                                val iniciales by cuenta.inicialesPresentacion.collectAsStateWithLifecycle()
                                HomeScreen(estado,vm::seleccionar,
                                    onVehiculos={ navegar(navegacion.abrir(SeccionMyCar.VEHICULOS)) },
                                    onMantenimientos={ id ->
                                        vehiculoMantenimiento=id
                                        abrirHistorialEnTalleres=false
                                        navegar(navegacion.abrir(SeccionMyCar.MANTENIMIENTOS))
                                    },
                                    onAlertas={ navegar(navegacion.abrir(SeccionMyCar.ALERTAS)) },
                                    onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                    onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },onRecargar=vm::cargar,
                                    onVerVehiculo={ abrirDetalle(it) },
                                    onActualizarKm={ abrirKilometraje(it) },
                                    onTalleres={ id ->
                                        vehiculoMantenimiento=id
                                        abrirHistorialEnTalleres=true
                                        navegar(navegacion.abrir(SeccionMyCar.MANTENIMIENTOS))
                                    },
                                    onVerDetalleAlerta={ idAlerta ->
                                        val idVehiculo = estado.seleccionado
                                        if (idVehiculo != null) {
                                            vehiculoAlertas = idVehiculo
                                            detalleAlertaTrasAtencion = idAlerta
                                            navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
                                        }
                                    },
                                    fotosRepository = cuenta.fotos,
                                    avatarBytes = avatarBytes,
                                    iniciales = iniciales,
                                    modifier=Modifier.statusBarsPadding())
                            }
                            SeccionMyCar.VEHICULOS -> Column {
                                val avatarBytes by cuenta.avatarPresentacion.collectAsStateWithLifecycle()
                                val iniciales by cuenta.inicialesPresentacion.collectAsStateWithLifecycle()
                                VehiculosRoute(cuenta.ciclo,cuenta.registro,cuenta.listado,cuenta.pendientes,
                                    onIrInicio={ navegar(navegacion.atras()) },onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onVerDetalle={ abrirDetalle(it) },
                                    onCompartir={ id, marca, modelo -> abrirCompartir(id, marca, modelo) },
                                    estadoEnSistema=false,modifier=Modifier.weight(1f),onFormularioAbierto={ formularioAbierto=it },
                                    onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },
                                    onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                    onVerPlanes = {
                                        perfilAbrirPlanes = true
                                        navegar(navegacion.abrir(SecundariaMyCar.PERFIL))
                                    },
                                    fotosRepository = cuenta.fotos,
                                    avatarBytes = avatarBytes,
                                    iniciales = iniciales)
                            }
                            SeccionMyCar.MANTENIMIENTOS -> {
                                val idKmOverlay = kmOverlayMantenimiento
                                val avatarBytes by cuenta.avatarPresentacion.collectAsStateWithLifecycle()
                                val iniciales by cuenta.inicialesPresentacion.collectAsStateWithLifecycle()
                                Box(Modifier.fillMaxSize()) {
                                    FlujoHistorialMantenimiento(
                                        onVolverInicio={
                                            abrirHistorialEnTalleres=false
                                            navegar(navegacion.atras())
                                        }, historialRepository=cuenta.historial,
                                        mantenimientoRepository=cuenta.mantenimiento,
                                        kilometrajeRepository=cuenta.kilometraje,
                                        claveCuenta="${com.mycar.app.BuildConfig.API_BASE_URL}|${cuenta.usuario}",
                                        onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                        onActualizarKm={ abrirKmDesdeMantenimiento(it) },
                                        kmEncima = idKmOverlay != null,
                                        idVehiculoInicial=vehiculoMantenimiento, archivoPendiente=cuenta.archivoMantenimiento,
                                        estadoEnSistema=false,
                                        onRegistroAbierto={ formularioAbierto=it },
                                        onFormularioOverlay={ formularioAbierto=it },
                                        recargaRegistroToken = recargaMantenimiento,
                                        onProgramarAlerta = null,
                                        talleresRepository = cuenta.talleres,
                                        alertasRepository = cuenta.alertas,
                                        onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },
                                        onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                        abrirEnTalleres = abrirHistorialEnTalleres,
                                        onAbrirEnTalleresConsumido = { abrirHistorialEnTalleres = false },
                                        avatarBytes = avatarBytes,
                                        iniciales = iniciales,
                                        exportarRepository = cuenta.exportarHistorial,
                                        fotosRepository = cuenta.fotos,
                                        onVerPlanes = {
                                            perfilAbrirPlanes = true
                                            navegar(navegacion.abrir(SecundariaMyCar.PERFIL))
                                        },
                                        onVerMisAlertas = { idVehiculo ->
                                            vehiculoAlertas = idVehiculo
                                            alertasFiltroTodasTrasExito = true
                                            alertasOrdenRecientesTrasExito = true
                                            recargaAlertas++
                                            navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
                                        })
                                    if (idKmOverlay != null) {
                                        BackHandler { kmOverlayMantenimiento = null }
                                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                            ActualizarKilometrajeRoute(
                                                "${cuenta.ciclo}|mant-$idKmOverlay|s$kmSesion", idKmOverlay, cuenta.kilometraje,
                                                onVolver={ kmOverlayMantenimiento = null },
                                                onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                                onActualizado={
                                                    recargaInicio++
                                                    recargaMantenimiento++
                                                    kmOverlayMantenimiento = null
                                                },
                                                onIrInicio={
                                                    kmOverlayMantenimiento = null
                                                    navegar(NavegacionInicio(SeccionMyCar.INICIO, null))
                                                },
                                                modifier=Modifier.statusBarsPadding())
                                        }
                                    }
                                }
                            }
                            SeccionMyCar.ALERTAS -> {
                                val avatarBytes by cuenta.avatarPresentacion.collectAsStateWithLifecycle()
                                val iniciales by cuenta.inicialesPresentacion.collectAsStateWithLifecycle()
                                LaunchedEffect(cuenta.mantenimiento) {
                                    runCatching {
                                        val ctx = cuenta.mantenimiento.contexto()
                                        vehiculosAlertas = ctx.vehiculos.map {
                                            it.idVehiculo to "${it.marca} ${it.modelo} · ${it.placa}"
                                        }
                                        if (vehiculoAlertas == null) {
                                            vehiculoAlertas = vehiculoMantenimiento
                                                ?: vehiculosAlertas.firstOrNull()?.first
                                        }
                                    }
                                }
                                val idProgramar = alertasProgramarId
                                if (idProgramar != null) {
                                    ProgramarAlertaRoute(
                                        repository = cuenta.alertas,
                                        idVehiculo = idProgramar,
                                        vehiculos = vehiculosAlertas,
                                        sesionFormulario = alertasSesion,
                                        borradorInicial = borradorAlertaRf17,
                                        onVolver={
                                            alertasProgramarId = null
                                            borradorAlertaRf17 = null
                                        },
                                        onExito={
                                            // G108: volver a Alertas con el mismo vehículo y filtro Todas.
                                            alertasProgramarId = null
                                            borradorAlertaRf17 = null
                                            vehiculoAlertas = idProgramar
                                            alertasFiltroTodasTrasExito = true
                                            recargaAlertas++
                                        },
                                        onIrInicio={
                                            alertasProgramarId = null
                                            borradorAlertaRf17 = null
                                            navegar(NavegacionInicio(SeccionMyCar.INICIO, null))
                                        },
                                        modifier=Modifier.statusBarsPadding())
                                } else {
                                    AlertasRoute(
                                        cuenta.alertas, vehiculosAlertas, vehiculoAlertas,
                                        onProgramar={ abrirProgramarAlerta(it) },
                                        mantenimientoRepository = cuenta.mantenimiento,
                                        claveCuentaBorrador = "${com.mycar.app.BuildConfig.API_BASE_URL}|${cuenta.usuario}",
                                        archivoPendiente = cuenta.archivoMantenimiento,
                                        onFormularioAtencionAbierto = { formularioAtencionAlerta = it },
                                        detalleInicialId = detalleAlertaTrasAtencion,
                                        onDetalleInicialConsumido = { detalleAlertaTrasAtencion = null },
                                        recargaToken = recargaAlertas,
                                        reiniciarFiltroTodas = alertasFiltroTodasTrasExito,
                                        onReinicioFiltroConsumido = { alertasFiltroTodasTrasExito = false },
                                        aplicarOrdenRecientes = alertasOrdenRecientesTrasExito,
                                        onOrdenRecientesConsumido = { alertasOrdenRecientesTrasExito = false },
                                        talleresRepository = cuenta.talleres,
                                        onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },
                                        onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                        onAbrirSoatDesdeAlerta = { idVehiculo ->
                                            vehiculoDetalleId = idVehiculo
                                            abrirSoat(idVehiculo, SecundariaMyCar.SOAT_CONSULTAR)
                                        },
                                        onAbrirLicenciaDesdeAlerta = {
                                            perfilAbrirLicencia = true
                                            navegar(navegacion.abrir(SecundariaMyCar.PERFIL))
                                        },
                                        avatarBytes = avatarBytes,
                                        iniciales = iniciales,
                                        modifier=Modifier)
                                }
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
