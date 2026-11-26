package com.mycar.app.features.alertas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DatosAlerta
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
import com.mycar.app.features.alertas.domain.etiquetaServicioAlerta
import com.mycar.app.features.alertas.viewmodel.AlertasViewModel
import com.mycar.app.features.alertas.viewmodel.AmbitoAlertas
import com.mycar.app.features.alertas.viewmodel.EstadoAlertas
import com.mycar.app.features.alertas.viewmodel.FaseAlertas
import com.mycar.app.features.alertas.viewmodel.FiltroAlertas
import com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente
import com.mycar.app.features.mantenimiento.domain.ContextoDesdeAlerta
import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import com.mycar.app.features.mantenimiento.ui.RegistrarMantenimientoRoute
import com.mycar.app.features.mantenimiento.viewmodel.AtenderAlertaDesdeFormulario
import com.mycar.app.ui.theme.MyCarStatusPendiente
import com.mycar.app.ui.theme.MyCarStatusProgramada
import com.mycar.app.ui.theme.MyCarStatusProxima
import com.mycar.app.ui.theme.MyCarStatusRealizada
import com.mycar.app.ui.theme.MyCarStatusVencida

private data class PaletaAlertas(
    val fondo: Color,
    val azul: Color,
    val celeste: Color,
    val superficie: Color,
    val muted: Color,
    val sobrePrimario: Color,
    val error: Color
)

@Composable
private fun paletaAlertas(): PaletaAlertas {
    val c = MaterialTheme.colorScheme
    return PaletaAlertas(
        fondo = c.background,
        azul = c.primary,
        celeste = c.secondary,
        superficie = c.surface,
        muted = c.onSurfaceVariant,
        sobrePrimario = c.onPrimary,
        error = c.error
    )
}

@Composable
fun AlertasRoute(
    repository: AlertasRepository,
    vehiculos: List<Pair<Long, String>>,
    idVehiculoInicial: Long?,
    onProgramar: (Long) -> Unit,
    /** RF24: repositorio RF17 para abrir el mismo formulario DESDE_ALERTA. */
    mantenimientoRepository: MantenimientoRepository? = null,
    claveCuentaBorrador: String? = null,
    archivoPendiente: ArchivoRegistroPendiente? = null,
    onFormularioAtencionAbierto: (Boolean) -> Unit = {},
    detalleInicialId: Long? = null,
    onDetalleInicialConsumido: () -> Unit = {},
    recargaToken: Int = 0,
    reiniciarFiltroTodas: Boolean = false,
    onReinicioFiltroConsumido: () -> Unit = {},
    talleresRepository: com.mycar.app.features.talleres.domain.TalleresRepository? = null,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    onAbrirSoatDesdeAlerta: ((idVehiculo: Long) -> Unit)? = null,
    onAbrirLicenciaDesdeAlerta: (() -> Unit)? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    modifier: Modifier = Modifier
) {
    val vm: AlertasViewModel = viewModel(key = "rf23-lista-${vehiculos.map { it.first }.joinToString()}-$idVehiculoInicial") {
        AlertasViewModel(repository, vehiculos, idVehiculoInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    var detalleId by remember { mutableStateOf<Long?>(null) }
    var atencion by remember { mutableStateOf<ContextoDesdeAlerta?>(null) }
    val listaVehiculos = rememberLazyListState()
    val listaLicencia = rememberLazyListState()
    BackHandler(enabled = detalleId != null && atencion == null) {
        detalleId = null
        vm.recargar()
    }
    LaunchedEffect(detalleInicialId) {
        if (detalleInicialId != null) {
            detalleId = detalleInicialId
            onDetalleInicialConsumido()
        }
    }
    LaunchedEffect(atencion) { onFormularioAtencionAbierto(atencion != null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && detalleId == null && atencion == null) vm.recargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vehiculos, idVehiculoInicial) {
        val id = idVehiculoInicial ?: vehiculos.firstOrNull()?.first ?: return@LaunchedEffect
        if (vm.estado.value.vehiculoId != id) vm.seleccionar(id)
    }
    LaunchedEffect(recargaToken, reiniciarFiltroTodas) {
        if (recargaToken > 0) {
            if (reiniciarFiltroTodas) {
                vm.reiniciarFiltroTodasYRecargar()
                onReinicioFiltroConsumido()
            } else {
                vm.recargar()
            }
        }
    }
    fun abrirRegistro(idAlerta: Long, idVehiculo: Long, codigoTipo: String, servicioOtro: String?) {
        if (codigoTipo.equals("SOAT", ignoreCase = true)) {
            detalleId = idAlerta
            return // SOAT no usa Atender RF24; ver detalle / Configurar SOAT.
        }
        if (mantenimientoRepository == null || claveCuentaBorrador.isNullOrBlank()) return
        if (codigoTipo.isBlank()) {
            detalleId = idAlerta
            return
        }
        detalleId = null
        atencion = ContextoDesdeAlerta(idAlerta, idVehiculo, codigoTipo, servicioOtro)
    }
    val ctxAtencion = atencion
    val idDetalle = detalleId
    val idVehiculo = estado.vehiculoId
    when {
        estado.ambito == AmbitoAlertas.LICENCIA && idDetalle != null -> {
            DetalleAlertaLicenciaRoute(
                repository = repository,
                idAlerta = idDetalle,
                onVolver = {
                    detalleId = null
                    vm.recargar()
                },
                onAbrirLicencia = {
                    detalleId = null
                    onAbrirLicenciaDesdeAlerta?.invoke()
                },
                modifier = modifier
            )
        }
        estado.ambito == AmbitoAlertas.VEHICULOS &&
            ctxAtencion != null && mantenimientoRepository != null && !claveCuentaBorrador.isNullOrBlank() -> {
            RegistrarMantenimientoRoute(
                repository = mantenimientoRepository,
                claveCuentaBorrador = "$claveCuentaBorrador|rf24|${ctxAtencion.idAlerta}",
                onCerrar = {
                    val id = ctxAtencion.idAlerta
                    atencion = null
                    detalleId = id
                    vm.recargar()
                },
                idVehiculoInicial = ctxAtencion.idVehiculo,
                archivoPendiente = archivoPendiente,
                estadoEnSistema = false,
                desdeAlerta = ctxAtencion,
                atenderAlerta = AtenderAlertaDesdeFormulario { sol: SolicitudAtenderAlerta ->
                    repository.atender(ctxAtencion.idVehiculo, ctxAtencion.idAlerta, sol)
                },
                talleresRepository = talleresRepository,
                modifier = modifier
            )
        }
        estado.ambito == AmbitoAlertas.VEHICULOS && idDetalle != null &&
            (estado.idVehiculoDeAlerta(idDetalle) ?: idVehiculo) != null -> {
            val idVehiculoDetalle = estado.idVehiculoDeAlerta(idDetalle) ?: idVehiculo!!
            DetalleAlertaRoute(
                repository = repository,
                idVehiculo = idVehiculoDetalle,
                idAlerta = idDetalle,
                onVolver = {
                    detalleId = null
                    vm.recargar()
                },
                onRegistrarComoRealizado = { d ->
                    abrirRegistro(d.id, idVehiculoDetalle, d.codigoTipo, d.servicioOtro)
                },
                onAbrirSoat = onAbrirSoatDesdeAlerta?.let { cb -> { cb(idVehiculoDetalle) } },
                talleresRepository = talleresRepository,
                modifier = modifier
            )
        }
        else -> {
            AlertasScreen(
                estado = estado,
                vehiculos = vehiculos,
                onAmbito = vm::cambiarAmbito,
                onSeleccionar = vm::seleccionar,
                onFiltro = vm::cambiarFiltro,
                onRecargar = vm::recargar,
                onProgramar = onProgramar,
                onAtenderDesdeFormulario = { idAlerta, codigo, servicio ->
                    val v = estado.idVehiculoDeAlerta(idAlerta) ?: return@AlertasScreen
                    abrirRegistro(idAlerta, v, codigo, servicio)
                },
                onAnular = vm::anular,
                onVerDetalle = { detalleId = it },
                listaVehiculos = listaVehiculos,
                listaLicencia = listaLicencia,
                onNotificaciones = onNotificaciones,
                onPerfil = onPerfil,
                avatarBytes = avatarBytes,
                iniciales = iniciales,
                modifier = modifier
            )
        }
    }
}

@Composable
fun AlertasScreen(
    estado: EstadoAlertas,
    vehiculos: List<Pair<Long, String>>,
    onAmbito: (AmbitoAlertas) -> Unit,
    onSeleccionar: (Long) -> Unit,
    onFiltro: (FiltroAlertas) -> Unit,
    onRecargar: () -> Unit,
    onProgramar: (Long) -> Unit,
    onAtenderDesdeFormulario: (Long, String, String?) -> Unit = { _, _, _ -> },
    onAnular: (Long, String) -> Unit = { _, _ -> },
    onVerDetalle: (Long) -> Unit = {},
    listaVehiculos: LazyListState,
    listaLicencia: LazyListState,
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    modifier: Modifier = Modifier
) {
    val p = paletaAlertas()
    val enVehiculos = estado.ambito == AmbitoAlertas.VEHICULOS
    Column(modifier.fillMaxSize().background(p.fondo)) {
        com.mycar.app.ui.MycarCabeceraApp(
            onNotificaciones = onNotificaciones,
            onPerfil = onPerfil,
            avatarBytes = avatarBytes,
            iniciales = iniciales
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Alertas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = p.azul
            )
            if (enVehiculos) {
                val id = estado.vehiculoId
                val puedeProgramar = id != null &&
                    id != EstadoAlertas.ID_TODOS_VEHICULOS &&
                    vehiculos.isNotEmpty()
                if (vehiculos.isNotEmpty()) {
                    Button(
                        onClick = { id?.let(onProgramar) },
                        enabled = puedeProgramar && estado.fase != FaseAlertas.CARGANDO,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = p.azul,
                            contentColor = p.sobrePrimario
                        ),
                        modifier = Modifier.testTag("rf23.programar")
                    ) { Text("Programar alerta", fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        PestanasAmbitoAlertas(activa = estado.ambito, onCambiar = onAmbito)

        if (enVehiculos) {
            ContenidoVehiculos(
                estado = estado,
                vehiculos = vehiculos,
                onSeleccionar = onSeleccionar,
                onFiltro = onFiltro,
                onRecargar = onRecargar,
                onAtenderDesdeFormulario = onAtenderDesdeFormulario,
                onAnular = onAnular,
                onVerDetalle = onVerDetalle,
                listaState = listaVehiculos
            )
        } else {
            ContenidoLicencia(
                estado = estado,
                onRecargar = onRecargar,
                onVerDetalle = onVerDetalle,
                listaState = listaLicencia
            )
        }
        }
    }
}

@Composable
private fun PestanasAmbitoAlertas(activa: AmbitoAlertas, onCambiar: (AmbitoAlertas) -> Unit) {
    val p = paletaAlertas()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            AmbitoAlertas.VEHICULOS to ("Vehículos" to "rf23.tab.vehiculos"),
            AmbitoAlertas.LICENCIA to ("Licencia" to "rf23.tab.licencia")
        ).forEach { (ambito, etiqueta) ->
            val seleccionada = ambito == activa
            OutlinedButton(
                onClick = { onCambiar(ambito) },
                modifier = Modifier.weight(1f).testTag(etiqueta.second),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                border = BorderStroke(
                    1.dp,
                    if (seleccionada) p.celeste else p.azul.copy(alpha = 0.25f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (seleccionada) p.superficie else Color.Transparent,
                    contentColor = if (seleccionada) p.celeste else p.azul
                )
            ) {
                Text(
                    etiqueta.first.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ContenidoVehiculos(
    estado: EstadoAlertas,
    vehiculos: List<Pair<Long, String>>,
    onSeleccionar: (Long) -> Unit,
    onFiltro: (FiltroAlertas) -> Unit,
    onRecargar: () -> Unit,
    onAtenderDesdeFormulario: (Long, String, String?) -> Unit,
    onAnular: (Long, String) -> Unit,
    onVerDetalle: (Long) -> Unit,
    listaState: LazyListState
) {
    val p = paletaAlertas()
    val deltaFiltro = remember { mutableFloatStateOf(0f) }
    val mostrarFiltros by remember {
        derivedStateOf {
            val enTope = !listaState.canScrollBackward
            enTope || deltaFiltro.floatValue > -24f
        }
    }
    val conexionFiltros = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val dy = consumed.y
                when {
                    dy > 0.5f -> deltaFiltro.floatValue = 0f
                    dy < -0.5f -> deltaFiltro.floatValue =
                        (deltaFiltro.floatValue + dy).coerceAtLeast(-80f)
                }
                return Offset.Zero
            }
        }
    }
    if (vehiculos.isEmpty()) {
        EstadoVacioCard(
            titulo = EstadoAlertas.TITULO_SIN_VEHICULOS,
            mensaje = EstadoAlertas.MENSAJE_SIN_VEHICULOS,
            modifier = Modifier.testTag("rf23.ea01.sinVehiculos")
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vehículo", style = MaterialTheme.typography.labelMedium, color = p.muted)
            var expandido by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandido = true },
                    modifier = Modifier.fillMaxWidth().testTag("rf23.selectorVehiculo"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when {
                            estado.esTodosVehiculos -> "Todos mis vehículos"
                            else -> vehiculos.firstOrNull { it.first == estado.vehiculoId }?.second
                                ?: "Elegir vehículo"
                        },
                        modifier = Modifier.weight(1f),
                        color = p.azul,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("⌄")
                }
                DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                    if (vehiculos.size > 1) {
                        DropdownMenuItem(
                            text = { Text("Todos mis vehículos") },
                            onClick = {
                                expandido = false
                                onSeleccionar(EstadoAlertas.ID_TODOS_VEHICULOS)
                            },
                            modifier = Modifier.testTag("rf23.vehiculo.todos")
                        )
                    }
                    vehiculos.forEach { (id, etiqueta) ->
                        DropdownMenuItem(
                            text = { Text(etiqueta) },
                            onClick = { expandido = false; onSeleccionar(id) }
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = mostrarFiltros,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FiltroAlertas.entries.forEach { f ->
                FilterChip(
                    selected = estado.filtro == f,
                    onClick = { onFiltro(f) },
                    enabled = estado.fase != FaseAlertas.CARGANDO,
                    label = { Text(f.etiqueta) },
                    leadingIcon = {
                        Icon(
                            iconoFiltroAlerta(f),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = p.azul,
                        selectedLabelColor = p.sobrePrimario,
                        selectedLeadingIconColor = p.sobrePrimario,
                        containerColor = p.superficie,
                        labelColor = p.azul,
                        iconColor = p.azul
                    )
                )
            }
        }
    }

    when (estado.fase) {
        FaseAlertas.CARGANDO -> {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.celeste)
            if (estado.visibles.isEmpty()) {
                Text("Actualizando alertas…", color = p.azul)
            }
        }
        FaseAlertas.ERROR -> {
            Text(estado.mensaje ?: "No se pudieron cargar las alertas.", color = p.azul)
            OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
        }
        FaseAlertas.VACIO -> {
            if (estado.visibles.isEmpty()) {
                EstadoVacioCard(
                    titulo = if (estado.filtro == FiltroAlertas.TODAS) "Sin alertas"
                    else "Sin alertas «${estado.filtro.etiqueta.lowercase()}»",
                    mensaje = estado.mensaje ?: EstadoAlertas.MENSAJE_SIN_ALERTAS_VEHICULO,
                    modifier = Modifier.testTag("rf23.ea03.sinAlertas")
                )
            }
        }
        FaseAlertas.LISTO -> {
            if (estado.sincronizadas > 0) {
                Text(
                    "Se actualizó el estado de ${estado.sincronizadas} alerta(s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted
                )
            }
            estado.avisoAccion?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = p.celeste)
            }
        }
    }
    if (estado.visibles.isNotEmpty()) {
        LazyColumn(
            state = listaState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = true).nestedScroll(conexionFiltros)
        ) {
            items(estado.visibles, key = { it.id }) { a ->
                TarjetaAlerta(
                    a,
                    ocupado = estado.ocupadoAccion,
                    onAtender = { onAtenderDesdeFormulario(a.id, a.codigoTipo, a.servicioOtro) },
                    onAnular = onAnular,
                    onVerDetalle = onVerDetalle
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ContenidoLicencia(
    estado: EstadoAlertas,
    onRecargar: () -> Unit,
    onVerDetalle: (Long) -> Unit = {},
    listaState: LazyListState
) {
    val p = paletaAlertas()
    Text(
        "Alertas de licencia de conducir",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = p.celeste,
        modifier = Modifier.testTag("rf23.licencia.titulo")
    )
    when (estado.faseLicencia) {
        FaseAlertas.CARGANDO -> {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.celeste)
            if (estado.alertasLicencia.isEmpty()) {
                Text("Consultando alertas de licencia…", color = p.azul)
            }
        }
        FaseAlertas.ERROR -> {
            Text(estado.mensajeLicencia ?: "No se pudieron cargar las alertas de licencia.", color = p.azul)
            OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
        }
        FaseAlertas.VACIO -> {
            if (estado.alertasLicencia.isEmpty()) {
                EstadoVacioCard(
                    titulo = EstadoAlertas.TITULO_SIN_ALERTAS_LICENCIA,
                    mensaje = estado.mensajeLicencia ?: EstadoAlertas.MENSAJE_SIN_ALERTAS_LICENCIA,
                    modifier = Modifier.testTag("rf23.ea06.sinLicencia")
                )
            }
        }
        FaseAlertas.LISTO -> {
            if (estado.sincronizadasLicencia > 0) {
                Text(
                    "Se actualizó el estado de ${estado.sincronizadasLicencia} alerta(s) de licencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted
                )
            }
        }
    }
    if (estado.alertasLicencia.isNotEmpty()) {
        LazyColumn(
            state = listaState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = true)
        ) {
            items(estado.alertasLicencia, key = { it.id }) { a ->
                TarjetaAlerta(a, onVerDetalle = onVerDetalle)
            }
        }
    }
}

@Composable
private fun EstadoVacioCard(titulo: String, mensaje: String, modifier: Modifier = Modifier) {
    val p = paletaAlertas()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = p.superficie),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(titulo, fontWeight = FontWeight.Bold, color = p.azul, textAlign = TextAlign.Center)
            Text(
                mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TarjetaAlerta(
    a: DatosAlerta,
    ocupado: Boolean = false,
    onAtender: () -> Unit = {},
    onAnular: (Long, String) -> Unit = { _, _ -> },
    onVerDetalle: (Long) -> Unit = {}
) {
    val p = paletaAlertas()
    val badge = colorEstado(a.estado)
    val atendible = puedeAtenderRf24(a)
    val anulable = puedeAnularRf24(a)
    var dialogAnular by remember { mutableStateOf(false) }
    var motivoAnular by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = p.superficie),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = p.azul.copy(alpha = 0.08f)) {
                    Icon(
                        iconoServicioAlerta(a),
                        contentDescription = null,
                        tint = p.celeste,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Text(
                    etiquetaServicioAlerta(a.codigoTipo, a.tipo, a.servicioOtro),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = p.azul,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = badge.copy(alpha = 0.15f)) {
                    Text(
                        etiquetaEstado(a.estado).uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badge
                    )
                }
            }
            Text(etiquetaCriterio(a.criterio), style = MaterialTheme.typography.bodySmall, color = p.muted)
            a.fechaObjetivo?.let {
                Text("Fecha objetivo: $it", style = MaterialTheme.typography.bodyMedium, color = p.azul)
            }
            a.kmObjetivo?.let {
                Text(
                    "Km objetivo: %,d km".format(it).replace(',', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.azul
                )
            }
            // Etiqueta unificada en el título; no repetir servicio_otro debajo.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onVerDetalle(a.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = p.celeste)
                ) { Text("Ver detalle") }
                if (anulable) {
                    OutlinedButton(
                        onClick = { dialogAnular = true },
                        enabled = !ocupado,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.error)
                    ) { Text("Anular") }
                }
            }
            if (atendible) {
                Button(
                    onClick = onAtender,
                    enabled = !ocupado,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.azul,
                        contentColor = p.sobrePrimario
                    )
                ) { Text("Registrar como realizado") }
            }
        }
    }
    if (dialogAnular) {
        AlertDialog(
            onDismissRequest = { if (!ocupado) dialogAnular = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("¿Anular alerta?", fontWeight = FontWeight.Bold, color = p.azul) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La alerta quedará ANULADA. El mantenimiento histórico de referencia no se anula.")
                    OutlinedTextField(
                        value = motivoAnular,
                        onValueChange = { if (it.length <= 48) motivoAnular = it },
                        label = { Text("Motivo") },
                        singleLine = true,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = motivoAnular.trim()
                        if (m.isNotEmpty()) {
                            dialogAnular = false
                            onAnular(a.id, m)
                            motivoAnular = ""
                        }
                    },
                    enabled = !ocupado && motivoAnular.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Anular") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { dialogAnular = false; motivoAnular = "" },
                    enabled = !ocupado,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cancelar") }
            }
        )
    }
}

/** RF24: SOAT y licencia no se anulan; el resto solo en PROGRAMADA, PRÓXIMA, PENDIENTE o VENCIDA. */
private fun puedeAnularRf24(a: DatosAlerta): Boolean {
    if (a.codigoTipo.equals("SOAT", ignoreCase = true) || a.codigoTipo.equals("LICENCIA", ignoreCase = true)) {
        return false
    }
    return a.estado in setOf("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA")
}

/** RF24: atender solo PRÓXIMA, PENDIENTE o VENCIDA, y nunca documentos SOAT/licencia. */
private fun puedeAtenderRf24(a: DatosAlerta): Boolean {
    if (a.codigoTipo.equals("SOAT", ignoreCase = true) || a.codigoTipo.equals("LICENCIA", ignoreCase = true)) {
        return false
    }
    return a.estado in setOf("PROXIMA", "PENDIENTE", "VENCIDA")
}

private fun colorEstado(estado: String): Color = when (estado) {
    "PROGRAMADA" -> MyCarStatusProgramada
    "PROXIMA" -> MyCarStatusProxima
    "PENDIENTE" -> MyCarStatusPendiente
    "VENCIDA" -> MyCarStatusVencida
    "REALIZADA" -> MyCarStatusRealizada
    else -> MyCarStatusRealizada
}

private fun etiquetaCriterio(criterio: String): String = when (criterio) {
    "POR_TIEMPO" -> "Por tiempo"
    "POR_KILOMETRAJE" -> "Por kilometraje"
    "AMBOS" -> "Por tiempo y kilometraje"
    else -> criterio
}

private fun iconoFiltroAlerta(filtro: FiltroAlertas): ImageVector = when (filtro) {
    FiltroAlertas.TODAS -> Icons.Outlined.Notifications
    FiltroAlertas.PROGRAMADAS -> Icons.Outlined.NotificationsNone
    FiltroAlertas.PROXIMAS -> Icons.Outlined.NotificationsActive
    FiltroAlertas.PENDIENTES -> Icons.Outlined.NotificationImportant
    FiltroAlertas.VENCIDAS -> Icons.Outlined.Alarm
    FiltroAlertas.REALIZADAS -> Icons.Outlined.DoneAll
}

private fun iconoServicioAlerta(a: DatosAlerta): ImageVector {
    val codigo = a.codigoTipo.uppercase()
    return when {
        codigo.contains("NEUMAT") || codigo.contains("LLANTA") ||
            codigo.contains("ALINEA") || codigo.contains("BALANCE") -> Icons.Outlined.Speed
        codigo.contains("FRENO") || codigo.contains("TRANSM") ||
            codigo.contains("EMBRAG") || codigo.contains("CAJA") -> Icons.Outlined.Settings
        codigo == "SOAT" || codigo == "LICENCIA" -> Icons.Outlined.Schedule
        a.criterio == "POR_KILOMETRAJE" -> Icons.Outlined.Speed
        a.criterio == "POR_TIEMPO" -> Icons.Outlined.Schedule
        a.criterio == "AMBOS" -> Icons.Outlined.Settings
        else -> Icons.Outlined.Build
    }
}

private fun etiquetaEstado(estado: String): String = when (estado) {
    "PROGRAMADA" -> "Programada"
    "PROXIMA" -> "Próxima"
    "PENDIENTE" -> "Pendiente"
    "VENCIDA" -> "Vencida"
    "REALIZADA" -> "Realizada"
    else -> estado
}
