package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.domain.AseguradoraSoat
import com.mycar.app.features.soat.domain.CamposSoat
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.viewmodel.*
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarOutlineSoft
import com.mycar.app.ui.theme.MyCarTeal
import com.mycar.app.ui.theme.MyCarTile

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
    /** Si no se pasa, "Ver SOAT" usa [onExitoContinuar] (p. ej. corregir → hub). */
    onVerSoat: (() -> Unit)? = null,
    onConfigurarRecordatorio: () -> Unit,
    onAvanzarAdjunto: () -> Unit = {},
    onRetrocederPaso: () -> Unit = {},
    onRecordarChange: (Boolean) -> Unit = {},
    textoConfirmar: String,
    botonHabilitado: Boolean = !estado.ocupado,
    modifier: Modifier = Modifier
) {
    val tituloPaso = when {
        estado.exito != null || estado.paso == PasoSoatForm.EXITO -> when (estado.modo) {
            TipoFormSoat.REGISTRAR -> "Confirmación"
            TipoFormSoat.RENOVAR -> "Renovación registrada"
            TipoFormSoat.CORREGIR -> "Cambios guardados"
        }
        estado.modo == TipoFormSoat.REGISTRAR && estado.paso == PasoSoatForm.ADJUNTO -> "Adjuntar documento"
        estado.modo == TipoFormSoat.REGISTRAR && estado.paso == PasoSoatForm.REVISION -> "Registrar SOAT"
        else -> titulo
    }
    val volver = {
        when {
            estado.modo == TipoFormSoat.REGISTRAR &&
                estado.paso in listOf(PasoSoatForm.ADJUNTO, PasoSoatForm.REVISION) &&
                estado.exito == null -> onRetrocederPaso()
            else -> onVolver()
        }
    }
    SoatScaffold(tituloPaso, volver, estado.ocupado, modifier) {
        if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        // Solo mensajes no asociados a un campo (los de fecha/póliza van bajo cada input).
        if (estado.mensaje != null && estado.errores.isEmpty()) {
            Text(estado.mensaje, color = Color(0xFFC62828))
        }
        when {
            estado.sesionRequerida -> SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
            estado.exito != null || estado.paso == PasoSoatForm.EXITO -> ExitoSoatPdf(
                estado = estado,
                onVerSoat = onVerSoat ?: onExitoContinuar,
                onVolverDetalle = onExitoContinuar,
                onConfigurarRecordatorio = onConfigurarRecordatorio
            )
            estado.modo == TipoFormSoat.REGISTRAR && estado.paso == PasoSoatForm.ADJUNTO -> {
                SoatPasoAdjuntoPdf(estado, onAdjunto, onAvanzarAdjunto)
            }
            estado.modo == TipoFormSoat.REGISTRAR && estado.paso == PasoSoatForm.REVISION -> {
                SoatPasoRevisionPdf(estado, onAdjunto, onRecordarChange, onConfirmar)
            }
            else -> {
                FormularioCamposSoat(estado, onActualizar, onAdjunto, onModoDoc, onRecordarChange)
                SoatBotonPrincipal(
                    if (estado.modo == TipoFormSoat.REGISTRAR) "Continuar" else textoConfirmar,
                    onContinuar,
                    botonHabilitado && estado.errores.isEmpty()
                )
            }
        }
    }
    if (estado.confirmarVisible) {
        AlertDialog(
            onDismissRequest = onOcultarConfirmacion,
            title = { Text("Confirmar", fontWeight = FontWeight.Bold, color = MyCarNavy) },
            text = {
                Text(
                    when (estado.modo) {
                        TipoFormSoat.RENOVAR -> "Se creará una nueva vigencia y la anterior pasará al historial."
                        TipoFormSoat.CORREGIR -> "Se actualizarán los datos del SOAT actual."
                        else -> "Revisa los datos antes de guardar el SOAT."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmar) {
                    Text(
                        when (estado.modo) {
                            TipoFormSoat.RENOVAR -> "Confirmar renovación"
                            else -> "Guardar"
                        }
                    )
                }
            },
            dismissButton = { TextButton(onClick = onOcultarConfirmacion) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FormularioCamposSoat(
    estado: EstadoSoatForm,
    onActualizar: ((CamposSoat) -> CamposSoat) -> Unit,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    onModoDoc: (ModoDocSoat) -> Unit,
    onRecordarChange: (Boolean) -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        estado.avisoRenovacion?.let { aviso ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MyCarTile,
                border = androidx.compose.foundation.BorderStroke(1.dp, MyCarOutlineSoft)
            ) {
                Text(
                    aviso,
                    Modifier.padding(12.dp),
                    color = MyCarNavy,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Text("Datos obligatorios", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        val c = estado.campos
        SoatSelectorAseguradora(
            valor = c.aseguradora,
            onChange = { t -> onActualizar { it.copy(aseguradora = t) } },
            error = estado.errores["aseguradora"],
            enabled = !estado.ocupado
        )
        SoatCampoPlaceholder(
            etiqueta = "Número de póliza / certificado *",
            valor = c.numeroPoliza,
            onChange = { t -> onActualizar { it.copy(numeroPoliza = SoatFormulario.limitarPoliza(t)) } },
            placeholder = "Ej. ${AseguradoraSoat.ejemploPoliza(c.aseguradora)}",
            error = estado.errores["numeroPoliza"],
            enabled = !estado.ocupado,
            keyboard = KeyboardType.Ascii,
            mayusculas = true
        )
        Text(
            AseguradoraSoat.ayudaPoliza(c.aseguradora),
            color = MyCarMuted,
            style = MaterialTheme.typography.bodySmall
        )
        SoatCampoFecha(
            etiqueta = "Fecha de inicio *",
            valorUi = c.fechaInicio,
            onChange = { t -> onActualizar { it.copy(fechaInicio = t) } },
            error = estado.errores["fechaInicio"],
            enabled = !estado.ocupado
        )
        SoatCampoFecha(
            etiqueta = "Fecha de vencimiento *",
            valorUi = c.fechaVencimiento,
            onChange = { t -> onActualizar { it.copy(fechaVencimiento = t) } },
            error = estado.errores["fechaVencimiento"],
            enabled = !estado.ocupado
        )
        Text("Información adicional", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        SoatCampoPlaceholder(
            etiqueta = "Nombre del contratante",
            valor = c.nombreContratante,
            onChange = { t -> onActualizar { it.copy(nombreContratante = t) } },
            placeholder = "Ej. JUAN PÉREZ GARCÍA",
            enabled = !estado.ocupado,
            mayusculas = true
        )
        SoatCampoPlaceholder(
            etiqueta = "Prima (S/)",
            valor = c.importePrima,
            onChange = { t -> onActualizar { it.copy(importePrima = SoatFormulario.limitarPrima(t)) } },
            placeholder = "Ej. 150.00 (máx. 999999.00)",
            error = estado.errores["importePrima"],
            enabled = !estado.ocupado,
            keyboard = KeyboardType.Decimal
        )
        SoatCampoFecha(
            etiqueta = "Fecha de emisión",
            valorUi = c.fechaEmision,
            onChange = { t -> onActualizar { it.copy(fechaEmision = t) } },
            error = estado.errores["fechaEmision"],
            enabled = !estado.ocupado
        )
        if (estado.modo == TipoFormSoat.CORREGIR) {
            Text("Documento", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
            when {
                estado.modoDocumento == ModoDocSoat.ELIMINAR -> {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MyCarTile,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyCarOutlineSoft)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("El documento actual se quitará al guardar.", color = MyCarMuted)
                            TextButton(
                                onClick = { onModoDoc(ModoDocSoat.MANTENER) },
                                enabled = !estado.ocupado
                            ) { Text("Conservar documento actual") }
                        }
                    }
                }
                estado.tieneDocumentoActual && estado.modoDocumento == ModoDocSoat.MANTENER && estado.adjunto == null -> {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyCarOutlineSoft)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Documento actual conservado", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                            Text("Puedes reemplazarlo con una foto/PDF o quitarlo.", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { onModoDoc(ModoDocSoat.REEMPLAZAR) },
                                    enabled = !estado.ocupado
                                ) { Text("Reemplazar") }
                                TextButton(
                                    onClick = { onModoDoc(ModoDocSoat.ELIMINAR) },
                                    enabled = !estado.ocupado
                                ) { Text("Quitar") }
                            }
                        }
                    }
                }
                else -> {
                    SoatSeccionAdjunto(
                        adjunto = estado.adjunto,
                        error = estado.errorAdjunto,
                        enabled = !estado.ocupado,
                        onAdjunto = onAdjunto,
                        estiloPdf = true,
                        etiqueta = "Documento del SOAT (opcional)",
                        subtituloVacio = "Foto, captura o PDF del SOAT"
                    )
                    if (estado.tieneDocumentoActual) {
                        TextButton(
                            onClick = { onModoDoc(ModoDocSoat.MANTENER) },
                            enabled = !estado.ocupado
                        ) { Text("Volver al documento actual") }
                    }
                }
            }
        }
        if (estado.modo == TipoFormSoat.RENOVAR) {
            Text("Documento nuevo (opcional)", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
            SoatSeccionAdjunto(estado.adjunto, estado.errorAdjunto, !estado.ocupado, onAdjunto, estiloPdf = true)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recordatorio de vencimiento", fontWeight = FontWeight.Bold, color = MyCarNavy)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar al renovar", Modifier.weight(1f))
                        Switch(
                            checked = estado.recordarAlRegistrar,
                            onCheckedChange = onRecordarChange,
                            enabled = !estado.ocupado
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoatPasoAdjuntoPdf(
    estado: EstadoSoatForm,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    onContinuar: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Adjunta una foto o PDF del SOAT. Es opcional; puedes continuar sin documento.",
            color = MyCarMuted
        )
        SoatSeccionAdjunto(
            adjunto = estado.adjunto,
            error = estado.errorAdjunto,
            enabled = !estado.ocupado,
            onAdjunto = onAdjunto,
            estiloPdf = true,
            etiqueta = "Documento del SOAT (opcional)",
            subtituloVacio = "Adjuntar fotografía o PDF (opcional)"
        )
        SoatBotonPrincipal("Continuar", onContinuar, !estado.ocupado)
    }
}

@Composable
private fun SoatPasoRevisionPdf(
    estado: EstadoSoatForm,
    onAdjunto: (AdjuntoSoatUi?) -> Unit,
    onRecordarChange: (Boolean) -> Unit,
    onRegistrar: () -> Unit
) {
    val c = estado.campos
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoatSeccionAdjunto(
            adjunto = estado.adjunto,
            error = estado.errorAdjunto,
            enabled = !estado.ocupado,
            onAdjunto = onAdjunto,
            estiloPdf = true,
            etiqueta = "Documento del SOAT (opcional)",
            subtituloVacio = "Foto, captura o PDF del SOAT"
        )
        val vencido = SoatFormulario.vencimientoYaPasado(c.fechaVencimiento)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recordatorio de vencimiento", fontWeight = FontWeight.Bold, color = MyCarNavy)
                if (vencido) {
                    // RF30-EA06: no ofrecer activación inicial si el vencimiento ya pasó.
                    Text(
                        "El vencimiento ya pasó. Puedes registrar el SOAT y configurar el recordatorio después desde Configurar SOAT.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Te avisaremos 30 días antes del vencimiento del SOAT.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar recordatorio", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = estado.recordarAlRegistrar,
                            onCheckedChange = onRecordarChange,
                            enabled = !estado.ocupado
                        )
                    }
                    SoatFormulario.fechaPrimerAviso(c.fechaVencimiento)?.let {
                        FilaSoat("Primer aviso", it)
                    }
                }
                FilaSoat("Fecha de vencimiento", SoatFormulario.formatearFecha(c.fechaVencimiento))
            }
        }
        SoatBotonPrincipal("Registrar", onRegistrar, !estado.ocupado)
    }
}

@Composable
private fun ExitoSoatPdf(
    estado: EstadoSoatForm,
    onVerSoat: () -> Unit,
    onVolverDetalle: () -> Unit,
    onConfigurarRecordatorio: () -> Unit
) {
    val soat = estado.exito
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            when (estado.modo) {
                TipoFormSoat.REGISTRAR -> "SOAT registrado correctamente"
                TipoFormSoat.RENOVAR -> "Renovación registrada"
                TipoFormSoat.CORREGIR -> "Datos actualizados"
            },
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        soat?.let {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilaSoat("Aseguradora", it.aseguradora)
                    FilaSoat("Póliza", it.numeroPoliza)
                    FilaSoat(
                        "Vigencia",
                        "${SoatFormulario.formatearFecha(it.fechaInicio)} – ${SoatFormulario.formatearFecha(it.fechaVencimiento)}"
                    )
                    val rec = estado.recordatorioAplicado ?: it.recordatorioActivo
                    FilaSoat("Recordatorio", if (rec) "Activado" else "Inactivo")
                }
            }
        }
        SoatBotonPrincipal("Ver SOAT", onVerSoat)
        SoatBotonSecundario("Volver al detalle del vehículo", onVolverDetalle)
        val recActivo = estado.recordatorioAplicado ?: soat?.recordatorioActivo ?: false
        when {
            // Intentó activar en el alta y falló el PUT.
            estado.recordatorioAplicado == false -> {
                Text("El SOAT se guardó, pero el recordatorio no se activó.", color = Color(0xFFC62828))
                SoatBotonSecundario("Configurar recordatorio", onConfigurarRecordatorio)
            }
            // Alta/renovación sin activar: queda inactivo; se configura después.
            !recActivo && estado.modo != TipoFormSoat.CORREGIR -> {
                SoatBotonSecundario("Configurar recordatorio", onConfigurarRecordatorio)
            }
        }
    }
}
