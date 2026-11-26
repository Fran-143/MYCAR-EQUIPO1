package com.mycar.app.features.recordatorioskm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import com.mycar.app.features.fotos.ui.ImagenVehiculoPredeterminada
import com.mycar.app.features.fotos.ui.MiniaturaFotoVehiculo
import com.mycar.app.features.recordatorioskm.domain.FrecuenciaKm
import com.mycar.app.features.recordatorioskm.domain.RecordatoriosKmRepository
import com.mycar.app.features.recordatorioskm.domain.VehiculoRecordatorioKm
import com.mycar.app.features.recordatorioskm.viewmodel.RecordatoriosKmViewModel
import com.mycar.app.ui.theme.MyCarBg
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarSky
import com.mycar.app.ui.theme.MyCarStatusActivo
import com.mycar.app.ui.theme.MyCarTeal
import com.mycar.app.ui.theme.MyCarTile
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun RecordatoriosKmRoute(
    repository: RecordatoriosKmRepository,
    fotos: FotoPersonalizadaRepository? = null,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: RecordatoriosKmViewModel = viewModel(factory = RecordatoriosKmViewModel.factory(repository))
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.cargar()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            vm.cargar()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }
    BackHandler { vm.intentarSalir(onVolver) }
    RecordatoriosKmScreen(
        estado = estado,
        fotos = fotos,
        onVolver = { vm.intentarSalir(onVolver) },
        onToggle = vm::setActivo,
        onFrecuencia = vm::setFrecuenciaLocal,
        onGuardar = vm::guardarPreferencia,
        onCerrarExito = vm::cerrarModalExito,
        onSeguirEditando = vm::seguirEditando,
        onSalirSinGuardar = vm::salirSinGuardar,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordatoriosKmScreen(
    estado: com.mycar.app.features.recordatorioskm.viewmodel.EstadoRecordatoriosKm,
    fotos: FotoPersonalizadaRepository?,
    onVolver: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onFrecuencia: (Long, FrecuenciaKm) -> Unit,
    onGuardar: (Long) -> Unit,
    onCerrarExito: () -> Unit,
    onSeguirEditando: () -> Unit,
    onSalirSinGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize().background(MyCarBg),
        containerColor = MyCarBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MyCarTile,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Speed,
                                    contentDescription = null,
                                    tint = MyCarTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "Recordatorios de kilometraje",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MyCarNavy,
                            maxLines = 2
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MyCarNavy
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MyCarBg,
                    scrolledContainerColor = MyCarBg
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        if (estado.cargando && estado.vehiculos.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MyCarNavy)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Ciclo de recordatorios",
                                fontWeight = FontWeight.SemiBold,
                                color = MyCarNavy
                            )
                            Text(
                                if (estado.activo) "Activo · se enviarán avisos según la frecuencia"
                                else "Pausado · no se generan avisos nuevos",
                                color = MyCarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (estado.guardandoGlobal) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = MyCarNavy, strokeWidth = 2.dp)
                        } else {
                            Switch(
                                checked = estado.activo,
                                onCheckedChange = onToggle,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MyCarNavy,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }
                Text(
                    "Configura la frecuencia por vehículo activo",
                    color = MyCarMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                estado.mensajeError?.let {
                    Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
                }
                if (estado.vehiculos.isEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                        Text(
                            "No tienes vehículos activos para configurar recordatorios.",
                            Modifier.padding(16.dp),
                            color = MyCarMuted
                        )
                    }
                }
                estado.vehiculos.forEach { v ->
                    val freq = estado.frecuenciasLocales[v.idVehiculo] ?: v.frecuencia
                    TarjetaVehiculoRecordatorio(
                        vehiculo = v,
                        frecuencia = freq,
                        habilitado = estado.activo && !estado.guardandoGlobal,
                        guardando = estado.guardandoId == v.idVehiculo,
                        fotos = fotos,
                        onFrecuencia = { onFrecuencia(v.idVehiculo, it) },
                        onGuardar = { onGuardar(v.idVehiculo) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    estado.modalExitoNombre?.let { nombre ->
        AlertDialog(
            onDismissRequest = onCerrarExito,
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(Icons.Filled.CheckCircle, null, tint = MyCarStatusActivo, modifier = Modifier.size(40.dp))
            },
            title = {
                Text(
                    "¡Preferencia guardada!",
                    fontWeight = FontWeight.Bold,
                    color = MyCarNavy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "El recordatorio de kilometraje se configuró correctamente para $nombre.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = onCerrarExito,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                ) { Text("Aceptar") }
            }
        )
    }
    if (estado.modalSalirPendiente) {
        com.mycar.app.ui.CambiosSinGuardarDialog(
            onSeguirEditando = onSeguirEditando,
            onDescartar = onSalirSinGuardar
        )
    }
}

@Composable
private fun TarjetaVehiculoRecordatorio(
    vehiculo: VehiculoRecordatorioKm,
    frecuencia: FrecuenciaKm,
    habilitado: Boolean,
    guardando: Boolean,
    fotos: FotoPersonalizadaRepository?,
    onFrecuencia: (FrecuenciaKm) -> Unit,
    onGuardar: () -> Unit
) {
    var menuAbierto by remember(vehiculo.idVehiculo) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MyCarTile),
                    contentAlignment = Alignment.Center
                ) {
                    MiniaturaFotoVehiculo(
                        idVehiculo = vehiculo.idVehiculo,
                        tieneFoto = vehiculo.tieneFoto,
                        fotos = fotos,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Foto ${vehiculo.marca}"
                    ) {
                        ImagenVehiculoPredeterminada(Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${vehiculo.marca} ${vehiculo.modelo}",
                        fontWeight = FontWeight.Bold,
                        color = MyCarNavy
                    )
                    Text("Placa: ${vehiculo.placa}", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                    vehiculo.kilometrajeActual?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Speed, null, tint = MyCarTeal, modifier = Modifier.size(14.dp))
                            Text(
                                "${formatearKm(it)} km",
                                color = MyCarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Text("Frecuencia", fontWeight = FontWeight.Medium, color = MyCarNavy)
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, MyCarNavy.copy(alpha = if (habilitado) 1f else 0.35f), RoundedCornerShape(12.dp))
                        .clickable(enabled = habilitado) { menuAbierto = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(frecuencia.etiqueta, color = MyCarNavy)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = MyCarNavy)
                }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    FrecuenciaKm.entries.forEach { op ->
                        DropdownMenuItem(
                            text = { Text(op.etiqueta) },
                            onClick = {
                                menuAbierto = false
                                onFrecuencia(op)
                            }
                        )
                    }
                }
            }
            when {
                vehiculo.sinLecturaValida ->
                    Text(
                        "Sin lectura válida: registra kilometraje para calcular el próximo recordatorio.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                vehiculo.pendiente && frecuencia == vehiculo.frecuencia ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Event, null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                        Text(
                            "Recordatorio pendiente",
                            color = Color(0xFFE53935),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                vehiculo.proximoRecordatorio != null && frecuencia == vehiculo.frecuencia ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Event, null, tint = MyCarTeal, modifier = Modifier.size(16.dp))
                        Text(
                            "Próximo recordatorio: ${vehiculo.proximoRecordatorio}",
                            color = MyCarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                else ->
                    Text(
                        "Guarda para recalcular la próxima fecha.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
            }
            if (guardando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarNavy)
            }
            Button(
                onClick = onGuardar,
                enabled = habilitado && !guardando && !vehiculo.sinLecturaValida,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
            ) {
                Text(if (guardando) "Guardando…" else "Guardar preferencia", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatearKm(valor: java.math.BigDecimal): String {
    val symbols = DecimalFormatSymbols(Locale("es", "PE")).apply { groupingSeparator = '.' }
    return DecimalFormat("#,###", symbols).format(valor)
}
