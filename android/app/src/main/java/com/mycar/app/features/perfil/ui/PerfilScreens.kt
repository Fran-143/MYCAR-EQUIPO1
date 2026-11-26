package com.mycar.app.features.perfil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import com.mycar.app.features.fotos.ui.FlujoSelectorFotoPersonalizada
import com.mycar.app.features.fotos.ui.ImagenBytesFoto
import com.mycar.app.features.perfil.domain.PerfilValidator
import com.mycar.app.features.perfil.viewmodel.EstadoPerfil
import com.mycar.app.features.perfil.viewmodel.PantallaPerfil
import com.mycar.app.features.perfil.viewmodel.PerfilViewModel

@Composable
fun PerfilRoute(
    vm: PerfilViewModel,
    preferenciasNotificacion: com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository,
    recordatoriosKm: com.mycar.app.features.recordatorioskm.domain.RecordatoriosKmRepository,
    fotosRepository: com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository? = null,
    licenciaRepository: com.mycar.app.features.licencia.domain.LicenciaRepository,
    soporteRepository: com.mycar.app.features.soporte.domain.SoporteRepository,
    planesRepository: com.mycar.app.features.planes.domain.PlanesRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }
    // LICENCIA / notificaciones / RF43 / PLANES manejan su propio atrás.
    BackHandler(
        enabled = estado.pantalla != PantallaPerfil.LICENCIA &&
            estado.pantalla != PantallaPerfil.SOPORTE &&
            estado.pantalla != PantallaPerfil.PLANES &&
            estado.pantalla != PantallaPerfil.NOTIFICACIONES_EXTERNAS &&
            estado.pantalla != PantallaPerfil.RECORDATORIOS_KM
    ) {
        when (estado.pantalla) {
            PantallaPerfil.HUB -> onVolver()
            else -> vm.volverAtras()
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (estado.pantalla) {
            PantallaPerfil.HUB -> PerfilHubScreen(estado, vm, onVolver)
            PantallaPerfil.DATOS -> DatosPersonalesScreen(estado, vm)
            PantallaPerfil.EDITAR -> EditarPerfilScreen(estado, vm)
            PantallaPerfil.CAMBIAR_CONTRASENA -> CambiarContrasenaScreen(estado, vm)
            PantallaPerfil.CONTRASENA_OK -> ContrasenaActualizadaScreen(vm::volverAlPerfil)
            PantallaPerfil.NOTIFICACIONES_EXTERNAS ->
                com.mycar.app.features.preferenciasnotif.ui.PreferenciasNotificacionRoute(
                    repository = preferenciasNotificacion,
                    onVolver = vm::volverAtras,
                    onSesionInvalida = onSesionInvalida,
                    modifier = Modifier.fillMaxSize()
                )
            PantallaPerfil.RECORDATORIOS_KM ->
                com.mycar.app.features.recordatorioskm.ui.RecordatoriosKmRoute(
                    repository = recordatoriosKm,
                    fotos = fotosRepository,
                    onVolver = vm::volverAtras,
                    onSesionInvalida = onSesionInvalida,
                    modifier = Modifier.fillMaxSize()
                )
            PantallaPerfil.LICENCIA ->
                com.mycar.app.features.licencia.ui.LicenciaRoute(
                    repository = licenciaRepository,
                    onVolver = vm::volverAtras,
                    onSesionInvalida = onSesionInvalida,
                    modifier = Modifier.fillMaxSize()
                )
            PantallaPerfil.SOPORTE ->
                com.mycar.app.features.soporte.ui.SoporteRoute(
                    repository = soporteRepository,
                    onVolver = vm::volverAtras,
                    onSesionInvalida = onSesionInvalida,
                    modifier = Modifier.fillMaxSize()
                )
            PantallaPerfil.PLANES ->
                com.mycar.app.features.planes.ui.PlanesRoute(
                    repository = planesRepository,
                    onVolver = vm::volverAtras,
                    onSesionInvalida = onSesionInvalida,
                    modifier = Modifier.fillMaxSize()
                )
            PantallaPerfil.ELIMINAR_CUENTA ->
                com.mycar.app.features.cuenta.ui.EliminarCuentaRoute(
                    onCancel = vm::volverAtras,
                    onCompletadoLogout = onCerrarSesion,
                    modifier = Modifier.fillMaxSize()
                )
        }
        if (estado.confirmarCierre) {
            CerrarSesionDialog(
                onCancelar = vm::cancelarCierre,
                onConfirmar = {
                    vm.cancelarCierre()
                    onCerrarSesion()
                }
            )
        }
    }
}

@Composable
private fun PerfilHubScreen(estado: EstadoPerfil, vm: PerfilViewModel, onVolver: () -> Unit) {
    var menuFoto by remember { mutableStateOf(false) }
    var selectorActivo by remember { mutableStateOf(false) }
    var confirmarEliminar by remember { mutableStateOf(false) }
    val iconos = perfilIconos()
    Column(Modifier.fillMaxSize()) {
        com.mycar.app.ui.MycarCabeceraApp(
            conStatusBars = true,
            avatarBytes = estado.fotoBytes,
            iniciales = estado.perfil?.iniciales
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVolver) { Text("Volver", color = MaterialTheme.colorScheme.primary) }
                Text(
                    "Perfil",
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.width(64.dp))
            }
            Spacer(Modifier.height(12.dp))
            when {
                estado.cargando && estado.perfil == null -> {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    Seccion("SESIÓN") {
                        FilaAccion(
                            "Cerrar sesión",
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            iconTint = iconos.logout,
                            onClick = vm::pedirCerrarSesion
                        )
                    }
                }
                estado.perfil == null -> {
                    estado.mensaje?.let { Text(it, color = perfilDanger()) }
                    TextButton(onClick = vm::cargar) { Text("Reintentar") }
                    Spacer(Modifier.height(16.dp))
                    Seccion("SESIÓN") {
                        FilaAccion(
                            "Cerrar sesión",
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            iconTint = iconos.logout,
                            onClick = vm::pedirCerrarSesion
                        )
                    }
                }
                else -> {
                    val perfil = estado.perfil!!
                    estado.mensajeFoto?.let { texto ->
                        Surface(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            color = if (estado.errorFoto) MaterialTheme.colorScheme.errorContainer else perfilSuccessContainer(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                texto,
                                Modifier.padding(14.dp),
                                color = if (estado.errorFoto) MaterialTheme.colorScheme.onErrorContainer else perfilSuccessContent()
                            )
                        }
                    }
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ImagenBytesFoto(
                                        bytes = estado.fotoBytes,
                                        contentDescription = "Foto de perfil",
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            perfil.iniciales,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 28.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Box {
                                    Surface(
                                        onClick = { if (!estado.ocupadoFoto) menuFoto = true },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 2.dp,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.PhotoCamera,
                                                contentDescription = "Foto de perfil",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = menuFoto,
                                        onDismissRequest = { menuFoto = false },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ) {
                                        if (perfil.tieneFoto) {
                                            DropdownMenuItem(
                                                text = { Text("Cambiar foto") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                                                },
                                                onClick = {
                                                    menuFoto = false
                                                    selectorActivo = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Eliminar foto") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Delete, null, tint = perfilDanger())
                                                },
                                                onClick = {
                                                    menuFoto = false
                                                    confirmarEliminar = true
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Agregar foto") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.AddAPhoto, null, tint = MaterialTheme.colorScheme.secondary)
                                                },
                                                onClick = {
                                                    menuFoto = false
                                                    selectorActivo = true
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Cancelar") },
                                            onClick = { menuFoto = false }
                                        )
                                    }
                                }
                            }
                            if (estado.ocupadoFoto) {
                                Spacer(Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                perfil.nombreCompleto,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            )
                            Text(perfil.correo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = vm::abrirDatos,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Datos personales") }
                        }
                    }
                    FlujoSelectorFotoPersonalizada(
                        activo = selectorActivo,
                        circular = true,
                        onDismiss = { selectorActivo = false },
                        onConfirmada = { foto ->
                            vm.registrarOActualizarFoto(foto.bytes, foto.contentType, foto.nombre)
                        },
                        onError = { /* mensaje vía estado al fallar subida; cancelación no marca éxito */ }
                    )
                    if (confirmarEliminar) {
                        AlertDialog(
                            onDismissRequest = { confirmarEliminar = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconContentColor = perfilDanger(),
                            icon = {
                                Icon(Icons.Filled.Delete, null, tint = perfilDanger(), modifier = Modifier.size(28.dp))
                            },
                            title = { Text("Eliminar foto", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                            text = { Text("¿Eliminar tu foto de perfil? Se mostrarán tus iniciales.") },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { confirmarEliminar = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Cancelar") }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        confirmarEliminar = false
                                        vm.eliminarFoto()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Eliminar") }
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Seccion("CUENTA") {
                        FilaAccion(
                            "Cambiar contraseña",
                            icon = Icons.Outlined.Lock,
                            iconTint = iconos.lock,
                            onClick = vm::abrirCambiarContrasena
                        )
                        FilaAccion(
                            "Licencia de conducir",
                            icon = Icons.Outlined.Badge,
                            iconTint = iconos.license,
                            onClick = vm::abrirLicencia
                        )
                        FilaAccion(
                            "Soporte",
                            icon = Icons.Outlined.SupportAgent,
                            iconTint = iconos.support,
                            onClick = vm::abrirSoporte
                        )
                        FilaAccion(
                            "Planes y suscripción",
                            icon = Icons.Outlined.WorkspacePremium,
                            iconTint = iconos.planes,
                            onClick = vm::abrirPlanes
                        )
                    }
                    Seccion("VEHÍCULO") {
                        FilaAccion(
                            "Recordatorios de kilometraje",
                            icon = Icons.Outlined.DirectionsCar,
                            iconTint = iconos.km,
                            onClick = vm::abrirRecordatoriosKm
                        )
                        FilaAccion(
                            "Notificaciones externas",
                            icon = Icons.Outlined.Notifications,
                            iconTint = iconos.notif,
                            onClick = vm::abrirNotificacionesExternas
                        )
                    }
                    Seccion("SESIÓN") {
                        FilaAccion(
                            "Cerrar sesión",
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            iconTint = iconos.logout,
                            onClick = vm::pedirCerrarSesion
                        )
                    }
                    Seccion("ZONA DE PELIGRO") {
                        FilaAccion(
                            "Eliminar cuenta",
                            icon = Icons.Outlined.DeleteForever,
                            iconTint = iconos.danger,
                            onClick = vm::abrirEliminarCuenta,
                            peligro = true
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DatosPersonalesScreen(estado: EstadoPerfil, vm: PerfilViewModel) {
    val perfil = estado.perfil ?: return
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Datos personales", onBack = vm::volverAtras)
        if (estado.exitoDatos) {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = perfilSuccessContainer(),
                shape = RoundedCornerShape(12.dp)) {
                Text("Datos actualizados correctamente.", Modifier.padding(14.dp), color = perfilSuccessContent())
            }
        }
        CampoSoloLectura("Nombre completo", perfil.nombreCompleto)
        CampoSoloLectura("Correo electrónico", perfil.correo)
        Text("El correo no puede modificarse", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        CampoSoloLectura("Fecha de nacimiento", PerfilValidator.isoToDisplay(perfil.fechaNacimientoIso))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::abrirEditar,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("Editar") }
    }
}

@Composable
private fun EditarPerfilScreen(estado: EstadoPerfil, vm: PerfilViewModel) {
    val form = estado.formulario
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Editar Perfil", onBack = vm::cancelarEdicion)
        estado.mensaje?.let { Text(it, color = perfilDanger(), modifier = Modifier.padding(bottom = 8.dp)) }
        CampoEditable("Nombre", form.nombre, estado.errores["nombre"], !estado.guardando) {
            vm.editarCampo { f -> f.copy(nombre = RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_NOMBRE)) }
        }
        CampoEditable("Apellido", form.apellido, estado.errores["apellido"], !estado.guardando) {
            vm.editarCampo { f -> f.copy(apellido = RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_APELLIDO)) }
        }
        CampoEditable("Correo electrónico", form.correo, null, enabled = false) {}
        Text("El correo no puede modificarse", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        CampoEditable("Fecha de nacimiento", form.fechaNacimiento, estado.errores["fechaNacimiento"],
            !estado.guardando, placeholder = "dd/mm/aaaa", keyboard = KeyboardType.Number) {
            vm.editarCampo { f -> f.copy(fechaNacimiento = RegistrationValidator.formatBirthDateInput(it)) }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = vm::cancelarEdicion,
                enabled = !estado.guardando,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Cancelar") }
            Button(
                onClick = vm::guardar,
                enabled = !estado.guardando,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            ) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Guardar cambios")
            }
        }
    }
}

@Composable
private fun CambiarContrasenaScreen(estado: EstadoPerfil, vm: PerfilViewModel) {
    var verActual by remember { mutableStateOf(false) }
    var verNueva by remember { mutableStateOf(false) }
    var verConfirm by remember { mutableStateOf(false) }
    val liberada = estado.contrasenaActualVerificada
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Cambiar contraseña", onBack = vm::cancelarCambioContrasena)
        Text(
            if (!liberada) "Primero confirma tu contraseña actual."
            else "Contraseña actual verificada. Elige una nueva distinta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)
        )
        estado.mensaje?.let { Text(it, color = perfilDanger(), modifier = Modifier.padding(bottom = 8.dp)) }
        CampoPassword("Contraseña actual", estado.contrasenaActual, estado.errores["contrasenaActual"],
            verActual, !estado.guardando, { verActual = !verActual }) { vm.editarContrasena(actual = it) }
        if (!liberada) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::verificarContrasenaActual,
                enabled = !estado.guardando && estado.contrasenaActual.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            ) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Continuar")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = vm::cancelarCambioContrasena, enabled = !estado.guardando,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Cancelar") }
            return
        }
        Spacer(Modifier.height(12.dp))
        CampoPassword("Nueva contraseña", estado.nuevaContrasena, estado.errores["nuevaContrasena"],
            verNueva, !estado.guardando, { verNueva = !verNueva }) { vm.editarContrasena(nueva = it) }
        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Debe ser distinta a la actual.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            RegistrationValidator.passwordRequirements(estado.nuevaContrasena).forEach { (label, met) ->
                Text((if (met) "✓ " else "• ") + label, color = if (met) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        CampoPassword("Confirmar nueva contraseña", estado.confirmacionContrasena, estado.errores["confirmacionContrasena"],
            verConfirm, !estado.guardando, { verConfirm = !verConfirm }) { vm.editarContrasena(confirmacion = it) }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::cancelarCambioContrasena, enabled = !estado.guardando,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Cancelar") }
            Button(onClick = vm::confirmarCambioContrasena, enabled = !estado.guardando,
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Cambiar contraseña")
            }
        }
    }
}

@Composable
private fun ContrasenaActualizadaScreen(onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(perfilSuccessContainer()), contentAlignment = Alignment.Center) {
            Text("✓", color = perfilSuccessContent(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("Contraseña actualizada", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("Tu contraseña ha sido cambiada correctamente. Los próximos accesos deben usar la nueva contraseña.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onVolver, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )) {
            Text("Volver al perfil")
        }
    }
}

@Composable
private fun CerrarSesionDialog(onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = { Text("¿Estás seguro de que deseas cerrar sesión? Tus datos quedarán guardados.") },
        dismissButton = {
            OutlinedButton(onClick = onCancelar, shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
        },
        confirmButton = {
            Button(onClick = onConfirmar, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
                shape = RoundedCornerShape(12.dp)) { Text("Cerrar sesión") }
        }
    )
}

@Composable
private fun TopBar(titulo: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp) }
        Text(titulo, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun Seccion(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp, start = 4.dp))
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun FilaAccion(
    texto: String,
    onClick: () -> Unit,
    peligro: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            texto,
            Modifier.weight(1f),
            color = if (peligro) perfilDanger() else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = if (peligro) perfilDanger() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FilaDeshabilitada(texto: String, peligro: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), color = if (peligro) perfilDanger() else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Próximamente", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun CampoSoloLectura(label: String, value: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    Surface(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(value, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CampoEditable(
    label: String, value: String, error: String?, enabled: Boolean,
    placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit
) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(
        value = value, onValueChange = onValue, enabled = enabled, singleLine = true,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = perfilFieldColors()
    )
}

@Composable
private fun CampoPassword(
    label: String, value: String, error: String?, visible: Boolean, enabled: Boolean,
    onToggle: () -> Unit, onValue: (String) -> Unit
) {
    Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(
        value = value, onValueChange = onValue, enabled = enabled, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = onToggle, enabled = enabled) { Text(if (visible) "Ocultar" else "Mostrar") } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = perfilFieldColors()
    )
}
