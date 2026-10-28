package com.mycar.app.features.inicio.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.inicio.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onRegistrarMantenimiento: (() -> Unit)? = null,
    onVerHistorial: (() -> Unit)? = null,
    onAbrirVehiculos: (() -> Unit)? = null,
    onAbrirPruebas: (() -> Unit)? = null,
    homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory
    )
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Bienvenida a MyCar",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tu vehículo, organizado en un solo lugar.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        onAbrirVehiculos?.let { abrir ->
            Button(onClick = abrir) { Text("Mis vehículos · iniciar sesión") }
            Spacer(modifier = Modifier.height(12.dp))
        }

        onVerHistorial?.let { abrir ->
            Button(onClick = abrir) { Text("Historial de mantenimientos") }
            Spacer(modifier = Modifier.height(12.dp))
        }

        onRegistrarMantenimiento?.let { abrir ->
            Button(onClick = abrir) { Text("Registrar mantenimiento") }
            Spacer(modifier = Modifier.height(12.dp))
        }

        onAbrirPruebas?.let { abrir ->
            Button(onClick = abrir) { Text("Pruebas visuales · sin guardar datos") }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = homeViewModel::checkConnection,
            enabled = !uiState.isLoading
        ) {
            Text(
                text = if (uiState.isLoading) {
                    "Comprobando..."
                } else {
                    "Comprobar conexión"
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}