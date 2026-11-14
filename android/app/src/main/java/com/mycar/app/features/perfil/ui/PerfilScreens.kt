package com.mycar.app.features.perfil.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import com.mycar.app.features.perfil.domain.PerfilValidator
import com.mycar.app.features.perfil.viewmodel.EstadoPerfil
import com.mycar.app.features.perfil.viewmodel.PantallaPerfil
import com.mycar.app.features.perfil.viewmodel.PerfilViewModel

@Composable
fun PerfilRoute(
    vm: PerfilViewModel,
    preferenciasNotificacion: com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estado by vm.estado.collectAsState()
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }
    BackHandler {
        when (estado.pantalla) {
            PantallaPerfil.HUB -> onVolver()
            else -> vm.volverAtras()
        }
    }
    Box(modifier.fillMaxSize().background(PerfilColors.Bg)) {
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVolver) { Text("Volver", color = PerfilColors.Navy) }
            Text("MyCar", Modifier.weight(1f), color = PerfilColors.Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.width(64.dp))
        }
        Spacer(Modifier.height(12.dp))
        if (estado.cargando && estado.perfil == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PerfilColors.Navy)
            }
            Seccion("SESIÓN") {
                FilaAccion("Cerrar sesión", onClick = vm::pedirCerrarSesion)
            }
            return
        }
        if (estado.perfil == null) {
            estado.mensaje?.let { Text(it, color = PerfilColors.Danger) }
            TextButton(onClick = vm::cargar) { Text("Reintentar") }
            Spacer(Modifier.height(16.dp))
            Seccion("SESIÓN") {
                FilaAccion("Cerrar sesión", onClick = vm::pedirCerrarSesion)
            }
            return
        }
        val perfil = estado.perfil
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = PerfilColors.Card, shadowElevation = 1.dp) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(PerfilColors.Teal), contentAlignment = Alignment.Center) {
                    Text(perfil.iniciales, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(perfil.nombreCompleto, color = PerfilColors.Navy, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(perfil.correo, color = PerfilColors.Mute, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = vm::abrirDatos,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfilColors.Navy)
                ) { Text("Datos personales") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Seccion("CUENTA") {
            FilaAccion("Cambiar contraseña", onClick = vm::abrirCambiarContrasena)
            FilaDeshabilitada("Planes y suscripción")
        }
        Seccion("NOTIFICACIONES") {
            FilaAccion("Notificaciones externas", onClick = vm::abrirNotificacionesExternas)
            FilaDeshabilitada("Recordatorios de km")
        }
        Seccion("APARIENCIA") {
            FilaDeshabilitada("Modo claro")
        }
        Seccion("SESIÓN") {
            FilaAccion("Cerrar sesión", onClick = vm::pedirCerrarSesion)
        }
        Seccion("ZONA DE PELIGRO") {
            FilaDeshabilitada("Eliminar cuenta", peligro = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DatosPersonalesScreen(estado: EstadoPerfil, vm: PerfilViewModel) {
    val perfil = estado.perfil ?: return
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Datos personales", onBack = vm::volverAtras)
        if (estado.exitoDatos) {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = PerfilColors.SuccessBg,
                shape = RoundedCornerShape(12.dp)) {
                Text("Datos actualizados correctamente.", Modifier.padding(14.dp), color = PerfilColors.SuccessText)
            }
        }
        CampoSoloLectura("Nombre completo", perfil.nombreCompleto)
        CampoSoloLectura("Correo electrónico", perfil.correo)
        Text("El correo no puede modificarse", color = PerfilColors.Mute, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        CampoSoloLectura("Fecha de nacimiento", PerfilValidator.isoToDisplay(perfil.fechaNacimientoIso))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::abrirEditar,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
        ) { Text("Editar") }
    }
}

@Composable
private fun EditarPerfilScreen(estado: EstadoPerfil, vm: PerfilViewModel) {
    val form = estado.formulario
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Editar Perfil", onBack = vm::cancelarEdicion)
        estado.mensaje?.let { Text(it, color = PerfilColors.Danger, modifier = Modifier.padding(bottom = 8.dp)) }
        CampoEditable("Nombre", form.nombre, estado.errores["nombre"], !estado.guardando) {
            vm.editarCampo { f -> f.copy(nombre = RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_NOMBRE)) }
        }
        CampoEditable("Apellido", form.apellido, estado.errores["apellido"], !estado.guardando) {
            vm.editarCampo { f -> f.copy(apellido = RegistrationValidator.limitCodePoints(it, RegistrationValidator.MAX_APELLIDO)) }
        }
        CampoEditable("Correo electrónico", form.correo, null, enabled = false) {}
        Text("El correo no puede modificarse", color = PerfilColors.Mute, fontSize = 12.sp,
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfilColors.Navy)
            ) { Text("Cancelar") }
            Button(
                onClick = vm::guardar,
                enabled = !estado.guardando,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
            ) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
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
            color = PerfilColors.Mute, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)
        )
        estado.mensaje?.let { Text(it, color = PerfilColors.Danger, modifier = Modifier.padding(bottom = 8.dp)) }
        CampoPassword("Contraseña actual", estado.contrasenaActual, estado.errores["contrasenaActual"],
            verActual, !estado.guardando, { verActual = !verActual }) { vm.editarContrasena(actual = it) }
        if (!liberada) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::verificarContrasenaActual,
                enabled = !estado.guardando && estado.contrasenaActual.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)
            ) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
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
            Text("Debe ser distinta a la actual.", color = PerfilColors.Mute, fontSize = 12.sp)
            RegistrationValidator.passwordRequirements(estado.nuevaContrasena).forEach { (label, met) ->
                Text((if (met) "✓ " else "• ") + label, color = if (met) PerfilColors.Teal else PerfilColors.Mute, fontSize = 12.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)) {
                if (estado.guardando) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Cambiar contraseña")
            }
        }
    }
}

@Composable
private fun ContrasenaActualizadaScreen(onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(PerfilColors.SuccessBg), contentAlignment = Alignment.Center) {
            Text("✓", color = PerfilColors.SuccessText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("Contraseña actualizada", color = PerfilColors.Navy, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("Tu contraseña ha sido cambiada correctamente. Los próximos accesos deben usar la nueva contraseña.",
            color = PerfilColors.Mute, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onVolver, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Navy)) {
            Text("Volver al perfil")
        }
    }
}

@Composable
private fun CerrarSesionDialog(onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Cerrar sesión", color = PerfilColors.Navy, fontWeight = FontWeight.Bold) },
        text = { Text("¿Estás seguro de que deseas cerrar sesión? Tus datos quedarán guardados.") },
        dismissButton = {
            OutlinedButton(onClick = onCancelar, shape = RoundedCornerShape(12.dp)) { Text("Cancelar") }
        },
        confirmButton = {
            Button(onClick = onConfirmar, colors = ButtonDefaults.buttonColors(containerColor = PerfilColors.Danger),
                shape = RoundedCornerShape(12.dp)) { Text("Cerrar sesión") }
        }
    )
}

@Composable
private fun TopBar(titulo: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", color = PerfilColors.Navy, fontSize = 20.sp) }
        Text(titulo, color = PerfilColors.Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun Seccion(titulo: String, content: @Composable ColumnScope.() -> Unit) {
    Text(titulo, color = PerfilColors.Mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp, start = 4.dp))
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = PerfilColors.Card) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun FilaAccion(texto: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), color = PerfilColors.Navy)
        Text("›", color = PerfilColors.Mute)
    }
}

@Composable
private fun FilaDeshabilitada(texto: String, peligro: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), color = if (peligro) PerfilColors.Danger.copy(alpha = 0.55f) else PerfilColors.Mute)
        Text("Próximamente", color = PerfilColors.Mute, fontSize = 11.sp)
    }
}

@Composable
private fun CampoSoloLectura(label: String, value: String) {
    Text(label, color = PerfilColors.Navy, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFE8EEF5)) {
        Text(value, Modifier.padding(16.dp), color = PerfilColors.Navy)
    }
}

@Composable
private fun CampoEditable(
    label: String, value: String, error: String?, enabled: Boolean,
    placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit
) {
    Text(label, color = PerfilColors.Navy, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(
        value = value, onValueChange = onValue, enabled = enabled, singleLine = true,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PerfilColors.Navy,
            unfocusedBorderColor = Color(0xFFC5D0DE),
            disabledTextColor = PerfilColors.Mute,
            disabledBorderColor = Color(0xFFC5D0DE)
        )
    )
}

@Composable
private fun CampoPassword(
    label: String, value: String, error: String?, visible: Boolean, enabled: Boolean,
    onToggle: () -> Unit, onValue: (String) -> Unit
) {
    Text(label, color = PerfilColors.Navy, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(
        value = value, onValueChange = onValue, enabled = enabled, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = onToggle, enabled = enabled) { Text(if (visible) "Ocultar" else "Mostrar") } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PerfilColors.Navy, unfocusedBorderColor = Color(0xFFC5D0DE))
    )
}
