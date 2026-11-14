package com.mycar.app.features.soat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soat.data.SoatDto
import com.mycar.app.features.soat.domain.SoatRepository
import com.mycar.app.features.soat.viewmodel.*

@Composable
fun RegistrarSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: (SoatDto) -> Unit,
    onRecordatorio: (SoatDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lector: suspend (android.net.Uri) -> ByteArray = remember(context) {
        { uri -> leerUriSoat(context, uri) }
    }
    val vm: RegistrarSoatViewModel = viewModel(
        key = "rf30-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo, lector) {
            RegistrarSoatViewModel.factory(idVehiculo, repository, lector)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    SoatFormScreen(
        estado = estado,
        titulo = "Registrar SOAT",
        onVolver = onVolver,
        onActualizar = vm::actualizarCampos,
        onAdjunto = vm::setAdjunto,
        onModoDoc = vm::setModoDocumento,
        onContinuar = vm::mostrarConfirmacion,
        onConfirmar = vm::confirmar,
        onOcultarConfirmacion = vm::ocultarConfirmacion,
        onIniciarSesion = onIniciarSesion,
        onExitoContinuar = { estado.exito?.let(onExito) },
        onConfigurarRecordatorio = { estado.exito?.let(onRecordatorio) },
        textoConfirmar = "Registrar SOAT",
        modifier = modifier
    )
}

@Composable
fun CorregirSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    soat: SoatDto,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lector: suspend (android.net.Uri) -> ByteArray = remember(context) {
        { uri -> leerUriSoat(context, uri) }
    }
    val vm: CorregirSoatViewModel = viewModel(
        key = "rf31c-$claveCuenta-$idVehiculo-${soat.idSoat}",
        factory = remember(repository, idVehiculo, soat, lector) {
            CorregirSoatViewModel.factory(idVehiculo, soat.idSoat, repository, soat, lector)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    SoatFormScreen(
        estado = estado,
        titulo = "Corregir SOAT",
        onVolver = onVolver,
        onActualizar = vm::actualizarCampos,
        onAdjunto = vm::setAdjunto,
        onModoDoc = vm::setModoDocumento,
        onContinuar = vm::mostrarConfirmacion,
        onConfirmar = vm::confirmar,
        onOcultarConfirmacion = vm::ocultarConfirmacion,
        onIniciarSesion = onIniciarSesion,
        onExitoContinuar = onExito,
        onConfigurarRecordatorio = onExito,
        textoConfirmar = "Guardar corrección",
        modifier = modifier
    )
}

@Composable
fun RenovarSoatRoute(
    claveCuenta: String,
    idVehiculo: Long,
    idSoatAnterior: Long,
    repository: SoatRepository,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onExito: (SoatDto) -> Unit,
    onRecordatorio: (SoatDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lector: suspend (android.net.Uri) -> ByteArray = remember(context) {
        { uri -> leerUriSoat(context, uri) }
    }
    val vm: RenovarSoatViewModel = viewModel(
        key = "rf31r-$claveCuenta-$idVehiculo-$idSoatAnterior",
        factory = remember(repository, idVehiculo, idSoatAnterior, lector) {
            RenovarSoatViewModel.factory(idVehiculo, idSoatAnterior, repository, lector)
        }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    SoatFormScreen(
        estado = estado,
        titulo = "Renovar SOAT",
        onVolver = onVolver,
        onActualizar = vm::actualizarCampos,
        onAdjunto = vm::setAdjunto,
        onModoDoc = vm::setModoDocumento,
        onContinuar = vm::mostrarConfirmacion,
        onConfirmar = vm::confirmar,
        onOcultarConfirmacion = vm::ocultarConfirmacion,
        onIniciarSesion = onIniciarSesion,
        onExitoContinuar = { estado.exito?.let(onExito) },
        onConfigurarRecordatorio = { estado.exito?.let(onRecordatorio) },
        textoConfirmar = "Renovar SOAT",
        modifier = modifier
    )
}