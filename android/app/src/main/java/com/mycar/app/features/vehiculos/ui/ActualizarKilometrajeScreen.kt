package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.viewmodel.*

@Composable
fun ActualizarKilometrajeRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: ActualizarKilometrajeRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ActualizarKilometrajeViewModel = viewModel(
        key = "rf14-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) { ActualizarKilometrajeViewModel.factory(idVehiculo, repository) }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    ActualizarKilometrajeScreen(estado, vm::cambiarKm, vm::irVistaPrevia, vm::volverEdicion,
        vm::confirmar, vm::cargarContexto, onVolver, onIniciarSesion,
        onContinuarExito = { onActualizado(); onVolver() }, modifier)
}

@Composable
fun ActualizarKilometrajeScreen(
    estado: EstadoActualizarKm,
    onCambiarKm: (String) -> Unit,
    onVistaPrevia: () -> Unit,
    onVolverEdicion: () -> Unit,
    onConfirmar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onContinuarExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        Surface(shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                TextButton(onClick = onVolver) { Text("Volver") }
                Text("Actualizar kilometraje", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (estado.paso) {
                PasoKm.CARGA, PasoKm.ENVIANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (estado.paso == PasoKm.ENVIANDO) "Guardando lectura…" else "Cargando kilometraje…")
                }
                PasoKm.SESION -> {
                    Text(estado.mensaje ?: "Inicia sesión.")
                    Button(onClick = onIniciarSesion) { Text("Iniciar sesión") }
                }
                PasoKm.INACTIVO, PasoKm.SIN_LECTURA -> Text(estado.mensaje ?: "No disponible.")
                PasoKm.ERROR -> {
                    Text(estado.mensaje ?: "Error.")
                    OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
                }
                PasoKm.EDICION -> {
                    val km = estado.contexto?.kmActual
                    Text("Kilometraje vigente: ${km?.let { "$it km" } ?: "No disponible"}")
                    OutlinedTextField(
                        value = estado.kmNuevoTexto,
                        onValueChange = onCambiarKm,
                        label = { Text("Nuevo kilometraje (km)") },
                        isError = estado.errores.containsKey("kmNuevo"),
                        supportingText = estado.errores["kmNuevo"]?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(onClick = onVistaPrevia, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
                }
                PasoKm.VISTA_PREVIA -> {
                    val ctx = estado.contexto
                    Text("Confirma la actualización")
                    Text("Actual: ${ctx?.kmActual} km")
                    Text("Nuevo: ${estado.kmNuevoTexto.trim()} km")
                    Button(onClick = onConfirmar, modifier = Modifier.fillMaxWidth()) { Text("Confirmar y guardar") }
                    OutlinedButton(onClick = onVolverEdicion, modifier = Modifier.fillMaxWidth()) { Text("Volver a editar") }
                }
                PasoKm.EXITO -> {
                    Text("Kilometraje actualizado", style = MaterialTheme.typography.titleLarge)
                    Text("${estado.kmConfirmado ?: estado.kmNuevoTexto} km registrados correctamente.")
                    Button(onClick = onContinuarExito, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
                }
            }
        }
    }
}
