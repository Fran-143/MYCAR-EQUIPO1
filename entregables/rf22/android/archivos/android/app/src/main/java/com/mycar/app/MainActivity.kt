package com.mycar.app

import android.os.Bundle
import android.content.Intent
import com.mycar.app.features.alertas.programacion.ui.ProgramarAlertaActivity
import com.mycar.app.features.alertas.programacion.data.ArchivoProgramacionPendiente
import com.mycar.app.features.autenticacion.data.AuthGraph
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
import com.mycar.app.features.autenticacion.ui.AuthGate
import com.mycar.app.ui.theme.MyCarTheme
import com.mycar.app.features.inicio.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthGraph.repository(applicationContext).registerLocalCleanup("rf22") {
            ArchivoProgramacionPendiente.limpiarLocales(applicationContext)
        }
        enableEdgeToEdge()
        setContent {
            MyCarTheme {
                var mostrarHistorial by rememberSaveable { mutableStateOf(false) }
                AuthGate(onLocalSessionCleared = { mostrarHistorial = false }) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (mostrarHistorial) {
                            FlujoHistorialMantenimiento(
                                onVolverInicio = { mostrarHistorial = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                onVerHistorial = { mostrarHistorial = true },
                                onProgramarAlerta = { startActivity(Intent(this, ProgramarAlertaActivity::class.java)) }
                            )
                        }
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
