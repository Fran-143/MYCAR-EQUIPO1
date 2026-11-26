@file:OptIn(ExperimentalMaterial3Api::class)

package com.mycar.app.features.localtest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.core.FechaMyCar
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Navy = Color(0xFF12285F)
private val Teal = Color(0xFF007E98)
private val Bg = Color(0xFFF0F5FA)
private val Mute = Color(0xFF718096)
private val Danger = Color(0xFFC53030)

@Composable
fun PruebasLocalesRoute(
    onVolver: () -> Unit,
    destinatarioInicial: String? = null,
    finalidadInicial: String? = null,
    modifier: Modifier = Modifier
) {
    if (!LocalTestClient.disponibleEnApk()) {
        Scaffold(modifier = modifier, containerColor = Bg) { padding ->
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text("Simulador no disponible en esta variante.", color = Danger)
                OutlinedButton(onClick = onVolver) { Text("Volver") }
            }
        }
        return
    }
    var seccion by remember { mutableStateOf(if (destinatarioInicial != null || finalidadInicial != null) "bandeja" else "hub") }
    when (seccion) {
        "bandeja" -> BandejaCorreosScreen(
            onVolver = { if (destinatarioInicial != null) onVolver() else seccion = "hub" },
            destinatarioFiltro = destinatarioInicial,
            finalidadDestacada = finalidadInicial,
            modifier = modifier
        )
        "escenarios" -> EscenariosCuentasScreen(
            onVolver = { seccion = "hub" },
            onAbrirBandeja = { seccion = "bandeja" },
            modifier = modifier
        )
        else -> PruebasHubScreen(
            onVolver = onVolver,
            onBandeja = { seccion = "bandeja" },
            onEscenarios = { seccion = "escenarios" },
            modifier = modifier
        )
    }
}

@Composable
private fun PruebasHubScreen(
    onVolver: () -> Unit,
    onBandeja: () -> Unit,
    onEscenarios: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = Bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Science, null, tint = Navy)
                        Spacer(Modifier.width(8.dp))
                        Text("Pruebas locales")
                    }
                },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Herramientas exclusivas de la APK hotfix con backend local. No usar en producción.",
                color = Mute
            )
            Button(
                onClick = onBandeja,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Icon(Icons.Outlined.Email, null)
                Spacer(Modifier.width(8.dp))
                Text("Bandeja de correos simulados")
            }
            OutlinedButton(
                onClick = onEscenarios,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Escenarios de cuentas") }
        }
    }
}

@Composable
private fun BandejaCorreosScreen(
    onVolver: () -> Unit,
    destinatarioFiltro: String?,
    finalidadDestacada: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val api = remember { LocalTestClient.api() }
    var items by remember { mutableStateOf<List<LocalTestCorreoDto>>(emptyList()) }
    var seleccionado by remember { mutableStateOf<LocalTestCorreoDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun cargar() {
        scope.launch {
            loading = true; error = null
            try {
                if (!finalidadDestacada.isNullOrBlank() && !destinatarioFiltro.isNullOrBlank()) {
                    val r = api.ultimoOtp(
                        LocalTestClient.QA_TOKEN,
                        LocalTestFinalidadBody(destinatarioFiltro, finalidadDestacada)
                    )
                    if (r.isSuccessful && r.body() != null) {
                        seleccionado = r.body()
                        items = listOf(r.body()!!)
                    }
                }
                val list = api.correos(LocalTestClient.QA_TOKEN, destinatarioFiltro, 50)
                if (!list.isSuccessful) throw IllegalStateException(errMsg(list.errorBody()?.string(), list.code()))
                items = list.body()?.items.orEmpty()
                if (seleccionado == null && items.isNotEmpty()) {
                    seleccionado = items.firstOrNull {
                        finalidadDestacada == null || it.finalidadOtp == finalidadDestacada
                    } ?: items.first()
                }
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(destinatarioFiltro, finalidadDestacada) { cargar() }

    Scaffold(
        modifier = modifier,
        containerColor = Bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text("Bandeja simulada") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                actions = {
                    TextButton(onClick = { cargar() }, enabled = !loading) { Text("Actualizar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Estado: SIMULADO · solo fixtures @mycar.test", color = Mute)
            error?.let { Text(it, color = Danger) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sel = seleccionado
                if (sel != null) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(sel.asunto.orEmpty(), fontWeight = FontWeight.Bold, color = Navy)
                                Text("Para: ${sel.destinatario}", color = Mute)
                                Text("Tipo: ${sel.tipoMensaje}", color = Mute)
                                Text(FechaMyCar.fechaHora(sel.creadoUtc), color = Mute)
                                Text(sel.cuerpo.orEmpty(), color = Navy)
                                if (!sel.codigoOtp.isNullOrBlank()) {
                                    Text("Código OTP", fontWeight = FontWeight.SemiBold, color = Navy)
                                    Text(
                                        sel.codigoOtp!!,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Teal
                                    )
                                    sel.expiraOtpUtc?.let {
                                        Text("Vigencia hasta: ${FechaMyCar.fechaHora(it)}", color = Mute)
                                    }
                                    Button(
                                        onClick = { copiar(context, sel.codigoOtp!!) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Copiar código")
                                    }
                                }
                            }
                        }
                    }
                }
                items(items, key = { it.idCorreo }) { m ->
                    Card(
                        Modifier.fillMaxWidth().clickable { seleccionado = m },
                        colors = CardDefaults.cardColors(
                            containerColor = if (seleccionado?.idCorreo == m.idCorreo) Teal.copy(0.12f) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.tipoMensaje.orEmpty(), fontWeight = FontWeight.SemiBold, color = Navy)
                            Text(m.destinatario.orEmpty(), color = Mute, style = MaterialTheme.typography.bodySmall)
                            Text(FechaMyCar.fechaHora(m.creadoUtc), color = Mute, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EscenariosCuentasScreen(
    onVolver: () -> Unit,
    onAbrirBandeja: () -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { LocalTestClient.api() }
    var msg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(label: String, block: suspend () -> Map<String, Any>) {
        scope.launch {
            busy = true; error = null; msg = null
            try {
                val r = block()
                msg = "$label OK. Password fixtures: ${r["password"] ?: r["passwordCompartida"] ?: "PruebaCuentas35!"}"
            } catch (ex: Exception) {
                error = ex.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Bg,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text("Escenarios de cuentas") },
                navigationIcon = { TextButton(onClick = onVolver) { Text("←") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Solo afecta cuentas @mycar.test / fixtures locales.", color = Mute)
            error?.let { Text(it, color = Danger) }
            msg?.let { Text(it, color = Teal) }
            Button(
                onClick = {
                    run("Restablecer usuarios") {
                        val r = api.restablecerUsuarios(LocalTestClient.QA_TOKEN)
                        if (!r.isSuccessful) throw IllegalStateException(errMsg(r.errorBody()?.string(), r.code()))
                        r.body()!!
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) { Text("Restablecer usuarios ficticios (RF55/56)") }
            OutlinedButton(
                onClick = {
                    run("Preparar RF51") {
                        val r = api.prepararRf51(LocalTestClient.QA_TOKEN)
                        if (!r.isSuccessful) throw IllegalStateException(errMsg(r.errorBody()?.string(), r.code()))
                        r.body()!!
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Preparar rf.pendiente (RF51)") }
            OutlinedButton(
                onClick = {
                    run("Límite 30d") {
                        val r = api.prepararLimite(LocalTestClient.QA_TOKEN)
                        if (!r.isSuccessful) throw IllegalStateException(errMsg(r.errorBody()?.string(), r.code()))
                        r.body()!!
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Preparar límite eliminación 30 días") }
            Button(
                onClick = {
                    run("Cuentas Planes") {
                        val r = api.prepararPlanes(LocalTestClient.QA_TOKEN)
                        if (!r.isSuccessful) throw IllegalStateException(errMsg(r.errorBody()?.string(), r.code()))
                        r.body()!!
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) { Text("Preparar cuentas Planes (FREE/prueba/sandbox)") }
            Text(
                "plan.free / plan.prueba / plan.sandbox / plan.premium @mycar.test · pass PruebaCuentas35!",
                color = Mute,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onAbrirBandeja) { Text("Abrir bandeja de correos") }
            Text(
                "Credenciales guardadas fuera de Git: %LOCALAPPDATA%\\MyCar\\pruebas-cuentas-rf-v39.json",
                color = Mute,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun errMsg(raw: String?, code: Int): String = try {
    JSONObject(raw ?: "{}").optString("mensaje").ifBlank { "Error $code" }
} catch (_: Exception) {
    raw?.take(160) ?: "Error $code"
}

private fun copiar(context: Context, texto: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("otp_mycar", texto))
    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
}
