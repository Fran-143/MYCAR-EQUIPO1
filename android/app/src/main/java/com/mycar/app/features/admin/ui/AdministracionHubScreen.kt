package com.mycar.app.features.admin.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycar.app.features.autenticacion.data.AdminHubResponse
import com.mycar.app.features.autenticacion.data.AdminSeccionDto
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.AuthenticatedContext

private val Navy = Color(0xFF12285F)
private val Teal = Color(0xFF007E98)
private val Bg = Color(0xFFF0F5FA)

@Composable
fun AdministracionHubScreen(
    authContext: AuthenticatedContext,
    onLogout: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
        AdminSeccionDto("INCIDENCIAS", "Incidencias", false, "PROXIMAMENTE"),
        AdminSeccionDto("CATALOGOS", "Catálogos", false, "PROXIMAMENTE")
    )

    Scaffold(containerColor = Bg) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text("MyCar", color = Navy, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(hub?.titulo ?: "Administración", color = Navy, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Panel administrativo. Las secciones se habilitarán en entregas posteriores.",
                color = Color(0xFF4A5568)
            )
            Spacer(Modifier.height(20.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Teal)
                }
            } else if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            } else {
                secciones.forEach { seccion ->
                    AdminSectionCard(seccion)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

@Composable
private fun AdminSectionCard(seccion: AdminSeccionDto) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(seccion.nombre ?: seccion.codigo.orEmpty(), color = Navy, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (seccion.disponible) "Disponible" else "Próximamente",
                color = if (seccion.disponible) Teal else Color(0xFF718096)
            )
        }
    }
}
