package com.mycar.app.features.tive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soat.ui.SoatBotonPrincipal
import com.mycar.app.features.soat.ui.SoatBotonSecundario
import com.mycar.app.features.soat.ui.SoatScaffold
import com.mycar.app.features.soat.ui.SoatVistaDocumento
import com.mycar.app.features.tive.domain.TiveRepository
import com.mycar.app.features.tive.viewmodel.ConsultarTiveViewModel
import com.mycar.app.features.tive.viewmodel.FaseConsultaTive
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarTeal

@Composable
fun ConsultarTiveRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: TiveRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onEditar: () -> Unit,
    onRegistrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ConsultarTiveViewModel = viewModel(
        key = "rf53-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) { ConsultarTiveViewModel.factory(idVehiculo, repository) }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.ocultarDocumento() } }
    ConsultarTiveScreen(
        estado = estado,
        onRecargar = vm::cargar,
        onRevelarDocumento = { estado.consulta?.tive?.let { if (it.conDocumento) vm.cargarDocumento(it) } },
        onOcultarDocumento = vm::ocultarDocumento,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onEditar = onEditar,
        onRegistrar = onRegistrar,
        modifier = modifier
    )
}

@Composable
fun ConsultarTiveScreen(
    estado: com.mycar.app.features.tive.viewmodel.EstadoConsultaTive,
    onRecargar: () -> Unit,
    onRevelarDocumento: () -> Unit,
    onOcultarDocumento: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onEditar: () -> Unit,
    onRegistrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoatScaffold("Información de la TIVE", onVolver, modifier = modifier) {
        when (estado.fase) {
            FaseConsultaTive.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
                Text("Cargando…", color = MyCarMuted)
            }
            FaseConsultaTive.SESION -> {
                Text(estado.mensaje ?: "Sesión requerida.")
                SoatBotonPrincipal("Iniciar sesión", onIniciarSesion)
            }
            FaseConsultaTive.ERROR -> {
                Text(estado.mensaje ?: "No se pudo cargar la TIVE.", color = Color(0xFFC62828))
                SoatBotonSecundario("Reintentar", onRecargar)
            }
            FaseConsultaTive.SIN_REGISTRO -> {
                TiveTarjetaVehiculoDesdeConsulta(estado.consulta)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sin TIVE registrada", fontWeight = FontWeight.Bold, color = MyCarNavy)
                        Text(
                            estado.consulta?.mensajeDocumento
                                ?: "Este vehículo aún no tiene título vehicular electrónico.",
                            color = MyCarMuted
                        )
                    }
                }
                SoatBotonPrincipal("Registrar TIVE", onRegistrar)
                SoatBotonSecundario("Volver", onVolver)
            }
            FaseConsultaTive.LISTO -> {
                val tive = estado.consulta?.tive
                if (tive != null) {
                    TiveTarjetaVehiculoDesdeConsulta(estado.consulta, tive)
                    TiveTarjetaDatos(tive)
                    Text(
                        "Los datos mostrados son los registrados en MyCar; no constituyen verificación oficial SUNARP.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (tive.conDocumento) {
                        Text("Documento / evidencia", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                        SoatVistaDocumento(
                            bytes = estado.docBytes,
                            esPdf = estado.docEsPdf,
                            cargando = estado.docCargando,
                            error = estado.docError,
                            revelado = estado.docRevelado,
                            onRevelar = onRevelarDocumento,
                            onOcultar = onOcultarDocumento,
                            tieneDocumento = true
                        )
                    } else {
                        Text(estado.consulta.mensajeDocumento ?: "Sin documento adjunto", color = MyCarMuted)
                    }
                    SoatBotonPrincipal("Actualizar TIVE", onEditar)
                    SoatBotonSecundario("Volver", onVolver)
                }
            }
        }
    }
}
