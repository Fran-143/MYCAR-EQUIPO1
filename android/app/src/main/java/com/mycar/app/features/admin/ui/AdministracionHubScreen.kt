package com.mycar.app.features.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.features.autenticacion.data.AdminHubResponse
import com.mycar.app.features.autenticacion.data.AdminSeccionDto
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext

private val Navy = Color(0xFF12285F)
private val Teal = Color(0xFF007E98)
private val Bg = Color(0xFFF0F5FA)
private val Mute = Color(0xFF718096)

@Composable
fun AdministracionHubScreen(
    authContext: AuthenticatedContext,
    onLogout: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var subpantalla by remember { mutableStateOf<String?>(null) }
    val adminRepo = remember(authContext.sessionId) {
        com.mycar.app.features.admin.data.adminVinRepository(context, authContext)
    }
    val usuariosRepo = remember(authContext.sessionId) {
        com.mycar.app.features.admin.data.adminUsuariosRepository(context, authContext)
    }
    val catalogosRepo = remember(authContext.sessionId) {
        com.mycar.app.features.admin.data.adminCatalogosRepository(context, authContext)
    }
    when (subpantalla) {
        "pruebas_locales" -> {
            if (com.mycar.app.BuildConfig.LOCAL_TEST_TOOLS) {
                com.mycar.app.features.localtest.PruebasLocalesRoute(
                    onVolver = { subpantalla = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            return
        }
        "usuarios" -> {
            UsuariosAdminRoute(
                repository = usuariosRepo,
                onVolver = { subpantalla = null },
                onAbrirPruebas = if (com.mycar.app.BuildConfig.LOCAL_TEST_TOOLS) {
                    { subpantalla = "pruebas_locales" }
                } else null,
                modifier = Modifier.fillMaxSize()
            )
            return
        }
        "catalogos" -> {
            CatalogosAdminRoute(
                repository = catalogosRepo,
                onVolver = { subpantalla = null },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
        "incidencias_vin" -> {
            IncidenciasVinRoute(
                repository = adminRepo,
                onVolver = { subpantalla = "incidencias_hub" },
                modifier = Modifier.fillMaxSize()
            )
            return
        }
        "incidencias_hub" -> {
            IncidenciasHubScreen(
                onVolver = { subpantalla = null },
                onVin = { subpantalla = "incidencias_vin" }
            )
            return
        }
    }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hub by remember { mutableStateOf<AdminHubResponse?>(null) }

    LaunchedEffect(authContext.sessionId, authContext.version) {
        loading = true
        error = null
        try {
            hub = AuthGraph.repository(context).loadAdminHub(authContext)
        } catch (ex: Exception) {
            error = ex.message ?: "No se pudo cargar Administración."
        } finally {
            loading = false
        }
    }

    val secciones = hub?.secciones?.takeIf { it.isNotEmpty() } ?: listOf(
        AdminSeccionDto("USUARIOS", "Usuarios", false, "PROXIMAMENTE"),
        AdminSeccionDto("SUSCRIPCIONES", "Suscripciones", false, "PROXIMAMENTE"),
        AdminSeccionDto("INCIDENCIAS", "Incidencias", true, "DISPONIBLE"),
        AdminSeccionDto("CATALOGOS", "Catálogos", true, "DISPONIBLE")
    )

    Scaffold(containerColor = Bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text("MyCar", color = Navy, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                hub?.titulo ?: "Administración",
                color = Navy,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hola, Administrador. Gestiona usuarios, suscripciones, incidencias y catálogos.",
                color = Color(0xFF4A5568)
            )
            Spacer(Modifier.height(20.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                secciones.forEach { seccion ->
                    AdminSectionCard(seccion) {
                        when {
                            seccion.codigo == "INCIDENCIAS" || seccion.nombre == "Incidencias" ->
                                subpantalla = "incidencias_hub"
                            seccion.codigo == "USUARIOS" || seccion.nombre == "Usuarios" ->
                                subpantalla = "usuarios"
                            seccion.codigo == "CATALOGOS" || seccion.nombre == "Catálogos" ->
                                subpantalla = "catalogos"
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (com.mycar.app.BuildConfig.LOCAL_TEST_TOOLS) {
                OutlinedButton(
                    onClick = { subpantalla = "pruebas_locales" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pruebas locales") }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun IncidenciasHubScreen(onVolver: () -> Unit, onVin: () -> Unit) {
    var proximamente by remember { mutableStateOf(false) }
    Scaffold(containerColor = Bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Navy, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Volver", color = Navy)
            }
            Text("Incidencias", fontWeight = FontWeight.Bold, color = Navy, style = MaterialTheme.typography.titleLarge)
            Text("Gestión de incidencias", color = Mute)
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onVin),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HubIconBadge(Icons.Outlined.Report, Navy)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Incidencias de VIN", fontWeight = FontWeight.SemiBold, color = Navy)
                        Text("Solicitudes de corrección de VIN de vehículos.", color = Mute)
                        Text("Disponible", color = Teal, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Mute)
                }
            }
            Card(
                Modifier.fillMaxWidth().clickable { proximamente = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HubIconBadge(Icons.Outlined.SupportAgent, Mute)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Otras incidencias", fontWeight = FontWeight.SemiBold, color = Navy)
                        Text("Otros reportes y solicitudes.", color = Mute)
                        Text("Próximamente", color = Mute, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
    if (proximamente) {
        AlertDialog(
            onDismissRequest = { proximamente = false },
            title = { Text("Próximamente") },
            text = { Text("Las incidencias varias se habilitarán en una entrega posterior.") },
            confirmButton = { TextButton(onClick = { proximamente = false }) { Text("Entendido") } }
        )
    }
}

@Composable
private fun AdminSectionCard(seccion: AdminSeccionDto, onClick: (() -> Unit)? = null) {
    val disponible = seccion.disponible ||
        seccion.codigo == "INCIDENCIAS" || seccion.nombre == "Incidencias" ||
        seccion.codigo == "USUARIOS" || seccion.nombre == "Usuarios" ||
        seccion.codigo == "CATALOGOS" || seccion.nombre == "Catálogos"
    val icon = when (seccion.codigo?.uppercase()) {
        "USUARIOS" -> Icons.Outlined.People
        "SUSCRIPCIONES" -> Icons.Outlined.CreditCard
        "INCIDENCIAS" -> Icons.Outlined.Report
        "CATALOGOS" -> Icons.Outlined.Category
        else -> Icons.Outlined.Category
    }
    val accent = when {
        !disponible -> Mute
        seccion.codigo.equals("CATALOGOS", true) -> Teal
        else -> Navy
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (disponible && onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HubIconBadge(icon, accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    seccion.nombre ?: seccion.codigo.orEmpty(),
                    color = Navy,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (disponible) "Disponible" else "Próximamente",
                    color = if (disponible) Teal else Mute
                )
            }
            if (disponible) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Mute,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HubIconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(44.dp)
            .background(tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
