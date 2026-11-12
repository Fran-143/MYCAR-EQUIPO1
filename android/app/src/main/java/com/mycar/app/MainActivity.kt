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
import com.mycar.app.features.sesion.domain.PoliticaRedLocal
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mycar.app.features.inicio.ui.MyCarNavigation
import com.mycar.app.ui.theme.MyCarTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.autenticacion.ui.AuthGate
import com.mycar.app.features.sesion.viewmodel.SesionVehiculosViewModel

class MainActivity : ComponentActivity() {
    private var solicitudesVehiculos by mutableIntStateOf(0)
    private var solicitudesNotificaciones by mutableIntStateOf(0)
    private var pushIdVehiculo by mutableStateOf<Long?>(null)
    private var pushIdAlerta by mutableStateOf<Long?>(null)
    private var redLocalDisponible by mutableStateOf(true)
    private val permisoRedLocal = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        redLocalDisponible = it
    }
    private val permisoNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* RF48 actualiza permiso vía Preferencias; aquí solo concedemos el OS. */ }
    private fun necesitaPermisoRedLocal() = PoliticaRedLocal.requierePermiso(
        BuildConfig.DEBUG, Build.VERSION.SDK_INT, BuildConfig.API_BASE_URL)
    private fun comprobarRedLocal() {
        redLocalDisponible = !necesitaPermisoRedLocal() ||
            checkSelfPermission(PoliticaRedLocal.PERMISO) == PackageManager.PERMISSION_GRANTED
    }
    private fun aplicarIntentPush(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("abrir_vehiculos", false)) solicitudesVehiculos++
        if (intent.getBooleanExtra("abrir_desde_push", false)) {
            solicitudesNotificaciones++
            pushIdVehiculo = intent.getLongExtra("id_vehiculo", -1L).takeIf { it > 0 }
            pushIdAlerta = intent.getLongExtra("id_alerta", -1L).takeIf { it > 0 }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        aplicarIntentPush(intent)
        comprobarRedLocal()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permisoNotificaciones.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MyCarTheme(darkTheme = false, dynamicColor = false) {
            if (redLocalDisponible) {
                val sesion: SesionVehiculosViewModel = viewModel()
                AuthGate {
                    MyCarNavigation(
                        solicitudesVehiculos,
                        sesion,
                        solicitudesNotificaciones = solicitudesNotificaciones,
                        pushIdVehiculo = pushIdVehiculo,
                        pushIdAlerta = pushIdAlerta
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
