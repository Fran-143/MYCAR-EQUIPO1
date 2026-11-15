package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.AseguradoraSoat
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarOutlineSoft
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun SoatCampoPlaceholder(
    etiqueta: String,
    valor: String,
    onChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    mayusculas: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        OutlinedTextField(
            value = valor,
            onValueChange = { raw ->
                val t = if (mayusculas) SoatFormulario.aMayusculasConservandoCursor(raw) else raw
                onChange(t)
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = error != null,
            placeholder = { Text(placeholder, color = MyCarMuted.copy(alpha = 0.75f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                capitalization = if (mayusculas) KeyboardCapitalization.Characters else KeyboardCapitalization.None
            ),
            trailingIcon = trailing
        )
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoatCampoFecha(
    etiqueta: String,
    valorUi: String,
    onChange: (String) -> Unit,
    error: String? = null,
    enabled: Boolean = true,
    placeholder: String = "DD/MM/AAAA"
) {
    var mostrarPicker by remember { mutableStateOf(false) }
    var field by remember(valorUi) {
        mutableStateOf(TextFieldValue(valorUi, TextRange(valorUi.length)))
    }
    LaunchedEffect(valorUi) {
        if (valorUi != field.text) {
            field = TextFieldValue(valorUi, TextRange(valorUi.length))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        OutlinedTextField(
            value = field,
            onValueChange = { nuevo ->
                val digitosAntes = field.text.count { it.isDigit() }
                val digitosNuevos = nuevo.text.count { it.isDigit() }
                val enmascarado = SoatFormulario.aplicarMascaraFecha(nuevo.text)
                // Al borrar, respetar el cursor del usuario; al escribir, ir al final del grupo.
                val cursor = if (digitosNuevos < digitosAntes) {
                    nuevo.selection.start.coerceIn(0, enmascarado.length)
                } else {
                    enmascarado.length
                }
                field = TextFieldValue(enmascarado, TextRange(cursor))
                onChange(enmascarado)
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = error != null,
            placeholder = { Text(placeholder, color = MyCarMuted.copy(alpha = 0.75f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                IconButton(onClick = { if (enabled) mostrarPicker = true }, enabled = enabled) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Abrir calendario", tint = MyCarNavy)
                }
            }
        )
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
    }
    if (mostrarPicker) {
        val inicial = SoatFormulario.parsearFecha(valorUi) ?: SoatFormulario.hoyLima()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val fecha = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val ui = SoatFormulario.fechaIsoAUi(fecha.toString())
                        field = TextFieldValue(ui, TextRange(ui.length))
                        onChange(ui)
                    }
                    mostrarPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun SoatSelectorAseguradora(
    valor: String,
    onChange: (String) -> Unit,
    error: String? = null,
    enabled: Boolean = true
) {
    var abierto by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf("") }
    val seleccion = AseguradoraSoat.porCodigo(valor)
    val opciones = remember(filtro) {
        val f = AseguradoraSoat.normalizar(filtro).orEmpty()
        AseguradoraSoat.activas().filter {
            f.isEmpty() ||
                AseguradoraSoat.normalizar(it.nombre)?.contains(f) == true ||
                AseguradoraSoat.normalizar(it.codigo)?.contains(f) == true ||
                AseguradoraSoat.normalizar(it.id)?.contains(f) == true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Aseguradora *", style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (error != null) Color(0xFFC62828) else MyCarOutlineSoft
            ),
            onClick = { if (enabled) abierto = true }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    seleccion?.nombre ?: "Selecciona una aseguradora",
                    color = if (seleccion == null) MyCarMuted.copy(alpha = 0.75f) else MyCarNavy,
                    fontWeight = if (seleccion == null) FontWeight.Normal else FontWeight.Medium
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MyCarMuted)
            }
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false; filtro = "" }) {
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = SoatFormulario.aMayusculasConservandoCursor(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Buscar aseguradora") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            opciones.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.nombre) },
                    onClick = {
                        onChange(a.nombre)
                        abierto = false
                        filtro = ""
                    }
                )
            }
            if (opciones.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Sin coincidencias", color = MyCarMuted) },
                    onClick = {},
                    enabled = false
                )
            }
        }
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
    }
}
