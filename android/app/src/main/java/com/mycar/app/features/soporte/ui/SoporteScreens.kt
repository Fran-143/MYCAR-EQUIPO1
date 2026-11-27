package com.mycar.app.features.soporte.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.soporte.domain.IncidenciaGeneralFormulario
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.soporte.domain.SoporteRepository
import com.mycar.app.features.soporte.viewmodel.*
import com.mycar.app.features.soat.ui.SoatBotonPrincipal
import com.mycar.app.features.soat.ui.SoatBotonSecundario
import com.mycar.app.features.soat.ui.SoatSeccionAdjunto
import com.mycar.app.features.soat.ui.leerUriSoat
import com.mycar.app.features.tive.ui.CampoSoloLectura
import com.mycar.app.features.tive.ui.CampoValidado
import com.mycar.app.features.vehiculos.domain.ValidadorVin
import com.mycar.app.ui.theme.*

@Composable
fun SoporteRoute(
    repository: SoporteRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: SoporteViewModel = viewModel(
        key = "soporte",
        factory = remember(repository) {
            SoporteViewModel.factory(repository) { uri -> leerUriSoat(context, uri) }
        }
    )
    val estado by vm.estado.collectAsState()
    // No expulsar a inicio en automático: si la API falla por red/cold start, mostrar error
    // y Reintentar. Solo SESIÓN con acción explícita (botón) invalida la sesión.
    val salir = estado.pantalla in setOf(
        PantallaSoporte.CENTRO, PantallaSoporte.CARGANDO, PantallaSoporte.ERROR, PantallaSoporte.SESION
    )
    BackHandler {
        if (salir) onVolver() else vm.volver()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MyCarBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (salir) onVolver() else vm.volver() }) { Text("Volver") }
            Text(
                when (estado.pantalla) {
                    PantallaSoporte.CENTRO -> "Centro de soporte"
                    PantallaSoporte.CORRECCION -> "Corrección de VIN"
                    PantallaSoporte.FORMULARIO -> "Nueva solicitud"
                    PantallaSoporte.INCIDENCIA_FORM -> "Reportar incidencia"
                    PantallaSoporte.HISTORIAL -> "Historial de solicitudes"
                    PantallaSoporte.DETALLE -> "Detalle de solicitud"
                    PantallaSoporte.EXITO, PantallaSoporte.EXITO_INCIDENCIA -> "Solicitud registrada"
                    else -> "Soporte"
                },
                fontWeight = FontWeight.Bold,
                color = MyCarNavy,
                modifier = Modifier.weight(1f)
            )
        }
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        when (estado.pantalla) {
            PantallaSoporte.CARGANDO -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
            PantallaSoporte.CENTRO -> CentroSoporte(estado, vm)
            PantallaSoporte.CORRECCION -> ModuloCorreccion(estado, vm)
            PantallaSoporte.FORMULARIO -> FormularioSolicitud(estado, vm)
            PantallaSoporte.INCIDENCIA_FORM -> FormularioIncidenciaGeneral(estado, vm)
            PantallaSoporte.HISTORIAL -> HistorialUnificado(estado, vm)
            PantallaSoporte.DETALLE -> DetalleSolicitud(estado, vm)
            PantallaSoporte.EXITO -> ExitoVin(estado, vm)
            PantallaSoporte.EXITO_INCIDENCIA -> ExitoIncidencia(estado, vm)
            PantallaSoporte.ERROR -> {
                Text(estado.mensaje ?: "No se pudo cargar el centro de soporte.", color = Color(0xFFC62828))
                SoatBotonPrincipal("Reintentar", vm::cargarCentro)
                SoatBotonSecundario("Volver", onVolver)
            }
            PantallaSoporte.SESION -> {
                Text(
                    estado.mensaje
                        ?: "No se pudo validar la sesión para Soporte. Reintenta; si el resto de la app funciona, no hace falta cerrar sesión.",
                    color = MyCarMuted
                )
                SoatBotonPrincipal("Reintentar", vm::cargarCentro)
                SoatBotonSecundario("Ir a inicio de sesión", onSesionInvalida)
            }
        }
    }
}

@Composable
private fun ExitoVin(estado: EstadoSoporte, vm: SoporteViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                Text(
                    "Solicitud registrada correctamente",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
            Text("Código: ${estado.codigoExito}", color = MyCarNavy, fontWeight = FontWeight.SemiBold)
            ChipEstado("PENDIENTE")
        }
    }
    SoatBotonPrincipal(
        "Ver historial de solicitudes",
        onClick = { vm.abrirCorreccionVin(PestanaCorreccion.HISTORIAL) }
    )
    SoatBotonSecundario("Volver a Soporte", vm::cargarCentro)
}

@Composable
private fun ExitoIncidencia(estado: EstadoSoporte, vm: SoporteViewModel) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
        Text("Solicitud registrada", fontWeight = FontWeight.Bold, color = MyCarNavy, style = MaterialTheme.typography.titleLarge)
        Text(
            "Hemos recibido tu incidencia. Puedes consultar su estado desde el historial de solicitudes.",
            color = MyCarMuted
        )
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Código: ${estado.codigoExito}", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                Text(
                    "Categoría: ${IncidenciaGeneralFormulario.etiquetaCategoria(estado.categoriaExito)}",
                    color = MyCarMuted
                )
                Text("Asunto: ${estado.asuntoExito.orEmpty()}", color = MyCarMuted)
                estado.fechaExito?.let {
                    Text("Fecha: ${it.take(16).replace('T', ' ')}", color = MyCarMuted)
                }
                ChipEstado("PENDIENTE")
            }
        }
    }
    SoatBotonPrincipal("Ver historial de solicitudes", onClick = vm::abrirHistorialUnificado)
    SoatBotonSecundario("Volver a Soporte", vm::cargarCentro)
}

@Composable
private fun CentroSoporte(estado: EstadoSoporte, vm: SoporteViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(44.dp).background(MyCarTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SupportAgent, null, tint = MyCarTeal, modifier = Modifier.size(24.dp))
        }
        Text("¿En qué podemos ayudarte?", color = MyCarMuted)
    }
    TarjetaSoporte(
        icono = Icons.Outlined.EditNote,
        titulo = "Corrección de VIN",
        subtitulo = "Solicita la corrección del VIN de tu vehículo",
        disponible = true,
        accion = "Crear solicitud",
        onClick = vm::abrirCorreccionVin
    )
    TarjetaSoporte(
        icono = Icons.Outlined.ReportProblem,
        titulo = "Incidencias generales",
        subtitulo = "Reporta problemas relacionados con el funcionamiento de MyCar.",
        disponible = true,
        accion = "Crear solicitud",
        onClick = vm::abrirIncidenciaGeneral
    )
    TarjetaSoporte(
        icono = Icons.Outlined.History,
        titulo = "Historial de solicitudes",
        subtitulo = if (estado.tieneSolicitudes) {
            "Consulta el estado de tus solicitudes de soporte."
        } else {
            "Aún no tienes solicitudes registradas."
        },
        disponible = estado.tieneSolicitudes,
        accion = if (estado.tieneSolicitudes) "Ver historial" else "Sin solicitudes",
        onClick = vm::abrirHistorialUnificado
    )
}

@Composable
private fun TarjetaSoporte(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    disponible: Boolean,
    accion: String,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = disponible, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(
                    if (disponible) MyCarBlue.copy(alpha = 0.12f) else Color(0xFFF0F2F5),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icono,
                    null,
                    tint = if (disponible) MyCarBlue else Color(0xFF9AA5B5),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                Text(subtitulo, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    accion,
                    color = if (disponible) MyCarTeal else Color(0xFF718096),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (disponible) {
                Icon(Icons.Outlined.ArrowForwardIos, null, tint = MyCarMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ModuloCorreccion(estado: EstadoSoporte, vm: SoporteViewModel) {
    TabRow(selectedTabIndex = if (estado.pestana == PestanaCorreccion.NUEVA) 0 else 1) {
        Tab(
            selected = estado.pestana == PestanaCorreccion.NUEVA,
            onClick = { vm.setPestana(PestanaCorreccion.NUEVA) },
            text = { Text("Nueva solicitud") },
            icon = { Icon(Icons.Outlined.EditNote, null, modifier = Modifier.size(18.dp)) }
        )
        Tab(
            selected = estado.pestana == PestanaCorreccion.HISTORIAL,
            onClick = { vm.setPestana(PestanaCorreccion.HISTORIAL) },
            text = { Text("Historial") },
            icon = { Icon(Icons.Outlined.History, null, modifier = Modifier.size(18.dp)) }
        )
    }
    when (estado.pestana) {
        PestanaCorreccion.NUEVA -> ListaVehiculosSoporte(estado, vm)
        PestanaCorreccion.HISTORIAL -> ListaSolicitudes(
            solicitudes = estado.solicitudes.filter { it.esCorreccionVin },
            onDetalle = { vm.abrirDetalle(it, PantallaSoporte.CORRECCION) }
        )
    }
}

@Composable
private fun HistorialUnificado(estado: EstadoSoporte, vm: SoporteViewModel) {
    Text("Consulta el estado de tus solicitudes de soporte.", color = MyCarMuted)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = estado.filtroTipoHistorial == FiltroTipoHistorial.TODAS,
            onClick = { vm.setFiltroTipoHistorial(FiltroTipoHistorial.TODAS) },
            label = { Text("Todas") }
        )
        FilterChip(
            selected = estado.filtroTipoHistorial == FiltroTipoHistorial.VIN,
            onClick = { vm.setFiltroTipoHistorial(FiltroTipoHistorial.VIN) },
            label = { Text("Corrección de VIN") }
        )
        FilterChip(
            selected = estado.filtroTipoHistorial == FiltroTipoHistorial.GENERALES,
            onClick = { vm.setFiltroTipoHistorial(FiltroTipoHistorial.GENERALES) },
            label = { Text("Incidencias generales") }
        )
    }
    val estados = when (estado.filtroTipoHistorial) {
        FiltroTipoHistorial.VIN -> listOf(null, "PENDIENTE", "EN_REVISION", "APROBADA", "RECHAZADA")
        FiltroTipoHistorial.GENERALES -> listOf(null, "PENDIENTE", "ACEPTADA", "EN_PROCESO", "RESUELTA", "RECHAZADA")
        FiltroTipoHistorial.TODAS -> listOf(
            null, "PENDIENTE", "EN_REVISION", "ACEPTADA", "EN_PROCESO", "APROBADA", "RESUELTA", "RECHAZADA"
        )
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        estados.forEach { est ->
            FilterChip(
                selected = estado.filtroEstadoHistorial == est,
                onClick = { vm.setFiltroEstadoHistorial(est) },
                label = { Text(est?.let { SoporteFormulario.etiquetaEstado(it) } ?: "Todos los estados") }
            )
        }
    }
    ListaSolicitudes(
        solicitudes = estado.solicitudesFiltradas,
        onDetalle = { vm.abrirDetalle(it, PantallaSoporte.HISTORIAL) }
    )
}

@Composable
private fun ListaVehiculosSoporte(estado: EstadoSoporte, vm: SoporteViewModel) {
    Text("Selecciona el vehículo para abrir el formulario de solicitud.", color = MyCarMuted)
    if (estado.vehiculos.isEmpty()) {
        Text("No tienes vehículos activos registrados.", color = MyCarMuted)
        return
    }
    estado.vehiculos.forEach { v ->
        Card(
            Modifier
                .fillMaxWidth()
                .clickable { vm.seleccionarVehiculo(v.idVehiculo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(42.dp).background(MyCarTeal.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DirectionsCar, null, tint = MyCarTeal, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v.placa, fontWeight = FontWeight.Bold, color = MyCarNavy)
                    Text("${v.marca} ${v.modelo}", color = MyCarMuted)
                    Text(
                        "VIN: ${v.numeroVin?.takeIf { it.isNotBlank() } ?: "Sin VIN"}",
                        color = MyCarNavy,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (v.tieneTive) "Con TIVE · tocar para continuar" else "Sin TIVE — no elegible aún",
                        color = if (v.tieneTive) Color(0xFF2E7D32) else Color(0xFFC62828),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(Icons.Outlined.ArrowForwardIos, null, tint = MyCarMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
    estado.errores["idVehiculo"]?.let { Text(it, color = Color(0xFFC62828)) }
}

@Composable
private fun FormularioSolicitud(estado: EstadoSoporte, vm: SoporteViewModel) {
    val veh = estado.vehiculos.find { it.idVehiculo == estado.idVehiculoSel }
    if (veh == null) {
        Text("Selecciona un vehículo para continuar.", color = MyCarMuted)
        SoatBotonSecundario("Volver a vehículos", vm::volverAListaVehiculos)
        return
    }
    Text("Completa los datos de tu solicitud.", color = MyCarMuted)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(42.dp).background(MyCarBlue.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.DirectionsCar, null, tint = MyCarBlue, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Vehículo seleccionado", color = MyCarMuted, style = MaterialTheme.typography.labelMedium)
                Text("${veh.marca} ${veh.modelo}", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                Text(veh.placa, color = MyCarMuted)
            }
        }
    }
    CampoSoloLectura("VIN registrado actualmente", veh.numeroVin.orEmpty().ifBlank { "Sin VIN" })
    if (estado.errores["numeroVin"] != null) {
        Text(estado.errores.getValue("numeroVin"), color = Color(0xFFC62828))
    }
    if (!veh.tieneTive) {
        Text(
            "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN.",
            color = Color(0xFFC62828)
        )
        SoatBotonSecundario("Volver a vehículos", vm::volverAListaVehiculos)
        return
    }
    if (veh.numeroVin.isNullOrBlank()) {
        Text(
            "Este vehículo no tiene VIN registrado. Completa el VIN o registra la TIVE primero.",
            color = Color(0xFFC62828)
        )
        SoatBotonSecundario("Volver a vehículos", vm::volverAListaVehiculos)
        return
    }
    CampoValidado(
        etiqueta = "VIN correcto solicitado *",
        valor = estado.vinPropuesto,
        placeholder = ValidadorVin.PLACEHOLDER,
        ayuda = ValidadorVin.AYUDA,
        error = estado.errores["vinPropuesto"],
        ok = estado.hintsOk["vinPropuesto"],
        contador = ValidadorVin.contador(estado.vinPropuesto),
        onChange = { vm.actualizarCampo(vin = it, campo = "vinPropuesto") }
    )
    CampoValidado(
        etiqueta = "Motivo de la solicitud *",
        valor = estado.motivo,
        placeholder = SoporteFormulario.PH_MOTIVO,
        error = estado.errores["motivo"],
        contador = SoporteFormulario.contadorMotivo(estado.motivo),
        minLines = 3,
        onChange = { vm.actualizarCampo(motivo = it, campo = "motivo") }
    )
    Text(
        "Evidencia opcional. " + SoporteFormulario.AYUDA_EVIDENCIA,
        color = MyCarMuted,
        style = MaterialTheme.typography.bodySmall
    )
    SoatSeccionAdjunto(
        adjunto = estado.adjunto,
        error = estado.errores["evidencia"],
        enabled = !estado.ocupado,
        onAdjunto = vm::setAdjunto,
        estiloPdf = true,
        etiqueta = "Evidencia de respaldo (opcional)",
        subtituloVacio = "JPG, PNG o PDF (máx. 10 MB) — opcional"
    )
    if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
    SoatBotonPrincipal(
        "Enviar solicitud",
        onClick = vm::enviar,
        enabled = !estado.ocupado && !estado.formularioIncompleto
    )
    SoatBotonSecundario("Volver a vehículos", vm::volverAListaVehiculos)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioIncidenciaGeneral(estado: EstadoSoporte, vm: SoporteViewModel) {
    Text(
        "Cuéntanos qué problema encontraste para que el equipo de MyCar pueda revisarlo.",
        color = MyCarMuted
    )
    Text("Tema de la incidencia *", fontWeight = FontWeight.Medium, color = MyCarNavy)
    var expanded by remember { mutableStateOf(false) }
    val etiquetaSel = estado.categoria?.let { IncidenciaGeneralFormulario.etiquetaCategoria(it) }
        ?: "Selecciona un tema"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = etiquetaSel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = estado.errores["categoria"] != null
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IncidenciaGeneralFormulario.CATEGORIAS.forEach { (cod, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        vm.actualizarIncidencia(categoria = cod, campo = "categoria")
                        expanded = false
                    }
                )
            }
        }
    }
    estado.errores["categoria"]?.let { Text(it, color = Color(0xFFC62828)) }

    if (estado.categoria.equals("OTROS", true)) {
        CampoValidado(
            etiqueta = "Especifica el tema *",
            valor = estado.temaOtro,
            placeholder = "Problema con una funcionalidad nueva",
            error = estado.errores["temaOtro"],
            onChange = { vm.actualizarIncidencia(temaOtro = it, campo = "temaOtro") }
        )
    }

    if (estado.sugerirVin) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MyCarBlue)
                    Text(
                        "Para corregir el VIN de tu vehículo utiliza la opción Corrección de VIN.",
                        color = MyCarNavy
                    )
                }
                TextButton(onClick = { vm.abrirCorreccionVin() }) {
                    Text("Ir a Corrección de VIN")
                }
            }
        }
    }

    CampoValidado(
        etiqueta = "Asunto *",
        valor = estado.asunto,
        placeholder = "Escribe un título breve del problema",
        error = estado.errores["asunto"],
        contador = IncidenciaGeneralFormulario.contadorAsunto(estado.asunto),
        onChange = { vm.actualizarIncidencia(asunto = it, campo = "asunto") }
    )
    CampoValidado(
        etiqueta = "Describe lo ocurrido *",
        valor = estado.descripcion,
        placeholder = "Explica qué sucedió, en qué pantalla y qué estabas intentando hacer.",
        error = estado.errores["descripcion"],
        contador = IncidenciaGeneralFormulario.contadorDescripcion(estado.descripcion),
        minLines = 5,
        onChange = { vm.actualizarIncidencia(descripcion = it, campo = "descripcion") }
    )
    Text("Adjuntar evidencia", fontWeight = FontWeight.Medium, color = MyCarNavy)
    Text(
        "Puedes adjuntar una imagen o documento que ayude a explicar el problema.",
        color = MyCarMuted,
        style = MaterialTheme.typography.bodySmall
    )
    SoatSeccionAdjunto(
        adjunto = estado.adjunto,
        error = estado.errores["evidencia"],
        enabled = !estado.ocupado,
        onAdjunto = vm::setAdjunto,
        estiloPdf = true,
        etiqueta = "Adjuntar archivo",
        subtituloVacio = "JPG, PNG o PDF (máx. 10 MB) — opcional"
    )
    if (estado.ocupado) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
    SoatBotonPrincipal(
        "Enviar solicitud",
        onClick = vm::enviarIncidencia,
        enabled = !estado.ocupado && !estado.incidenciaIncompleta
    )
    SoatBotonSecundario("Cancelar", vm::cargarCentro)
}

@Composable
private fun ListaSolicitudes(
    solicitudes: List<com.mycar.app.features.soporte.data.SolicitudResumenDto>,
    onDetalle: (String) -> Unit
) {
    if (solicitudes.isEmpty()) {
        Text("Aún no tienes solicitudes registradas.", color = MyCarMuted)
        return
    }
    solicitudes.forEach { s ->
        Card(
            Modifier.fillMaxWidth().clickable { onDetalle(s.codigo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (s.esIncidenciaGeneral) Icons.Outlined.ReportProblem else Icons.Outlined.EditNote,
                    null,
                    tint = MyCarBlue,
                    modifier = Modifier.size(22.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.codigo, fontWeight = FontWeight.Bold, color = MyCarNavy)
                        ChipEstado(s.estado)
                    }
                    Text(SoporteFormulario.etiquetaTipo(s.tipo), color = MyCarMuted)
                    if (s.esIncidenciaGeneral) {
                        Text(
                            IncidenciaGeneralFormulario.etiquetaCategoria(s.categoria),
                            color = MyCarMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        s.asunto?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MyCarNavy)
                        }
                    } else {
                        Text("Vehículo: ${s.placa.orEmpty()}", color = MyCarNavy)
                        s.asunto?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    s.creadoUtc?.let {
                        Text(it.take(10), color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.Outlined.ArrowForwardIos, null, tint = MyCarMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun DetalleSolicitud(estado: EstadoSoporte, vm: SoporteViewModel) {
    val d = estado.detalle ?: return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CabeceraDetalleSolicitud(d.codigo, d.estado)
            if (d.esIncidenciaGeneral) {
                CamposDetalleIncidenciaGeneral(
                    d = d,
                    mostrarUsuario = false,
                    evidenciaBytes = estado.evidenciaBytes,
                    evidenciaCargando = estado.evidenciaCargando,
                    evidenciaError = estado.evidenciaError,
                    evidenciaRevelada = estado.evidenciaRevelada,
                    onRevelarEvidencia = vm::revelarEvidencia,
                    onOcultarEvidencia = vm::ocultarEvidencia
                )
            } else {
                CamposDetalleSolicitudVin(
                    d = d,
                    mostrarUsuario = true,
                    evidenciaBytes = estado.evidenciaBytes,
                    evidenciaCargando = estado.evidenciaCargando,
                    evidenciaError = estado.evidenciaError,
                    evidenciaRevelada = estado.evidenciaRevelada,
                    onRevelarEvidencia = vm::revelarEvidencia,
                    onOcultarEvidencia = vm::ocultarEvidencia
                )
            }
            if (d.eventos.isNotEmpty()) {
                Text("Seguimiento", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
                d.eventos.forEach { e ->
                    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            SoporteFormulario.etiquetaEstado(e.estadoNuevo ?: ""),
                            fontWeight = FontWeight.SemiBold,
                            color = MyCarNavy
                        )
                        e.observacion?.takeIf { it.isNotBlank() }?.let { Text(it, color = MyCarMuted) }
                        e.ocurridoUtc?.let {
                            Text(
                                it.take(16).replace('T', ' '),
                                color = MyCarMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChipEstado(estado: String) {
    val color = when (estado.uppercase()) {
        "PENDIENTE" -> Color(0xFFF9A825)
        "EN_REVISION", "EN REVISIÓN" -> Color(0xFF1E88E5)
        "ACEPTADA" -> Color(0xFF007E98)
        "EN_PROCESO", "EN PROCESO" -> Color(0xFF1E88E5)
        "APROBADA", "RESUELTA" -> Color(0xFF2E7D32)
        "RECHAZADA" -> Color(0xFFC62828)
        else -> Color(0xFF718096)
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            SoporteFormulario.etiquetaEstado(estado),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
