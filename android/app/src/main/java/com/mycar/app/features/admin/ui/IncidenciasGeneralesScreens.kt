package com.mycar.app.features.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.admin.domain.AdminGeneralesRepository
import com.mycar.app.features.admin.viewmodel.AdminGeneralesViewModel
import com.mycar.app.features.admin.viewmodel.PantallaAdminGenerales
import com.mycar.app.features.soporte.data.ContadoresIncidenciaDto
import com.mycar.app.features.soporte.domain.IncidenciaGeneralFormulario
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.soporte.ui.CabeceraDetalleSolicitud
import com.mycar.app.features.soporte.ui.CamposDetalleIncidenciaGeneral
import com.mycar.app.features.soporte.ui.ChipEstado
import com.mycar.app.features.soat.ui.SoatBotonPrincipal
import com.mycar.app.features.soat.ui.SoatBotonSecundario
import com.mycar.app.ui.theme.MyCarNavy

private val Navy = Color(0xFF12285F)
private val Teal = Color(0xFF007E98)
private val Mute = Color(0xFF718096)

@Composable
fun IncidenciasGeneralesRoute(
    repository: AdminGeneralesRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: AdminGeneralesViewModel = viewModel(factory = remember(repository) { AdminGeneralesViewModel.factory(repository) })
    val estado by vm.estado.collectAsState()
    BackHandler {
        when (estado.pantalla) {
            PantallaAdminGenerales.DETALLE -> vm.volverLista()
            PantallaAdminGenerales.TERMINAR, PantallaAdminGenerales.RECHAZAR -> vm.volverDetalle()
            PantallaAdminGenerales.CONFIRMACION -> vm.volverLista()
            else -> onVolver()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                when (estado.pantalla) {
                    PantallaAdminGenerales.DETALLE -> vm.volverLista()
                    PantallaAdminGenerales.TERMINAR, PantallaAdminGenerales.RECHAZAR -> vm.volverDetalle()
                    PantallaAdminGenerales.CONFIRMACION -> vm.volverLista()
                    else -> onVolver()
                }
            }) { Text("Volver", color = Navy) }
            Text(
                when (estado.pantalla) {
                    PantallaAdminGenerales.DETALLE -> "Detalle de incidencia"
                    PantallaAdminGenerales.TERMINAR -> "Resolver incidencia"
                    PantallaAdminGenerales.RECHAZAR -> "Rechazar incidencia"
                    PantallaAdminGenerales.CONFIRMACION -> "Incidencia cerrada"
                    else -> "Incidencias generales"
                },
                fontWeight = FontWeight.Bold,
                color = Navy,
                modifier = Modifier.weight(1f)
            )
        }
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (estado.cargando || estado.ocupado) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
        }
        when (estado.pantalla) {
            PantallaAdminGenerales.LISTA -> ListaIncidenciasGenerales(estado, vm)
            PantallaAdminGenerales.DETALLE -> DetalleIncidenciaGeneral(estado, vm)
            PantallaAdminGenerales.TERMINAR -> TerminarIncidenciaGeneral(estado, vm)
            PantallaAdminGenerales.RECHAZAR -> RechazarIncidenciaGeneral(estado, vm)
            PantallaAdminGenerales.CONFIRMACION -> ConfirmacionIncidenciaGeneral(estado, vm)
        }
    }
}

@Composable
private fun FilaContadores(c: ContadoresIncidenciaDto) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniContador("Pend.", c.pendientes, Color(0xFFF9A825))
        MiniContador("Acept.", c.aceptadas, Color(0xFF007E98))
        MiniContador("Proc.", c.enProceso, Color(0xFF1E88E5))
        MiniContador("Resuelt.", c.resueltas, Color(0xFF2E7D32))
        MiniContador("Rech.", c.rechazadas, Color(0xFFC62828))
    }
}

@Composable
private fun MiniContador(etiqueta: String, valor: Int, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.1f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$valor", fontWeight = FontWeight.Bold, color = color)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = Mute)
        }
    }
}

@Composable
private fun ListaIncidenciasGenerales(
    estado: com.mycar.app.features.admin.viewmodel.EstadoAdminGenerales,
    vm: AdminGeneralesViewModel
) {
    Text("Gestión de incidencias generales reportadas por usuarios.", color = Mute)
    FilaContadores(estado.contadores)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            null,
            "PENDIENTE",
            "ACEPTADA",
            "EN_PROCESO",
            "RESUELTA",
            "RECHAZADA"
        ).forEach { f ->
            FilterChip(
                selected = estado.filtroEstado == f,
                onClick = { vm.setFiltro(f) },
                label = {
                    Text(
                        f?.let { SoporteFormulario.etiquetaEstado(it) } ?: "Todas",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
    OutlinedTextField(
        value = estado.busqueda,
        onValueChange = vm::setBusqueda,
        label = { Text("Buscar por código o asunto") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
    if (estado.lista.isEmpty() && !estado.cargando) {
        Text("No hay incidencias para este filtro.", color = Mute)
    }
    estado.lista.forEach { s ->
        Card(
            Modifier.fillMaxWidth().clickable { vm.abrirDetalle(s.codigo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.codigo,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ChipEstado(s.estado)
                s.asunto?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                s.categoria?.let {
                    Text(
                        IncidenciaGeneralFormulario.etiquetaCategoria(it),
                        color = Mute,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                s.creadoUtc?.let {
                    Text(it.take(10), color = Mute, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DetalleIncidenciaGeneral(
    estado: com.mycar.app.features.admin.viewmodel.EstadoAdminGenerales,
    vm: AdminGeneralesViewModel
) {
    val d = estado.detalle ?: return
    val cerrado = d.estado in setOf("RESUELTA", "RECHAZADA")
    val puedeRechazar = d.estado in setOf("PENDIENTE", "ACEPTADA", "EN_PROCESO")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CabeceraDetalleSolicitud(d.codigo, d.estado)
            CamposDetalleIncidenciaGeneral(
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
    }
    if (!cerrado) {
        val mostrarObs = d.estado == "PENDIENTE" || d.estado == "ACEPTADA"
        if (mostrarObs) {
            OutlinedTextField(
                value = estado.observacionAccion,
                onValueChange = vm::editarObservacionAccion,
                label = { Text("Observación interna (opcional)") },
                supportingText = { Text("${estado.observacionAccion.length}/500") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )
        }
        when (d.estado) {
            "PENDIENTE" -> {
                Button(
                    onClick = vm::aceptar,
                    enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aceptar solicitud", fontWeight = FontWeight.SemiBold)
                }
            }
            "ACEPTADA" -> {
                Button(
                    onClick = vm::iniciarAtencion,
                    enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar atención", fontWeight = FontWeight.SemiBold)
                }
            }
            "EN_PROCESO" -> {
                Button(
                    onClick = vm::abrirTerminar,
                    enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Terminar y responder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (puedeRechazar) {
            Button(
                onClick = vm::abrirRechazar,
                enabled = !estado.ocupado,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Icon(Icons.Filled.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rechazar incidencia", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TerminarIncidenciaGeneral(
    estado: com.mycar.app.features.admin.viewmodel.EstadoAdminGenerales,
    vm: AdminGeneralesViewModel
) {
    Text(
        "Escribe la respuesta que verá el usuario al cerrar el caso.",
        color = Mute
    )
    Text("Requiere MFA confirmado en los últimos 10 minutos.", color = Mute, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = estado.textoRespuesta,
        onValueChange = vm::editarTextoRespuesta,
        label = { Text("Respuesta al usuario *") },
        supportingText = { Text("${estado.textoRespuesta.length}/3000") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        minLines = 4
    )
    SoatBotonPrincipal("Enviar respuesta y cerrar", vm::confirmarTerminar, enabled = !estado.ocupado)
    SoatBotonSecundario("Cancelar", vm::volverDetalle)
}

@Composable
private fun RechazarIncidenciaGeneral(
    estado: com.mycar.app.features.admin.viewmodel.EstadoAdminGenerales,
    vm: AdminGeneralesViewModel
) {
    Text("Indica el motivo del rechazo.", color = Mute)
    Text("Requiere MFA confirmado en los últimos 10 minutos.", color = Mute, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = estado.motivoRechazo,
        onValueChange = vm::editarMotivoRechazo,
        label = { Text("Motivo del rechazo *") },
        supportingText = { Text("${estado.motivoRechazo.length}/500") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        minLines = 3
    )
    SoatBotonPrincipal("Confirmar rechazo", vm::confirmarRechazo, enabled = !estado.ocupado)
    SoatBotonSecundario("Cancelar", vm::volverDetalle)
}

@Composable
private fun ConfirmacionIncidenciaGeneral(
    estado: com.mycar.app.features.admin.viewmodel.EstadoAdminGenerales,
    vm: AdminGeneralesViewModel
) {
    val d = estado.detalle ?: return
    val resuelta = d.estado == "RESUELTA"
    Text(
        "La incidencia ${d.codigo} fue ${SoporteFormulario.etiquetaEstado(d.estado).lowercase()}.",
        fontWeight = FontWeight.Bold,
        color = if (resuelta) Color(0xFF2E7D32) else Color(0xFFC62828)
    )
    if (resuelta) {
        d.respuestaAdmin?.let { FilaAdminGenerales("Respuesta enviada", it) }
    } else {
        d.motivoRechazo?.let { FilaAdminGenerales("Motivo de rechazo", it) }
    }
    SoatBotonPrincipal("Volver al listado", vm::volverLista)
}

@Composable
private fun FilaAdminGenerales(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, color = Mute, style = MaterialTheme.typography.labelMedium)
        Text(valor, fontWeight = FontWeight.Medium, color = Navy)
    }
}