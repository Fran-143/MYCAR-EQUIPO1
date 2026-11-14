package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.soat.viewmodel.ConsultarSoatViewModel
import com.mycar.app.features.soat.viewmodel.FaseConsultaSoat
import com.mycar.app.ui.theme.*

@Composable
fun ConsultarSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onConfigurar: () -> Unit,
    onCorregir: () -> Unit,
    onRenovar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ConsultarSoatViewModel = viewModel(
        key = "rf64-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) { ConsultarSoatViewModel.factory(idVehiculo, repository) }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    val actual = estado.consulta?.actual
    LaunchedEffect(actual?.idSoat, actual?.tieneDocumento) {
        if (actual?.tieneDocumento == true) vm.cargarDocumento(actual)
    }
    ConsultarSoatScreen(
        estado = estado,
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onConfigurar = onConfigurar,
        onCorregir = onCorregir,
        onRenovar = onRenovar,
        modifier = modifier
    )
}

@Composable
fun ConsultarSoatScreen(
    estado: com.mycar.app.features.soat.viewmodel.EstadoConsultaSoat,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onConfigurar: () -> Unit,
    onCorregir: () -> Unit,
    onRenovar: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoatScaffold("Consultar SOAT", onVolver, modifier = modifier) {
        when (estado.fase) {
            FaseConsultaSoat.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
                Text("Cargando…", color = MyCarMuted)
            }
            FaseConsultaSoat.SESION -> {
                Text(estado.mensaje ?: "Sesión requerida.")
                SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
            }
            FaseConsultaSoat.ERROR -> {
                Text(estado.mensaje ?: "Error.", color = Color(0xFFC62828))
                SoatBotonSecundario("Reintentar", onRecargar)
            }
            FaseConsultaSoat.LISTO -> {
                val consulta = estado.consulta
                val actual = consulta?.actual
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (actual == null) {
                        Text("No hay SOAT vigente registrado.", color = MyCarMuted)
                    } else {
                        Text("Vigencia actual", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                        SoatTarjetaDatos(actual)
                        if (actual.tieneDocumento) {
                            Text("Documento", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                            SoatVistaDocumento(estado.docBytes, estado.docEsPdf, estado.docCargando, estado.docError)
                        }
                        if (actual.vigencia == "ACTUAL") {
                            SoatBotonSecundario("Configurar SOAT", onConfigurar)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SoatBotonSecundario("Corregir", onCorregir, modifier = Modifier.weight(1f))
                                SoatBotonPrincipal("Renovar", onRenovar, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    val historial = consulta?.historial.orEmpty().filter { it.vigencia != "ACTUAL" }
                    if (historial.isNotEmpty()) {
                        Text("Historial", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                        historial.forEach { h ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(h.aseguradora, fontWeight = FontWeight.Bold, color = MyCarNavy)
                                    Text("Póliza ${h.numeroPoliza}", color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${SoatFormulario.formatearFecha(h.fechaInicio)} – ${SoatFormulario.formatearFecha(h.fechaVencimiento)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MyCarMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}