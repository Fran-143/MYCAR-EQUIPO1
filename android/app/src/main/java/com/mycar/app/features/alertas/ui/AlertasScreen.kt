package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)
private val RojoAnular = Color(0xFFC62828)

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
    modifier: Modifier = Modifier
) {
    val vm: AlertasViewModel = viewModel(key = "rf23-lista-${vehiculos.map { it.first }.joinToString()}-$idVehiculoInicial") {
        AlertasViewModel(repository, vehiculos, idVehiculoInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    var detalleId by remember { mutableStateOf<Long?>(null) }
    var atencion by remember { mutableStateOf<ContextoDesdeAlerta?>(null) }
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
        val id = idVehiculoInicial ?: vehiculos.firstOrNull()?.first
        if (id != null) vm.seleccionar(id)
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
    // Detalle/atención RF24 solo en ámbito vehicular.
    when {
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
        estado.ambito == AmbitoAlertas.VEHICULOS && idDetalle != null && idVehiculo != null -> {
            DetalleAlertaRoute(
                repository = repository,
                idVehiculo = idVehiculo,
                idAlerta = idDetalle,
                onVolver = {
                    detalleId = null
                    vm.recargar()
                },
                onRegistrarComoRealizado = { d ->
                    abrirRegistro(d.id, idVehiculo, d.codigoTipo, d.servicioOtro)
                },
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
                    val v = estado.vehiculoId ?: return@AlertasScreen
                    abrirRegistro(idAlerta, v, codigo, servicio)
                },
                onAnular = vm::anular,
                onVerDetalle = { detalleId = it },
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
    modifier: Modifier = Modifier
) {
    val enVehiculos = estado.ambito == AmbitoAlertas.VEHICULOS
    Column(
        modifier.fillMaxSize().background(Fondo).statusBarsPadding().padding(16.dp),
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
                color = Azul
            )
            if (enVehiculos) {
                val id = estado.vehiculoId
                if (id != null && vehiculos.isNotEmpty()) {
                    Button(
                        onClick = { onProgramar(id) },
                        enabled = estado.fase != FaseAlertas.CARGANDO,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Celeste),
                        modifier = Modifier.testTag("rf23.programar")
                    ) { Text("+ Programar", fontWeight = FontWeight.SemiBold) }
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
                onVerDetalle = onVerDetalle
            )
        } else {
            ContenidoLicencia(estado = estado, onRecargar = onRecargar)
        }
    }
}

@Composable
private fun PestanasAmbitoAlertas(activa: AmbitoAlertas, onCambiar: (AmbitoAlertas) -> Unit) {
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
                    if (seleccionada) Celeste else Azul.copy(alpha = 0.25f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (seleccionada) Color.White else Color.Transparent,
                    contentColor = if (seleccionada) Celeste else Azul
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
    onVerDetalle: (Long) -> Unit
) {
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vehículo seleccionado", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
            if (vehiculos.size > 1) {
                var expandido by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expandido = true },
                        modifier = Modifier.fillMaxWidth().testTag("rf23.selectorVehiculo"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            vehiculos.firstOrNull { it.first == estado.vehiculoId }?.second ?: "Elegir vehículo",
                            modifier = Modifier.weight(1f),
                            color = Azul
                        )
                        Text("⌄")
                    }
                    DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                        vehiculos.forEach { (id, etiqueta) ->
                            DropdownMenuItem(
                                text = { Text(etiqueta) },
                                onClick = { expandido = false; onSeleccionar(id) }
                            )
                        }
                    }
                }
            } else {
                Text(
                    vehiculos.first().second,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Azul
                )
            }
        }
    }

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
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Azul,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = Azul
                )
            )
        }
    }

    when (estado.fase) {
        FaseAlertas.CARGANDO -> {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
            Text("Actualizando alertas…", color = Azul)
        }
        FaseAlertas.ERROR -> {
            Text(estado.mensaje ?: "No se pudieron cargar las alertas.", color = Azul)
            OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
        }
        FaseAlertas.VACIO -> {
            EstadoVacioCard(
                titulo = if (estado.filtro == FiltroAlertas.TODAS) "Sin alertas"
                else "Sin alertas «${estado.filtro.etiqueta.lowercase()}»",
                mensaje = estado.mensaje ?: EstadoAlertas.MENSAJE_SIN_ALERTAS_VEHICULO,
                modifier = Modifier.testTag("rf23.ea03.sinAlertas")
            )
        }
        FaseAlertas.LISTO -> {
            if (estado.sincronizadas > 0) {
                Text(
                    "Se actualizó el estado de ${estado.sincronizadas} alerta(s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6881A5)
                )
            }
            estado.avisoAccion?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Celeste)
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = true)
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
}

@Composable
private fun ColumnScope.ContenidoLicencia(estado: EstadoAlertas, onRecargar: () -> Unit) {
    Text(
        "Alertas de licencia de conducir",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Celeste,
        modifier = Modifier.testTag("rf23.licencia.titulo")
    )
    when (estado.faseLicencia) {
        FaseAlertas.CARGANDO -> {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
            Text("Consultando alertas de licencia…", color = Azul)
        }
        FaseAlertas.ERROR -> {
            Text(estado.mensajeLicencia ?: "No se pudieron cargar las alertas de licencia.", color = Azul)
            OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
        }
        FaseAlertas.VACIO -> {
            EstadoVacioCard(
                titulo = EstadoAlertas.TITULO_SIN_ALERTAS_LICENCIA,
                mensaje = estado.mensajeLicencia ?: EstadoAlertas.MENSAJE_SIN_ALERTAS_LICENCIA,
                modifier = Modifier.testTag("rf23.ea06.sinLicencia")
            )
        }
        FaseAlertas.LISTO -> {
            // Reservado para cuando RF51–56 exponga alertas personales reales.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = true)
            ) {
                items(estado.alertasLicencia, key = { it.id }) { a ->
                    TarjetaAlerta(a, onVerDetalle = {})
                }
            }
        }
    }
}

@Composable
private fun EstadoVacioCard(titulo: String, mensaje: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(titulo, fontWeight = FontWeight.Bold, color = Azul, textAlign = TextAlign.Center)
            Text(
                mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6881A5),
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
    val badge = colorEstado(a.estado)
    val atendible = a.estado in setOf("PROXIMA", "PENDIENTE", "VENCIDA")
    val anulable = a.estado in setOf("PROGRAMADA", "PROXIMA", "PENDIENTE", "VENCIDA")
    var dialogAnular by remember { mutableStateOf(false) }
    var motivoAnular by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    etiquetaServicioAlerta(a.codigoTipo, a.tipo, a.servicioOtro),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Azul,
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
            Text(etiquetaCriterio(a.criterio), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
            a.fechaObjetivo?.let {
                Text("Fecha objetivo: $it", style = MaterialTheme.typography.bodyMedium, color = Azul)
            }
            a.kmObjetivo?.let {
                Text(
                    "Km objetivo: %,d km".format(it).replace(',', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Azul
                )
            }
            // Etiqueta unificada en el título; no repetir servicio_otro debajo.
            TextButton(onClick = { onVerDetalle(a.id) }, colors = ButtonDefaults.textButtonColors(contentColor = Celeste)) {
                Text("Ver detalle →")
            }
            if (atendible || anulable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (atendible) {
                        Button(
                            onClick = onAtender,
                            enabled = !ocupado,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Azul)
                        ) { Text("Registrar como realizado") }
                    }
                    if (anulable) {
                        OutlinedButton(
                            onClick = { dialogAnular = true },
                            enabled = !ocupado,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RojoAnular)
                        ) { Text("Anular") }
                    }
                }
            }
        }
    }
    if (dialogAnular) {
        AlertDialog(
            onDismissRequest = { if (!ocupado) dialogAnular = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("¿Anular alerta?", fontWeight = FontWeight.Bold, color = Azul) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = RojoAnular),
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

private fun etiquetaEstado(estado: String): String = when (estado) {
    "PROGRAMADA" -> "Programada"
    "PROXIMA" -> "Próxima"
    "PENDIENTE" -> "Pendiente"
    "VENCIDA" -> "Vencida"
    "REALIZADA" -> "Realizada"
    else -> estado
}
