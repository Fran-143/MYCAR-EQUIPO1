package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.vehiculos.data.DetalleVehiculoDto
import com.mycar.app.features.vehiculos.domain.DetalleVehiculosRepository
import com.mycar.app.features.vehiculos.viewmodel.*
import com.mycar.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FondoDetalle = MyCarBg
private val AzulDetalle = MyCarNavy
private val CelesteDetalle = MyCarTeal

@Composable
fun DetalleVehiculoRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: DetalleVehiculosRepository,
    soatRepository: SoatRepository? = null,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizarKm: (() -> Unit)? = null,
    onEditarDatos: (() -> Unit)? = null,
    onRegistrarSoat: (() -> Unit)? = null,
    onConsultarSoat: (() -> Unit)? = null,
    onConfigurarSoat: (() -> Unit)? = null,
    onRegistrarTive: (() -> Unit)? = null,
    onConsultarTive: (() -> Unit)? = null,
    onEditarTive: (() -> Unit)? = null,
    onCompletarVinParaTive: (() -> Unit)? = null,
    onCancelarVinParaTive: (() -> Unit)? = null,
    mostrarModalVinCompletado: Boolean = false,
    onRegistrarTiveTrasVin: (() -> Unit)? = null,
    onVolverDetalleTrasVin: (() -> Unit)? = null,
    onCambioOperativo: (() -> Unit)? = null,
    recargaToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val vm: DetalleVehiculoViewModel = viewModel(
        key = "rf10-$claveCuenta-$idVehiculo",
        factory = remember(repository, soatRepository, idVehiculo) {
            DetalleVehiculoViewModel.factory(idVehiculo, repository, soatRepository)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    LaunchedEffect(recargaToken) { if (recargaToken > 0) vm.cargar() }
    DetalleVehiculoScreen(
        estado = estado,
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onActualizarKm = onActualizarKm,
        onEditarDatos = onEditarDatos,
        onRegistrarSoat = onRegistrarSoat,
        onConsultarSoat = onConsultarSoat,
        onConfigurarSoat = onConfigurarSoat,
        onRegistrarTive = onRegistrarTive,
        onConsultarTive = onConsultarTive,
        onEditarTive = onEditarTive,
        onCompletarVinParaTive = onCompletarVinParaTive,
        onCancelarVinParaTive = onCancelarVinParaTive,
        mostrarModalVinCompletado = mostrarModalVinCompletado,
        onRegistrarTiveTrasVin = onRegistrarTiveTrasVin,
        onVolverDetalleTrasVin = onVolverDetalleTrasVin,
        onEstablecerPrincipal = { vm.establecerPrincipal { onCambioOperativo?.invoke() } },
        onActivar = { vm.activar { onCambioOperativo?.invoke() } },
        onDesactivar = { vm.desactivar { onCambioOperativo?.invoke() } },
        modifier = modifier
    )
}

@Composable
fun DetalleVehiculoScreen(
    estado: EstadoDetalleVehiculo,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizarKm: (() -> Unit)? = null,
    onEditarDatos: (() -> Unit)? = null,
    onRegistrarSoat: (() -> Unit)? = null,
    onConsultarSoat: (() -> Unit)? = null,
    onConfigurarSoat: (() -> Unit)? = null,
    onRegistrarTive: (() -> Unit)? = null,
    onConsultarTive: (() -> Unit)? = null,
    onEditarTive: (() -> Unit)? = null,
    onCompletarVinParaTive: (() -> Unit)? = null,
    onCancelarVinParaTive: (() -> Unit)? = null,
    mostrarModalVinCompletado: Boolean = false,
    onRegistrarTiveTrasVin: (() -> Unit)? = null,
    onVolverDetalleTrasVin: (() -> Unit)? = null,
    onEstablecerPrincipal: (() -> Unit)? = null,
    onActivar: (() -> Unit)? = null,
    onDesactivar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var confirmarPrincipal by remember { mutableStateOf(false) }
    var confirmarDesactivar by remember { mutableStateOf(false) }
    var modalVinRequerido by remember { mutableStateOf(false) }
    fun intentarRegistrarTive() {
        val vin = estado.detalle?.numeroVin
        if (vin.isNullOrBlank()) {
            modalVinRequerido = true
        } else {
            onRegistrarTive?.invoke()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FondoDetalle,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver, enabled = !estado.ocupado) { Text("Volver") }
                    Text(
                        "Detalle del vehículo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AzulDetalle,
                        modifier = Modifier.weight(1f)
                    )
                    estado.detalle?.let { BadgeEstado(it.estadoVehiculo) }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CelesteDetalle)
            estado.mensajeAccion?.let { texto ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (estado.accionError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    contentColor = if (estado.accionError) Color(0xFFC62828) else Color(0xFF2E7D32)
                ) {
                    Text(texto, Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                }
            }
            when (estado.fase) {
                FaseDetalle.CARGANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = CelesteDetalle)
                    Text("Consultando vehículo…", color = MyCarMuted)
                }
                FaseDetalle.SESION_REQUERIDA -> {
                    Text(estado.mensaje ?: "Inicia sesión para continuar.")
                    Button(
                        onClick = onIniciarSesion,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                    ) { Text("Iniciar sesión") }
                }
                FaseDetalle.ERROR, FaseDetalle.NO_DISPONIBLE -> {
                    Text(estado.mensaje ?: "No se pudo cargar el detalle.")
                    if (estado.fase == FaseDetalle.ERROR) {
                        OutlinedButton(onClick = onRecargar, shape = RoundedCornerShape(12.dp)) {
                            Text("Reintentar")
                        }
                    }
                }
                FaseDetalle.LISTO -> estado.detalle?.let { det ->
                    CabeceraDetalle(det)
                    GridEspecificaciones(det)

                    if (det.estadoVehiculo == "ACTIVO") {
                        if (onActualizarKm != null) {
                            OutlinedButton(
                                onClick = onActualizarKm,
                                enabled = !estado.ocupado,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulDetalle)
                            ) { Text("Actualizar kilometraje", fontWeight = FontWeight.SemiBold) }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (onEditarDatos != null) {
                                Button(
                                    onClick = onEditarDatos,
                                    enabled = !estado.ocupado,
                                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                                ) { Text("Editar datos") }
                            }
                            if (!det.principal && onEstablecerPrincipal != null) {
                                Button(
                                    onClick = { confirmarPrincipal = true },
                                    enabled = !estado.ocupado,
                                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CelesteDetalle)
                                ) { Text("Hacer principal") }
                            }
                        }
                        if (onDesactivar != null) {
                            Button(
                                onClick = { confirmarDesactivar = true },
                                enabled = !estado.ocupado,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) { Text("Desactivar vehículo", fontWeight = FontWeight.SemiBold) }
                        }
                    } else if (onActivar != null) {
                        Button(
                            onClick = onActivar,
                            enabled = !estado.ocupado,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                        ) { Text("Activar vehículo", fontWeight = FontWeight.SemiBold) }
                    }

                    SeccionIdentificacion(det)
                    SeccionDocumentos(
                        soatActual = estado.soatActual,
                        soatCargando = estado.soatResumenCargando,
                        vehiculoActivo = det.estadoVehiculo == "ACTIVO",
                        tieneTive = det.tieneTive,
                        onRegistrarSoat = onRegistrarSoat,
                        onConsultarSoat = onConsultarSoat,
                        onConfigurarSoat = onConfigurarSoat,
                        onRegistrarTive = { intentarRegistrarTive() },
                        onConsultarTive = onConsultarTive,
                        onEditarTive = onEditarTive,
                        ocupado = estado.ocupado
                    )
                }
            }
        }
    }

    if (confirmarPrincipal) {
        val det = estado.detalle
        AlertDialog(
            onDismissRequest = { if (!estado.ocupado) confirmarPrincipal = false },
            title = { Text("Establecer como principal", fontWeight = FontWeight.Bold, color = AzulDetalle) },
            text = {
                Text(
                    if (det == null) "¿Confirmas el cambio de vehículo principal?"
                    else "${det.marca} ${det.modelo} será tu vehículo principal. El vehículo anterior perderá esa condición."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmarPrincipal = false
                        onEstablecerPrincipal?.invoke()
                    },
                    enabled = !estado.ocupado,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarPrincipal = false }, enabled = !estado.ocupado) {
                    Text("Cancelar")
                }
            }
        )
    }
    if (modalVinRequerido) {
        AlertDialog(
            onDismissRequest = { modalVinRequerido = false },
            title = { Text("Necesitas completar tu vehículo", fontWeight = FontWeight.Bold, color = AzulDetalle) },
            text = {
                Text("Para registrar la TIVE, primero necesitas completar el número VIN de tu vehículo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        modalVinRequerido = false
                        onCompletarVinParaTive?.invoke()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                ) { Text("COMPLETAR") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        modalVinRequerido = false
                        onCancelarVinParaTive?.invoke()
                    }
                ) { Text("CANCELAR") }
            }
        )
    }
    if (mostrarModalVinCompletado) {
        AlertDialog(
            onDismissRequest = { onVolverDetalleTrasVin?.invoke() },
            title = { Text("VIN registrado correctamente", fontWeight = FontWeight.Bold, color = AzulDetalle) },
            text = {
                Text("Ya completaste el VIN de tu vehículo. ¿Qué deseas hacer ahora?")
            },
            confirmButton = {
                Button(
                    onClick = { onRegistrarTiveTrasVin?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                ) { Text("REGISTRAR TIVE") }
            },
            dismissButton = {
                TextButton(onClick = { onVolverDetalleTrasVin?.invoke() }) {
                    Text("VOLVER AL DETALLE")
                }
            }
        )
    }
    if (confirmarDesactivar) {
        val det = estado.detalle
        AlertDialog(
            onDismissRequest = { if (!estado.ocupado) confirmarDesactivar = false },
            title = { Text("Desactivar vehículo", fontWeight = FontWeight.Bold, color = AzulDetalle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (det == null) "¿Deseas desactivar este vehículo?"
                        else "¿Deseas desactivar ${det.marca} ${det.modelo} (${det.placa})?"
                    )
                    Text(
                        "Los datos se volverán solo de lectura. Conservaremos el historial. Debes mantener al menos un vehículo activo.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (det?.principal == true) {
                        Text(
                            "Si es el principal y tienes otros activos, primero establece otro como principal.",
                            color = Color(0xFFC62828),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmarDesactivar = false
                        onDesactivar?.invoke()
                    },
                    enabled = !estado.ocupado,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Aceptar") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmarDesactivar = false },
                    enabled = !estado.ocupado,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BadgeEstado(estado: String) {
    val activo = estado == "ACTIVO"
    Surface(
        shape = RoundedCornerShape(50),
        color = if (activo) MyCarStatusActivo.copy(alpha = 0.15f) else MyCarOutlineSoft
    ) {
        Text(
            if (activo) "Activo" else "Inactivo",
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (activo) MyCarStatusActivo else MyCarMuted
        )
    }
}

@Composable
private fun CabeceraDetalle(v: DetalleVehiculoDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MyCarTile)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${v.marca} ${v.modelo}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AzulDetalle
                )
                Text(v.placa, color = MyCarMuted, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeEstado(v.estadoVehiculo)
                    if (v.principal) {
                        Surface(shape = RoundedCornerShape(50), color = MyCarSky.copy(alpha = 0.15f)) {
                            Text(
                                "★ Principal",
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MyCarBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridEspecificaciones(v: DetalleVehiculoDto) {
    Text("Especificaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AzulDetalle)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle("Uso", v.usoVehiculo, Modifier.weight(1f))
        TileDetalle("Año", v.anioFabricacion.toString(), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle("Motor", v.tipoMotor, Modifier.weight(1f))
        TileDetalle(
            "Combustible",
            if (v.motorElectrico) "Eléctrico" else (v.combustible ?: "—"),
            Modifier.weight(1f)
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TileDetalle(
            "Aceite",
            if (v.motorElectrico) "N/A" else (v.tipoAceite ?: "—"),
            Modifier.weight(1f)
        )
        TileDetalle(
            "Km actual",
            v.kilometrajeActual?.let { "${com.mycar.app.features.vehiculos.domain.KilometrajeTexto.formatear(it)} km" } ?: "Sin lectura",
            Modifier.weight(1f)
        )
    }
    TileDetalle("Color", v.color, Modifier.fillMaxWidth())
}

@Composable
private fun SeccionIdentificacion(v: DetalleVehiculoDto) {
    var expandido by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expandido = !expandido }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Identificación adicional",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AzulDetalle
                )
                Text(
                    if (expandido) "▲" else "▼",
                    color = MyCarMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (expandido) {
                Column(
                    Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilaDetalle("VIN", v.numeroVin ?: "No registrado")
                    FilaDetalle("N.º de serie", v.numeroSerie ?: "No registrado")
                    FilaDetalle("N.º de motor", v.numeroMotor ?: "No registrado")
                    v.fechaUltimaLecturaKm?.let { FilaDetalle("Última lectura", formatearFecha(it)) }
                }
            }
        }
    }
}

@Composable
private fun SeccionDocumentos(
    soatActual: com.mycar.app.features.soat.data.SoatDto?,
    soatCargando: Boolean,
    vehiculoActivo: Boolean,
    tieneTive: Boolean,
    onRegistrarSoat: (() -> Unit)?,
    onConsultarSoat: (() -> Unit)?,
    onConfigurarSoat: (() -> Unit)?,
    onRegistrarTive: (() -> Unit)?,
    onConsultarTive: (() -> Unit)?,
    onEditarTive: (() -> Unit)?,
    ocupado: Boolean
) {
    Text("Documentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AzulDetalle)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SOAT", fontWeight = FontWeight.Bold, color = AzulDetalle)
            when {
                soatCargando -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = CelesteDetalle)
                    Text("Consultando SOAT…", style = MaterialTheme.typography.bodySmall, color = MyCarMuted)
                }
                soatActual == null -> {
                    Text("Sin SOAT registrado", style = MaterialTheme.typography.bodyMedium, color = MyCarMuted)
                    if (vehiculoActivo && onRegistrarSoat != null) {
                        Button(
                            onClick = onRegistrarSoat,
                            enabled = !ocupado,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                        ) { Text("Registrar SOAT") }
                    }
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estado", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(SoatFormulario.colorCobertura(soatActual.estadoCobertura.orEmpty())).copy(alpha = 0.12f)
                        ) {
                            Text(
                                SoatFormulario.etiquetaCobertura(soatActual.estadoCobertura.orEmpty()),
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Color(SoatFormulario.colorCobertura(soatActual.estadoCobertura.orEmpty())),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(
                        "Inicio: ${SoatFormulario.formatearFecha(soatActual.fechaInicio)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyCarMuted
                    )
                    Text(
                        "Vencimiento: ${SoatFormulario.formatearFecha(soatActual.fechaVencimiento)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyCarMuted
                    )
                    if (onConsultarSoat != null) {
                        OutlinedButton(
                            onClick = onConsultarSoat,
                            enabled = !ocupado,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulDetalle)
                        ) { Text("Consultar SOAT") }
                    }
                    if (soatActual.vigencia == "ACTUAL" && vehiculoActivo && onConfigurarSoat != null) {
                        Button(
                            onClick = onConfigurarSoat,
                            enabled = !ocupado,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                        ) { Text("Configurar SOAT") }
                    }
                }
            }
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TIVE", fontWeight = FontWeight.Bold, color = AzulDetalle)
            if (!tieneTive) {
                Text("Sin TIVE registrada", style = MaterialTheme.typography.bodyMedium, color = MyCarMuted)
                if (vehiculoActivo && onRegistrarTive != null) {
                    Button(
                        onClick = onRegistrarTive,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                    ) { Text("Registrar TIVE") }
                }
            } else {
                Text("TIVE registrada", style = MaterialTheme.typography.bodySmall, color = MyCarMuted)
                if (onConsultarTive != null) {
                    OutlinedButton(
                        onClick = onConsultarTive,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulDetalle)
                    ) { Text("Consultar TIVE") }
                }
                if (vehiculoActivo && onEditarTive != null) {
                    Button(
                        onClick = onEditarTive,
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulDetalle)
                    ) { Text("Editar TIVE") }
                }
            }
        }
    }
}

@Composable
private fun TileDetalle(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MyCarTile
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MyCarMuted)
            Text(
                valor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AzulDetalle,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun FilaDetalle(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = MyCarMuted)
        Text(valor, fontWeight = FontWeight.Medium, color = AzulDetalle, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatearFecha(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "PE"))
        .withZone(ZoneId.of("America/Lima"))
        .format(Instant.parse(iso))
}.getOrDefault(iso)
