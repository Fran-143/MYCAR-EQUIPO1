package com.mycar.app.debug

import android.os.Bundle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.mycar.app.MainActivity
import com.mycar.app.features.sesion.ui.SesionVehiculosActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mycar.app.features.inicio.ui.HomeScreen
import com.mycar.app.features.vehiculos.ui.RegistroVehiculoDemo
import com.mycar.app.features.vehiculos.ui.ListadoVehiculosDemo
import com.mycar.app.ui.theme.MyCarTheme

/** Entrada exclusiva de DEBUG. No crea identidad, sesión ni repositorio de vehículos. */
class RegistroVehiculoDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyCarTheme { NavegacionPrueba() } }
    }
}

@Composable
private fun NavegacionPrueba() {
    val context = LocalContext.current
    var pantalla by rememberSaveable { mutableStateOf("registro") }
    BackHandler(enabled = pantalla != "inicio") { pantalla = if (pantalla == "registro") "autos" else "inicio" }
    when (pantalla) {
        "registro" -> RegistroVehiculoDemo(onVolver = { pantalla = "autos" })
        "autos" -> ListadoVehiculosDemo(onVolver = { pantalla = "inicio" }, onAgregar = { pantalla = "registro" })
        "conexion" -> Scaffold(
            topBar = {
                Surface {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pantalla = "inicio" }) { Text("Volver") }
                        Text("Conexión con el servidor", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        ) { padding -> HomeScreen(modifier = Modifier.padding(padding)) }
        else -> Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("MyCar · Pruebas", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Prueba las pantallas de vehículos o comprueba la conexión con tu servidor.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = { pantalla = "autos" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mis vehículos · prueba")
                }
                Button(onClick = { context.startActivity(Intent(context, SesionVehiculosActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Vehículos · sesión real")
                }
                OutlinedButton(onClick = { context.startActivity(Intent(context, MainActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Inicio y mantenimientos")
                }
                OutlinedButton(onClick = { pantalla = "conexion" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Comprobar conexión")
                }
            }
        }
    }
}
