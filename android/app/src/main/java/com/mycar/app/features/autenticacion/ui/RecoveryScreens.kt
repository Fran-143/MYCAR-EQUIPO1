@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.autenticacion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.core.SoporteMyCar
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import com.mycar.app.features.perfil.ui.PerfilColors

@Composable
fun RecoveryFlow(
    state: RecoveryState,
    onEditEmail: (String) -> Unit,
    onEmit: () -> Unit,
    onEditCode: (String) -> Unit,
    onVerify: () -> Unit,
    onEditPassword: (nueva: String?, confirmacion: String?) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit
) {
    // Desde BLOQUEADO, atrás siempre a Login (no al formulario de código).
    BackHandler(enabled = !state.busy) {
        if (state.step == RecoveryStep.BLOQUEADO) onLogin() else onBack()
    }
    when (state.step) {
        RecoveryStep.EMAIL -> RecoveryEmailScreen(state, onEditEmail, onEmit, onBack)
        RecoveryStep.CODE -> RecoveryCodeScreen(state, onEditCode, onVerify, onEmit, onBack)
        RecoveryStep.PASSWORD -> RecoveryPasswordScreen(state, onEditPassword, onReset, onBack)
        RecoveryStep.SUCCESS -> RecoverySuccessScreen(onLogin)
        RecoveryStep.BLOQUEADO -> RecoveryBloqueoScreen(state, onLogin = onLogin)
    }
}

@Composable
private fun RecoveryEmailScreen(
    state: RecoveryState, onEditEmail: (String) -> Unit, onEmit: () -> Unit, onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        topBar = {
            TopAppBar(title = { Text("Recuperar contraseña") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PerfilColors.Bg))
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Ingresa el correo de una cuenta registrada y activa. Te enviaremos un código temporal.",
                color = PerfilColors.Mute
            )
            OutlinedTextField(
                value = state.correo, onValueChange = onEditEmail, enabled = !state.busy,
                label = { Text("Correo electrónico") }, singleLine = true,
                isError = state.errors["correo"] != null,
                supportingText = state.errors["correo"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Spacer(Modifier.weight(1f, fill = true))
            Button(
                onClick = onEmit, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Solicitar código")
            }
            OutlinedButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun RecoveryBloqueoScreen(state: RecoveryState, onLogin: () -> Unit) {
    val context = LocalContext.current
    val restante = formatDuration(state.segundosBloqueoRestantes)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Recuperación bloqueada") },
                navigationIcon = { TextButton(onClick = onLogin) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PerfilColors.Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Recuperación temporalmente bloqueada",
                fontWeight = FontWeight.Bold,
                color = PerfilColors.Navy,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Detectamos múltiples solicitudes de código. Por seguridad, no puedes pedir otro hasta más adelante.",
                color = PerfilColors.Mute
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Podrás solicitar otro código en $restante.",
                        fontWeight = FontWeight.SemiBold,
                        color = PerfilColors.Navy,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "El tiempo exacto lo controla el servidor; aquí solo se muestra una estimación.",
                        color = PerfilColors.Mute,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "Puedes iniciar sesión con tu contraseña actual. Este bloqueo solo afecta la solicitud de códigos de recuperación.",
                color = PerfilColors.Navy
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Volver a iniciar sesión") }
            OutlinedButton(
                onClick = { SoporteMyCar.abrirCorreo(context) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Contactar soporte") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    // Presentación aproximada RF03 (sin segundos ni ISO).
    val s = totalSeconds.coerceAtLeast(0)
    val h = (s + 1799) / 3600
    return when {
        s < 3600 -> "aproximadamente 1 hora"
        h <= 1 -> "aproximadamente 1 hora"
        h <= 3 -> "aproximadamente 2 horas"
        h <= 6 -> "aproximadamente 4 horas"
        else -> "aproximadamente 24 horas"
    }
}

@Composable
private fun RecoveryCodeScreen(
    state: RecoveryState, onEditCode: (String) -> Unit, onVerify: () -> Unit, onResend: () -> Unit, onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        topBar = {
            TopAppBar(title = { Text("Verificar código") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PerfilColors.Bg))
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Revisa tu correo e ingresa el código de 6 dígitos.", color = PerfilColors.Mute)
            if (!state.codigoDesarrollo.isNullOrBlank()) {
                Text("Código de desarrollo: ${state.codigoDesarrollo}", color = PerfilColors.Teal)
            }
            OutlinedTextField(
                value = state.codigo, onValueChange = onEditCode, enabled = !state.busy,
                label = { Text("Código") }, singleLine = true, isError = state.errors["codigo"] != null,
                supportingText = state.errors["codigo"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Spacer(Modifier.weight(1f, fill = true))
            Button(
                onClick = onVerify, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Verificar")
            }
            val puedeReenviar = state.puedeReenviar
            TextButton(onClick = onResend, enabled = puedeReenviar, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.reenvioEnSegundos > 0)
                        "Reenviar código (${state.reenvioEnSegundos}s)"
                    else "Reenviar código"
                )
            }
        }
    }
}

@Composable
private fun RecoveryPasswordScreen(
    state: RecoveryState,
    onEditPassword: (nueva: String?, confirmacion: String?) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        topBar = {
            TopAppBar(title = { Text("Nueva contraseña") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PerfilColors.Bg))
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Debe ser distinta a tu contraseña actual.", color = PerfilColors.Mute,
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.nueva, onValueChange = { onEditPassword(it, null) }, enabled = !state.busy,
                label = { Text("Nueva contraseña") }, singleLine = true,
                visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showNew = !showNew }) { Text(if (showNew) "Ocultar" else "Mostrar") } },
                isError = state.errors["nuevaContrasena"] != null,
                supportingText = state.errors["nuevaContrasena"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            RegistrationValidator.passwordRequirements(state.nueva).forEach { (label, met) ->
                Text((if (met) "✓ " else "• ") + label, style = MaterialTheme.typography.bodySmall,
                    color = if (met) PerfilColors.Teal else PerfilColors.Mute)
            }
            OutlinedTextField(
                value = state.confirmacion, onValueChange = { onEditPassword(null, it) }, enabled = !state.busy,
                label = { Text("Confirmar contraseña") }, singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showConfirm = !showConfirm }) { Text(if (showConfirm) "Ocultar" else "Mostrar") } },
                isError = state.errors["confirmacionContrasena"] != null,
                supportingText = state.errors["confirmacionContrasena"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Spacer(Modifier.weight(1f, fill = true))
            Button(
                onClick = onReset, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar contraseña")
            }
        }
    }
}

@Composable
private fun RecoverySuccessScreen(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(Modifier.size(88.dp), shape = CircleShape, color = PerfilColors.SuccessBg) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("✓", color = PerfilColors.SuccessText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Contraseña actualizada", fontWeight = FontWeight.Bold, color = PerfilColors.Navy)
        Spacer(Modifier.height(8.dp))
        Text("Ya puedes iniciar sesión con tu nueva contraseña.", textAlign = TextAlign.Center, color = PerfilColors.Mute)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Iniciar sesión") }
    }
}
