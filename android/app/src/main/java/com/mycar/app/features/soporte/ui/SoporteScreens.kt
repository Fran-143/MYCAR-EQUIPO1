package com.mycar.app.features.soporte.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    PantallaSoporte.DETALLE -> "Detalle de solicitud"
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
            PantallaSoporte.DETALLE -> DetalleSolicitud(estado, vm)
            PantallaSoporte.EXITO -> {
                Text(
                    "Solicitud registrada. Tu solicitud de corrección de VIN ha sido registrada correctamente.",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text("Código: ${estado.codigoExito}", color = MyCarNavy)
                ChipEstado("PENDIENTE")
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
    Text("¿En qué podemos ayudarte?", color = MyCarMuted)
    TarjetaSoporte(
        titulo = "Corrección de VIN",
        subtitulo = "Solicita la corrección del VIN de tu vehículo",
        disponible = true,
        onClick = vm::abrirCorreccionVin
    )
    TarjetaSoporte(
        titulo = "Reporte de incidencias varias",
        subtitulo = "Otros reportes y solicitudes",
        disponible = false,
        onClick = vm::abrirProximamente
    )
}

@Composable
private fun TarjetaSoporte(titulo: String, subtitulo: String, disponible: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold, color = MyCarNavy)
            Text(subtitulo, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                if (disponible) "Disponible" else "Próximamente",
                color = if (disponible) MyCarTeal else Color(0xFF718096),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ModuloCorreccion(estado: EstadoSoporte, vm: SoporteViewModel) {
    TabRow(selectedTabIndex = if (estado.pestana == PestanaCorreccion.NUEVA) 0 else 1) {
        Tab(
            selected = estado.pestana == PestanaCorreccion.NUEVA,
            onClick = { vm.setPestana(PestanaCorreccion.NUEVA) },
            text = { Text("Nueva solicitud") }
        )
        Tab(
            selected = estado.pestana == PestanaCorreccion.HISTORIAL,
            onClick = { vm.setPestana(PestanaCorreccion.HISTORIAL) },
            text = { Text("Historial de solicitudes") }
        )
    }
    when (estado.pestana) {
        PestanaCorreccion.NUEVA -> FormularioNueva(estado, vm)
        PestanaCorreccion.HISTORIAL -> ListaSolicitudes(estado, vm::abrirDetalle)
    }
}

@Composable
private fun FormularioNueva(estado: EstadoSoporte, vm: SoporteViewModel) {
    Text("Selecciona el vehículo al que deseas realizar la corrección.", color = MyCarMuted)
    if (estado.vehiculos.isEmpty()) {
        Text("No tienes vehículos activos registrados.", color = MyCarMuted)
        return
    }
    estado.vehiculos.forEach { v ->
        val sel = estado.idVehiculoSel == v.idVehiculo
        Card(
            Modifier
                .fillMaxWidth()
                .then(
                    if (sel) Modifier.border(2.dp, MyCarTeal, RoundedCornerShape(14.dp))
                    else Modifier
                )
                .clickable { vm.seleccionarVehiculo(v.idVehiculo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.placa, fontWeight = FontWeight.Bold, color = MyCarNavy)
                Text("${v.marca} ${v.modelo}", color = MyCarMuted)
                Text(
                    if (v.tieneTive) "Con TIVE" else "Sin TIVE — no elegible aún",
                    color = if (v.tieneTive) Color(0xFF2E7D32) else Color(0xFFC62828),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
    estado.errores["idVehiculo"]?.let { Text(it, color = Color(0xFFC62828)) }
    val veh = estado.vehiculos.find { it.idVehiculo == estado.idVehiculoSel } ?: return
    Spacer(Modifier.height(4.dp))
    Text("Vehículo seleccionado", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("${veh.marca} ${veh.modelo}", fontWeight = FontWeight.SemiBold, color = MyCarNavy)
            Text(veh.placa, color = MyCarMuted)
        }
    }
    if (!veh.tieneTive) {
        Text(
            "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN.",
            color = Color(0xFFC62828)
        )
        return
    }
    CampoSoloLectura("VIN registrado actualmente", veh.numeroVin.orEmpty().ifBlank { "Sin VIN" })
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
    Text("Evidencia opcional. " + SoporteFormulario.AYUDA_EVIDENCIA, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
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
}

@Composable
private fun ListaSolicitudes(estado: EstadoSoporte, onDetalle: (String) -> Unit) {
    if (estado.solicitudes.isEmpty()) {
        Text("Aún no tienes solicitudes. Si acabas de enviar una, desliza o vuelve a abrir Historial.", color = MyCarMuted)
        return
    }
    estado.solicitudes.forEach { s ->
        Card(
            Modifier.fillMaxWidth().clickable { onDetalle(s.codigo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
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
        colors = CardDefaults.cardColors(containerColor = Color.White)
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

@Composable
private fun FilaSoporte(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, color = MyCarMuted, style = MaterialTheme.typography.labelMedium)
        Text(valor, fontWeight = FontWeight.Medium, color = MyCarNavy)
    }
}
