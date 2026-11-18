package com.mycar.app.features.tive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.tive.domain.TiveRepository
import com.mycar.app.features.tive.viewmodel.EditarTiveViewModel
import com.mycar.app.features.tive.viewmodel.RegistrarTiveViewModel
import com.mycar.app.features.soat.ui.leerUriSoat

@Composable
fun RegistrarTiveRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: TiveRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: () -> Unit,
    onCompletarVin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lector: suspend (android.net.Uri) -> ByteArray = remember(context) {
        { uri -> leerUriSoat(context, uri) }
    }
    val vm: RegistrarTiveViewModel = viewModel(
        key = "rf52-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo, lector) {
            RegistrarTiveViewModel.factory(idVehiculo, repository, lector)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    TiveFormScreen(
        estado = estado,
        titulo = "Registrar TIVE",
        onVolver = onVolver,
        onActualizar = { t, campo -> vm.actualizarCampos(t, campo) },
        onAdjunto = vm::setAdjunto,
        onModoDoc = {},
        onConfirmar = vm::confirmar,
        onIniciarSesion = onIniciarSesion,
        onExito = onExito,
        onCompletarVin = onCompletarVin,
        onCerrarDialogoVin = vm::cerrarDialogoVin,
        modifier = modifier
    )
}

@Composable
fun EditarTiveRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: TiveRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lector: suspend (android.net.Uri) -> ByteArray = remember(context) {
        { uri -> leerUriSoat(context, uri) }
    }
    val vm: EditarTiveViewModel = viewModel(
        key = "rf54-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo, lector) {
            EditarTiveViewModel.factory(idVehiculo, repository, lector)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    TiveFormScreen(
        estado = estado,
        titulo = "Editar TIVE",
        onVolver = onVolver,
        onActualizar = { t, campo -> vm.actualizarCampos(t, campo) },
        onAdjunto = vm::setAdjunto,
        onModoDoc = vm::setModoDocumento,
        onConfirmar = vm::confirmar,
        onIniciarSesion = onIniciarSesion,
        onExito = onExito,
        onCompletarVin = {},
        onCerrarDialogoVin = {},
        modifier = modifier
    )
}