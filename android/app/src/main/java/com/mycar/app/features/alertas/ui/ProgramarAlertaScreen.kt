package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.TipoAlerta
import com.mycar.app.features.alertas.viewmodel.BorradorAlerta
import com.mycar.app.features.alertas.viewmodel.PasoProgramar
import com.mycar.app.features.alertas.viewmodel.ProgramarAlertaViewModel
import com.mycar.app.features.alertas.viewmodel.ResumenAlerta
import com.mycar.app.ui.theme.MyCarBlue
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarSky
import com.mycar.app.ui.theme.MyCarTile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BordeAzulSuave = Color(0xFFC5D8F0)

@Composable
fun ProgramarAlertaRoute(
    repository: AlertasRepository,
    idVehiculo: Long,
    vehiculos: List<Pair<Long, String>>,
    sesionFormulario: Int,
    borradorInicial: BorradorAlerta?,
    onVolver: () -> Unit,
    onExito: () -> Unit,
    onIrInicio: () -> Unit = onExito,
    modifier: Modifier = Modifier
) {
    val vm: ProgramarAlertaViewModel = viewModel(key = "rf22-$idVehiculo-s$sesionFormulario") {
        ProgramarAlertaViewModel(repository, idVehiculo, borradorInicial)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    ProgramarAlertaScreen(
        estado = estado,
        vehiculos = vehiculos,
        onActualizar = vm::actualizar,
        onCambiarVehiculo = vm::cambiarVehiculo,
        onRevisar = vm::revisar,
        onConfirmar = vm::confirmar,
        onVolverFormulario = vm::volverFormulario,
        onRecargar = vm::cargar,
        onVolver = onVolver,
        onExitoVolver = onExito,
        onIrInicio = onIrInicio,
        modifier = modifier
    )
}

@Composable
fun ProgramarAlertaScreen(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    vehiculos: List<Pair<Long, String>> = emptyList(),
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onCambiarVehiculo: (Long) -> Unit = {},
    onRevisar: () -> Unit,
    onConfirmar: () -> Unit,
    onVolverFormulario: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    onExitoVolver: () -> Unit,
    onIrInicio: () -> Unit = onExitoVolver,
    modifier: Modifier = Modifier
) {
    when (estado.paso) {
        PasoProgramar.EXITO -> PantallaExitoAlerta(estado.exito, onExitoVolver, onIrInicio, modifier)
        PasoProgramar.REVISION -> PantallaRevisionAlerta(estado, onConfirmar, onVolverFormulario, modifier)
        PasoProgramar.FORMULARIO -> PantallaFormularioAlerta(
            estado, vehiculos, onActualizar, onCambiarVehiculo, onRevisar, onRecargar, onVolver, modifier
        )
    }
}

@Composable
private fun PantallaExitoAlerta(
    idAlerta: Long?,
    onVerAlertas: () -> Unit,
    onIrInicio: () -> Unit,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.background)) {
        CabeceraProgramar(
            titulo = "¡Alerta programada!",
            subtitulo = if (idAlerta != null) "La alerta #$idAlerta quedó registrada."
            else "La alerta quedó registrada.",
            icono = Icons.Filled.CheckCircle
        )
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BotonPrimario(
                texto = "Ver alertas",
                onClick = onVerAlertas,
                enabled = true,
                icono = Icons.Outlined.Notifications
            )
            BotonSecundario(
                texto = "Ir al inicio",
                onClick = onIrInicio,
                enabled = true,
                icono = Icons.Outlined.Home
            )
        }
    }
}

@Composable
private fun PantallaRevisionAlerta(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onConfirmar: () -> Unit,
    onVolverFormulario: () -> Unit,
    modifier: Modifier
) {
    val b = estado.borrador
    val r = estado.resumen
    val ctx = estado.contexto
    val scheme = MaterialTheme.colorScheme
    var paso = 1
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CabeceraProgramar(
                titulo = "Revisar programación",
                subtitulo = "Paso 2 de 3 · confirma antes de guardar",
                onVolver = onVolverFormulario,
                volverEnabled = !estado.enviando
            )
        },
        bottomBar = {
            BarraAcciones {
                BotonPrimario(
                    texto = if (estado.enviando) "Guardando…" else "Programar alerta",
                    onClick = onConfirmar,
                    enabled = !estado.enviando
                )
                BotonSecundario(
                    texto = "Editar configuración",
                    onClick = onVolverFormulario,
                    enabled = !estado.enviando,
                    icono = Icons.Outlined.Edit
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.basesCambiaron) {
                AvisoProgramar(
                    estado.mensaje ?: "Las bases cambiaron. Confirma de nuevo con los valores actualizados.",
                    error = true
                )
            } else {
                estado.mensaje?.let { AvisoProgramar(it, error = true) }
            }
            SeccionProgramar(paso++, "Servicio", Icons.Outlined.Build) {
                Text(
                    "Tipo: ${ctx?.tipos?.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: b.codigoTipo}",
                    color = scheme.onSurface
                )
                if (b.codigoTipo == "OTRO") {
                    Text("Servicio: ${b.servicioOtro}", color = scheme.onSurface)
                }
            }
            SeccionProgramar(paso++, "Criterio", Icons.Outlined.Tune) {
                Text("Criterio: ${etiquetaCriterio(b.criterio)}", color = scheme.onSurface)
                Text(
                    "Origen: " + if (b.origenCalculo == "DESDE_MANTENIMIENTO") "Desde un mantenimiento realizado"
                    else "Desde hoy",
                    color = scheme.onSurface
                )
            }
            if (CriterioAlerta.usaTiempo(b.criterio) || CriterioAlerta.usaKm(b.criterio)) {
                SeccionProgramar(paso++, "Intervalo", Icons.Outlined.Schedule) {
                    if (CriterioAlerta.usaTiempo(b.criterio)) {
                        Text(
                            "Intervalo temporal: ${b.cantidadTiempo} ${b.unidadTiempo.lowercase()}",
                            color = scheme.onSurface
                        )
                    }
                    if (CriterioAlerta.usaKm(b.criterio)) {
                        Text("Intervalo km: ${b.intervaloKm} km", color = scheme.onSurface)
                    }
                }
            }
            SeccionProgramar(paso, "Cálculo", Icons.Outlined.EventAvailable) {
                Text("Fecha base: ${r?.fechaBase ?: "—"}", color = scheme.onSurface)
                Text("Kilometraje base: ${r?.kmBase?.let { "$it km" } ?: "—"}", color = scheme.onSurface)
                r?.fechaObjetivo?.let { Text("Fecha objetivo: $it", color = scheme.onSurface) }
                r?.kmObjetivo?.let { Text("Kilometraje objetivo: $it km", color = scheme.onSurface) }
                r?.referencia?.let { ref ->
                    Text(
                        "Mantenimiento de referencia: ${etiquetaMantenimiento(nombreServicio(ref, ctx?.tipos.orEmpty()), ref)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (fondoOscuro()) scheme.surfaceVariant else MyCarTile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        "Estos valores se guardarán al confirmar. Aún no está programada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PantallaFormularioAlerta(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    vehiculos: List<Pair<Long, String>>,
    onActualizar: ((BorradorAlerta) -> BorradorAlerta) -> Unit,
    onCambiarVehiculo: (Long) -> Unit,
    onRevisar: () -> Unit,
    onRecargar: () -> Unit,
    onVolver: () -> Unit,
    modifier: Modifier
) {
    val ctx = estado.contexto
    if (ctx == null) {
        PantallaFormularioAlertaSinContexto(estado, onVolver, onRecargar, modifier)
        return
    }
    val b = estado.borrador
    val tipos = ctx.tipos
    val refs = ProgramarAlertaViewModel.referenciasElegibles(b, ctx)
    val editable = !estado.enviando && estado.exito == null
    val scheme = MaterialTheme.colorScheme
    var paso = 1
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CabeceraProgramar(
                titulo = "Programar alerta",
                subtitulo = "Paso 1 de 3 · servicio, criterio e intervalo",
                onVolver = onVolver,
                volverEnabled = !estado.enviando
            )
        },
        bottomBar = {
            BarraAcciones {
                BotonPrimario(
                    texto = "Revisar programación",
                    onClick = onRevisar,
                    enabled = editable && !estado.cargando && estado.contexto?.disponible == true && tipos.isNotEmpty() && vehiculos.isNotEmpty(),
                    iconoFinal = Icons.AutoMirrored.Filled.ArrowForward
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.cargando) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant
                )
            }
            estado.mensaje?.let {
                AvisoProgramar(it, error = true)
                if (estado.contexto == null && !estado.cargando) {
                    BotonSecundario(texto = "Reintentar carga", onClick = onRecargar, enabled = editable)
                }
            }

            SeccionProgramar(paso++, "Vehículo y servicio", Icons.Outlined.DirectionsCar) {
                if (vehiculos.isEmpty()) {
                    Text("No hay vehículos activos para programar alertas.", color = scheme.onSurface)
                } else {
                    SelectorSimple(
                        etiqueta = vehiculos.firstOrNull { it.first == estado.idVehiculo }?.second
                            ?: "Seleccionar vehículo",
                        opciones = vehiculos.map { it.first.toString() to it.second },
                        seleccionado = estado.idVehiculo.toString(),
                        enabled = editable && vehiculos.size > 1
                    ) { id -> onCambiarVehiculo(id.toLong()) }
                }
                Text(
                    "Se mostrarán únicamente tus vehículos activos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            SeccionProgramar(paso++, "Tipo de servicio", Icons.Outlined.Build) {
                if (tipos.isEmpty() && !estado.cargando) {
                    Text(
                        "No hay tipos disponibles. Comprueba la conexión e inténtalo de nuevo.",
                        color = scheme.onSurface
                    )
                } else {
                    SelectorSimple(
                        etiqueta = tipos.firstOrNull { it.codigo == b.codigoTipo }?.nombre ?: "Seleccionar tipo",
                        opciones = tipos.map { it.codigo to it.nombre },
                        seleccionado = b.codigoTipo,
                        enabled = editable
                    ) { codigo ->
                        onActualizar {
                            it.copy(
                                codigoTipo = codigo,
                                servicioOtro = if (codigo == "OTRO") it.servicioOtro else "",
                                idReferencia = null
                            )
                        }
                    }
                }
                estado.errores["codigoTipo"]?.let {
                    Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (b.codigoTipo == "OTRO") {
                    OutlinedTextField(
                        b.servicioOtro,
                        { v -> onActualizar { it.copy(servicioOtro = v, idReferencia = null) } },
                        label = { Text("Especificar servicio") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editable,
                        isError = estado.errores.containsKey("servicioOtro"),
                        supportingText = estado.errores["servicioOtro"]?.let { { Text(it) } },
                        shape = RoundedCornerShape(12.dp),
                        colors = coloresCampo()
                    )
                }
            }

            SeccionProgramar(paso++, "Tipo de alerta", Icons.Outlined.Tune) {
                listOf(
                    "POR_TIEMPO" to "Por tiempo",
                    "POR_KILOMETRAJE" to "Por kilometraje",
                    "AMBOS" to "Por tiempo y kilometraje"
                ).forEach { (c, l) ->
                    val seleccionado = b.criterio == c
                    val forma = RoundedCornerShape(12.dp)
                    val icono = when (c) {
                        "POR_TIEMPO" -> Icons.Outlined.Schedule
                        "POR_KILOMETRAJE" -> Icons.Outlined.Speed
                        else -> Icons.Outlined.Tune
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(forma)
                            .background(if (seleccionado) scheme.primaryContainer else Color.Transparent)
                            .border(
                                1.dp,
                                if (seleccionado) scheme.primary else bordeSuave(),
                                forma
                            )
                            .selectable(
                                selected = seleccionado,
                                enabled = editable,
                                role = Role.RadioButton,
                                onClick = { onActualizar { it.copy(criterio = c) } }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = seleccionado,
                            onClick = null,
                            enabled = editable,
                            colors = RadioButtonDefaults.colors(selectedColor = scheme.primary)
                        )
                        Icon(
                            icono,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(18.dp)
                        )
                        Text(l, modifier = Modifier.padding(start = 8.dp), color = scheme.onSurface)
                    }
                }
            }

            SeccionProgramar(paso++, "¿Desde cuándo calcular?", Icons.Outlined.Event) {
                SelectorSimple(
                    etiqueta = if (b.origenCalculo == "DESDE_MANTENIMIENTO") "Desde un mantenimiento realizado"
                    else "Desde hoy",
                    opciones = listOf(
                        "DESDE_HOY" to "Desde hoy",
                        "DESDE_MANTENIMIENTO" to "Desde un mantenimiento realizado"
                    ),
                    seleccionado = b.origenCalculo,
                    enabled = editable
                ) { origen ->
                    onActualizar { it.copy(origenCalculo = origen, idReferencia = null) }
                }

                if (b.origenCalculo == "DESDE_HOY") {
                    Text(
                        "Datos actuales del vehículo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    DatoSuave("Fecha: ${estado.contexto?.fechaServidor?.take(10) ?: "—"}")
                    DatoSuave(
                        "Kilometraje: ${estado.contexto?.kmActual?.let { "%,d km".format(it).replace(',', ' ') } ?: "No registrado"}"
                    )
                } else {
                    Text(
                        "Selecciona el mantenimiento registrado",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface
                    )
                    if (refs.isEmpty()) {
                        Text(
                            "No hay mantenimientos registrados para este vehículo y servicio.",
                            color = scheme.error
                        )
                    } else {
                        val seleccion = refs.firstOrNull { it.id == b.idReferencia }
                        SelectorSimple(
                            etiqueta = seleccion?.let {
                                etiquetaMantenimiento(nombreServicio(it, tipos), it)
                            } ?: "Seleccionar mantenimiento",
                            opciones = refs.map {
                                it.id.toString() to etiquetaMantenimiento(nombreServicio(it, tipos), it)
                            },
                            seleccionado = b.idReferencia?.toString().orEmpty(),
                            enabled = editable
                        ) { id -> onActualizar { it.copy(idReferencia = id.toLongOrNull()) } }
                        Text(
                            "Se muestra el nombre del servicio, la fecha y el kilometraje del mantenimiento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    estado.errores["idReferencia"]?.let {
                        Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (CriterioAlerta.usaTiempo(b.criterio) || CriterioAlerta.usaKm(b.criterio)) {
                SeccionProgramar(paso++, "Intervalo", Icons.Outlined.Schedule) {
                    if (CriterioAlerta.usaTiempo(b.criterio)) {
                        Text(
                            "Intervalo de tiempo",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                b.cantidadTiempo,
                                { v -> onActualizar { it.copy(cantidadTiempo = v) } },
                                modifier = Modifier.weight(1f),
                                enabled = editable,
                                label = { Text("Cantidad") },
                                isError = estado.errores.containsKey("cantidadTiempo"),
                                supportingText = estado.errores["cantidadTiempo"]?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = coloresCampo()
                            )
                            SelectorSimple(
                                etiqueta = if (b.unidadTiempo == "SEMANAS") "Semanas" else "Meses",
                                opciones = listOf("SEMANAS" to "Semanas", "MESES" to "Meses"),
                                seleccionado = b.unidadTiempo,
                                enabled = editable,
                                modifier = Modifier.weight(1f)
                            ) { u -> onActualizar { it.copy(unidadTiempo = u) } }
                        }
                    }
                    if (CriterioAlerta.usaKm(b.criterio)) {
                        OutlinedTextField(
                            b.intervaloKm,
                            { v -> onActualizar { it.copy(intervaloKm = v) } },
                            label = { Text("Intervalo de kilometraje (km)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = editable,
                            isError = estado.errores.containsKey("intervaloKm"),
                            supportingText = estado.errores["intervaloKm"]?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = coloresCampo()
                        )
                    }
                }
            }

            TarjetaProximoMantenimiento(paso, estado.preview)
        }
    }
}

@Composable
private fun PantallaFormularioAlertaSinContexto(
    estado: com.mycar.app.features.alertas.viewmodel.EstadoProgramarAlerta,
    onVolver: () -> Unit,
    onRecargar: () -> Unit,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CabeceraProgramar(
                titulo = "Programar alerta",
                subtitulo = "Paso 1 de 3",
                onVolver = onVolver
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (estado.cargando) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = scheme.primary,
                    trackColor = scheme.surfaceVariant
                )
            }
            estado.mensaje?.let { AvisoProgramar(it, error = true) }
            BotonSecundario(texto = "Reintentar carga", onClick = onRecargar, enabled = true)
        }
    }
}

@Composable
private fun TarjetaProximoMantenimiento(
    numero: Int,
    preview: ResumenAlerta?
) {
    val scheme = MaterialTheme.colorScheme
    SeccionProgramar(numero, "Próximo mantenimiento", Icons.Outlined.EventAvailable) {
        if (preview == null) {
            Text("Completa los datos para ver la vista previa.", color = scheme.onSurfaceVariant)
        } else {
            preview.fechaObjetivo?.let { Text("Fecha objetivo: $it", color = scheme.onSurface) }
            preview.kmObjetivo?.let { Text("KM objetivo: $it km", color = scheme.onSurface) }
            if (preview.fechaObjetivo == null && preview.kmObjetivo == null) {
                Text("Faltan datos para calcular el próximo mantenimiento.", color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CabeceraProgramar(
    titulo: String,
    subtitulo: String,
    onVolver: (() -> Unit)? = null,
    volverEnabled: Boolean = true,
    icono: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    val oscuro = fondoOscuro()
    val brush = if (oscuro) {
        Brush.verticalGradient(listOf(scheme.surface, scheme.background))
    } else {
        Brush.verticalGradient(listOf(MyCarNavy, MyCarBlue, MyCarSky))
    }
    val tinta = if (oscuro) scheme.onSurface else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .background(brush)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 18.dp)
    ) {
        if (onVolver != null) {
            TextButton(
                onClick = onVolver,
                enabled = volverEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = tinta)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Volver")
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icono != null) {
                Surface(shape = CircleShape, color = tinta.copy(alpha = 0.16f)) {
                    Icon(
                        icono,
                        contentDescription = null,
                        tint = tinta,
                        modifier = Modifier.padding(10.dp).size(28.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tinta
                )
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tinta.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun SeccionProgramar(
    numero: Int,
    titulo: String,
    icono: ImageVector,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val oscuro = fondoOscuro()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        border = BorderStroke(1.dp, bordeSuave()),
        elevation = CardDefaults.cardElevation(defaultElevation = if (oscuro) 0.dp else 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (oscuro) scheme.primaryContainer else MyCarTile
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text(
                            numero.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (oscuro) scheme.onPrimaryContainer else MyCarBlue
                        )
                    }
                }
                Icon(icono, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            contenido()
        }
    }
}

@Composable
private fun BarraAcciones(contenido: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surface, shadowElevation = if (fondoOscuro()) 0.dp else 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = contenido
        )
    }
}

@Composable
private fun BotonPrimario(
    texto: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icono: ImageVector? = null,
    iconoFinal: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary
        )
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, fontWeight = FontWeight.SemiBold)
        if (iconoFinal != null) {
            Spacer(Modifier.width(8.dp))
            Icon(iconoFinal, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BotonSecundario(
    texto: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icono: ImageVector? = null
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, if (enabled) scheme.primary else scheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary)
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvisoProgramar(texto: String, error: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tinta = if (error) scheme.error else scheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = tinta.copy(alpha = 0.12f)
    ) {
        Text(texto, color = tinta, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DatoSuave(texto: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (fondoOscuro()) scheme.surfaceVariant else MyCarTile
    ) {
        Text(texto, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), color = scheme.onSurface)
    }
}

@Composable
private fun SelectorSimple(
    etiqueta: String,
    opciones: List<Pair<String, String>>,
    seleccionado: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box(modifier) {
        OutlinedButton(
            onClick = { abierto = true },
            enabled = enabled && opciones.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, bordeSuave()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface)
        ) {
            Text(
                etiqueta,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = scheme.primary)
        }
        DropdownMenu(
            expanded = abierto && enabled,
            onDismissRequest = { abierto = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            opciones.forEach { (codigo, nombre) ->
                DropdownMenuItem(
                    text = { Text(if (codigo == seleccionado) "✓ $nombre" else nombre) },
                    onClick = { abierto = false; onSelect(codigo) }
                )
            }
        }
    }
}

@Composable
private fun fondoOscuro(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
private fun bordeSuave(): Color =
    if (fondoOscuro()) MaterialTheme.colorScheme.outline else BordeAzulSuave

@Composable
private fun coloresCampo() = run {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = bordeSuave(),
        cursorColor = scheme.primary,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface.copy(alpha = 0.6f),
        errorBorderColor = scheme.error,
        errorLabelColor = scheme.error,
        errorSupportingTextColor = scheme.error,
        errorCursorColor = scheme.error
    )
}

private fun etiquetaCriterio(criterio: String): String = when (criterio) {
    "POR_TIEMPO" -> "Por tiempo"
    "POR_KILOMETRAJE" -> "Por kilometraje"
    "AMBOS" -> "Por tiempo y kilometraje"
    else -> criterio
}

private fun nombreServicio(ref: ReferenciaMantenimientoAlerta, tipos: List<TipoAlerta>): String {
    if (ref.codigoTipo == "OTRO") {
        return ref.servicioOtro?.takeIf { it.isNotBlank() } ?: "Otro servicio"
    }
    return tipos.firstOrNull { it.codigo == ref.codigoTipo }?.nombre
        ?: ref.codigoTipo.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private fun etiquetaMantenimiento(nombre: String, ref: ReferenciaMantenimientoAlerta): String {
    val fecha = formatearFechaCorta(ref.fecha)
    val km = ref.kmServicio?.let { "%,d km".format(it).replace(',', ' ') } ?: "— km"
    return "$nombre · $fecha · $km"
}

private fun formatearFechaCorta(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(iso.take(10))
