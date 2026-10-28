package com.mycar.app.features.autenticacion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mycar.app.R
import com.mycar.app.features.autenticacion.domain.LocalPrivacyDocument
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.domain.RegistrationForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recurso incluido y conocido; nunca interpreta file://, content:// ni rutas recibidas del cliente. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalPrivacyPolicyScreen(document: LocalPrivacyDocument, onBack: () -> Unit, onLoaded: () -> Unit) {
    val resources = LocalContext.current.resources
    val text by produceState<String?>(initialValue = null, document) {
        value = withContext(Dispatchers.IO) {
            try {
                when (document) {
                    LocalPrivacyDocument.MYCAR_ACADEMIC_V1 ->
                        resources.openRawResource(R.raw.mycar_privacy_1_0).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } catch (_: Exception) { "" }
        }
    }
    LaunchedEffect(text) { if (!text.isNullOrEmpty()) onLoaded() }
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Política de Privacidad") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_auth_back), "Volver al registro") }
        })
    }, bottomBar = {
        Surface {
            Button(onClick = onBack,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text("Volver al registro")
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("privacy.document")) {
            Text("Uso académico · versión interna ${document.version}",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text("Pendiente de revisión legal antes de cualquier publicación pública.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            when (val content = text) {
                null -> CircularProgressIndicator()
                "" -> Text("No se pudo cargar el documento local. No se ha aceptado la política.")
                else -> Text(content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Navegación RF01 en memoria: volver del documento conserva el formulario y no marca la casilla. */
@Composable
internal fun RegistrationContent(
    state: RegistrationState, policy: PrivacyPolicyConfiguration,
    onEdit: ((RegistrationForm) -> RegistrationForm) -> Unit,
    onSubmit: () -> Unit, onBack: () -> Unit, onLogin: () -> Unit,
    onPolicy: () -> Unit, onPolicyBack: () -> Unit, onPolicyLoaded: () -> Unit
) {
    when {
        state.success -> RegistrationSuccessScreen(onLogin)
        state.policyVisible && policy.localDocument != null && policy.available ->
            LocalPrivacyPolicyScreen(policy.localDocument, onPolicyBack, onPolicyLoaded)
        else -> RegistrationScreen(state, policy, onEdit, onSubmit, onBack, onPolicy)
    }
}
