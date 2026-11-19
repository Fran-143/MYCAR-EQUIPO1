package com.mycar.app.features.tive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.ui.*
import com.mycar.app.features.tive.domain.CamposTive
import com.mycar.app.features.tive.domain.TiveFormulario
import com.mycar.app.features.tive.viewmodel.*
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun TiveFormScreen(
    estado: EstadoTiveForm,
    titulo: String,
    onVolver: () -> Unit,
    onActualizar: (transform: (CamposTive) -> CamposTive, campoTocado: String?) -> Unit,
    onAdjunto: (com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi?) -> Unit,
    onModoDoc: (ModoDocTive) -> Unit,
    onConfirmar: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: () -> Unit,
    onCompletarVin: () -> Unit,
    onCerrarDialogoVin: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoatScaffold(titulo, onVolver, estado.ocupado, modifier) {
        if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        if (estado.mensaje != null) {
            Text(estado.mensaje, color = Color(0xFFC62828))
        }
        when {
            estado.sesionRequerida -> SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
            estado.fase == FaseTiveForm.EXITO -> {
                Text("TIVE guardada correctamente.", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                SoatBotonPrincipal("Ver TIVE", onExito)
                SoatBotonSecundario("Volver al detalle", onExito)
            }
            estado.fase == FaseTiveForm.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Verificando requisitos…", color = MyCarMuted)
            }
            estado.fase == FaseTiveForm.BLOQUEO -> {
                Text(estado.mensaje ?: estado.precheck?.mensaje ?: "No puedes continuar.", color = Color(0xFFC62828))
                if (estado.precheck?.codigoBloqueo == "VIN_AUSENTE") {
                    SoatBotonPrincipal("Completar VIN", onCompletarVin)
                }
                SoatBotonSecundario("Volver", onVolver)
            }
            estado.fase == FaseTiveForm.ERROR -> {
                Text(estado.mensaje ?: "No se pudo cargar el formulario.", color = Color(0xFFC62828))
                SoatBotonSecundario("Reintentar", onVolver)
            }
            estado.fase == FaseTiveForm.FORMULARIO -> {
                CamposTiveForm(estado, onActualizar, onAdjunto, onModoDoc)
                SoatBotonPrincipal(
                    if (estado.modo == TipoFormTive.REGISTRAR) "Registrar TIVE" else "Guardar cambios",
                    onConfirmar,
                    enabled = !estado.ocupado && !estado.formularioIncompleto
                )
            }
        }
    }
    // El modal de VIN ausente se muestra en Detalle; este diálogo queda como respaldo.
    if (estado.mostrarDialogoVin) {
        AlertDialog(
            onDismissRequest = onCerrarDialogoVin,
            title = { Text("Necesitas completar tu vehículo") },
            text = { Text("Para registrar la TIVE, primero necesitas completar el número VIN de tu vehículo.") },
            confirmButton = {
                TextButton(onClick = { onCerrarDialogoVin(); onCompletarVin() }) { Text("COMPLETAR") }
            },
            dismissButton = { TextButton(onClick = onCerrarDialogoVin) { Text("CANCELAR") } }
        )
    }
}

@Composable
private fun CamposTiveForm(
    estado: EstadoTiveForm,
    onActualizar: (transform: (CamposTive) -> CamposTive, campoTocado: String?) -> Unit,
    onAdjunto: (com.mycar.app.features.soat.viewmodel.AdjuntoSoatUi?) -> Unit,
    onModoDoc: (ModoDocTive) -> Unit
) {
    val c = estado.campos
    Text("Datos de la TIVE", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
    CampoValidado(
        etiqueta = "Número de título *",
        valor = c.numeroTitulo,
        placeholder = TiveFormulario.PH_TITULO,
        ayuda = TiveFormulario.AYUDA_TITULO,
        error = estado.errores["numeroTitulo"],
        ok = estado.hintsOk["numeroTitulo"],
        contador = "${c.numeroTitulo.length}/${TiveFormulario.MAX_TITULO}",
        onChange = { v ->
            onActualizar({ it.copy(numeroTitulo = v.uppercase().take(TiveFormulario.MAX_TITULO + 5)) }, "numeroTitulo")
        }
    )
    CampoValidado(
        etiqueta = "Código de verificación *",
        valor = c.codigoVerificacion,
        placeholder = TiveFormulario.PH_CODIGO,
        ayuda = TiveFormulario.AYUDA_CODIGO,
        error = estado.errores["codigoVerificacion"],
        ok = estado.hintsOk["codigoVerificacion"],
        contador = "${c.codigoVerificacion.length}/${TiveFormulario.MAX_CODIGO}",
        onChange = { v ->
            onActualizar(
                { it.copy(codigoVerificacion = v.uppercase().replace(" ", "").take(TiveFormulario.MAX_CODIGO + 5)) },
                "codigoVerificacion"
            )
        }
    )
    SoatCampoFecha(
        etiqueta = "Fecha de título *",
        valorUi = c.fechaTitulo,
        onChange = { fecha -> onActualizar({ f -> f.copy(fechaTitulo = fecha) }, "fechaTitulo") },
        error = estado.errores["fechaTitulo"],
        enabled = !estado.ocupado
    )
    Text("Evidencia fotográfica (opcional)", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
    Text("JPG, PNG o PDF (máx. 10 MB). La vista previa no certifica autenticidad ante SUNARP.",
        color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
    if (estado.modo == TipoFormTive.EDITAR && estado.tieneDocumentoActual) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ModoDocTive.MANTENER, ModoDocTive.REEMPLAZAR, ModoDocTive.QUITAR).forEach { modo ->
                FilterChip(
                    selected = estado.modoDocumento == modo,
                    onClick = { onModoDoc(modo) },
                    label = {
                        Text(
                            when (modo) {
                                ModoDocTive.MANTENER -> "Mantener"
                                ModoDocTive.REEMPLAZAR -> "Reemplazar"
                                else -> "Quitar"
                            }
                        )
                    }
                )
            }
        }
    }
    val mostrarAdjunto = estado.modo == TipoFormTive.REGISTRAR ||
        estado.modoDocumento == ModoDocTive.AGREGAR ||
        estado.modoDocumento == ModoDocTive.REEMPLAZAR ||
        (!estado.tieneDocumentoActual && estado.modo == TipoFormTive.EDITAR)
    if (mostrarAdjunto) {
        SoatSeccionAdjunto(
            adjunto = estado.adjunto,
            error = estado.errorAdjunto ?: estado.errores["documento"],
            enabled = !estado.ocupado,
            onAdjunto = onAdjunto,
            estiloPdf = true,
            etiqueta = "Adjuntar foto o PDF de la TIVE",
            subtituloVacio = "JPG, PNG o PDF (máx. 10 MB)"
        )
    }
}

@Composable
fun CampoSoloLectura(etiqueta: String, valor: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun CampoValidado(
    etiqueta: String,
    valor: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    ayuda: String? = null,
    error: String? = null,
    ok: String? = null,
    contador: String? = null,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        OutlinedTextField(
            value = valor,
            onValueChange = onChange,
            enabled = enabled,
            placeholder = placeholder?.let { { Text(it, color = MyCarMuted) } },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = minLines == 1,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val texto = when {
                        error != null -> error
                        ok != null -> ok
                        else -> ayuda.orEmpty()
                    }
                    val color = when {
                        error != null -> Color(0xFFC62828)
                        ok != null -> Color(0xFF2E7D32)
                        else -> MyCarMuted
                    }
                    Text(texto, color = color, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    contador?.let {
                        Text(it, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        )
    }
}
