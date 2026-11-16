package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.ui.theme.*

@Composable
fun SoatScaffold(
    titulo: String,
    onVolver: () -> Unit,
    ocupado: Boolean = false,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MyCarBg,
        topBar = {
            Surface(color = Color.White, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver, enabled = !ocupado) { Text("Volver") }
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MyCarNavy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        // Scroll en el contenedor acotado del Scaffold (evita crash por altura infinita).
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = contenido
        )
    }
}

@Composable
fun SoatTarjetaDatos(soat: SoatDto, modifier: Modifier = Modifier) {
    var polizaVisible by remember(soat.idSoat) { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeCobertura(soat.estadoCobertura.orEmpty())
                if (soat.recordatorioActivo) {
                    Surface(shape = RoundedCornerShape(50), color = MyCarSky.copy(alpha = 0.2f)) {
                        Text(
                            "Recordatorio activo",
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MyCarBlue
                        )
                    }
                }
            }
            FilaSoat("Aseguradora", soat.aseguradora)
            FilaSoatSensibles(
                etiqueta = "Póliza",
                valorCompleto = soat.numeroPoliza,
                valorEnmascarado = SoatFormulario.enmascararPoliza(soat.numeroPoliza),
                visible = polizaVisible,
                onToggle = { polizaVisible = !polizaVisible }
            )
            FilaSoat("Inicio", SoatFormulario.formatearFecha(soat.fechaInicio))
            FilaSoat("Vencimiento", SoatFormulario.formatearFecha(soat.fechaVencimiento))
            soat.fechaEmision?.let { FilaSoat("Emisión", SoatFormulario.formatearFecha(it)) }
            soat.nombreContratante?.takeIf { it.isNotBlank() }?.let { FilaSoat("Contratante", it) }
            soat.importePrima?.let { FilaSoat("Prima", "S/ %.2f".format(it)) }
        }
    }
}

@Composable
fun BadgeCobertura(estado: String) {
    val color = Color(SoatFormulario.colorCobertura(estado))
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            SoatFormulario.etiquetaCobertura(estado),
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
fun FilaSoat(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = MyCarMuted)
        Text(valor, fontWeight = FontWeight.Medium, color = MyCarNavy, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun FilaSoatSensibles(
    etiqueta: String,
    valorCompleto: String,
    valorEnmascarado: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(etiqueta, color = MyCarMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (visible) valorCompleto else valorEnmascarado,
                fontWeight = FontWeight.Medium,
                color = MyCarNavy,
                modifier = Modifier.padding(start = 12.dp)
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Ocultar póliza" else "Mostrar póliza",
                    tint = MyCarBlue
                )
            }
        }
    }
}

@Composable
fun SoatCampoTexto(
    etiqueta: String,
    valor: String,
    onChange: (String) -> Unit,
    error: String? = null,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MyCarMuted)
        OutlinedTextField(
            value = valor,
            onValueChange = onChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = keyboard != KeyboardType.Text
        )
        error?.let { Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun SoatBotonPrincipal(texto: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
    ) { Text(texto, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SoatBotonSecundario(texto: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MyCarNavy)
    ) { Text(texto, fontWeight = FontWeight.SemiBold) }
}
