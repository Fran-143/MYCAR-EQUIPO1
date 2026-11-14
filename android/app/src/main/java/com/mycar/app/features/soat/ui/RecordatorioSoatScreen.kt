package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.soat.viewmodel.RecordatorioSoatViewModel
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun RecordatorioSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    idSoat: Long,
    recordatorioInicial: Boolean,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onGuardado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: RecordatorioSoatViewModel = viewModel(
        key = "rf32-$claveCuenta-$idVehiculo-$idSoat",
        factory = remember(repository, idVehiculo, idSoat, recordatorioInicial) {
            RecordatorioSoatViewModel.factory(idVehiculo, idSoat, repository, recordatorioInicial)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.exito) { if (estado.exito) onGuardado() }
    RecordatorioSoatScreen(estado, onVolver, onIniciarSesion, vm::alternar, modifier)
}

@Composable
fun RecordatorioSoatScreen(
    estado: RecordatorioSoatViewModel.Estado,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SoatScaffold("Recordatorio SOAT", onVolver, estado.ocupado, modifier) {
        if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
        Text(
            "Te avisaremos con anticipación (30 días) antes del vencimiento.",
            color = MyCarMuted,
            fontWeight = FontWeight.Medium
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recordatorio activo", fontWeight = FontWeight.SemiBold)
            Switch(checked = estado.activo, onCheckedChange = onCambio, enabled = !estado.ocupado)
        }
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        if (estado.sesion) SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
    }
}