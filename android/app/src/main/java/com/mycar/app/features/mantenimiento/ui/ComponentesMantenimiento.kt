package com.mycar.app.features.mantenimiento.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.ui.theme.MyCarBlue
import com.mycar.app.ui.theme.MyCarTile
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BordeAzulSuave = Color(0xFFC5D8F0)

@Composable
internal fun SeccionMantenimiento(
    numero: String,
    titulo: String,
    descripcion: String? = null,
    icono: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val oscuro = false // Tema fijo claro MyCar
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (oscuro) 0.dp else 1.dp),
        border = BorderStroke(1.dp, if (oscuro) scheme.outlineVariant else BordeAzulSuave)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (oscuro) scheme.primaryContainer else MyCarTile
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            numero,
                            color = if (oscuro) scheme.onPrimaryContainer else MyCarBlue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (icono != null) {
                    Icon(icono, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    descripcion?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
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
    singleLine: Boolean = true, modifier: Modifier = Modifier,
    icono: ImageVector? = null, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        enabled = enabled,
        isError = error != null,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        leadingIcon = if (icono == null) null else {
            { Icon(icono, contentDescription = null) }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
        colors = coloresCampo(),
        supportingText = if (error != null) ({ Text(error) }) else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SelectorMantenimiento(label: String, value: String, opciones: List<Pair<T, String>>,
    enabled: Boolean, error: String? = null, modifier: Modifier = Modifier,
    icono: ImageVector? = null, onSelect: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val puedeAbrir = enabled && opciones.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = abierto && puedeAbrir,
        onExpandedChange = { if (puedeAbrir) abierto = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = puedeAbrir,
            label = { Text(label) },
            leadingIcon = if (icono == null) null else {
                { Icon(icono, contentDescription = null) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto && puedeAbrir) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = puedeAbrir)
                .fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = coloresCampo()
        )
        ExposedDropdownMenu(
            expanded = abierto && puedeAbrir,
            onDismissRequest = { abierto = false },
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            opciones.forEach { (clave, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = {
                        abierto = false
                        onSelect(clave)
                    }
                )
            }
        }
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
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (value.isBlank()) "Elegir fecha" else fechaServicioVisible(value))
            }
            if (opcional && value.isNotBlank()) TextButton(onClick = { onValue("") }, enabled = enabled) { Text("Quitar") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun coloresCampo(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    if (isSystemInDarkTheme()) return OutlinedTextFieldDefaults.colors()
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = BordeAzulSuave,
        cursorColor = scheme.primary,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        focusedLeadingIconColor = scheme.primary,
        unfocusedLeadingIconColor = scheme.primary,
        disabledLeadingIconColor = scheme.onSurface.copy(alpha = 0.38f),
        errorLeadingIconColor = scheme.error,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        errorBorderColor = scheme.error,
        errorLabelColor = scheme.error,
        errorSupportingTextColor = scheme.error,
        errorCursorColor = scheme.error
    )
}

internal fun fechaServicioVisible(fecha: String): String = runCatching {
    LocalDate.parse(fecha).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(fecha)

internal fun fechaLecturaVisible(fecha: String): String = runCatching {
    OffsetDateTime.parse(fecha).atZoneSameInstant(ZoneId.of("America/Lima"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (Lima)"
}.getOrDefault("Fecha no disponible")
