package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState

@Composable
internal fun RepuestoMantenimientoDialog(s: MantenimientoUiState, acciones: AccionesMantenimiento) {
    val p = s.editor ?: return
    val editando = s.borrador.repuestos.any { it.idLocal == p.idLocal }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = acciones::cerrarEditor,
        shape = RoundedCornerShape(28.dp),
        containerColor = scheme.surface,
        icon = {
            Surface(shape = CircleShape, color = scheme.primaryContainer) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
        },
        title = {
            Text(
                if (editando) "Editar repuesto" else "Agregar repuesto",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Añade los repuestos utilizados en este servicio. Los campos con * son obligatorios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                CampoMantenimiento(
                    "Nombre del repuesto *", p.nombre, true, s.erroresEditor["nombre"],
                    modifier = Modifier.testTag("rf17.repuesto.nombre"),
                    icono = Icons.Outlined.Inventory2
                ) { v -> acciones.cambiarEditor { it.copy(nombre = v) } }
                CampoMantenimiento("Marca (opcional)", p.marca, true, s.erroresEditor["marca"],
                    icono = Icons.Outlined.Edit) { v -> acciones.cambiarEditor { it.copy(marca = v) } }
                CantidadRepuesto(
                    cantidad = p.cantidad,
                    error = s.erroresEditor["cantidad"],
                    onCantidad = { v -> acciones.cambiarEditor { it.copy(cantidad = v) } }
                )
                CampoMantenimiento(
                    "Precio unitario (opcional)", p.precio, true,
                    s.erroresEditor["precio"], KeyboardType.Decimal,
                    icono = Icons.Outlined.Payments
                ) { v -> acciones.cambiarEditor { it.copy(precio = v) } }
                Text(
                    "Usa hasta dos decimales. El precio puede quedar vacío o ser 0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = acciones::confirmarRepuesto, shape = RoundedCornerShape(14.dp)) {
                Icon(
                    if (editando) Icons.Outlined.Edit else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (editando) "Guardar cambios" else "Agregar repuesto")
            }
        },
        dismissButton = { TextButton(onClick = acciones::cerrarEditor) { Text("Cancelar") } }
    )
}

@Composable
private fun CantidadRepuesto(cantidad: String, error: String?, onCantidad: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val n = cantidad.toIntOrNull()
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = {
                val actual = cantidad.toIntOrNull() ?: return@FilledTonalIconButton
                if (actual > 1) onCantidad((actual - 1).toString())
            },
            enabled = n != null && n > 1,
            modifier = Modifier.padding(top = 8.dp).size(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Disminuir cantidad")
        }
        Box(Modifier.weight(1f)) {
            CampoMantenimiento("Cantidad *", cantidad, true, error, KeyboardType.Number) { onCantidad(it) }
        }
        FilledTonalIconButton(
            onClick = {
                val actual = cantidad.toIntOrNull()
                onCantidad(
                    when {
                        actual == null || actual < 0 -> "1"
                        actual >= 1_000_000 -> cantidad
                        else -> (actual + 1).toString()
                    }
                )
            },
            modifier = Modifier.padding(top = 8.dp).size(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary
            )
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Aumentar cantidad")
        }
    }
}
