package com.mycar.app.features.planes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.core.FechaMyCar
import com.mycar.app.features.planes.data.PlanDto
import com.mycar.app.features.planes.domain.PlanesRepository
import com.mycar.app.features.planes.viewmodel.EstadoPlanes
import com.mycar.app.features.planes.viewmodel.PantallaPlanes
import com.mycar.app.features.planes.viewmodel.PlanesViewModel

private val Navy = Color(0xFF0B1F33)
private val Teal = Color(0xFF0D9488)
private val Blue = Color(0xFF1D4E89)
private val Bg = Color(0xFFF4F7FB)
private val CardBg = Color.White
private val Mute = Color(0xFF5B6B7C)

@Composable
fun PlanesRoute(
    repository: PlanesRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: PlanesViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlanesViewModel(repository) as T
        }
    )
    val estado by vm.estado.collectAsState()
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }
    BackHandler(enabled = estado.pantalla != PantallaPlanes.PROCESANDO) {
        if (estado.pantalla == PantallaPlanes.CATALOGO) onVolver() else vm.volverAtras()
    }
    Box(modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        when (estado.pantalla) {
            PantallaPlanes.CATALOGO -> CatalogoScreen(estado, vm, onVolver)
            PantallaPlanes.PRUEBA_RESUMEN -> PruebaResumenScreen(estado, vm)
            PantallaPlanes.PAGO_CONFIRMAR -> PagoSimuladoConfirmarScreen(estado, vm)
            // Escenarios local-test solo en sidecar; en release se redirige al catálogo.
            PantallaPlanes.CONTRATAR, PantallaPlanes.ESCENARIOS -> CatalogoScreen(estado, vm, onVolver)
            PantallaPlanes.PROCESANDO -> ProcesandoScreen()
            PantallaPlanes.RESULTADO -> ResultadoScreen(estado, vm)
            PantallaPlanes.MI_SUSCRIPCION -> MiSuscripcionScreen(estado, vm)
            PantallaPlanes.DETALLE_SOLICITUD -> DetalleSolicitudScreen(estado, vm)
        }
    }
}

@Composable
private fun CatalogoScreen(estado: EstadoPlanes, vm: PlanesViewModel, onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onVolver) { Text("Volver", color = Navy) }
        Text("Planes y suscripción", color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("El plan efectivo lo define el servidor.", color = Mute, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Surface(
            color = Color(0xFFE8F4FC),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Pago simulado — Proyecto académico",
                color = Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
            return
        }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        estado.mia?.let { mia ->
            mia.avisoUsuario?.takeIf { it.isNotBlank() }?.let { aviso ->
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        aviso,
                        color = Color(0xFFB71C1C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("Plan actual", color = Mute, fontSize = 12.sp)
                    Text(
                        "${mia.planNombre ?: mia.planCodigo} · ${mia.estadoSuscripcionUi ?: mia.estado}",
                        color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 18.sp
                    )
                    mia.modalidad?.let { Text("Modalidad: $it", color = Teal, fontSize = 12.sp) }
                    Text(
                        "Cupo: ${mia.vehiculosRegistrados}/${mia.maxVehiculosRegistrados} · ${mia.vehiculosActivos} activos",
                        color = Mute, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val planes = estado.catalogo?.planes.orEmpty()
        planes.forEach { plan ->
            PlanCard(plan, esActual = plan.codigo == estado.mia?.planCodigo)
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = vm::abrirMiSuscripcion,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.History, null, tint = Navy)
            Spacer(Modifier.width(8.dp))
            Text("Mi suscripción y solicitudes", color = Navy)
        }
        Spacer(Modifier.height(12.dp))
        val solAbierta = estado.mia?.codigoSolicitudAbierta
        if (!solAbierta.isNullOrBlank()) {
            Surface(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Solicitud Premium en curso", color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                    Text(
                        "Código $solAbierta · sin cobro real.",
                        color = Mute, fontSize = 13.sp
                    )
                    TextButton(onClick = { vm.abrirDetalleSolicitud(solAbierta) }) {
                        Text("Ver mi solicitud", color = Teal)
                    }
                }
            }
        }
        val puedePrueba = estado.mia?.puedeSolicitarPruebaGratuita == true
        val puedeSandbox = estado.mia?.puedeContratarSandbox == true
        val prem = estado.catalogo?.planes?.firstOrNull { it.codigo == "PREMIUM" }
        // Solo visible si aún es elegible (una sola vez por cuenta).
        if (puedePrueba) {
            Button(
                onClick = vm::abrirPruebaGratuita,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Solicitar prueba gratuita (14 días)") }
            Text(
                "Una sola vez por cuenta · 14 días · requiere aprobación admin · sin cobro real.",
                color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
        } else {
            Text(
                when {
                    !solAbierta.isNullOrBlank() -> "Ya tienes una solicitud abierta. Admin puede aprobar o rechazar."
                    (estado.mia?.motivoNoPruebaGratuita ?: "").contains("utilizaste", ignoreCase = true) ->
                        "Ya utilizaste tu prueba gratuita Premium (solo una vez)."
                    else -> estado.mia?.motivoNoPruebaGratuita ?: "Prueba gratuita no disponible."
                },
                color = Mute, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Button(
            onClick = vm::abrirConfirmacionPagoSimulado,
            enabled = puedeSandbox && !estado.procesando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Probar Premium (simulado)") }
        Text(
            when {
                !solAbierta.isNullOrBlank() -> "Espera la decisión admin (aprobar/rechazar) o revisa tu solicitud."
                puedeSandbox ->
                    "Varias veces · vigente hasta que admin apague · ${prem?.moneda ?: ""} ${prem?.precioDemo ?: "—"} · sin cobro real."
                else -> estado.mia?.motivoNoSandbox ?: "No disponible"
            },
            color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onClick = vm::cargar, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reintentar", color = Teal)
        }
    }
}

@Composable
private fun PlanCard(plan: PlanDto, esActual: Boolean) {
    Surface(color = CardBg, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (plan.codigo == "PREMIUM") Icons.Outlined.WorkspacePremium else Icons.Outlined.DirectionsCar,
                    null,
                    tint = if (plan.codigo == "PREMIUM") Teal else Navy
                )
                Spacer(Modifier.width(8.dp))
                Text(plan.nombre ?: plan.codigo.orEmpty(), color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (esActual) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color(0xFFE0F2F1), shape = RoundedCornerShape(8.dp)) {
                        Text("Plan actual", color = Teal, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (plan.codigo == "FREE") {
                Text("Precio S/ 0", color = Navy, fontSize = 13.sp)
            } else {
                val precio = when {
                    plan.precioDemo != null && !plan.moneda.isNullOrBlank() ->
                        "${plan.moneda} ${plan.precioDemo} · ${plan.periodicidad ?: ""}"
                    else -> "Precio: pendiente de configuración"
                }
                Text(precio, color = Navy, fontSize = 13.sp)
            }
            Text("Hasta ${plan.maxVehiculosRegistrados ?: "—"} vehículos registrados", color = Mute, fontSize = 13.sp)
            plan.descripcionBeneficios?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun PruebaResumenScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    val prem = estado.catalogo?.planes?.firstOrNull { it.codigo == "PREMIUM" }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = vm::volverAtras) { Text("Cancelar", color = Navy) }
        Text("Solicitar Premium de prueba", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFFE8F4FC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Pago simulado — Proyecto académico",
                color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Plan: Premium", color = Navy, fontWeight = FontWeight.SemiBold)
                Text("Importe: S/ 0 (simulado)", color = Mute)
                Text("Duración: 14 días (luego vuelves a FREE automáticamente)", color = Mute)
                Text("Config: ${prem?.vigenciaPruebaConfigurada ?: "P14D"}", color = Mute, fontSize = 12.sp)
                Text("Cupo: ${prem?.maxVehiculosRegistrados ?: "—"} vehículos", color = Mute)
                prem?.descripcionBeneficios?.let { Text(it, color = Mute, fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta operación es una simulación académica. No se realizará ningún cobro real.",
                    color = Blue, fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::enviarPruebaGratuita,
            enabled = !estado.procesando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (estado.procesando) "Enviando…" else "Confirmar simulación") }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun PagoSimuladoConfirmarScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    val prem = estado.catalogo?.planes?.firstOrNull { it.codigo == "PREMIUM" }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = vm::volverAtras) { Text("Cancelar", color = Navy) }
        Text("Confirmar Premium simulado", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFFE8F4FC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Pago simulado — Proyecto académico",
                color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Plan: Premium", color = Navy, fontWeight = FontWeight.SemiBold)
                Text(
                    "Importe demo: ${prem?.moneda ?: "PEN"} ${prem?.precioDemo ?: "—"} · ${prem?.periodicidad ?: ""}",
                    color = Mute
                )
                Text(
                    "Vigencia: ${
                        prem?.vigenciaSandboxConfigurada?.takeIf {
                            it.isNotBlank() && !it.equals("ILIMITADA", true)
                        } ?: "hasta que admin apague el plan"
                    }",
                    color = Mute
                )
                Text("Cupo: hasta ${prem?.maxVehiculosRegistrados ?: "—"} vehículos", color = Mute)
                prem?.descripcionBeneficios?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Esta operación es una simulación académica. No se realizará ningún cobro real.",
                    color = Blue, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    "No se solicitan tarjeta, CVV ni credenciales bancarias. Cualquier comprobante es SIMULADO y SIN VALIDEZ DE PAGO.",
                    color = Mute, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::confirmarPagoSimulado,
            enabled = !estado.procesando,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (estado.procesando) "Procesando…" else "Confirmar simulación") }
        OutlinedButton(
            onClick = vm::volverAtras,
            enabled = !estado.procesando,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Cancelar", color = Navy) }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ContratarResumen(estado: EstadoPlanes, vm: PlanesViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = vm::volverAtras) { Text("Cancelar", color = Navy) }
        Text("Contratar Premium", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFFFFF7E6), shape = RoundedCornerShape(12.dp)) {
            Text(
                "ENTORNO DE PRUEBAS — Esta es una transacción de prueba. No se cobrará dinero real.",
                color = Color(0xFF8A5A00), modifier = Modifier.padding(12.dp), fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        val prem = estado.catalogo?.planes?.firstOrNull { it.codigo == "PREMIUM" }
        Text("Plan: Premium", color = Navy, fontWeight = FontWeight.SemiBold)
        Text(
            "Importe: ${prem?.moneda ?: "—"} ${prem?.precioDemo ?: "—"} (${prem?.periodicidad ?: "—"})",
            color = Mute
        )
        Text("Cupo: ${prem?.maxVehiculosRegistrados ?: "—"}", color = Mute)
        Text("Tras el pago de prueba, Administración debe aprobar la solicitud.", color = Blue, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = vm::continuarAEscenarios,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Continuar con pago de prueba") }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun EscenariosScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        TextButton(onClick = vm::volverAtras) { Text("Volver", color = Navy) }
        Text("Escenario de simulación", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Surface(color = Color(0xFFFFF7E6), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                "SIMULACIÓN interna — no es Mercado Pago real.",
                color = Color(0xFF8A5A00), modifier = Modifier.padding(12.dp), fontSize = 13.sp
            )
        }
        listOf("APROBADA", "RECHAZADA", "PENDIENTE", "CANCELADA").forEach { esc ->
            OutlinedButton(
                onClick = { vm.simularEscenario(esc) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(esc, color = Navy) }
        }
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ProcesandoScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Teal)
            Spacer(Modifier.height(12.dp))
            Text("Procesando…", color = Navy)
        }
    }
}

@Composable
private fun ResultadoScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            estado.resultadoTitulo ?: "Resultado",
            color = if (estado.exito) Teal else Navy,
            fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(estado.resultadoDetalle.orEmpty(), color = Mute)
        estado.operacion?.codigoSolicitud?.let {
            Text("Solicitud: $it", color = Mute, modifier = Modifier.padding(top = 8.dp))
        }
        estado.solicitudDetalle?.codigo?.let {
            Text("Solicitud: $it", color = Mute, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        val codigo = estado.operacion?.codigoSolicitud ?: estado.solicitudDetalle?.codigo
        if (codigo != null) {
            OutlinedButton(
                onClick = { vm.abrirDetalleSolicitud(codigo) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Ver mi solicitud", color = Navy) }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = vm::volverCatalogo,
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Volver a planes") }
    }
}

@Composable
private fun MiSuscripcionScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = vm::volverAtras) { Text("Volver", color = Navy) }
        Text("Mi suscripción", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        }
        estado.mia?.let { mia ->
            mia.avisoUsuario?.takeIf { it.isNotBlank() }?.let { aviso ->
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(aviso, color = Color(0xFFB71C1C), fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${mia.planNombre} · ${mia.estadoSuscripcionUi}", color = Navy, fontWeight = FontWeight.Bold)
                    Text("Modalidad: ${mia.modalidad ?: "—"}", color = Mute, fontSize = 13.sp)
                    Text("Inicio: ${FechaMyCar.fechaHora(mia.inicioUtc)}", color = Mute, fontSize = 13.sp)
                    Text("Fin: ${FechaMyCar.fechaHora(mia.finUtc)}", color = Mute, fontSize = 13.sp)
                    Text(
                        "Cupo efectivo: ${mia.maxVehiculosRegistrados} · activos ${mia.vehiculosActivos}",
                        color = Mute, fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Mis solicitudes", color = Navy, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (estado.solicitudes.isEmpty()) {
            Text("Sin solicitudes aún.", color = Mute)
        } else {
            estado.solicitudes.forEach { s ->
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.codigo.orEmpty(), color = Navy, fontWeight = FontWeight.Medium)
                        Text(
                            "${etiquetaModalidad(s.modalidad)} · ${s.estado}",
                            color = Mute, fontSize = 12.sp
                        )
                        Text(FechaMyCar.fechaHora(s.creadoUtc), color = Mute, fontSize = 12.sp)
                        s.motivoRechazo?.let {
                            Text("Motivo: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        TextButton(onClick = { vm.abrirDetalleSolicitud(s.codigo.orEmpty()) }) {
                            Text("Ver detalle", color = Teal)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Historial", color = Navy, fontWeight = FontWeight.SemiBold)
        estado.eventos.take(20).forEach { e ->
            Text(
                "${FechaMyCar.fechaHora(e.creadoUtc)} · ${e.tipoEvento}",
                color = Mute, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DetalleSolicitudScreen(estado: EstadoPlanes, vm: PlanesViewModel) {
    val s = estado.solicitudDetalle
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = vm::volverAtras) { Text("Volver", color = Navy) }
        Text("Detalle de solicitud", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        estado.mensaje?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (estado.cargando) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
            }
        }
        if (s == null && !estado.cargando) {
            Text("Sin datos de la solicitud.", color = Mute)
            return
        }
        if (s != null) {
            Surface(color = CardBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.codigo.orEmpty(), color = Navy, fontWeight = FontWeight.Bold)
                    Text(etiquetaModalidad(s.modalidad), color = Teal, fontSize = 13.sp)
                    Text("Estado: ${s.estado}", color = Mute)
                    Text("Creada: ${FechaMyCar.fechaHora(s.creadoUtc)}", color = Mute, fontSize = 13.sp)
                    Text("Decisión: ${FechaMyCar.fechaHora(s.decididoUtc)}", color = Mute, fontSize = 13.sp)
                    s.motivoRechazo?.let {
                        Text("Motivo rechazo: $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    s.observacionAdmin?.let { Text("Observación: $it", color = Mute, fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    when (s.modalidad) {
                        "PAGO_SANDBOX" -> {
                            if (s.referenciaOperacion.isNullOrBlank()) {
                                Text(
                                    "Pago simulado sin cobro real. Esperando aprobación del administrador.",
                                    color = Mute, fontSize = 13.sp
                                )
                            } else {
                                Text("Operación: ${s.referenciaOperacion}", color = Mute, fontSize = 12.sp)
                                Text(
                                    "Proveedor: ${s.proveedorOperacion ?: "—"} · ${s.estadoOperacion ?: "—"}",
                                    color = Mute, fontSize = 12.sp
                                )
                                Text(
                                    "Importe: ${s.monedaOperacion ?: ""} ${s.importeOperacion ?: "—"}",
                                    color = Mute, fontSize = 12.sp
                                )
                            }
                        }
                        else -> Text("Prueba gratuita — no requiere pago.", color = Mute, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun etiquetaModalidad(m: String?): String = when (m) {
    "PRUEBA_GRATUITA" -> "Prueba gratuita (14 días)"
    "PAGO_SANDBOX" -> "Pago simulado (hasta que admin apague)"
    else -> m ?: "—"
}
