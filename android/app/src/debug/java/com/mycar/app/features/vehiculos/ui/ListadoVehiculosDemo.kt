package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.viewmodel.*
import java.util.Locale

/** Escenarios visuales de RF09. No representan vehículos guardados ni una cuenta autenticada. */
@Composable
fun ListadoVehiculosDemo(onVolver: () -> Unit, onAgregar: () -> Unit) {
    var ejemplo by rememberSaveable { mutableStateOf(false) }
    var inactivo by rememberSaveable { mutableStateOf(false) }
    var filtro by rememberSaveable { mutableStateOf("TODOS") }
    var texto by rememberSaveable { mutableStateOf("") }
    var aplicado by rememberSaveable { mutableStateOf("") }
    var mostrarLimite by remember { mutableStateOf(false) }
    val todos = if (ejemplo) listOf(VehiculoResumenDto(1, "A1B234", "Toyota", "Corolla", 2020,
        "Particular", "Gris", "Combustión", "Gasolina", if (inactivo) "INACTIVO" else "ACTIVO", true, 58240)) else emptyList()
    val buscar = aplicado.trim().uppercase(Locale.ROOT)
    val lista = todos.filter { (filtro == "TODOS" || it.estadoVehiculo == filtro) &&
        (buscar.isEmpty() || it.placa.contains(buscar) || "${it.marca} ${it.modelo}".uppercase(Locale.ROOT).contains(buscar)) }
    val resultado = ListadoVehiculosDto(lista, ResumenEstadosDto(todos.size.toLong(),
        todos.count { it.estadoVehiculo == "ACTIVO" }.toLong(), todos.count { it.estadoVehiculo == "INACTIVO" }.toLong()), filtro, aplicado)
    Column(Modifier.fillMaxSize().statusBarsPadding().consumeWindowInsets(WindowInsets.statusBars)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Modo de prueba · ejemplos, no datos de tu cuenta", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ejemplo, onClick = { ejemplo = !ejemplo }, label = { Text("Mostrar ejemplo") })
                    if (ejemplo) FilterChip(selected = inactivo, onClick = { inactivo = !inactivo }, label = { Text("Ejemplo inactivo") })
                }
            }
        }
        ListadoVehiculosScreen(EstadoListadoVehiculos(filtro, texto, FaseListado.LISTO, resultado),
            onBusqueda = { texto = it }, onFiltro = { filtro = it; aplicado = texto }, onBuscar = { aplicado = texto },
            onVolver = onVolver, onAgregar = { if (todos.isEmpty()) onAgregar() else mostrarLimite = true },
            onIniciarSesion = {}, modifier = Modifier.weight(1f))
    }
    if (mostrarLimite) AlertDialog(onDismissRequest = { mostrarLimite = false },
        title = { Text("Cupo del ejemplo") },
        text = { Text("El ejemplo ya tiene un vehículo. Free permite uno registrado, aunque esté inactivo.") },
        confirmButton = { TextButton(onClick = { mostrarLimite = false }) { Text("Entendido") } })
}
