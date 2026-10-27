package com.mycar.app

import android.os.Bundle
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
import com.mycar.app.features.mantenimiento.ui.EntradaRegistroMantenimiento
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
                var mostrarRegistro by rememberSaveable { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (mostrarRegistro) {
                        EntradaRegistroMantenimiento(
                            onCerrar = { mostrarRegistro = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRegistrarMantenimiento = { mostrarRegistro = true }
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