package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.mycar.app.ui.theme.MyCarStatusActivo
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
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            Surface(color = colors.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver) { Text("Volver") }
                    Text(
                        "Detalle del vehículo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
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
                    Button(onClick = onIniciarSesion, shape = RoundedCornerShape(12.dp)) { Text("Iniciar sesión") }
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
                        OutlinedButton(
                            onClick = onActualizarKm,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Actualizar kilometraje") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContenidoDetalle(v: DetalleVehiculoDto) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), null, Modifier.size(88.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${v.marca} ${v.modelo}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer
                )
                Text(v.placa, color = colors.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (v.estadoVehiculo == "ACTIVO") MyCarStatusActivo.copy(alpha = 0.15f)
                    else colors.surfaceVariant
                ) {
                    Text(
                        if (v.estadoVehiculo == "ACTIVO") "Activo" else "Inactivo",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (v.estadoVehiculo == "ACTIVO") MyCarStatusActivo else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (v.principal) Text("Vehículo principal", color = colors.primary, fontWeight = FontWeight.Medium)
            }
        }
    }

    Text("Especificaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle("Uso del vehículo", v.usoVehiculo, Modifier.weight(1f))
        TileDetalle("Año", v.anioFabricacion.toString(), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle("Tipo de motor", v.tipoMotor, Modifier.weight(1f))
        TileDetalle(
            "Combustible",
            if (v.motorElectrico) "Eléctrico" else (v.combustible ?: "—"),
            Modifier.weight(1f)
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle(
            "Tipo de aceite",
            if (v.motorElectrico) "N/A" else (v.tipoAceite ?: "—"),
            Modifier.weight(1f)
        )
        TileDetalle(
            "Kilometraje actual",
            v.kilometrajeActual?.let { "%,d km".format(it).replace(',', ' ') } ?: "Sin lectura",
            Modifier.weight(1f)
        )
    }
    TileDetalle("Color", v.color, Modifier.fillMaxWidth())

    SeccionDetalle("Identificación adicional") {
        FilaDetalle("VIN", v.numeroVin ?: "No registrado")
        FilaDetalle("N.º de serie", v.numeroSerie ?: "No registrado")
        FilaDetalle("N.º de motor", v.numeroMotor ?: "No registrado")
        v.fechaUltimaLecturaKm?.let { FilaDetalle("Última lectura", formatearFecha(it)) }
    }
}

@Composable
private fun TileDetalle(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SeccionDetalle(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
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
