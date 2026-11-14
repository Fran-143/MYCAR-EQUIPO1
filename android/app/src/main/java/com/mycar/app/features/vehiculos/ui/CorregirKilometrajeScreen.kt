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
fun CorregirKilometrajeRoute(
    clave: String,
    idVehiculo: Long,
    idLectura: Long,
    repository: ActualizarKilometrajeRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onVerHistorial: () -> Unit,
    onVolverVehiculo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: CorregirKilometrajeViewModel = viewModel(
        key = "rf16-$clave-$idVehiculo-$idLectura",
        factory = remember(repository, idVehiculo, idLectura) {
            CorregirKilometrajeViewModel.factory(idVehiculo, idLectura, repository)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    CorregirKilometrajeScreen(
        estado = estado,
        onCambiarKm = vm::cambiarKm,
        onCambiarMotivoOpcion = vm::cambiarMotivoOpcion,
        onCambiarMotivoDetalle = vm::cambiarMotivoDetalle,
        onContinuar = vm::irVistaPrevia,
        onVolverEdicion = vm::volverEdicion,
        onConfirmar = vm::confirmar,
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onVerHistorial = onVerHistorial,
        onVolverVehiculo = onVolverVehiculo,
        modifier = modifier
    )
}

@Composable
fun CorregirKilometrajeScreen(
    estado: EstadoCorregirKm,
    onCambiarKm: (String) -> Unit,
    onCambiarMotivoOpcion: (MotivoCorreccionKm) -> Unit,
    onCambiarMotivoDetalle: (String) -> Unit,
    onContinuar: () -> Unit,
    onVolverEdicion: () -> Unit,
    onConfirmar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onVerHistorial: () -> Unit,
    onVolverVehiculo: () -> Unit,
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
                        when (estado.paso) {
                            PasoCorreccionKm.VISTA_PREVIA -> "Confirmar corrección"
                            PasoCorreccionKm.EXITO -> "Corrección exitosa"
                            else -> "Corregir kilometraje"
                        },
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
                PasoCorreccionKm.CARGA, PasoCorreccionKm.ENVIANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
                    Text(
                        if (estado.paso == PasoCorreccionKm.ENVIANDO) "Guardando corrección…"
                        else "Cargando…",
                        color = MyCarMuted
                    )
                }
                PasoCorreccionKm.SESION -> {
                    Text(estado.mensaje ?: "Inicia sesión.")
                    Button(
                        onClick = onIniciarSesion,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                    ) { Text("Iniciar sesión") }
                }
                PasoCorreccionKm.INACTIVO, PasoCorreccionKm.NO_DISPONIBLE ->
                    Text(estado.mensaje ?: "No disponible.", color = MyCarMuted)
                PasoCorreccionKm.ERROR -> {
                    Text(estado.mensaje ?: "Error.", color = Color(0xFFC62828))
                    OutlinedButton(onClick = onRecargar, shape = RoundedCornerShape(12.dp)) {
                        Text("Reintentar")
                    }
                }
                PasoCorreccionKm.EDICION -> FormularioCorreccion(
                    estado, onCambiarKm, onCambiarMotivoOpcion, onCambiarMotivoDetalle, onContinuar, onVolver
                )
                PasoCorreccionKm.VISTA_PREVIA -> VistaPreviaCorreccion(estado, onConfirmar, onVolverEdicion)
                PasoCorreccionKm.EXITO -> ExitoCorreccion(estado, onVerHistorial, onVolverVehiculo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioCorreccion(
    estado: EstadoCorregirKm,
    onCambiarKm: (String) -> Unit,
    onCambiarMotivoOpcion: (MotivoCorreccionKm) -> Unit,
    onCambiarMotivoDetalle: (String) -> Unit,
    onContinuar: () -> Unit,
    onVolver: () -> Unit
) {
    val ctx = estado.contexto
    Text(
        "Rectifica un error de la última lectura registrada.",
        style = MaterialTheme.typography.bodyMedium,
        color = MyCarMuted
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${ctx?.marca.orEmpty()} ${ctx?.modelo.orEmpty()}", fontWeight = FontWeight.Bold, color = MyCarNavy)
            Text(ctx?.placa.orEmpty(), color = MyCarMuted)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Penúltima lectura", color = MyCarMuted, style = MaterialTheme.typography.labelLarge)
            Text(
                ctx?.kmPenultimo?.let { "${KilometrajeTexto.formatear(it)} km" } ?: "—",
                fontWeight = FontWeight.SemiBold,
                color = MyCarNavy
            )
            Text("Última lectura (a corregir)", color = MyCarMuted, style = MaterialTheme.typography.labelLarge)
            Text(
                ctx?.kmUltimo?.let { "${KilometrajeTexto.formatear(it)} km" } ?: "—",
                fontWeight = FontWeight.SemiBold,
                color = MyCarNavy
            )
        }
    }
    OutlinedTextField(
        value = estado.kmTexto,
        onValueChange = onCambiarKm,
        label = { Text("Nuevo kilometraje *") },
        isError = estado.errores.containsKey("kmCorregido"),
        supportingText = {
            Text(
                estado.errores["kmCorregido"]
                    ?: "Debe ser mayor que ${KilometrajeTexto.formatear(ctx?.kmPenultimo)} km y menor que ${KilometrajeTexto.formatear(ctx?.kmUltimo)} km."
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MyCarBlue,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
    var motivoAbierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = motivoAbierto,
        onExpandedChange = { motivoAbierto = it }
    ) {
        OutlinedTextField(
            value = estado.motivoOpcion?.etiqueta.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Motivo de la corrección *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = motivoAbierto) },
            isError = estado.errores.containsKey("motivo") && estado.motivoOpcion == null,
            supportingText = estado.errores["motivo"]?.takeIf { estado.motivoOpcion == null }?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MyCarBlue,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
        ExposedDropdownMenu(expanded = motivoAbierto, onDismissRequest = { motivoAbierto = false }) {
            MotivoCorreccionKm.entries.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.etiqueta) },
                    onClick = {
                        onCambiarMotivoOpcion(opcion)
                        motivoAbierto = false
                    }
                )
            }
        }
    }
    if (estado.motivoOpcion?.requiereDetalle == true) {
        OutlinedTextField(
            value = estado.motivoDetalle,
            onValueChange = onCambiarMotivoDetalle,
            label = { Text("Describe el motivo *") },
            isError = estado.errores.containsKey("motivo"),
            supportingText = {
                Text(estado.errores["motivo"] ?: "${estado.motivoDetalle.trim().length} / 180")
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            minLines = 3,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MyCarBlue,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
    }
    Text(
        "El valor anterior se conservará en el historial con fines de auditoría.",
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
            onClick = onContinuar,
            modifier = Modifier.weight(1.3f).heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
        ) { Text("Continuar", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun VistaPreviaCorreccion(
    estado: EstadoCorregirKm,
    onConfirmar: () -> Unit,
    onVolverEdicion: () -> Unit
) {
    val ctx = estado.contexto
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen de la corrección", fontWeight = FontWeight.Bold, color = MyCarNavy)
            Text("${ctx?.marca} ${ctx?.modelo} · ${ctx?.placa}", color = MyCarMuted)
            Text("Penúltima lectura: ${KilometrajeTexto.formatear(ctx?.kmPenultimo)} km")
            Text("Última lectura (actual): ${KilometrajeTexto.formatear(ctx?.kmUltimo)} km")
            Text(
                "Nuevo kilometraje: ${estado.kmTexto} km",
                fontWeight = FontWeight.Bold,
                color = MyCarNavy
            )
            Text("Motivo: ${estado.motivoFinal().orEmpty()}")
        }
    }
    Text(
        "El valor anterior se conservará en el historial con fines de auditoría.",
        style = MaterialTheme.typography.bodySmall,
        color = MyCarMuted
    )
    Button(
        onClick = onConfirmar,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
    ) { Text("Confirmar corrección", fontWeight = FontWeight.SemiBold) }
    OutlinedButton(
        onClick = onVolverEdicion,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp)
    ) { Text("Volver a editar") }
}

@Composable
private fun ExitoCorreccion(
    estado: EstadoCorregirKm,
    onVerHistorial: () -> Unit,
    onVolverVehiculo: () -> Unit
) {
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
                "¡Kilometraje corregido!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MyCarNavy,
                textAlign = TextAlign.Center
            )
            Text(
                "La última lectura se actualizó a ${estado.kmConfirmado ?: estado.kmTexto} km y se registró la corrección en el historial.",
                color = MyCarMuted,
                textAlign = TextAlign.Center
            )
        }
    }
    Button(
        onClick = onVerHistorial,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
    ) { Text("Ver historial", fontWeight = FontWeight.SemiBold) }
    OutlinedButton(
        onClick = onVolverVehiculo,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp)
    ) { Text("Volver al vehículo") }
}
