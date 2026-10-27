package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.R
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.features.vehiculos.viewmodel.RegistroVehiculoViewModel

/** El host aporta repositorio autenticado, almacenamiento por cuenta y navegación reales. */
@Composable
fun RegistroVehiculoRoute(
    claveCuenta: String,
    repository: RegistroVehiculoRepository,
    pendientes: SolicitudRegistroPendienteStore,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onVerVehiculos: () -> Unit,
    onIrInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: RegistroVehiculoViewModel = viewModel(key = "rf08-$claveCuenta",
        factory = remember(claveCuenta, repository, pendientes) { RegistroVehiculoViewModel.factory(repository, pendientes) })
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    BackHandler {
        if (estado.fase == FaseRegistro.CONFIRMADA) vm.finalizar(onIrInicio) else onVolver()
    }
    RegistroVehiculoScreen(estado, vm::cambiar, vm::guardar, vm::cargar, vm::comprobar, vm::reenviar,
        onVolver, onIniciarSesion, { vm.finalizar(onVerVehiculos) }, { vm.finalizar(onIrInicio) }, modifier)
}

@Composable
fun RegistroVehiculoScreen(
    estado: EstadoRegistroVehiculo,
    onCambiar: (String, String) -> Unit,
    onGuardar: () -> Unit,
    onRecargar: () -> Unit,
    onComprobar: () -> Unit,
    onReenviar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onVerVehiculos: () -> Unit,
    onIrInicio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (estado.fase != FaseRegistro.CONFIRMADA)
            TextButton(onClick = onVolver, enabled = !estado.ocupado) { Text("Volver") }
        Text(if (estado.fase == FaseRegistro.CONFIRMADA) "Vehículo registrado" else "Registrar vehículo",
            style = MaterialTheme.typography.headlineMedium)
        if (estado.ocupado || estado.fase == FaseRegistro.CARGANDO) LinearProgressIndicator(Modifier.fillMaxWidth())
        estado.mensaje?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        when (estado.fase) {
            FaseRegistro.CARGANDO -> Text("Cargando información…")
            FaseRegistro.FORMULARIO -> {
                FormularioRegistroContenido(estado, onCambiar)
                Button(onClick = onGuardar, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) {
                    Text(if (estado.ocupado) "Guardando…" else "Guardar vehículo")
                }
                OutlinedButton(onClick = onVolver, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
            FaseRegistro.SESION_REQUERIDA -> {
                Button(onClick = onIniciarSesion, enabled = !estado.ocupado) { Text("Iniciar sesión") }
                OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Volver a comprobar") }
            }
            FaseRegistro.SIN_CUPO -> OutlinedButton(onClick = onVolver) { Text("Volver") }
            FaseRegistro.INCIERTA -> {
                Text("Conservamos tu solicitud para evitar registros duplicados.")
                Button(onClick = onComprobar, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Comprobar registro") }
                if (estado.permiteReenviar) OutlinedButton(onClick = onReenviar, enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth()) { Text("Reintentar envío") }
            }
            FaseRegistro.CONFIRMADA -> {
                estado.resultado?.let {
                    Text("${it.placa} · ${it.kilometrajeActual} km", style = MaterialTheme.typography.titleLarge)
                    Text("Tu vehículo se registró correctamente.")
                }
                Button(onClick = onVerVehiculos, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Ver mis vehículos") }
                OutlinedButton(onClick = onIrInicio, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Ir a inicio") }
            }
            FaseRegistro.ERROR -> OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Reintentar") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FormularioRegistroContenido(estado: EstadoRegistroVehiculo, onCambiar: (String, String) -> Unit) {
    val f = estado.formulario
    val catalogos = estado.catalogos ?: return
    val habilitado = !estado.ocupado
    val tipo = catalogos.tiposMotor.find { it.idTipoMotor.toString() == f.idTipoMotor }
    val combustible = catalogos.combustibles.find { it.idCombustible.toString() == f.idCombustible }
    val aceite = catalogos.tiposAceite.find { it.idTipoAceite.toString() == f.idTipoAceite }
    var adicionales by rememberSaveable { mutableStateOf(false) }
    val erroresOpcionales = estado.errores.keys.any { it in setOf("numeroVin", "numeroSerie", "numeroMotor", "idTipoAceite", "tipoAceiteOtro") }
    Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), "Imagen predeterminada del vehículo",
        Modifier.fillMaxWidth().height(100.dp))
    Text("Datos del vehículo", style = MaterialTheme.typography.titleMedium)
    Text("Los datos de esta sección son obligatorios.", style = MaterialTheme.typography.bodySmall)
    CampoRegistro("Marca", "marca", f.marca, estado, onCambiar)
    CampoRegistro("Modelo", "modelo", f.modelo, estado, onCambiar)
    SelectorRegistro("Uso del vehículo", f.usoVehiculoCodigo, catalogos.usos.map { it.codigo to it.nombre },
        estado.errores["usoVehiculoCodigo"], habilitado) { onCambiar("usoVehiculoCodigo", it) }
    CampoRegistro("Placa (A1B234)", "placa", f.placa, estado, onCambiar, mayusculas = true)
    CampoRegistro("Año de fabricación", "anioFabricacion", f.anioFabricacion, estado, onCambiar, numerico = true)
    CampoRegistro("Color", "color", f.color, estado, onCambiar)
    SelectorRegistro("Tipo de motor", f.idTipoMotor, catalogos.tiposMotor.map { it.idTipoMotor.toString() to it.nombre },
        estado.errores["idTipoMotor"], habilitado) { onCambiar("idTipoMotor", it) }
    if (tipo?.codigo == "OTRO") CampoRegistro("Especifica el motor", "tipoMotorOtro", f.tipoMotorOtro, estado, onCambiar)
    if (tipo?.esElectrico == true) Text("Combustible, aceite y número de motor: no aplican a vehículos eléctricos.")
    else {
        SelectorRegistro("Combustible", f.idCombustible, catalogos.combustibles.map { it.idCombustible.toString() to it.nombre },
            estado.errores["idCombustible"], habilitado) { onCambiar("idCombustible", it) }
        if (combustible?.codigo == "OTRO") CampoRegistro("Especifica el combustible", "combustibleOtro", f.combustibleOtro, estado, onCambiar)
    }
    CampoRegistro("Kilometraje inicial", "kilometrajeInicial", f.kilometrajeInicial, estado, onCambiar, numerico = true)
    OutlinedButton(onClick = { adicionales = !adicionales }, enabled = habilitado, modifier = Modifier.fillMaxWidth()) {
        Text(if (adicionales || erroresOpcionales) "Datos adicionales (opcionales) ▴" else "Datos adicionales (opcionales) ▾")
    }
    if (adicionales || erroresOpcionales) {
        CampoRegistro("VIN", "numeroVin", f.numeroVin, estado, onCambiar, mayusculas = true)
        CampoRegistro("Número de serie", "numeroSerie", f.numeroSerie, estado, onCambiar, mayusculas = true)
        if (tipo?.esElectrico != true) {
            CampoRegistro("Número de motor", "numeroMotor", f.numeroMotor, estado, onCambiar, mayusculas = true)
            SelectorRegistro("Tipo de aceite", f.idTipoAceite,
                listOf("" to "No registrado") + catalogos.tiposAceite.map { it.idTipoAceite.toString() to it.nombre },
                estado.errores["idTipoAceite"], habilitado) { onCambiar("idTipoAceite", it) }
            if (aceite?.codigo == "OTRO") CampoRegistro("Especifica el aceite", "tipoAceiteOtro", f.tipoAceiteOtro, estado, onCambiar)
        }
    }
}

@Composable
private fun CampoRegistro(etiqueta: String, campo: String, valor: String, estado: EstadoRegistroVehiculo,
                          onCambiar: (String, String) -> Unit, numerico: Boolean = false, mayusculas: Boolean = false) {
    val error = estado.errores[campo]
    OutlinedTextField(value = valor, onValueChange = { onCambiar(campo, it) },
        modifier = Modifier.fillMaxWidth(), label = { Text(etiqueta) }, enabled = !estado.ocupado,
        singleLine = true, isError = error != null,
        supportingText = if (error == null) null else ({ Text(error) }),
        keyboardOptions = KeyboardOptions(
            capitalization = if (mayusculas) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
            keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text))
}

@Composable
private fun SelectorRegistro(etiqueta: String, valor: String, opciones: List<Pair<String, String>>,
                             error: String?, habilitado: Boolean, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { abierto = true }, enabled = habilitado, modifier = Modifier.fillMaxWidth()) {
                Text(opciones.find { it.first == valor }?.second ?: "Seleccionar")
            }
            DropdownMenu(expanded = abierto && habilitado, onDismissRequest = { abierto = false }) {
                opciones.forEach { (codigo, nombre) ->
                    DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(codigo); abierto = false })
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
