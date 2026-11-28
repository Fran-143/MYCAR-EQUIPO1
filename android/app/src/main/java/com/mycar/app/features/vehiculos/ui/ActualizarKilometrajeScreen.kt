package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.vehiculos.domain.ActualizarKilometrajeRepository
import com.mycar.app.features.vehiculos.domain.KilometrajeTexto
import com.mycar.app.features.vehiculos.viewmodel.*
import com.mycar.app.ui.theme.*

@Composable
fun ActualizarKilometrajeRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: ActualizarKilometrajeRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onActualizado: () -> Unit,
    onIrInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ActualizarKilometrajeViewModel = viewModel(
        key = "rf14-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) {
            ActualizarKilometrajeViewModel.factory(idVehiculo, repository)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    ActualizarKilometrajeScreen(
        estado, vm::cambiarKm, vm::irVistaPrevia, vm::volverEdicion,
        vm::confirmar, vm::cargarContexto, onVolver, onIniciarSesion,
        onContinuarExito = { onActualizado(); onVolver() },
        onIrInicioExito = { onActualizado(); onIrInicio() },
        modifier
    )
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
    onIrInicioExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MyCarBg,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver) { Text("Volver") }
                    Text(
                        "Actualizar kilometraje",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MyCarNavy
                    )
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
            when (estado.paso) {
                PasoKm.CARGA, PasoKm.ENVIANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
                    Text(
                        if (estado.paso == PasoKm.ENVIANDO) "Guardando lectura…" else "Cargando kilometraje…",
                        color = MyCarMuted
                    )
                }
                PasoKm.SESION -> {
                    Text(estado.mensaje ?: "Inicia sesión.")
                    Button(
                        onClick = onIniciarSesion,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                    ) { Text("Iniciar sesión") }
                }
                PasoKm.INACTIVO, PasoKm.SIN_LECTURA -> Text(estado.mensaje ?: "No disponible.", color = MyCarMuted)
                PasoKm.ERROR -> {
                    Text(estado.mensaje ?: "Error.", color = Color(0xFFC62828))
                    OutlinedButton(onClick = onRecargar, shape = RoundedCornerShape(12.dp)) {
                        Text("Reintentar")
                    }
                }
                PasoKm.EDICION -> {
                    val km = estado.contexto?.kmActual
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Kilometraje vigente", color = MyCarMuted, style = MaterialTheme.typography.labelLarge)
                            Text(
                                km?.let { "${KilometrajeTexto.formatear(it)} km" } ?: "No disponible",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MyCarNavy
                            )
                        }
                    }
                    OutlinedTextField(
                        value = estado.kmNuevoTexto,
                        onValueChange = onCambiarKm,
                        label = { Text("Nuevo kilometraje (km)") },
                        isError = estado.errores.containsKey("kmNuevo"),
                        supportingText = {
                            Text(
                                estado.errores["kmNuevo"]
                                    ?: "Máximo 9 999 999.99 · hasta 2 decimales · solo números"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MyCarBlue,
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )
                    Text(
                        "La fecha se registra automáticamente al confirmar. El nuevo valor debe ser mayor al vigente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyCarMuted
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onVolver,
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Cancelar") }
                        Button(
                            onClick = onVistaPrevia,
                            modifier = Modifier.weight(1.3f).heightIn(min = 50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                        ) { Text("Continuar", fontWeight = FontWeight.SemiBold) }
                    }
                }
                PasoKm.VISTA_PREVIA -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Confirma la actualización", fontWeight = FontWeight.Bold, color = MyCarNavy)
                            Text(
                                "Actual: ${estado.contexto?.kmActual?.let(KilometrajeTexto::formatear)} km",
                                color = MyCarMuted
                            )
                            Text(
                                "Nuevo: ${estado.kmNuevoTexto.trim()} km",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MyCarNavy
                            )
                        }
                    }
                    estado.mensaje?.let {
                        Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = onConfirmar,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                    ) { Text("Confirmar y guardar", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = onVolverEdicion,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Volver a editar") }
                }
                PasoKm.EXITO -> {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("✓", style = MaterialTheme.typography.displaySmall, color = MyCarStatusActivo)
                            Text(
                                "Kilometraje actualizado correctamente",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MyCarNavy,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "${estado.kmConfirmado?.let(KilometrajeTexto::formatear) ?: estado.kmNuevoTexto} km",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MyCarBlue
                            )
                        }
                    }
                    Button(
                        onClick = onContinuarExito,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                    ) { Text("Continuar", fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = onIrInicioExito,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Ir a inicio") }
                }
            }
        }
    }
}
