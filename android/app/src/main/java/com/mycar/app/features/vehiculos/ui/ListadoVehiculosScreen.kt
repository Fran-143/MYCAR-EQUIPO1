package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun ListadoVehiculosRoute(claveCuenta: String, repository: ListadoVehiculosRepository,
                         onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                         modifier: Modifier = Modifier, onVerDetalle: ((Long) -> Unit)? = null) {
    val vm: ListadoVehiculosViewModel = viewModel(key = "rf09-$claveCuenta",
        factory = remember(repository, claveCuenta) { ListadoVehiculosViewModel.factory(repository) })
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    ListadoVehiculosScreen(estado, vm::cambiarBusqueda, vm::filtrar, vm::cargar,
        onVolver, onAgregar, onIniciarSesion, modifier = modifier, onVerDetalle = onVerDetalle)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListadoVehiculosScreen(estado: EstadoListadoVehiculos, onBusqueda: (String) -> Unit,
                          onFiltro: (String) -> Unit, onBuscar: () -> Unit,
                          onVolver: () -> Unit, onAgregar: () -> Unit, onIniciarSesion: () -> Unit,
                          modifier: Modifier = Modifier, onVerDetalle: ((Long) -> Unit)? = null) {
    Scaffold(modifier = modifier.fillMaxSize().imePadding(), topBar = {
        Surface(shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onVolver) { Text("Volver") }
                Text("Mis vehículos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                OutlinedTextField(value = estado.busqueda, onValueChange = onBusqueda, singleLine = true,
                    label = { Text("Buscar por placa, marca o modelo") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onBuscar() }))
                TextButton(onClick = onBuscar) { Text("Buscar") }
                val c = estado.resultado?.resumen
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Triple("TODOS", "Todos", c?.todos), Triple("ACTIVO", "Activos", c?.activos),
                        Triple("INACTIVO", "Inactivos", c?.inactivos)).forEach { (codigo, etiqueta, cantidad) ->
                        FilterChip(selected = estado.filtro == codigo, onClick = { onFiltro(codigo) },
                            label = { Text(etiqueta + if (cantidad == null) "" else " ($cantidad)") })
                    }
                }
            }
            when (estado.fase) {
                FaseListado.CARGANDO -> item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Consultando vehículos…") }
                FaseListado.SESION_REQUERIDA -> item {
                    Text(estado.mensaje ?: "Inicia sesión para continuar.")
                    Button(onClick = onIniciarSesion) { Text("Iniciar sesión") }
                }
                FaseListado.ERROR -> item {
                    Text(estado.mensaje ?: "No se pudo cargar el listado.")
                    OutlinedButton(onClick = onBuscar) { Text("Reintentar") }
                }
                FaseListado.LISTO -> {
                    val lista = estado.resultado
                    if (lista != null) {
                        item { Button(onClick = onAgregar, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar vehículo") } }
                        if (lista.vehiculos.isEmpty()) item {
                            Text(if (lista.resumen.todos == 0L) "Aún no tienes vehículos registrados." else "No hay coincidencias con esta búsqueda y filtro.")
                        }
                        items(lista.vehiculos, key = { it.idVehiculo }) { v ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), null, Modifier.size(72.dp))
                                    Text("${v.marca} ${v.modelo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(v.placa, style = MaterialTheme.typography.titleLarge)
                                    Text("${v.anioFabricacion} · ${v.usoVehiculo} · ${v.color}")
                                    Text(if (v.estadoVehiculo == "ACTIVO") "Activo" else "Inactivo")
                                    if (v.principal) Text("Vehículo principal", color = MaterialTheme.colorScheme.primary)
                                    Text(v.kilometrajeActual?.let { "$it km" } ?: "Kilometraje no disponible")
                                    Text(v.tipoMotor + (v.combustible?.let { " · $it" } ?: ""))
                                    if (onVerDetalle != null) OutlinedButton(onClick = { onVerDetalle(v.idVehiculo) }) { Text("Ver detalles") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
