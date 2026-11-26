package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.ui.theme.MyCarMuted
import kotlinx.coroutines.CancellationException

@Composable
fun CorregirSoatLoader(
    claveCuenta: String,
    idVehiculo: Long,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    var soat by remember { mutableStateOf<SoatDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sesion by remember { mutableStateOf(false) }
    LaunchedEffect(idVehiculo) {
        try {
            soat = repository.consultar(idVehiculo).actual
            if (soat == null) error = "No hay SOAT vigente para corregir."
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            error = "No se pudo cargar el SOAT."
        }
    }
    when {
        soat != null -> CorregirSoatRoute(
            claveCuenta, idVehiculo, soat!!, repository, onVolver, onIniciarSesion, onExito, modifier
        )
        error != null -> Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onVolver) { Text("Volver") }
            Text(error!!, color = MyCarMuted)
            if (sesion) TextButton(onClick = onIniciarSesion) { Text("Iniciar sesión") }
        }
        else -> Column(Modifier.fillMaxSize().padding(24.dp)) {
            CircularProgressIndicator()
        }
    }
}