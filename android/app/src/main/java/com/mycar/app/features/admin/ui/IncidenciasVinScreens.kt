package com.mycar.app.features.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.admin.domain.AdminVinRepository
import com.mycar.app.features.admin.viewmodel.AdminVinViewModel
import com.mycar.app.features.admin.viewmodel.PantallaAdminVin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.soporte.ui.CabeceraDetalleSolicitud
import com.mycar.app.features.soporte.ui.CamposDetalleSolicitudVin
import com.mycar.app.features.soporte.ui.ChipEstado
import com.mycar.app.features.soat.ui.SoatBotonPrincipal
import com.mycar.app.features.soat.ui.SoatBotonSecundario

private val Teal = Color(0xFF007E98)

@Composable
fun IncidenciasVinRoute(
    repository: AdminVinRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val vm: AdminVinViewModel = viewModel(factory = remember(repository) { AdminVinViewModel.factory(repository) })
    val estado by vm.estado.collectAsState()
    BackHandler {
        when (estado.pantalla) {
            PantallaAdminVin.DETALLE -> vm.volverLista()
            PantallaAdminVin.CONFIRMACION -> vm.volverLista()
            else -> onVolver()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                when (estado.pantalla) {
                    PantallaAdminVin.DETALLE, PantallaAdminVin.CONFIRMACION -> vm.volverLista()
                    else -> onVolver()
                }
            }) { Text("Volver", color = scheme.onSurface) }
            Text(
                when (estado.pantalla) {
                    PantallaAdminVin.DETALLE -> "Detalle de solicitud"
                    PantallaAdminVin.RESOLVER -> "Resolver solicitud"
                    PantallaAdminVin.CONFIRMACION -> "Solicitud resuelta"
                    else -> "Incidencias de VIN"
                },
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (estado.cargando || estado.ocupado) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
        }
        when (estado.pantalla) {
            PantallaAdminVin.LISTA -> ListaIncidencias(estado, vm)
            PantallaAdminVin.DETALLE -> DetalleIncidencia(estado, vm)
            PantallaAdminVin.RESOLVER -> ResolverIncidencia(estado, vm)
            PantallaAdminVin.CONFIRMACION -> ConfirmacionResolucion(estado, vm)
        }
    }
}

@Composable
private fun ListaIncidencias(estado: com.mycar.app.features.admin.viewmodel.EstadoAdminVin, vm: AdminVinViewModel) {
    val scheme = MaterialTheme.colorScheme
    Text("Gestión de solicitudes de corrección de VIN.", color = scheme.onSurfaceVariant)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(null, "PENDIENTE", "EN_REVISION", "APROBADA", "RECHAZADA").forEach { f ->
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
        label = { Text("Buscar por código o placa") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
    SoatBotonSecundario("Buscar", onClick = vm::cargarLista)
    if (estado.lista.isEmpty() && !estado.cargando) {
        Text("No hay incidencias para este filtro.", color = scheme.onSurfaceVariant)
    }
    estado.lista.forEach { s ->
        Card(
            Modifier.fillMaxWidth().clickable { vm.abrirDetalle(s.codigo) },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.codigo,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ChipEstado(s.estado)
                Text("Placa: ${s.placa.orEmpty()}", color = scheme.onSurfaceVariant)
                s.creadoUtc?.let {
                    Text(it.take(10), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DetalleIncidencia(estado: com.mycar.app.features.admin.viewmodel.EstadoAdminVin, vm: AdminVinViewModel) {
    val scheme = MaterialTheme.colorScheme
    val d = estado.detalle ?: return
    val cerrado = d.estado in setOf("APROBADA", "RECHAZADA")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
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
        }
    }
    if (!cerrado) {
        when (d.estado) {
            "PENDIENTE" -> Button(
                onClick = vm::iniciarRevision,
                enabled = !estado.ocupado,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Iniciar revisión", fontWeight = FontWeight.SemiBold)
            }
            "EN_REVISION", "EN REVISIÓN" -> {
                Button(
                    onClick = { vm.abrirResolver(aprobar = true) },
                    enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aprobar solicitud", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { vm.abrirResolver(aprobar = false) },
                    enabled = !estado.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rechazar solicitud", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ResolverIncidencia(estado: com.mycar.app.features.admin.viewmodel.EstadoAdminVin, vm: AdminVinViewModel) {
    val scheme = MaterialTheme.colorScheme
    val d = estado.detalle ?: return
    Text("Resultado *", fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = estado.aprobarSeleccionado, onClick = { vm.setResultado(aprobar = true) })
        Column {
            Text("Aprobar solicitud", fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
            Text("Se actualizará el VIN del vehículo.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = !estado.aprobarSeleccionado, onClick = { vm.setResultado(aprobar = false) })
        Column {
            Text("Rechazar solicitud", fontWeight = FontWeight.Medium, color = Color(0xFFC62828))
            Text("No se realizará el cambio.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (!estado.aprobarSeleccionado) {
        OutlinedTextField(
            value = estado.motivoRechazo,
            onValueChange = vm::editarMotivoRechazo,
            label = { Text("Observación *") },
            supportingText = { Text("${estado.motivoRechazo.length}/250") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 3
        )
    } else {
        OutlinedTextField(
            value = estado.motivoRechazo,
            onValueChange = vm::editarMotivoRechazo,
            label = { Text("Observación (opcional)") },
            supportingText = { Text("${estado.motivoRechazo.length}/250") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            minLines = 2
        )
    }
    FilaAdmin("VIN anterior", d.vinRegistrado.orEmpty())
    FilaAdmin("VIN nuevo", d.vinPropuesto.orEmpty())
    SoatBotonPrincipal("Resolver solicitud", vm::confirmarResolucion, enabled = !estado.ocupado)
    SoatBotonSecundario("Cancelar", vm::cancelarResolver)
}

@Composable
private fun ConfirmacionResolucion(estado: com.mycar.app.features.admin.viewmodel.EstadoAdminVin, vm: AdminVinViewModel) {
    val d = estado.detalle ?: return
    Text(
        "Solicitud resuelta. La solicitud ${d.codigo} fue ${SoporteFormulario.etiquetaEstado(d.estado).lowercase()}.",
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2E7D32)
    )
    if (d.estado == "APROBADA") {
        FilaAdmin("VIN anterior", estado.vinAnteriorConfirm.orEmpty())
        FilaAdmin("VIN nuevo", d.vinPropuesto.orEmpty())
        Text("El VIN del vehículo se actualizó correctamente.", color = Color(0xFF2E7D32))
    }
    SoatBotonPrincipal("Volver al listado", vm::volverLista)
}

@Composable
private fun FilaAdmin(etiqueta: String, valor: String) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(valor, fontWeight = FontWeight.Medium, color = scheme.onSurface)
    }
}
