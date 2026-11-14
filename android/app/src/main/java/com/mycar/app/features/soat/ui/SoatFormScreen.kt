package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mycar.app.features.soat.domain.CamposSoat
import com.mycar.app.features.soat.domain.SoatFormulario
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
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        when {
            estado.sesionRequerida -> SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
            estado.exito != null || estado.paso == PasoSoatForm.EXITO -> ExitoSoatPdf(
                estado = estado,
                onVerSoat = onExitoContinuar,
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
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Datos obligatorios", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        val c = estado.campos
        SoatCampoTexto("Aseguradora *", c.aseguradora, { t -> onActualizar { it.copy(aseguradora = t) } }, estado.errores["aseguradora"], !estado.ocupado)
        SoatCampoTexto("Número de póliza *", c.numeroPoliza, { t -> onActualizar { it.copy(numeroPoliza = t) } }, estado.errores["numeroPoliza"], !estado.ocupado)
        SoatCampoTexto("Fecha de inicio *", c.fechaInicio, { t -> onActualizar { it.copy(fechaInicio = t) } }, estado.errores["fechaInicio"], !estado.ocupado)
        SoatCampoTexto("Fecha de vencimiento *", c.fechaVencimiento, { t -> onActualizar { it.copy(fechaVencimiento = t) } }, estado.errores["fechaVencimiento"], !estado.ocupado)
        Text("Información adicional", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        SoatCampoTexto("Nombre del contratante", c.nombreContratante, { t -> onActualizar { it.copy(nombreContratante = t) } }, null, !estado.ocupado)
        SoatCampoTexto(
            "Importe de prima (S/)", c.importePrima,
            { t -> onActualizar { it.copy(importePrima = t) } },
            estado.errores["importePrima"],
            !estado.ocupado,
            keyboard = KeyboardType.Decimal
        )
        SoatCampoTexto("Fecha de emisión", c.fechaEmision, { t -> onActualizar { it.copy(fechaEmision = t) } }, estado.errores["fechaEmision"], !estado.ocupado)
        Text("Fechas en formato AAAA-MM-DD.", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
        if (estado.modo == TipoFormSoat.CORREGIR) {
            Text("Documento", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
            if (estado.tieneDocumentoActual) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = estado.modoDocumento == ModoDocSoat.MANTENER, onClick = { onModoDoc(ModoDocSoat.MANTENER) }, label = { Text("Mantener") })
                    FilterChip(selected = estado.modoDocumento == ModoDocSoat.REEMPLAZAR, onClick = { onModoDoc(ModoDocSoat.REEMPLAZAR) }, label = { Text("Reemplazar archivo") })
                    FilterChip(selected = estado.modoDocumento == ModoDocSoat.ELIMINAR, onClick = { onModoDoc(ModoDocSoat.ELIMINAR) }, label = { Text("Quitar archivo") })
                }
            }
            if (!estado.tieneDocumentoActual || estado.modoDocumento == ModoDocSoat.REEMPLAZAR) {
                SoatSeccionAdjunto(estado.adjunto, estado.errorAdjunto, !estado.ocupado, onAdjunto, estiloPdf = true)
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
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Adjunta una foto o PDF del SOAT. Es opcional; puedes continuar sin documento.",
            color = MyCarMuted
        )
        SoatSeccionAdjunto(estado.adjunto, estado.errorAdjunto, !estado.ocupado, onAdjunto, estiloPdf = true)
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
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Documento", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
        if (estado.adjunto != null) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(estado.adjunto.nombre, Modifier.weight(1f).padding(12.dp), color = MyCarNavy)
                    TextButton(onClick = { onAdjunto(null) }, enabled = !estado.ocupado) { Text("Quitar") }
                }
            }
        } else {
            Text("Sin documento adjunto", color = MyCarMuted)
            SoatSeccionAdjunto(null, estado.errorAdjunto, !estado.ocupado, onAdjunto, estiloPdf = false)
        }
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
        if (estado.recordatorioAplicado == false) {
            Text("El SOAT se guardó, pero el recordatorio no se activó.", color = Color(0xFFC62828))
            SoatBotonSecundario("Configurar recordatorio", onConfigurarRecordatorio)
        }
    }
}
