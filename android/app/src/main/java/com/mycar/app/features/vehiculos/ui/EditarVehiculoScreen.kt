package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.vehiculos.domain.EditarVehiculoRepository
import com.mycar.app.features.vehiculos.domain.ValidadorFormularioEdicion
import com.mycar.app.features.vehiculos.viewmodel.*

@Composable
fun EditarVehiculoRoute(
    claveCuenta: String,
    idVehiculo: Long,
    repository: EditarVehiculoRepository,
    onVolver: () -> Unit,
    onGuardado: () -> Unit,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: EditarVehiculoViewModel = viewModel(
        key = "rf11-$claveCuenta-$idVehiculo",
        factory = remember(repository, idVehiculo) { EditarVehiculoViewModel.factory(idVehiculo, repository) }
    )
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.cargar() }
    BackHandler(enabled = !estado.ocupado) { onVolver() }
    EditarVehiculoScreen(
        estado = estado,
        onCambiar = vm::cambiar,
        onGuardar = { vm.guardar(onGuardado) },
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onIniciarSesion = onIniciarSesion,
        onCerrarAvisoSinCambios = vm::cerrarAvisoSinCambios,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarVehiculoScreen(
    estado: EstadoEdicionVehiculo,
    onCambiar: (String, String) -> Unit,
    onGuardar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onIniciarSesion: () -> Unit,
    onCerrarAvisoSinCambios: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val catalogos = estado.catalogos
    val erroresLocales = catalogos?.let {
        ValidadorFormularioEdicion.validar(estado.formulario, it, estado.tieneTive)
    }.orEmpty()
    // Guardar habilitado también sin cambios para mostrar el modal de misma información.
    val puedeGuardar = !estado.ocupado && estado.fase == FaseEdicion.FORMULARIO
        && erroresLocales.isEmpty()
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver, enabled = !estado.ocupado) { Text("Cancelar") }
                    Text(
                        "Editar datos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            if (estado.fase == FaseEdicion.FORMULARIO || estado.fase == FaseEdicion.GUARDANDO) {
                Surface(shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onVolver,
                            enabled = !estado.ocupado,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) { Text("Cancelar") }
                        Button(
                            onClick = onGuardar,
                            enabled = puedeGuardar,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.4f).heightIn(min = 50.dp)
                        ) {
                            Text(
                                when {
                                    estado.ocupado -> "Guardando…"
                                    !estado.hayCambios -> "Sin cambios"
                                    else -> "Guardar cambios"
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.ocupado || estado.fase == FaseEdicion.CARGANDO) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            estado.mensaje?.let { texto ->
                Text(
                    texto,
                    color = if (estado.errores.isNotEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (estado.errores.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            when (estado.fase) {
                FaseEdicion.CARGANDO -> Text("Cargando datos del vehículo…")
                FaseEdicion.SESION_REQUERIDA -> {
                    Button(onClick = onIniciarSesion, shape = RoundedCornerShape(12.dp)) { Text("Iniciar sesión") }
                }
                FaseEdicion.ERROR, FaseEdicion.NO_EDITABLE -> {
                    OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
                    TextButton(onClick = onVolver) { Text("Volver al detalle") }
                }
                FaseEdicion.FORMULARIO, FaseEdicion.GUARDANDO -> catalogos?.let { cats ->
                    val f = estado.formulario
                    val tipo = cats.tiposMotor.find { it.idTipoMotor.toString() == f.idTipoMotor }
                    val combustible = cats.combustibles.find { it.idCombustible.toString() == f.idCombustible }
                    val aceite = cats.tiposAceite.find { it.idTipoAceite.toString() == f.idTipoAceite }
                    Text("* Campos obligatorios", style = MaterialTheme.typography.bodySmall)
                    CampoEdicion("Placa", estado.placa, soloLectura = true, ayuda = "La placa no se puede modificar desde aquí.")
                    CampoEdicion("Kilometraje actual",
                        if (estado.kilometrajeActual.isBlank()) "Sin lectura" else "${estado.kilometrajeActual} km",
                        soloLectura = true, ayuda = "El kilometraje se actualiza en su propio flujo.")
                    fun err(campo: String) = estado.errores[campo] ?: erroresLocales[campo]
                    CampoEdicion("Marca *", f.marca, error = err("marca"),
                        enabled = !estado.ocupado) { onCambiar("marca", it) }
                    CampoEdicion("Modelo *", f.modelo, error = err("modelo"),
                        enabled = !estado.ocupado) { onCambiar("modelo", it) }
                    SelectorEdicion("Uso del vehículo *", f.usoVehiculoCodigo,
                        cats.usos.map { it.codigo to it.nombre }, err("usoVehiculoCodigo"),
                        !estado.ocupado) { onCambiar("usoVehiculoCodigo", it) }
                    CampoEdicion("Año de fabricación *", f.anioFabricacion, error = err("anioFabricacion"),
                        numerico = true, enabled = !estado.ocupado) { onCambiar("anioFabricacion", it) }
                    CampoEdicion("Color *", f.color, error = err("color"),
                        enabled = !estado.ocupado) { onCambiar("color", it) }
                    SelectorEdicion("Tipo de motor *", f.idTipoMotor,
                        cats.tiposMotor.map { it.idTipoMotor.toString() to it.nombre },
                        err("idTipoMotor"), !estado.ocupado) { onCambiar("idTipoMotor", it) }
                    if (tipo?.codigo == "OTRO") {
                        CampoEdicion("Especifica el tipo de motor *", f.tipoMotorOtro,
                            error = err("tipoMotorOtro"), enabled = !estado.ocupado) {
                            onCambiar("tipoMotorOtro", it)
                        }
                    }
                    if (tipo?.esElectrico == true) {
                        Text("Combustible, aceite y número de motor no aplican a vehículos eléctricos.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        SelectorEdicion("Combustible *", f.idCombustible,
                            cats.combustibles.map { it.idCombustible.toString() to it.nombre },
                            err("idCombustible"), !estado.ocupado) { onCambiar("idCombustible", it) }
                        if (combustible?.codigo == "OTRO") {
                            CampoEdicion("Especifica el combustible *", f.combustibleOtro,
                                error = err("combustibleOtro"), enabled = !estado.ocupado) {
                                onCambiar("combustibleOtro", it)
                            }
                        }
                        SelectorEdicion("Tipo de aceite (opcional)", f.idTipoAceite,
                            listOf("" to "Sin especificar") + cats.tiposAceite.map { it.idTipoAceite.toString() to it.nombre },
                            err("idTipoAceite"), !estado.ocupado) { onCambiar("idTipoAceite", it) }
                        if (aceite?.codigo == "OTRO") {
                            CampoEdicion("Especifica el tipo de aceite *", f.tipoAceiteOtro,
                                error = err("tipoAceiteOtro"), enabled = !estado.ocupado) {
                                onCambiar("tipoAceiteOtro", it)
                            }
                        }
                    }
                    if (estado.tieneTive) {
                        Text("VIN, motor y serie están protegidos por la TIVE registrada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CampoEdicion("VIN", f.numeroVin, error = err("numeroVin"),
                        soloLectura = estado.tieneTive, enabled = !estado.ocupado && !estado.tieneTive,
                        ayuda = if (estado.tieneTive) null else "17 caracteres; sin I, O ni Q.") {
                        onCambiar("numeroVin", it)
                    }
                    CampoEdicion("Número de serie", f.numeroSerie, error = err("numeroSerie"),
                        soloLectura = estado.tieneTive, enabled = !estado.ocupado && !estado.tieneTive) {
                        onCambiar("numeroSerie", it)
                    }
                    if (tipo?.esElectrico != true) {
                        CampoEdicion("Número de motor", f.numeroMotor, error = err("numeroMotor"),
                            soloLectura = estado.tieneTive, enabled = !estado.ocupado && !estado.tieneTive) {
                            onCambiar("numeroMotor", it)
                        }
                    }
                }
            }
        }
    }

    if (estado.mostrarAvisoSinCambios) {
        AlertDialog(
            onDismissRequest = onCerrarAvisoSinCambios,
            title = { Text("Sin cambios", fontWeight = FontWeight.Bold) },
            text = {
                Text("No puedes guardar la misma información. Modifica algún campo o vuelve sin guardar.")
            },
            confirmButton = {
                TextButton(onClick = onCerrarAvisoSinCambios) { Text("Entendido") }
            }
        )
    }
}

@Composable
private fun CampoEdicion(
    etiqueta: String,
    valor: String,
    error: String? = null,
    ayuda: String? = null,
    numerico: Boolean = false,
    soloLectura: Boolean = false,
    enabled: Boolean = true,
    onCambiar: ((String) -> Unit)? = null
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { if (!soloLectura) onCambiar?.invoke(it) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !soloLectura,
        readOnly = soloLectura,
        singleLine = true,
        isError = error != null,
        shape = RoundedCornerShape(10.dp),
        label = { Text(etiqueta) },
        supportingText = if (error == null && ayuda == null) null else ({ Text(error ?: ayuda.orEmpty()) }),
        keyboardOptions = KeyboardOptions(
            capitalization = if (numerico) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
            keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Next
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorEdicion(
    etiqueta: String,
    valor: String,
    opciones: List<Pair<String, String>>,
    error: String?,
    habilitado: Boolean,
    onElegir: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto && habilitado, onExpandedChange = { if (habilitado) abierto = it }) {
        OutlinedTextField(
            value = opciones.find { it.first == valor }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            singleLine = true,
            isError = error != null,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto && habilitado) },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = habilitado).fillMaxWidth(),
            supportingText = if (error == null) null else ({ Text(error) })
        )
        ExposedDropdownMenu(expanded = abierto && habilitado, onDismissRequest = { abierto = false }) {
            opciones.forEach { (codigo, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { onElegir(codigo); abierto = false })
            }
        }
    }
}
