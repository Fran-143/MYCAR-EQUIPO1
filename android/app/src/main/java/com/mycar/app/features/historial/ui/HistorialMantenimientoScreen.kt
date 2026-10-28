package com.mycar.app.features.historial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.historial.domain.*
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FondoHistorial = Color(0xFFF0F5FA)
private val AzulHistorial = Color(0xFF12285F)
private val CelesteHistorial = Color(0xFF007E98)

/** Vista real de producción; ejemplos de vehículos/registros viven únicamente en debug y pruebas. */
@Composable
fun HistorialMantenimientoScreen(
    estado: EstadoHistorial,
    onVolver: () -> Unit,
    onSeleccionar: (Long) -> Unit,
    onReintentar: () -> Unit,
    onActualizar: () -> Unit,
    onRegistrar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    registroDisponible: Boolean = false,
    onIniciarSesion: (() -> Unit)? = null
) {
    Column(modifier.fillMaxSize().background(FondoHistorial).testTag("rf18.pantalla")) {
        Surface(color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVolver, modifier = Modifier.testTag("rf18.volver")) { Text("Volver") }
                Text("Historial", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = AzulHistorial)
                Button(onClick = { estado.seleccionado?.id?.let(onRegistrar) },
                    enabled = estado.puedeRegistrar && registroDisponible,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("rf18.registrar"),
                    colors = ButtonDefaults.buttonColors(containerColor = CelesteHistorial)) { Text("Registrar") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (estado.vehiculos.isNotEmpty()) {
                item { SelectorVehiculoHistorial(estado, onSeleccionar) }
            }
            item { PestanasHistorial() }
            estado.aviso?.let { item { MensajeHistorial("Vehículo no disponible", it) } }
            when (estado.fase) {
                FaseHistorial.SESION_REQUERIDA -> item {
                    MensajeHistorial("Tu historial, en un solo lugar",
                        "Inicia sesión para consultar los mantenimientos de tus vehículos.") {
                        onIniciarSesion?.let { Button(onClick = it) { Text("Iniciar sesión") } }
                    }
                }
                FaseHistorial.CARGANDO_VEHICULOS, FaseHistorial.CARGANDO_HISTORIAL -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("rf18.cargando"), color = CelesteHistorial)
                        Text(if (estado.fase == FaseHistorial.CARGANDO_VEHICULOS) "Cargando tus vehículos…"
                            else "Cargando el historial del vehículo…", color = AzulHistorial)
                    }
                }
                FaseHistorial.SIN_VEHICULOS -> item {
                    MensajeHistorial("No tienes vehículos registrados",
                        "Aquí podrás consultar su historial cuando tengas un vehículo registrado.")
                }
                FaseHistorial.SIN_SELECCION -> item {
                    MensajeHistorial("Elige un vehículo", "Selecciona uno de tus vehículos para consultar su historial. Esta elección no cambia tu vehículo principal.")
                }
                FaseHistorial.ERROR_VEHICULOS, FaseHistorial.ERROR_HISTORIAL -> item {
                    MensajeHistorial("No se pudo cargar el historial", estado.mensaje ?: "Inténtalo nuevamente") {
                        Button(onClick = onReintentar, modifier = Modifier.testTag("rf18.reintentar")) { Text("Reintentar") }
                    }
                }
                FaseHistorial.LISTO -> {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${estado.contador} registros", Modifier.weight(1f).testTag("rf18.contador"),
                                style = MaterialTheme.typography.labelLarge, color = AzulHistorial)
                            Text("Más recientes primero", style = MaterialTheme.typography.labelMedium, color = AzulHistorial)
                        }
                    }
                    if (estado.seleccionado?.activo == false) item {
                        MensajeHistorial("Vehículo inactivo", "Puedes consultar su historial. El registro de mantenimientos está disponible para vehículos activos.")
                    }
                    if (estado.mantenimientos.isEmpty()) item {
                        MensajeHistorial("Todavía no hay mantenimientos", "Este vehículo tiene 0 registros de mantenimiento.")
                    }
                    items(estado.mantenimientos, key = { it.id }) { TarjetaMantenimientoHistorial(it) }
                }
            }
            if (estado.fase !in setOf(FaseHistorial.SESION_REQUERIDA, FaseHistorial.CARGANDO_VEHICULOS)) {
                item {
                    TextButton(onClick = onActualizar, enabled = !estado.cargando,
                        modifier = Modifier.fillMaxWidth().testTag("rf18.actualizar")) { Text("Actualizar historial") }
                }
            }
        }
    }
}

@Composable
private fun SelectorVehiculoHistorial(estado: EstadoHistorial, onSeleccionar: (Long) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Vehículo", style = MaterialTheme.typography.labelMedium, color = AzulHistorial)
            Box {
                TextButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().testTag("rf18.selector")) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(estado.seleccionado?.nombre ?: "Selecciona un vehículo", color = AzulHistorial,
                            fontWeight = FontWeight.SemiBold)
                        estado.seleccionado?.let { Text("${it.placa} · ${if (it.activo) "Activo" else "Inactivo"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                    Text("▾", color = CelesteHistorial)
                }
                DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    estado.vehiculos.forEach { v ->
                        DropdownMenuItem(text = {
                            Column {
                                Text(v.nombre)
                                Text("${v.placa} · ${if (v.activo) "Activo" else "Inactivo"}${if (v.principal) " · Principal" else ""}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }, onClick = { abierto = false; onSeleccionar(v.id) }, modifier = Modifier.testTag("rf18.vehiculo.${v.id}"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PestanasHistorial() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.weight(1f), color = Color.White, shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CelesteHistorial)) {
            Text("Mantenimientos", Modifier.padding(vertical = 18.dp, horizontal = 4.dp),
                color = CelesteHistorial, style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
        }
        listOf("Kilometraje" to "rf18.tab.km", "Talleres" to "rf18.tab.talleres").forEach { (texto, tag) ->
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).testTag(tag),
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 3.dp, vertical = 10.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(texto, style = MaterialTheme.typography.labelMedium)
                    Text("Próximamente", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TarjetaMantenimientoHistorial(m: MantenimientoHistorial) {
    val realizado = m.estado == "REALIZADO"
    Card(Modifier.fillMaxWidth().testTag("rf18.registro.${m.id}"), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(m.titulo, style = MaterialTheme.typography.titleMedium, color = AzulHistorial, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = if (realizado) Color(0xFFDBF8E7) else Color(0xFFFCE4E4)) {
                    Text(if (realizado) "Realizado" else "Anulado", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (realizado) Color(0xFF146943) else Color(0xFF943535), style = MaterialTheme.typography.labelMedium)
                }
                if (m.codigoTipo == "OTRO") Text("Otro", style = MaterialTheme.typography.labelSmall)
            }
            Text("${m.fecha.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))} · ${kilometrajeHistorial(m.kmServicio)} km",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (m.costo == null) "Costo: No registrado" else "Costo: S/ ${m.costo.setScale(2, RoundingMode.UNNECESSARY).toPlainString()}",
                color = AzulHistorial, style = MaterialTheme.typography.bodyMedium)
            m.taller?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {}, enabled = false, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("rf18.detalle.${m.id}")) { Text("Ver detalle · Próximamente") }
        }
    }
}

private fun kilometrajeHistorial(km: Int): String = DecimalFormat("#,##0",
    DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = ' ' }).format(km)

@Composable
private fun MensajeHistorial(titulo: String, texto: String, acciones: @Composable () -> Unit = {}) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AzulHistorial)
            Text(texto, style = MaterialTheme.typography.bodyMedium)
            acciones()
        }
    }
}
