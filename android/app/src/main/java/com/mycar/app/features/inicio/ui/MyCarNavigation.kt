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
import com.mycar.app.features.alertas.ui.AlertasRoute
import com.mycar.app.features.alertas.ui.ProgramarAlertaRoute
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.autenticacion.ui.LocalConfirmedLogout
import com.mycar.app.features.historial.ui.FlujoHistorialMantenimiento
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.inicio.viewmodel.ResumenInicioViewModel
import com.mycar.app.features.mantenimiento.viewmodel.OfertaProgramarAlerta
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
    pushIdAlerta: Long? = null
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
    }
    var seccionNombre by rememberSaveable { mutableStateOf(SeccionMyCar.INICIO.name) }
    var secundariaNombre by rememberSaveable { mutableStateOf<String?>(null) }
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
    var recargaAlertas by rememberSaveable { mutableIntStateOf(0) }
    var alertasFiltroTodasTrasExito by rememberSaveable { mutableStateOf(false) }
    var formularioAbierto by remember { mutableStateOf(false) }
    var formularioAtencionAlerta by remember { mutableStateOf(false) }
    var detalleAlertaTrasAtencion by rememberSaveable { mutableStateOf<Long?>(null) }
    var perfilAbrirLicencia by rememberSaveable { mutableStateOf(false) }
    var borradorAlertaRf17 by remember { mutableStateOf<BorradorAlerta?>(null) }
    val navegacion=NavegacionInicio(SeccionMyCar.valueOf(seccionNombre),secundariaNombre?.let(SecundariaMyCar::valueOf))
    fun navegar(n: NavegacionInicio) { seccionNombre=n.seccion.name; secundariaNombre=n.secundaria?.name; formularioAbierto=false }
    LaunchedEffect(solicitudVehiculos) { if(solicitudVehiculos>0) navegar(navegacion.abrir(SeccionMyCar.VEHICULOS)) }
    // Deep link FCM: abre alertas del vehículo o la secundaria Notificaciones (placeholder en esta rama).
    LaunchedEffect(solicitudesNotificaciones, pushIdVehiculo, pushIdAlerta, cuenta?.ciclo) {
        if (solicitudesNotificaciones <= 0 || cuenta == null) return@LaunchedEffect
        if (pushIdVehiculo != null) {
            vehiculoAlertas = pushIdVehiculo
            navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
        } else {
            navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES))
        }
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
    val esquema=lightColorScheme(primary=Color(0xFF079FDC),onPrimary=Color.White,
        primaryContainer=Color(0xFFE2F5FF),onPrimaryContainer=Color(0xFF163468),
        background=Color(0xFFF2FAFF),surface=Color.White,onSurface=Color(0xFF163468))
    MaterialTheme(colorScheme=esquema) {
        Scaffold(contentWindowInsets=WindowInsets(0),bottomBar={
            if(navegacion.secundaria==null && !formularioAbierto && !formularioAtencionAlerta && kmOverlayMantenimiento==null && alertasProgramarId==null)
                NavigationBar(containerColor=Color.White) {
                SeccionMyCar.entries.forEach { destino ->
                    val icono=when(destino) {
                        SeccionMyCar.INICIO -> R.drawable.ic_inicio_inicio
                        SeccionMyCar.VEHICULOS -> R.drawable.ic_vehiculo_predeterminado_rf08
                        SeccionMyCar.MANTENIMIENTOS -> R.drawable.ic_inicio_llave
                        SeccionMyCar.ALERTAS -> R.drawable.ic_inicio_campana
                    }
                    NavigationBarItem(selected=navegacion.seccion==destino,onClick={
                        if (destino==SeccionMyCar.MANTENIMIENTOS) abrirHistorialEnTalleres=false
                        navegar(navegacion.abrir(destino))
                    },
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
                                        if (kmOrigenDetalle && vehiculoDetalleId == idKm) {
                                            kmOrigenDetalle = false
                                            navegar(navegacion.copy(secundaria = SecundariaMyCar.DETALLE))
                                        } else {
                                            vehiculoKmId = null
                                            navegar(navegacion.atras())
                                        }
                                    },
                                    onIniciarSesion={ sesion.sesionRequerida(cuenta.ciclo) },
                                    onActualizado={
                                        recargaInicio++
                                        recargaAlertas++
                                        if (kmOrigenDetalle && vehiculoDetalleId == idKm) recargaDetalle++
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
                            LaunchedEffect(estadoPerfilCabecera.perfil?.iniciales, estadoPerfilCabecera.fotoBytes) {
                                cuenta.inicialesPresentacion.value = estadoPerfilCabecera.perfil?.iniciales
                                cuenta.avatarPresentacion.value = estadoPerfilCabecera.fotoBytes
                            }
                            LaunchedEffect(perfilAbrirLicencia) {
                                if (perfilAbrirLicencia) {
                                    perfilVm.abrirLicencia()
                                    perfilAbrirLicencia = false
                                }
                            }
                            PerfilRoute(
                                vm = perfilVm,
                                preferenciasNotificacion = cuenta.preferenciasNotificacion,
                                licenciaRepository = cuenta.licencia,
                                soporteRepository = cuenta.soporte,
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
                            NotificacionesRoute(
                                repository = cuenta.notificaciones,
                                onVolver = { navegar(navegacion.atras()) },
                                onAbrirDetalleAlerta = { idVehiculo, idAlerta ->
                                    vehiculoAlertas = idVehiculo
                                    detalleAlertaTrasAtencion = idAlerta
                                    navegar(navegacion.abrir(SeccionMyCar.ALERTAS))
                                },
                                onAbrirSoatDesdeAviso = { idVehiculo ->
                                    vehiculoDetalleId = idVehiculo
                                    abrirSoat(idVehiculo, SecundariaMyCar.SOAT_CONSULTAR)
                                },
                                onAbrirLicenciaDesdeAviso = {
                                    perfilAbrirLicencia = true
                                    navegar(navegacion.abrir(SecundariaMyCar.PERFIL))
                                },
                                onPerfil = { navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
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
                                LaunchedEffect(vm, recargaInicio) { vm.cargar() }
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
                                    fotosRepository = cuenta.fotos,
                                    avatarBytes = avatarBytes,
                                    iniciales = iniciales)
                            }
                            SeccionMyCar.MANTENIMIENTOS -> {
                                val idKmOverlay = kmOverlayMantenimiento
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
                                        idVehiculoInicial=vehiculoMantenimiento, archivoPendiente=cuenta.archivoMantenimiento,
                                        estadoEnSistema=false, onRegistroAbierto={ formularioAbierto=it },
                                        recargaRegistroToken = recargaMantenimiento,
                                        onProgramarAlerta = { abrirProgramarDesdeRf17(it) },
                                        talleresRepository = cuenta.talleres,
                                        onNotificaciones={ navegar(navegacion.abrir(SecundariaMyCar.NOTIFICACIONES)) },
                                        onPerfil={ navegar(navegacion.abrir(SecundariaMyCar.PERFIL)) },
                                        abrirEnTalleres = abrirHistorialEnTalleres,
                                        onAbrirEnTalleresConsumido = { abrirHistorialEnTalleres = false })
                                    if (idKmOverlay != null) {
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
                                                modifier=Modifier.statusBarsPadding())
                                        }
                                    }
                                }
                            }
                            SeccionMyCar.ALERTAS -> {
                                var vehiculosAlertas by remember(cuenta.ciclo) {
                                    mutableStateOf<List<Pair<Long, String>>>(emptyList())
                                }
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
                                        modifier=Modifier)
                                }
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
