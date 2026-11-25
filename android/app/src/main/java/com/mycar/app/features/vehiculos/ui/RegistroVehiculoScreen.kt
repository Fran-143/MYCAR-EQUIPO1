package com.mycar.app.features.vehiculos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.mycar.app.features.fotos.ui.FlujoSelectorFotoPersonalizada
import com.mycar.app.features.vehiculos.domain.*
import com.mycar.app.features.vehiculos.viewmodel.RegistroVehiculoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier,
    estadoEnSistema: Boolean = true,
    fotosRepository: com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository? = null
) {
    val vm: RegistroVehiculoViewModel = viewModel(key = "rf08-$claveCuenta",
        factory = remember(claveCuenta, repository, pendientes) { RegistroVehiculoViewModel.factory(repository, pendientes, estadoEnSistema) })
    val estado by vm.estado.collectAsStateWithLifecycle()
    var fotoPendiente by remember(claveCuenta) { mutableStateOf<ByteArray?>(null) }
    var fotoContentType by remember(claveCuenta) { mutableStateOf("image/jpeg") }
    var fotoNombre by remember(claveCuenta) { mutableStateOf("vehiculo.jpg") }
    var avisoFoto by remember { mutableStateOf<String?>(null) }
    var subiendoFoto by remember { mutableStateOf(false) }
    var fotoYaIntentada by remember(claveCuenta) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) { vm.cargar() }
    LaunchedEffect(estado.fase, estado.resultado?.idVehiculo, fotoPendiente) {
        val id = estado.resultado?.idVehiculo
        val bytes = fotoPendiente
        if (estado.fase == FaseRegistro.CONFIRMADA && id != null && id > 0 && bytes != null
            && fotosRepository != null && !subiendoFoto && !fotoYaIntentada
        ) {
            fotoYaIntentada = true
            subiendoFoto = true
            scope.launch {
                try {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        fotosRepository.registrarVehiculo(id, bytes, fotoContentType, fotoNombre)
                    }
                    fotoPendiente = null
                    avisoFoto = null
                } catch (_: Exception) {
                    avisoFoto = "El vehículo se registró, pero la fotografía no pudo guardarse. Puedes añadirla desde el detalle."
                } finally {
                    subiendoFoto = false
                }
            }
        }
    }
    BackHandler {
        if (!estado.ocupado && !subiendoFoto) {
            if (estado.fase == FaseRegistro.CONFIRMADA) vm.finalizar(onIrInicio) else onVolver()
        }
    }
    RegistroVehiculoScreen(
        estado, vm::cambiar, vm::guardar, vm::cargar, vm::comprobar, vm::reenviar,
        onVolver, onIniciarSesion, { vm.finalizar(onVerVehiculos) }, { vm.finalizar(onIrInicio) },
        modifier,
        fotoPendiente = fotoPendiente,
        onFotoElegida = { bytes, ct, nombre ->
            fotoPendiente = bytes
            fotoContentType = ct
            fotoNombre = nombre
            avisoFoto = null
        },
        onQuitarFoto = { fotoPendiente = null },
        avisoFoto = avisoFoto,
        subiendoFoto = subiendoFoto
    )
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
    modifier: Modifier = Modifier,
    fotoPendiente: ByteArray? = null,
    onFotoElegida: (ByteArray, String, String) -> Unit = { _, _, _ -> },
    onQuitarFoto: () -> Unit = {},
    avisoFoto: String? = null,
    subiendoFoto: Boolean = false
) {
    var camposEditados by rememberSaveable { mutableStateOf(listOf<String>()) }
    val erroresLocales = estado.catalogos?.let { ValidadorFormularioRegistro.validar(estado.formulario, it) }
    val puedeGuardar = !estado.ocupado && erroresLocales != null && erroresLocales.isEmpty()
    val estadoVisible = estado.copy(errores = estado.errores +
        erroresLocales.orEmpty().filterKeys { it in camposEditados })
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
                        Text(
                            if (estado.fase == FaseRegistro.CONFIRMADA) "¡Vehículo agregado!" else "Agregar vehículo",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = Color(0xFF003366),
                            modifier = Modifier.padding(vertical = 16.dp).semantics { heading() }
                        )
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
                            Button(onClick = onGuardar, enabled = puedeGuardar,
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
                    FaseRegistro.FORMULARIO -> {
                        if (!puedeGuardar && !estado.ocupado) Text(
                            "Completa los campos obligatorios y corrige los datos para habilitar Guardar.",
                            style = MaterialTheme.typography.bodySmall)
                        FormularioRegistroContenido(
                            estado = estadoVisible,
                            fotoPendiente = fotoPendiente,
                            onFotoElegida = onFotoElegida,
                            onQuitarFoto = onQuitarFoto
                        ) { campo, valor ->
                            if (campo !in camposEditados) camposEditados = camposEditados + campo
                            onCambiar(campo, valor)
                        }
                    }
                    FaseRegistro.SESION_REQUERIDA -> {
                        Text("Inicia sesión para registrar tu vehículo.")
                        Button(onClick = onIniciarSesion, enabled = !estado.ocupado) { Text("Iniciar sesión") }
                        OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Volver a comprobar") }
                    }
                    FaseRegistro.SIN_CUPO -> {
                        val azul = Color(0xFF1565C0)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = azul.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, azul.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .background(azul.copy(alpha = 0.14f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.DirectionsCar,
                                        contentDescription = null,
                                        tint = azul,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    "Cupo de vehículos completo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF003366)
                                )
                                Text(
                                    estado.mensaje
                                        ?: "Tu plan ya alcanzó el máximo de vehículos registrados.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onVolver,
                                    enabled = !estado.ocupado,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = azul),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Volver a mis vehículos") }
                            }
                        }
                    }
                    FaseRegistro.INCIERTA -> {
                        Text("Conservamos tu solicitud para evitar registros duplicados.")
                        Button(onClick = onComprobar, enabled = !estado.ocupado, modifier = Modifier.fillMaxWidth()) { Text("Comprobar registro") }
                        if (estado.permiteReenviar) OutlinedButton(onClick = onReenviar, enabled = !estado.ocupado,
                            modifier = Modifier.fillMaxWidth()) { Text("Reintentar envío") }
                    }
                    FaseRegistro.CONFIRMADA -> {
                        val azul = Color(0xFF003366)
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(110.dp)
                                .background(Color(0xFFE8F5E9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF43A047),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                        Text(
                            "¡Vehículo agregado!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = azul,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            "${estado.formulario.marca} ${estado.formulario.modelo}".trim().ifBlank { "Tu vehículo" },
                            style = MaterialTheme.typography.titleMedium,
                            color = azul,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        estado.resultado?.let {
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Resumen", fontWeight = FontWeight.SemiBold, color = azul)
                                    Text("Placa: ${it.placa}")
                                    Text("Kilometraje inicial: ${it.kilometrajeActual} km")
                                }
                            }
                        }
                        avisoFoto?.let {
                            Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodySmall)
                        }
                        if (subiendoFoto) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Guardando fotografía…")
                        }
                        Button(
                            onClick = onVerVehiculos,
                            enabled = !estado.ocupado && !subiendoFoto,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = azul)
                        ) { Text("Ver mis vehículos", fontWeight = FontWeight.SemiBold) }
                        OutlinedButton(
                            onClick = onIrInicio,
                            enabled = !estado.ocupado && !subiendoFoto,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Ir al inicio") }
                    }
                    FaseRegistro.ERROR -> OutlinedButton(onClick = onRecargar, enabled = !estado.ocupado) { Text("Reintentar") }
                }
            }
        }
}

@Composable
private fun FormularioRegistroContenido(
    estado: EstadoRegistroVehiculo,
    fotoPendiente: ByteArray?,
    onFotoElegida: (ByteArray, String, String) -> Unit,
    onQuitarFoto: () -> Unit,
    onCambiar: (String, String) -> Unit
) {
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
    var selectorFoto by remember { mutableStateOf(false) }
    val errorGeneral = estado.errores.keys.any { it in camposGenerales }
    val errorIdentificacion = estado.errores.keys.any { it in camposIdentificacion }
    val errorMotor = estado.errores.keys.any { it in camposMotor }
    val azul = Color(0xFF003366)
    val azulClaro = Color(0xFFE8F4FC)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = azulClaro,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, azul.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(enabled = habilitado) { selectorFoto = true }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (fotoPendiente != null) {
                    val bmp = remember(fotoPendiente) {
                        BitmapFactory.decodeByteArray(fotoPendiente, 0, fotoPendiente.size)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Vista previa",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Filled.AddAPhoto, null, tint = azul)
                    }
                } else {
                    Icon(Icons.Filled.AddAPhoto, null, tint = azul, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Agregar imagen", fontWeight = FontWeight.SemiBold, color = azul)
                Text(
                    "Opcional · JPG/PNG hasta 5 MiB",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7C93)
                )
                if (fotoPendiente != null) {
                    TextButton(onClick = onQuitarFoto, enabled = habilitado) { Text("Quitar imagen") }
                }
            }
        }
    }
    FlujoSelectorFotoPersonalizada(
        activo = selectorFoto,
        onDismiss = { selectorFoto = false },
        onConfirmada = {
            onFotoElegida(it.bytes, it.contentType, it.nombre)
            selectorFoto = false
        },
        onError = { selectorFoto = false }
    )
    Text("* Campos obligatorios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SeccionRegistro("Información general", R.drawable.ic_rf08_informacion, generales || errorGeneral,
        { generales = if (errorGeneral) true else !generales }, habilitado, errorGeneral) {
        CampoRegistro("Marca *", "marca", f.marca, estado, onCambiar, mayusculas = true, ejemplo = "Ej. Toyota")
        CampoRegistro("Modelo *", "modelo", f.modelo, estado, onCambiar, mayusculas = true, ejemplo = "Ej. Corolla")
        SelectorRegistro("Uso del vehículo *", f.usoVehiculoCodigo,
            catalogos.usos.map { it.codigo to it.nombre }, estado.errores["usoVehiculoCodigo"], habilitado) { onCambiar("usoVehiculoCodigo", it) }
        CampoRegistro("Placa *", "placa", f.placa, estado, onCambiar, mayusculas = true,
            ejemplo = "Ej. A1B234 o ABC123", ayuda = "Dos formatos: A1B234 o ABC123. Sin espacios ni guiones.")
        CampoRegistro("Año de fabricación *", "anioFabricacion", f.anioFabricacion, estado, onCambiar,
            numerico = true, ejemplo = "Ej. 2020", ayuda = "Solo el año, no una fecha completa.")
        CampoRegistro("Color *", "color", f.color, estado, onCambiar, mayusculas = true, ejemplo = "Ej. Gris")
    }
    SeccionRegistro("Identificación adicional (opcional)", R.drawable.ic_rf08_identificacion, identificacion || errorIdentificacion,
        { identificacion = if (errorIdentificacion) true else !identificacion }, habilitado, errorIdentificacion) {
        Text("Puedes completar estos datos ahora o dejarlos vacíos.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CampoRegistro("VIN", "numeroVin", f.numeroVin, estado, onCambiar, mayusculas = true,
            ayuda = "17 caracteres; no admite I, O ni Q.")
        CampoRegistro("Número de serie", "numeroSerie", f.numeroSerie, estado, onCambiar,
            mayusculas = true, ayuda = "De 6 a 80 letras mayúsculas o números.")
        if (tipo?.esElectrico != true) CampoRegistro("Número de motor", "numeroMotor", f.numeroMotor, estado,
            onCambiar, mayusculas = true, ayuda = "De 5 a 15 caracteres; admite guiones y espacios.")
    }
    SeccionRegistro("Motor y mantenimiento", R.drawable.ic_rf08_motor, motor || errorMotor,
        { motor = if (errorMotor) true else !motor }, habilitado, errorMotor) {
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
