package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
        if (!estado.ocupado) {
            if (estado.fase == FaseRegistro.CONFIRMADA) vm.finalizar(onIrInicio) else onVolver()
        }
    }
    RegistroVehiculoScreen(estado, vm::cambiar, vm::guardar, vm::cargar, vm::comprobar, vm::reenviar,
        onVolver, onIniciarSesion, { vm.finalizar(onVerVehiculos) }, { vm.finalizar(onIrInicio) }, modifier)
}

/** Colores de esta pantalla; no modifica el tema compartido con otros módulos. */
@Composable
private fun TemaRegistroVehiculo(content: @Composable () -> Unit) {
    val colores = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFA8C4FF), onPrimary = Color(0xFF002B70),
        primaryContainer = Color(0xFF173B70), onPrimaryContainer = Color(0xFFDAE6FF),
        background = Color(0xFF10151F), surface = Color(0xFF171E2B),
        onSurface = Color(0xFFE7EDF8), onSurfaceVariant = Color(0xFFBBC7DA),
        outline = Color(0xFF64748B), outlineVariant = Color(0xFF37465D)
    ) else lightColorScheme(
        primary = Color(0xFF154EBD), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDF5FF), onPrimaryContainer = Color(0xFF103C91),
        background = Color(0xFFF7FAFF), surface = Color.White,
        onSurface = Color(0xFF17243D), onSurfaceVariant = Color(0xFF53647F),
        outline = Color(0xFF8193AF), outlineVariant = Color(0xFFDAE4F1)
    )
    MaterialTheme(colorScheme = colores, typography = MaterialTheme.typography, content = content)
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
    TemaRegistroVehiculo {
        Scaffold(
            modifier = modifier.fillMaxSize().imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (estado.fase != FaseRegistro.CONFIRMADA) {
                            IconButton(onClick = onVolver, enabled = !estado.ocupado) {
                                Icon(painterResource(R.drawable.ic_rf08_volver), contentDescription = "Volver")
                            }
                        } else Spacer(Modifier.width(20.dp))
                        Text(if (estado.fase == FaseRegistro.CONFIRMADA) "Vehículo registrado" else "Agregar vehículo",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 16.dp).semantics { heading() })
                    }
                }
            },
            bottomBar = {
                if (estado.fase == FaseRegistro.FORMULARIO) {
                    Surface(shadowElevation = 6.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onVolver, enabled = !estado.ocupado,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Cancelar") }
                            Button(onClick = onGuardar, enabled = !estado.ocupado,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.4f).heightIn(min = 50.dp)) {
                                Text(if (estado.ocupado) "Guardando…" else "Guardar vehículo")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (estado.ocupado || estado.fase == FaseRegistro.CARGANDO)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                estado.mensaje?.let {
                    Surface(shape = RoundedCornerShape(12.dp), color = if (estado.errores.isEmpty())
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (estado.errores.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer) {
                        Text(it, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                when (estado.fase) {
                    FaseRegistro.CARGANDO -> Text("Cargando información…")
                    FaseRegistro.FORMULARIO -> FormularioRegistroContenido(estado, onCambiar)
                    FaseRegistro.SESION_REQUERIDA -> {
                        Text("Inicia sesión para registrar tu vehículo.")
                        Button(onClick = onIniciarSesion, enabled = !estado.ocupado) { Text("Iniciar sesión") }
                        OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Volver a comprobar") }
                    }
                    FaseRegistro.SIN_CUPO -> OutlinedButton(onClick = onVolver, enabled = !estado.ocupado) { Text("Volver") }
                    FaseRegistro.INCIERTA -> {
                        Text("Conservamos tu solicitud para evitar registros duplicados.")
                        Button(onClick = onComprobar, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Comprobar registro") }
                        if (estado.permiteReenviar) OutlinedButton(onClick = onReenviar, enabled = !estado.ocupado,
                            modifier = Modifier.fillMaxWidth()) { Text("Reintentar envío") }
                    }
                    FaseRegistro.CONFIRMADA -> {
                        Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterHorizontally).height(96.dp))
                        estado.resultado?.let {
                            Text("${it.placa} · ${it.kilometrajeActual} km", style = MaterialTheme.typography.titleLarge)
                            Text("Tu vehículo se registró correctamente.")
                        }
                        Button(onClick = onVerVehiculos, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Ver mis vehículos") }
                        OutlinedButton(onClick = onIrInicio, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Ir a inicio") }
                    }
                    FaseRegistro.ERROR -> OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Reintentar") }
                }
            }
        }
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
    val camposGenerales = setOf("marca", "modelo", "usoVehiculoCodigo", "placa", "anioFabricacion", "color")
    val camposIdentificacion = setOf("numeroVin", "numeroSerie", "numeroMotor")
    val camposMotor = setOf("idTipoMotor", "tipoMotorOtro", "idCombustible", "combustibleOtro", "idTipoAceite", "tipoAceiteOtro", "kilometrajeInicial")
    var generales by rememberSaveable { mutableStateOf(true) }
    var identificacion by rememberSaveable { mutableStateOf(false) }
    var motor by rememberSaveable { mutableStateOf(true) }
    // Los errores de Guardar o del servidor nunca quedan ocultos en una sección cerrada.
    LaunchedEffect(estado.errores) {
        if (estado.errores.keys.any { it in camposGenerales }) generales = true
        if (estado.errores.keys.any { it in camposIdentificacion }) identificacion = true
        if (estado.errores.keys.any { it in camposMotor }) motor = true
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08), contentDescription = null,
                modifier = Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tu vehículo, en un solo lugar", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                Text("Completa los datos para comenzar.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("* Campos obligatorios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SeccionRegistro("Información general", R.drawable.ic_rf08_informacion, generales,
        { generales = !generales }, habilitado, estado.errores.keys.any { it in camposGenerales }) {
        CampoRegistro("Marca *", "marca", f.marca, estado, onCambiar, ejemplo = "Ej. Toyota")
        CampoRegistro("Modelo *", "modelo", f.modelo, estado, onCambiar, ejemplo = "Ej. Corolla")
        SelectorRegistro("Uso del vehículo *", f.usoVehiculoCodigo,
            catalogos.usos.map { it.codigo to it.nombre }, estado.errores["usoVehiculoCodigo"], habilitado) { onCambiar("usoVehiculoCodigo", it) }
        CampoRegistro("Placa *", "placa", f.placa, estado, onCambiar, mayusculas = true,
            ejemplo = "Ej. A1B234", ayuda = "Sin espacios ni guiones.")
        CampoRegistro("Año de fabricación *", "anioFabricacion", f.anioFabricacion, estado, onCambiar,
            numerico = true, ejemplo = "Ej. 2020", ayuda = "Solo el año, no una fecha completa.")
        CampoRegistro("Color *", "color", f.color, estado, onCambiar, ejemplo = "Ej. Gris")
    }
    SeccionRegistro("Identificación adicional (opcional)", R.drawable.ic_rf08_identificacion, identificacion,
        { identificacion = !identificacion }, habilitado, estado.errores.keys.any { it in camposIdentificacion }) {
        Text("Puedes completar estos datos ahora o dejarlos vacíos.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoRegistro("VIN", "numeroVin", f.numeroVin, estado, onCambiar, mayusculas = true,
            ayuda = "17 caracteres; no admite I, O ni Q.")
        CampoRegistro("Número de serie", "numeroSerie", f.numeroSerie, estado, onCambiar,
            mayusculas = true, ayuda = "De 6 a 80 letras mayúsculas o números.")
        if (tipo?.esElectrico != true) CampoRegistro("Número de motor", "numeroMotor", f.numeroMotor, estado,
            onCambiar, mayusculas = true, ayuda = "De 5 a 15 caracteres; admite guiones y espacios.")
    }
    SeccionRegistro("Motor y mantenimiento", R.drawable.ic_rf08_motor, motor,
        { motor = !motor }, habilitado, estado.errores.keys.any { it in camposMotor }) {
        SelectorRegistro("Tipo de motor *", f.idTipoMotor,
            catalogos.tiposMotor.map { it.idTipoMotor.toString() to it.nombre }, estado.errores["idTipoMotor"], habilitado) { onCambiar("idTipoMotor", it) }
        if (tipo?.codigo == "OTRO") CampoRegistro("Especifica el tipo de motor *", "tipoMotorOtro", f.tipoMotorOtro, estado, onCambiar)
        if (tipo?.esElectrico == true) {
            Text("Combustible, aceite y número de motor no aplican a vehículos eléctricos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SelectorRegistro("Combustible *", f.idCombustible,
                catalogos.combustibles.map { it.idCombustible.toString() to it.nombre }, estado.errores["idCombustible"], habilitado) { onCambiar("idCombustible", it) }
            if (combustible?.codigo == "OTRO") CampoRegistro("Especifica el combustible *", "combustibleOtro", f.combustibleOtro, estado, onCambiar)
            SelectorRegistro("Tipo de aceite (opcional)", f.idTipoAceite,
                listOf("" to "Sin especificar") + catalogos.tiposAceite.map { it.idTipoAceite.toString() to it.nombre },
                estado.errores["idTipoAceite"], habilitado) { onCambiar("idTipoAceite", it) }
            if (aceite?.codigo == "OTRO") CampoRegistro("Especifica el tipo de aceite *", "tipoAceiteOtro", f.tipoAceiteOtro, estado, onCambiar)
        }
        CampoRegistro("Kilometraje inicial *", "kilometrajeInicial", f.kilometrajeInicial, estado, onCambiar,
            numerico = true, ejemplo = "Ej. 58240", ayuda = "Número entero mayor que cero.", unidad = "km")
    }
}

@Composable
private fun SeccionRegistro(titulo: String, icono: Int, abierta: Boolean, onAlternar: () -> Unit,
                            habilitada: Boolean, tieneErrores: Boolean, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Surface(onClick = onAlternar, enabled = habilitada, color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (abierta) "Expandida" else "Contraída" }) {
                Row(Modifier.heightIn(min = 50.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f).semantics { heading() })
                    if (tieneErrores) Text("!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Icon(painterResource(if (abierta) R.drawable.ic_rf08_arriba else R.drawable.ic_rf08_abajo),
                        contentDescription = if (abierta) "Contraer sección" else "Expandir sección",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            AnimatedVisibility(visible = abierta) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
            }
        }
    }
}

@Composable
private fun CampoRegistro(etiqueta: String, campo: String, valor: String, estado: EstadoRegistroVehiculo,
                          onCambiar: (String, String) -> Unit, numerico: Boolean = false, mayusculas: Boolean = false,
                          ejemplo: String = "", ayuda: String? = null, unidad: String? = null) {
    val error = estado.errores[campo]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value = valor, onValueChange = { onCambiar(campo, it) },
            modifier = Modifier.fillMaxWidth(), enabled = !estado.ocupado,
            singleLine = true, isError = error != null, shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { if (ejemplo.isNotEmpty()) Text(ejemplo, style = MaterialTheme.typography.bodyMedium) },
            suffix = if (unidad == null) null else ({ Text(unidad, style = MaterialTheme.typography.bodySmall) }),
            label = { Text(etiqueta, style = MaterialTheme.typography.bodySmall) },
            supportingText = if (error == null && ayuda == null) null else ({ Text(error ?: ayuda.orEmpty()) }),
            keyboardOptions = KeyboardOptions(
                capitalization = if (mayusculas) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
                imeAction = if (campo == "kilometrajeInicial") ImeAction.Done else ImeAction.Next))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorRegistro(etiqueta: String, valor: String, opciones: List<Pair<String, String>>,
                             error: String?, habilitado: Boolean, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = abierto && habilitado, onExpandedChange = { if (habilitado) abierto = it }) {
            OutlinedTextField(value = opciones.find { it.first == valor }?.second.orEmpty(), onValueChange = {},
                readOnly = true, enabled = habilitado, singleLine = true, isError = error != null,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text(etiqueta, style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text("Seleccionar", style = MaterialTheme.typography.bodyMedium) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto && habilitado) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = habilitado).fillMaxWidth(),
                supportingText = if (error == null) null else ({ Text(error) }))
            ExposedDropdownMenu(expanded = abierto && habilitado, onDismissRequest = { abierto = false }) {
                opciones.forEach { (codigo, nombre) ->
                    DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(codigo); abierto = false })
                }
            }
        }
    }
}
