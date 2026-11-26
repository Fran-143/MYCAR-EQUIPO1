@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.cuenta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.BuildConfig
import com.mycar.app.core.FechaMyCar
import com.mycar.app.core.SoporteMyCar
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.cuenta.data.ConfirmarEliminacionBody
import com.mycar.app.features.cuenta.data.CuentaApi
import com.mycar.app.features.cuenta.data.CuentaOtpBody
import com.mycar.app.features.localtest.CodigoPruebaLocalBanner
import com.mycar.app.features.perfil.ui.PerfilColors
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private enum class PasoEliminar {
    INFO, MOTIVO, CONFIRMAR, OTP, FINAL, REGISTRADA, LIMITE_30D
}

@Composable
fun EliminarCuentaRoute(
    onCancel: () -> Unit,
    onCompletadoLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val api = remember {
        val authRepo = AuthGraph.repository(context)
        val cliente = ClienteVehiculosAutenticado(
            BuildConfig.API_BASE_URL,
            autorizacion = {
                runBlocking {
                    val ctx = authRepo.authenticatedContext()
                        ?: error("Sesión no disponible")
                    authRepo.authorization(ctx)
                }
            }
        )
        cliente.retrofit.create(CuentaApi::class.java)
    }
    var paso by remember { mutableStateOf(PasoEliminar.INFO) }
    var motivoCodigo by remember { mutableStateOf<String?>(null) }
    var motivoTexto by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var otpDev by remember { mutableStateOf<String?>(null) }
    var venceUtc by remember { mutableStateOf<String?>(null) }
    var proximaSolicitudUtc by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val scroll = rememberScrollState()

    BackHandler { if (!busy) onCancel() }

    fun parseError(raw: String?): String {
        return try {
            JSONObject(raw ?: "{}").optString("mensaje").ifBlank { "No se pudo completar." }
        } catch (_: Exception) {
            raw?.take(180) ?: "No se pudo completar."
        }
    }

    fun manejarInicioFallido(raw: String?): Boolean {
        return try {
            val o = JSONObject(raw ?: "{}")
            if (o.optString("codigo") == "LIMITE_ELIMINACION_30D") {
                val campos = o.optJSONObject("erroresPorCampo")
                proximaSolicitudUtc = campos?.optString("proximaSolicitudUtc")?.takeIf { it.isNotBlank() }
                error = null
                paso = PasoEliminar.LIMITE_30D
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PerfilColors.Bg,
        // Solo systemBars: adjustResize ya reduce el área; safeDrawing+imePadding+navBars deformaban el OTP.
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text("Eliminar cuenta", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("←") }
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
                PasoEliminar.INFO -> {
                    Text("Se solicitará la eliminación de MyCar.", color = PerfilColors.Navy)
                    Text("Tendrás 30 días calendario para recuperar la cuenta.", color = PerfilColors.Mute)
                    Text("Durante ese plazo no podrás usar normalmente MyCar.", color = PerfilColors.Mute)
                    Text("Después del plazo se ejecutará la eliminación definitiva según la política aprobada.", color = PerfilColors.Mute)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        Button(
                            onClick = { paso = PasoEliminar.MOTIVO },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Danger)
                        ) { Text("Continuar") }
                    }
                }
                PasoEliminar.MOTIVO -> {
                    Text("Motivo opcional", fontWeight = FontWeight.SemiBold, color = PerfilColors.Navy)
                    listOf(
                        "YA_NO_NECESITO" to "Ya no necesito MyCar",
                        "PROBLEMAS_USO" to "Problemas al utilizar la aplicación",
                        "PRIVACIDAD" to "Privacidad",
                        "OTRO" to "Otro motivo"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = motivoCodigo == code,
                            onClick = { motivoCodigo = code },
                            label = { Text(label) }
                        )
                    }
                    OutlinedTextField(
                        value = motivoTexto,
                        onValueChange = { motivoTexto = it },
                        label = { Text("Observación opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { motivoCodigo = null; motivoTexto = ""; paso = PasoEliminar.CONFIRMAR }) {
                            Text("Omitir")
                        }
                        Button(
                            onClick = { paso = PasoEliminar.CONFIRMAR },
                            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                        ) { Text("Continuar") }
                    }
                }
                PasoEliminar.CONFIRMAR -> {
                    Text("Confirmar solicitud", fontWeight = FontWeight.Bold, color = PerfilColors.Navy)
                    Text(
                        "Perderás el acceso normal a MyCar durante 30 días. Podrás recuperar la cuenta dentro del plazo con verificación.",
                        color = PerfilColors.Mute
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true; error = null
                                    try {
                                        val r = api.iniciarEliminacion()
                                        if (!r.isSuccessful) {
                                            val raw = r.errorBody()?.string()
                                            if (manejarInicioFallido(raw)) return@launch
                                            throw IllegalStateException(parseError(raw))
                                        }
                                        otpDev = r.body()?.get("codigoDesarrollo")?.toString()
                                        paso = PasoEliminar.OTP
                                    } catch (ex: Exception) {
                                        error = ex.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Danger)
                        ) { Text("Continuar con verificación") }
                    }
                }
                PasoEliminar.OTP -> {
                    Text(
                        "Verificación por correo",
                        fontWeight = FontWeight.Bold,
                        color = PerfilColors.Navy,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Ingresa el código de 6 dígitos enviado a tu correo.",
                        color = PerfilColors.Mute
                    )
                    CodigoPruebaLocalBanner(codigo = otpDev)
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { if (it.length <= 6) codigo = it.filter { c -> c.isDigit() } },
                        label = { Text("Código") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboard?.hide() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    keyboard?.hide()
                                    val r = api.otpEliminacion(CuentaOtpBody(codigo))
                                    if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                                    otpDev = null
                                    paso = PasoEliminar.FINAL
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && codigo.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Verificar código") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                try {
                                    val r = api.iniciarEliminacion()
                                    if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                                    otpDev = r.body()?.get("codigoDesarrollo")?.toString()
                                } catch (ex: Exception) {
                                    error = ex.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reenviar código") }
                    OutlinedButton(
                        onClick = { keyboard?.hide(); onCancel() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Volver") }
                    Spacer(Modifier.height(120.dp))
                }
                PasoEliminar.FINAL -> {
                    Text(
                        "Tu cuenta se pondrá en proceso de eliminación durante 30 días calendario.",
                        color = PerfilColors.Navy,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !busy) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true; error = null
                                    try {
                                        val r = api.confirmarEliminacion(
                                            ConfirmarEliminacionBody(
                                                codigo = codigo,
                                                motivoCodigo = motivoCodigo,
                                                motivoTexto = motivoTexto.ifBlank { null }
                                            )
                                        )
                                        if (!r.isSuccessful) throw IllegalStateException(parseError(r.errorBody()?.string()))
                                        venceUtc = r.body()?.get("venceUtc")?.toString()
                                        paso = PasoEliminar.REGISTRADA
                                    } catch (ex: Exception) {
                                        error = ex.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Danger)
                        ) { Text("Solicitar eliminación") }
                    }
                }
                PasoEliminar.REGISTRADA -> {
                    Text("Solicitud registrada correctamente.", fontWeight = FontWeight.Bold, color = PerfilColors.Navy)
                    Text("Fecha límite: ${FechaMyCar.fecha(venceUtc)}", color = PerfilColors.Mute)
                    Text("Puedes recuperar la cuenta desde el inicio de sesión dentro del plazo.", color = PerfilColors.Mute)
                    Button(
                        onClick = onCompletadoLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Entendido") }
                }
                PasoEliminar.LIMITE_30D -> {
                    Text(
                        "Ya solicitaste eliminar tu cuenta recientemente. Por seguridad, solo puedes realizar una solicitud cada 30 días.",
                        color = PerfilColors.Navy,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Podrás volver a solicitarlo a partir del ${FechaMyCar.fecha(proximaSolicitudUtc)}.",
                        color = PerfilColors.Mute
                    )
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
                    ) { Text("Entendido") }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PerfilColors.Navy)
            }
            if (paso != PasoEliminar.REGISTRADA) {
                TextButton(
                    onClick = { SoporteMyCar.abrirCorreo(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Contactar soporte") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
