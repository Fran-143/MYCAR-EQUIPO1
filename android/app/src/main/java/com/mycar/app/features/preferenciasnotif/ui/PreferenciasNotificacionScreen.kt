package com.mycar.app.features.preferenciasnotif.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.perfil.ui.PerfilColors
import com.mycar.app.features.preferenciasnotif.domain.PermisoDispositivo
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository
import com.mycar.app.features.preferenciasnotif.viewmodel.PreferenciasNotificacionViewModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun PreferenciasNotificacionRoute(
    repository: PreferenciasNotificacionRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<CancellableContinuation<PermisoDispositivo>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pending?.resume(if (granted) PermisoDispositivo.CONCEDIDO else PermisoDispositivo.DENEGADO)
        pending = null
    }

    fun permisoOsActual(): PermisoDispositivo {
        if (Build.VERSION.SDK_INT < 33) return PermisoDispositivo.CONCEDIDO
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermisoDispositivo.CONCEDIDO else PermisoDispositivo.DENEGADO
    }

    suspend fun solicitarPermiso(): PermisoDispositivo {
        if (Build.VERSION.SDK_INT < 33) return PermisoDispositivo.CONCEDIDO
        if (permisoOsActual() == PermisoDispositivo.CONCEDIDO) return PermisoDispositivo.CONCEDIDO
        return suspendCancellableCoroutine { cont ->
            pending = cont
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            cont.invokeOnCancellation { pending = null }
        }
    }

    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PreferenciasNotificacionViewModel(
                    repository,
                    consultarPermisoOs = { permisoOsActual() },
                    solicitarPermisoOs = { solicitarPermiso() }
                ) as T
            }
        }
    }
    val vm: PreferenciasNotificacionViewModel = viewModel(factory = factory)
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onVolver) { Text("Volver", color = PerfilColors.Navy) }
        Text(
            "Notificaciones externas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PerfilColors.Navy
        )
        Text(
            "Activa o desactiva el envío externo por categoría. Los avisos dentro de la app no se borran.",
            color = PerfilColors.Mute
        )
        if (estado.cargando) {
            CircularProgressIndicator(color = PerfilColors.Navy, modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }
        SwitchFila("Mantenimiento", estado.mantenimiento, enabled = !estado.guardando, onChange = vm::setMantenimiento)
        SwitchFila("SOAT", estado.soat, enabled = !estado.guardando, onChange = vm::setSoat)
        SwitchFila("Kilometraje", estado.kilometraje, enabled = !estado.guardando, onChange = vm::setKilometraje)
        Text(
            "Permiso del dispositivo: ${estado.permisoDispositivo.api}",
            color = PerfilColors.Mute
        )
        estado.avisoPermiso?.let { Text(it, color = PerfilColors.Danger) }
        estado.mensajeError?.let { Text(it, color = PerfilColors.Danger) }
        estado.mensajeExito?.let { Text(it, color = PerfilColors.Teal) }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::guardar,
            enabled = !estado.guardando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (estado.guardando) "Guardando…" else "Guardar preferencias")
        }
    }
}

@Composable
private fun SwitchFila(
    etiqueta: String,
    valor: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(etiqueta, color = PerfilColors.Navy, fontWeight = FontWeight.Medium)
        Switch(checked = valor, onCheckedChange = onChange, enabled = enabled)
    }
}
