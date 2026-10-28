package com.mycar.app

import android.os.Bundle
import android.content.Intent
import com.mycar.app.features.sesion.ui.SesionVehiculosActivity
import com.mycar.app.features.mantenimiento.ui.EntradaRegistroMantenimiento
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.mycar.app.features.historial.ui.FlujoHistorialMantenimiento
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.ui.theme.MyCarTheme
import com.mycar.app.features.inicio.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCarTheme {
                var pantalla by rememberSaveable { mutableStateOf("inicio") }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (pantalla) {
                        "registro" -> EntradaRegistroMantenimiento(
                            onCerrar = { pantalla = "inicio" },
                            modifier = Modifier.padding(innerPadding)
                        )
                        "historial" -> FlujoHistorialMantenimiento(
                            onVolverInicio = { pantalla = "inicio" },
                            modifier = Modifier.padding(innerPadding)
                        )
                        else -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRegistrarMantenimiento = { pantalla = "registro" },
                            onVerHistorial = { pantalla = "historial" },
                            onAbrirVehiculos = { startActivity(Intent(this, SesionVehiculosActivity::class.java)) },
                            onAbrirPruebas = if (BuildConfig.DEBUG) ({
                                startActivity(Intent().setClassName(packageName,
                                    "com.mycar.app.debug.RegistroVehiculoDemoActivity"))
                            }) else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyCarTheme {
        Greeting("Android")
    }
}