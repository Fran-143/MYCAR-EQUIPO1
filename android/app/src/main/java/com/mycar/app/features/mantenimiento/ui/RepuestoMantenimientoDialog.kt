package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState

@Composable
internal fun RepuestoMantenimientoDialog(s: MantenimientoUiState, acciones: AccionesMantenimiento) {
    val p = s.editor ?: return
    val editando = s.borrador.repuestos.any { it.idLocal == p.idLocal }
    AlertDialog(onDismissRequest = acciones::cerrarEditor,
        title = { Text(if (editando) "Editar repuesto" else "Agregar repuesto") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Añade los repuestos utilizados en este servicio. Los campos con * son obligatorios.",
                    style = MaterialTheme.typography.bodySmall)
                CampoMantenimiento("Nombre del repuesto *", p.nombre, true, s.erroresEditor["nombre"],
                    modifier = Modifier.testTag("rf17.repuesto.nombre")) { v -> acciones.cambiarEditor { it.copy(nombre = v) } }
                CampoMantenimiento("Marca (opcional)", p.marca, true, s.erroresEditor["marca"]) {
                    v -> acciones.cambiarEditor { it.copy(marca = v) }
                }
                CampoMantenimiento("Cantidad *", p.cantidad, true, s.erroresEditor["cantidad"], KeyboardType.Number) {
                    v -> acciones.cambiarEditor { it.copy(cantidad = v) }
                }
                CampoMantenimiento("Precio unitario (opcional)", p.precio, true,
                    s.erroresEditor["precio"], KeyboardType.Decimal) { v -> acciones.cambiarEditor { it.copy(precio = v) } }
                Text("Usa hasta dos decimales. El precio puede quedar vacío o ser 0.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = acciones::confirmarRepuesto) {
            Text(if (editando) "Guardar cambios" else "Agregar repuesto")
        } },
        dismissButton = { TextButton(onClick = acciones::cerrarEditor) { Text("Cancelar") } })
}
