package com.mycar.app.features.sesion.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.sesion.viewmodel.SesionVehiculosViewModel
import com.mycar.app.features.vehiculos.ui.VehiculosRoute
import com.mycar.app.ui.theme.MyCarTheme

class SesionVehiculosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { MyCarTheme { AccesoVehiculos(onSalir = ::finish) } }
    }
}

@Composable
private fun AccesoVehiculos(onSalir: () -> Unit, vm: SesionVehiculosViewModel = viewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val cuenta = estado.cuenta
    var confirmar by remember { mutableStateOf(false) }
    // No cerrar el Activity autenticado accidentalmente: ofrecer el diálogo que limpia y revoca.
    BackHandler(enabled = cuenta != null) { confirmar = true }
    if (cuenta == null) {
        var correo by remember { mutableStateOf("") }
        var contrasena by remember { mutableStateOf("") }
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick=onSalir) { Text("Volver al inicio") }
                Text("Bienvenida a MyCar", style=MaterialTheme.typography.headlineMedium)
                Text("Inicia sesión para consultar y registrar tus vehículos.")
                OutlinedTextField(correo, { correo=it }, label={ Text("Correo") }, singleLine=true,
                    enabled=!estado.ocupada, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email), modifier=Modifier.fillMaxWidth())
                OutlinedTextField(contrasena, { contrasena=it }, label={ Text("Contraseña") }, singleLine=true,
                    enabled=!estado.ocupada, visualTransformation=PasswordVisualTransformation(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password), modifier=Modifier.fillMaxWidth())
                Button(onClick={ val prueba=contrasena; contrasena=""; vm.entrar(correo,prueba) },
                    enabled=!estado.ocupada && correo.isNotBlank() && contrasena.isNotEmpty(), modifier=Modifier.fillMaxWidth()) {
                    Text(if(estado.ocupada) "Entrando…" else "Iniciar sesión")
                }
                if (estado.ocupada) LinearProgressIndicator(Modifier.fillMaxWidth())
                estado.mensaje?.let { Text(it) }
                TextButton(onClick=vm::reintentarLimpieza) { Text("Reintentar limpieza local") }
                Text("Usa una cuenta existente. El registro de cuentas se integra con la pantalla de Elsa.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    } else key(cuenta.ciclo) {
        var pagina by rememberSaveable { mutableStateOf("inicio") }
        CompositionLocalProvider(LocalViewModelStoreOwner provides cuenta) {
            if (pagina == "vehiculos") {
                Column(Modifier.fillMaxSize()) {
                    Surface { Row(Modifier.fillMaxWidth().statusBarsPadding()) {
                        TextButton(onClick={ confirmar=true }) { Text("Cerrar sesión") }
                    } }
                    VehiculosRoute(claveCuenta=cuenta.ciclo, registroRepository=cuenta.registro,
                        listadoRepository=cuenta.listado, pendientes=cuenta.pendientes,
                        onIrInicio={ pagina="inicio" }, onIniciarSesion={ vm.sesionRequerida(cuenta.ciclo) },
                        estadoEnSistema=false, modifier=Modifier.weight(1f))
                }
            } else Scaffold { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("MyCar", style=MaterialTheme.typography.headlineLarge)
                    Text("Tu sesión está activa.")
                    Button(onClick={ pagina="vehiculos" },modifier=Modifier.fillMaxWidth()) { Text("Mis vehículos") }
                    OutlinedButton(onClick={ confirmar=true },modifier=Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
                    Text("Al volver a entrar podrás recuperar una solicitud de vehículo pendiente de esta cuenta.")
                }
            }
        }
    }
    if(confirmar && cuenta != null) AlertDialog(onDismissRequest={ confirmar=false },
        title={ Text("¿Cerrar sesión?") },
        text={ Text("Se eliminarán los borradores y solicitudes pendientes de este dispositivo. Un vehículo ya guardado permanecerá en tu cuenta; consúltalo antes de volver a registrarlo.") },
        confirmButton={ TextButton(onClick={ confirmar=false; vm.cerrarConfirmado() }) { Text("Cerrar sesión") } },
        dismissButton={ TextButton(onClick={ confirmar=false }) { Text("Cancelar") } })
}
