package com.mycar.app.features.autenticacion.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.R
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.domain.RegistrationForm
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegistrationScreen(
    state: RegistrationState, policy: PrivacyPolicyConfiguration,
    onEdit: ((RegistrationForm) -> RegistrationForm) -> Unit,
    onSubmit: () -> Unit, onBack: () -> Unit, onPolicy: () -> Unit
) {
    val form = state.form
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val legalReady = policy.available && !state.privacyStale
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val protegidaAntes = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!protegidaAntes) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { if (!state.busy) onBack() }
    if (pickDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis =
            RegistrationValidator.birthDate(form.fechaNacimiento)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(enabled = dateState.selectedDateMillis != null, onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onEdit { it.copy(fechaNacimiento = date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
                    }
                    pickDate = false
                }) { Text("Aceptar") }
            }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } }
        ) { DatePicker(state = dateState, title = { Text("Fecha de nacimiento", Modifier.padding(24.dp)) }) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Crear cuenta") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !state.busy) {
                Icon(painterResource(R.drawable.ic_auth_back), "Volver")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(4.dp))
            RegistrationField("Nombre", form.nombre, state.errors["nombre"], !state.busy,
                onValue = { value -> onEdit { it.copy(nombre = value) } })
            RegistrationField("Apellido", form.apellido, state.errors["apellido"], !state.busy,
                onValue = { value -> onEdit { it.copy(apellido = value) } })
            RegistrationField("Correo electrónico", form.correo, state.errors["correo"], !state.busy,
                keyboardType = KeyboardType.Email,
                onValue = { value -> onEdit { it.copy(correo = value) } })
            RegistrationField("Fecha de nacimiento", form.fechaNacimiento, state.errors["fechaNacimiento"], !state.busy,
                placeholder = "dd/mm/aaaa", keyboardType = KeyboardType.Text,
                trailing = { TextButton(onClick = { keyboard?.hide(); pickDate = true }, enabled = !state.busy) { Text("Calendario") } },
                onValue = { value -> onEdit { it.copy(fechaNacimiento = value) } })
            RegistrationField("Contraseña", form.contrasena, state.errors["contrasena"], !state.busy,
                keyboardType = KeyboardType.Password, passwordVisible = showPassword,
                trailing = { TextButton(onClick = { showPassword = !showPassword }, enabled = !state.busy) {
                    Text(if (showPassword) "Ocultar" else "Mostrar")
                } }, onValue = { value -> onEdit { it.copy(contrasena = value) } })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RegistrationValidator.passwordRequirements(form.contrasena).forEach { (label, met) ->
                    Text((if (met) "✓ " else "• ") + label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RegistrationField("Confirmar contraseña", form.confirmacionContrasena,
                state.errors["confirmacionContrasena"], !state.busy,
                keyboardType = KeyboardType.Password, passwordVisible = showConfirmation,
                trailing = { TextButton(onClick = { showConfirmation = !showConfirmation }, enabled = !state.busy) {
                    Text(if (showConfirmation) "Ocultar" else "Mostrar")
                } }, onValue = { value -> onEdit { it.copy(confirmacionContrasena = value) } })
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Política de Privacidad", fontWeight = FontWeight.SemiBold)
                    if (legalReady) {
                        Text(if (policy.localDocument != null)
                            "Documento local para pruebas académicas. Pendiente de revisión legal antes de publicación pública."
                            else "Consulta el documento aprobado antes de aceptar.")
                        Text("Versión: ${policy.version}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(if (state.privacyStale) "La configuración legal debe actualizarse para continuar."
                            else "Pendiente: falta configurar el documento y la versión aprobados. El registro está bloqueado.")
                    }
                    TextButton(onClick = onPolicy, enabled = legalReady && !state.busy) {
                        Text("Consultar Política de Privacidad")
                    }
                    state.errors["versionPrivacidad"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.privacidadAceptada,
                    modifier = Modifier.semantics { contentDescription = "Acepto la Política de Privacidad vigente. Obligatorio." },
                    onCheckedChange = { accepted -> onEdit { it.copy(privacidadAceptada = accepted) } },
                    enabled = legalReady && !state.busy &&
                        (policy.localDocument == null || state.policyConsulted))
                Text("He leído y acepto la Política de Privacidad vigente. Obligatorio.",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            state.errors["privacidadAceptada"]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = { keyboard?.hide(); onSubmit() }, enabled = legalReady && !state.busy,
                modifier = Modifier.testTag("registration.submit").fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (state.busy) "Creando cuenta…" else "Crear cuenta")
            }
            TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Volver")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RegistrationField(
    label: String, value: String, error: String?, enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text, placeholder: String? = null,
    passwordVisible: Boolean = true, trailing: (@Composable () -> Unit)? = null,
    onValue: (String) -> Unit
) {
    val isPassword = keyboardType == KeyboardType.Password
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) },
        singleLine = true, enabled = enabled, isError = error != null,
        supportingText = error?.let { { Text(it) } }, trailingIcon = trailing,
        placeholder = placeholder?.let { { Text(it) } },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = !isPassword && keyboardType != KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}

@Composable
internal fun RegistrationSuccessScreen(onLogin: () -> Unit) {
    BackHandler(onBack = onLogin)
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                Text("✓", Modifier.padding(24.dp), style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text("Cuenta creada correctamente", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Ya puedes iniciar sesión con tu correo y contraseña.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Ir a iniciar sesión")
            }
        }
    }
}
