package com.mycar.app.features.localtest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.BuildConfig

/**
 * Banner exclusivo de APK hotfix local: muestra el OTP real emitido por el backend
 * de pruebas encima del campo de introducción. No se incluye en release/producción.
 */
@Composable
fun CodigoPruebaLocalBanner(
    codigo: String?,
    modifier: Modifier = Modifier,
    etiqueta: String = "Código de prueba local"
) {
    if (!BuildConfig.LOCAL_TEST_TOOLS) return
    val clean = codigo?.trim()?.takeIf { it.isNotBlank() } ?: return
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFE8F5F3),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, color = Color(0xFF5A6A7A), fontWeight = FontWeight.Medium)
                Text(
                    clean,
                    color = Color(0xFF0B1F33),
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = { copiarOtpLocal(context, clean) }) {
                Text("Copiar")
            }
        }
    }
}

suspend fun consultarOtpSimulado(destinatario: String?, finalidad: String): String? {
    if (!BuildConfig.LOCAL_TEST_TOOLS) return null
    val correo = destinatario?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val r = LocalTestClient.api().ultimoOtp(
            LocalTestClient.QA_TOKEN,
            LocalTestFinalidadBody(correo, finalidad)
        )
        if (!r.isSuccessful) return null
        val body = r.body() ?: return null
        val codigo = body.codigoOtp?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val expira = body.expiraOtpUtc
        if (!expira.isNullOrBlank()) {
            try {
                val t = java.time.Instant.parse(expira)
                if (!java.time.Instant.now().isBefore(t)) return null
            } catch (_: Exception) {
                // Si no parsea vigencia, aún mostramos el último código emitido.
            }
        }
        codigo
    } catch (_: Exception) {
        null
    }
}

fun copiarOtpLocal(context: Context, texto: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("otp_mycar", texto))
    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
}
