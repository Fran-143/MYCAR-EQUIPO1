package com.mycar.app

import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mycar.app.features.sesion.domain.PoliticaRedLocal
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mycar.app.features.inicio.ui.MyCarNavigation
import com.mycar.app.ui.theme.MyCarTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.autenticacion.ui.AuthGate
import com.mycar.app.features.sesion.viewmodel.SesionVehiculosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var solicitudesVehiculos by mutableIntStateOf(0)
    private var solicitudesNotificaciones by mutableIntStateOf(0)
    private var pushIdVehiculo by mutableStateOf<Long?>(null)
    private var pushIdAlerta by mutableStateOf<Long?>(null)
    private var pushIdNotificacion by mutableStateOf<Long?>(null)
    private var pushCategoria by mutableStateOf<String?>(null)
    private var redLocalDisponible by mutableStateOf(true)
    private val permisoRedLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        redLocalDisponible = it
    }
    private val permisoNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.mycar.app.features.push.MycarPushChannels.asegurarCanalAlertas(this)
            // Login suele ocurrir antes del diálogo OS: re-registrar token + RF48 ya.
            lifecycleScope.launch(Dispatchers.IO) {
                com.mycar.app.features.push.data.PushTokenSync.registrarSiSesionActiva(
                    applicationContext, forzar = true
                )
            }
        }
    }
    private fun necesitaPermisoRedLocal() = PoliticaRedLocal.requierePermiso(
        BuildConfig.DEBUG, Build.VERSION.SDK_INT, BuildConfig.API_BASE_URL)
    private fun comprobarRedLocal() {
        redLocalDisponible = !necesitaPermisoRedLocal() ||
            checkSelfPermission(PoliticaRedLocal.PERMISO) == PackageManager.PERMISSION_GRANTED
    }
    private fun aplicarIntentPush(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("abrir_vehiculos", false) ||
            intent.getStringExtra("abrir_vehiculos") == "true"
        ) {
            solicitudesVehiculos++
        }
        // FCM en bandeja del sistema entrega extras como String; no siempre trae abrir_desde_push.
        val desdePush = intent.getBooleanExtra("abrir_desde_push", false)
            || intent.getStringExtra("abrir_desde_push") == "true"
            || !intent.getStringExtra("id_notificacion").isNullOrBlank()
            || intent.hasExtra("id_notificacion")
            || !intent.getStringExtra("destino_push").isNullOrBlank()
            || !intent.getStringExtra("destino").isNullOrBlank()
        if (desdePush) {
            solicitudesNotificaciones++
            pushIdVehiculo = intent.extraLong("id_vehiculo")
            pushIdAlerta = intent.extraLong("id_alerta")
            pushIdNotificacion = intent.extraLong("id_notificacion")
            pushCategoria = intent.getStringExtra("categoria_push")
                ?: intent.getStringExtra("categoria")
                ?: intent.getStringExtra("destino_push")?.takeIf {
                    it in listOf("MANTENIMIENTO", "SOAT", "LICENCIA", "KILOMETRAJE")
                }
        }
    }

    private fun Intent.extraLong(key: String): Long? {
        val fromLong = getLongExtra(key, -1L).takeIf { it > 0 }
        if (fromLong != null) return fromLong
        return getStringExtra(key)?.toLongOrNull()?.takeIf { it > 0 }
            ?: getIntExtra(key, -1).takeIf { it > 0 }?.toLong()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // APK general: permitir capturas/grabación (evidencias). Hotfix sidecar conserva FLAG_SECURE.
        if (BuildConfig.APPLICATION_ID.endsWith(".hotfix")) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        aplicarIntentPush(intent)
        comprobarRedLocal()
        com.mycar.app.features.push.MycarPushChannels.asegurarCanalAlertas(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permisoNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // Tema fijo claro MyCar (sin selector claro/oscuro).
            MyCarTheme(darkTheme = false, dynamicColor = false) {
            if (redLocalDisponible) {
                val sesion: SesionVehiculosViewModel = viewModel()
                AuthGate {
                    MyCarNavigation(
                        solicitudesVehiculos,
                        sesion,
                        solicitudesNotificaciones = solicitudesNotificaciones,
                        pushIdVehiculo = pushIdVehiculo,
                        pushIdAlerta = pushIdAlerta,
                        pushIdNotificacion = pushIdNotificacion,
                        pushCategoria = pushCategoria
                    )
                }
            } else PermisoRedLocalScreen(
                onSolicitar = { permisoRedLocal.launch(PoliticaRedLocal.PERMISO) },
                onAjustes = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null))) })
        } }
        if (!redLocalDisponible && savedInstanceState == null)
            permisoRedLocal.launch(PoliticaRedLocal.PERMISO)
    }
    override fun onResume() {
        super.onResume()
        comprobarRedLocal()
        // Segundo dispositivo / permiso concedido en Ajustes: reintentar registro FCM.
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.mycar.app.features.push.data.PushTokenSync.registrarSiSesionActiva(applicationContext)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        aplicarIntentPush(intent)
    }
}

@Composable
private fun PermisoRedLocalScreen(onSolicitar: () -> Unit, onAjustes: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Text("Permite la conexión local", style = MaterialTheme.typography.headlineMedium)
            Text("Esta versión de pruebas necesita conectarse al servidor de MyCar en tu computadora.")
            Button(onClick = onSolicitar, modifier = Modifier.fillMaxWidth()) { Text("Permitir red local") }
            OutlinedButton(onClick = onAjustes, modifier = Modifier.fillMaxWidth()) { Text("Abrir ajustes") }
        }
    }
}
