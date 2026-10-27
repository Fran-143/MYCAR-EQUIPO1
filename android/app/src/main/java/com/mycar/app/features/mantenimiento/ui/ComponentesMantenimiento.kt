package com.mycar.app.features.mantenimiento.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun SeccionMantenimiento(numero: String, titulo: String, descripcion: String? = null,
                                 content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(numero, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
internal fun AvisoMantenimiento(texto: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(texto, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun CampoMantenimiento(label: String, value: String, enabled: Boolean,
    error: String? = null, teclado: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) },
        enabled = enabled, isError = error != null, singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth(),
        supportingText = if (error != null) ({ Text(error) }) else null)
}

@Composable
internal fun <T> SelectorMantenimiento(label: String, value: String, opciones: List<Pair<T, String>>,
    enabled: Boolean, error: String? = null, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { abierto = true }, enabled = enabled && opciones.isNotEmpty(),
                shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Text(value, modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = abierto && enabled, onDismissRequest = { abierto = false },
                modifier = Modifier.heightIn(max = 280.dp)) {
                opciones.forEach { (clave, texto) -> DropdownMenuItem(text = { Text(texto) },
                    onClick = { abierto = false; onSelect(clave) }) }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun FechaMantenimiento(label: String, value: String, enabled: Boolean,
    maximo: String?, onValue: (String) -> Unit, opcional: Boolean = false, error: String? = null) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val inicial = runCatching { LocalDate.parse(value) }.getOrElse {
                    runCatching { LocalDate.parse(maximo) }.getOrDefault(LocalDate.now(ZoneId.of("America/Lima")))
                }
                val dialog = DatePickerDialog(context, { _, y, m, d ->
                    onValue(LocalDate.of(y, m + 1, d).toString())
                }, inicial.year, inicial.monthValue - 1, inicial.dayOfMonth)
                maximo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let {
                    dialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                dialog.show()
            }, enabled = enabled, shape = RoundedCornerShape(12.dp)) {
                Text(if (value.isBlank()) "Elegir fecha" else fechaServicioVisible(value))
            }
            if (opcional && value.isNotBlank()) TextButton(onClick = { onValue("") }, enabled = enabled) { Text("Quitar") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun fechaServicioVisible(fecha: String): String = runCatching {
    LocalDate.parse(fecha).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(fecha)

internal fun fechaLecturaVisible(fecha: String): String = runCatching {
    OffsetDateTime.parse(fecha).atZoneSameInstant(ZoneId.of("America/Lima"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (Lima)"
}.getOrDefault("Fecha no disponible")
