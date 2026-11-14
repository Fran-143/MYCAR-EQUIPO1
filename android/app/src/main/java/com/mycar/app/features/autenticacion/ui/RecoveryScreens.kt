@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.autenticacion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    BackHandler { if (!state.busy) onBack() }
    when (state.step) {
        RecoveryStep.EMAIL -> RecoveryEmailScreen(state, onEditEmail, onEmit, onBack)
        RecoveryStep.CODE -> RecoveryCodeScreen(state, onEditCode, onVerify, onEmit, onBack)
        RecoveryStep.PASSWORD -> RecoveryPasswordScreen(state, onEditPassword, onReset, onBack)
        RecoveryStep.SUCCESS -> RecoverySuccessScreen(onLogin)
    }
}

@Composable
private fun RecoveryEmailScreen(
    state: RecoveryState, onEditEmail: (String) -> Unit, onEmit: () -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Recuperar contraseña") }, navigationIcon = {
            TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ingresa el correo de una cuenta registrada y activa. Te enviaremos un código temporal.",
                color = PerfilColors.Mute)
            OutlinedTextField(value = state.correo, onValueChange = onEditEmail, enabled = !state.busy,
                label = { Text("Correo electrónico") }, singleLine = true, isError = state.errors["correo"] != null,
                supportingText = state.errors["correo"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Button(onClick = onEmit, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Enviar código")
            }
        }
    }
}

@Composable
private fun RecoveryCodeScreen(
    state: RecoveryState, onEditCode: (String) -> Unit, onVerify: () -> Unit, onResend: () -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Verificar código") }, navigationIcon = {
            TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Revisa tu correo e ingresa el código de 6 dígitos.", color = PerfilColors.Mute)
            if (!state.codigoDesarrollo.isNullOrBlank()) {
                Text("Código de desarrollo: ${state.codigoDesarrollo}", color = PerfilColors.Teal)
            }
            OutlinedTextField(value = state.codigo, onValueChange = onEditCode, enabled = !state.busy,
                label = { Text("Código") }, singleLine = true, isError = state.errors["codigo"] != null,
                supportingText = state.errors["codigo"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Button(onClick = onVerify, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Verificar")
            }
            val puedeReenviar = state.puedeReenviar
            TextButton(onClick = onResend, enabled = puedeReenviar) {
                Text(
                    if (state.reenvioEnSegundos > 0)
                        "Reenviar código (${state.reenvioEnSegundos}s)"
                    else "Reenviar código"
                )
            }
            if (state.reenvioEnSegundos > 0) {
                Text(
                    "Espera para reenviar y evitar correos repetidos.",
                    color = PerfilColors.Mute,
                    style = MaterialTheme.typography.bodySmall
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
    Scaffold(topBar = {
        TopAppBar(title = { Text("Nueva contraseña") }, navigationIcon = {
            TextButton(onClick = onBack, enabled = !state.busy) { Text("←") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Debe ser distinta a tu contraseña actual.", color = PerfilColors.Mute,
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = state.nueva, onValueChange = { onEditPassword(it, null) }, enabled = !state.busy,
                label = { Text("Nueva contraseña") }, singleLine = true,
                visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showNew = !showNew }) { Text(if (showNew) "Ocultar" else "Mostrar") } },
                isError = state.errors["nuevaContrasena"] != null,
                supportingText = state.errors["nuevaContrasena"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            RegistrationValidator.passwordRequirements(state.nueva).forEach { (label, met) ->
                Text((if (met) "✓ " else "• ") + label, style = MaterialTheme.typography.bodySmall,
                    color = if (met) PerfilColors.Teal else PerfilColors.Mute)
            }
            OutlinedTextField(value = state.confirmacion, onValueChange = { onEditPassword(null, it) }, enabled = !state.busy,
                label = { Text("Confirmar contraseña") }, singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showConfirm = !showConfirm }) { Text(if (showConfirm) "Ocultar" else "Mostrar") } },
                isError = state.errors["confirmacionContrasena"] != null,
                supportingText = state.errors["confirmacionContrasena"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.message?.let { Text(it, color = PerfilColors.Danger) }
            Button(onClick = onReset, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                shape = RoundedCornerShape(14.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar contraseña")
            }
        }
    }
}

@Composable
private fun RecoverySuccessScreen(onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
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
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
            shape = RoundedCornerShape(14.dp)) { Text("Iniciar sesión") }
    }
}
