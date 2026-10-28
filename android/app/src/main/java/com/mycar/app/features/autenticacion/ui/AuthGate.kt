package com.mycar.app.features.autenticacion.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.SessionPhase

/** Envuelve el contenido existente sin modificar sus pantallas ni callbacks. */
@Composable
fun AuthGate(onLocalSessionCleared: () -> Unit = {}, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory(AuthGraph.repository(context)))
    val policy = remember { AuthGraph.privacyPolicy() }
    val registration: RegistrationViewModel = viewModel(
        factory = RegistrationViewModel.Factory(AuthGraph.repository(context), policy))
    val registrationState by registration.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val logout by vm.logout.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    var page by rememberSaveable { mutableStateOf("welcome") }
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.maintainSession() }
    }
    LaunchedEffect(session.phase, session.contextVersion) {
        if (session.phase == SessionPhase.AUTHENTICATED) { page = "welcome"; registration.reset() }
        if (session.phase == SessionPhase.SIGNED_OUT && session.contextVersion > 0) {
            page = "welcome"
            registration.reset()
            vm.onLocalSessionCleared()
            onLocalSessionCleared()
        }
    }
    when (session.phase) {
        SessionPhase.AUTHENTICATED -> Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MyCar", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = vm::requestLogout, enabled = !logout.busy) { Text("Cerrar sesión") }
                }
            }
            Box(Modifier.weight(1f)) { key(session.contextVersion) { content() } }
        }
        SessionPhase.CLEAR_FAILED -> Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.message.orEmpty(), textAlign = TextAlign.Center)
                Button(onClick = vm::requestLogout, enabled = !logout.busy) { Text("Reintentar limpieza local") }
            }
        }
        SessionPhase.CHECKING, SessionPhase.RETRY -> Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (session.phase == SessionPhase.CHECKING) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Validando sesión…")
                } else {
                    Text(session.message.orEmpty(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = vm::retry) { Text("Volver a intentar") }
                    if (session.canRequestLogout) {
                        TextButton(onClick = vm::requestLogout, enabled = !logout.busy) {
                            Text("Cerrar sesión")
                        }
                    }
                }
            }
        }
        SessionPhase.SIGNED_OUT -> when (page) {
            "login" -> LoginScreen(login, onBack = { vm.clearErrors(); page = "welcome" },
                onSubmit = vm::signIn, onEdit = vm::clearErrors)
            "registration" -> RegistrationContent(
                registrationState, policy, registration::edit, registration::submit,
                onBack = { registration.reset(); page = "welcome" },
                onLogin = { registration.reset(); page = "login" },
                onPolicyBack = registration::closeLocalPolicy,
                onPolicyLoaded = registration::localPolicyLoaded,
                onPolicy = {
                    if (policy.localDocument != null) registration.openLocalPolicy()
                    else if (policy.available) try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policy.documentUrl))
                            .addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: Exception) { registration.policyOpenFailed() }
                })
            else -> WelcomeScreen(session.message, onLogin = { page = "login" },
                onRegister = { registration.reset(); page = "registration" })
        }
    }
    if (logout.confirmation) {
        AlertDialog(onDismissRequest = vm::cancelLogout,
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Cerrarás sesión y se eliminarán los borradores y solicitudes pendientes de vehículos y mantenimientos de este dispositivo. Los registros guardados en el servidor se conservan.") },
            confirmButton = { TextButton(onClick = vm::confirmedLogout) { Text("Cerrar sesión") } },
            dismissButton = { TextButton(onClick = vm::cancelLogout) { Text("Cancelar") } })
    }
}

@Composable
private fun WelcomeScreen(message: String?, onLogin: () -> Unit, onRegister: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.primaryContainer) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface) {
                Icon(painterResource(R.drawable.ic_auth_car), contentDescription = "MyCar",
                    modifier = Modifier.padding(22.dp).size(48.dp), tint = colors.primary)
            }
            Spacer(Modifier.height(24.dp))
            Text("MyCar", style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Text("Tu vehículo, organizado en un solo lugar.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = colors.onPrimaryContainer)
            Spacer(Modifier.height(48.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            message?.let {
                Text(it, color = colors.onPrimaryContainer, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Iniciar sesión") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRegister,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Crear cuenta") }
            Spacer(Modifier.height(32.dp))
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(state: LoginState, onBack: () -> Unit,
    onSubmit: (String, String) -> Unit, onEdit: () -> Unit) {
    // Nunca rememberSaveable: la contraseña no entra en Bundle, rutas ni estado persistente.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val protegidaAntes = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!protegidaAntes) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { if (!state.busy) onBack() }
    val submit = { keyboard?.hide(); onSubmit(email, password) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Iniciar sesión") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.busy) {
                Icon(painterResource(R.drawable.ic_auth_back), contentDescription = "Volver")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(20.dp))
            Text("Bienvenido de nuevo", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Ingresa tus datos para continuar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(value = email, onValueChange = { email = it; onEdit() },
                label = { Text("Correo electrónico") }, singleLine = true,
                enabled = !state.busy, isError = state.emailError != null,
                supportingText = state.emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it; onEdit() },
                label = { Text("Contraseña") }, singleLine = true, enabled = !state.busy,
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { { Text(it) } },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }, enabled = !state.busy) {
                        Text(if (visible) "Ocultar" else "Mostrar")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!state.busy) submit() }),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = { submit() }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "Ingresando…" else "Ingresar")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
