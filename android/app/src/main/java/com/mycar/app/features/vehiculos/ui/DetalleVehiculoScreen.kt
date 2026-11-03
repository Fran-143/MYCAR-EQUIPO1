package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.domain.DetalleVehiculosRepository
import com.mycar.app.features.vehiculos.viewmodel.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DetalleVehiculoRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: DetalleVehiculosRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizarKm: (() -> Unit)? = null,
    recargaToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val vm: DetalleVehiculoViewModel = viewModel(
        key = "rf10-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) { DetalleVehiculoViewModel.factory(idVehiculo, repository) }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(recargaToken) { if (recargaToken > 0) vm.cargar() }
    DetalleVehiculoScreen(estado, vm::cargar, onVolver, onIniciarSesion, onActualizarKm, modifier)
}

@Composable
fun DetalleVehiculoScreen(
    estado: EstadoDetalleVehiculo,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizarKm: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        Surface(shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                TextButton(onClick = onVolver) { Text("Volver") }
                Text("Detalle de vehículo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (estado.fase) {
                FaseDetalle.CARGANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Consultando vehículo…")
                }
                FaseDetalle.SESION_REQUERIDA -> {
                    Text(estado.mensaje ?: "Inicia sesión para continuar.")
                    Button(onClick = onIniciarSesion) { Text("Iniciar sesión") }
                }
                FaseDetalle.ERROR, FaseDetalle.NO_DISPONIBLE -> {
                    Text(estado.mensaje ?: "No se pudo cargar el detalle.")
                    if (estado.fase == FaseDetalle.ERROR) {
                        OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
                    }
                }
                FaseDetalle.LISTO -> estado.detalle?.let { det ->
                    ContenidoDetalle(det)
                    if (onActualizarKm != null && det.estadoVehiculo == "ACTIVO") {
                        OutlinedButton(onClick = onActualizarKm, modifier = Modifier.fillMaxWidth()) {
                            Text("Actualizar kilometraje")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContenidoDetalle(v: DetalleVehiculoDto) {
    Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), null, Modifier.size(96.dp))
    Text("${v.marca} ${v.modelo}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(v.placa, style = MaterialTheme.typography.titleLarge)
    if (v.principal) Text("Vehículo principal", color = MaterialTheme.colorScheme.primary)
    Text(if (v.estadoVehiculo == "ACTIVO") "Activo" else "Inactivo")
    SeccionDetalle("Información general") {
        FilaDetalle("Año", v.anioFabricacion.toString())
        FilaDetalle("Uso", v.usoVehiculo)
        FilaDetalle("Color", v.color)
        FilaDetalle("Kilometraje", v.kilometrajeActual?.let { "$it km" } ?: "Sin lectura disponible")
        v.fechaUltimaLecturaKm?.let { FilaDetalle("Última lectura", formatearFecha(it)) }
    }
    SeccionDetalle("Identificación adicional") {
        FilaDetalle("VIN", v.numeroVin ?: "No registrado")
        FilaDetalle("N.º de serie", v.numeroSerie ?: "No registrado")
        FilaDetalle("N.º de motor", v.numeroMotor ?: "No registrado")
    }
    SeccionDetalle("Motor y mantenimiento") {
        FilaDetalle("Tipo de motor", v.tipoMotor)
        if (!v.motorElectrico) FilaDetalle("Combustible", v.combustible ?: "No registrado")
        if (!v.motorElectrico) FilaDetalle("Tipo de aceite", v.tipoAceite ?: "No registrado")
    }
}

@Composable
private fun SeccionDetalle(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatearFecha(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
        .withZone(ZoneId.of("America/Lima")).format(Instant.parse(iso))
}.getOrDefault(iso)
