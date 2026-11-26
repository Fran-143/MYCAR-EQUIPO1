package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import com.mycar.app.features.fotos.ui.ImagenVehiculoPredeterminada
import com.mycar.app.features.fotos.ui.MiniaturaFotoVehiculo
import com.mycar.app.features.vehiculos.data.VehiculoResumenDto
import com.mycar.app.features.vehiculos.domain.ListadoVehiculosRepository
import com.mycar.app.features.vehiculos.viewmodel.*
import com.mycar.app.ui.theme.MyCarStatusActivo

private val AzulListado = Color(0xFF003366)
private val AzulClaroChip = Color(0xFFE8F1FA)

@Composable
fun ListadoVehiculosRoute(claveCuenta: String, repository: ListadoVehiculosRepository,
                         onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                         modifier: Modifier = Modifier,                          onVerDetalle: ((Long) -> Unit)? = null,
                         onCompartir: ((Long, String, String) -> Unit)? = null,
                         estadoEnSistema: Boolean = true,
                         onNotificaciones: (() -> Unit)? = null, onPerfil: (() -> Unit)? = null,
                         fotosRepository: FotoPersonalizadaRepository? = null,
                         avatarBytes: ByteArray? = null, iniciales: String? = null) {
    val vm: ListadoVehiculosViewModel = viewModel(key = "rf09-$claveCuenta",
        factory = remember(repository, claveCuenta) { ListadoVehiculosViewModel.factory(repository, estadoEnSistema) })
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    ListadoVehiculosScreen(estado, vm::cambiarBusqueda, vm::filtrar, vm::cargar,
        onVolver, onAgregar, onIniciarSesion, modifier = modifier, onVerDetalle = onVerDetalle,
        onCompartir = onCompartir,
        onNotificaciones = onNotificaciones, onPerfil = onPerfil,
        fotosRepository = fotosRepository, avatarBytes = avatarBytes, iniciales = iniciales)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ListadoVehiculosScreen(estado: EstadoListadoVehiculos, onBusqueda: (String) -> Unit,
                          onFiltro: (String) -> Unit, onBuscar: () -> Unit,
                          onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                          modifier: Modifier = Modifier, onVerDetalle: ((Long) -> Unit)? = null,
                          onCompartir: ((Long, String, String) -> Unit)? = null,
                          onNotificaciones: (() -> Unit)? = null, onPerfil: (() -> Unit)? = null,
                          fotosRepository: FotoPersonalizadaRepository? = null,
                          avatarBytes: ByteArray? = null, iniciales: String? = null) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            com.mycar.app.ui.MycarCabeceraApp(
                onNotificaciones = onNotificaciones,
                onPerfil = onPerfil,
                avatarBytes = avatarBytes,
                iniciales = iniciales
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Mis vehículos",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AzulListado
                        )
                        Text(
                            "Gestiona tus vehículos registrados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onAgregar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulListado),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = estado.busqueda,
                    onValueChange = onBusqueda,
                    singleLine = true,
                    maxLines = 1,
                    placeholder = {
                        Text(
                            "Buscar por placa o nombre…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (estado.busqueda.isNotEmpty()) {
                            IconButton(onClick = { onBusqueda("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AzulListado,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onBuscar() })
                )
                Spacer(Modifier.height(10.dp))
                val c = estado.resultado?.resumen
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("TODOS", "Todos", c?.todos),
                        Triple("ACTIVO", "Activos", c?.activos),
                        Triple("INACTIVO", "Inactivos", c?.inactivos)
                    ).forEach { (codigo, etiqueta, cantidad) ->
                        FilterChip(
                            selected = estado.filtro == codigo,
                            onClick = { onFiltro(codigo) },
                            label = { Text(etiqueta + if (cantidad == null) "" else " ($cantidad)") },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AzulListado,
                                selectedLabelColor = Color.White,
                                containerColor = AzulClaroChip,
                                labelColor = AzulListado
                            )
                        )
                    }
                }
            }
            when (estado.fase) {
                FaseListado.CARGANDO -> item {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = AzulListado)
                    Text("Consultando vehículos…")
                }
                FaseListado.SESION_REQUERIDA -> item {
                    Text(estado.mensaje ?: "Inicia sesión para continuar.")
                    Button(
                        onClick = onIniciarSesion,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AzulListado)
                    ) { Text("Iniciar sesión") }
                }
                FaseListado.ERROR -> item {
                    Text(estado.mensaje ?: "No se pudo cargar el listado.")
                    OutlinedButton(onClick = onBuscar) { Text("Reintentar") }
                }
                FaseListado.LISTO -> {
                    val lista = estado.resultado
                    if (lista != null) {
                        if (lista.vehiculos.isEmpty()) item {
                            Text(
                                if (lista.resumen.todos == 0L) "Aún no tienes vehículos registrados."
                                else "No hay coincidencias con esta búsqueda y filtro."
                            )
                        }
                        items(lista.vehiculos, key = { it.idVehiculo }) { v ->
                            TarjetaVehiculoListado(
                                vehiculo = v,
                                fotosRepository = fotosRepository,
                                onVerDetalle = onVerDetalle,
                                onCompartir = onCompartir
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaVehiculoListado(
    vehiculo: VehiculoResumenDto,
    fotosRepository: FotoPersonalizadaRepository?,
    onVerDetalle: ((Long) -> Unit)?,
    onCompartir: ((Long, String, String) -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val activo = vehiculo.estadoVehiculo == "ACTIVO"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    MiniaturaFotoVehiculo(
                        idVehiculo = vehiculo.idVehiculo,
                        tieneFoto = vehiculo.tieneFoto,
                        fotos = fotosRepository,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = null
                    ) {
                        ImagenVehiculoPredeterminada(Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "${vehiculo.marca} ${vehiculo.modelo}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AzulListado,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (vehiculo.principal) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = AzulClaroChip
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = AzulListado,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            "Principal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AzulListado,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (activo) MyCarStatusActivo.copy(alpha = 0.15f)
                                else colors.surfaceVariant
                            ) {
                                Text(
                                    if (activo) "Activo" else "Inactivo",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (activo) MyCarStatusActivo else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FilaDatoTarjeta(
                        icono = Icons.Filled.DirectionsCar,
                        texto = "${vehiculo.placa} · ${vehiculo.anioFabricacion}"
                    )
                    FilaDatoTarjeta(
                        icono = Icons.Filled.Speed,
                        texto = "${vehiculo.usoVehiculo} · ${
                            vehiculo.kilometrajeActual?.let { "%,d km".format(it).replace(',', ' ') } ?: "Sin km"
                        }"
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipDocumento(
                    icono = Icons.Filled.Shield,
                    texto = etiquetaSoat(vehiculo.estadoSoat),
                    color = colorSoat(vehiculo.estadoSoat)
                )
                ChipDocumento(
                    icono = Icons.Filled.Description,
                    texto = if (vehiculo.tieneTive) "TIVE" else "Sin TIVE",
                    color = if (vehiculo.tieneTive) MyCarStatusActivo else Color(0xFF6B7C93)
                )
            }
            if (onVerDetalle != null || onCompartir != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onVerDetalle != null) {
                        Button(
                            onClick = { onVerDetalle(vehiculo.idVehiculo) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AzulListado)
                        ) {
                            Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ver detalles")
                        }
                    }
                    if (onCompartir != null) {
                        OutlinedButton(
                            onClick = { onCompartir(vehiculo.idVehiculo, vehiculo.marca, vehiculo.modelo) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AzulListado),
                            border = BorderStroke(1.5.dp, AzulListado)
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaDatoTarjeta(icono: ImageVector, texto: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icono, contentDescription = null, tint = Color(0xFF6B7C93), modifier = Modifier.size(16.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4A5568))
    }
}

@Composable
private fun ChipDocumento(icono: ImageVector, texto: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icono, null, tint = color, modifier = Modifier.size(14.dp))
            Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun etiquetaSoat(estado: String?): String = when (estado?.uppercase()) {
    "VIGENTE" -> "SOAT vigente"
    "PROXIMA" -> "SOAT por vencer"
    "VENCIDA" -> "SOAT vencido"
    "POR_INICIAR" -> "SOAT por iniciar"
    else -> "Sin SOAT"
}

private fun colorSoat(estado: String?): Color = when (estado?.uppercase()) {
    "VIGENTE" -> MyCarStatusActivo
    "PROXIMA" -> Color(0xFFF9A825)
    "VENCIDA" -> Color(0xFFC62828)
    "POR_INICIAR" -> Color(0xFF079FDC)
    else -> Color(0xFF6B7C93)
}
