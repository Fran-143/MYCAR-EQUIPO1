package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    estadoEnSistema: Boolean = true
) {
    key(claveCuenta) {
        var pantalla by rememberSaveable(claveCuenta) { mutableStateOf("lista") }
        var comprobando by remember(claveCuenta) { mutableStateOf(false) }
        var mensaje by remember(claveCuenta) { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        BackHandler(enabled = pantalla == "lista") { onIrInicio() }
        val agregar: () -> Unit = {
            if (!comprobando) {
                comprobando = true
                scope.launch {
                    try {
                        // Recuperar pendientes tiene prioridad sobre cupo: el registro podría estar confirmado.
                        val hayPendiente = withContext(Dispatchers.IO) { pendientes.cargar() != null }
                        val permitido = hayPendiente || registroRepository.cupo().puedeRegistrar
                        if (permitido) pantalla = "registro"
                        else mensaje = "Tu plan Free permite un vehículo registrado, aunque esté inactivo."
                    } catch (cancelada: CancellationException) { throw cancelada }
                    catch (error: Exception) {
                        if (error is FalloRegistroVehiculo && error.estadoHttp == 401) onIniciarSesion()
                        else mensaje = "No se pudo comprobar el registro. Inténtalo nuevamente; tu solicitud pendiente se conserva."
                    } finally { comprobando = false }
                }
            }
        }
        if (pantalla == "registro") {
            RegistroVehiculoRoute(claveCuenta, registroRepository, pendientes,
                onVolver = { pantalla = "lista" }, onIniciarSesion = onIniciarSesion,
                onVerVehiculos = { pantalla = "lista" }, onIrInicio = onIrInicio, modifier = modifier, estadoEnSistema = estadoEnSistema)
        } else {
            Column(modifier.fillMaxSize()) {
                if (comprobando) LinearProgressIndicator(Modifier.fillMaxWidth())
                ListadoVehiculosRoute(claveCuenta, listadoRepository, onVolver = onIrInicio,
                    onAgregar = agregar, onIniciarSesion = onIniciarSesion,
                    onVerDetalle = onVerDetalle, modifier = Modifier.weight(1f), estadoEnSistema = estadoEnSistema)
            }
        }
        mensaje?.let { texto ->
            AlertDialog(onDismissRequest = { mensaje = null }, title = { Text("Registro de vehículo") },
                text = { Text(texto) }, confirmButton = { TextButton(onClick = { mensaje = null }) { Text("Entendido") } })
        }
    }
}
