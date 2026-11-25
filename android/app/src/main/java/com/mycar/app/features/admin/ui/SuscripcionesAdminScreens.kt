package com.mycar.app.features.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.core.FechaMyCar
import com.mycar.app.features.admin.data.AdminSuscripcionesRepository
import com.mycar.app.features.admin.viewmodel.EstadoAdminSuscripciones
import com.mycar.app.features.admin.viewmodel.PantallaAdminSuscripciones
import com.mycar.app.features.admin.viewmodel.PestanaAdminSuscripciones
import com.mycar.app.features.admin.viewmodel.SuscripcionesAdminViewModel
import com.mycar.app.features.planes.data.EventoSuscripcionDto

private val Teal = Color(0xFF0D9488)
private val Danger = Color(0xFFC53030)

@Composable
fun SuscripcionesAdminRoute(
    repository: AdminSuscripcionesRepository,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: SuscripcionesAdminViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SuscripcionesAdminViewModel(repository) as T
        }
    )
    val estado by vm.estado.collectAsState()
    BackHandler {
        if (estado.pantalla != PantallaAdminSuscripciones.LISTA) vm.volverLista() else onVolver()
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        when (estado.pantalla) {
            PantallaAdminSuscripciones.LISTA -> ListaHub(estado, vm, onVolver)
            PantallaAdminSuscripciones.DETALLE_SUSCRIPCION -> DetalleSuscripcion(estado, vm)
            PantallaAdminSuscripciones.DETALLE_SOLICITUD -> DetalleSolicitudAdmin(estado, vm)
        }
    }
}

@Composable
private fun ListaHub(
    estado: EstadoAdminSuscripciones,
    vm: SuscripcionesAdminViewModel,
    onVolver: () -> Unit
) {
    var resumenAbierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CreditCard, null, tint = Teal, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Gestión de suscripciones", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Consulta planes y administra solicitudes Premium.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            estado.resumen?.let { r ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { resumenAbierto = !resumenAbierto }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Resumen de suscripciones",
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                if (resumenAbierto) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (resumenAbierto) "Ocultar" else "Desplegar",
                                tint = Teal
                            )
                        }
                        AnimatedVisibility(
                            visible = resumenAbierto,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResumenChip("FREE", r.freeActivas.toString(), Icons.Outlined.Person)
                                ResumenChip("Prueba", r.premiumPruebaActivas.toString(), Icons.Outlined.WorkspacePremium)
                                ResumenChip("Sandbox", r.premiumSandboxActivas.toString(), Icons.Outlined.CreditCard)
                                ResumenChip("Pendientes", r.solicitudesPendientesAprobacion.toString(), Icons.Outlined.CreditCard)
                                ResumenChip("Vencidas", r.vencidas.toString(), Icons.Outlined.Person)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TabRow(
                selectedTabIndex = if (estado.pestana == PestanaAdminSuscripciones.SOLICITUDES) 0 else 1,
                containerColor = Color.Transparent,
                contentColor = Teal
            ) {
                Tab(
                    selected = estado.pestana == PestanaAdminSuscripciones.SOLICITUDES,
                    onClick = { vm.setPestana(PestanaAdminSuscripciones.SOLICITUDES) },
                    text = { Text("Solicitudes Premium") }
                )
                Tab(
                    selected = estado.pestana == PestanaAdminSuscripciones.SUSCRIPCIONES,
                    onClick = { vm.setPestana(PestanaAdminSuscripciones.SUSCRIPCIONES) },
                    text = { Text("Suscripciones") }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = estado.q,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre, correo o código") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (estado.q.isNotEmpty()) {
                        IconButton(onClick = vm::limpiarQuery) {
                            Icon(Icons.Outlined.Clear, null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (estado.pestana == PestanaAdminSuscripciones.SOLICITUDES) {
                    ChipFiltro("Todas", estado.filtro == null) { vm.setFiltro(null) }
                    ChipFiltro("Pendientes", estado.filtro == "PENDIENTES") { vm.setFiltro("PENDIENTES") }
                    ChipFiltro("Aprobadas", estado.filtro == "APROBADA") { vm.setFiltro("APROBADA") }
                    ChipFiltro("Rechazadas", estado.filtro == "RECHAZADA") { vm.setFiltro("RECHAZADA") }
                } else {
                    ChipFiltro("Todas", estado.filtro == null) { vm.setFiltro(null) }
                    ChipFiltro("FREE", estado.filtro == "FREE") { vm.setFiltro("FREE") }
                    ChipFiltro("Premium prueba", estado.filtro == "PREMIUM_PRUEBA") { vm.setFiltro("PREMIUM_PRUEBA") }
                    ChipFiltro("Premium Sandbox", estado.filtro == "PREMIUM_SANDBOX") { vm.setFiltro("PREMIUM_SANDBOX") }
                    ChipFiltro("Vencidas", estado.filtro == "VENCIDAS") { vm.setFiltro("VENCIDAS") }
                }
            }
            estado.mensaje?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        } else if (estado.pestana == PestanaAdminSuscripciones.SOLICITUDES) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(estado.solicitudes, key = { it.codigo.orEmpty() }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { vm.abrirDetalleSolicitud(item.codigo.orEmpty()) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.WorkspacePremium, null, tint = Teal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.codigo.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Usuario: ${item.nombre.orEmpty().ifBlank { "—" }}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Correo: ${item.correo.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text("Modalidad: ${etiquetaModalidad(item.modalidad)}", color = Teal, fontSize = 12.sp)
                                Text("Estado: ${item.estado}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                Text("Fecha: ${FechaMyCar.fechaHora(item.creadoUtc)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Text("Ver detalle", color = Teal, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(estado.items, key = { "${it.idUsuario}-${it.inicioUtc}" }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { vm.abrirDetalleUsuario(item.idUsuario) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.nombre.orEmpty().ifBlank { "Usuario" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text(item.correo.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(
                                    "${item.planCodigo} · ${item.modalidad ?: "—"} · ${item.estado}",
                                    color = Teal, fontSize = 12.sp
                                )
                                item.diasRestantes?.let {
                                    Text(
                                        "Días restantes: $it",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Text("Ver", color = Teal, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumenChip(titulo: String, valor: String, icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Teal, modifier = Modifier.size(18.dp))
            Text(valor, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChipFiltro(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun BarraAtras(titulo: String, onVolver: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onVolver) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(titulo, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun puedeCancelarPremiumDetalle(estado: EstadoAdminSuscripciones): Boolean {
    val s = estado.detalle?.suscripcion ?: return false
    val ui = s.estadoSuscripcionUi.orEmpty()
    val mod = s.modalidad.orEmpty()
    val premiumActivo = ui == "PREMIUM_PRUEBA_ACTIVA" || ui == "PREMIUM_SANDBOX_ACTIVA" ||
        (s.planCodigo == "PREMIUM" && s.estado == "ACTIVA")
    return premiumActivo && (mod == "PRUEBA_GRATUITA" || mod == "PAGO_SANDBOX")
}

@Composable
private fun DetalleSuscripcion(estado: EstadoAdminSuscripciones, vm: SuscripcionesAdminViewModel) {
    var historialAbierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BarraAtras("Detalle de suscripción", vm::volverLista)
        if (estado.cargando) {
            CircularProgressIndicator(color = Teal)
            return
        }
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val d = estado.detalle ?: return
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Person, null, tint = Teal, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(d.nombre.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(d.correo.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${d.suscripcion?.planCodigo ?: "—"} · ${d.suscripcion?.estadoSuscripcionUi ?: d.suscripcion?.estado}",
                        color = Teal, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        d.suscripcion?.let { s ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Información del plan", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Modalidad: ${s.modalidad ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Inicio: ${FechaMyCar.fechaHora(s.inicioUtc)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Fin: ${FechaMyCar.fechaHora(s.finUtc)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    s.diasRestantes?.let {
                        Text(
                            "Días restantes: $it",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Text("Cupo: ${s.maxVehiculosRegistrados}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Origen: ${s.origen}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Vehículos", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Registrados: ${d.vehiculos["registrados"]}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Activos: ${d.vehiculos["activos"]}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Inactivos: ${d.vehiculos["inactivos"]}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Principal: ${d.vehiculos["idPrincipal"] ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (puedeCancelarPremiumDetalle(estado)) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = vm::pedirCancelarPremium,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Cancel, null)
                Spacer(Modifier.width(8.dp))
                Text("Anular Premium / detener experiencia")
            }
            Text(
                "Quita beneficios Premium (prueba o pago simulado) y deja solo el vehículo principal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { historialAbierto = !historialAbierto },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.History, null, tint = Teal)
            Spacer(Modifier.width(8.dp))
            Text(if (historialAbierto) "Ocultar historial" else "Ver historial", color = MaterialTheme.colorScheme.onSurface)
        }
        AnimatedVisibility(visible = historialAbierto) {
            Column(Modifier.padding(top = 10.dp)) {
                TimelineEventos(d.eventos)
            }
        }
    }
    if (estado.mostrarCancelarPremium) {
        DialogoAnularPremium(estado, vm)
    }
}

@Composable
private fun DialogoAnularPremium(estado: EstadoAdminSuscripciones, vm: SuscripcionesAdminViewModel) {
    AlertDialog(
        onDismissRequest = vm::cancelarModales,
        title = { Text("Anular Premium") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Finaliza los beneficios Premium (prueba o pago simulado) y regresa la cuenta a FREE.")
                Text(
                    "Si tiene varios vehículos activos, solo permanecerá activo el vehículo principal.",
                    color = Danger, fontSize = 13.sp
                )
                OutlinedTextField(
                    value = estado.motivoCancelarPremium,
                    onValueChange = vm::setMotivoCancelarPremium,
                    label = { Text("Motivo de anulación") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = vm::confirmarCancelarPremium) {
                Text("Confirmar anulación", color = Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = vm::cancelarModales) {
                Text("Volver", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun DetalleSolicitudAdmin(estado: EstadoAdminSuscripciones, vm: SuscripcionesAdminViewModel) {
    var historialAbierto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        BarraAtras("Detalle de solicitud", vm::volverLista)
        if (estado.cargando) {
            CircularProgressIndicator(color = Teal)
            return
        }
        estado.mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val d = estado.detalleSolicitud ?: return
        val s = d.solicitud
        Text(s?.codigo.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        Text("Estado: ${s?.estado}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, tint = Teal)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(d.nombre.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(d.correo.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("ID ${d.idUsuario}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Plan solicitado", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("PREMIUM · ${etiquetaModalidad(s?.modalidad)}", color = Teal)
                Text("Vigencia a conceder: ${d.vigenciaQueSeConcederia ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Cupo: ${d.cupoVehiculos ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                d.descripcionBeneficios?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Pago", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                when {
                    s?.modalidad == "PAGO_SANDBOX" && s.referenciaOperacion.isNullOrBlank() -> {
                        Text(
                            "Pago simulado (sin cobro real). Pendiente de tu aprobación o rechazo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    s?.modalidad == "PAGO_SANDBOX" -> {
                        Text("Proveedor: ${s.proveedorOperacion ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("Estado: ${s.estadoOperacion ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("Importe: ${s.monedaOperacion ?: ""} ${s.importeOperacion ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("Operación: ${s.referenciaOperacion}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        TextButton(onClick = vm::verificarUltima) {
                            Text("Verificar estado", color = Teal)
                        }
                    }
                    else -> {
                        Text("Prueba gratuita — no requiere pago.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
                estado.verificacion?.let { v ->
                    Text(v.mensaje.orEmpty(), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Aprobación", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text("Estado: ${s?.estado}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Creada: ${FechaMyCar.fechaHora(s?.creadoUtc)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Decisión: ${FechaMyCar.fechaHora(s?.decididoUtc)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                s?.motivoRechazo?.let {
                    Text("Motivo: $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                s?.observacionAdmin?.let { Text("Observación: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (s?.estado == "PENDIENTE_APROBACION") {
            if (!d.puedeAprobar) {
                Text(d.motivoNoAprobar ?: "No se puede aprobar aún.", color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = vm::pedirAprobar,
                enabled = d.puedeAprobar,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Aprobar solicitud") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = vm::pedirRechazar,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Rechazar solicitud", color = MaterialTheme.colorScheme.onSurface) }
            Text(
                "Requiere MFA confirmado en los últimos 10 minutos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)
            )
        } else if (s?.estado == "APROBADA") {
            Text("Solicitud aprobada", color = Teal, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::pedirCancelarPremium,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Cancel, null)
                Spacer(Modifier.width(8.dp))
                Text("Anular Premium / detener experiencia")
            }
            Text(
                "Para pruebas: quita el Premium concedido (prueba 14d o pago 30d) y vuelve a FREE.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "Requiere MFA confirmado en los últimos 10 minutos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (s?.estado == "RECHAZADA") {
            Text("Solicitud rechazada", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { historialAbierto = !historialAbierto },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.History, null, tint = Teal)
            Spacer(Modifier.width(8.dp))
            Text(if (historialAbierto) "Ocultar historial" else "Ver historial", color = MaterialTheme.colorScheme.onSurface)
        }
        AnimatedVisibility(visible = historialAbierto) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Eventos", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                TimelineEventos(d.historial)
                if (d.solicitudesAnteriores.isNotEmpty()) {
                    Text("Solicitudes anteriores (solo lectura)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    d.solicitudesAnteriores.forEach { ant ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(ant.codigo.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text("${etiquetaModalidad(ant.modalidad)} · ${ant.estado}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(FechaMyCar.fechaHora(ant.creadoUtc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (estado.mostrarAprobar) {
        AlertDialog(
            onDismissRequest = vm::cancelarModales,
            title = { Text("Aprobar Premium") },
            text = {
                Column {
                    Text("Usuario: ${estado.detalleSolicitud?.nombre}")
                    Text("Modalidad: ${etiquetaModalidad(estado.detalleSolicitud?.solicitud?.modalidad)}")
                    Text("Vigencia: ${estado.detalleSolicitud?.vigenciaQueSeConcederia ?: "—"}")
                    Text("Cupo: ${estado.detalleSolicitud?.cupoVehiculos ?: "—"}")
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmarAprobar) { Text("Confirmar", color = Teal) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelarModales) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
    if (estado.mostrarRechazar) {
        AlertDialog(
            onDismissRequest = vm::cancelarModales,
            title = { Text("Rechazar solicitud Premium") },
            text = {
                OutlinedTextField(
                    value = estado.motivoRechazo,
                    onValueChange = vm::setMotivoRechazo,
                    label = { Text("Motivo del rechazo") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmarRechazar) {
                    Text("Confirmar rechazo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelarModales) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
    if (estado.mostrarCancelarPremium) {
        DialogoAnularPremium(estado, vm)
    }
}

@Composable
private fun TimelineEventos(eventos: List<EventoSuscripcionDto>) {
    if (eventos.isEmpty()) {
        Text("Sin eventos registrados.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        eventos.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .background(Teal, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(etiquetaEvento(e.tipoEvento), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(FechaMyCar.fechaHora(e.creadoUtc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    e.detalle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun etiquetaEvento(tipo: String?): String = when (tipo) {
    "SOLICITUD_PRUEBA_CREADA", "SOLICITUD_REGISTRADA" -> "Solicitud registrada"
    "PAGO_APROBADO", "OPERACION_APROBADA" -> "Pago de prueba aprobado"
    "SOLICITUD_PENDIENTE_APROBACION" -> "Pendiente de aprobación"
    "SOLICITUD_APROBADA", "PREMIUM_ACTIVADO" -> "Solicitud aprobada / Premium activado"
    "SOLICITUD_RECHAZADA" -> "Solicitud rechazada"
    "PREMIUM_CANCELADO_ADMIN" -> "Premium cancelado"
    "PREMIUM_VENCIDO", "VENCIMIENTO" -> "Premium vencido"
    "FREE_REACTIVADA" -> "Vuelta a FREE"
    else -> tipo?.replace('_', ' ') ?: "Evento"
}

private fun etiquetaModalidad(m: String?): String = when (m) {
    "PRUEBA_GRATUITA" -> "Prueba gratuita (14 días)"
    "PAGO_SANDBOX" -> "Pago simulado (30 días)"
    else -> m ?: "—"
}
