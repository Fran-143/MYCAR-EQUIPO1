package com.mycar.app.features.autenticacion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.mycar.app.features.autenticacion.data.MfaStep
import com.mycar.app.features.autenticacion.data.MfaUiState

private val Navy = Color(0xFF12285F)
private val Teal = Color(0xFF007E98)
private val Bg = Color(0xFFF0F5FA)

@Composable
fun MfaFlowScreen(
    state: MfaUiState,
    onStartEnroll: () -> Unit,
    onContinueIntro: () -> Unit,
    onContinueToConfirm: () -> Unit,
    onConfirmEnroll: (String) -> Unit,
    onConfirmBackup: () -> Unit,
    onVerify: (String) -> Unit,
    onRecover: (String) -> Unit,
    onGoRecover: () -> Unit,
    onLogout: () -> Unit
) {
    when (state.step) {
        MfaStep.ENROLL_INTRO -> MfaIntroScreen(state, onContinue = {
            onContinueIntro()
            onStartEnroll()
        }, onLogout = onLogout)
        MfaStep.ENROLL_QR -> MfaQrScreen(state, onContinue = onContinueToConfirm, onLogout = onLogout)
        MfaStep.ENROLL_CONFIRM -> MfaConfirmScreen(state, title = "Confirmar autenticador",
            subtitle = "Ingresa el código de 6 dígitos de tu app autenticadora.",
            onSubmit = onConfirmEnroll, onLogout = onLogout)
        MfaStep.RECOVERY_CODES -> MfaRecoveryCodesScreen(state, onConfirm = onConfirmBackup, onLogout = onLogout)
        MfaStep.VERIFY -> MfaConfirmScreen(state, title = "Verificación administrativa",
            subtitle = "Ingresa el código TOTP de 6 dígitos. El desafío caduca en minutos.",
            onSubmit = onVerify, onLogout = onLogout, secondary = "Usar código de recuperación" to onGoRecover)
        MfaStep.BLOCKED -> MfaBlockedScreen(state, onLogout = onLogout, onRecover = onGoRecover)
        MfaStep.RECOVER -> MfaRecoverScreen(state, onSubmit = onRecover, onLogout = onLogout)
        MfaStep.RECONFIGURE -> MfaIntroScreen(
            state.copy(error = state.error ?: "Reconfigura tu autenticador con un código nuevo."),
            onContinue = {
                onContinueIntro()
                onStartEnroll()
            },
            onLogout = onLogout,
            title = "Reconfiguración del autenticador"
        )
    }
}

@Composable
private fun MfaShell(
    title: String,
    subtitle: String,
    onLogout: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(containerColor = Bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MyCar", color = Navy, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Navy, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFF4A5568), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            content()
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onLogout) { Text("Cerrar sesión") }
        }
    }
}

@Composable
private fun MfaIntroScreen(
    state: MfaUiState,
    onContinue: () -> Unit,
    onLogout: () -> Unit,
    title: String = "Configurar seguridad administrativa"
) {
    MfaShell(title, "Protege el acceso de administrador con Google Authenticator (TOTP).", onLogout) {
        Text(
            "Necesitarás una app autenticadora. Tras vincularla verás códigos de recuperación una sola vez.",
            textAlign = TextAlign.Center, color = Color(0xFF4A5568)
        )
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.busy) "Generando…" else "Continuar") }
    }
}

@Composable
private fun MfaQrScreen(state: MfaUiState, onContinue: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val qr = remember(state.otpauthUri) {
        state.otpauthUri?.let { encodeQr(it, 512) }
    }
    MfaShell("Vincular autenticador", "Escanea el QR o copia la clave manual en tu app.", onLogout) {
        if (qr != null) {
            Image(bitmap = qr.asImageBitmap(), contentDescription = "Código QR TOTP",
                modifier = Modifier.size(220.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Clave manual", color = Navy, fontWeight = FontWeight.SemiBold)
        Text(state.secretoManual.orEmpty(), fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("totp", state.secretoManual.orEmpty()))
        }) { Text("Copiar clave") }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ya lo vinculé") }
    }
}

@Composable
private fun MfaConfirmScreen(
    state: MfaUiState,
    title: String,
    subtitle: String,
    onSubmit: (String) -> Unit,
    onLogout: () -> Unit,
    secondary: Pair<String, () -> Unit>? = null
) {
    var code by remember { mutableStateOf("") }
    MfaShell(title, subtitle, onLogout) {
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            label = { Text("Código de 6 dígitos") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (state.intento != null) {
            Text("Intento ${state.intento} de ${state.maxIntentos}", color = Color(0xFF4A5568))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(code) },
            enabled = !state.busy && code.length == 6,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.busy) "Verificando…" else "Verificar") }
        if (secondary != null) {
            TextButton(onClick = secondary.second, enabled = !state.busy) { Text(secondary.first) }
        }
    }
}

@Composable
private fun MfaRecoveryCodesScreen(state: MfaUiState, onConfirm: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    MfaShell(
        "Códigos de recuperación",
        "Guárdalos en un lugar seguro. Solo se muestran una vez.",
        onLogout
    ) {
        state.recoveryCodes.forEach { code ->
            Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("recovery", state.recoveryCodes.joinToString("\n")))
        }) { Text("Copiar códigos") }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            Text("Ya guardé los códigos de recuperación")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            enabled = checked && !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.busy) "Entrando…" else "Continuar al panel") }
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MfaBlockedScreen(state: MfaUiState, onLogout: () -> Unit, onRecover: () -> Unit) {
    MfaShell(
        "Acceso bloqueado temporalmente",
        state.error ?: "Demasiados intentos fallidos. Espera unos minutos.",
        onLogout
    ) {
        if (state.bloqueoHastaUtc != null) {
            Text("Bloqueo hasta: ${state.bloqueoHastaUtc}", textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRecover, modifier = Modifier.fillMaxWidth()) {
            Text("Usar código de recuperación")
        }
    }
}

@Composable
private fun MfaRecoverScreen(state: MfaUiState, onSubmit: (String) -> Unit, onLogout: () -> Unit) {
    var code by remember { mutableStateOf("") }
    MfaShell(
        "Recuperación administrativa",
        "Ingresa un código de respaldo. Tras usarlo deberás reconfigurar el autenticador.",
        onLogout
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Código de recuperación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(code) },
            enabled = !state.busy && code.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.busy) "Validando…" else "Recuperar acceso") }
    }
}

private fun encodeQr(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
    return bmp
}
