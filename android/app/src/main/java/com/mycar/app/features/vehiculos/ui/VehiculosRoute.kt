package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycar.app.features.vehiculos.data.CupoRegistroDto
import com.mycar.app.features.vehiculos.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Punto de integración real para Elsa: el host aporta clientes autenticados, almacenamiento
 * y una clave de servidor/cuenta/ciclo de sesión. No crea identidad ni usa catálogos de ejemplo.
 */
@Composable
fun VehiculosRoute(
    claveCuenta: String,
    registroRepository: RegistroVehiculoRepository,
    listadoRepository: ListadoVehiculosRepository,
    pendientes: SolicitudRegistroPendienteStore,
    onIrInicio: () -> Unit,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    onVerDetalle: ((Long) -> Unit)? = null,
    onCompartir: ((Long, String, String) -> Unit)? = null,
    estadoEnSistema: Boolean = true,
    onFormularioAbierto: (Boolean) -> Unit = {},
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    fotosRepository: com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null
) {
    key(claveCuenta) {
        var pantalla by rememberSaveable(claveCuenta) { mutableStateOf("lista") }
        LaunchedEffect(pantalla) { onFormularioAbierto(pantalla == "registro") }
        var comprobando by remember(claveCuenta) { mutableStateOf(false) }
        var cupoBloqueo by remember(claveCuenta) { mutableStateOf<CupoRegistroDto?>(null) }
        var mensajeError by remember(claveCuenta) { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val azul = Color(0xFF1565C0)
        val teal = Color(0xFF00897B)
        BackHandler(enabled = pantalla == "lista") { onIrInicio() }
        val agregar: () -> Unit = {
            if (!comprobando) {
                comprobando = true
                scope.launch {
                    try {
                        // Recuperar pendientes tiene prioridad sobre cupo: el registro podría estar confirmado.
                        val hayPendiente = withContext(Dispatchers.IO) { pendientes.cargar() != null }
                        val cupo = withContext(Dispatchers.IO) { registroRepository.cupo() }
                        if (hayPendiente || cupo.puedeRegistrar) {
                            pantalla = "registro"
                        } else {
                            cupoBloqueo = cupo
                        }
                    } catch (cancelada: CancellationException) { throw cancelada }
                    catch (error: Exception) {
                        if (error is FalloRegistroVehiculo && error.estadoHttp == 401) onIniciarSesion()
                        else mensajeError = "No se pudo comprobar el registro. Inténtalo nuevamente; tu solicitud pendiente se conserva."
                    } finally { comprobando = false }
                }
            }
        }
        if (pantalla == "registro") {
            RegistroVehiculoRoute(claveCuenta, registroRepository, pendientes,
                onVolver = { pantalla = "lista" }, onIniciarSesion = onIniciarSesion,
                onVerVehiculos = { pantalla = "lista" }, onIrInicio = onIrInicio, modifier = modifier,
                estadoEnSistema = estadoEnSistema, fotosRepository = fotosRepository)
        } else {
            Column(modifier.fillMaxSize()) {
                if (comprobando) LinearProgressIndicator(Modifier.fillMaxWidth())
                ListadoVehiculosRoute(claveCuenta, listadoRepository, onVolver = onIrInicio,
                    onAgregar = agregar, onIniciarSesion = onIniciarSesion,
                    onVerDetalle = onVerDetalle, onCompartir = onCompartir,
                    modifier = Modifier.weight(1f), estadoEnSistema = estadoEnSistema,
                    onNotificaciones = onNotificaciones, onPerfil = onPerfil,
                    fotosRepository = fotosRepository, avatarBytes = avatarBytes, iniciales = iniciales)
            }
        }
        cupoBloqueo?.let { cupo ->
            ModalCupoVehiculos(
                cupo = cupo,
                azul = azul,
                teal = teal,
                onDismiss = { cupoBloqueo = null }
            )
        }
        mensajeError?.let { texto ->
            AlertDialog(
                onDismissRequest = { mensajeError = null },
                title = { Text("Registro de vehículo") },
                text = { Text(texto) },
                confirmButton = {
                    TextButton(onClick = { mensajeError = null }) { Text("Entendido") }
                }
            )
        }
    }
}

@Composable
private fun ModalCupoVehiculos(
    cupo: CupoRegistroDto,
    azul: Color,
    teal: Color,
    onDismiss: () -> Unit
) {
    val plan = cupo.plan.ifBlank { "tu plan" }
    val esPremium = plan.equals("PREMIUM", ignoreCase = true)
    val acento = if (esPremium) teal else azul
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                Modifier
                    .size(64.dp)
                    .background(acento.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (esPremium) Icons.Outlined.WorkspacePremium else Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = acento,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                "Cupo de vehículos completo",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (esPremium) {
                        "Tu plan Premium ya alcanzó el máximo de vehículos registrados. No puedes agregar otro hasta liberar cupo."
                    } else {
                        "Tu plan Free permite un solo vehículo registrado (aunque esté inactivo). Para registrar más, activa Premium."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Surface(
                    color = acento.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Plan $plan",
                            color = acento,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            "${cupo.registrados} / ${cupo.limite} registrados",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Límite de tu suscripción actual",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = acento),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Entendido") }
        }
    )
}
