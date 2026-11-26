package com.mycar.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarStatusProxima

@Composable
fun CambiosSinGuardarDialog(
    onSeguirEditando: () -> Unit,
    onDescartar: () -> Unit,
    titulo: String = "Cambios sin guardar",
    mensaje: String = "Tienes cambios que aún no has guardado. Si sales, se perderán.",
    etiquetaDescartar: String = "Descartar cambios"
) {
    AlertDialog(
        onDismissRequest = onSeguirEditando,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MyCarStatusProxima,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                titulo,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                mensaje,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onSeguirEditando,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
            ) { Text("Seguir editando") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDescartar, shape = RoundedCornerShape(12.dp)) {
                Text(etiquetaDescartar)
            }
        }
    )
}
