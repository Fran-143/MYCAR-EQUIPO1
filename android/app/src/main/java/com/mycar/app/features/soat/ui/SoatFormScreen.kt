package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.CamposSoat
import com.mycar.app.features.soat.viewmodel.*
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun SoatFormScreen(
    estado: EstadoSoatForm,
    titulo: String,
    onVolver: () -> Unit,
    onActualizar: ((CamposSoat) -> CamposSoat) -> Unit,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    onModoDoc: (ModoDocSoat) -> Unit,
    onContinuar: () -> Unit,
    onConfirmar: () -> Unit,
    onOcultarConfirmacion: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExitoContinuar: () -> Unit,
    onConfigurarRecordatorio: () -> Unit,
    textoConfirmar: String,
    modifier: Modifier = Modifier
) {
    var ofertaRecordatorio by remember { mutableStateOf(false) }
    LaunchedEffect(estado.ofrecerRecordatorio, estado.exito) {
        if (estado.exito != null && estado.ofrecerRecordatorio) ofertaRecordatorio = true
    }
    SoatScaffold(titulo, onVolver, estado.ocupado, modifier) {
        if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        if (estado.sesionRequerida) {
            SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
        } else if (estado.exito != null) {
            Text("SOAT guardado correctamente.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
            SoatBotonPrincipal("Continuar", onExitoContinuar)
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Fechas en formato AAAA-MM-DD.", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                val c = estado.campos
                SoatCampoTexto("Aseguradora", c.aseguradora, { t -> onActualizar { it.copy(aseguradora = t) } }, estado.errores["aseguradora"], !estado.ocupado)
                SoatCampoTexto("N.º de póliza", c.numeroPoliza, { t -> onActualizar { it.copy(numeroPoliza = t) } }, estado.errores["numeroPoliza"], !estado.ocupado)
                SoatCampoTexto("Fecha de inicio", c.fechaInicio, { t -> onActualizar { it.copy(fechaInicio = t) } }, estado.errores["fechaInicio"], !estado.ocupado)
                SoatCampoTexto("Fecha de vencimiento", c.fechaVencimiento, { t -> onActualizar { it.copy(fechaVencimiento = t) } }, estado.errores["fechaVencimiento"], !estado.ocupado)
                SoatCampoTexto("Fecha de emisión (opcional)", c.fechaEmision, { t -> onActualizar { it.copy(fechaEmision = t) } }, estado.errores["fechaEmision"], !estado.ocupado)
                SoatCampoTexto("Contratante (opcional)", c.nombreContratante, { t -> onActualizar { it.copy(nombreContratante = t) } }, null, !estado.ocupado)
                SoatCampoTexto(
                    "Importe de prima (opcional)", c.importePrima,
                    { t -> onActualizar { it.copy(importePrima = t) } },
                    estado.errores["importePrima"],
                    !estado.ocupado,
                    keyboard = KeyboardType.Decimal
                )
                if (estado.modo == TipoFormSoat.CORREGIR && estado.tieneDocumentoActual) {
                    Text("Documento actual", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipDoc("Mantener", estado.modoDocumento == ModoDocSoat.MANTENER) { onModoDoc(ModoDocSoat.MANTENER) }
                        FilterChipDoc("Reemplazar", estado.modoDocumento == ModoDocSoat.REEMPLAZAR) { onModoDoc(ModoDocSoat.REEMPLAZAR) }
                        FilterChipDoc("Quitar", estado.modoDocumento == ModoDocSoat.ELIMINAR) { onModoDoc(ModoDocSoat.ELIMINAR) }
                    }
                }
                if (estado.modo != TipoFormSoat.CORREGIR || estado.modoDocumento != ModoDocSoat.MANTENER) {
                    if (estado.modoDocumento != ModoDocSoat.ELIMINAR) {
                        SoatSeccionAdjunto(estado.adjunto, estado.errorAdjunto, !estado.ocupado, onAdjunto)
                    }
                }
            }
            SoatBotonPrincipal(textoConfirmar, onContinuar, !estado.ocupado)
        }
    }
    if (estado.confirmarVisible) {
        AlertDialog(
            onDismissRequest = onOcultarConfirmacion,
            title = { Text("Confirmar", fontWeight = FontWeight.Bold, color = MyCarNavy) },
            text = { Text("Revisa los datos antes de guardar el SOAT.") },
            confirmButton = { TextButton(onClick = onConfirmar) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onOcultarConfirmacion) { Text("Cancelar") } }
        )
    }
    if (ofertaRecordatorio) {
        AlertDialog(
            onDismissRequest = { ofertaRecordatorio = false; onExitoContinuar() },
            title = { Text("Recordatorio", fontWeight = FontWeight.Bold, color = MyCarNavy) },
            text = { Text("¿Deseas activar el recordatorio de vencimiento del SOAT?") },
            confirmButton = {
                TextButton(onClick = { ofertaRecordatorio = false; onConfigurarRecordatorio() }) { Text("Configurar") }
            },
            dismissButton = {
                TextButton(onClick = { ofertaRecordatorio = false; onExitoContinuar() }) { Text("Ahora no") }
            }
        )
    }
}

@Composable
private fun FilterChipDoc(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    FilterChip(selected = seleccionado, onClick = onClick, label = { Text(texto) })
}