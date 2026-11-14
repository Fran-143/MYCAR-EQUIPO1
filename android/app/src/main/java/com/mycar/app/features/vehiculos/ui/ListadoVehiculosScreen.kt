package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.vehiculos.domain.ListadoVehiculosRepository
import com.mycar.app.features.vehiculos.viewmodel.*
import com.mycar.app.ui.theme.MyCarStatusActivo

@Composable
fun ListadoVehiculosRoute(claveCuenta: String, repository: ListadoVehiculosRepository,
                         onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                         modifier: Modifier = Modifier, onVerDetalle: ((Long) -> Unit)? = null, estadoEnSistema: Boolean = true,
                         onNotificaciones: (() -> Unit)? = null, onPerfil: (() -> Unit)? = null) {
    val vm: ListadoVehiculosViewModel = viewModel(key = "rf09-$claveCuenta",
        factory = remember(repository, claveCuenta) { ListadoVehiculosViewModel.factory(repository, estadoEnSistema) })
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    ListadoVehiculosScreen(estado, vm::cambiarBusqueda, vm::filtrar, vm::cargar,
        onVolver, onAgregar, onIniciarSesion, modifier = modifier, onVerDetalle = onVerDetalle,
        onNotificaciones = onNotificaciones, onPerfil = onPerfil)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListadoVehiculosScreen(estado: EstadoListadoVehiculos, onBusqueda: (String) -> Unit,
                          onFiltro: (String) -> Unit, onBuscar: () -> Unit,
                          onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                          modifier: Modifier = Modifier, onVerDetalle: ((Long) -> Unit)? = null,
                          onNotificaciones: (() -> Unit)? = null, onPerfil: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = colors.background,
        topBar = {
            Column {
                com.mycar.app.ui.MycarCabeceraApp(
                    onNotificaciones = onNotificaciones,
                    onPerfil = onPerfil
                )
                Surface(color = colors.surface, shadowElevation = 1.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onVolver) { Text("Volver") }
                        Text(
                            "Mis vehículos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = estado.busqueda,
                    onValueChange = onBusqueda,
                    singleLine = true,
                    label = { Text("Buscar por placa, marca o modelo") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onBuscar() })
                )
                Spacer(Modifier.height(8.dp))
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
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.onPrimaryContainer,
                                selectedLabelColor = colors.onPrimary
                            )
                        )
                    }
                }
            }
            when (estado.fase) {
                FaseListado.CARGANDO -> item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Consultando vehículos…")
                }
                FaseListado.SESION_REQUERIDA -> item {
                    Text(estado.mensaje ?: "Inicia sesión para continuar.")
                    Button(onClick = onIniciarSesion, shape = RoundedCornerShape(12.dp)) { Text("Iniciar sesión") }
                }
                FaseListado.ERROR -> item {
                    Text(estado.mensaje ?: "No se pudo cargar el listado.")
                    OutlinedButton(onClick = onBuscar) { Text("Reintentar") }
                }
                FaseListado.LISTO -> {
                    val lista = estado.resultado
                    if (lista != null) {
                        item {
                            Button(
                                onClick = onAgregar,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.tertiary)
                            ) { Text("+ Agregar vehículo", fontWeight = FontWeight.SemiBold) }
                        }
                        if (lista.vehiculos.isEmpty()) item {
                            Text(
                                if (lista.resumen.todos == 0L) "Aún no tienes vehículos registrados."
                                else "No hay coincidencias con esta búsqueda y filtro."
                            )
                        }
                        items(lista.vehiculos, key = { it.idVehiculo }) { v ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Image(
                                            painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),
                                            null,
                                            Modifier.size(72.dp)
                                        )
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${v.marca} ${v.modelo}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.onPrimaryContainer
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = if (v.estadoVehiculo == "ACTIVO")
                                                        MyCarStatusActivo.copy(alpha = 0.15f)
                                                    else colors.surfaceVariant
                                                ) {
                                                    Text(
                                                        if (v.estadoVehiculo == "ACTIVO") "Activo" else "Inactivo",
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (v.estadoVehiculo == "ACTIVO") MyCarStatusActivo
                                                        else colors.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text("${v.placa} · ${v.anioFabricacion}", color = colors.onSurfaceVariant)
                                            Text("${v.usoVehiculo} · ${v.kilometrajeActual?.let { "%,d km".format(it).replace(',', ' ') } ?: "Sin km"}")
                                            if (v.principal) {
                                                Text("Vehículo principal", color = colors.primary, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    if (onVerDetalle != null) {
                                        Button(
                                            onClick = { onVerDetalle(v.idVehiculo) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.onPrimaryContainer
                                            )
                                        ) { Text("Ver detalles") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
