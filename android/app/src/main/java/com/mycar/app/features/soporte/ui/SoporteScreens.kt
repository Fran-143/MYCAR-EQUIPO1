package com.mycar.app.features.soporte.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    LaunchedEffect(estado.pantalla) {
        if (estado.pantalla == PantallaSoporte.SESION) onSesionInvalida()
    }
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
                    PantallaSoporte.DETALLE -> "Detalle de solicitud"
                    PantallaSoporte.EXITO -> "Solicitud enviada"
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
            PantallaSoporte.DETALLE -> DetalleSolicitud(estado, vm)
            PantallaSoporte.EXITO -> {
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
            PantallaSoporte.ERROR -> {
                Text(estado.mensaje ?: "No se pudo cargar el centro de soporte.", color = Color(0xFFC62828))
                SoatBotonSecundario("Reintentar", vm::cargarCentro)
            }
            PantallaSoporte.SESION -> Text("Sesión requerida.", color = MyCarMuted)
        }
        if (estado.proximamente) {
            AlertDialog(
                onDismissRequest = vm::cerrarProximamente,
                title = { Text("Próximamente") },
                text = { Text("El reporte de incidencias varias estará disponible en una entrega posterior.") },
                confirmButton = {
                    TextButton(onClick = vm::cerrarProximamente) { Text("Entendido") }
                }
            )
        }
    }
}

@Composable
private fun CentroSoporte(@Suppress("UNUSED_PARAMETER") estado: EstadoSoporte, vm: SoporteViewModel) {
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
        onClick = vm::abrirCorreccionVin
    )
    TarjetaSoporte(
        icono = Icons.Outlined.ReportProblem,
        titulo = "Reporte de incidencias varias",
        subtitulo = "Otros reportes y solicitudes",
        disponible = false,
        onClick = vm::abrirProximamente
    )
}

@Composable
private fun TarjetaSoporte(
    icono: ImageVector,
    titulo: String,
    subtitulo: String,
    disponible: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                    if (disponible) "Disponible" else "Próximamente",
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
        PestanaCorreccion.HISTORIAL -> ListaSolicitudes(estado, vm::abrirDetalle)
    }
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

@Composable
private fun ListaSolicitudes(estado: EstadoSoporte, onDetalle: (String) -> Unit) {
    if (estado.solicitudes.isEmpty()) {
        Text("Aún no tienes solicitudes. Si acabas de enviar una, vuelve a abrir Historial.", color = MyCarMuted)
        return
    }
    estado.solicitudes.forEach { s ->
        Card(
            Modifier.fillMaxWidth().clickable { onDetalle(s.codigo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.codigo, fontWeight = FontWeight.Bold, color = MyCarNavy)
                    ChipEstado(s.estado)
                }
                Text("Corrección de VIN", color = MyCarMuted)
                Text("Vehículo: ${s.placa}", color = MyCarNavy)
                s.creadoUtc?.let { Text(it.take(10), color = MyCarMuted, style = MaterialTheme.typography.bodySmall) }
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
        "APROBADA" -> Color(0xFF2E7D32)
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
