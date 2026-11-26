@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.cuenta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mycar.app.BuildConfig
import com.mycar.app.core.SoporteMyCar
import com.mycar.app.features.cuenta.data.AsistidaConfirmarBody
import com.mycar.app.features.cuenta.data.CuentaApi
import com.mycar.app.features.cuenta.data.ReactivarInicioBody
import com.mycar.app.features.cuenta.data.ReactivarOtpBody
import com.mycar.app.features.localtest.CodigoPruebaLocalBanner
import com.mycar.app.features.localtest.consultarOtpSimulado
import com.mycar.app.features.perfil.ui.PerfilColors
import com.mycar.app.features.sesion.data.ClientesSesion
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private enum class PasoReactivar { MENU, CREDENCIALES, OTP, OK, ASISTIDA }

@Composable
fun ReactivarCuentaRoute(
    onBack: () -> Unit,
    onIrLogin: () -> Unit,
    onAbrirBandeja: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val api = remember {
        ClientesSesion.publico(BuildConfig.API_BASE_URL).create(CuentaApi::class.java)
    }
    var paso by remember { mutableStateOf(PasoReactivar.MENU) }
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var mostrarClave by remember { mutableStateOf(false) }
    var codigo by remember { mutableStateOf("") }
    var otpDev by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val installation = remember { UUID.randomUUID() }
    val scroll = rememberScrollState()

    fun parseError(raw: String?): String = try {
        val o = JSONObject(raw ?: "{}")
        val campos = o.optJSONObject("erroresPorCampo")
        val campo = campos?.optString("credenciales")?.takeIf { it.isNotBlank() }
            ?: campos?.optString("correo")?.takeIf { it.isNotBlank() }
            ?: campos?.optString("codigo")?.takeIf { it.isNotBlank() }
        campo ?: o.optString("mensaje").ifBlank { "No se pudo completar." }
    } catch (_: Exception) {
        raw?.take(180) ?: "No se pudo completar."
    }

    fun iniciarRecuperacion() {
        scope.launch {
            busy = true; error = null
            try {
                val r = api.iniciarReactivacion(
                    ReactivarInicioBody(correo.trim(), clave, installation)
                )
                if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                otpDev = r.body()?.get("codigoDesarrollo")?.toString()
                paso = PasoReactivar.OTP
                keyboard?.hide()
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                busy = false
            }
        }
    }

    BackHandler {
        if (busy) return@BackHandler
        when (paso) {
            PasoReactivar.MENU, PasoReactivar.OK -> onBack()
            PasoReactivar.CREDENCIALES, PasoReactivar.ASISTIDA -> {
                error = null; paso = PasoReactivar.MENU
            }
            PasoReactivar.OTP -> {
                error = null; paso = PasoReactivar.CREDENCIALES
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        // Solo systemBars: adjustResize ya reduce el área útil; imePadding duplicado deformaba.
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text("Recuperar cuenta") },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            when (paso) {
                                PasoReactivar.MENU, PasoReactivar.OK -> onBack()
                                else -> { error = null; paso = PasoReactivar.MENU }
                            }
                        },
                        enabled = !busy
                    ) { Text("←") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PerfilColors.Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let { Text(it, color = PerfilColors.Danger) }
            when (paso) {
                PasoReactivar.MENU -> {
                    Text(
                        "Recuperar cuenta",
                        fontWeight = FontWeight.Bold,
                        color = PerfilColors.Navy,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Si solicitaste eliminar tu cuenta de MyCar y todavía estás dentro del plazo de recuperación, puedes recuperarla por tu cuenta o solicitar ayuda a Soporte.",
                        color = PerfilColors.Mute
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { error = null; paso = PasoReactivar.CREDENCIALES },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Recuperar con correo y contraseña")
                    }
                    OutlinedButton(
                        onClick = { SoporteMyCar.abrirRecuperacionCuenta(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfilColors.Navy)
                    ) {
                        Icon(Icons.Outlined.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Contactar soporte")
                    }
                    TextButton(
                        onClick = { error = null; paso = PasoReactivar.ASISTIDA },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Confirmar asistencia de Soporte") }
                    if (BuildConfig.LOCAL_TEST_TOOLS && onAbrirBandeja != null) {
                        TextButton(onClick = onAbrirBandeja, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir bandeja de correos simulados")
                        }
                    }
                }
                PasoReactivar.CREDENCIALES -> {
                    Text("Verifica tu identidad", fontWeight = FontWeight.SemiBold, color = PerfilColors.Navy)
                    Text(
                        "Introduce el correo y la contraseña de la cuenta pendiente de eliminación.",
                        color = PerfilColors.Mute
                    )
                    OutlinedTextField(
                        value = correo,
                        onValueChange = { correo = it },
                        label = { Text("Correo") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focus.moveFocus(FocusDirection.Down) }
                        )
                    )
                    OutlinedTextField(
                        value = clave,
                        onValueChange = { clave = it },
                        label = { Text("Contraseña") },
                        visualTransformation = if (mostrarClave) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { mostrarClave = !mostrarClave }) {
                                Icon(
                                    if (mostrarClave) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (mostrarClave) "Ocultar" else "Mostrar"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                if (!busy && correo.isNotBlank() && clave.isNotBlank()) iniciarRecuperacion()
                            }
                        )
                    )
                    Button(
                        onClick = { iniciarRecuperacion() },
                        enabled = !busy && correo.isNotBlank() && clave.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Continuar") }
                    OutlinedButton(
                        onClick = { error = null; paso = PasoReactivar.MENU },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Volver") }
                    if (BuildConfig.LOCAL_TEST_TOOLS && onAbrirBandeja != null) {
                        TextButton(onClick = onAbrirBandeja) { Text("Consultar OTP en bandeja de pruebas") }
                    }
                    Spacer(Modifier.height(160.dp))
                }
                PasoReactivar.OTP -> {
                    Text("Código enviado a tu correo (finalidad REACTIVAR_CUENTA).", color = PerfilColors.Mute)
                    CodigoPruebaLocalBanner(codigo = otpDev)
                    if (BuildConfig.LOCAL_TEST_TOOLS && onAbrirBandeja != null) {
                        TextButton(onClick = {
                            scope.launch {
                                otpDev = consultarOtpSimulado(correo.trim(), "REACTIVAR_CUENTA") ?: otpDev
                            }
                            onAbrirBandeja()
                        }) { Text("Consultar código en bandeja de pruebas") }
                    }
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { if (it.length <= 6) codigo = it.filter { c -> c.isDigit() } },
                        label = { Text("Código") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val v = api.otpReactivacion(ReactivarOtpBody(correo.trim(), codigo))
                                    if (!v.isSuccessful) throw IllegalStateException(parseError(v.errorBody()?.string()))
                                    val c = api.confirmarReactivacion(ReactivarOtpBody(correo.trim(), codigo))
                                    if (!c.isSuccessful) throw IllegalStateException(parseError(c.errorBody()?.string()))
                                    mensaje = c.body()?.get("mensaje")?.toString()
                                    otpDev = null
                                    paso = PasoReactivar.OK
                                    keyboard?.hide()
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && codigo.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Verificar y recuperar") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val r = api.iniciarReactivacion(
                                        ReactivarInicioBody(correo.trim(), clave, installation)
                                    )
                                    if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                                    otpDev = r.body()?.get("codigoDesarrollo")?.toString()
                                    codigo = ""
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy
                    ) { Text("Reenviar código") }
                    Spacer(Modifier.height(160.dp))
                }
                PasoReactivar.ASISTIDA -> {
                    Text("Confirmar asistencia de Soporte", fontWeight = FontWeight.SemiBold, color = PerfilColors.Navy)
                    Text(
                        "Si Soporte inició una recuperación asistida, introduce el correo de tu cuenta y el código recibido.",
                        color = PerfilColors.Mute
                    )
                    OutlinedTextField(
                        value = correo, onValueChange = { correo = it },
                        label = { Text("Correo") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    LaunchedEffect(correo) {
                        if (BuildConfig.LOCAL_TEST_TOOLS && correo.contains("@")) {
                            otpDev = consultarOtpSimulado(correo.trim(), "VERIF_REACTIV_ADMIN")
                        }
                    }
                    CodigoPruebaLocalBanner(codigo = otpDev)
                    if (BuildConfig.LOCAL_TEST_TOOLS && onAbrirBandeja != null) {
                        TextButton(onClick = {
                            scope.launch {
                                otpDev = consultarOtpSimulado(correo.trim(), "VERIF_REACTIV_ADMIN") ?: otpDev
                            }
                            onAbrirBandeja()
                        }) { Text("Consultar código en bandeja de pruebas") }
                    }
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { if (it.length <= 6) codigo = it.filter { c -> c.isDigit() } },
                        label = { Text("Código de verificación") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val r = api.confirmarTitularAsistida(
                                        AsistidaConfirmarBody(correo.trim(), codigo)
                                    )
                                    if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                                    mensaje = r.body()?.get("mensaje")?.toString()
                                        ?: "Identidad confirmada. Soporte podrá completar la reactivación."
                                    otpDev = null
                                    paso = PasoReactivar.OK
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && correo.isNotBlank() && codigo.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Confirmar identidad") }
                    Spacer(Modifier.height(160.dp))
                }
                PasoReactivar.OK -> {
                    Text("Listo", fontWeight = FontWeight.Bold, color = PerfilColors.Navy)
                    Text(mensaje ?: "Ya puedes continuar.", color = PerfilColors.Mute)
                    Button(
                        onClick = onIrLogin,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Ir a iniciar sesión") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
