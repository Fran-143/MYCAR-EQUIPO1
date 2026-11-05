package com.mycar.app.features.autenticacion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.BuildConfig
import com.mycar.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerificationCodeScreen(
    state: RegistrationState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler { onBack() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Verificar correo") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_auth_back), contentDescription = "Volver")
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Ingresa el código de 6 dígitos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Lo usamos para confirmar tu correo antes de crear la cuenta.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (BuildConfig.DEBUG && !state.codigoDesarrollo.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Código de desarrollo: ${state.codigoDesarrollo}",
                        Modifier.padding(16.dp).testTag("registration.devCode"),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = onCodeChange,
                label = { Text("Código") },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("registration.code"),
                shape = RoundedCornerShape(12.dp)
            )
            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { keyboard?.hide(); onConfirm() },
                enabled = !state.busy && state.codeInput.length == 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("registration.confirmCode"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "Confirmando…" else "Confirmar código")
            }
            TextButton(onClick = onResend, enabled = !state.busy) {
                Text("Reenviar código")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
